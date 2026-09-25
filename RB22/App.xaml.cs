using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace RB22;

public partial class App : Application
{
    static readonly string LogPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RB22",
        "rb22-startup.log");

    static void Log(string message)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
            File.AppendAllText(LogPath, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {message}{Environment.NewLine}");
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        Log("STARTUP begin");
        try
        {
            base.OnStartup(e);
            ShutdownMode = ShutdownMode.OnMainWindowClose;

            Log("Creating MainWindow");
            var window = new MainWindow();
            MainWindow = window;

            Log("Showing MainWindow");
            window.Visibility = Visibility.Visible;
            window.ShowActivated = true;
            window.Show();
            window.Activate();
            window.Focus();
            Log("MainWindow shown");

            Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, new Action(() =>
            {
                try
                {
                    if (MainWindow is Window w)
                    {
                        w.WindowState = WindowState.Normal;
                        w.Visibility = Visibility.Visible;
                        w.Activate();
                        w.Focus();
                        Log("Startup visibility check complete");
                    }
                }
                catch (Exception ex) { Log("Visibility check failed: " + ex); }
            }));
        }
        catch (Exception ex)
        {
            Log("STARTUP ERROR: " + ex);
            try
            {
                MessageBox.Show(
                    "RB22 could not start.\n\n" + ex.GetType().Name + ": " + ex.Message +
                    "\n\nStartup log:\n" + LogPath,
                    "RB22 startup error",
                    MessageBoxButton.OK,
                    MessageBoxImage.Error);
            }
            catch { }
            Shutdown(1);
        }
    }

    protected override void OnDispatcherUnhandledException(DispatcherUnhandledExceptionEventArgs e)
    {
        Log("UNHANDLED UI ERROR: " + e.Exception);
        try
        {
            MessageBox.Show(
                "RB22 encountered a UI error.\n\n" + e.Exception.GetType().Name + ": " + e.Exception.Message +
                "\n\nStartup log:\n" + LogPath,
                "RB22 error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
        catch { }
        e.Handled = true;
    }
}
