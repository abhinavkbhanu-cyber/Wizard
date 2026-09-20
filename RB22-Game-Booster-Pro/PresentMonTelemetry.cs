using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
using System.Threading.Tasks;

namespace RB22.GameBooster;

internal sealed class PresentMonTelemetry : IDisposable
{
    Process? process;
    CancellationTokenSource? stopCts;
    int frameTimeColumn=-1;

    public bool IsRunning => process is { HasExited: false };
    public event Action<double>? FpsUpdated;

    public bool Start(string processName)
    {
        Stop();
        if (string.IsNullOrWhiteSpace(processName)) return false;

        var exe = Path.Combine(AppContext.BaseDirectory, "PresentMon.exe");
        if (!File.Exists(exe)) return false;

        try
        {
            stopCts = new CancellationTokenSource();
            process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = exe,
                    Arguments = $"--process_name \"{processName}\" --output_stdout --no_console_stats --terminate_on_proc_exit --session_name RB22Telemetry --stop_existing_session",
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true
                },
                EnableRaisingEvents = true
            };

            process.OutputDataReceived += OnOutput;
            process.ErrorDataReceived += (_, _) => { };

            if (!process.Start())
            {
                process.Dispose();
                process = null;
                return false;
            }

            process.BeginOutputReadLine();
            process.BeginErrorReadLine();
            _ = WatchExitAsync(process, stopCts.Token);
            return true;
        }
        catch
        {
            Stop();
            return false;
        }
    }

    async Task WatchExitAsync(Process p, CancellationToken token)
    {
        try
        {
            while (!token.IsCancellationRequested && !p.HasExited)
                await Task.Delay(500, token).ConfigureAwait(false);
        }
        catch { }
    }

    void OnOutput(object? sender, DataReceivedEventArgs e)
    {
        var line = e.Data;
        if (string.IsNullOrWhiteSpace(line))
            return;

        var parts = line.Split(',');
        if (line.StartsWith("Application,", StringComparison.OrdinalIgnoreCase))
        {
            frameTimeColumn = FindFrameTimeColumn(parts);
            return;
        }

        if (frameTimeColumn < 0 || frameTimeColumn >= parts.Length)
            return;

        if (double.TryParse(parts[frameTimeColumn].Trim().Trim('"'),
            System.Globalization.NumberStyles.Float,
            System.Globalization.CultureInfo.InvariantCulture, out var ms) &&
            ms > 0.1 && ms < 200)
        {
            FpsUpdated?.Invoke(1000.0 / ms);
        }
    }

    static int FindFrameTimeColumn(string[] header)
    {
        for (var i = 0; i < header.Length; i++)
        {
            var name = header[i].Trim().Trim('"');
            if (name.Equals("MsBetweenDisplayChange", StringComparison.OrdinalIgnoreCase) ||
                name.Equals("MsBetweenPresents", StringComparison.OrdinalIgnoreCase) ||
                name.Equals("FrameTime", StringComparison.OrdinalIgnoreCase))
                return i;
        }
        return -1;
    }

    public void Stop()
    {
        var p = process;
        process = null;

        try { stopCts?.Cancel(); } catch { }
        stopCts?.Dispose();
        stopCts = null;

        if (p == null) return;
        try
        {
            if (!p.HasExited) p.Kill(true);
        }
        catch { }
        try { p.Dispose(); } catch { }
    }

    public void Dispose() => Stop();
}
