using System;
using System.IO;
using System.Diagnostics;

namespace RB22.GameBooster;

// Compatibility helpers kept separate so the redesigned UI does not duplicate the profile/session engine.
public partial class MainWindow
{
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
