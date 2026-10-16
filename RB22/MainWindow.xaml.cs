using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Threading;

namespace RB22;

public partial class MainWindow : Window
{
    private readonly DispatcherTimer timer = new() { Interval = TimeSpan.FromSeconds(2) };
    private readonly DispatcherTimer keyTimer = new();
    private string mode = "Balanced", dns = "Auto", angle = "Off", autoKey = "F";
    private bool keyRunning;
    private int boostCount;
    private readonly Process currentProcess = Process.GetCurrentProcess();
    private ulong lastIdle, lastKernel, lastUser;
    private double systemCpu;
    private readonly Dictionary<int, ProcessPriorityClass> originalPriorities = new();
    private TextBlock? cpuMetricText, ramMetricText, gameMetricText, processSummaryText;
    private ComboBox? processPicker;
    private bool onPerformancePage;

    private static readonly string[] KnownGameNames =
    {
        "robloxplayerbeta", "gta5", "gta5_enhanced", "cs2", "csgo", "valorant-win64-shipping",
        "r5apex", "fortniteclient-win64-shipping", "minecraft", "javaw", "lfs", "eldenring",
        "rocketleague", "league of legends", "leagueclient", "overwatch", "dota2", "helldivers2",
        "eldenring", "palworld", "fallguys_client", "cod", "cod.exe", "forza"
    };

    [StructLayout(LayoutKind.Sequential)]
    private struct FILETIME { public uint Low; public uint High; public ulong Value => ((ulong)High << 32) | Low; }

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Auto)]
    private class MEMORYSTATUSEX
    {
        public uint dwLength = (uint)Marshal.SizeOf<MEMORYSTATUSEX>();
        public uint dwMemoryLoad;
        public ulong ullTotalPhys, ullAvailPhys, ullTotalPageFile, ullAvailPageFile, ullTotalVirtual, ullAvailVirtual;
        public ulong ullAvailExtendedVirtual;
    }

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool GetSystemTimes(out FILETIME idle, out FILETIME kernel, out FILETIME user);
    [DllImport("kernel32.dll", CharSet = CharSet.Auto, SetLastError = true)]
    private static extern bool GlobalMemoryStatusEx([In, Out] MEMORYSTATUSEX buffer);
    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [StructLayout(LayoutKind.Sequential)] private struct INPUT { public uint type; public InputUnion U; }
    [StructLayout(LayoutKind.Explicit)] private struct InputUnion { [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] private struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    private const uint INPUT_KEYBOARD = 1, KEYEVENTF_KEYUP = 2;

    public MainWindow()
    {
        InitializeComponent();
        keyTimer.Tick += (_, _) => SendKey(autoKey);
        keyTimer.Interval = TimeSpan.FromMilliseconds(100);
        Loaded += (_, _) =>
        {
            ShowDashboard();
            timer.Tick += Timer_Tick;
            timer.Start();
            SampleSystemMetrics();
        };
        Closed += (_, _) =>
        {
            keyTimer.Stop();
            timer.Stop();
            RestoreChangedPriorities(showMessage: false);
        };
    }

    private void Timer_Tick(object? sender, EventArgs e)
    {
        SampleSystemMetrics();
        StatusText.Text = $"READY • CPU {systemCpu:0}% • {mode.ToUpperInvariant()}";
        if (cpuMetricText is not null) cpuMetricText.Text = $"CPU load: {systemCpu:0}%";
        if (ramMetricText is not null) ramMetricText.Text = GetMemorySummary();
        if (gameMetricText is not null) gameMetricText.Text = $"Detected game: {DetectGame() ?? "None detected"}";
    }

    private void SampleSystemMetrics()
    {
        try
        {
            if (GetSystemTimes(out var idle, out var kernel, out var user))
            {
                ulong idleDelta = idle.Value - lastIdle;
                ulong kernelDelta = kernel.Value - lastKernel;
                ulong userDelta = user.Value - lastUser;
                ulong total = kernelDelta + userDelta;
                if (lastKernel != 0 && total > 0)
                    systemCpu = Math.Clamp(100.0 * (total - idleDelta) / total, 0, 100);
                lastIdle = idle.Value; lastKernel = kernel.Value; lastUser = user.Value;
            }
        }
        catch { }
    }

    private static string GetMemorySummary()
    {
        try
        {
            var m = new MEMORYSTATUSEX();
            if (GlobalMemoryStatusEx(m))
            {
                double total = m.ullTotalPhys / 1024d / 1024 / 1024;
                double available = m.ullAvailPhys / 1024d / 1024 / 1024;
                return $"{(total - available):0.0} / {total:0.0} GB used ({m.dwMemoryLoad}% load)";
            }
        }
        catch { }
        return "RAM data unavailable";
    }

    private static string? DetectGame()
    {
        try
        {
            var games = Process.GetProcesses()
                .Where(p =>
                {
                    try { return KnownGameNames.Any(n => p.ProcessName.Contains(n, StringComparison.OrdinalIgnoreCase)); }
                    catch { return false; }
                })
                .Select(p => { try { return p.ProcessName; } catch { return ""; } })
                .Where(n => !string.IsNullOrWhiteSpace(n))
                .Distinct(StringComparer.OrdinalIgnoreCase)
                .OrderBy(n => n)
                .ToArray();
            return games.Length == 0 ? null : string.Join(", ", games.Take(3));
        }
        catch { return null; }
    }

    private Button B(string text, RoutedEventHandler click)
    {
        var b = new Button { Content = text, Margin = new Thickness(0, 6, 0, 6), Padding = new Thickness(12, 9, 12, 9) };
        b.Click += click;
        return b;
    }
    private TextBlock H(string text) => new() { Text = text, FontSize = 22, FontWeight = FontWeights.SemiBold, Margin = new Thickness(0, 0, 0, 14) };
    private TextBlock P(string text) => new() { Text = text, FontSize = 14, Foreground = FindResource("Muted") as Brush, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 4, 0, 12) };
    private void Clear(string title, string subtitle)
    {
        onPerformancePage = title == "Performance Center";
        MainContent.Children.Clear();
        MainContent.Children.Add(H(title));
        MainContent.Children.Add(P(subtitle));
    }
    private void Show(string title, string subtitle, params UIElement[] controls)
    {
        Clear(title, subtitle);
        foreach (var control in controls) MainContent.Children.Add(control);
    }
    private Border Card(string title, string value)
    {
        var panel = new Border { Background = FindResource("Panel") as Brush, Padding = new Thickness(18), Margin = new Thickness(5), CornerRadius = new CornerRadius(14) };
        var stack = new StackPanel();
        stack.Children.Add(new TextBlock { Text = title, Foreground = FindResource("Muted") as Brush });
        stack.Children.Add(new TextBlock { Text = value, FontSize = 20, FontWeight = FontWeights.Bold, Margin = new Thickness(0, 8, 0, 0), TextWrapping = TextWrapping.Wrap });
        panel.Child = stack;
        return panel;
    }

    private void ShowDashboard()
    {
        Clear("RB22 Performance Dashboard", "Live Windows telemetry, game detection and reversible optimization controls.");
        var grid = new Grid();
        for (int i = 0; i < 3; i++) grid.ColumnDefinitions.Add(new ColumnDefinition());
        UIElement[] cards =
        {
            Card("SYSTEM CPU", $"{systemCpu:0}%"),
            Card("MEMORY", GetMemorySummary()),
            Card("DETECTED GAME", DetectGame() ?? "None")
        };
        for (int i = 0; i < cards.Length; i++) { Grid.SetColumn(cards[i], i); grid.Children.Add(cards[i]); }
        MainContent.Children.Add(grid);
        MainContent.Children.Add(B("⚡ Balanced Profile", (_, _) => Apply("Balanced")));
        MainContent.Children.Add(B("🎮 Gaming Profile", (_, _) => Apply("Gaming")));
        MainContent.Children.Add(B("🔥 Low-Overhead Profile", (_, _) => Apply("Low Overhead")));
        MainContent.Children.Add(B("↩ Restore RB22-managed process priorities", (_, _) => RestoreChangedPriorities()));
        MainContent.Children.Add(P("RB22 reports live system CPU/RAM and can temporarily set a selected app to Above Normal priority. It does not overclock hardware or guarantee higher FPS."));
    }

    private void Apply(string profile)
    {
        mode = profile;
        boostCount++;
        StatusText.Text = $"PROFILE SELECTED • {profile.ToUpperInvariant()}";
        ShowDashboard();
    }

    private void ShowAI() => Show("AI Optimizer", "Rule-based profile suggestions from currently detected Windows state.",
        B("🤖 Analyze current system", (_, _) =>
        {
            string game = DetectGame() ?? "no known game detected";
            string memory = GetMemorySummary();
            string recommendation = systemCpu > 85 ? "High CPU load detected: close only apps you recognize and do not need." :
                (memory.Contains("GB used", StringComparison.Ordinal) && memory.Contains("% load", StringComparison.Ordinal) ? "Review the memory load before launching another heavy app." : "System load looks normal from available telemetry.");
            MessageBox.Show($"CPU: {systemCpu:0}%\nMemory: {memory}\nGame: {game}\n\nSuggestion: {recommendation}\n\nNo settings were changed.", "RB22 System Analysis");
        }),
        B("🎮 Apply Gaming profile", (_, _) => Apply("Gaming")),
        P("This version uses transparent rules, not a cloud AI model. Analysis does not silently close processes or change Windows settings."));

    private void ShowGames()
    {
        processPicker = new ComboBox { Margin = new Thickness(0, 6, 0, 10), MinWidth = 360, DisplayMemberPath = nameof(ProcessEntry.Label) };
        RefreshProcessList();
        processSummaryText = P("Select an app to view and change its process priority.");
        Show("Game Library & Process Profiles", "Detect known games and manage the selected app with reversible priority changes.",
            B("↻ Refresh running apps", (_, _) => RefreshProcessList()),
            processPicker,
            B("🎮 Detect running game", (_, _) => MessageBox.Show($"Detected game: {DetectGame() ?? "None detected"}", "RB22 Game Detection")),
            B("⚡ Set selected app to Above Normal", (_, _) => SetSelectedPriority(ProcessPriorityClass.AboveNormal)),
            B("↩ Restore selected app priority", (_, _) => RestoreSelectedPriority()),
            B("↩ Restore all RB22-managed priorities", (_, _) => RestoreChangedPriorities()),
            processSummaryText,
            P("Priority changes affect scheduling only, require no admin rights for your own processes, and are restored when RB22 closes where permitted. RB22 will never set High or Realtime priority."));
        RefreshProcessList();
    }

    private sealed class ProcessEntry
    {
        public int Id { get; init; }
        public string Name { get; init; } = "";
        public string Label => $"{Name}  (PID {Id})";
    }

    private void RefreshProcessList()
    {
        if (processPicker is null) return;
        int? selectedId = (processPicker.SelectedItem as ProcessEntry)?.Id;
        var entries = new List<ProcessEntry>();
        foreach (var process in Process.GetProcesses())
        {
            try { entries.Add(new ProcessEntry { Id = process.Id, Name = process.ProcessName }); }
            catch { }
            finally { process.Dispose(); }
        }
        processPicker.ItemsSource = entries.OrderBy(p => p.Name, StringComparer.OrdinalIgnoreCase).ToList();
        if (selectedId.HasValue) processPicker.SelectedItem = entries.FirstOrDefault(p => p.Id == selectedId.Value);
        if (processPicker.SelectedItem is null) processPicker.SelectedIndex = 0;
        if (processSummaryText is not null)
            processSummaryText.Text = $"{entries.Count} accessible process entries. Detected game: {DetectGame() ?? "None"}";
    }

    private void SetSelectedPriority(ProcessPriorityClass priority)
    {
        if (processPicker?.SelectedItem is not ProcessEntry entry)
        {
            MessageBox.Show("Choose a running app first.", "RB22");
            return;
        }
        if (entry.Id == Environment.ProcessId)
        {
            MessageBox.Show("RB22 cannot change its own priority from this control.", "RB22");
            return;
        }
        var answer = MessageBox.Show($"Temporarily set {entry.Name} (PID {entry.Id}) to Above Normal priority?\n\nThis may increase CPU contention for other apps. You can restore the original priority.", "Confirm performance change", MessageBoxButton.YesNo, MessageBoxImage.Warning);
        if (answer != MessageBoxResult.Yes) return;
        try
        {
            using var p = Process.GetProcessById(entry.Id);
            if (!originalPriorities.ContainsKey(entry.Id)) originalPriorities[entry.Id] = p.PriorityClass;
            p.PriorityClass = priority;
            MessageBox.Show($"{entry.Name} is now {priority}.", "RB22");
            RefreshProcessList();
        }
        catch (Exception ex) { MessageBox.Show($"Could not change priority: {ex.Message}", "RB22", MessageBoxButton.OK, MessageBoxImage.Warning); }
    }

    private void RestoreSelectedPriority()
    {
        if (processPicker?.SelectedItem is ProcessEntry entry && originalPriorities.TryGetValue(entry.Id, out var original))
            RestorePriority(entry.Id, original);
        else MessageBox.Show("No RB22-managed priority change exists for this selection.", "RB22");
        RefreshProcessList();
    }

    private void RestoreChangedPriorities(bool showMessage = true)
    {
        int restored = 0;
        foreach (var item in originalPriorities.ToArray())
            if (RestorePriority(item.Key, item.Value, false)) restored++;
        originalPriorities.Clear();
        if (showMessage) MessageBox.Show($"Restored {restored} process priority setting(s) where permitted.", "RB22");
        RefreshProcessList();
    }

    private bool RestorePriority(int id, ProcessPriorityClass priority, bool remove = true)
    {
        try
        {
            using var p = Process.GetProcessById(id);
            p.PriorityClass = priority;
            if (remove) originalPriorities.Remove(id);
            return true;
        }
        catch { if (remove) originalPriorities.Remove(id); return false; }
    }

    private void ShowPerformance()
    {
        Clear("Performance Center", "Live system telemetry and safe, reversible controls.");
        cpuMetricText = P($"CPU load: {systemCpu:0}%");
        ramMetricText = P(GetMemorySummary());
        gameMetricText = P($"Detected game: {DetectGame() ?? "None detected"}");
        MainContent.Children.Add(Card("LIVE CPU", $"{systemCpu:0}%"));
        MainContent.Children.Add(cpuMetricText);
        MainContent.Children.Add(ramMetricText);
        MainContent.Children.Add(gameMetricText);
        MainContent.Children.Add(B("↻ Refresh running processes", (_, _) => { SampleSystemMetrics(); RefreshProcessList(); if (cpuMetricText is not null) cpuMetricText.Text = $"CPU load: {systemCpu:0}%"; if (ramMetricText is not null) ramMetricText.Text = GetMemorySummary(); if (gameMetricText is not null) gameMetricText.Text = $"Detected game: {DetectGame() ?? "None detected"}"; }));
        MainContent.Children.Add(B("🎮 Open game/process profiles", (_, _) => ShowGames()));
        MainContent.Children.Add(B("↩ Restore RB22-managed priorities", (_, _) => RestoreChangedPriorities()));
        MainContent.Children.Add(P("FPS and 1% low require a frame-time source such as PresentMon; this build does not invent FPS numbers. GPU temperatures and clocks are not reported unless a supported telemetry provider is added."));
    }

    private void ShowMemory() => Show("Memory Center", "Read actual Windows memory pressure; avoid misleading 'RAM cleaner' promises.",
        P(GetMemorySummary()),
        B("↻ Refresh memory reading", (_, _) => { var text = GetMemorySummary(); MessageBox.Show(text, "RB22 Memory Status"); }),
        B("Open Windows Storage settings", (_, _) => { try { Process.Start(new ProcessStartInfo("ms-settings:storagesense") { UseShellExecute = true }); } catch (Exception ex) { MessageBox.Show(ex.Message, "RB22"); } }),
        P("Windows manages physical memory automatically. Forced garbage collection only affects RB22's managed heap and is not presented as a system-wide RAM boost."));

    private void ShowNetwork() => Show("Network Center", "Choose a DNS profile label or open Windows network settings. RB22 does not claim to apply DNS unless the system configuration is actually changed.",
        B("☁ Cloudflare 1.1.1.1 profile", (_, _) => SetDns("Cloudflare 1.1.1.1")),
        B("🔎 Google 8.8.8.8 profile", (_, _) => SetDns("Google 8.8.8.8")),
        B("🛡 Quad9 9.9.9.9 profile", (_, _) => SetDns("Quad9 9.9.9.9")),
        B("🌐 OpenDNS profile", (_, _) => SetDns("OpenDNS")),
        B("⚡ Auto profile", (_, _) => SetDns("Auto")),
        B("Open Windows network settings", (_, _) => { try { Process.Start(new ProcessStartInfo("ms-settings:network-status") { UseShellExecute = true }); } catch (Exception ex) { MessageBox.Show(ex.Message, "RB22"); } }),
        P($"Selected profile: {dns}. This selection is informational; it does not change adapter DNS settings."));

    private void SetDns(string profile) { dns = profile; StatusText.Text = $"DNS PROFILE SELECTED • {profile}"; ShowNetwork(); }

    private void ShowInput() => Show("Input Lab", "Keyboard Auto-Key controls for testing and accessibility. Use only where permitted by the app/game.",
        B($"⌨ Auto-Key: {(keyRunning ? "ON" : "OFF")}", (_, _) => ToggleKey()),
        B($"Key: {autoKey}", (_, _) => CycleKey()),
        B($"Repeat interval: {keyTimer.Interval.TotalMilliseconds:0} ms", (_, _) => CycleRate()),
        B($"〰 Angle Snap profile: {angle}", (_, _) => CycleAngle()),
        P("Angle Snap is a profile label only; it does not alter mouse movement in this build. Auto-Key sends the selected key to the focused application."),
        B("⛔ Stop all input automation", (_, _) => { keyTimer.Stop(); keyRunning = false; StatusText.Text = "INPUT AUTOMATION OFF"; ShowInput(); }));

    private void ToggleKey() { keyRunning = !keyRunning; if (keyRunning) keyTimer.Start(); else keyTimer.Stop(); StatusText.Text = keyRunning ? "AUTO-KEY RUNNING" : "AUTO-KEY STOPPED"; ShowInput(); }
    private void CycleKey() { autoKey = autoKey == "F" ? "E" : autoKey == "E" ? "Space" : "F"; ShowInput(); }
    private void CycleRate() { double ms = keyTimer.Interval.TotalMilliseconds; keyTimer.Interval = TimeSpan.FromMilliseconds(ms >= 250 ? 50 : ms >= 100 ? 250 : 100); ShowInput(); }
    private void CycleAngle() { angle = angle == "Off" ? "0°" : angle == "0°" ? "45°" : angle == "45°" ? "90°" : angle == "90°" ? "Custom" : "Off"; ShowInput(); }
    private static void SendKey(string key)
    {
        ushort vk = key == "Space" ? (ushort)0x20 : (ushort)key[0];
        var inputs = new INPUT[2];
        inputs[0].type = INPUT_KEYBOARD; inputs[0].U.ki = new KEYBDINPUT { wVk = vk };
        inputs[1].type = INPUT_KEYBOARD; inputs[1].U.ki = new KEYBDINPUT { wVk = vk, dwFlags = KEYEVENTF_KEYUP };
        SendInput(2, inputs, Marshal.SizeOf<INPUT>());
    }

    private void ShowBenchmark() => Show("Benchmark & Diagnostics", "Capture a transparent snapshot of real Windows telemetry; this is not an FPS benchmark.",
        B("📄 Save system snapshot report", (_, _) => SaveBenchmarkReport()),
        B("↻ Refresh measurements", (_, _) => { SampleSystemMetrics(); MessageBox.Show($"CPU: {systemCpu:0}%\nRAM: {GetMemorySummary()}\nKnown game: {DetectGame() ?? "None"}\nProcesses: {Process.GetProcesses().Length}", "RB22 Snapshot"); }),
        P("Frame rate, frame-time percentiles and 1% lows are not fabricated. A later telemetry integration can add them using a supported frame-time provider."));

    private void SaveBenchmarkReport()
    {
        try
        {
            SampleSystemMetrics();
            string folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "RB22 Reports");
            Directory.CreateDirectory(folder);
            string path = Path.Combine(folder, $"RB22-System-Snapshot-{DateTime.Now:yyyyMMdd-HHmmss}.txt");
            var report = new StringBuilder();
            report.AppendLine("RB22 PC — System Snapshot");
            report.AppendLine($"Timestamp: {DateTime.Now:O}");
            report.AppendLine($"OS: {Environment.OSVersion}");
            report.AppendLine($"Architecture: {Environment.Is64BitOperatingSystem switch { true => "64-bit", false => "32-bit" }}");
            report.AppendLine($"Logical processors: {Environment.ProcessorCount}");
            report.AppendLine($"System CPU load: {systemCpu:0}%");
            report.AppendLine($"Physical memory: {GetMemorySummary()}");
            report.AppendLine($"Detected game: {DetectGame() ?? "None detected"}");
            report.AppendLine($"Process count: {Process.GetProcesses().Length}");
            report.AppendLine($"Selected RB22 profile: {mode}");
            report.AppendLine("FPS/frametime: not measured by this snapshot.");
            File.WriteAllText(path, report.ToString());
            MessageBox.Show($"Report saved:\n{path}", "RB22");
        }
        catch (Exception ex) { MessageBox.Show($"Could not save report: {ex.Message}", "RB22"); }
    }

    private void ShowHardware() => Show("Hardware Center", "Windows-reported system information.",
        P($"OS: {Environment.OSVersion}"),
        P($"Architecture: {(Environment.Is64BitOperatingSystem ? "64-bit" : "32-bit")}"),
        P($"Logical processors: {Environment.ProcessorCount}"),
        P($"CPU load: {systemCpu:0}%"),
        P(GetMemorySummary()),
        P("CPU/GPU temperature, clocks and VRAM require vendor/driver telemetry not included in this build."));

    private void ShowSettings() => Show("Settings", "RB22 global controls.",
        B("Reset input automation", (_, _) => { keyTimer.Stop(); keyRunning = false; angle = "Off"; autoKey = "F"; ShowInput(); }),
        B("Reset profile", (_, _) => { mode = "Balanced"; StatusText.Text = "PROFILE RESET"; ShowDashboard(); }),
        B("Restore managed process priorities", (_, _) => RestoreChangedPriorities()),
        P("RB22 PC is a separate Windows application. Its updates do not change the Android mobile project."));

    private void Dashboard_Click(object sender, RoutedEventArgs e) => ShowDashboard();
    private void AI_Click(object sender, RoutedEventArgs e) => ShowAI();
    private void Games_Click(object sender, RoutedEventArgs e) => ShowGames();
    private void Performance_Click(object sender, RoutedEventArgs e) => ShowPerformance();
    private void Memory_Click(object sender, RoutedEventArgs e) => ShowMemory();
    private void Network_Click(object sender, RoutedEventArgs e) => ShowNetwork();
    private void Input_Click(object sender, RoutedEventArgs e) => ShowInput();
    private void Benchmark_Click(object sender, RoutedEventArgs e) => ShowBenchmark();
    private void Hardware_Click(object sender, RoutedEventArgs e) => ShowHardware();
    private void Settings_Click(object sender, RoutedEventArgs e) => ShowSettings();
    private void AIBoost_Click(object sender, RoutedEventArgs e) => Apply("Gaming");
}
