using System;
using System.Windows;
using System.Windows.Threading;

namespace RB22;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        try
        {
            base.OnStartup(e);
            var window = new MainWindow();
            MainWindow = window;
            window.Show();
        }
        catch (Exception ex)
        {
            MessageBox.Show(
                "RB22 could not start.\n\n" + ex.GetType().Name + ": " + ex.Message,
                "RB22 startup error",
                MessageBoxButton.OK,
                MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    protected override void OnDispatcherUnhandledException(DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show(
            "RB22 encountered an error.\n\n" + e.Exception.GetType().Name + ": " + e.Exception.Message,
            "RB22 error",
            MessageBoxButton.OK,
            MessageBoxImage.Error);
        e.Handled = true;
    }
}
