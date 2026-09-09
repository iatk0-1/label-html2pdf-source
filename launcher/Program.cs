using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace LabelPrinterLauncher
{
    class Program
    {
        [STAThread]
        static void Main()
        {
            try
            {
                string exeDir = Path.GetDirectoryName(Application.ExecutablePath);
                string batFile = Path.Combine(exeDir, "LabelPrinter.bat");

                if (!File.Exists(batFile))
                {
                    MessageBox.Show("找不到 LabelPrinter.bat 文件！", "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
                    return;
                }

                ProcessStartInfo startInfo = new ProcessStartInfo
                {
                    FileName = batFile,
                    WorkingDirectory = exeDir,
                    UseShellExecute = false,
                    CreateNoWindow = true,
                    WindowStyle = ProcessWindowStyle.Hidden
                };

                Process.Start(startInfo);
            }
            catch (Exception ex)
            {
                MessageBox.Show($"启动失败：{ex.Message}", "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
