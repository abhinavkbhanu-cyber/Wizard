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
        if (string.IsNullOrWhiteSpace(line) ||
            line.StartsWith("Application,", StringComparison.OrdinalIgnoreCase))
            return;

        var parts = line.Split(',');
        foreach (var part in parts)
        {
            if (double.TryParse(part.Trim().Trim('\"'),
                System.Globalization.NumberStyles.Float,
                System.Globalization.CultureInfo.InvariantCulture, out var ms) &&
                ms > 0.1 && ms < 200 &&
                1000.0 / ms >= 5 && 1000.0 / ms <= 1000)
            {
                FpsUpdated?.Invoke(1000.0 / ms);
                break;
            }
        }
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
