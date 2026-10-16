using System;
using System.IO;
using System.Linq;
using System.Windows;
using System.Windows.Threading;

namespace RB22;

public partial class App : Application
{
    private static readonly string LogPath = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "RB22",
        "rb22-startup.log");
    private bool smokeTest;
    private bool uiError;

    private static void Log(string message)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(LogPath)!);
            File.AppendAllText(LogPath,
                $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] {message}{Environment.NewLine}");
        }
        catch { }
    }

    protected override void OnStartup(StartupEventArgs e)
    {
        smokeTest = e.Args.Any(a => string.Equals(a, "--smoke-test", StringComparison.OrdinalIgnoreCase));
        Log("=== RB22 STARTUP ===");
        Log("Args: " + string.Join(" ", e.Args));
        DispatcherUnhandledException += HandleDispatcherUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            Log("APPDOMAIN ERROR: " + args.ExceptionObject);

        try
        {
            Log("Calling base.OnStartup");
            base.OnStartup(e);
            ShutdownMode = ShutdownMode.OnMainWindowClose;

            Log("Constructing MainWindow");
            var window = new MainWindow();
            MainWindow = window;
            Log("MainWindow constructed successfully");
            window.WindowState = WindowState.Normal;
            window.Visibility = Visibility.Visible;
            window.ShowInTaskbar = true;
            window.ShowActivated = true;
            window.Topmost = true;

            Log("Calling Show()");
            window.Show();
            window.Activate();
            window.Focus();
            Log($"Shown: IsVisible={window.IsVisible}, State={window.WindowState}");

            Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, new Action(() =>
            {
                try
                {
                    if (MainWindow is not Window w)
                        throw new InvalidOperationException("MainWindow was not assigned.");
                    w.WindowState = WindowState.Normal;
                    w.Visibility = Visibility.Visible;
                    w.Activate();
                    w.Focus();
                    w.Topmost = false;
                    Log($"Visibility check: IsVisible={w.IsVisible}, State={w.WindowState}");

                    if (smokeTest)
                    {
                        // Let the Loaded event build the dashboard before validating it.
                        Dispatcher.BeginInvoke(DispatcherPriority.ApplicationIdle, new Action(() =>
                        {
                            var content = w.FindName("MainContent") as System.Windows.Controls.StackPanel;
                            bool ready = !uiError && w.IsVisible && content is not null && content.Children.Count > 0;
                            Log($"SMOKE TEST {(ready ? "PASS" : "FAIL")}: visible={w.IsVisible}, contentChildren={content?.Children.Count ?? -1}, uiError={uiError}");
                            Shutdown(ready ? 0 : 2);
                        }));
                    }
                }
                catch (Exception ex)
                {
                    Log("Visibility/smoke check ERROR: " + ex);
                    if (smokeTest) Shutdown(2);
                }
            }));
        }
        catch (Exception ex)
        {
            Log("STARTUP ERROR: " + ex);
            if (smokeTest)
            {
                Shutdown(1);
                return;
            }

            try
            {
                var fallback = new Window
                {
                    Title = "RB22 Startup Error",
                    Width = 620,
                    Height = 280,
                    WindowStartupLocation = WindowStartupLocation.CenterScreen,
                    Topmost = true,
                    Content = new System.Windows.Controls.TextBox
                    {
                        Text = "RB22 could not load its main window.\n\n" +
                               ex.GetType().Name + ": " + ex.Message +
                               "\n\nLog:\n" + LogPath,
                        IsReadOnly = true,
                        TextWrapping = System.Windows.TextWrapping.Wrap,
                        Padding = new Thickness(20),
                        FontSize = 15,
                        VerticalScrollBarVisibility = System.Windows.Controls.ScrollBarVisibility.Auto
                    }
                };
                MainWindow = fallback;
                fallback.Show();
                Log("Fallback error window shown");
            }
            catch (Exception fallbackEx)
            {
                Log("FALLBACK WINDOW ERROR: " + fallbackEx);
                Shutdown(1);
            }
        }
    }

    private void HandleDispatcherUnhandledException(object? sender, DispatcherUnhandledExceptionEventArgs e)
    {
        uiError = true;
        Log("UI ERROR: " + e.Exception);
        if (smokeTest)
        {
            e.Handled = true;
            Shutdown(2);
            return;
        }

        try
        {
            MessageBox.Show(
                "RB22 encountered a UI error.\n\n" +
                e.Exception.GetType().Name + ": " + e.Exception.Message +
                "\n\nLog:\n" + LogPath,
                "RB22 Error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
        }
        catch { }
        e.Handled = true;
    }
}
