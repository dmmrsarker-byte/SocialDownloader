@echo off
cd /d "%~dp0"
echo [1/3] Compiling SocialDownloader.exe...
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /target:winexe /out:YTDownloader.exe /win32icon:app_icon.ico /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Web.Extensions.dll YTDownloader.cs QaTests.cs
if %ERRORLEVEL% NEQ 0 (
    echo Failed to compile YTDownloader.exe!
    pause
    exit /b 1
)
copy /Y YTDownloader.exe SocialDownloader.exe >nul

echo [2/3] Creating installer payload.zip...
powershell -NoProfile -Command "$ErrorActionPreference='Stop'; Add-Type -AssemblyName System.IO.Compression.FileSystem; $dir=(Get-Location).Path; $zipPath=Join-Path $dir 'payload.zip'; if (Test-Path $zipPath) { Remove-Item $zipPath -Force }; $z = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create'); foreach ($f in @('YTDownloader.exe','SocialDownloader.exe','app_icon.ico','app_logo.png','bin/yt-dlp.exe','bin/ffmpeg.exe')) { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, (Join-Path $dir ($f -replace '/','\')), $f) | Out-Null }; $z.Dispose()"

echo [3/3] Compiling single-file installer SocialDownloader-Setup.exe...
"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /target:winexe /out:SocialDownloader-Setup.exe /win32icon:app_icon.ico /resource:payload.zip,YTDownloaderSetup.payload.zip /resource:app_logo.png,YTDownloaderSetup.app_logo.png /resource:app_icon.ico,YTDownloaderSetup.app_icon.ico /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll /r:Microsoft.CSharp.dll Installer.cs
del /F /Q payload.zip >nul 2>&1
copy /Y SocialDownloader-Setup.exe YTDownloader-Setup.exe >nul

if %ERRORLEVEL% EQU 0 (
    echo Build successful: SocialDownloader.exe and SocialDownloader-Setup.exe
) else (
    echo Failed to build installer!
    pause
)
