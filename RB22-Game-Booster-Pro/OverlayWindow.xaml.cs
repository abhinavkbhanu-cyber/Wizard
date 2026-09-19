using System;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;

namespace RB22.GameBooster;

public partial class OverlayWindow : Window
{
    private readonly PerformanceCounter? cpuCounter;
    private readonly PerformanceCounter? ramCounter;
    private readonly DispatcherTimer timer;
    private Process? presentMon;
    private StreamReader? presentReader;
    private readonly List<double> frameTimes=new();
    private readonly object telemetryLock=new();
    private int fps=-1;
    private double onePercentLow=-1;
    private double gpuBusy=-1;
    private double frameTime=-1;
    private string telemetryState="Searching for game telemetry…";

    public OverlayWindow()
    {
        InitializeComponent();

        try
        {
            cpuCounter = new PerformanceCounter("Processor", "% Processor Time", "_Total");
            cpuCounter.NextValue();
        }
        catch
        {
        }

        try
        {
            ramCounter = new PerformanceCounter("Memory", "% Committed Bytes In Use");
        }
        catch
        {
        }

        MouseLeftButtonDown += Overlay_MouseLeftButtonDown;

        timer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(1)
        };
        timer.Tick += UpdateStats;
        timer.Start();
        StartPresentMon();

        Closed += (_, _) =>
        {
            timer.Stop();
            StopPresentMon();
            cpuCounter?.Dispose();
            ramCounter?.Dispose();
        };
    }

    private void StartPresentMon()
    {
        try
        {
            var exe=Path.Combine(AppContext.BaseDirectory,"PresentMon.exe");
            if(!File.Exists(exe)){telemetryState="FPS telemetry unavailable";return;}
            var game=Process.GetProcesses().Where(p=>p.MainWindowHandle!=IntPtr.Zero).Where(IsLikelyGame).FirstOrDefault();
            if(game==null){telemetryState="Waiting for a game…";return;}
            var psi=new ProcessStartInfo(exe,"--process_id "+game.Id+" --output_stdout --track_display --track_gpu --terminate_on_proc_exit"){UseShellExecute=false,RedirectStandardOutput=true,RedirectStandardError=true,CreateNoWindow=true};
            presentMon=Process.Start(psi);
            if(presentMon==null){telemetryState="FPS telemetry unavailable";return;}
            presentReader=presentMon.StandardOutput;
            telemetryState="Tracking "+game.ProcessName+".exe";
            _=System.Threading.Tasks.Task.Run(ReadPresentMon);
        }catch{telemetryState="FPS telemetry unavailable";}
    }
    private async System.Threading.Tasks.Task ReadPresentMon()
    {
        try
        {
            var header=await presentReader!.ReadLineAsync();
            if(string.IsNullOrWhiteSpace(header))return;
            var columns=ParseCsv(header);
            int between=FindColumn(columns,"MsBetweenPresents");
            int gpu=FindColumn(columns,"MsGPUBusy");
            if(between<0)between=FindColumn(columns,"MsBetweenDisplayChange");
            while(presentReader!=null&&!presentReader.EndOfStream)
            {
                var line=await presentReader.ReadLineAsync();
                if(string.IsNullOrWhiteSpace(line))continue;
                var row=ParseCsv(line);
                if(between>=0&&between<row.Count&&double.TryParse(row[between],NumberStyles.Float,CultureInfo.InvariantCulture,out var ms)&&ms>0&&ms<1000)
                {
                    lock(telemetryLock)
                    {
                        frameTimes.Add(ms);
                        if(frameTimes.Count>240)frameTimes.RemoveAt(0);
                        var recent=frameTimes.TakeLast(Math.Min(120,frameTimes.Count)).ToArray();
                        if(recent.Length>0)fps=(int)Math.Round(1000.0/recent.Average());
                        if(recent.Length>=30)
                        {
                            var sorted=recent.OrderBy(v=>v).ToArray();
                            var p99=sorted[Math.Min(sorted.Length-1,(int)Math.Ceiling(sorted.Length*.99)-1)];
                            onePercentLow=p99>0?1000.0/p99:-1;
                            frameTime=recent.Average();
                        }
                    }
                }
                if(gpu>=0&&gpu<row.Count&&double.TryParse(row[gpu],NumberStyles.Float,CultureInfo.InvariantCulture,out var gpuMs))lock(telemetryLock)gpuBusy=gpuMs;
            }
        }catch{telemetryState="FPS telemetry stopped";}
    }
    private static int FindColumn(List<string> columns,string name)
    {
        for(int i=0;i<columns.Count;i++)if(string.Equals(columns[i].Trim(),name,StringComparison.OrdinalIgnoreCase))return i;
        return -1;
    }
    private static List<string> ParseCsv(string line)
    {
        var result=new List<string>();var sb=new StringBuilder();bool quoted=false;
        foreach(var ch in line){if(ch=='"'){quoted=!quoted;continue;}if(ch==','&&!quoted){result.Add(sb.ToString());sb.Clear();}else sb.Append(ch);}
        result.Add(sb.ToString());return result;
    }
    private static bool IsLikelyGame(Process p)
    {
        var n=p.ProcessName.ToLowerInvariant();
        return n.Contains("roblox")||n.Contains("valorant")||n.Contains("fortnite")||n.Contains("cs2")||n.Contains("minecraft")||n.Contains("gta")||n.Contains("elden")||n.Contains("overwatch")||n.Contains("apex")||n.Contains("rocketleague")||n.Contains("r5apex")||n.Contains("game");
    }
    private void StopPresentMon()
    {
        try{if(presentMon!=null&&!presentMon.HasExited)presentMon.Kill(true);}catch{}
        presentMon?.Dispose();presentMon=null;presentReader?.Dispose();presentReader=null;
    }
    private void Overlay_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.OriginalSource is not System.Windows.Controls.Button)
        {
            DragMove();
        }
    }

    private void UpdateStats(object? sender, EventArgs e)
    {
        try
        {
            Cpu.Text = $"{cpuCounter?.NextValue():F0}";
        }
        catch
        {
            Cpu.Text = "N/A";
        }

        try
        {
            Ram.Text = $"{ramCounter?.NextValue():F0}";
        }
        catch
        {
            Ram.Text = "N/A";
        }

        lock(telemetryLock)
        {
            Fps.Text=fps>=0?fps.ToString():"N/A";
            Low1.Text=onePercentLow>=0?onePercentLow.ToString("F0"):"N/A";
            Gpu.Text=gpuBusy>=0?gpuBusy.ToString("F1")+" ms":"N/A";
            Frame.Text=frameTime>=0?frameTime.ToString("F1")+" ms":"N/A";
        }
        State.Text=telemetryState;
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }
}