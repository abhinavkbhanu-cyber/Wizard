using System;
using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace RB22.GameBooster;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        DispatcherUnhandledException += OnDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnUnhandledException;
        try
        {
            base.OnStartup(e);
            MainWindow = new MainWindow();
            MainWindow.Show();
        }
        catch (Exception ex)
        {
            ShowStartupError(ex);
            Shutdown(1);
        }
    }

    private void OnDispatcherUnhandledException(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        ShowStartupError(e.Exception);
        e.Handled = true;
    }

    private static void OnUnhandledException(object sender, UnhandledExceptionEventArgs e)
    {
        if (e.ExceptionObject is Exception ex) LogError(ex);
    }

    private static void ShowStartupError(Exception ex)
    {
        LogError(ex);
        MessageBox.Show(
            "RB22 Game Booster could not start.\\n\\n" + ex.Message +
            "\\n\\nA detailed log was saved to:\\n" + LogPath(),
            "RB22 Game Booster - Startup Error",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
    }

    private static void LogError(Exception ex)
    {
        try
        {
            File.AppendAllText(LogPath(), $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}] {ex}\\n\\n");
        }
        catch { }
    }

    private static string LogPath() => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RB22GameBooster", "startup-error.log");
}
