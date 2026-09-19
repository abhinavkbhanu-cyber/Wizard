using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace RB22;

public partial class MainWindow : Window
{
    readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(3) };
    string mode = "Balanced";
    int cpuUsage;

    public MainWindow()
    {
        InitializeComponent();

        // Keep startup completely UI-only. Optional telemetry starts after the window is visible.
        Loaded += (_, _) =>
        {
            try
            {
                ShowDashboard();
                timer.Tick += Timer_Tick;
                timer.Start();
            }
            catch
            {
                cpuUsage = 0;
            }
        };
    }

    void Timer_Tick(object? sender, EventArgs e)
    {
        // Telemetry is intentionally disabled in this startup-stability build.
        // The dashboard remains responsive and can be extended with the hardware engine later.
    }

    Button B(string text, RoutedEventHandler click)
    {
        var b = new Button { Content = text, Margin = new Thickness(0,6,0,6), Padding = new Thickness(12,9,12,9) };
        b.Click += click;
        return b;
    }

    TextBlock H(string text) => new() { Text=text, FontSize=22, FontWeight=FontWeights.SemiBold, Margin=new Thickness(0,0,0,14) };

    TextBlock P(string text) => new()
    {
        Text=text, FontSize=14, Foreground=FindResource("Muted") as Brush,
        TextWrapping=TextWrapping.Wrap, Margin=new Thickness(0,4,0,12)
    };

    void Clear(string title,string sub)
    {
        MainContent.Children.Clear();
        MainContent.Children.Add(H(title));
        MainContent.Children.Add(P(sub));
    }

    void ShowDashboard()
    {
        Clear("Gaming Dashboard","RB22 is a separate product from RB22 Game Booster Pro.");
        var g=new Grid();
        for(int i=0;i<4;i++) g.ColumnDefinitions.Add(new ColumnDefinition());
        string[] a={"CPU","GPU","RAM","MODE"};
        string[] v={cpuUsage>0 ? cpuUsage+"%" : "—","—","—",mode};
        for(int i=0;i<4;i++)
        {
            var p=new Border
            {
                Background=FindResource("Panel") as Brush,
                Padding=new Thickness(18), Margin=new Thickness(5), CornerRadius=new CornerRadius(14)
            };
            var s=new StackPanel();
            s.Children.Add(new TextBlock{Text=a[i],Foreground=FindResource("Muted") as Brush});
            s.Children.Add(new TextBlock{Text=v[i],FontSize=26,FontWeight=FontWeights.Bold,Margin=new Thickness(0,8,0,0)});
            p.Child=s; Grid.SetColumn(p,i); g.Children.Add(p);
        }
        MainContent.Children.Add(g);
        MainContent.Children.Add(B("⚡ Basic Boost",(_,_)=>Apply("Basic")));
        MainContent.Children.Add(B("⚡ Advanced Boost",(_,_)=>Apply("Advanced")));
        MainContent.Children.Add(B("🔥 TURBO Boost",(_,_)=>Apply("Turbo")));
    }

    void Apply(string m)
    {
        mode=m;
        ShowDashboard();
    }

    void Show(string title,string sub, params UIElement[] controls)
    {
        Clear(title,sub);
        foreach(var c in controls) MainContent.Children.Add(c);
    }

    void ShowAI() => Show("AI Game Optimizer","Analyze the active system and choose a software-side performance profile.",
        B("🤖 Analyze & Optimize",(_,_)=>Apply("Basic")),
        P("AI optimization engine will be connected after the stable UI build."));

    void ShowGames() => Show("Game Library","Per-game profiles, launch and optimization will live here.",
        B("+ Add Game",(_,_)=>MessageBox.Show("Game picker is reserved for the next module update.","RB22")),
        P("Create profiles for each game and keep optimization settings isolated."));

    void ShowPerformance() => Show("Performance Center","Live CPU telemetry and FPS/frametime monitoring.",
        P("Hardware telemetry is disabled in this stability build."),
        P("FPS, 1% low, 0.1% low and frametime graphs are planned for the benchmark/overlay engine."));

    void ShowMemory() => Show("Memory Center","Smart memory pressure management.",
        P("Automatic cleanup can be configured by threshold, including the planned 70% trigger."),
        B("Configure Memory Policy",(_,_)=>MessageBox.Show("Memory policy editor will be added with the optimization engine.","RB22")));

    void ShowNetwork() => Show("Network Center","Auto or manual DNS selection plus connection diagnostics.",
        B("⚡ Auto Select Best DNS",(_,_)=>MessageBox.Show("RB22 will test supported DNS providers and select the lowest measured DNS latency.","RB22")),
        B("Manual DNS",(_,_)=>MessageBox.Show("Manual providers: Cloudflare, Google, Quad9, OpenDNS and Custom.","RB22")));

    void ShowInput() => Show("Input Lab","Keyboard, mouse and hotkey controls in one glass-style workspace.",
        B("⌨ Keyboard Auto-Key",(_,_)=>MessageBox.Show("Keyboard automation settings are reserved for the input module.","RB22")),
        B("🖱 Mouse Auto-Clicker",(_,_)=>MessageBox.Show("Mouse automation settings are reserved for the input module.","RB22")),
        B("〰 Angle Snapping",(_,_)=>MessageBox.Show("Angle modes: Off, 0°, 45°, 90° or Custom.","RB22")),
        P("Input automation can be disabled globally and should only be used where permitted by the application/game."));

    void ShowBenchmark() => Show("Benchmark","Measure before/after performance changes.",
        B("Run Benchmark",(_,_)=>MessageBox.Show("Benchmark engine is planned for the next module update.","RB22")));

    void ShowHardware() => Show("Hardware Center","System telemetry and thermal-aware optimization.",
        P("CPU • GPU • RAM • VRAM • temperatures • clocks • fans where hardware exposes them."));

    void ShowSettings() => Show("Settings","Global RB22 controls and profiles.",
        B("Desktop Profile",(_,_)=>MessageBox.Show("Desktop profile selected.","RB22")),
        B("Productivity Profile",(_,_)=>MessageBox.Show("Productivity profile selected.","RB22")),
        B("Custom Profile",(_,_)=>MessageBox.Show("Custom profile selected.","RB22")));

    void Dashboard_Click(object s,RoutedEventArgs e)=>ShowDashboard();
    void AI_Click(object s,RoutedEventArgs e)=>ShowAI();
    void Games_Click(object s,RoutedEventArgs e)=>ShowGames();
    void Performance_Click(object s,RoutedEventArgs e)=>ShowPerformance();
    void Memory_Click(object s,RoutedEventArgs e)=>ShowMemory();
    void Network_Click(object s,RoutedEventArgs e)=>ShowNetwork();
    void Input_Click(object s,RoutedEventArgs e)=>ShowInput();
    void Benchmark_Click(object s,RoutedEventArgs e)=>ShowBenchmark();
    void Hardware_Click(object s,RoutedEventArgs e)=>ShowHardware();
    void Settings_Click(object s,RoutedEventArgs e)=>ShowSettings();
    void AIBoost_Click(object s,RoutedEventArgs e)=>Apply("Basic");
}
