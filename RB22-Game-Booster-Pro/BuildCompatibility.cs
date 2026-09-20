using System;
using System.IO;
using System.Linq;
using System.Diagnostics;
using System.Windows.Controls;

namespace RB22.GameBooster;

// Compatibility helpers kept separate so UI redesigns cannot break the performance engine build.
public partial class MainWindow
{
    // The dashboard no longer needs to visibly render the username, but the existing profile engine
    // still references this control. Keep a lightweight compatibility target.
    readonly TextBlock UsernameText = new();

    void LoadGameProfiles()
    {
        try
        {
            gameProfiles.Clear();
            var path=ProfilesPath();
            if(!File.Exists(path)) return;
            foreach(var line in File.ReadAllLines(path))
            {
                var parts=line.Split('|',2);
                if(parts.Length==2 && !string.IsNullOrWhiteSpace(parts[0]))
                    gameProfiles[parts[0]]=parts[1];
            }
        }
        catch { }
    }

    string GetGameProfile(string exe)
    {
        try
        {
            if(gameProfiles.TryGetValue(exe,out var mode) && !string.IsNullOrWhiteSpace(mode))
                return mode;
        }
        catch { }
        return "Advanced";
    }

    void SaveGameProfile(string exe,string mode)
    {
        try
        {
            gameProfiles[exe]=mode;
            var path=ProfilesPath();
            var dir=Path.GetDirectoryName(path);
            if(!string.IsNullOrEmpty(dir)) Directory.CreateDirectory(dir);
            File.WriteAllLines(path,gameProfiles.Select(x=>$"{x.Key}|{x.Value}"));
        }
        catch { }
    }

    string GetActivePowerScheme()
    {
        try
        {
            var psi=new ProcessStartInfo("powercfg.exe","/getactivescheme")
            {
                UseShellExecute=false,CreateNoWindow=true,RedirectStandardOutput=true
            };
            using var p=Process.Start(psi);
            if(p==null) return "";
            var output=p.StandardOutput.ReadToEnd();
            p.WaitForExit(2000);
            var match=System.Text.RegularExpressions.Regex.Match(output,@"([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})");
            return match.Success ? match.Groups[1].Value : "";
        }
        catch { return ""; }
    }

    void RestorePowerScheme()
    {
        try
        {
            if(!string.IsNullOrWhiteSpace(originalPowerScheme))
            {
                Process.Start(new ProcessStartInfo("powercfg.exe","/setactive "+originalPowerScheme)
                {UseShellExecute=false,CreateNoWindow=true})?.Dispose();
            }
        }
        catch { }
    }
}

public partial class OverlayWindow
{
    // OverlayWindow.xaml does not need a visible state label; keep telemetry state available to the engine.
    readonly TextBlock State = new();
}
