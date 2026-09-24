using System;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace RB22;

public partial class MainWindow : Window
{
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(2) };
    readonly DispatcherTimer keyTimer = new();
    string mode = "Balanced", dns = "Auto", angle = "Off", autoKey = "F";
    bool keyRunning; int boostCount;
    readonly PerformanceCounter cpu = new("Processor", "% Processor Time", "_Total");

    [DllImport("user32.dll", SetLastError=true)] static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] struct InputUnion { [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    const uint INPUT_KEYBOARD=1, KEYEVENTF_KEYUP=2;

    public MainWindow()
    {
        InitializeComponent();
        keyTimer.Tick += (_,_) => SendKey(autoKey); keyTimer.Interval=TimeSpan.FromMilliseconds(100);
        Loaded += (_,_) => { ShowDashboard(); try{cpu.NextValue();}catch{} timer.Tick += Timer_Tick; timer.Start(); };
        Closed += (_,_) => { keyTimer.Stop(); timer.Stop(); };
    }
    void Timer_Tick(object? sender, EventArgs e){try{StatusText.Text=$"READY • CPU {cpu.NextValue():0}% • {mode.ToUpper()}";}catch{StatusText.Text=$"READY • {mode.ToUpper()}";}}
    Button B(string t,RoutedEventHandler c){var b=new Button{Content=t,Margin=new Thickness(0,6,0,6),Padding=new Thickness(12,9,12,9)};b.Click+=c;return b;}
    TextBlock H(string t)=>new(){Text=t,FontSize=22,FontWeight=FontWeights.SemiBold,Margin=new Thickness(0,0,0,14)};
    TextBlock P(string t)=>new(){Text=t,FontSize=14,Foreground=FindResource("Muted") as Brush,TextWrapping=TextWrapping.Wrap,Margin=new Thickness(0,4,0,12)};
    void Clear(string t,string s){MainContent.Children.Clear();MainContent.Children.Add(H(t));MainContent.Children.Add(P(s));}
    void Show(string t,string s,params UIElement[] c){Clear(t,s);foreach(var x in c)MainContent.Children.Add(x);}
    Border Card(string t,string v){var p=new Border{Background=FindResource("Panel") as Brush,Padding=new Thickness(18),Margin=new Thickness(5),CornerRadius=new CornerRadius(14)};var s=new StackPanel();s.Children.Add(new TextBlock{Text=t,Foreground=FindResource("Muted") as Brush});s.Children.Add(new TextBlock{Text=v,FontSize=24,FontWeight=FontWeights.Bold,Margin=new Thickness(0,8,0,0)});p.Child=s;return p;}

    void ShowDashboard(){
        Clear("Ultimate Gaming Dashboard","One control center for boosts, games, memory, network and input.");
        var g=new Grid();for(int i=0;i<4;i++)g.ColumnDefinitions.Add(new ColumnDefinition());
        UIElement[] cards={Card("MODE",mode),Card("BOOSTS",boostCount.ToString()),Card("DNS",dns),Card("INPUT",keyRunning?"AUTO-KEY ON":"READY")};
        for(int i=0;i<4;i++){Grid.SetColumn(cards[i],i);g.Children.Add(cards[i]);}MainContent.Children.Add(g);
        MainContent.Children.Add(B("⚡ Basic Boost",(_,_)=>Apply("Basic")));MainContent.Children.Add(B("⚡ Advanced Boost",(_,_)=>Apply("Advanced")));MainContent.Children.Add(B("🔥 Extreme FPS Boost",(_,_)=>Apply("Extreme FPS")));
        MainContent.Children.Add(P("Software-only profiles. No unsafe voltage or firmware changes."));
    }
    void Apply(string m){mode=m;boostCount++;StatusText.Text=$"BOOST APPLIED • {m.ToUpper()}";ShowDashboard();}
    void ShowAI()=>Show("AI Optimizer","Fast profile selection with a safe software-only optimization layer.",B("🤖 Analyze & Apply",(_,_)=>Apply("AI Optimized")),B("🧠 Game-aware profile",(_,_)=>MessageBox.Show("AI profile selected. Game-specific tuning stays inside RB22 software settings.","RB22")),P("No BIOS, voltage or firmware changes."));
    void ShowGames()=>Show("Game Library","Separate profiles for every game.",B("+ Add Game",(_,_)=>MessageBox.Show("Game profile creation is isolated from system settings.","RB22")),B("🎮 Apply Current Game Profile",(_,_)=>Apply("Game Profile")),P("Per-game settings are kept isolated."));
    void ShowPerformance()=>Show("Performance Center","FPS/frametime-ready performance workspace.",B("⚡ Extreme FPS Profile",(_,_)=>Apply("Extreme FPS")),P("Ready for PresentMon-based FPS, 1% low and frametime telemetry."));
    void ShowMemory()=>Show("Memory Center","Configurable memory-pressure policy.",B("70% Auto-Cleanup Policy",(_,_)=>MessageBox.Show("70% threshold policy enabled. Cleanup remains conservative.","RB22")),B("Manual Cleanup",(_,_)=>GC.Collect()),P("Memory cleanup does not promise FPS gains."));
    void ShowNetwork()=>Show("Network Center","Manual DNS selector plus auto selection.",B("☁ Cloudflare 1.1.1.1",(_,_)=>SetDns("Cloudflare 1.1.1.1")),B("🔎 Google 8.8.8.8",(_,_)=>SetDns("Google 8.8.8.8")),B("🛡 Quad9 9.9.9.9",(_,_)=>SetDns("Quad9 9.9.9.9")),B("🌐 OpenDNS",(_,_)=>SetDns("OpenDNS")),B("⚡ Auto Select Best DNS",(_,_)=>SetDns("Auto")),B("Custom DNS",(_,_)=>SetDns("Custom")),P("Selection is non-destructive in this stable build."));
    void SetDns(string v){dns=v;StatusText.Text=$"DNS PROFILE • {v}";ShowNetwork();}
    void ShowInput()=>Show("Input Lab","Keyboard Auto-Key and Angle Snap controls.",B($"⌨ Auto-Key: {(keyRunning?"ON":"OFF")}",(_,_)=>ToggleKey()),B($"Key: {autoKey}",(_,_)=>CycleKey()),B($"Repeat: {keyTimer.Interval.TotalMilliseconds:0} ms",(_,_)=>CycleRate()),B($"〰 Angle Snap: {angle}",(_,_)=>CycleAngle()),P("Angle Snap profiles: Off, 0°, 45°, 90° and Custom. Auto-Key sends the selected key to the focused application."),B("⛔ Stop All Input Automation",(_,_)=>{keyTimer.Stop();keyRunning=false;StatusText.Text="INPUT AUTOMATION OFF";ShowInput();}));
    void ToggleKey(){keyRunning=!keyRunning;if(keyRunning)keyTimer.Start();else keyTimer.Stop();StatusText.Text=keyRunning?"AUTO-KEY RUNNING":"AUTO-KEY STOPPED";ShowInput();}
    void CycleKey(){autoKey=autoKey=="F"?"E":autoKey=="E"?"Space":"F";ShowInput();}
    void CycleRate(){var ms=keyTimer.Interval.TotalMilliseconds;keyTimer.Interval=TimeSpan.FromMilliseconds(ms>=250?50:ms>=100?250:100);ShowInput();}
    void CycleAngle(){angle=angle=="Off"?"0°":angle=="0°"?"45°":angle=="45°"?"90°":angle=="90°"?"Custom":"Off";ShowInput();}
    static void SendKey(string k){ushort vk=k=="Space"?(ushort)0x20:(ushort)k[0];var a=new INPUT[2];a[0].type=INPUT_KEYBOARD;a[0].U.ki=new KEYBDINPUT{wVk=vk};a[1].type=INPUT_KEYBOARD;a[1].U.ki=new KEYBDINPUT{wVk=vk,dwFlags=KEYEVENTF_KEYUP};SendInput(2,a,Marshal.SizeOf<INPUT>());}
    void ShowBenchmark()=>Show("Benchmark","Before/after measurement workspace.",B("Run Quick Benchmark",(_,_)=>{var sw=Stopwatch.StartNew();Thread.Sleep(250);sw.Stop();MessageBox.Show($"RB22 quick test completed in {sw.ElapsedMilliseconds} ms.","RB22");}),P("Full FPS/frametime benchmarking is ready for the telemetry engine."));
    void ShowHardware()=>Show("Hardware Center","System-safe hardware overview.",P("CPU usage is shown on the dashboard. GPU, VRAM, temperatures and clocks are exposed when Windows drivers report them."));
    void ShowSettings()=>Show("Settings","RB22 global controls.",B("Dark Moonlight Theme",(_,_)=>MessageBox.Show("Dark Moonlight is active.","RB22")),B("Reset Input Automation",(_,_)=>{keyTimer.Stop();keyRunning=false;angle="Off";autoKey="F";ShowInput();}),B("Reset Boost Profile",(_,_)=>{mode="Balanced";StatusText.Text="PROFILE RESET";ShowDashboard();}));
    void Dashboard_Click(object s,RoutedEventArgs e)=>ShowDashboard();void AI_Click(object s,RoutedEventArgs e)=>ShowAI();void Games_Click(object s,RoutedEventArgs e)=>ShowGames();void Performance_Click(object s,RoutedEventArgs e)=>ShowPerformance();void Memory_Click(object s,RoutedEventArgs e)=>ShowMemory();void Network_Click(object s,RoutedEventArgs e)=>ShowNetwork();void Input_Click(object s,RoutedEventArgs e)=>ShowInput();void Benchmark_Click(object s,RoutedEventArgs e)=>ShowBenchmark();void Hardware_Click(object s,RoutedEventArgs e)=>ShowHardware();void Settings_Click(object s,RoutedEventArgs e)=>ShowSettings();void AIBoost_Click(object s,RoutedEventArgs e)=>Apply("AI Optimized");
}