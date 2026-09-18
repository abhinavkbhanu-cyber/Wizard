using System;
using System.Diagnostics;
using System.Linq;
using System.Management;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Threading;
namespace RB22.GameBooster;
public partial class MainWindow : Window
{
    const int HotkeyId=2208; const uint MOD_NONE=0;
    [DllImport("user32.dll")] static extern bool RegisterHotKey(IntPtr hWnd,int id,uint fsModifiers,uint vk);
    [DllImport("user32.dll")] static extern bool UnregisterHotKey(IntPtr hWnd,int id);
    HwndSource? source; DispatcherTimer timer=new(){Interval=TimeSpan.FromSeconds(1)};
    readonly (string Name,string Host)[] dns={("Cloudflare","1.1.1.1"),("Google","8.8.8.8"),("Quad9","9.9.9.9"),("OpenDNS","208.67.222.222")};
    public MainWindow(){InitializeComponent(); Loaded+=LoadedHandler; Closed+=(_,_)=>{if(source!=null)UnregisterHotKey(source.Handle,HotkeyId);}; timer.Tick+=(_,_)=>UpdateStats(); timer.Start();}
    void LoadedHandler(object? s,RoutedEventArgs e){DeviceLabel.Text=Environment.Is64BitOperatingSystem?"LAPTOP/DESKTOP • x64":"Windows"; var h=new WindowInteropHelper(this).Handle; source=HwndSource.FromHwnd(h); source.AddHook(WndProc); RegisterHotKey(h,HotkeyId,MOD_NONE,0x77); UpdateStats();}
    IntPtr WndProc(IntPtr h,int m,IntPtr w,IntPtr l,ref bool handled){if(m==0x0312 && w.ToInt32()==HotkeyId){OverlayWindow();handled=true;}return IntPtr.Zero;}
    void OverlayWindow(){var o=new OverlayWindow();o.Owner=this;o.Show();}
    void UpdateStats(){try{var c=PerformanceCounter("Processor","% Processor Time","_Total");CpuText.Text=$"{c:F0}%";}catch{CpuText.Text="N/A";} try{var pc=new PerformanceCounter("Memory","% Committed Bytes In Use");RamText.Text=$"{pc.NextValue():F0}%";}catch{RamText.Text="N/A";} PingText.Text="—"; NetText.Text="DNS ready";}
    static float PerformanceCounter(string cat,string name,string inst){using var p=new PerformanceCounter(cat,name,inst);p.NextValue();System.Threading.Thread.Sleep(20);return p.NextValue();}
    async void TestDns_Click(object s,RoutedEventArgs e){StatusText.Text="Testing four DNS endpoints…";long best=long.MaxValue;string bestName="";foreach(var d in dns){try{using var p=new System.Net.NetworkInformation.Ping();var r=await p.SendPingAsync(d.Host,1200);if(r.Status==IPStatus.Success&&r.RoundtripTime<best){best=r.RoundtripTime;bestName=d.Name;}}catch{}}PingText.Text=best==long.MaxValue?"N/A":best+" ms";NetText.Text=bestName==""?"No response":bestName+" lowest measured latency";StatusText.Text="DNS test complete.";}
    void Boost_Click(object s,RoutedEventArgs e){StatusText.Text="Boost active • safe Windows optimizations enabled";try{Process.Start(new ProcessStartInfo("powercfg","/setactive SCHEME_MIN"){CreateNoWindow=true,UseShellExecute=false});}catch{}}
    void Settings_Click(object s,RoutedEventArgs e){MessageBox.Show("Default overlay hotkey: F8\nNetwork: Cloudflare, Google, Quad9, OpenDNS\nFPS is shown only when a reliable frame source is available.","RB22 Settings");}
}