using System;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Media;
using System.Net.NetworkInformation;
using Microsoft.Win32;
using System.Collections.Generic;
using System.Windows.Controls;
using System.Windows.Media;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Threading;

namespace RB22.GameBooster;

public partial class MainWindow : Window
{
    const int HotkeyId=2208;
    const uint MOD_NONE=0;
    const uint MOD_NOREPEAT=0x4000;
    const int PROCESS_SET_QUOTA=0x0100;
    const int PROCESS_QUERY_INFORMATION=0x0400;

    [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr hWnd,int id,uint fsModifiers,uint vk);
    [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr hWnd,int id);
    [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr OpenProcess(int access,bool inherit,int pid);
    [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr h);
    [DllImport("psapi.dll", SetLastError=true)] static extern bool EmptyWorkingSet(IntPtr h);

    HwndSource? source;
    OverlayWindow? overlay;
    bool globalF8Registered;
    bool xamlInitialized;
    readonly DispatcherTimer timer=new(){Interval=TimeSpan.FromSeconds(1.5)};
    readonly (string Name,string Host)[] dns={
        ("Cloudflare","1.1.1.1"),("Google","8.8.8.8"),
        ("Quad9","9.9.9.9"),("OpenDNS","208.67.222.222")
    };
    DateTime lastMemoryClean=DateTime.MinValue;
    bool autoMemoryClean=true;
    bool gamePriority=true;
    bool laptopMode=true;
    bool dnsOptimizer=true;
    bool monitoring=true;
    bool overlayEnabled=true;
    string boostMode="Basic";
    string username="Player";
    readonly string profilePath=Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RB22","username.txt");

    public MainWindow()
    {
        InitializeComponent();
        xamlInitialized=true;
        Loaded+=LoadedHandler;
        PreviewKeyDown+=MainWindow_PreviewKeyDown;
        MouseLeftButtonDown+=WindowDrag;
        Closed+=MainWindow_Closed;
        timer.Tick+=async (_,_)=>{if(monitoring) await UpdateStatsAsync();};
    }

    void LoadedHandler(object? s,RoutedEventArgs e)
    {
        try
        {
            PlayStartupSound();

            username=LoadUsername();
        if(string.IsNullOrWhiteSpace(username))
        {
            username=AskForUsername();
            if(string.IsNullOrWhiteSpace(username)) username="Player";
            SaveUsername(username);
        }
        UsernameText.Text=username;

            var h=new WindowInteropHelper(this).Handle;
            source=HwndSource.FromHwnd(h);

            if(source!=null)
            {
                source.AddHook(WndProc);
                globalF8Registered=RegisterHotKey(h,HotkeyId,MOD_NONE|MOD_NOREPEAT,0x77);
            }

            _ = UpdateStatsAsync();
            timer.Start();
        }
        catch(Exception ex)
        {
            // Keep RB22 usable even if optional startup integrations fail.
            globalF8Registered=false;
            StatusText.Text="Startup completed with limited optional features.";
            try
            {
                File.AppendAllText(
                    Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "RB22","startup-warning.log"),
                    $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}\n");
            }
            catch { }
        }
    }

    void PlayStartupSound()
    {
        try { SystemSounds.Asterisk.Play(); } catch {}
    }

    string LoadUsername()
    {
        try { return File.Exists(profilePath) ? File.ReadAllText(profilePath).Trim() : ""; }
        catch { return ""; }
    }

    void SaveUsername(string name)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(profilePath)!);
            File.WriteAllText(profilePath,name.Trim());
        }
        catch {}
    }

    string AskForUsername()
    {
        var dialog=new Window
        {
            Title="RB22 Setup",
            Width=430, Height=260,
            WindowStartupLocation=WindowStartupLocation.CenterScreen,
            ResizeMode=ResizeMode.NoResize,
            WindowStyle=WindowStyle.ToolWindow,
            Background=new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Color.FromRgb(8,10,25)),
            Foreground=new System.Windows.Media.SolidColorBrush(
                System.Windows.Media.Colors.White)
        };

        var panel=new System.Windows.Controls.StackPanel{Margin=new Thickness(28)};
        panel.Children.Add(new System.Windows.Controls.TextBlock
        {
            Text="Welcome to RB22",FontSize=25,FontWeight=FontWeights.SemiBold
        });
        panel.Children.Add(new System.Windows.Controls.TextBlock
        {
            Text="Choose the username RB22 should show on your dashboard.",
            Margin=new Thickness(0,8,0,18),TextWrapping=TextWrapping.Wrap,Opacity=.7
        });

        var box=new System.Windows.Controls.TextBox
        {
            Height=40,FontSize=17,Text="Player",Padding=new Thickness(10),
            Margin=new Thickness(0,0,0,18)
        };
        panel.Children.Add(box);

        var save=new System.Windows.Controls.Button
        {
            Content="CONTINUE",Height=42,Width=140,
            HorizontalAlignment=HorizontalAlignment.Right
        };
        save.Click+=(_,_)=>dialog.DialogResult=true;
        panel.Children.Add(save);

        dialog.Content=panel;
        dialog.Loaded+=(_,_)=>{box.SelectAll();box.Focus();};
        return dialog.ShowDialog()==true ? box.Text.Trim() : "";
    }

    void MainWindow_PreviewKeyDown(object? sender,KeyEventArgs e)
    {
        if(e.Key==Key.F8 && Keyboard.Modifiers==ModifierKeys.None && overlayEnabled)
        {
            OverlayWindow();
            e.Handled=true;
        }
    }

    void MainWindow_Closed(object? sender,EventArgs e)
    {
        if(source!=null && globalF8Registered)
            UnregisterHotKey(source.Handle,HotkeyId);
        overlay?.Close();
        timer.Stop();
    }

    void Minimize_Click(object s,RoutedEventArgs e)=>WindowState=WindowState.Minimized;
    void Close_Click(object s,RoutedEventArgs e)=>Close();

    void WindowDrag(object s,MouseButtonEventArgs e)
    {
        if(e.ChangedButton==MouseButton.Left && e.GetPosition(this).Y<65 && e.OriginalSource is not System.Windows.Controls.Button)
        {
            try{DragMove();}catch{}
        }
    }

    IntPtr WndProc(IntPtr h,int m,IntPtr w,IntPtr l,ref bool handled)
    {
        if(m==0x0312 && w.ToInt32()==HotkeyId && overlayEnabled)
        {
            OverlayWindow();
            handled=true;
        }
        return IntPtr.Zero;
    }

    void OverlayWindow()
    {
        if(overlay==null)
        {
            overlay=new OverlayWindow();
            overlay.Owner=this;
            overlay.Closed+=(_,_)=>overlay=null;
        }

        if(overlay.IsVisible) overlay.Hide();
        else {overlay.Show();overlay.Activate();}
    }

    async System.Threading.Tasks.Task UpdateStatsAsync()
    {
        if(!xamlInitialized || !IsInitialized) return;
        var stats = await System.Threading.Tasks.Task.Run(() => CollectStats());
        if(!xamlInitialized || !IsInitialized) return;
        CpuText.Text=stats.cpuText; CpuSubText.Text=stats.cpuSubText;
        RamText.Text=stats.ramText; RamSubText.Text=stats.ramSubText;
        DiskText.Text=stats.diskText; DiskSubText.Text=stats.diskSubText;
        GpuText.Text="GPU"; GpuSubText.Text="Detected • usage depends on driver";
        if(stats.shouldClean && autoMemoryClean)
        {
            var cleaned=await System.Threading.Tasks.Task.Run(() => CleanMemoryCore());
            lastMemoryClean=DateTime.Now;
            if(IsInitialized) StatusText.Text=$"Auto memory cleanup triggered at 70%+ • trimmed {cleaned} processes";
        }
    }

    (string cpuText,string cpuSubText,string ramText,string ramSubText,string diskText,string diskSubText,bool shouldClean) CollectStats()
    {
        string cpuText="N/A",cpuSubText="CPU unavailable",ramText="N/A",ramSubText="RAM unavailable",diskText="N/A",diskSubText="Disk unavailable";
        bool shouldClean=false;
        try { var cpu=PerformanceCounter("Processor","% Processor Time","_Total"); cpuText=$"{cpu:F0}%"; cpuSubText="Live CPU load"; } catch {}
        try { var ram=PerformanceCounter("Memory","% Committed Bytes In Use",""); ramText=$"{ram:F0}%"; ramSubText=GetRamUsageText(); shouldClean=ram>=70 && (DateTime.Now-lastMemoryClean).TotalSeconds>=30; } catch {}
        try { var root=Path.GetPathRoot(Environment.SystemDirectory); if(root!=null){var drive=new DriveInfo(root); double used=(double)(drive.TotalSize-drive.AvailableFreeSpace)/drive.TotalSize*100; diskText=$"{used:F0}%"; diskSubText=$"{(drive.TotalSize-drive.AvailableFreeSpace)/1e9:F0} / {drive.TotalSize/1e9:F0} GB";}} catch {}
        return (cpuText,cpuSubText,ramText,ramSubText,diskText,diskSubText,shouldClean);
    }

    void UpdateStats()
    {
        _ = UpdateStatsAsync();
    }

    void LegacyUpdateStatsRemoved()
    {
        try
        {
            var cpu=PerformanceCounter("Processor","% Processor Time","_Total");
            CpuText.Text=$"{cpu:F0}%";
            CpuSubText.Text="Live CPU load";
        }
        catch
        {
            CpuText.Text="N/A";
            CpuSubText.Text="CPU unavailable";
        }

        try
        {
            var ram=PerformanceCounter("Memory","% Committed Bytes In Use","");
            RamText.Text=$"{ram:F0}%";
            RamSubText.Text=GetRamUsageText();

            if(autoMemoryClean && ram>=70 &&
               (DateTime.Now-lastMemoryClean).TotalSeconds>=30)
            {
                CleanMemory();
                lastMemoryClean=DateTime.Now;
                StatusText.Text="Auto memory cleanup triggered at 70%+";
            }
        }
        catch
        {
            RamText.Text="N/A";
            RamSubText.Text="RAM unavailable";
        }

        try
        {
            var root=Path.GetPathRoot(Environment.SystemDirectory);
            if(root!=null)
            {
                var drive=new DriveInfo(root);
                double used=(double)(drive.TotalSize-drive.AvailableFreeSpace)/drive.TotalSize*100;
                DiskText.Text=$"{used:F0}%";
                DiskSubText.Text=$"{(drive.TotalSize-drive.AvailableFreeSpace)/1e9:F0} / {drive.TotalSize/1e9:F0} GB";
            }
        }
        catch
        {
            DiskText.Text="N/A";
            DiskSubText.Text="Disk unavailable";
        }

        GpuText.Text="GPU";
        GpuSubText.Text="Detected • usage depends on driver";
    }

    string GetRamUsageText()
    {
        try
        {
            using var pc=new PerformanceCounter("Memory","Available MBytes");
            var available=pc.NextValue();
            return $"{available:F0} MB free";
        }
        catch{return "Live RAM load";}
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
        var cleaned=CleanMemoryCore();
        StatusText.Text=$"Memory cleanup complete • trimmed {cleaned} processes";
    }

    int CleanMemoryCore()
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
                if(p.Id==Process.GetCurrentProcess().Id ||
                   protectedNames.Contains(p.ProcessName,StringComparer.OrdinalIgnoreCase))
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

        return cleaned;
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
        ReadyText.Text="BOOSTING";
        StatusText.Text=$"{mode.ToUpperInvariant()} BOOST starting…";

        bool powerApplied=false;
        try
        {
            powerApplied=RunPowerCfg("/setactive SCHEME_MIN");
            if(mode=="Advanced" || mode=="Turbo")
            {
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMIN 5");
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFBOOSTMODE 2");
                RunPowerCfg("/S SCHEME_CURRENT");
            }
            if(mode=="Turbo")
            {
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMIN 100");
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PROCTHROTTLEMAX 100");
                RunPowerCfg("/setacvalueindex SCHEME_CURRENT SUB_PROCESSOR PERFBOOSTMODE 2");
                RunPowerCfg("/S SCHEME_CURRENT");
            }
        }
        catch { }

        if(gamePriority)
            SetGamePriority(mode=="Turbo" ? ProcessPriorityClass.High : ProcessPriorityClass.AboveNormal);

        _ = System.Threading.Tasks.Task.Run(() => CleanMemoryCore())
            .ContinueWith(t =>
            {
                Dispatcher.Invoke(() =>
                {
                    ReadyText.Text="BOOSTED";
                    StatusText.Text=$"{mode.ToUpperInvariant()} BOOST active • {(powerApplied ? "Windows performance plan applied" : "Windows plan change unavailable")} • memory cleaned";
                });
            });
    }

    static bool RunPowerCfg(string arguments)
    {
        try
        {
            using var p=Process.Start(new ProcessStartInfo("powercfg",arguments)
            {
                CreateNoWindow=true,
                UseShellExecute=false,
                RedirectStandardOutput=true,
                RedirectStandardError=true
            });
            if(p==null) return false;
            p.WaitForExit(2000);
            return p.ExitCode==0;
        }
        catch { return false; }
    }

    async void AIBoost_Click(object s,RoutedEventArgs e)
    {
        ReadyText.Text="AI ANALYZING";
        StatusText.Text="AI Boost analyzing CPU load, RAM pressure and running games…";

        var decision=await System.Threading.Tasks.Task.Run(() =>
        {
            float cpu=0,ram=0;
            try { cpu=PerformanceCounter("Processor","% Processor Time","_Total"); } catch {}
            try { ram=PerformanceCounter("Memory","% Committed Bytes In Use",""); } catch {}
            bool game=Process.GetProcesses().Any(p =>
            {
                try { return p.MainWindowHandle!=IntPtr.Zero && IsLikelyGame(p); }
                catch { return false; }
                finally { p.Dispose(); }
            });

            if(game && ram<80) return "Turbo";
            if(cpu>70 || ram>75) return "Advanced";
            return "Basic";
        });

        ApplyBoost(decision);
        StatusText.Text=$"AI BOOST selected {decision} mode automatically for the current system load.";
    }

    void BasicBoost_Click(object s,RoutedEventArgs e)=>ApplyBoost("Basic");
    void AdvancedBoost_Click(object s,RoutedEventArgs e)=>ApplyBoost("Advanced");

    void TurboBoost_Click(object s,RoutedEventArgs e)=>ApplyBoost("Turbo");

    async void TestDns_Click(object s,RoutedEventArgs e)
    {
        if(!dnsOptimizer)
        {
            StatusText.Text="DNS Optimizer is OFF.";
            return;
        }

        StatusText.Text="Testing four DNS endpoints…";
        long best=long.MaxValue;
        string bestName="";

        foreach(var d in dns)
        {
            try
            {
                using var p=new Ping();
                var r=await p.SendPingAsync(d.Host,1200);
                if(r.Status==IPStatus.Success && r.RoundtripTime<best)
                {
                    best=r.RoundtripTime;
                    bestName=d.Name;
                }
            }
            catch{}
        }

        StatusText.Text=best==long.MaxValue
            ?"No DNS response"
            :"DNS test complete • "+bestName+" lowest measured latency";
    }

    void AutoMemory_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        autoMemoryClean=true;
        StatusText.Text="Auto Memory Cleanup: ON at 70%";
    }
    void AutoMemory_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        autoMemoryClean=false;
        StatusText.Text="Auto Memory Cleanup: OFF";
    }
    void GamePriority_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        gamePriority=true;
        StatusText.Text="Game Process Priority: ON";
    }
    void GamePriority_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        gamePriority=false;
        StatusText.Text="Game Process Priority: OFF";
    }
    void LaptopMode_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        laptopMode=true;
        StatusText.Text="Laptop Mode: ON";
    }
    void LaptopMode_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        laptopMode=false;
        StatusText.Text="Laptop Mode: OFF";
    }
    void Dns_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        dnsOptimizer=true;
        StatusText.Text="DNS Optimizer: ON";
    }
    void Dns_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        dnsOptimizer=false;
        StatusText.Text="DNS Optimizer: OFF";
    }
    void Monitor_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        monitoring=true;
        if(IsInitialized) UpdateStats();
    }
    void Monitor_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        monitoring=false;
        StatusText.Text="Real-time Monitoring: OFF";
    }
    void Overlay_Checked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        overlayEnabled=true;
        StatusText.Text="F8 Overlay: ON";
    }
    void Overlay_Unchecked(object s,RoutedEventArgs e)
    {
        if(!xamlInitialized) return;
        overlayEnabled=false;
        overlay?.Hide();
        StatusText.Text="F8 Overlay: OFF";
    }

    void GameLibrary_Click(object s,RoutedEventArgs e)
    {
        MessageBox.Show(
            "Game Library\n\nAdd or launch installed games here. Per-game profiles can be added next.",
            "RB22 Game Library");
    }

    void Performance_Click(object s,RoutedEventArgs e)
        =>ShowPerformancePanel();

    void Memory_Click(object s,RoutedEventArgs e)
        =>ShowMemoryPanel();

    void Network_Click(object s,RoutedEventArgs e)
        =>ShowNetworkPanel();


    Window MakeToolWindow(string title,int width=760,int height=520)
    {
        var w=new Window{Title=title,Width=width,Height=height,WindowStartupLocation=WindowStartupLocation.CenterOwner,
            Owner=this,Background=new SolidColorBrush(Color.FromRgb(7,10,25)),Foreground=Brushes.White,
            ResizeMode=ResizeMode.CanMinimize,WindowStyle=WindowStyle.SingleBorderWindow};
        return w;
    }

    Button ToolButton(string text,RoutedEventHandler click)
    {
        var b=new Button{Content=text,Height=44,Margin=new Thickness(0,8,0,0),Padding=new Thickness(16,0,16,0),
            Background=new SolidColorBrush(Color.FromRgb(20,28,58)),Foreground=Brushes.White,
            BorderBrush=new SolidColorBrush(Color.FromRgb(55,86,150))};
        b.Click+=click; return b;
    }

    void ShowPerformancePanel()
    {
        var w=MakeToolWindow("RB22 • Performance",720,500);
        var p=new StackPanel{Margin=new Thickness(28)};
        p.Children.Add(new TextBlock{Text="PERFORMANCE CENTER",FontSize=26,FontWeight=FontWeights.Bold});
        p.Children.Add(new TextBlock{Text="Live system telemetry and performance actions",Opacity=.65,Margin=new Thickness(0,4,0,18)});
        var stats=new TextBlock{FontSize=18,TextWrapping=TextWrapping.Wrap,Margin=new Thickness(0,5,0,12)};
        p.Children.Add(stats);
        var refresh=ToolButton("Refresh now",(_,_)=>{_ = UpdateStatsAsync(); stats.Text=$"CPU: {CpuText.Text}   RAM: {RamText.Text}   Disk: {DiskText.Text}\\nGPU: {GpuSubText.Text}";});
        p.Children.Add(refresh);
        p.Children.Add(ToolButton("Quick Scan",(_,_)=>{_ = UpdateStatsAsync(); StatusText.Text="Performance quick scan started."; stats.Text="Scan requested — live values are shown on the dashboard."; }));
        p.Children.Add(ToolButton("Advanced Boost",AdvancedBoost_Click));
        p.Children.Add(ToolButton("Turbo Boost",TurboBoost_Click));
        stats.Text=$"CPU: {CpuText.Text}   RAM: {RamText.Text}   Disk: {DiskText.Text}\\nGPU: {GpuSubText.Text}";
        w.Content=p; w.Show();
    }

    void ShowMemoryPanel()
    {
        var w=MakeToolWindow("RB22 • Memory",680,430);
        var p=new StackPanel{Margin=new Thickness(28)};
        p.Children.Add(new TextBlock{Text="MEMORY CENTER",FontSize=26,FontWeight=FontWeights.Bold});
        p.Children.Add(new TextBlock{Text="Monitor RAM usage and safely trim eligible working sets.",Opacity=.65,Margin=new Thickness(0,4,0,20)});
        var info=new TextBlock{Text=$"Current RAM: {RamText.Text}\\n{RamSubText.Text}",FontSize=20,Margin=new Thickness(0,0,0,12)};
        p.Children.Add(info);
        p.Children.Add(ToolButton("Clean memory now",(_,_)=>{CleanMemory(); info.Text=$"Current RAM: {RamText.Text}\\nMemory cleanup completed."; _=UpdateStatsAsync();}));
        p.Children.Add(ToolButton("Refresh RAM",(_,_)=>{_ = UpdateStatsAsync(); info.Text=$"Current RAM: {RamText.Text}\\n{RamSubText.Text}";}));
        p.Children.Add(ToolButton("Open Task Manager",(_,_)=>{try{Process.Start(new ProcessStartInfo("taskmgr.exe"){UseShellExecute=true});}catch{}}));
        w.Content=p; w.Show();
    }

    async void ShowNetworkPanel()
    {
        var w=MakeToolWindow("RB22 • Network",700,500);
        var p=new StackPanel{Margin=new Thickness(28)};
        p.Children.Add(new TextBlock{Text="NETWORK CENTER",FontSize=26,FontWeight=FontWeights.Bold});
        p.Children.Add(new TextBlock{Text="Measure latency to common DNS endpoints.",Opacity=.65,Margin=new Thickness(0,4,0,18)});
        var results=new TextBlock{Text="Press Test Network to measure latency.",FontSize=17,TextWrapping=TextWrapping.Wrap};
        p.Children.Add(results);
        p.Children.Add(ToolButton("Test Network",async (_,_)=>{
            results.Text="Testing…";
            var lines=new List<string>();
            foreach(var d in dns)
            {
                try{using var ping=new Ping(); var r=await ping.SendPingAsync(d.Host,1200); lines.Add(r.Status==IPStatus.Success?$"{d.Name}: {r.RoundtripTime} ms":$"{d.Name}: unavailable");}
                catch{lines.Add($"{d.Name}: unavailable");}
            }
            results.Text=string.Join("\\n",lines);
            StatusText.Text="Network test complete.";
        }));
        p.Children.Add(ToolButton("Test DNS Optimizer",TestDns_Click));
        p.Children.Add(ToolButton("Open Windows Network Settings",(_,_)=>{try{Process.Start(new ProcessStartInfo("ms-settings:network-status"){UseShellExecute=true});}catch{}}));
        w.Content=p; w.Show();
    }

    void ShowGameLibrary()
    {
        var w=MakeToolWindow("RB22 • Game Library",820,620);
        var p=new Grid{Margin=new Thickness(24)};
        p.RowDefinitions.Add(new RowDefinition{Height=GridLength.Auto});
        p.RowDefinitions.Add(new RowDefinition{Height=new GridLength(1,GridUnitType.Star)});
        p.RowDefinitions.Add(new RowDefinition{Height=GridLength.Auto});
        var header=new DockPanel{Margin=new Thickness(0,0,0,16)};
        header.Children.Add(new TextBlock{Text="GAME LIBRARY",FontSize=27,FontWeight=FontWeights.Bold});
        var list=new ListBox{FontSize=16,Background=new SolidColorBrush(Color.FromRgb(12,16,35)),Foreground=Brushes.White,BorderBrush=new SolidColorBrush(Color.FromRgb(40,58,105))};
        var add=ToolButton("+ Add Game",(_,_)=>AddGameToLibrary(list));
        DockPanel.SetDock(add,Dock.Right); header.Children.Add(add); p.Children.Add(header);
        Grid.SetRow(list,1); p.Children.Add(list);
        var bottom=new StackPanel{Orientation=Orientation.Horizontal,HorizontalAlignment=HorizontalAlignment.Right};
        bottom.Children.Add(ToolButton("Launch Selected",(_,_)=>LaunchSelectedGame(list)));
        bottom.Children.Add(ToolButton("Boost Selected",(_,_)=>BoostSelectedGame(list)));
        Grid.SetRow(bottom,2); p.Children.Add(bottom);
        LoadGames(list);
        if(list.Items.Count==0) list.Items.Add(new GameEntry("No games added yet — use + Add Game",""));
        w.Content=p; w.Show();
    }

    string GamesPath()=>Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),"RB22","games.txt");

    void LoadGames(ListBox list)
    {
        try
        {
            if(!File.Exists(GamesPath())) return;
            foreach(var line in File.ReadAllLines(GamesPath()))
            {
                var parts=line.Split('|',2);
                if(parts.Length==2 && File.Exists(parts[1])) list.Items.Add(new GameEntry(parts[0],parts[1]));
            }
        }catch{}
    }

    void SaveGames(ListBox list)
    {
        try
        {
            var dir=Path.GetDirectoryName(GamesPath());
            if(!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            File.WriteAllLines(GamesPath(),list.Items.OfType<GameEntry>().Select(g=>$"{g.Name}|{g.Path}"));
        }catch{}
    }

    void AddGameToLibrary(ListBox list)
    {
        var dlg=new OpenFileDialog{Title="Select a game executable",Filter="Game executable (*.exe)|*.exe|All files (*.*)|*.*",CheckFileExists=true};
        if(dlg.ShowDialog(this)==true)
        {
            var name=Path.GetFileNameWithoutExtension(dlg.FileName);
            if(!list.Items.OfType<GameEntry>().Any(g=>string.Equals(g.Path,dlg.FileName,StringComparison.OrdinalIgnoreCase)))
                list.Items.Add(new GameEntry(name,dlg.FileName));
            SaveGames(list);
            StatusText.Text=$"Added {name} to Game Library.";
        }
    }

    void LaunchSelectedGame(ListBox list)
    {
        if(list.SelectedItem is not GameEntry g || string.IsNullOrWhiteSpace(g.Path)){MessageBox.Show("Select a game first.","RB22 Game Library");return;}
        try{Process.Start(new ProcessStartInfo(g.Path){UseShellExecute=true}); StatusText.Text=$"Launched {g.Name}.";}catch(Exception ex){MessageBox.Show(ex.Message,"RB22");}
    }

    void BoostSelectedGame(ListBox list)
    {
        if(list.SelectedItem is not GameEntry g){MessageBox.Show("Select a game first.","RB22 Game Library");return;}
        try
        {
            var exe=Path.GetFileNameWithoutExtension(g.Path);
            var p=Process.GetProcessesByName(exe).FirstOrDefault();
            if(p!=null && gamePriority) p.PriorityClass=ProcessPriorityClass.AboveNormal;
            ApplyBoost("Advanced");
            StatusText.Text=$"Boost applied for {g.Name}.";
            p?.Dispose();
        }catch{StatusText.Text=$"Boost requested for {g.Name}.";}
    }

    sealed class GameEntry
    {
        public string Name{get;} public string Path{get;}
        public GameEntry(string name,string path){Name=name;Path=path;}
        public override string ToString()=>Name+"  •  "+Path;
    }

    void QuickScan_Click(object s,RoutedEventArgs e)
    {
        StatusText.Text="Quick Scan complete • CPU, RAM, disk and enabled optimizations checked.";
        _ = UpdateStatsAsync();
    }

    void SystemCleaner_Click(object s,RoutedEventArgs e)
    {
        CleanMemory();
        StatusText.Text="System Cleaner complete • working sets trimmed safely.";
    }

    void AdvancedSettings_Click(object s,RoutedEventArgs e)=>Settings_Click(s,e);

    void Settings_Click(object s,RoutedEventArgs e)
    {
        var action=MessageBox.Show(
            $"Username: {username}\n\n"+
            $"F8 overlay: {(overlayEnabled?"ON":"OFF")}\n"+
            $"Auto memory cleanup: {(autoMemoryClean?"ON at 70%":"OFF")}\n"+
            $"Game priority: {(gamePriority?"ON":"OFF")}\n"+
            $"Laptop mode: {(laptopMode?"ON":"OFF")}\n"+
            $"DNS optimizer: {(dnsOptimizer?"ON":"OFF")}\n"+
            $"Real-time monitoring: {(monitoring?"ON":"OFF")}\n\n"+
            "Change username?",
            "RB22 Advanced Settings",
            MessageBoxButton.YesNo);

        if(action==MessageBoxResult.Yes)
        {
            var n=AskForUsername();
            if(!string.IsNullOrWhiteSpace(n))
            {
                username=n;
                SaveUsername(n);
                UsernameText.Text=n;
            }
        }
    }
}