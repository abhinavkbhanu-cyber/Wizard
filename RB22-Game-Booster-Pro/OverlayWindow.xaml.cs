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

        Closed += (_, _) =>
        {
            timer.Stop();
            cpuCounter?.Dispose();
            ramCounter?.Dispose();
        };
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

        // FPS and ping require game-specific telemetry, so don't pretend these are live values.
        Fps.Text = "N/A";
        Ping.Text = "—";
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }
}