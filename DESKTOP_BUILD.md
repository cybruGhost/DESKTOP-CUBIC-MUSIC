# Cubic Music desktop builds

Desktop packages are built for the operating system running Gradle. Use a Linux
x86_64 machine for Linux packages and a Windows x64 machine (or Windows CI runner)
for Windows packages. The Compose/jpackage toolchain does not cross-package a
Windows installer from Linux.

If you only have Linux locally, run the manual **Build desktop packages** GitHub
Actions workflow after pushing the code. It builds on both operating systems and
uploads the packages as workflow artifacts; it does not publish a release for
you. Test the artifacts before attaching them to a public release.

Use JDK 21. Override the desktop package version when preparing a release with
`-PdesktopVersion=3.0.1` (or the new release number).

## Linux

Run from the repository root:

```bash
./gradlew :composeApp:run
./gradlew :composeApp:packageLinuxPortable
./gradlew :composeApp:packageDeb
./gradlew :composeApp:packageRpm
```

The portable `.tar.gz` is under `composeApp/build/compose/binaries/main/portable/`.
After extraction, run `CubicMusic/bin/CubicMusic`. The archive bundles a Java
runtime, but a compatible x86_64 Linux desktop and system graphics/audio
libraries are still required. Ubuntu and Kali use the `.deb`; RPM-based
distributions use the `.rpm`. Other Linux distributions can try the portable
archive. Building `.deb` needs `fakeroot` and `dpkg-deb`; building `.rpm` needs
`rpmbuild` installed on the build machine.

## Windows

Run from the repository root in PowerShell:

```powershell
.\gradlew.bat :composeApp:run
.\gradlew.bat :composeApp:packageWindowsPortable
.\gradlew.bat :composeApp:packageExe
.\gradlew.bat :composeApp:packageMsi
```

The portable `.zip` is under `composeApp/build/compose/binaries/main/portable/`.
The `.exe` and `.msi` installers are under `composeApp/build/compose/binaries/main/`.
Windows installer packaging requires WiX Toolset on the build machine; the
portable zip does not.

Desktop library data is stored under `$XDG_DATA_HOME/CubicMusic` on Linux (or
`~/.local/share/CubicMusic` when unset) and `%APPDATA%\CubicMusic` on Windows.

## Windows text-rendering check

If artwork and icons appear but **all** Compose labels disappear (including
fixed labels such as Browse), test a different Skiko graphics backend before
assuming the music metadata is empty. In PowerShell, launch the portable app
with:

```powershell
$env:SKIKO_RENDER_API = "OPENGL"
& ".\CubicMusic\CubicMusic.exe"
```

If that restores the text, the device's default Direct3D/graphics-driver path
is the likely cause. Try an updated graphics driver; `SOFTWARE_FAST` can be used
as a slower diagnostic fallback. Remove the environment variable to return to
the default renderer.
