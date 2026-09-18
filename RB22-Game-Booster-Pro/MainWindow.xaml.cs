using System;
using System.Diagnostics;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Threading;

namespace RB22.GameBooster;

public partial class MainWindow : Window
{
    const int HotkeyId=2208;
    const uint MOD_NONE=0;
    const int PROCESS_SET_QUOTA=0x0100;
    const int PROCESS_QUERY_INFORMATION=0x0400;

    [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr hWnd,int id,uint fsModifiers,uint vk);
    [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr hWnd,int id);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(int access,bool inherit,int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
    [DllImport("psapi.dll", SetLastError=true)] static extern bool EmptyWorkingSet(IntPtr h);

    HwndSource? source;
    readonly DispatcherTimer timer=new(){Interval=TimeSpan.FromSeconds(1)};
    readonly (string Name,string Host)[] dns={
        ("Cloudflare","1.1.1.1"),("Google","8.8.8.8"),
        ("Quad9","9.9.9.9"),("OpenDNS","208.67.222.222")
    };
    DateTime lastMemoryClean=DateTime.MinValue;
    bool autoMemoryClean=true;
    string boostMode="Basic";

    public MainWindow()
    {
        InitializeComponent();
        Loaded+=LoadedHandler;
        Closed+=(_,_)=>{if(source!=null)UnregisterHotKey(source.Handle,HotkeyId);};
        timer.Tick+=(_,_)=>UpdateStats();
        timer.Start();
    }

    void LoadedHandler(object? s,RoutedEventArgs e)
    {
        DeviceLabel.Text=Environment.Is64BitOperatingSystem?"LAPTOP/DESKTOP • x64":"Windows";
        var h=new WindowInteropHelper(this).Handle;
        source=HwndSource.FromHwnd(h);
        source.AddHook(WndProc);
        RegisterHotKey(h,HotkeyId,MOD_NONE,0x77);
        UpdateStats();
    }

    IntPtr WndProc(IntPtr h,int m,IntPtr w,IntPtr l,ref bool handled)
    {
        if(m==0x0312 && w.ToInt32()==HotkeyId){OverlayWindow();handled=true;}
        return IntPtr.Zero;
    }

    void OverlayWindow(){var o=new OverlayWindow();o.Owner=this;o.Show();}

    void UpdateStats()
    {
        try
        {
            var cpu=PerformanceCounter("Processor","% Processor Time","_Total");
            CpuText.Text=$"{cpu:F0}%";
        }
        catch{CpuText.Text="N/A";}

        try
        {
            var ram=PerformanceCounter("Memory","% Committed Bytes In Use");
            RamText.Text=$"{ram:F0}%";
            if(autoMemoryClean && ram>=70 && (DateTime.Now-lastMemoryClean).TotalSeconds>=30)
            {
                CleanMemory();
                lastMemoryClean=DateTime.Now;
                StatusText.Text="Auto memory clean triggered at 70%+";
            }
        }
        catch{RamText.Text="N/A";}

        PingText.Text="—";
        NetText.Text="DNS ready";
        MemoryModeText.Text=autoMemoryClean?"Auto memory clean: ON at 70%":"Auto memory clean: OFF";
    }

    static float PerformanceCounter(string cat,string name,string inst)
    {
        using var p=new PerformanceCounter(cat,name,inst);
        p.NextValue();
        System.Threading.Thread.Sleep(20);
        return p.NextValue();
    }

    void CleanMemory()
    {
        string[] protectedNames={
            "System","Idle","Registry","smss","csrss","wininit","winlogon","services",
            "lsass","svchost","dwm","fontdrvhost","Memory Compression","MsMpEng",
            "explorer","SearchHost","StartMenuExperienceHost","ShellExperienceHost",
            "audiodg","spoolsv","RuntimeBroker","SecurityHealthService"
        };

        int cleaned=0;
        foreach(var p in Process.GetProcesses())
        {
            try
            {
                if(p.Id==Process.GetCurrentProcess().Id || protectedNames.Contains(p.ProcessName,StringComparer.OrdinalIgnoreCase))
                    continue;
                if(p.MainWindowHandle!=IntPtr.Zero && IsLikelyGame(p)) continue;
                var h=OpenProcess(PROCESS_SET_QUOTA|PROCESS_QUERY_INFORMATION,false,p.Id);
                if(h!=IntPtr.Zero)
                {
                    if(EmptyWorkingSet(h)) cleaned++;
                    CloseHandle(h);
                }
            }
            catch{}
            finally{p.Dispose();}
        }
        StatusText.Text=$"Memory cleanup complete • trimmed {cleaned} processes";
    }

    static bool IsLikelyGame(Process p)
    {
        string n=p.ProcessName.ToLowerInvariant();
        return n.Contains("game")||n.Contains("steam")||n.Contains("epic")||
               n.Contains("roblox")||n.Contains("valorant")||n.Contains("fortnite")||
               n.Contains("cs2")||n.Contains("minecraft")||n.Contains("gta")||
               n.Contains("elden")||n.Contains("overwatch")||n.Contains("cod");
    }

    void ApplyBoost(string mode)
    {
        boostMode=mode;
        string plan=mode=="Turbo"?"SCHEME_MIN":"SCHEME_MIN";
        try
        {
            Process.Start(new ProcessStartInfo("powercfg",$"/setactive {plan}")
            {CreateNoWindow=true,UseShellExecute=false});
        }catch{}

        int priority=mode=="Turbo"?ProcessPriorityClass.High:
                     mode=="Advanced"?ProcessPriorityClass.AboveNormal:
                     ProcessPriorityClass.Normal;
        try{Process.GetCurrentProcess().PriorityClass=priority;}catch{}

        CleanMemory();
        StatusText.Text=$"{mode.ToUpperInvariant()} BOOST active • performance plan + safe memory cleanup";
    }

    void BasicBoost_Click(object s,RoutedEventArgs e)=>ApplyBoost("Basic");
    void AdvancedBoost_Click(object s,RoutedEventArgs e)=>ApplyBoost("Advanced");
    void TurboBoost_Click(object s,RoutedEventArgs e)
    {
        ApplyBoost("Turbo");
        MessageBox.Show(
            "TURBO BOOST enables the strongest safe software-side performance settings RB22 can request. Windows still controls actual CPU/GPU clocks and thermal limits; RB22 cannot safely force a CPU to a specific GHz.",
            "RB22 Turbo Boost");
    }

    async void TestDns_Click(object s,RoutedEventArgs e)
    {
        StatusText.Text="Testing four DNS endpoints…";
        long best=long.MaxValue; string bestName="";
        foreach(var d in dns)
        {
            try
            {
                using var p=new Ping();
                var r=await p.SendPingAsync(d.Host,1200);
                if(r.Status==IPStatus.Success && r.RoundtripTime<best)
                {best=r.RoundtripTime;bestName=d.Name;}
            }catch{}
        }
        PingText.Text=best==long.MaxValue?"N/A":best+" ms";
        NetText.Text=bestName==""?"No response":bestName+" lowest measured latency";
        StatusText.Text="DNS test complete.";
    }

    void Settings_Click(object s,RoutedEventArgs e)
    {
        autoMemoryClean=!autoMemoryClean;
        MessageBox.Show(
            $"Default overlay hotkey: F8\nAuto memory clean: {(autoMemoryClean?"ON at 70%":"OFF")}\nBoost modes: Basic / Advanced / Turbo\nNetwork: Cloudflare, Google, Quad9, OpenDNS\nFPS is shown only when a reliable frame source is available.",
            "RB22 Settings");
    }
}
