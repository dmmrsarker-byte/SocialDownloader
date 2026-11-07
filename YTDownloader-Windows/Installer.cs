using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading.Tasks;
using System.Windows.Forms;
using Microsoft.Win32;

namespace YTDownloaderSetup
{
    static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        static void Main(string[] args)
        {
            try { SetProcessDPIAware(); } catch { }

            // Support silent install via /S or /silent (defaults to English "en")
            if (args != null && args.Length > 0 &&
                (string.Equals(args[0], "/S", StringComparison.OrdinalIgnoreCase) ||
                 string.Equals(args[0], "/silent", StringComparison.OrdinalIgnoreCase)))
            {
                string defaultDir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "Programs", "SocialDownloader");
                InstallerEngine.Install(defaultDir, "en", true, true, null);
                return;
            }

            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new SetupForm());
        }
    }

    public class SetupLanguage
    {
        public string Code { get; set; }
        public string DisplayName { get; set; }
        public string Subtitle { get; set; }
        public string LangLabel { get; set; }
        public string FolderLabel { get; set; }
        public string DesktopChk { get; set; }
        public string StartMenuChk { get; set; }
        public string LaunchChk { get; set; }
        public string ReadyStatus { get; set; }
        public string InstallBtn { get; set; }
        public string FinishBtn { get; set; }
        public string CloseBtn { get; set; }

        public override string ToString() { return DisplayName; }
    }

    public static class SetupLanguages
    {
        public static readonly List<SetupLanguage> All = new List<SetupLanguage>
        {
            new SetupLanguage
            {
                Code = "en",
                DisplayName = "English (Default)",
                Subtitle = "Install Social Downloader (YouTube, FB, IG, Threads, LinkedIn, X, TikTok & More) on Windows",
                LangLabel = "🌐 Select Application Language (Default: English):",
                FolderLabel = "📁 Installation Folder:",
                DesktopChk = "Create a Desktop Shortcut",
                StartMenuChk = "Create a Start Menu Shortcut",
                LaunchChk = "Launch Social Downloader after installation",
                ReadyStatus = "Click 'Install Now' to begin installation (Bundled: yt-dlp + FFmpeg)",
                InstallBtn = "⬇ Install Now",
                FinishBtn = "✔ Finish",
                CloseBtn = "Close"
            },
            new SetupLanguage
            {
                Code = "bn",
                DisplayName = "বাংলা (Bengali)",
                Subtitle = "Windows পিসিতে Social Downloader (YouTube, FB, IG, Threads, LinkedIn, X, TikTok) ইনস্টল করুন",
                LangLabel = "🌐 অ্যাপের ভাষা নির্বাচন করুন (Select Language):",
                FolderLabel = "📁 ইনস্টলেশন ফোল্ডার (Install Location):",
                DesktopChk = "ডেস্কটপে শর্টকাট তৈরি করুন (Create Desktop Shortcut)",
                StartMenuChk = "স্টার্ট মেনুতে শর্টকাট তৈরি করুন (Create Start Menu Shortcut)",
                LaunchChk = "ইনস্টলেশন শেষে Social Downloader চালু করুন (Launch after setup)",
                ReadyStatus = "ইনস্টল করার জন্য 'Install Now' বাটনে ক্লিক করুন (বান্ডেলড: yt-dlp + FFmpeg)",
                InstallBtn = "⬇ ইনস্টল করুন",
                FinishBtn = "✔ সম্পন্ন",
                CloseBtn = "বন্ধ করুন"
            },
            new SetupLanguage
            {
                Code = "hi",
                DisplayName = "हिन्दी (Hindi)",
                Subtitle = "Windows पीसी पर YouTube, Facebook (Watch/Reels) और Instagram डाउनलोडर इंस्टॉल करें",
                LangLabel = "🌐 ऐप की भाषा चुनें (Select Language):",
                FolderLabel = "📁 इंस्टॉलेशन फ़ोल्डर (Install Location):",
                DesktopChk = "डेस्कटॉप शॉर्टकट बनाएँ (Create Desktop Shortcut)",
                StartMenuChk = "स्टार्ट मेनू शॉर्टकट बनाएँ (Create Start Menu Shortcut)",
                LaunchChk = "इंस्टॉलेशन के बाद ऐप चालू करें (Launch after setup)",
                ReadyStatus = "इंस्टॉल शुरू करने के लिए 'Install Now' पर क्लिक करें (yt-dlp + FFmpeg सहित)",
                InstallBtn = "⬇ अभी इंस्टॉल करें",
                FinishBtn = "✔ समाप्त",
                CloseBtn = "बंद करें"
            },
            new SetupLanguage
            {
                Code = "ur",
                DisplayName = "اردو (Urdu)",
                Subtitle = "ونڈوز پی سی پر یوٹیوب، فیس بک اور انسٹاگرام ویڈیو ڈاؤن لوڈر انسٹال کریں",
                LangLabel = "🌐 ایپ کی زبان منتخب کریں (Select Language):",
                FolderLabel = "📁 انسٹالیشن فولڈر:",
                DesktopChk = "ڈیسک ٹاپ شارٹ کٹ بنائیں",
                StartMenuChk = "اسٹارٹ مینو شارٹ کٹ بنائیں",
                LaunchChk = "انسٹالیشن کے بعد ایپ چلائیں",
                ReadyStatus = "انسٹال کرنے کے لیے 'Install Now' پر کلک کریں",
                InstallBtn = "⬇ انسٹال کریں",
                FinishBtn = "✔ مکمل",
                CloseBtn = "بند کریں"
            },
            new SetupLanguage
            {
                Code = "ar",
                DisplayName = "العربية (Arabic)",
                Subtitle = "تثبيت برنامج تحميل الفيديو من يوتيوب وفيسبوك وإنستغرام على ويندوز",
                LangLabel = "🌐 اختر لغة البرنامج (Select Language):",
                FolderLabel = "📁 مجلد التثبيت:",
                DesktopChk = "إنشاء اختصار على سطح المكتب",
                StartMenuChk = "إنشاء اختصار في قائمة ابدأ",
                LaunchChk = "تشغيل البرنامج بعد التثبيت",
                ReadyStatus = "اضغط على 'Install Now' لبدء التثبيت",
                InstallBtn = "⬇ تثبيت الآن",
                FinishBtn = "✔ إنهاء",
                CloseBtn = "إغلاق"
            },
            new SetupLanguage
            {
                Code = "es",
                DisplayName = "Español (Spanish)",
                Subtitle = "Instalar descargador de YouTube, Facebook e Instagram para Windows",
                LangLabel = "🌐 Seleccione el idioma de la aplicación:",
                FolderLabel = "📁 Carpeta de instalación:",
                DesktopChk = "Crear acceso directo en el Escritorio",
                StartMenuChk = "Crear acceso directo en el Menú Inicio",
                LaunchChk = "Iniciar la aplicación al finalizar",
                ReadyStatus = "Haga clic en 'Instalar Ahora' para comenzar",
                InstallBtn = "⬇ Instalar Ahora",
                FinishBtn = "✔ Finalizar",
                CloseBtn = "Cerrar"
            },
            new SetupLanguage
            {
                Code = "pt",
                DisplayName = "Português (Portuguese)",
                Subtitle = "Instalar baixador de vídeos do YouTube, Facebook e Instagram no Windows",
                LangLabel = "🌐 Selecione o idioma do aplicativo:",
                FolderLabel = "📁 Pasta de instalação:",
                DesktopChk = "Criar atalho na Área de Trabalho",
                StartMenuChk = "Criar atalho no Menu Iniciar",
                LaunchChk = "Iniciar aplicativo após a instalação",
                ReadyStatus = "Clique em 'Instalar Agora' para iniciar",
                InstallBtn = "⬇ Instalar Agora",
                FinishBtn = "✔ Concluir",
                CloseBtn = "Fechar"
            },
            new SetupLanguage
            {
                Code = "ru",
                DisplayName = "Русский (Russian)",
                Subtitle = "Установка загрузчика видео с YouTube, Facebook и Instagram для Windows",
                LangLabel = "🌐 Выберите язык приложения:",
                FolderLabel = "📁 Папка установки:",
                DesktopChk = "Создать ярлык на рабочем столе",
                StartMenuChk = "Создать ярлык в меню «Пуск»",
                LaunchChk = "Запустить программу после установки",
                ReadyStatus = "Нажмите «Установить» для начала установки",
                InstallBtn = "⬇ Установить",
                FinishBtn = "✔ Готово",
                CloseBtn = "Закрыть"
            },
            new SetupLanguage
            {
                Code = "id",
                DisplayName = "Bahasa Indonesia",
                Subtitle = "Instal Pengunduh Video YouTube, Facebook & Instagram untuk Windows",
                LangLabel = "🌐 Pilih Bahasa Aplikasi:",
                FolderLabel = "📁 Folder Instalasi:",
                DesktopChk = "Buat Pintasan di Desktop",
                StartMenuChk = "Buat Pintasan di Start Menu",
                LaunchChk = "Jalankan aplikasi setelah instalasi selesai",
                ReadyStatus = "Klik 'Instal Sekarang' untuk memulai instalasi",
                InstallBtn = "⬇ Instal Sekarang",
                FinishBtn = "✔ Selesai",
                CloseBtn = "Tutup"
            }
        };
    }

    public static class InstallerEngine
    {
        public static void Install(string targetDir, string langCode, bool desktopShortcut, bool startMenuShortcut, Action<int, string> onProgress)
        {
            if (onProgress != null) onProgress(5, "Preparing installation directory...");
            Directory.CreateDirectory(targetDir);

            try
            {
                foreach (string procName in new string[] { "YTDownloader", "SocialDownloader" })
                {
                    foreach (var p in Process.GetProcessesByName(procName))
                    {
                        try { p.Kill(); p.WaitForExit(2000); } catch { }
                    }
                }
            }
            catch { }

            string tempZip = Path.Combine(Path.GetTempPath(), "ytdl_setup_payload_" + Guid.NewGuid().ToString("N") + ".zip");
            try
            {
                if (onProgress != null) onProgress(15, "Reading embedded package...");
                var asm = Assembly.GetExecutingAssembly();
                using (Stream resStream = asm.GetManifestResourceStream("YTDownloaderSetup.payload.zip"))
                {
                    if (resStream == null)
                        throw new Exception("Embedded payload.zip resource not found.");
                    using (FileStream fs = new FileStream(tempZip, FileMode.Create, FileAccess.Write))
                    {
                        resStream.CopyTo(fs);
                    }
                }

                if (onProgress != null) onProgress(35, "Extracting application & media engines (yt-dlp + FFmpeg)...");
                using (ZipArchive archive = ZipFile.OpenRead(tempZip))
                {
                    int total = archive.Entries.Count;
                    int index = 0;
                    foreach (ZipArchiveEntry entry in archive.Entries)
                    {
                        index++;
                        string fullPath = Path.Combine(targetDir, entry.FullName.Replace('/', Path.DirectorySeparatorChar));
                        if (string.IsNullOrEmpty(entry.Name))
                        {
                            Directory.CreateDirectory(fullPath);
                            continue;
                        }
                        Directory.CreateDirectory(Path.GetDirectoryName(fullPath));
                        entry.ExtractToFile(fullPath, true);
                        int pct = 35 + (int)((index * 45.0) / Math.Max(1, total));
                        if (onProgress != null) onProgress(pct, "Installing: " + entry.Name);
                    }
                }
            }
            finally
            {
                try { if (File.Exists(tempZip)) File.Delete(tempZip); } catch { }
            }

            // Ensure targetDir\bin\yt-dlp.exe exists (copy from local bin if available or download)
            try
            {
                string targetBin = Path.Combine(targetDir, "bin");
                Directory.CreateDirectory(targetBin);
                string targetYtDlp = Path.Combine(targetBin, "yt-dlp.exe");
                string targetFfmpeg = Path.Combine(targetBin, "ffmpeg.exe");
                string setupDir = AppDomain.CurrentDomain.BaseDirectory;
                string[] localBins = new string[]
                {
                    Path.Combine(setupDir, "bin"),
                    Path.Combine(setupDir, "YTDownloader-Windows", "bin")
                };
                foreach (string lb in localBins)
                {
                    if (!File.Exists(targetYtDlp) && File.Exists(Path.Combine(lb, "yt-dlp.exe")))
                    {
                        File.Copy(Path.Combine(lb, "yt-dlp.exe"), targetYtDlp, true);
                    }
                    if (!File.Exists(targetFfmpeg) && File.Exists(Path.Combine(lb, "ffmpeg.exe")))
                    {
                        File.Copy(Path.Combine(lb, "ffmpeg.exe"), targetFfmpeg, true);
                    }
                }
                if (!File.Exists(targetYtDlp))
                {
                    if (onProgress != null) onProgress(82, "Downloading yt-dlp media engine...");
                    ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
                    using (var wc = new WebClient())
                    {
                        wc.DownloadFile("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", targetYtDlp);
                    }
                }
            }
            catch { }

            // Save selected language code to both targetDir and LocalApplicationData\YTDownloaderWindows
            string selectedLang = string.IsNullOrWhiteSpace(langCode) ? "en" : langCode.Trim().ToLowerInvariant();
            try
            {
                File.WriteAllText(Path.Combine(targetDir, "language.cfg"), selectedLang, Encoding.UTF8);
                string dataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "YTDownloaderWindows");
                Directory.CreateDirectory(dataDir);
                File.WriteAllText(Path.Combine(dataDir, "language.cfg"), selectedLang, Encoding.UTF8);
            }
            catch { }

            string exePath = Path.Combine(targetDir, "YTDownloader.exe");
            string icoPath = Path.Combine(targetDir, "app_icon.ico");

            if (desktopShortcut)
            {
                if (onProgress != null) onProgress(85, "Creating Desktop shortcut...");
                string desktopDir = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                CreateShortcut(Path.Combine(desktopDir, "Social Downloader.lnk"), exePath, targetDir, icoPath, "Social Downloader — YouTube, Facebook, Instagram, Threads, LinkedIn, X & TikTok");
            }

            if (startMenuShortcut)
            {
                if (onProgress != null) onProgress(90, "Creating Start Menu shortcut...");
                string programsDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                string appMenuDir = Path.Combine(programsDir, "Social Downloader");
                Directory.CreateDirectory(appMenuDir);
                CreateShortcut(Path.Combine(appMenuDir, "Social Downloader.lnk"), exePath, targetDir, icoPath, "Social Downloader — YouTube, Facebook, Instagram, Threads, LinkedIn, X & TikTok");
            }

            if (onProgress != null) onProgress(95, "Registering uninstaller...");
            CreateUninstaller(targetDir, exePath, icoPath);

            if (onProgress != null) onProgress(100, "✔ Installation completed successfully!");
        }

        private static void CreateShortcut(string shortcutPath, string targetExe, string workingDir, string iconPath, string description)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                dynamic shell = Activator.CreateInstance(shellType);
                dynamic shortcut = shell.CreateShortcut(shortcutPath);
                shortcut.TargetPath = targetExe;
                shortcut.WorkingDirectory = workingDir;
                if (File.Exists(iconPath)) shortcut.IconLocation = iconPath;
                shortcut.Description = description;
                shortcut.Save();
            }
            catch { }
        }

        private static void CreateUninstaller(string targetDir, string exePath, string icoPath)
        {
            try
            {
                string uninstBat = Path.Combine(targetDir, "Uninstall.bat");
                string batContent = "@echo off\r\n" +
                    "echo Uninstalling YT Downloader...\r\n" +
                    "taskkill /F /IM YTDownloader.exe >nul 2>&1\r\n" +
                    "del /F /Q \"%USERPROFILE%\\Desktop\\YT Downloader.lnk\" >nul 2>&1\r\n" +
                    "rmdir /S /Q \"%APPDATA%\\Microsoft\\Windows\\Start Menu\\Programs\\YT Downloader\" >nul 2>&1\r\n" +
                    "reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\YTDownloader\" /f >nul 2>&1\r\n" +
                    "cd /d \"%TEMP%\"\r\n" +
                    "rmdir /S /Q \"" + targetDir + "\"\r\n";
                File.WriteAllText(uninstBat, batContent);

                using (RegistryKey key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\SocialDownloader"))
                {
                    if (key != null)
                    {
                        key.SetValue("DisplayName", "Social Downloader v2.4");
                        key.SetValue("DisplayVersion", "2.4.0");
                        key.SetValue("Publisher", "Bistar Digital");
                        key.SetValue("DisplayIcon", File.Exists(icoPath) ? icoPath : exePath);
                        key.SetValue("InstallLocation", targetDir);
                        key.SetValue("UninstallString", "cmd.exe /c \"" + uninstBat + "\"");
                        key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                        key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
                    }
                }
            }
            catch { }
        }
    }

    public class SetupForm : Form
    {
        private readonly Color BgDark = Color.FromArgb(18, 18, 22);
        private readonly Color CardDark = Color.FromArgb(28, 28, 36);
        private readonly Color InputDark = Color.FromArgb(38, 38, 48);
        private readonly Color AccentRed = Color.FromArgb(229, 32, 43);
        private readonly Color TextPrimary = Color.FromArgb(245, 245, 250);
        private readonly Color TextSecondary = Color.FromArgb(175, 178, 190);

        private Label lblSub;
        private Label lblLangPrompt;
        private ComboBox cmbLanguage;
        private Label lblFolder;
        private TextBox txtInstallDir;
        private Button btnBrowse;
        private CheckBox chkDesktop;
        private CheckBox chkStartMenu;
        private CheckBox chkLaunchNow;
        private ProgressBar pbInstall;
        private Label lblStatus;
        private Button btnInstall;
        private Button btnCancel;

        private bool isInstalled = false;

        public SetupForm()
        {
            Text = "Social Downloader v2.4 Setup — Installer";
            Size = new Size(640, 530);
            FormBorderStyle = FormBorderStyle.FixedDialog;
            MaximizeBox = false;
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = BgDark;
            ForeColor = TextPrimary;
            Font = new Font("Segoe UI", 9.5f);

            InitializeWizardUi();
            ApplySelectedLanguage();
        }

        private void InitializeWizardUi()
        {
            Panel pnlHeader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 88,
                BackColor = CardDark
            };

            PictureBox picLogo = new PictureBox
            {
                Location = new Point(22, 16),
                Size = new Size(56, 56),
                SizeMode = PictureBoxSizeMode.Zoom
            };

            try
            {
                var asm = Assembly.GetExecutingAssembly();
                using (Stream s = asm.GetManifestResourceStream("YTDownloaderSetup.app_logo.png"))
                {
                    if (s != null) picLogo.Image = Image.FromStream(s);
                }
                using (Stream sIco = asm.GetManifestResourceStream("YTDownloaderSetup.app_icon.ico"))
                {
                    if (sIco != null) Icon = new Icon(sIco);
                }
            }
            catch { }

            pnlHeader.Controls.Add(picLogo);

            Label lblTitle = new Label
            {
                Text = "Social Downloader v2.4 Setup",
                Font = new Font("Segoe UI Semibold", 15f, FontStyle.Bold),
                ForeColor = TextPrimary,
                Location = new Point(92, 16),
                AutoSize = true
            };
            pnlHeader.Controls.Add(lblTitle);

            lblSub = new Label
            {
                Text = "Install Social Downloader (YouTube, FB, IG, Threads, LinkedIn, X, TikTok & More) on Windows",
                Font = new Font("Segoe UI", 9f),
                ForeColor = TextSecondary,
                Location = new Point(94, 48),
                AutoSize = true
            };
            pnlHeader.Controls.Add(lblSub);

            Controls.Add(pnlHeader);

            // 1. Language Selection Row (Default: English)
            lblLangPrompt = new Label
            {
                Text = "🌐 Select Application Language (Default: English):",
                Font = new Font("Segoe UI Semibold", 10f, FontStyle.Bold),
                Location = new Point(24, 104),
                AutoSize = true,
                ForeColor = Color.FromArgb(255, 215, 110)
            };
            Controls.Add(lblLangPrompt);

            cmbLanguage = new ComboBox
            {
                Location = new Point(24, 130),
                Width = 564,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = InputDark,
                ForeColor = TextPrimary,
                Font = new Font("Segoe UI Semibold", 10.5f)
            };
            foreach (var lang in SetupLanguages.All)
            {
                cmbLanguage.Items.Add(lang);
            }
            cmbLanguage.SelectedIndex = 0; // Default: English ("en")
            cmbLanguage.SelectedIndexChanged += (s, e) => ApplySelectedLanguage();
            Controls.Add(cmbLanguage);

            // 2. Installation Folder Row
            lblFolder = new Label
            {
                Text = "📁 Installation Folder:",
                Font = new Font("Segoe UI Semibold", 10f, FontStyle.Bold),
                Location = new Point(24, 174),
                AutoSize = true
            };
            Controls.Add(lblFolder);

            string defaultDir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Programs", "SocialDownloader");

            txtInstallDir = new TextBox
            {
                Text = defaultDir,
                Location = new Point(24, 200),
                Width = 454,
                BackColor = InputDark,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 10f)
            };
            Controls.Add(txtInstallDir);

            btnBrowse = new Button
            {
                Text = "Browse...",
                Location = new Point(488, 198),
                Size = new Size(100, 29),
                BackColor = Color.FromArgb(52, 54, 68),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnBrowse.FlatAppearance.BorderSize = 0;
            btnBrowse.Click += (s, e) =>
            {
                using (var dlg = new FolderBrowserDialog())
                {
                    dlg.SelectedPath = txtInstallDir.Text;
                    if (dlg.ShowDialog() == DialogResult.OK && !string.IsNullOrWhiteSpace(dlg.SelectedPath))
                    {
                        txtInstallDir.Text = Path.Combine(dlg.SelectedPath, "YTDownloader");
                    }
                }
            };
            Controls.Add(btnBrowse);

            // 3. Options Checkboxes
            chkDesktop = new CheckBox
            {
                Text = "Create a Desktop Shortcut",
                Location = new Point(24, 244),
                AutoSize = true,
                Checked = true,
                ForeColor = TextPrimary
            };
            Controls.Add(chkDesktop);

            chkStartMenu = new CheckBox
            {
                Text = "Create a Start Menu Shortcut",
                Location = new Point(24, 274),
                AutoSize = true,
                Checked = true,
                ForeColor = TextPrimary
            };
            Controls.Add(chkStartMenu);

            chkLaunchNow = new CheckBox
            {
                Text = "Launch YT Downloader after installation",
                Location = new Point(24, 304),
                AutoSize = true,
                Checked = true,
                ForeColor = TextPrimary
            };
            Controls.Add(chkLaunchNow);

            lblStatus = new Label
            {
                Text = "Click 'Install Now' to begin installation (Bundled: yt-dlp + FFmpeg)",
                Location = new Point(24, 348),
                Size = new Size(564, 22),
                ForeColor = TextSecondary
            };
            Controls.Add(lblStatus);

            pbInstall = new ProgressBar
            {
                Location = new Point(24, 374),
                Size = new Size(564, 24),
                Style = ProgressBarStyle.Continuous
            };
            Controls.Add(pbInstall);

            btnInstall = new Button
            {
                Text = "⬇ Install Now",
                Location = new Point(316, 422),
                Size = new Size(155, 42),
                BackColor = AccentRed,
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI Semibold", 10.5f, FontStyle.Bold),
                Cursor = Cursors.Hand
            };
            btnInstall.FlatAppearance.BorderSize = 0;
            btnInstall.Click += async (s, e) => await RunInstallAsync();
            Controls.Add(btnInstall);

            btnCancel = new Button
            {
                Text = "Close",
                Location = new Point(480, 422),
                Size = new Size(108, 42),
                BackColor = Color.FromArgb(52, 54, 68),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand
            };
            btnCancel.FlatAppearance.BorderSize = 0;
            btnCancel.Click += (s, e) => Close();
            Controls.Add(btnCancel);
        }

        private void ApplySelectedLanguage()
        {
            var lang = cmbLanguage.SelectedItem as SetupLanguage;
            if (lang == null) return;

            lblSub.Text = lang.Subtitle;
            lblLangPrompt.Text = lang.LangLabel;
            lblFolder.Text = lang.FolderLabel;
            chkDesktop.Text = lang.DesktopChk;
            chkStartMenu.Text = lang.StartMenuChk;
            chkLaunchNow.Text = lang.LaunchChk;
            if (!isInstalled)
            {
                lblStatus.Text = lang.ReadyStatus;
                btnInstall.Text = lang.InstallBtn;
            }
            else
            {
                btnInstall.Text = lang.FinishBtn;
            }
            btnCancel.Text = lang.CloseBtn;
        }

        private async Task RunInstallAsync()
        {
            var lang = (cmbLanguage.SelectedItem as SetupLanguage) ?? SetupLanguages.All[0];

            if (isInstalled)
            {
                LaunchInstalledApp();
                Close();
                return;
            }

            string targetDir = txtInstallDir.Text.Trim();
            if (string.IsNullOrEmpty(targetDir))
            {
                MessageBox.Show("Please select a valid installation folder.", "Folder Required", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            btnInstall.Enabled = false;
            btnBrowse.Enabled = false;
            cmbLanguage.Enabled = false;
            txtInstallDir.ReadOnly = true;
            chkDesktop.Enabled = false;
            chkStartMenu.Enabled = false;

            bool desktop = chkDesktop.Checked;
            bool startMenu = chkStartMenu.Checked;
            string selectedLangCode = lang.Code;

            try
            {
                await Task.Run(() =>
                {
                    InstallerEngine.Install(targetDir, selectedLangCode, desktop, startMenu, (pct, msg) =>
                    {
                        BeginInvoke(new Action(() =>
                        {
                            pbInstall.Value = Math.Max(0, Math.Min(100, pct));
                            lblStatus.Text = msg;
                        }));
                    });
                });

                isInstalled = true;
                btnInstall.Enabled = true;
                btnInstall.BackColor = Color.FromArgb(40, 167, 69);
                btnInstall.Text = lang.FinishBtn;

                if (chkLaunchNow.Checked)
                {
                    LaunchInstalledApp();
                    Close();
                }
                else
                {
                    MessageBox.Show("YT Downloader has been installed successfully!", "Installation Complete", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
            }
            catch (Exception ex)
            {
                btnInstall.Enabled = true;
                cmbLanguage.Enabled = true;
                lblStatus.Text = "⚠ Error: " + ex.Message;
                MessageBox.Show("Installation failed:\n" + ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void LaunchInstalledApp()
        {
            try
            {
                string exePath = Path.Combine(txtInstallDir.Text.Trim(), "YTDownloader.exe");
                if (File.Exists(exePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = exePath,
                        WorkingDirectory = txtInstallDir.Text.Trim(),
                        UseShellExecute = true
                    });
                }
            }
            catch { }
        }
    }
}
