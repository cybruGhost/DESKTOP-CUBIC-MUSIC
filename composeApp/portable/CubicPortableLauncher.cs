using System;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

[assembly: AssemblyTitle("Cubic Music Portable")]
[assembly: AssemblyProduct("Cubic Music")]
[assembly: AssemblyVersion("0.0.1.0")]

internal static class CubicPortableLauncher
{
    private const string PayloadResource = "CubicMusic.payload.zip";

    [STAThread]
    private static void Main()
    {
        try
        {
            string launcherPath = Assembly.GetExecutingAssembly().Location;
            var launcherInfo = new FileInfo(launcherPath);
            string packageId = launcherInfo.Length.ToString("x") + "-" +
                launcherInfo.LastWriteTimeUtc.Ticks.ToString("x");
            string cacheRoot = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "CubicMusic",
                "PortableCache"
            );
            string packageRoot = Path.Combine(cacheRoot, "package-" + packageId);
            string markerPath = Path.Combine(packageRoot, ".ready");

            bool ownsMutex;
            using (var mutex = new Mutex(true, "Local\\CubicMusicPortable_" + packageId, out ownsMutex))
            {
                if (!ownsMutex)
                {
                    mutex.WaitOne();
                    ownsMutex = true;
                }

                try
                {
                    if (!File.Exists(markerPath))
                    {
                        ExtractPayload(cacheRoot, packageRoot);
                    }
                }
                finally
                {
                    if (ownsMutex) mutex.ReleaseMutex();
                }
            }

            string appPath = Path.Combine(packageRoot, "CubicMusic", "CubicMusic.exe");
            if (!File.Exists(appPath))
                throw new FileNotFoundException("The packaged Cubic Music executable is missing.", appPath);

            Process.Start(new ProcessStartInfo
            {
                FileName = appPath,
                WorkingDirectory = Path.GetDirectoryName(appPath),
                UseShellExecute = true
            });
        }
        catch (Exception error)
        {
            MessageBox.Show(
                error.Message,
                "Cubic Music could not start",
                MessageBoxButtons.OK,
                MessageBoxIcon.Error
            );
        }
    }

    private static void ExtractPayload(string cacheRoot, string packageRoot)
    {
        Directory.CreateDirectory(cacheRoot);
        string stagingRoot = packageRoot + ".extracting-" + Process.GetCurrentProcess().Id;
        if (Directory.Exists(stagingRoot)) Directory.Delete(stagingRoot, true);
        Directory.CreateDirectory(stagingRoot);

        try
        {
            using (Stream payload = Assembly.GetExecutingAssembly().GetManifestResourceStream(PayloadResource))
            {
                if (payload == null) throw new InvalidOperationException("The Cubic Music payload is missing.");
                using (var archive = new ZipArchive(payload, ZipArchiveMode.Read, false))
                {
                    string safeRoot = Path.GetFullPath(stagingRoot) + Path.DirectorySeparatorChar;
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        string destination = Path.GetFullPath(Path.Combine(stagingRoot, entry.FullName));
                        if (!destination.StartsWith(safeRoot, StringComparison.OrdinalIgnoreCase))
                            throw new InvalidDataException("The Cubic Music payload contains an unsafe path.");

                        if (String.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(destination);
                            continue;
                        }

                        string parent = Path.GetDirectoryName(destination);
                        if (!String.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                        entry.ExtractToFile(destination, true);
                    }
                }
            }

            File.WriteAllText(Path.Combine(stagingRoot, ".ready"), "Cubic Music portable cache");
            if (Directory.Exists(packageRoot)) Directory.Delete(packageRoot, true);
            Directory.Move(stagingRoot, packageRoot);
        }
        catch
        {
            if (Directory.Exists(stagingRoot)) Directory.Delete(stagingRoot, true);
            throw;
        }
    }
}
