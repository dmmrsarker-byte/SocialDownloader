using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Linq;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using System.Web.Script.Serialization;
using System.Windows.Forms;

namespace YTDownloaderWindows
{
    static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        static void Main(string[] args)
        {
            try { SetProcessDPIAware(); } catch { }
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            if (args != null && args.Any(a => string.Equals(a, "--qa-test", StringComparison.OrdinalIgnoreCase)))
            {
                Environment.Exit(QaTests.RunAllTests());
                return;
            }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    public enum SocialPlatform
    {
        YouTube,
        Pornhub,
        Facebook,
        Instagram,
        Threads,
        LinkedIn,
        TwitterX,
        TikTokOrOther
    }

    public class LanguageItem
    {
        public string Code { get; set; }
        public string DisplayName { get; set; }
        public override string ToString() { return DisplayName; }
    }

    public static class I18n
    {
        public static readonly List<LanguageItem> SupportedLanguages = new List<LanguageItem>
        {
            new LanguageItem { Code = "en", DisplayName = "English (Default)" },
            new LanguageItem { Code = "bn", DisplayName = "বাংলা (Bengali)" },
            new LanguageItem { Code = "hi", DisplayName = "हिन्दी (Hindi)" },
            new LanguageItem { Code = "ur", DisplayName = "اردو (Urdu)" },
            new LanguageItem { Code = "ar", DisplayName = "العربية (Arabic)" },
            new LanguageItem { Code = "es", DisplayName = "Español (Spanish)" },
            new LanguageItem { Code = "pt", DisplayName = "Português (Portuguese)" },
            new LanguageItem { Code = "ru", DisplayName = "Русский (Russian)" },
            new LanguageItem { Code = "id", DisplayName = "Bahasa Indonesia" }
        };

        public static string CurrentLang = "en";

        private static readonly Dictionary<string, Dictionary<string, string>> Dict =
            new Dictionary<string, Dictionary<string, string>>
        {
            {
                "en", new Dictionary<string, string>
                {
                    { "app_subtitle", "Universal Playlist, Music Cover, Best Audio & 4K Video Downloader for YT Music, YouTube, SoundCloud, FB, IG, X, TikTok & Any Site" },
                    { "btn_downloads_folder", "📂 Downloads" },
                    { "btn_update_engine", "🔄 Update Engine" },
                    { "engine_checking", "Checking engine..." },
                    { "auto_grab_chk", "⚡ Auto-grab & download copied links" },
                    { "cookies_chk", "Use Browser Cookies (for private/login videos):" },
                    { "prompt_unified", "🔗 Paste Any Playlist, YT Music, Audio, Social Media, Web or Adult Link (YT Music, YouTube, SoundCloud, FB, IG, X, TikTok, Pornhub & Any Site):" },
                    { "chk_mix", "Load full playlist for YouTube Radio Mix (&list=RD...)" },
                    { "btn_paste", "📋 Paste" },
                    { "btn_search", "🔍 Search" },
                    { "btn_searching", "⏳ Searching..." },
                    { "rb_video", "🎬 Video (MP4)" },
                    { "rb_audio", "🎵 Audio + Music Cover" },
                    { "best_mp4", "Best Resolution (MP4)" },
                    { "save_to", "Save Folder:" },
                    { "btn_browse", "Change Folder..." },
                    { "btn_download_now", "⬇ Start Download" },
                    { "playlist_header", "Playlist / Album Tracks (Select items to download):" },
                    { "btn_select_all", "Select All" },
                    { "btn_select_none", "Select None" },
                    { "col_title", "Title" },
                    { "col_channel", "Channel / Page" },
                    { "col_duration", "Duration" },
                    { "tab_queue", "⬇ Active Downloads (Queue)" },
                    { "tab_history", "📜 Download History" },
                    { "tab_monetize", "💰 Monetization & Ads" },
                    { "no_active_dl", "No active downloads" },
                    { "btn_play_file", "▶ Play" },
                    { "btn_open_folder", "📂 Folder" },
                    { "btn_pause_resume", "⏸ Pause / ▶ Resume" },
                    { "btn_retry_link", "🔄 Retry / Link" },
                    { "btn_rename", "✏️ Rename" },
                    { "btn_save_cover", "🖼 Save Cover" },
                    { "btn_copy_name", "📋 Copy Name" },
                    { "btn_copy_link", "🔗 Copy Link" },
                    { "btn_cancel", "✖ Cancel" },
                    { "dlg_rename_title", "Rename Video / File" },
                    { "dlg_rename_prompt", "Enter new video / file name:" },
                    { "msg_copied_name", "Copied name to clipboard!" },
                    { "msg_copied_link", "Copied link to clipboard!" },
                    { "msg_failed_link_saved", "\n\n✔ This link has been automatically saved to Queue & History.\nWould you like to queue a Direct Download from this link right now?" },
                    { "col_format", "Platform & Quality" },
                    { "col_status", "Status" },
                    { "col_progress", "Progress" },
                    { "col_speed", "Speed" },
                    { "col_eta", "ETA" },
                    { "lbl_search_hist", "🔍 Search History:" },
                    { "btn_clear_hist", "🗑 Clear History" },
                    { "col_date", "Date & Time" },
                    { "col_size", "Size" },
                    { "col_path", "File Path" },
                    { "status_queued", "Queued" },
                    { "status_starting", "Starting..." },
                    { "status_downloading", "Downloading..." },
                    { "status_paused", "⏸ Paused" },
                    { "status_processing", "Merging / Processing..." },
                    { "status_done", "✔ Completed" },
                    { "status_canceled", "✖ Canceled" },
                    { "status_failed", "⚠ Failed: " },
                    { "msg_enter_url", "Please paste a valid YouTube, Facebook, or Instagram link." },
                    { "msg_no_info", "Could not retrieve video info. Please check the URL." },
                    { "msg_cookie_hint", "\n\nTip: Enable 'Use Browser Cookies' at the top for private or age-restricted Facebook/Instagram videos." },
                    { "msg_select_one", "Please select at least one video from the playlist." },
                    { "msg_engine_updated", "yt-dlp engine has been updated to the latest version!" },
                    { "msg_confirm_clear_hist", "Are you sure you want to clear all download history?" },
                    { "mon_header", "Configure your Website (Google AdSense page), Direct Ad Link (Adsterra / PropellerAds) or Sponsor Banner:" },
                    { "mon_headline", "Banner Headline Text:" },
                    { "mon_btn_text", "Banner Button Text:" },
                    { "mon_target_url", "Target Website / Ad URL:" },
                    { "mon_chk_first", "Automatically open my Website / Ad URL in browser on user's first download" },
                    { "mon_btn_save", "💾 Save Ad Settings" },
                    { "mon_saved_msg", "Monetization and banner settings saved successfully!" }
                }
            },
            {
                "bn", new Dictionary<string, string>
                {
                    { "app_subtitle", "হাই-স্পিড YouTube, Facebook, Instagram, Threads, LinkedIn, X, TikTok ও অডিও ডাউনলোডার" },
                    { "btn_downloads_folder", "📂 ডাউনলোড ফোল্ডার" },
                    { "btn_update_engine", "🔄 ইঞ্জিন আপডেট" },
                    { "engine_checking", "ইঞ্জিন চেক করা হচ্ছে..." },
                    { "auto_grab_chk", "⚡ লিংক কপি করলেই অটো-ডাউনলোড হবে" },
                    { "cookies_chk", "Browser কুকিজ (প্রাইভেট/লগইন ভিডিওর জন্য):" },
                    { "prompt_unified", "🔗 যেকোনো সোশ্যাল মিডিয়া লিংক পেস্ট বা কপি করুন (YouTube, FB, IG, Threads, LinkedIn, X, TikTok):" },
                    { "chk_mix", "YouTube Radio Mix (&list=RD...) পুরো প্লেলিস্ট লোড করুন" },
                    { "btn_paste", "📋 পেস্ট" },
                    { "btn_search", "🔍 খুঁজুন (Search)" },
                    { "btn_searching", "⏳ খোঁজা হচ্ছে..." },
                    { "rb_video", "🎬 ভিডিও (MP4)" },
                    { "rb_audio", "🎵 শুধু অডিও" },
                    { "best_mp4", "সর্বোচ্চ রেজোলিউশন (Best MP4)" },
                    { "save_to", "সেভ ফোল্ডার:" },
                    { "btn_browse", "ফোল্ডার পরিবর্তন..." },
                    { "btn_download_now", "⬇ ডাউনলোড শুরু করুন" },
                    { "playlist_header", "প্লেলিস্টের ভিডিওসমূহ (টিক দিয়ে নির্বাচন করুন):" },
                    { "btn_select_all", "সব নির্বাচন" },
                    { "btn_select_none", "সব বাতিল" },
                    { "col_title", "শিরোনাম (Title)" },
                    { "col_channel", "চ্যানেল / পেজ" },
                    { "col_duration", "সময়কাল" },
                    { "tab_queue", "⬇ চলমান ডাউনলোড (Queue)" },
                    { "tab_history", "📜 ডাউনলোড হিস্ট্রি (History)" },
                    { "tab_monetize", "💰 মনিটাইজেশন ও অ্যাড সেটআপ" },
                    { "no_active_dl", "কোনো ডাউনলোড চলছে না" },
                    { "btn_play_file", "▶ চালান" },
                    { "btn_open_folder", "📂 ফোল্ডার" },
                    { "btn_pause_resume", "⏸ বিরতি / ▶ চালু" },
                    { "btn_retry_link", "🔄 আবার চেষ্টা / লিংক" },
                    { "btn_rename", "✏️ রিনেম" },
                    { "btn_copy_name", "📋 নাম কপি" },
                    { "btn_copy_link", "🔗 লিংক কপি" },
                    { "btn_cancel", "✖ বাতিল করুন" },
                    { "dlg_rename_title", "নাম পরিবর্তন করুন (Rename)" },
                    { "dlg_rename_prompt", "নতুন নাম লিখুন:" },
                    { "msg_copied_name", "নাম কপি হয়েছে!" },
                    { "msg_copied_link", "লিংক কপি হয়েছে!" },
                    { "msg_failed_link_saved", "\n\n✔ এই লিংকটি অটোমেটিক Queue এবং History-তে সেভ করা হয়েছে।\nআপনি কি এখনই সরাসরি এই লিংক থেকে ডাউনলোড শুরু করতে চান?" },
                    { "col_format", "প্ল্যাটফর্ম ও কোয়ালিটি" },
                    { "col_status", "অবস্থা (Status)" },
                    { "col_progress", "অগ্রগতি (%)" },
                    { "col_speed", "স্পিড (Speed)" },
                    { "col_eta", "বাকি সময় (ETA)" },
                    { "lbl_search_hist", "🔍 হিস্ট্রি খুঁজুন:" },
                    { "btn_clear_hist", "🗑 হিস্ট্রি মুছুন" },
                    { "col_date", "তারিখ ও সময়" },
                    { "col_size", "সাইজ" },
                    { "col_path", "ফাইল পাথ" },
                    { "status_queued", "অপেক্ষমাণ (Queued)" },
                    { "status_starting", "শুরু হচ্ছে..." },
                    { "status_downloading", "ডাউনলোড হচ্ছে..." },
                    { "status_processing", "প্রসেসিং / একত্র করা হচ্ছে..." },
                    { "status_done", "✔ সম্পন্ন (Done)" },
                    { "status_canceled", "✖ বাতিল (Canceled)" },
                    { "status_failed", "⚠ ব্যর্থ: " },
                    { "msg_enter_url", "অনুগ্রহ করে YouTube, Facebook বা Instagram-এর একটি লিংক পেস্ট করুন।" },
                    { "msg_no_info", "ভিডিওর তথ্য পাওয়া যায়নি। লিংকটি সঠিক কিনা তা যাচাই করুন।" },
                    { "msg_cookie_hint", "\n\nটিপস: প্রাইভেট বা রিলস ভিডিওর ক্ষেত্রে উপরের 'Browser কুকিজ' অপশনে টিক দিয়ে আবার চেষ্টা করুন।" },
                    { "msg_select_one", "তালিকা থেকে অন্তত একটি ভিডিও নির্বাচন করুন।" },
                    { "msg_engine_updated", "yt-dlp ইঞ্জিন সর্বশেষ সংস্করণে আপডেট করা হয়েছে!" },
                    { "msg_confirm_clear_hist", "আপনি কি সমস্ত ডাউনলোড হিস্ট্রি মুছে ফেলতে চান?" },
                    { "mon_header", "অ্যাপের ভেতরে আপনার ওয়েবসাইট (Google AdSense পেজ), Direct Ad Link বা অ্যাফিলিয়েট ব্যানার সেট করুন:" },
                    { "mon_headline", "ব্যানার হেডলাইন টেক্সট:" },
                    { "mon_btn_text", "বাটনের টেক্সট:" },
                    { "mon_target_url", "টার্গেট ওয়েবসাইট / Ad URL:" },
                    { "mon_chk_first", "ব্যবহারকারী প্রথমবার ডাউনলোড করার সময় স্বয়ংক্রিয়ভাবে আমার ওয়েবসাইট/অ্যাড পেজ ব্রাউজারে ওপেন হবে" },
                    { "mon_btn_save", "💾 অ্যাড সেটিংস সেভ করুন" },
                    { "mon_saved_msg", "মনিটাইজেশন ও ব্যানার সেটিংস সফলভাবে সেভ হয়েছে!" }
                }
            },
            {
                "hi", new Dictionary<string, string>
                {
                    { "app_subtitle", "हाई-स्पीड YouTube (4K/1080p/Playlist), Facebook, Instagram और ऑडियो (MP3/M4A) डाउनलोडर" },
                    { "btn_downloads_folder", "📂 डाउनलोड फ़ोल्डर" },
                    { "btn_update_engine", "🔄 इंजन अपडेट" },
                    { "engine_checking", "इंजन की जाँच हो रही है..." },
                    { "platform_label", "प्लेटफ़ॉर्म:" },
                    { "pill_other", "🎵 TikTok / अन्य" },
                    { "cookies_chk", "ब्राउज़र कुकीज़ (FB/Insta लॉगिन वीडियो के लिए):" },
                    { "prompt_yt", "▶ YouTube वीडियो, Shorts या प्लेलिस्ट लिंक पेस्ट करें:" },
                    { "prompt_fb", "📘 Facebook वीडियो, Watch, Reels या Share लिंक पेस्ट करें:" },
                    { "prompt_ig", "📸 Instagram Reels, वीडियो या पोस्ट लिंक पेस्ट करें:" },
                    { "prompt_other", "🎵 TikTok, X (Twitter) या कोई अन्य वीडियो लिंक पेस्ट करें:" },
                    { "chk_mix", "YouTube Radio Mix (&list=RD...) की पूरी प्लेलिस्ट लोड करें" },
                    { "btn_paste", "📋 पेस्ट" },
                    { "btn_search", "🔍 खोजें (Search)" },
                    { "btn_searching", "⏳ खोजा जा रहा है..." },
                    { "rb_video", "🎬 वीडियो (MP4)" },
                    { "rb_audio", "🎵 केवल ऑडियो" },
                    { "best_mp4", "सर्वश्रेष्ठ रेज़ोल्यूशन (Best MP4)" },
                    { "save_to", "सेव फ़ोल्डर:" },
                    { "btn_browse", "फ़ोल्डर बदलें..." },
                    { "btn_download_now", "⬇ डाउनलोड शुरू करें" },
                    { "playlist_header", "प्लेलिस्ट वीडियो (डाउनलोड के लिए चुनें):" },
                    { "btn_select_all", "सभी चुनें" },
                    { "btn_select_none", "कोई नहीं" },
                    { "col_title", "शीर्षक (Title)" },
                    { "col_channel", "चैनल / पेज" },
                    { "col_duration", "अवधि" },
                    { "tab_queue", "⬇ चालू डाउनलोड (Queue)" },
                    { "tab_history", "📜 डाउनलोड हिस्ट्री (History)" },
                    { "tab_monetize", "💰 मोनेटाइजेशन और विज्ञापन" },
                    { "no_active_dl", "कोई डाउनलोड नहीं चल रहा है" },
                    { "btn_play_file", "▶ फ़ाइल चलाएँ" },
                    { "btn_open_folder", "📂 फ़ोल्डर में देखें" },
                    { "btn_cancel", "✖ रद्द करें" },
                    { "col_format", "प्लेटफ़ॉर्म और क्वालिटी" },
                    { "col_status", "स्थिति (Status)" },
                    { "col_progress", "प्रगति (%)" },
                    { "col_speed", "गति (Speed)" },
                    { "col_eta", "शेष समय (ETA)" },
                    { "lbl_search_hist", "🔍 हिस्ट्री खोजें:" },
                    { "btn_clear_hist", "🗑 हिस्ट्री मिटाएँ" },
                    { "col_date", "दिनांक और समय" },
                    { "col_size", "आकार" },
                    { "col_path", "फ़ाइल पथ" },
                    { "status_queued", "कतार में (Queued)" },
                    { "status_starting", "शुरू हो रहा है..." },
                    { "status_downloading", "डाउनलोड हो रहा है..." },
                    { "status_processing", "प्रोसेसिंग / मर्ज हो रहा है..." },
                    { "status_done", "✔ पूर्ण (Done)" },
                    { "status_canceled", "✖ रद्द (Canceled)" },
                    { "status_failed", "⚠ विफल: " },
                    { "msg_enter_url", "कृपया YouTube, Facebook या Instagram का लिंक पेस्ट करें।" },
                    { "msg_no_info", "वीडियो जानकारी नहीं मिली। कृपया लिंक की जाँच करें।" },
                    { "msg_cookie_hint", "\n\nसुझाव: प्राइवेट या रील्स वीडियो के लिए ऊपर 'ब्राउज़र कुकीज़' विकल्प चुनें।" },
                    { "msg_select_one", "कृपया प्लेलिस्ट से कम से कम एक वीडियो चुनें।" },
                    { "msg_engine_updated", "yt-dlp इंजन नवीनतम संस्करण में अपडेट हो गया है!" },
                    { "msg_confirm_clear_hist", "क्या आप संपूर्ण डाउनलोड हिस्ट्री मिटाना चाहते हैं?" },
                    { "mon_header", "ऐप के अंदर अपनी वेबसाइट (Google AdSense पेज), Direct Ad Link या स्पॉन्सर बैनर सेट करें:" },
                    { "mon_headline", "बैनर हेडलाइन टेक्स्ट:" },
                    { "mon_btn_text", "बटन टेक्स्ट:" },
                    { "mon_target_url", "टारगेट वेबसाइट / Ad URL:" },
                    { "mon_chk_first", "पहले डाउनलोड पर मेरी वेबसाइट/विज्ञापन पेज ब्राउज़र में स्वतः खोलें" },
                    { "mon_btn_save", "💾 सेटिंग्स सेव करें" },
                    { "mon_saved_msg", "मोनेटाइजेशन और बैनर सेटिंग्स सफलतापूर्वक सेव हो गईं!" }
                }
            },
            {
                "es", new Dictionary<string, string>
                {
                    { "app_subtitle", "Descargador rápido de YouTube (4K/1080p/Playlist), Facebook, Instagram y Audio (MP3/M4A)" },
                    { "btn_downloads_folder", "📂 Descargas" },
                    { "btn_update_engine", "🔄 Actualizar" },
                    { "engine_checking", "Verificando motor..." },
                    { "platform_label", "Plataforma:" },
                    { "pill_other", "🎵 TikTok / Otros" },
                    { "cookies_chk", "Usar Cookies del navegador (para FB/Insta):" },
                    { "prompt_yt", "▶ Pegue el enlace de YouTube Video, Shorts o Playlist:" },
                    { "prompt_fb", "📘 Pegue el enlace de Facebook Video, Watch o Reels:" },
                    { "prompt_ig", "📸 Pegue el enlace de Instagram Reels o Post:" },
                    { "prompt_other", "🎵 Pegue el enlace de TikTok, X o cualquier video:" },
                    { "chk_mix", "Cargar lista completa para YouTube Radio Mix (&list=RD...)" },
                    { "btn_paste", "📋 Pegar" },
                    { "btn_search", "🔍 Buscar" },
                    { "btn_searching", "⏳ Buscando..." },
                    { "rb_video", "🎬 Video (MP4)" },
                    { "rb_audio", "🎵 Solo Audio" },
                    { "best_mp4", "Mejor Resolución (MP4)" },
                    { "save_to", "Carpeta:" },
                    { "btn_browse", "Cambiar..." },
                    { "btn_download_now", "⬇ Descargar Ahora" },
                    { "playlist_header", "Videos de la lista (Seleccione para descargar):" },
                    { "btn_select_all", "Todos" },
                    { "btn_select_none", "Ninguno" },
                    { "tab_queue", "⬇ Descargas Activas (Queue)" },
                    { "tab_history", "📜 Historial de Descargas" },
                    { "tab_monetize", "💰 Monetización y Anuncios" },
                    { "no_active_dl", "Sin descargas activas" },
                    { "btn_play_file", "▶ Reproducir" },
                    { "btn_open_folder", "📂 Mostrar en Carpeta" },
                    { "btn_cancel", "✖ Cancelar" }
                }
            },
            {
                "ar", new Dictionary<string, string>
                {
                    { "app_subtitle", "برنامج تحميل الفيديو والصوت من يوتيوب وفيسبوك وإنستغرام وتيك توك بجودة عالية" },
                    { "btn_downloads_folder", "📂 المجلد" },
                    { "btn_update_engine", "🔄 تحديث المحرك" },
                    { "platform_label", "المنصة:" },
                    { "pill_other", "🎵 TikTok / أخرى" },
                    { "cookies_chk", "استخدام كوكيز المتصفح (لفيديوهات فيسبوك/إنستغرام):" },
                    { "prompt_yt", "▶ الصق رابط فيديو أو قائمة تشغيل يوتيوب:" },
                    { "prompt_fb", "📘 الصق رابط فيديو أو ريلز فيسبوك:" },
                    { "prompt_ig", "📸 الصق رابط ريلز أو منشور إنستغرام:" },
                    { "prompt_other", "🎵 الصق رابط تيك توك أو أي موقع آخر:" },
                    { "btn_paste", "📋 لصق" },
                    { "btn_search", "🔍 بحث" },
                    { "btn_searching", "⏳ جاري البحث..." },
                    { "rb_video", "🎬 فيديو (MP4)" },
                    { "rb_audio", "🎵 صوت فقط" },
                    { "best_mp4", "أفضل دقة (MP4)" },
                    { "save_to", "مجلد الحفظ:" },
                    { "btn_browse", "تغيير المجلد..." },
                    { "btn_download_now", "⬇ بدء التحميل" },
                    { "tab_queue", "⬇ التحميلات الجارية" },
                    { "tab_history", "📜 سجل التحميلات" },
                    { "tab_monetize", "💰 الإعلانات والأرباح" },
                    { "btn_play_file", "▶ تشغيل" },
                    { "btn_open_folder", "📂 فتح المجلد" },
                    { "btn_cancel", "✖ إلغاء" }
                }
            },
            {
                "ur", new Dictionary<string, string>
                {
                    { "app_subtitle", "یوٹیوب، فیس بک، انسٹاگرام اور آڈیو (MP3/M4A) کے لیے تیز رفتار ڈاؤن لوڈر" },
                    { "btn_downloads_folder", "📂 ڈاؤن لوڈ فولڈر" },
                    { "btn_update_engine", "🔄 انجن اپ ڈیٹ" },
                    { "platform_label", "پلیٹ فارم:" },
                    { "pill_other", "🎵 TikTok / دیگر" },
                    { "cookies_chk", "براؤزر کوکیز استعمال کریں (FB/Insta کے لیے):" },
                    { "prompt_yt", "▶ یوٹیوب ویڈیو، شارٹس یا پلے لسٹ کا لنک پیسٹ کریں:" },
                    { "prompt_fb", "📘 فیس بک ویڈیو یا ریلز کا لنک پیسٹ کریں:" },
                    { "prompt_ig", "📸 انسٹاگرام ریلز یا پوسٹ کا لنک پیسٹ کریں:" },
                    { "prompt_other", "🎵 ٹک ٹاک یا کسی بھی ویڈیو کا لنک پیسٹ کریں:" },
                    { "btn_paste", "📋 پیسٹ" },
                    { "btn_search", "🔍 تلاش کریں" },
                    { "btn_searching", "⏳ تلاش جاری ہے..." },
                    { "rb_video", "🎬 ویڈیو (MP4)" },
                    { "rb_audio", "🎵 صرف آڈیو" },
                    { "best_mp4", "بہترین کوالٹی (MP4)" },
                    { "save_to", "فولڈر:" },
                    { "btn_browse", "فولڈر بدلیں..." },
                    { "btn_download_now", "⬇ ڈاؤن لوڈ শুরু کریں" },
                    { "tab_queue", "⬇ جاری ڈاؤن لوڈز" },
                    { "tab_history", "📜 ڈاؤن لوڈ ہسٹری" },
                    { "tab_monetize", "💰 مونیٹائزیشن اور اشتہارات" },
                    { "btn_play_file", "▶ فائل چلائیں" },
                    { "btn_open_folder", "📂 فولڈر کھولیں" },
                    { "btn_cancel", "✖ منسوخ کریں" }
                }
            },
            {
                "pt", new Dictionary<string, string>
                {
                    { "app_subtitle", "Baixador rápido de vídeos do YouTube (4K/1080p), Facebook, Instagram e Áudio (MP3/M4A)" },
                    { "btn_downloads_folder", "📂 Pasta Downloads" },
                    { "btn_update_engine", "🔄 Atualizar Motor" },
                    { "platform_label", "Plataforma:" },
                    { "pill_other", "🎵 TikTok / Outros" },
                    { "prompt_yt", "▶ Cole o link do vídeo ou playlist do YouTube:" },
                    { "prompt_fb", "📘 Cole o link do vídeo ou Reels do Facebook:" },
                    { "prompt_ig", "📸 Cole o link do Reels ou Post do Instagram:" },
                    { "prompt_other", "🎵 Cole o link do TikTok ou outro site:" },
                    { "btn_paste", "📋 Colar" },
                    { "btn_search", "🔍 Buscar" },
                    { "btn_download_now", "⬇ Iniciar Download" },
                    { "tab_queue", "⬇ Downloads Ativos" },
                    { "tab_history", "📜 Histórico" },
                    { "tab_monetize", "💰 Monetização e Anúncios" }
                }
            },
            {
                "ru", new Dictionary<string, string>
                {
                    { "app_subtitle", "Быстрое скачивание видео с YouTube (4K/1080p), Facebook, Instagram и аудио (MP3/M4A)" },
                    { "btn_downloads_folder", "📂 Папка загрузок" },
                    { "btn_update_engine", "🔄 Обновить" },
                    { "platform_label", "Платформа:" },
                    { "pill_other", "🎵 TikTok / Другие" },
                    { "prompt_yt", "▶ Вставьте ссылку на видео или плейлист YouTube:" },
                    { "prompt_fb", "📘 Вставьте ссылку на видео или Reels Facebook:" },
                    { "prompt_ig", "📸 Вставьте ссылку на Reels или пост Instagram:" },
                    { "prompt_other", "🎵 Вставьте ссылку на TikTok или другой сайт:" },
                    { "btn_paste", "📋 Вставить" },
                    { "btn_search", "🔍 Найти" },
                    { "btn_download_now", "⬇ Скачать сейчас" },
                    { "tab_queue", "⬇ Активные загрузки" },
                    { "tab_history", "📜 История загрузок" },
                    { "tab_monetize", "💰 Монетизация и реклама" }
                }
            },
            {
                "id", new Dictionary<string, string>
                {
                    { "app_subtitle", "Pengunduh Cepat Video YouTube (4K/1080p), Facebook, Instagram & Audio (MP3/M4A)" },
                    { "btn_downloads_folder", "📂 Folder Unduhan" },
                    { "btn_update_engine", "🔄 Perbarui Mesin" },
                    { "platform_label", "Platform:" },
                    { "pill_other", "🎵 TikTok / Lainnya" },
                    { "prompt_yt", "▶ Tempel tautan Video, Shorts, atau Playlist YouTube:" },
                    { "prompt_fb", "📘 Tempel tautan Video atau Reels Facebook:" },
                    { "prompt_ig", "📸 Tempel tautan Reels atau Postingan Instagram:" },
                    { "prompt_other", "🎵 Tempel tautan TikTok atau video lainnya:" },
                    { "btn_paste", "📋 Tempel" },
                    { "btn_search", "🔍 Cari" },
                    { "btn_download_now", "⬇ Mulai Unduh" },
                    { "tab_queue", "⬇ Unduhan Aktif" },
                    { "tab_history", "📜 Riwayat Unduhan" },
                    { "tab_monetize", "💰 Monetisasi & Iklan" }
                }
            }
        };

        public static string T(string key)
        {
            Dictionary<string, string> langMap;
            if (Dict.TryGetValue(CurrentLang, out langMap) && langMap.ContainsKey(key))
            {
                return langMap[key];
            }
            if (Dict["en"].ContainsKey(key))
            {
                return Dict["en"][key];
            }
            return key;
        }
    }

    public class PlaylistEntryItem
    {
        public string Id { get; set; }
        public string Title { get; set; }
        public string Url { get; set; }
        public long DurationSec { get; set; }
        public string Uploader { get; set; }
        public string Thumbnail { get; set; }
    }

    public class MediaMetadata
    {
        public string Url { get; set; }
        public string RawInputUrl { get; set; }
        public string Title { get; set; }
        public string Uploader { get; set; }
        public string Thumbnail { get; set; }
        public long DurationSec { get; set; }
        public bool IsPlaylist { get; set; }
        public bool IsAudioCentric { get; set; }
        public SocialPlatform Platform { get; set; }
        public List<int> VideoHeights { get; set; }
        public List<PlaylistEntryItem> Entries { get; set; }

        public MediaMetadata()
        {
            Platform = SocialPlatform.YouTube;
            VideoHeights = new List<int>();
            Entries = new List<PlaylistEntryItem>();
        }
    }

    public class DownloadJob
    {
        public string Id { get; set; }
        public string Url { get; set; }
        public string RawInputUrl { get; set; }
        public string Title { get; set; }
        public string CustomTitle { get; set; }
        public string Uploader { get; set; }
        public string PlaylistTitle { get; set; }
        public SocialPlatform Platform { get; set; }
        public bool IsAudio { get; set; }
        public string AudioFormat { get; set; }
        public int? VideoHeight { get; set; }
        public string OutputDir { get; set; }
        public string BrowserCookies { get; set; }
        public string Status { get; set; }
        public float Progress { get; set; }
        public string Speed { get; set; }
        public string Eta { get; set; }
        public string OutputFilePath { get; set; }
        [ScriptIgnore]
        public Process RunningProcess { get; set; }
        public bool Canceled { get; set; }
        public bool Paused { get; set; }
        public bool Finished { get; set; }
        public bool Failed { get; set; }
        public long RunGeneration;
        public bool IsActive { get; set; }

        public DownloadJob()
        {
            Id = Guid.NewGuid().ToString("N");
            Status = I18n.T("status_queued");
            Progress = 0f;
            Speed = "-";
            Eta = "-";
            OutputFilePath = "";
            Platform = SocialPlatform.YouTube;
        }

        public string PlatformName
        {
            get
            {
                switch (Platform)
                {
                    case SocialPlatform.Pornhub: return "Pornhub";
                    case SocialPlatform.Facebook: return "Facebook";
                    case SocialPlatform.Instagram: return "Instagram";
                    case SocialPlatform.Threads: return "Threads";
                    case SocialPlatform.LinkedIn: return "LinkedIn";
                    case SocialPlatform.TwitterX: return "X (Twitter)";
                    case SocialPlatform.TikTokOrOther: return "TikTok/Social";
                    default: return "YouTube";
                }
            }
        }

        public string QualityLabel
        {
            get
            {
                if (IsAudio) return PlatformName + " • AUDIO " + (AudioFormat ?? "mp3").ToUpperInvariant();
                return PlatformName + " • " + (VideoHeight.HasValue ? (VideoHeight.Value + "p MP4") : "Best MP4");
            }
        }
    }

    public class HistoryRecord
    {
        public string Id { get; set; }
        public string DateStr { get; set; }
        public string Title { get; set; }
        public string Uploader { get; set; }
        public string Quality { get; set; }
        public string FilePath { get; set; }
        public string Url { get; set; }
        public long SizeBytes { get; set; }
        public string Status { get; set; }
    }

    public class AdConfig
    {
        public string BannerHeadline { get; set; }
        public string BannerButtonText { get; set; }
        public string TargetUrl { get; set; }
        public bool OpenSponsorOnFirstDownload { get; set; }

        public AdConfig()
        {
            BannerHeadline = "🔥 Visit our website for free tools, updates & pro guides — Configure your own AdSense / Ad URL in Monetization tab!";
            BannerButtonText = "🌐 Visit Now";
            TargetUrl = "https://www.google.com/adsense/start/";
            OpenSponsorOnFirstDownload = false;
        }
    }

    public class MainForm : Form
    {
        // Theme Colors
        private readonly Color BgDark = Color.FromArgb(18, 18, 22);
        private readonly Color CardDark = Color.FromArgb(28, 28, 35);
        private readonly Color CardInput = Color.FromArgb(38, 38, 48);
        private readonly Color AccentRed = Color.FromArgb(229, 32, 43);
        private readonly Color AccentFbBlue = Color.FromArgb(24, 119, 242);
        private readonly Color AccentInstaPink = Color.FromArgb(225, 48, 108);
        private readonly Color AccentTikTokTeal = Color.FromArgb(0, 175, 160);
        private readonly Color InactivePill = Color.FromArgb(44, 46, 58);
        private readonly Color TextPrimary = Color.FromArgb(245, 245, 250);
        private readonly Color TextSecondary = Color.FromArgb(170, 172, 185);
        private readonly Color SuccessGreen = Color.FromArgb(46, 204, 113);

        // Paths
        private readonly string AppDir;
        private readonly string BinDir;
        private readonly string YtDlpPath;
        private readonly string FfmpegPath;
        private readonly string DataDir;
        private readonly string HistoryFilePath;
        private readonly string SavedJobsFilePath;
        private readonly string AdConfigFilePath;
        private readonly string LangConfigFilePath;
        private string DownloadFolder;

        // UI Controls
        private Label lblAppSubtitle;
        private Label lblEngineStatus;
        private ComboBox cmbLanguage;
        private Button btnOpenDownloadsHeader;
        private Button btnUpdateEngine;

        private CheckBox chkAutoClipboard;
        private System.Windows.Forms.Timer clipboardTimer;
        private System.Windows.Forms.Timer pasteDebounceTimer;
        private string lastAutoGrabbedUrl = "";
        private string lastAutoCheckedInputUrl = "";
        private Label lblUrlPrompt;
        private TextBox txtUrl;
        private Button btnFetch;
        private Button btnPaste;
        private Button btnClearUrl;
        private CheckBox chkAllowMixPlaylist;
        private CheckBox chkUseBrowserCookies;
        private ComboBox cmbCookieBrowser;

        // Media Preview Card
        private Panel pnlMediaCard;
        private PictureBox picThumbnail;
        private Label lblPlatformBadge;
        private Label lblMediaTitle;
        private TextBox txtCustomTitle;
        private Button btnPreviewSaveCover;
        private Button btnPreviewCopyName;
        private Button btnPreviewCopyLink;
        private Label lblMediaSub;
        private RadioButton rbVideo;
        private RadioButton rbAudio;
        private ComboBox cmbVideoQuality;
        private ComboBox cmbAudioFormat;
        private Label lblSaveTo;
        private TextBox txtOutputFolder;
        private Button btnBrowseFolder;
        private Button btnDownloadNow;

        // Playlist Selection Panel
        private Panel pnlPlaylist;
        private Label lblPlaylistCount;
        private Button btnSelectAll;
        private Button btnSelectNone;
        private ListView lvPlaylist;

        // Bottom Tabs (Queue + History + Monetization)
        private TabControl tabBottom;
        private TabPage tabQueue;
        private TabPage tabHistory;
        private TabPage tabMonetize;

        private ListView lvQueue;
        private ProgressBar pbActive;
        private Label lblActiveProgress;
        private Button btnRetrySelected;
        private Button btnRenameSelected;
        private Button btnCopyNameSelected;
        private Button btnCopyLinkSelected;
        private Button btnPauseResumeSelected;
        private Button btnCancelSelected;
        private Button btnOpenSelectedFile;
        private Button btnOpenSelectedFolder;

        private Label lblSearchHist;
        private TextBox txtHistorySearch;
        private ListView lvHistory;
        private Button btnHistoryRedownload;
        private Button btnHistoryRename;
        private Button btnHistoryCopyName;
        private Button btnHistoryCopyLink;
        private Button btnHistoryPlay;
        private Button btnHistoryFolder;
        private Button btnHistoryClear;

        // Monetization Controls
        private Label lblMonHeader;
        private Label lblMon1;
        private Label lblMon2;
        private Label lblMon3;
        private CheckBox chkMonFirstDl;
        private Button btnMonSave;

        // Sponsor / Ad Banner Bar
        private Panel pnlAdBanner;
        private Label lblAdBannerText;
        private Button btnAdBannerAction;

        // State
        private SocialPlatform selectedPlatform = SocialPlatform.YouTube;
        private MediaMetadata currentMedia;
        private AdConfig adConfig = new AdConfig();
        private bool sponsorOpenedThisSession = false;
        private readonly List<DownloadJob> jobs = new List<DownloadJob>();
        private readonly List<HistoryRecord> history = new List<HistoryRecord>();
        private readonly SemaphoreSlim downloadLock = new SemaphoreSlim(2, 2);
        private CancellationTokenSource fetchCts;
        private long fetchSequence = 0;
        private Task engineInitTask;

        public MainForm()
        {
            AppDir = AppDomain.CurrentDomain.BaseDirectory;
            DataDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "YTDownloaderWindows");
            Directory.CreateDirectory(DataDir);

            BinDir = ResolveBinDirectory(AppDir, DataDir);
            YtDlpPath = Path.Combine(BinDir, "yt-dlp.exe");
            FfmpegPath = Path.Combine(BinDir, "ffmpeg.exe");

            HistoryFilePath = Path.Combine(DataDir, "history.json");
            SavedJobsFilePath = Path.Combine(DataDir, "saved_jobs_v21.json");
            AdConfigFilePath = Path.Combine(DataDir, "ad_config.json");
            LangConfigFilePath = Path.Combine(DataDir, "language.cfg");

            string userDownloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
            DownloadFolder = Path.Combine(userDownloads, "SocialDownloader");
            Directory.CreateDirectory(DownloadFolder);

            LoadLanguageSetting();
            LoadAdConfig();
            InitializeUi();
            ApplyLanguageToUi();
            LoadHistory();
            LoadSavedJobs();
            InitClipboardAutoGrabTimer();
            engineInitTask = CheckEngineAsync();
        }

        private static string ResolveBinDirectory(string appDir, string dataDir)
        {
            string primary = Path.Combine(appDir, "bin");
            if (File.Exists(Path.Combine(primary, "yt-dlp.exe"))) return primary;

            string localProg = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs");
            string[] fallbacks = new string[]
            {
                Path.Combine(appDir, "YTDownloader-Windows", "bin"),
                Path.Combine(localProg, "SocialDownloader", "bin"),
                Path.Combine(localProg, "YTDownloader", "bin"),
                Path.Combine(dataDir, "bin")
            };
            foreach (string candidate in fallbacks)
            {
                try
                {
                    if (File.Exists(Path.Combine(candidate, "yt-dlp.exe")))
                    {
                        try
                        {
                            Directory.CreateDirectory(primary);
                            File.Copy(Path.Combine(candidate, "yt-dlp.exe"), Path.Combine(primary, "yt-dlp.exe"), true);
                            if (File.Exists(Path.Combine(candidate, "ffmpeg.exe")) && !File.Exists(Path.Combine(primary, "ffmpeg.exe")))
                            {
                                File.Copy(Path.Combine(candidate, "ffmpeg.exe"), Path.Combine(primary, "ffmpeg.exe"), true);
                            }
                            return primary;
                        }
                        catch
                        {
                            return candidate;
                        }
                    }
                }
                catch { }
            }
            return primary;
        }

        private void LoadLanguageSetting()
        {
            try
            {
                // 1. Check user DataDir language.cfg (written by Installer or App)
                if (File.Exists(LangConfigFilePath))
                {
                    string code = File.ReadAllText(LangConfigFilePath, Encoding.UTF8).Trim().ToLowerInvariant();
                    if (I18n.SupportedLanguages.Any(l => l.Code == code))
                    {
                        I18n.CurrentLang = code;
                        return;
                    }
                }
                // 2. Check AppDir language.cfg (written by Installer into installation folder)
                string appLangFile = Path.Combine(AppDir, "language.cfg");
                if (File.Exists(appLangFile))
                {
                    string code = File.ReadAllText(appLangFile, Encoding.UTF8).Trim().ToLowerInvariant();
                    if (I18n.SupportedLanguages.Any(l => l.Code == code))
                    {
                        I18n.CurrentLang = code;
                        return;
                    }
                }
            }
            catch { }
            // Default to English ("en")
            I18n.CurrentLang = "en";
        }

        private void SaveLanguageSetting(string code)
        {
            I18n.CurrentLang = code;
            try { File.WriteAllText(LangConfigFilePath, code, Encoding.UTF8); } catch { }
            try { File.WriteAllText(Path.Combine(AppDir, "language.cfg"), code, Encoding.UTF8); } catch { }
        }

        private void InitializeUi()
        {
            Text = "Social Downloader v2.4";
            Size = new Size(1120, 860);
            MinimumSize = new Size(1020, 720);
            StartPosition = FormStartPosition.CenterScreen;
            BackColor = BgDark;
            ForeColor = TextPrimary;
            Font = new Font("Segoe UI", 10f, FontStyle.Regular);

            string icoPath = Path.Combine(AppDir, "app_icon.ico");
            if (File.Exists(icoPath))
            {
                try { Icon = new Icon(icoPath); } catch { }
            }

            // 1. Top Header Panel
            Panel pnlHeader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 68,
                BackColor = CardDark,
                Padding = new Padding(18, 10, 18, 10)
            };

            PictureBox picLogo = new PictureBox
            {
                Size = new Size(46, 46),
                Location = new Point(18, 11),
                SizeMode = PictureBoxSizeMode.Zoom
            };
            string logoPath = Path.Combine(AppDir, "app_logo.png");
            if (File.Exists(logoPath))
            {
                try { picLogo.Image = Image.FromFile(logoPath); } catch { }
            }
            pnlHeader.Controls.Add(picLogo);

            Label lblAppTitle = new Label
            {
                Text = "Social Downloader v2.4",
                Font = new Font("Segoe UI Semibold", 15f, FontStyle.Bold),
                ForeColor = TextPrimary,
                AutoSize = true,
                Location = new Point(74, 8)
            };
            pnlHeader.Controls.Add(lblAppTitle);

            lblAppSubtitle = new Label
            {
                Text = I18n.T("app_subtitle"),
                Font = new Font("Segoe UI", 8.8f),
                ForeColor = TextSecondary,
                AutoSize = true,
                Location = new Point(76, 38)
            };
            pnlHeader.Controls.Add(lblAppSubtitle);

            // Language Dropdown in Header
            cmbLanguage = new ComboBox
            {
                Width = 150,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                Font = new Font("Segoe UI", 9.2f),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(Width - 185, 20)
            };
            foreach (var lang in I18n.SupportedLanguages)
            {
                cmbLanguage.Items.Add(lang);
            }
            int langIdx = I18n.SupportedLanguages.FindIndex(l => l.Code == I18n.CurrentLang);
            cmbLanguage.SelectedIndex = langIdx >= 0 ? langIdx : 0;
            cmbLanguage.SelectedIndexChanged += (s, e) =>
            {
                var item = cmbLanguage.SelectedItem as LanguageItem;
                if (item != null && item.Code != I18n.CurrentLang)
                {
                    SaveLanguageSetting(item.Code);
                    ApplyLanguageToUi();
                }
            };
            pnlHeader.Controls.Add(cmbLanguage);

            btnOpenDownloadsHeader = CreateStyledButton(I18n.T("btn_downloads_folder"), Color.FromArgb(48, 50, 64), 140, 34);
            btnOpenDownloadsHeader.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnOpenDownloadsHeader.Location = new Point(Width - 335, 17);
            btnOpenDownloadsHeader.Click += (s, e) => OpenFolder(DownloadFolder);
            pnlHeader.Controls.Add(btnOpenDownloadsHeader);

            btnUpdateEngine = CreateStyledButton(I18n.T("btn_update_engine"), Color.FromArgb(48, 50, 64), 135, 34);
            btnUpdateEngine.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnUpdateEngine.Location = new Point(Width - 478, 17);
            btnUpdateEngine.Click += async (s, e) => await UpdateEngineAsync();
            pnlHeader.Controls.Add(btnUpdateEngine);

            lblEngineStatus = new Label
            {
                Text = I18n.T("engine_checking"),
                Font = new Font("Segoe UI", 8.8f),
                ForeColor = SuccessGreen,
                AutoSize = true,
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Location = new Point(Width - 670, 24)
            };
            pnlHeader.Controls.Add(lblEngineStatus);

            // Bottom Sponsor / Monetization Banner
            pnlAdBanner = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 42,
                BackColor = Color.FromArgb(24, 26, 34),
                Padding = new Padding(18, 6, 18, 6)
            };

            lblAdBannerText = new Label
            {
                Text = adConfig.BannerHeadline,
                Font = new Font("Segoe UI Semibold", 9.2f),
                ForeColor = Color.FromArgb(255, 215, 110),
                AutoSize = true,
                Location = new Point(18, 11),
                Cursor = Cursors.Hand
            };
            lblAdBannerText.Click += (s, e) => OpenSponsorLink();
            pnlAdBanner.Controls.Add(lblAdBannerText);

            btnAdBannerAction = CreateStyledButton(adConfig.BannerButtonText, Color.FromArgb(215, 130, 20), 130, 28);
            btnAdBannerAction.Font = new Font("Segoe UI Semibold", 8.8f, FontStyle.Bold);
            btnAdBannerAction.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnAdBannerAction.Location = new Point(Width - 165, 7);
            btnAdBannerAction.Click += (s, e) => OpenSponsorLink();
            pnlAdBanner.Controls.Add(btnAdBannerAction);

            pnlAdBanner.Resize += (s, e) =>
            {
                btnAdBannerAction.Left = pnlAdBanner.ClientSize.Width - 148;
            };

            // 2. Main Container
            Panel pnlMain = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(18, 12, 18, 10),
                BackColor = BgDark
            };

            // URL Input & Auto-Grab Options Card (Unified — No separate platform buttons needed)
            Panel pnlUrlCard = new Panel
            {
                Dock = DockStyle.Top,
                Height = 136,
                BackColor = CardDark,
                Padding = new Padding(16)
            };

            chkAutoClipboard = new CheckBox
            {
                Text = I18n.T("auto_grab_chk"),
                Font = new Font("Segoe UI Semibold", 9.2f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 215, 110),
                AutoSize = true,
                Location = new Point(16, 12),
                Checked = false
            };
            pnlUrlCard.Controls.Add(chkAutoClipboard);

            chkUseBrowserCookies = new CheckBox
            {
                Text = I18n.T("cookies_chk"),
                Font = new Font("Segoe UI", 8.8f),
                ForeColor = TextSecondary,
                AutoSize = true,
                Location = new Point(365, 13),
                Checked = false
            };
            pnlUrlCard.Controls.Add(chkUseBrowserCookies);

            cmbCookieBrowser = new ComboBox
            {
                Location = new Point(665, 10),
                Width = 78,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                Font = new Font("Segoe UI", 8.8f)
            };
            cmbCookieBrowser.Items.AddRange(new object[] { "edge", "chrome", "firefox", "brave" });
            cmbCookieBrowser.SelectedIndex = 0;
            pnlUrlCard.Controls.Add(cmbCookieBrowser);

            chkAllowMixPlaylist = new CheckBox
            {
                Text = I18n.T("chk_mix"),
                Font = new Font("Segoe UI", 8.8f),
                ForeColor = TextSecondary,
                AutoSize = true,
                Location = new Point(760, 13),
                Checked = false
            };
            pnlUrlCard.Controls.Add(chkAllowMixPlaylist);

            lblUrlPrompt = new Label
            {
                Text = I18n.T("prompt_unified"),
                Font = new Font("Segoe UI Semibold", 9.8f, FontStyle.Bold),
                ForeColor = TextPrimary,
                AutoSize = true,
                Location = new Point(16, 46)
            };
            pnlUrlCard.Controls.Add(lblUrlPrompt);

            pasteDebounceTimer = new System.Windows.Forms.Timer { Interval = 350 };
            pasteDebounceTimer.Tick += (s, e) =>
            {
                pasteDebounceTimer.Stop();
                string raw = txtUrl.Text.Trim();
                if (string.IsNullOrEmpty(raw)) return;
                string extracted = ExtractSupportedUrlFromText(raw);
                if (string.IsNullOrEmpty(extracted)) return;
                if (string.Equals(raw, lastAutoCheckedInputUrl, StringComparison.OrdinalIgnoreCase)) return;
                if (btnFetch != null && !btnFetch.Enabled) return;
                lastAutoCheckedInputUrl = raw;
                var t = FetchMediaInfoAsync(chkAutoClipboard.Checked);
            };

            txtUrl = new TextBox
            {
                Font = new Font("Segoe UI", 11.5f),
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Location = new Point(16, 76),
                Width = 650,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right
            };
            txtUrl.TextChanged += (s, e) =>
            {
                string u = txtUrl.Text.Trim();
                if (!string.IsNullOrEmpty(u))
                {
                    selectedPlatform = DetectPlatform(u);
                    string candidate = ExtractSupportedUrlFromText(u);
                    if (!string.IsNullOrEmpty(candidate) &&
                        u.Length > 8 &&
                        !string.Equals(u, lastAutoCheckedInputUrl, StringComparison.OrdinalIgnoreCase))
                    {
                        pasteDebounceTimer.Stop();
                        pasteDebounceTimer.Start();
                    }
                }
                else
                {
                    pasteDebounceTimer.Stop();
                    lastAutoCheckedInputUrl = "";
                }
            };
            txtUrl.KeyDown += (s, e) =>
            {
                if (e.KeyCode == Keys.Enter)
                {
                    e.SuppressKeyPress = true;
                    var t = FetchMediaInfoAsync(false);
                }
            };
            pnlUrlCard.Controls.Add(txtUrl);

            btnPaste = CreateStyledButton(I18n.T("btn_paste"), Color.FromArgb(52, 54, 68), 95, 34);
            btnPaste.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnPaste.Location = new Point(pnlUrlCard.Width - 345, 74);
            btnPaste.Click += (s, e) =>
            {
                if (Clipboard.ContainsText())
                {
                    string clip = Clipboard.GetText().Trim();
                    lastAutoGrabbedUrl = clip;
                    txtUrl.Text = clip;
                    var t = FetchMediaInfoAsync(chkAutoClipboard.Checked);
                }
            };
            pnlUrlCard.Controls.Add(btnPaste);

            btnClearUrl = CreateStyledButton("✖", Color.FromArgb(52, 54, 68), 44, 34);
            btnClearUrl.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnClearUrl.Location = new Point(pnlUrlCard.Width - 242, 74);
            btnClearUrl.Click += (s, e) =>
            {
                ClearUrlInputAndPreview();
                txtUrl.Focus();
            };
            pnlUrlCard.Controls.Add(btnClearUrl);

            btnFetch = CreateStyledButton(I18n.T("btn_search"), AccentRed, 175, 34);
            btnFetch.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnFetch.Location = new Point(pnlUrlCard.Width - 190, 74);
            btnFetch.Click += async (s, e) => await FetchMediaInfoAsync(false);
            pnlUrlCard.Controls.Add(btnFetch);

            pnlUrlCard.Resize += (s, e) =>
            {
                btnFetch.Left = pnlUrlCard.ClientSize.Width - 190;
                btnClearUrl.Left = btnFetch.Left - 52;
                btnPaste.Left = btnClearUrl.Left - 103;
                txtUrl.Width = Math.Max(200, btnPaste.Left - 28);
            };

            // 3. Media Info & Format Selection Card
            pnlMediaCard = new Panel
            {
                Dock = DockStyle.Top,
                Height = 250,
                BackColor = CardDark,
                Padding = new Padding(16),
                Visible = false
            };

            picThumbnail = new PictureBox
            {
                Location = new Point(16, 16),
                Size = new Size(220, 124),
                BackColor = Color.FromArgb(14, 14, 18),
                SizeMode = PictureBoxSizeMode.Zoom
            };
            pnlMediaCard.Controls.Add(picThumbnail);

            lblPlatformBadge = new Label
            {
                Text = "YOUTUBE",
                Font = new Font("Segoe UI Semibold", 8.5f, FontStyle.Bold),
                BackColor = AccentRed,
                ForeColor = Color.White,
                AutoSize = true,
                Padding = new Padding(6, 2, 6, 2),
                Location = new Point(252, 14)
            };
            pnlMediaCard.Controls.Add(lblPlatformBadge);

            lblMediaTitle = new Label
            {
                Text = "Video Title",
                Visible = false
            };
            pnlMediaCard.Controls.Add(lblMediaTitle);

            txtCustomTitle = new TextBox
            {
                Text = "Video Title",
                Font = new Font("Segoe UI Semibold", 10.5f, FontStyle.Bold),
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Location = new Point(345, 11),
                Size = new Size(470, 27),
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right
            };
            txtCustomTitle.TextChanged += (s, e) =>
            {
                lblMediaTitle.Text = txtCustomTitle.Text;
                if (currentMedia != null && !string.IsNullOrWhiteSpace(txtCustomTitle.Text))
                {
                    currentMedia.Title = txtCustomTitle.Text.Trim();
                }
            };
            pnlMediaCard.Controls.Add(txtCustomTitle);

            btnPreviewSaveCover = CreateStyledButton(I18n.T("btn_save_cover"), Color.FromArgb(46, 134, 90), 106, 27);
            btnPreviewSaveCover.Font = new Font("Segoe UI", 8.5f);
            btnPreviewSaveCover.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnPreviewSaveCover.Location = new Point(710, 11);
            btnPreviewSaveCover.Click += (s, e) => SaveCurrentMusicCover();
            pnlMediaCard.Controls.Add(btnPreviewSaveCover);

            btnPreviewCopyName = CreateStyledButton(I18n.T("btn_copy_name"), Color.FromArgb(52, 54, 68), 112, 27);
            btnPreviewCopyName.Font = new Font("Segoe UI", 8.5f);
            btnPreviewCopyName.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnPreviewCopyName.Location = new Point(822, 11);
            btnPreviewCopyName.Click += (s, e) =>
            {
                string name = !string.IsNullOrWhiteSpace(txtCustomTitle.Text) ? txtCustomTitle.Text.Trim() : lblMediaTitle.Text;
                CopyTextSafe(name, I18n.T("msg_copied_name"));
            };
            pnlMediaCard.Controls.Add(btnPreviewCopyName);

            btnPreviewCopyLink = CreateStyledButton(I18n.T("btn_copy_link"), Color.FromArgb(52, 54, 68), 108, 27);
            btnPreviewCopyLink.Font = new Font("Segoe UI", 8.5f);
            btnPreviewCopyLink.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnPreviewCopyLink.Location = new Point(940, 11);
            btnPreviewCopyLink.Click += (s, e) =>
            {
                if (currentMedia != null) CopyTextSafe(currentMedia.Url, I18n.T("msg_copied_link"));
            };
            pnlMediaCard.Controls.Add(btnPreviewCopyLink);

            lblMediaSub = new Label
            {
                Text = "Channel • Duration",
                Font = new Font("Segoe UI", 9.5f),
                ForeColor = TextSecondary,
                Location = new Point(252, 44),
                Size = new Size(740, 22),
                AutoEllipsis = true,
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right
            };
            pnlMediaCard.Controls.Add(lblMediaSub);

            rbVideo = new RadioButton
            {
                Text = I18n.T("rb_video"),
                Font = new Font("Segoe UI Semibold", 10f, FontStyle.Bold),
                ForeColor = TextPrimary,
                Location = new Point(252, 74),
                AutoSize = true,
                Checked = true
            };
            rbVideo.CheckedChanged += (s, e) =>
            {
                cmbVideoQuality.Enabled = rbVideo.Checked;
                cmbAudioFormat.Enabled = rbAudio.Checked;
            };
            pnlMediaCard.Controls.Add(rbVideo);

            cmbVideoQuality = new ComboBox
            {
                Location = new Point(390, 72),
                Width = 175,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                Font = new Font("Segoe UI", 10f)
            };
            cmbVideoQuality.Items.AddRange(new object[] {
                I18n.T("best_mp4"),
                "2160p (4K Ultra HD)",
                "1440p (2K QHD)",
                "1080p (Full HD)",
                "720p (HD)",
                "480p (Standard)",
                "360p (Data Saver)"
            });
            cmbVideoQuality.SelectedIndex = 0;
            pnlMediaCard.Controls.Add(cmbVideoQuality);

            rbAudio = new RadioButton
            {
                Text = I18n.T("rb_audio"),
                Font = new Font("Segoe UI Semibold", 10f, FontStyle.Bold),
                ForeColor = TextPrimary,
                Location = new Point(585, 74),
                AutoSize = true
            };
            pnlMediaCard.Controls.Add(rbAudio);

            cmbAudioFormat = new ComboBox
            {
                Location = new Point(745, 72),
                Width = 175,
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                Font = new Font("Segoe UI", 10f),
                Enabled = false
            };
            cmbAudioFormat.Items.AddRange(new object[] {
                "MP3 (320kbps Best Quality)",
                "M4A (Original AAC Best)",
                "OPUS (Hi-Fi Best)",
                "FLAC (Lossless Audio)",
                "WAV (Uncompressed Studio)"
            });
            cmbAudioFormat.SelectedIndex = 0;
            pnlMediaCard.Controls.Add(cmbAudioFormat);

            lblSaveTo = new Label
            {
                Text = I18n.T("save_to"),
                Font = new Font("Segoe UI", 9.5f),
                ForeColor = TextSecondary,
                Location = new Point(252, 114),
                AutoSize = true
            };
            pnlMediaCard.Controls.Add(lblSaveTo);

            txtOutputFolder = new TextBox
            {
                Text = DownloadFolder,
                Location = new Point(342, 111),
                Width = 355,
                ReadOnly = true,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 9.5f)
            };
            pnlMediaCard.Controls.Add(txtOutputFolder);

            btnBrowseFolder = CreateStyledButton(I18n.T("btn_browse"), Color.FromArgb(52, 54, 68), 130, 30);
            btnBrowseFolder.Location = new Point(705, 109);
            btnBrowseFolder.Click += (s, e) =>
            {
                using (var dlg = new FolderBrowserDialog())
                {
                    dlg.SelectedPath = DownloadFolder;
                    if (dlg.ShowDialog() == DialogResult.OK && !string.IsNullOrWhiteSpace(dlg.SelectedPath))
                    {
                        DownloadFolder = dlg.SelectedPath;
                        txtOutputFolder.Text = DownloadFolder;
                    }
                }
            };
            pnlMediaCard.Controls.Add(btnBrowseFolder);

            btnDownloadNow = CreateStyledButton(I18n.T("btn_download_now"), AccentRed, 195, 38);
            btnDownloadNow.Font = new Font("Segoe UI Semibold", 10.5f, FontStyle.Bold);
            btnDownloadNow.Location = new Point(855, 104);
            btnDownloadNow.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnDownloadNow.Click += (s, e) => EnqueueCurrentMedia();
            pnlMediaCard.Controls.Add(btnDownloadNow);

            // Playlist Checklist inside MediaCard
            pnlPlaylist = new Panel
            {
                Location = new Point(16, 148),
                Size = new Size(990, 140),
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                Visible = false
            };

            lblPlaylistCount = new Label
            {
                Text = I18n.T("playlist_header"),
                Font = new Font("Segoe UI Semibold", 9.5f, FontStyle.Bold),
                ForeColor = TextPrimary,
                Location = new Point(0, 4),
                AutoSize = true
            };
            pnlPlaylist.Controls.Add(lblPlaylistCount);

            btnSelectAll = CreateStyledButton(I18n.T("btn_select_all"), Color.FromArgb(48, 50, 64), 95, 26);
            btnSelectAll.Font = new Font("Segoe UI", 8.5f);
            btnSelectAll.Location = new Point(320, 0);
            btnSelectAll.Click += (s, e) =>
            {
                foreach (ListViewItem item in lvPlaylist.Items) item.Checked = true;
            };
            pnlPlaylist.Controls.Add(btnSelectAll);

            btnSelectNone = CreateStyledButton(I18n.T("btn_select_none"), Color.FromArgb(48, 50, 64), 95, 26);
            btnSelectNone.Font = new Font("Segoe UI", 8.5f);
            btnSelectNone.Location = new Point(422, 0);
            btnSelectNone.Click += (s, e) =>
            {
                foreach (ListViewItem item in lvPlaylist.Items) item.Checked = false;
            };
            pnlPlaylist.Controls.Add(btnSelectNone);

            lvPlaylist = new ListView
            {
                Location = new Point(0, 30),
                Size = new Size(990, 108),
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right | AnchorStyles.Bottom,
                View = View.Details,
                CheckBoxes = true,
                FullRowSelect = true,
                GridLines = false,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            lvPlaylist.Columns.Add("#", 52);
            lvPlaylist.Columns.Add(I18n.T("col_title"), 560);
            lvPlaylist.Columns.Add(I18n.T("col_channel"), 210);
            lvPlaylist.Columns.Add(I18n.T("col_duration"), 110);
            pnlPlaylist.Controls.Add(lvPlaylist);

            pnlMediaCard.Controls.Add(pnlPlaylist);
            pnlMediaCard.Resize += (s, e) =>
            {
                btnDownloadNow.Left = pnlMediaCard.ClientSize.Width - 210;
                btnPreviewCopyLink.Left = pnlMediaCard.ClientSize.Width - 122;
                btnPreviewCopyName.Left = btnPreviewCopyLink.Left - 118;
                if (btnPreviewSaveCover != null)
                {
                    btnPreviewSaveCover.Left = btnPreviewCopyName.Left - 112;
                    txtCustomTitle.Width = Math.Max(160, btnPreviewSaveCover.Left - txtCustomTitle.Left - 8);
                }
                else
                {
                    txtCustomTitle.Width = Math.Max(180, btnPreviewCopyName.Left - txtCustomTitle.Left - 8);
                }
                pnlPlaylist.Width = pnlMediaCard.ClientSize.Width - 32;
                lvPlaylist.Width = pnlPlaylist.Width;
            };

            // 4. Bottom Tabs (Active Queue, History, Monetization)
            tabBottom = new TabControl
            {
                Dock = DockStyle.Fill,
                Font = new Font("Segoe UI Semibold", 10f)
            };

            tabQueue = new TabPage(I18n.T("tab_queue"))
            {
                BackColor = CardDark,
                ForeColor = TextPrimary,
                Padding = new Padding(12)
            };

            Panel pnlQueueBottom = new Panel
            {
                Dock = DockStyle.Bottom,
                Height = 46,
                BackColor = CardDark
            };

            pbActive = new ProgressBar
            {
                Location = new Point(4, 12),
                Size = new Size(155, 24),
                Style = ProgressBarStyle.Continuous
            };
            pnlQueueBottom.Controls.Add(pbActive);

            lblActiveProgress = new Label
            {
                Text = I18n.T("no_active_dl"),
                Font = new Font("Segoe UI", 8.8f),
                ForeColor = TextSecondary,
                Location = new Point(165, 14),
                AutoSize = true
            };
            pnlQueueBottom.Controls.Add(lblActiveProgress);

            btnOpenSelectedFile = CreateStyledButton(I18n.T("btn_play_file"), Color.FromArgb(46, 134, 90), 82, 32);
            btnOpenSelectedFile.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnOpenSelectedFile.Font = new Font("Segoe UI Semibold", 8.5f);
            btnOpenSelectedFile.Click += (s, e) => PlaySelectedQueueItem();
            pnlQueueBottom.Controls.Add(btnOpenSelectedFile);

            btnOpenSelectedFolder = CreateStyledButton(I18n.T("btn_open_folder"), Color.FromArgb(52, 54, 68), 88, 32);
            btnOpenSelectedFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnOpenSelectedFolder.Font = new Font("Segoe UI Semibold", 8.5f);
            btnOpenSelectedFolder.Click += (s, e) => OpenSelectedQueueFolder();
            pnlQueueBottom.Controls.Add(btnOpenSelectedFolder);

            btnRetrySelected = CreateStyledButton(I18n.T("btn_retry_link"), Color.FromArgb(36, 118, 195), 118, 32);
            btnRetrySelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRetrySelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnRetrySelected.Click += (s, e) => RetryOrDownloadSelectedQueueItem();
            pnlQueueBottom.Controls.Add(btnRetrySelected);

            btnRenameSelected = CreateStyledButton(I18n.T("btn_rename"), Color.FromArgb(95, 72, 165), 92, 32);
            btnRenameSelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnRenameSelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnRenameSelected.Click += (s, e) => RenameSelectedQueueItem();
            pnlQueueBottom.Controls.Add(btnRenameSelected);

            btnCopyNameSelected = CreateStyledButton(I18n.T("btn_copy_name"), Color.FromArgb(52, 54, 68), 104, 32);
            btnCopyNameSelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnCopyNameSelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnCopyNameSelected.Click += (s, e) => CopySelectedQueueName();
            pnlQueueBottom.Controls.Add(btnCopyNameSelected);

            btnCopyLinkSelected = CreateStyledButton(I18n.T("btn_copy_link"), Color.FromArgb(52, 54, 68), 98, 32);
            btnCopyLinkSelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnCopyLinkSelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnCopyLinkSelected.Click += (s, e) => CopySelectedQueueLink();
            pnlQueueBottom.Controls.Add(btnCopyLinkSelected);

            btnPauseResumeSelected = CreateStyledButton(I18n.T("btn_pause_resume"), Color.FromArgb(205, 135, 25), 130, 32);
            btnPauseResumeSelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnPauseResumeSelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnPauseResumeSelected.Click += (s, e) => TogglePauseResumeSelectedQueueItem();
            pnlQueueBottom.Controls.Add(btnPauseResumeSelected);

            btnCancelSelected = CreateStyledButton(I18n.T("btn_cancel"), Color.FromArgb(170, 40, 48), 88, 32);
            btnCancelSelected.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnCancelSelected.Font = new Font("Segoe UI Semibold", 8.5f);
            btnCancelSelected.Click += (s, e) => CancelSelectedQueueItem();
            pnlQueueBottom.Controls.Add(btnCancelSelected);

            Action layoutQueueButtons = () =>
            {
                int right = pnlQueueBottom.ClientSize.Width - 4;
                btnCancelSelected.Location = new Point(right - btnCancelSelected.Width, 8);
                right = btnCancelSelected.Left - 5;
                btnPauseResumeSelected.Location = new Point(right - btnPauseResumeSelected.Width, 8);
                right = btnPauseResumeSelected.Left - 5;
                btnRetrySelected.Location = new Point(right - btnRetrySelected.Width, 8);
                right = btnRetrySelected.Left - 5;
                btnRenameSelected.Location = new Point(right - btnRenameSelected.Width, 8);
                right = btnRenameSelected.Left - 5;
                btnCopyNameSelected.Location = new Point(right - btnCopyNameSelected.Width, 8);
                right = btnCopyNameSelected.Left - 5;
                btnCopyLinkSelected.Location = new Point(right - btnCopyLinkSelected.Width, 8);
                right = btnCopyLinkSelected.Left - 5;
                btnOpenSelectedFolder.Location = new Point(right - btnOpenSelectedFolder.Width, 8);
                right = btnOpenSelectedFolder.Left - 5;
                btnOpenSelectedFile.Location = new Point(right - btnOpenSelectedFile.Width, 8);
            };
            pnlQueueBottom.Resize += (s, e) => layoutQueueButtons();

            lvQueue = new ListView
            {
                Dock = DockStyle.Fill,
                View = View.Details,
                FullRowSelect = true,
                GridLines = false,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 9.5f)
            };
            lvQueue.Columns.Add(I18n.T("col_title"), 370);
            lvQueue.Columns.Add(I18n.T("col_format"), 170);
            lvQueue.Columns.Add(I18n.T("col_status"), 150);
            lvQueue.Columns.Add(I18n.T("col_progress"), 95);
            lvQueue.Columns.Add(I18n.T("col_speed"), 105);
            lvQueue.Columns.Add(I18n.T("col_eta"), 95);
            lvQueue.DoubleClick += (s, e) => PlaySelectedQueueItem();

            var queueMenu = new ContextMenuStrip();
            queueMenu.Items.Add("▶ Play / Open File", null, (s, e) => PlaySelectedQueueItem());
            queueMenu.Items.Add("📂 Show in Folder", null, (s, e) => OpenSelectedQueueFolder());
            queueMenu.Items.Add(new ToolStripSeparator());
            queueMenu.Items.Add("🔄 Retry Download Now", null, (s, e) => RetryOrDownloadSelectedQueueItem());
            queueMenu.Items.Add("🔗 Load Link in Search Box (Choose Quality)", null, (s, e) => LoadSelectedQueueLinkToHome());
            queueMenu.Items.Add("⏸ Pause / ▶ Resume", null, (s, e) => TogglePauseResumeSelectedQueueItem());
            queueMenu.Items.Add(new ToolStripSeparator());
            queueMenu.Items.Add("✏️ Rename File / Title", null, (s, e) => RenameSelectedQueueItem());
            queueMenu.Items.Add("📋 Copy Name", null, (s, e) => CopySelectedQueueName());
            queueMenu.Items.Add("🔗 Copy Link", null, (s, e) => CopySelectedQueueLink());
            queueMenu.Items.Add(new ToolStripSeparator());
            queueMenu.Items.Add("✖ Cancel", null, (s, e) => CancelSelectedQueueItem());
            lvQueue.ContextMenuStrip = queueMenu;

            tabQueue.Controls.Add(lvQueue);
            tabQueue.Controls.Add(pnlQueueBottom);
            layoutQueueButtons();

            // Tab 2: Download History
            tabHistory = new TabPage(I18n.T("tab_history"))
            {
                BackColor = CardDark,
                ForeColor = TextPrimary,
                Padding = new Padding(12)
            };

            Panel pnlHistoryTop = new Panel
            {
                Dock = DockStyle.Top,
                Height = 42,
                BackColor = CardDark
            };

            lblSearchHist = new Label
            {
                Text = I18n.T("lbl_search_hist"),
                Location = new Point(4, 10),
                AutoSize = true,
                ForeColor = TextSecondary
            };
            pnlHistoryTop.Controls.Add(lblSearchHist);

            txtHistorySearch = new TextBox
            {
                Location = new Point(135, 7),
                Width = 200,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            txtHistorySearch.TextChanged += (s, e) => RefreshHistoryListView();
            pnlHistoryTop.Controls.Add(txtHistorySearch);

            btnHistoryPlay = CreateStyledButton(I18n.T("btn_play_file"), Color.FromArgb(46, 134, 90), 82, 30);
            btnHistoryPlay.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryPlay.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryPlay.Click += (s, e) => PlaySelectedHistoryItem();
            pnlHistoryTop.Controls.Add(btnHistoryPlay);

            btnHistoryFolder = CreateStyledButton(I18n.T("btn_open_folder"), Color.FromArgb(52, 54, 68), 88, 30);
            btnHistoryFolder.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryFolder.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryFolder.Click += (s, e) => OpenSelectedHistoryFolder();
            pnlHistoryTop.Controls.Add(btnHistoryFolder);

            btnHistoryRedownload = CreateStyledButton(I18n.T("btn_retry_link"), Color.FromArgb(36, 118, 195), 118, 30);
            btnHistoryRedownload.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryRedownload.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryRedownload.Click += (s, e) => RedownloadSelectedHistoryItem();
            pnlHistoryTop.Controls.Add(btnHistoryRedownload);

            btnHistoryRename = CreateStyledButton(I18n.T("btn_rename"), Color.FromArgb(95, 72, 165), 92, 30);
            btnHistoryRename.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryRename.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryRename.Click += (s, e) => RenameSelectedHistoryItem();
            pnlHistoryTop.Controls.Add(btnHistoryRename);

            btnHistoryCopyName = CreateStyledButton(I18n.T("btn_copy_name"), Color.FromArgb(52, 54, 68), 104, 30);
            btnHistoryCopyName.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryCopyName.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryCopyName.Click += (s, e) => CopySelectedHistoryName();
            pnlHistoryTop.Controls.Add(btnHistoryCopyName);

            btnHistoryCopyLink = CreateStyledButton(I18n.T("btn_copy_link"), Color.FromArgb(52, 54, 68), 98, 30);
            btnHistoryCopyLink.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryCopyLink.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryCopyLink.Click += (s, e) => CopySelectedHistoryLink();
            pnlHistoryTop.Controls.Add(btnHistoryCopyLink);

            btnHistoryClear = CreateStyledButton(I18n.T("btn_clear_hist"), Color.FromArgb(140, 42, 48), 112, 30);
            btnHistoryClear.Anchor = AnchorStyles.Top | AnchorStyles.Right;
            btnHistoryClear.Font = new Font("Segoe UI Semibold", 8.5f);
            btnHistoryClear.Click += (s, e) =>
            {
                if (MessageBox.Show(I18n.T("msg_confirm_clear_hist"), "Confirm", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
                {
                    history.Clear();
                    SaveHistory();
                    RefreshHistoryListView();
                }
            };
            pnlHistoryTop.Controls.Add(btnHistoryClear);

            Action layoutHistoryButtons = () =>
            {
                int right = pnlHistoryTop.ClientSize.Width - 4;
                btnHistoryClear.Location = new Point(right - btnHistoryClear.Width, 6);
                right = btnHistoryClear.Left - 5;
                btnHistoryCopyLink.Location = new Point(right - btnHistoryCopyLink.Width, 6);
                right = btnHistoryCopyLink.Left - 5;
                btnHistoryCopyName.Location = new Point(right - btnHistoryCopyName.Width, 6);
                right = btnHistoryCopyName.Left - 5;
                btnHistoryRename.Location = new Point(right - btnHistoryRename.Width, 6);
                right = btnHistoryRename.Left - 5;
                btnHistoryRedownload.Location = new Point(right - btnHistoryRedownload.Width, 6);
                right = btnHistoryRedownload.Left - 5;
                btnHistoryFolder.Location = new Point(right - btnHistoryFolder.Width, 6);
                right = btnHistoryFolder.Left - 5;
                btnHistoryPlay.Location = new Point(right - btnHistoryPlay.Width, 6);
            };
            pnlHistoryTop.Resize += (s, e) => layoutHistoryButtons();

            lvHistory = new ListView
            {
                Dock = DockStyle.Fill,
                View = View.Details,
                FullRowSelect = true,
                GridLines = false,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle,
                Font = new Font("Segoe UI", 9.5f)
            };
            lvHistory.Columns.Add(I18n.T("col_date"), 145);
            lvHistory.Columns.Add(I18n.T("col_title"), 360);
            lvHistory.Columns.Add(I18n.T("col_channel"), 145);
            lvHistory.Columns.Add(I18n.T("col_format"), 140);
            lvHistory.Columns.Add(I18n.T("col_size"), 90);
            lvHistory.Columns.Add(I18n.T("col_path"), 230);
            lvHistory.DoubleClick += (s, e) => PlaySelectedHistoryItem();

            var histMenu = new ContextMenuStrip();
            histMenu.Items.Add("▶ Play / Open File", null, (s, e) => PlaySelectedHistoryItem());
            histMenu.Items.Add("📂 Show in Folder", null, (s, e) => OpenSelectedHistoryFolder());
            histMenu.Items.Add(new ToolStripSeparator());
            histMenu.Items.Add("🔄 Retry / Download from Link", null, (s, e) => RedownloadSelectedHistoryItem());
            histMenu.Items.Add("✏️ Rename File / Title", null, (s, e) => RenameSelectedHistoryItem());
            histMenu.Items.Add("📋 Copy Name", null, (s, e) => CopySelectedHistoryName());
            histMenu.Items.Add("🔗 Copy Link", null, (s, e) => CopySelectedHistoryLink());
            lvHistory.ContextMenuStrip = histMenu;

            tabHistory.Controls.Add(lvHistory);
            tabHistory.Controls.Add(pnlHistoryTop);
            layoutHistoryButtons();

            // Tab 3: Monetization & Ad Setup
            tabMonetize = BuildMonetizationTab();

            tabBottom.TabPages.Add(tabQueue);
            tabBottom.TabPages.Add(tabHistory);
            tabBottom.TabPages.Add(tabMonetize);

            // Spacers
            Panel sp1 = new Panel { Dock = DockStyle.Top, Height = 10, BackColor = BgDark };
            Panel sp2 = new Panel { Dock = DockStyle.Top, Height = 10, BackColor = BgDark };

            pnlMain.Controls.Add(tabBottom);
            pnlMain.Controls.Add(sp2);
            pnlMain.Controls.Add(pnlMediaCard);
            pnlMain.Controls.Add(sp1);
            pnlMain.Controls.Add(pnlUrlCard);

            Controls.Add(pnlMain);
            Controls.Add(pnlAdBanner);
            Controls.Add(pnlHeader);
        }

        private TabPage BuildMonetizationTab()
        {
            TabPage tab = new TabPage(I18n.T("tab_monetize"))
            {
                BackColor = CardDark,
                ForeColor = TextPrimary,
                Padding = new Padding(16)
            };

            lblMonHeader = new Label
            {
                Text = I18n.T("mon_header"),
                Font = new Font("Segoe UI Semibold", 10.2f, FontStyle.Bold),
                ForeColor = Color.FromArgb(255, 215, 110),
                Location = new Point(16, 14),
                AutoSize = true
            };
            tab.Controls.Add(lblMonHeader);

            lblMon1 = new Label { Text = I18n.T("mon_headline"), Location = new Point(16, 52), AutoSize = true, ForeColor = TextSecondary };
            tab.Controls.Add(lblMon1);

            TextBox txtHeadline = new TextBox
            {
                Text = adConfig.BannerHeadline,
                Location = new Point(220, 49),
                Width = 650,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            tab.Controls.Add(txtHeadline);

            lblMon2 = new Label { Text = I18n.T("mon_btn_text"), Location = new Point(16, 88), AutoSize = true, ForeColor = TextSecondary };
            tab.Controls.Add(lblMon2);

            TextBox txtBtn = new TextBox
            {
                Text = adConfig.BannerButtonText,
                Location = new Point(220, 85),
                Width = 220,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            tab.Controls.Add(txtBtn);

            lblMon3 = new Label { Text = I18n.T("mon_target_url"), Location = new Point(16, 124), AutoSize = true, ForeColor = TextSecondary };
            tab.Controls.Add(lblMon3);

            TextBox txtTarget = new TextBox
            {
                Text = adConfig.TargetUrl,
                Location = new Point(220, 121),
                Width = 650,
                BackColor = CardInput,
                ForeColor = TextPrimary,
                BorderStyle = BorderStyle.FixedSingle
            };
            tab.Controls.Add(txtTarget);

            chkMonFirstDl = new CheckBox
            {
                Text = I18n.T("mon_chk_first"),
                Location = new Point(220, 156),
                AutoSize = true,
                Checked = adConfig.OpenSponsorOnFirstDownload,
                ForeColor = TextPrimary
            };
            tab.Controls.Add(chkMonFirstDl);

            btnMonSave = CreateStyledButton(I18n.T("mon_btn_save"), SuccessGreen, 210, 36);
            btnMonSave.Location = new Point(220, 190);
            btnMonSave.Click += (s, e) =>
            {
                adConfig.BannerHeadline = txtHeadline.Text.Trim();
                adConfig.BannerButtonText = txtBtn.Text.Trim();
                adConfig.TargetUrl = txtTarget.Text.Trim();
                adConfig.OpenSponsorOnFirstDownload = chkMonFirstDl.Checked;
                SaveAdConfig();
                lblAdBannerText.Text = adConfig.BannerHeadline;
                btnAdBannerAction.Text = adConfig.BannerButtonText;
                MessageBox.Show(I18n.T("mon_saved_msg"), "Saved", MessageBoxButtons.OK, MessageBoxIcon.Information);
            };
            tab.Controls.Add(btnMonSave);

            return tab;
        }

        private void ApplyLanguageToUi()
        {
            lblAppSubtitle.Text = I18n.T("app_subtitle");
            btnOpenDownloadsHeader.Text = I18n.T("btn_downloads_folder");
            btnUpdateEngine.Text = I18n.T("btn_update_engine");
            chkAutoClipboard.Text = I18n.T("auto_grab_chk");
            chkUseBrowserCookies.Text = I18n.T("cookies_chk");
            chkAllowMixPlaylist.Text = I18n.T("chk_mix");
            lblUrlPrompt.Text = I18n.T("prompt_unified");
            btnPaste.Text = I18n.T("btn_paste");
            if (btnFetch.Enabled) btnFetch.Text = I18n.T("btn_search");

            rbVideo.Text = I18n.T("rb_video");
            rbAudio.Text = I18n.T("rb_audio");
            lblSaveTo.Text = I18n.T("save_to");
            btnBrowseFolder.Text = I18n.T("btn_browse");
            btnDownloadNow.Text = I18n.T("btn_download_now");
            if (btnPreviewCopyName != null) btnPreviewCopyName.Text = I18n.T("btn_copy_name");
            if (btnPreviewCopyLink != null) btnPreviewCopyLink.Text = I18n.T("btn_copy_link");
            lblPlaylistCount.Text = I18n.T("playlist_header");
            btnSelectAll.Text = I18n.T("btn_select_all");
            btnSelectNone.Text = I18n.T("btn_select_none");

            if (lvPlaylist.Columns.Count >= 4)
            {
                lvPlaylist.Columns[1].Text = I18n.T("col_title");
                lvPlaylist.Columns[2].Text = I18n.T("col_channel");
                lvPlaylist.Columns[3].Text = I18n.T("col_duration");
            }

            tabQueue.Text = I18n.T("tab_queue");
            tabHistory.Text = I18n.T("tab_history");
            tabMonetize.Text = I18n.T("tab_monetize");

            if (jobs.Count == 0) lblActiveProgress.Text = I18n.T("no_active_dl");
            btnOpenSelectedFile.Text = I18n.T("btn_play_file");
            btnOpenSelectedFolder.Text = I18n.T("btn_open_folder");
            if (btnRetrySelected != null) btnRetrySelected.Text = I18n.T("btn_retry_link");
            if (btnRenameSelected != null) btnRenameSelected.Text = I18n.T("btn_rename");
            if (btnCopyNameSelected != null) btnCopyNameSelected.Text = I18n.T("btn_copy_name");
            if (btnCopyLinkSelected != null) btnCopyLinkSelected.Text = I18n.T("btn_copy_link");
            if (btnPauseResumeSelected != null) btnPauseResumeSelected.Text = I18n.T("btn_pause_resume");
            btnCancelSelected.Text = I18n.T("btn_cancel");

            if (lvQueue.Columns.Count >= 6)
            {
                lvQueue.Columns[0].Text = I18n.T("col_title");
                lvQueue.Columns[1].Text = I18n.T("col_format");
                lvQueue.Columns[2].Text = I18n.T("col_status");
                lvQueue.Columns[3].Text = I18n.T("col_progress");
                lvQueue.Columns[4].Text = I18n.T("col_speed");
                lvQueue.Columns[5].Text = I18n.T("col_eta");
            }

            lblSearchHist.Text = I18n.T("lbl_search_hist");
            btnHistoryPlay.Text = I18n.T("btn_play_file");
            btnHistoryFolder.Text = I18n.T("btn_open_folder");
            if (btnHistoryRedownload != null) btnHistoryRedownload.Text = I18n.T("btn_retry_link");
            if (btnHistoryRename != null) btnHistoryRename.Text = I18n.T("btn_rename");
            if (btnHistoryCopyName != null) btnHistoryCopyName.Text = I18n.T("btn_copy_name");
            if (btnHistoryCopyLink != null) btnHistoryCopyLink.Text = I18n.T("btn_copy_link");
            btnHistoryClear.Text = I18n.T("btn_clear_hist");

            if (lvHistory.Columns.Count >= 6)
            {
                lvHistory.Columns[0].Text = I18n.T("col_date");
                lvHistory.Columns[1].Text = I18n.T("col_title");
                lvHistory.Columns[2].Text = I18n.T("col_channel");
                lvHistory.Columns[3].Text = I18n.T("col_format");
                lvHistory.Columns[4].Text = I18n.T("col_size");
                lvHistory.Columns[5].Text = I18n.T("col_path");
            }

            lblMonHeader.Text = I18n.T("mon_header");
            lblMon1.Text = I18n.T("mon_headline");
            lblMon2.Text = I18n.T("mon_btn_text");
            lblMon3.Text = I18n.T("mon_target_url");
            chkMonFirstDl.Text = I18n.T("mon_chk_first");
            btnMonSave.Text = I18n.T("mon_btn_save");
        }

        private void InitClipboardAutoGrabTimer()
        {
            try
            {
                if (Clipboard.ContainsText())
                {
                    string initial = ExtractSupportedUrlFromText(Clipboard.GetText());
                    if (!string.IsNullOrEmpty(initial)) lastAutoGrabbedUrl = initial;
                }
            }
            catch { }

            clipboardTimer = new System.Windows.Forms.Timer();
            clipboardTimer.Interval = 1200;
            clipboardTimer.Tick += (s, e) =>
            {
                if (btnFetch == null || !btnFetch.Enabled) return;
                try
                {
                    if (!Clipboard.ContainsText()) return;
                    string raw = Clipboard.GetText();
                    string url = ExtractSupportedUrlFromText(raw);
                    if (string.IsNullOrEmpty(url)) return;
                    if (string.Equals(url, lastAutoGrabbedUrl, StringComparison.OrdinalIgnoreCase)) return;

                    lastAutoGrabbedUrl = url;
                    lastAutoCheckedInputUrl = url;
                    txtUrl.Text = url;
                    var t = FetchMediaInfoAsync(chkAutoClipboard != null && chkAutoClipboard.Checked);
                }
                catch { }
            };
            clipboardTimer.Start();
        }

        internal static bool IsAudioCentricUrl(string url)
        {
            if (string.IsNullOrWhiteSpace(url)) return false;
            string low = url.Trim().ToLowerInvariant();
            string clean = low.Split('?')[0].Split('#')[0];
            return low.Contains("music.youtube.com") ||
                   low.Contains("soundcloud.com") || low.Contains("on.soundcloud.com") ||
                   low.Contains("bandcamp.com") ||
                   low.Contains("audiomack.com") || low.Contains("mixcloud.com") || low.Contains("open.spotify.com") ||
                   clean.EndsWith(".mp3") || clean.EndsWith(".m4a") || clean.EndsWith(".flac") ||
                   clean.EndsWith(".wav") || clean.EndsWith(".opus") || clean.EndsWith(".ogg") || clean.EndsWith(".aac");
        }

        internal string ExtractSupportedUrlFromText(string text)
        {
            if (string.IsNullOrWhiteSpace(text)) return null;
            var m = Regex.Match(text, @"https?://\S+", RegexOptions.IgnoreCase);
            string u = null;
            if (m.Success)
            {
                u = m.Value.TrimEnd('.', ',', ')', ']', '"', '\'', ';', '>');
                if (u.StartsWith("HTTP://", StringComparison.Ordinal) || u.StartsWith("Http://", StringComparison.Ordinal))
                    u = "http://" + u.Substring(7);
                else if (u.StartsWith("HTTPS://", StringComparison.Ordinal) || u.StartsWith("Https://", StringComparison.Ordinal))
                    u = "https://" + u.Substring(8);
            }
            else
            {
                var bareMatch = Regex.Match(
                    text,
                    @"(?:^|\s)((?:www\.|m\.|(?:music\.youtube\.com|youtube\.com|youtu\.be|soundcloud\.com|on\.soundcloud\.com|bandcamp\.com|audiomack\.com|mixcloud\.com|pornhub\.com|pornhub\.org|xvideos\.com|xnxx\.com|xhamster\.com|spankbang\.com|redtube\.com|youporn\.com|facebook\.com|fb\.watch|instagram\.com|instagr\.am|threads\.net|threads\.com|linkedin\.com|x\.com|twitter\.com|tiktok\.com|reddit\.com|vimeo\.com|dailymotion\.com)/)\S+)",
                    RegexOptions.IgnoreCase);
                if (bareMatch.Success)
                {
                    u = "https://" + bareMatch.Groups[1].Value.TrimEnd('.', ',', ')', ']', '"', '\'', ';', '>');
                }
            }
            if (string.IsNullOrEmpty(u)) return null;

            string low = u.ToLowerInvariant();
            string cleanPath = low.Split('?')[0].Split('#')[0];
            if (cleanPath.EndsWith(".mp4") || cleanPath.EndsWith(".m3u8") || cleanPath.EndsWith(".webm") || cleanPath.EndsWith(".mov") || cleanPath.EndsWith(".mkv") ||
                IsAudioCentricUrl(u))
                return u;
            string[] hosts = new string[]
            {
                "youtube.com/", "youtu.be/", "music.youtube.com/",
                "soundcloud.com/", "on.soundcloud.com/", "bandcamp.com/", "audiomack.com/", "mixcloud.com/", "open.spotify.com/",
                "pornhub.com/", "pornhub.org/", "pornhubpremium.com/",
                "xvideos.com/", "xvideos.es/", "xvideos2.com/", "xvideos3.com/", "xv-ru.com/",
                "xnxx.com/", "xnxx.tv/", "xnxx.es/", "xnxx2.com/",
                "xhamster.com/", "xhamster.desi/", "xhamster2.com/", "xhamster3.com/", "xhwebsite.com/", "xhopen.com/",
                "spankbang.com/", "spankbang.party/", "spankbang.obi/",
                "redtube.com/", "youporn.com/", "tube8.com/", "thumbzilla.com/",
                "eporner.com/", "motherless.com/", "beeg.com/", "txxx.com/", "upornia.com/", "hclips.com/",
                "rule34video.com/", "hanime.tv/", "chaturbate.com/", "stripchat.com/",
                "facebook.com/", "fb.watch/", "fb.gg/", "fb.com/",
                "instagram.com/", "instagr.am/",
                "threads.net/", "threads.com/",
                "linkedin.com/", "lnkd.in/",
                "x.com/", "twitter.com/", "t.co/",
                "tiktok.com/",
                "reddit.com/", "redd.it/",
                "pinterest.com/", "pin.it/",
                "vimeo.com/", "dailymotion.com/", "twitch.tv/", "bilibili.com/", "rumble.com/", "odysee.com/", "bitchute.com/", "streamable.com/", "vk.com/", "ok.ru/"
            };
            foreach (string h in hosts)
            {
                if (low.Contains(h)) return u;
            }
            if (low.Contains("/video") || low.Contains("/watch") || low.Contains("/reel") || low.Contains("/shorts/") || low.Contains("/embed/") || low.Contains("viewkey=") || low.Contains("/track/"))
            {
                return u;
            }
            try
            {
                var uri = new Uri(u);
                if (uri.Host.Contains(".") && uri.AbsolutePath.Length > 1 && !uri.Host.Contains("google.com"))
                {
                    return u;
                }
            }
            catch { }
            return null;
        }

        private void ClearUrlInputAndPreview()
        {
            if (pasteDebounceTimer != null) pasteDebounceTimer.Stop();
            lastAutoCheckedInputUrl = "";
            txtUrl.Clear();
            currentMedia = null;
            pnlMediaCard.Visible = false;
        }

        internal SocialPlatform DetectPlatform(string url)
        {
            string u = (url ?? "").ToLowerInvariant();
            string[] adultDomains = new string[]
            {
                "pornhub.com", "pornhub.org", "pornhubpremium.com",
                "xvideos.com", "xvideos.es", "xvideos2.com", "xvideos3.com", "xv-ru.com",
                "xnxx.com", "xnxx.tv", "xnxx.es", "xnxx2.com",
                "xhamster.com", "xhamster.desi", "xhamster2.com", "xhamster3.com", "xhwebsite.com", "xhopen.com",
                "spankbang.com", "spankbang.party", "spankbang.obi",
                "redtube.com", "youporn.com", "tube8.com", "thumbzilla.com",
                "eporner.com", "motherless.com", "beeg.com", "txxx.com", "upornia.com", "hclips.com",
                "rule34video.com", "hanime.tv", "chaturbate.com", "stripchat.com"
            };
            foreach (string ad in adultDomains)
            {
                if (u.Contains(ad)) return SocialPlatform.Pornhub;
            }
            if (u.Contains("facebook.com") || u.Contains("fb.watch") || u.Contains("fb.gg") || u.Contains("fb.com"))
                return SocialPlatform.Facebook;
            if (u.Contains("instagram.com") || u.Contains("instagr.am"))
                return SocialPlatform.Instagram;
            if (u.Contains("threads.net") || u.Contains("threads.com"))
                return SocialPlatform.Threads;
            if (u.Contains("linkedin.com") || u.Contains("lnkd.in"))
                return SocialPlatform.LinkedIn;
            if (u.Contains("x.com/") || u.Contains("twitter.com/") || u.Contains("t.co/"))
                return SocialPlatform.TwitterX;
            if (u.Contains("youtube.com") || u.Contains("youtu.be"))
                return SocialPlatform.YouTube;
            return SocialPlatform.TikTokOrOther;
        }

        private Button CreateStyledButton(string text, Color bg, int width, int height)
        {
            var btn = new Button
            {
                Text = text,
                Size = new Size(width, height),
                BackColor = bg,
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Cursor = Cursors.Hand,
                Font = new Font("Segoe UI Semibold", 9.5f)
            };
            btn.FlatAppearance.BorderSize = 0;
            return btn;
        }

        private async Task CheckEngineAsync()
        {
            await Task.Run(() =>
            {
                if (!File.Exists(YtDlpPath))
                {
                    if (IsHandleCreated) Invoke(new Action(() => lblEngineStatus.Text = "Downloading yt-dlp.exe..."));
                    Directory.CreateDirectory(BinDir);
                    string tmpPath = YtDlpPath + ".tmp";
                    try
                    {
                        using (var wc = new WebClient())
                        {
                            wc.DownloadFile("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", tmpPath);
                        }
                        if (File.Exists(YtDlpPath)) File.Delete(YtDlpPath);
                        File.Move(tmpPath, YtDlpPath);
                    }
                    catch
                    {
                        try { if (File.Exists(tmpPath)) File.Delete(tmpPath); } catch { }
                    }
                }
                string ver = RunShortCommand(YtDlpPath, "--version");
                bool hasFfmpeg = File.Exists(FfmpegPath);
                if (IsHandleCreated)
                {
                    Invoke(new Action(() =>
                    {
                        lblEngineStatus.Text = string.Format("✔ yt-dlp {0} • FFmpeg {1}",
                            string.IsNullOrWhiteSpace(ver) ? "Ready" : ver.Trim(),
                            hasFfmpeg ? "Ready" : "System");
                    }));
                }
            });
        }

        private async Task UpdateEngineAsync()
        {
            lblEngineStatus.Text = I18n.T("engine_checking");
            await Task.Run(() =>
            {
                RunShortCommand(YtDlpPath, "-U");
            });
            await CheckEngineAsync();
            MessageBox.Show(I18n.T("msg_engine_updated"), "Engine Update", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }

        internal string NormalizeMediaUrl(string rawUrl)
        {
            string url = (rawUrl ?? "").Trim();
            if (string.IsNullOrEmpty(url)) return url;

            string extracted = ExtractSupportedUrlFromText(url);
            if (!string.IsNullOrEmpty(extracted))
            {
                url = extracted;
            }
            else if (!url.StartsWith("http://", StringComparison.OrdinalIgnoreCase) &&
                     !url.StartsWith("https://", StringComparison.OrdinalIgnoreCase) &&
                     url.Contains(".") && url.Contains("/"))
            {
                url = "https://" + url;
            }

            if ((chkAllowMixPlaylist == null || !chkAllowMixPlaylist.Checked) && url.Contains("watch?v=") && url.Contains("list=RD"))
            {
                try
                {
                    var uri = new Uri(url);
                    var query = uri.Query.TrimStart('?').Split('&');
                    string v = query.FirstOrDefault(p => p.StartsWith("v="));
                    if (!string.IsNullOrEmpty(v))
                    {
                        return "https://www.youtube.com/watch?" + v;
                    }
                }
                catch { }
            }

            if (url.Contains("instagram.com/reels/"))
            {
                url = url.Replace("instagram.com/reels/", "instagram.com/reel/");
            }

            return url;
        }

        internal bool IsSingleVideoUrl(string url, SocialPlatform platform)
        {
            string u = (url ?? "").ToLowerInvariant();
            if (u.Contains("/sets/") || u.Contains("/album/") || u.Contains("/playlist") ||
                u.Contains("/browse/mpreb_") || u.Contains("/browse/vl"))
            {
                return false;
            }
            if (platform != SocialPlatform.YouTube)
            {
                return true;
            }
            if (u.Contains("list=pl") || u.Contains("list=ol") || u.Contains("list=uu") || u.Contains("list=fl"))
            {
                return false;
            }
            if (chkAllowMixPlaylist != null && chkAllowMixPlaylist.Checked && u.Contains("list=")) return false;
            return (u.Contains("youtube.com") && u.Contains("v=")) || u.Contains("youtu.be/") || u.Contains("/shorts/") || u.Contains("/live/");
        }

        private async Task FetchMediaInfoAsync(bool autoDownloadOnSuccess = false)
        {
            string rawUrl = txtUrl.Text.Trim();
            if (string.IsNullOrWhiteSpace(rawUrl))
            {
                MessageBox.Show(I18n.T("msg_enter_url"), "URL Required", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            if (pasteDebounceTimer != null) pasteDebounceTimer.Stop();
            lastAutoGrabbedUrl = rawUrl;
            lastAutoCheckedInputUrl = rawUrl;
            long mySeq = Interlocked.Increment(ref fetchSequence);
            if (fetchCts != null) fetchCts.Cancel();
            fetchCts = new CancellationTokenSource();
            var token = fetchCts.Token;

            string url = NormalizeMediaUrl(rawUrl);
            SocialPlatform platform = DetectPlatform(url);
            selectedPlatform = platform;

            bool useCookies = chkUseBrowserCookies.Checked;
            string browserName = cmbCookieBrowser.SelectedItem != null ? cmbCookieBrowser.SelectedItem.ToString() : "edge";

            btnFetch.Text = I18n.T("btn_searching");

            try
            {
                if (!File.Exists(YtDlpPath) && engineInitTask != null)
                {
                    await engineInitTask;
                }
                bool single = IsSingleVideoUrl(url, platform);
                string cookieArg = useCookies ? ("--cookies-from-browser " + browserName) : "";
                string args = string.Format("--dump-single-json --flat-playlist --no-warnings --no-check-certificates --socket-timeout 25 {0} {1} \"{2}\"",
                    single ? "--no-playlist" : "", cookieArg, url);

                string jsonOut = null;
                Exception firstEx = null;
                try
                {
                    jsonOut = await Task.Run(() => RunCommandCaptureStdout(YtDlpPath, args, token), token);
                }
                catch (Exception ex1)
                {
                    firstEx = ex1;
                }

                if (firstEx != null)
                {
                    if (!token.IsCancellationRequested && (useCookies || (platform != SocialPlatform.YouTube && platform != SocialPlatform.Pornhub)))
                    {
                        string fallbackCookieArg = useCookies ? "" : ("--cookies-from-browser " + browserName);
                        string fallbackArgs = string.Format("--dump-single-json --flat-playlist --no-warnings --no-check-certificates --socket-timeout 25 {0} {1} \"{2}\"",
                            single ? "--no-playlist" : "", fallbackCookieArg, url);
                        try
                        {
                            jsonOut = await Task.Run(() => RunCommandCaptureStdout(YtDlpPath, fallbackArgs, token), token);
                            firstEx = null;
                        }
                        catch { }
                    }
                }

                if (token.IsCancellationRequested || mySeq != Interlocked.Read(ref fetchSequence)) return;

                MediaMetadata meta = null;
                if (firstEx == null && !string.IsNullOrWhiteSpace(jsonOut) && jsonOut.Contains("{"))
                {
                    int idx = jsonOut.IndexOf('{');
                    var serializer = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                    var root = serializer.Deserialize<Dictionary<string, object>>(jsonOut.Substring(idx));
                    if (mySeq != Interlocked.Read(ref fetchSequence)) return;
                    meta = ParseMetadata(url, rawUrl, platform, root);
                }
                else
                {
                    meta = await Task.Run(() => TryExtractUniversalWebMetadata(url, rawUrl, platform));
                    if (meta == null)
                    {
                        if (firstEx != null) throw firstEx;
                        throw new Exception(I18n.T("msg_no_info"));
                    }
                }

                currentMedia = meta;
                DisplayMediaMetadata(meta);

                if (autoDownloadOnSuccess && (!meta.IsPlaylist || (meta.Entries != null && meta.Entries.Count > 0 && meta.Entries.Count <= 50)))
                {
                    EnqueueCurrentMedia();
                }
            }
            catch (OperationCanceledException) { }
            catch (Exception ex)
            {
                if (mySeq == Interlocked.Read(ref fetchSequence))
                {
                    string errLine = FirstLine(ex.Message);
                    SaveFailedLink(url, rawUrl, platform, null, errLine);

                    string hint = (platform != SocialPlatform.YouTube)
                        ? I18n.T("msg_cookie_hint")
                        : "";
                    string msg = errLine + hint + I18n.T("msg_failed_link_saved");
                    if (MessageBox.Show(msg, "Link Saved — Retry / Direct Download", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes)
                    {
                        EnqueueDirectFromUrl(rawUrl);
                    }
                }
            }
            finally
            {
                if (mySeq == Interlocked.Read(ref fetchSequence))
                {
                    btnFetch.Enabled = true;
                    btnFetch.Text = I18n.T("btn_search");
                }
            }
        }

        private MediaMetadata ParseMetadata(string url, string rawUrl, SocialPlatform platform, Dictionary<string, object> root)
        {
            string defaultPlatformName = platform == SocialPlatform.Pornhub ? "Pornhub Video"
                : platform == SocialPlatform.Facebook ? "Facebook Video"
                : platform == SocialPlatform.Instagram ? "Instagram Reel/Post"
                : platform == SocialPlatform.Threads ? "Threads Video"
                : platform == SocialPlatform.LinkedIn ? "LinkedIn Video"
                : platform == SocialPlatform.TwitterX ? "X (Twitter) Video"
                : platform == SocialPlatform.TikTokOrOther ? "Social Video"
                : "YouTube Media";

            string title = GetStr(root, "title") ?? GetStr(root, "description") ?? defaultPlatformName;
            title = Regex.Replace(title, @"\s+", " ").Trim();
            if (title.Length > 140) title = title.Substring(0, 140) + "...";

            var meta = new MediaMetadata
            {
                Url = GetStr(root, "webpage_url") ?? url,
                RawInputUrl = rawUrl,
                Platform = platform,
                Title = title,
                Uploader = GetStr(root, "uploader") ?? GetStr(root, "channel") ?? GetStr(root, "uploader_id") ?? defaultPlatformName,
                Thumbnail = GetStr(root, "thumbnail") ?? ExtractLastThumbnail(root),
                DurationSec = GetLong(root, "duration"),
                IsAudioCentric = IsAudioCentricUrl(rawUrl) || IsAudioCentricUrl(url)
            };

            string type = GetStr(root, "_type");
            if (type == "playlist" || root.ContainsKey("entries"))
            {
                var rawEntries = root["entries"] as System.Collections.ArrayList;
                if (rawEntries != null && (type == "playlist" || platform == SocialPlatform.YouTube || rawEntries.Count > 1))
                {
                    meta.IsPlaylist = true;
                    bool isYtMusic = (rawUrl ?? "").IndexOf("music.youtube.com", StringComparison.OrdinalIgnoreCase) >= 0;
                    foreach (var itemObj in rawEntries)
                    {
                        var e = itemObj as Dictionary<string, object>;
                        if (e == null) continue;
                        string id = GetStr(e, "id");
                        string eTitle = GetStr(e, "title") ?? (defaultPlatformName + " " + (meta.Entries.Count + 1));
                        if (eTitle == "[Private video]" || eTitle == "[Deleted video]")
                            continue;
                        string entryUrl = GetStr(e, "url");
                        if (string.IsNullOrWhiteSpace(entryUrl) || !entryUrl.StartsWith("http"))
                        {
                            string ytHost = isYtMusic ? "https://music.youtube.com/watch?v=" : "https://www.youtube.com/watch?v=";
                            entryUrl = GetStr(e, "webpage_url") ?? (!string.IsNullOrWhiteSpace(id) && platform == SocialPlatform.YouTube ? ytHost + id : url);
                        }
                        if (string.IsNullOrWhiteSpace(entryUrl)) continue;

                        meta.Entries.Add(new PlaylistEntryItem
                        {
                            Id = id,
                            Title = eTitle,
                            Url = entryUrl,
                            DurationSec = GetLong(e, "duration"),
                            Uploader = GetStr(e, "uploader") ?? GetStr(e, "channel") ?? meta.Uploader,
                            Thumbnail = GetStr(e, "thumbnail") ?? ExtractLastThumbnail(e)
                        });
                    }
                    if (string.IsNullOrWhiteSpace(meta.Thumbnail) && meta.Entries.Count > 0)
                    {
                        meta.Thumbnail = meta.Entries[0].Thumbnail;
                    }
                    return meta;
                }
            }

            var heights = new HashSet<int>();
            var formats = root.ContainsKey("formats") ? root["formats"] as System.Collections.ArrayList : null;
            if (formats != null)
            {
                foreach (var fObj in formats)
                {
                    var f = fObj as Dictionary<string, object>;
                    if (f == null) continue;
                    string vcodec = GetStr(f, "vcodec");
                    if (vcodec == "none") continue;
                    int h = (int)GetLong(f, "height");
                    int w = (int)GetLong(f, "width");
                    int side = (w > 0 && h > 0) ? Math.Min(w, h) : h;
                    if (side >= 144) heights.Add(SnapStandardHeight(side));
                }
            }
            meta.VideoHeights = heights.OrderByDescending(x => x).ToList();
            return meta;
        }

        private int SnapStandardHeight(int h)
        {
            int[] std = { 4320, 2160, 1440, 1080, 720, 480, 360, 240, 144 };
            foreach (int s in std)
            {
                if (h >= s * 0.9) return s;
            }
            return h;
        }

        private void DisplayMediaMetadata(MediaMetadata meta)
        {
            lblPlatformBadge.ForeColor = Color.White;
            btnDownloadNow.ForeColor = Color.White;
            string lowUrl = (meta.RawInputUrl ?? meta.Url ?? "").ToLowerInvariant();
            switch (meta.Platform)
            {
                case SocialPlatform.Pornhub:
                    lblPlatformBadge.Text = lowUrl.Contains("xvideos") || lowUrl.Contains("xv-ru") ? "XVIDEOS"
                        : lowUrl.Contains("xnxx") ? "XNXX"
                        : lowUrl.Contains("xhamster") || lowUrl.Contains("xhwebsite") ? "XHAMSTER"
                        : lowUrl.Contains("spankbang") ? "SPANKBANG"
                        : lowUrl.Contains("redtube") ? "REDTUBE"
                        : lowUrl.Contains("youporn") ? "YOUPORN"
                        : lowUrl.Contains("eporner") ? "EPORNER"
                        : lowUrl.Contains("pornhub") ? "PORNHUB"
                        : "ADULT 18+";
                    lblPlatformBadge.BackColor = Color.FromArgb(255, 153, 0);
                    lblPlatformBadge.ForeColor = Color.Black;
                    btnDownloadNow.BackColor = Color.FromArgb(255, 153, 0);
                    btnDownloadNow.ForeColor = Color.Black;
                    break;
                case SocialPlatform.Facebook:
                    lblPlatformBadge.Text = "FACEBOOK";
                    lblPlatformBadge.BackColor = AccentFbBlue;
                    btnDownloadNow.BackColor = AccentFbBlue;
                    break;
                case SocialPlatform.Instagram:
                    lblPlatformBadge.Text = "INSTAGRAM";
                    lblPlatformBadge.BackColor = AccentInstaPink;
                    btnDownloadNow.BackColor = AccentInstaPink;
                    break;
                case SocialPlatform.Threads:
                    lblPlatformBadge.Text = "THREADS";
                    lblPlatformBadge.BackColor = Color.FromArgb(40, 42, 52);
                    btnDownloadNow.BackColor = AccentInstaPink;
                    break;
                case SocialPlatform.LinkedIn:
                    lblPlatformBadge.Text = "LINKEDIN";
                    lblPlatformBadge.BackColor = Color.FromArgb(10, 102, 194);
                    btnDownloadNow.BackColor = Color.FromArgb(10, 102, 194);
                    break;
                case SocialPlatform.TwitterX:
                    lblPlatformBadge.Text = "X / TWITTER";
                    lblPlatformBadge.BackColor = Color.FromArgb(29, 155, 240);
                    btnDownloadNow.BackColor = Color.FromArgb(29, 155, 240);
                    break;
                case SocialPlatform.TikTokOrOther:
                    lblPlatformBadge.Text = lowUrl.Contains("tiktok.com") ? "TIKTOK"
                        : lowUrl.Contains("soundcloud") ? "SOUNDCLOUD AUDIO"
                        : lowUrl.Contains("bandcamp") ? "BANDCAMP AUDIO"
                        : (meta.IsAudioCentric || IsAudioCentricUrl(lowUrl)) ? "BEST AUDIO"
                        : "ANY SITE / WEB VIDEO";
                    lblPlatformBadge.BackColor = AccentTikTokTeal;
                    btnDownloadNow.BackColor = AccentTikTokTeal;
                    break;
                default:
                    lblPlatformBadge.Text = lowUrl.Contains("music.youtube.com") ? "YT MUSIC (BEST AUDIO)" : "YOUTUBE";
                    lblPlatformBadge.BackColor = AccentRed;
                    btnDownloadNow.BackColor = AccentRed;
                    break;
            }

            lblMediaTitle.Left = lblPlatformBadge.Right + 10;
            lblMediaTitle.Text = meta.Title;
            if (txtCustomTitle != null)
            {
                txtCustomTitle.Left = lblPlatformBadge.Right + 10;
                txtCustomTitle.Text = meta.Title;
                txtCustomTitle.ReadOnly = false;
                int rightLimit = btnPreviewSaveCover != null ? btnPreviewSaveCover.Left : (btnPreviewCopyName != null ? btnPreviewCopyName.Left : 800);
                txtCustomTitle.Width = Math.Max(160, rightLimit - txtCustomTitle.Left - 8);
            }

            bool defaultAudio = meta.IsAudioCentric || IsAudioCentricUrl(lowUrl);
            rbVideo.Checked = !defaultAudio;
            rbAudio.Checked = defaultAudio;
            cmbVideoQuality.Enabled = !defaultAudio;
            cmbAudioFormat.Enabled = defaultAudio;

            if (meta.IsPlaylist)
            {
                lblMediaSub.Text = string.Format("📂 {0} • {1} items", meta.Uploader, meta.Entries.Count);
                pnlPlaylist.Visible = true;
                pnlMediaCard.Height = 305;

                lvPlaylist.BeginUpdate();
                lvPlaylist.Items.Clear();
                for (int i = 0; i < meta.Entries.Count; i++)
                {
                    var entry = meta.Entries[i];
                    var lvi = new ListViewItem((i + 1).ToString()) { Checked = true, Tag = entry };
                    lvi.SubItems.Add(entry.Title);
                    lvi.SubItems.Add(entry.Uploader ?? "");
                    lvi.SubItems.Add(FormatDuration(entry.DurationSec));
                    lvPlaylist.Items.Add(lvi);
                }
                lvPlaylist.EndUpdate();
            }
            else
            {
                lblMediaSub.Text = string.Format("🎬 {0} • {1}", meta.Uploader, FormatDuration(meta.DurationSec));
                pnlPlaylist.Visible = false;
                pnlMediaCard.Height = 156;

                cmbVideoQuality.Items.Clear();
                cmbVideoQuality.Items.Add(I18n.T("best_mp4"));
                var heights = meta.VideoHeights.Count > 0 ? meta.VideoHeights : new List<int> { 1080, 720, 480, 360 };
                int preferredIdx = 0;
                for (int i = 0; i < heights.Count; i++)
                {
                    int h = heights[i];
                    string tag = h == 2160 ? " (4K)" : h == 1440 ? " (2K)" : h == 1080 ? " (Full HD)" : h == 720 ? " (HD)" : "";
                    cmbVideoQuality.Items.Add(h + "p" + tag);
                    if (h == 1080 || (preferredIdx == 0 && i == 0))
                    {
                        preferredIdx = i + 1;
                    }
                }
                cmbVideoQuality.SelectedIndex = preferredIdx;
            }

            pnlMediaCard.Visible = true;

            picThumbnail.Image = null;
            if (!string.IsNullOrWhiteSpace(meta.Thumbnail))
            {
                string thumbUrl = meta.Thumbnail;
                Task.Run(() =>
                {
                    try
                    {
                        using (var wc = new WebClient())
                        {
                            wc.Headers.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                            byte[] bytes = wc.DownloadData(thumbUrl);
                            using (var ms = new MemoryStream(bytes))
                            {
                                var img = Image.FromStream(ms);
                                Invoke(new Action(() => picThumbnail.Image = img));
                            }
                        }
                    }
                    catch { }
                });
            }
        }

        private void SaveCurrentMusicCover()
        {
            var meta = currentMedia;
            if (meta == null) return;
            string thumbUrl = meta.Thumbnail;
            if (string.IsNullOrWhiteSpace(thumbUrl) && meta.Entries != null && meta.Entries.Count > 0)
            {
                thumbUrl = meta.Entries[0].Thumbnail;
            }
            if (string.IsNullOrWhiteSpace(thumbUrl))
            {
                MessageBox.Show("No Music Cover / Thumbnail available for this item.", "Save Cover", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }
            string title = (txtCustomTitle != null && !string.IsNullOrWhiteSpace(txtCustomTitle.Text))
                ? txtCustomTitle.Text.Trim()
                : meta.Title;
            string safeBase = SanitizeFileName(title);
            string coversDir = Path.Combine(DownloadFolder, "Covers");
            Directory.CreateDirectory(coversDir);
            string destPath = Path.Combine(coversDir, safeBase + " [Cover].jpg");

            Task.Run(() =>
            {
                try
                {
                    byte[] bytes = null;
                    using (var wc = new WebClient())
                    {
                        wc.Headers.Add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                        if (thumbUrl.Contains("ytimg.com/vi/") && !thumbUrl.Contains("maxresdefault.jpg"))
                        {
                            string hiRes = Regex.Replace(thumbUrl, @"/(?:hqdefault|mqdefault|sddefault|default)\.jpg", "/maxresdefault.jpg");
                            try { bytes = wc.DownloadData(hiRes); } catch { bytes = null; }
                        }
                        if (bytes == null || bytes.Length < 512)
                        {
                            bytes = wc.DownloadData(thumbUrl);
                        }
                    }
                    File.WriteAllBytes(destPath, bytes);
                    Invoke(new Action(() =>
                    {
                        MessageBox.Show("Music Cover saved to:\n" + destPath, "Music Cover Saved", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    }));
                }
                catch (Exception ex)
                {
                    Invoke(new Action(() =>
                    {
                        MessageBox.Show("Could not save cover image: " + ex.Message, "Save Cover Error", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                    }));
                }
            });
        }

        private string GetSelectedAudioFormatCode()
        {
            if (cmbAudioFormat == null) return "mp3";
            switch (cmbAudioFormat.SelectedIndex)
            {
                case 1: return "m4a";
                case 2: return "opus";
                case 3: return "flac";
                case 4: return "wav";
                default: return "mp3";
            }
        }

        private void EnqueueCurrentMedia()
        {
            var media = Interlocked.Exchange(ref currentMedia, null);
            if (media == null) return;

            bool isAudio = rbAudio.Checked;
            string audioFmt = GetSelectedAudioFormatCode();
            int? videoHeight = null;
            if (!isAudio && cmbVideoQuality.SelectedIndex > 0)
            {
                string sel = cmbVideoQuality.SelectedItem.ToString();
                var m = Regex.Match(sel, @"^(\d+)p");
                if (m.Success) videoHeight = int.Parse(m.Groups[1].Value);
            }

            string cookieBrowser = chkUseBrowserCookies.Checked && cmbCookieBrowser.SelectedItem != null
                ? cmbCookieBrowser.SelectedItem.ToString()
                : null;

            var newJobs = new List<DownloadJob>();
            if (media.IsPlaylist)
            {
                foreach (ListViewItem item in lvPlaylist.Items)
                {
                    if (!item.Checked) continue;
                    var entry = item.Tag as PlaylistEntryItem;
                    if (entry == null) continue;

                    string plFolder = Path.Combine(DownloadFolder, SanitizeFileName(media.Title));
                    newJobs.Add(new DownloadJob
                    {
                        Url = entry.Url,
                        RawInputUrl = media.RawInputUrl,
                        Platform = media.Platform,
                        Title = entry.Title,
                        Uploader = entry.Uploader,
                        PlaylistTitle = media.Title,
                        IsAudio = isAudio,
                        AudioFormat = audioFmt,
                        VideoHeight = videoHeight,
                        OutputDir = plFolder,
                        BrowserCookies = cookieBrowser
                    });
                }
                if (newJobs.Count == 0)
                {
                    currentMedia = media;
                    MessageBox.Show(I18n.T("msg_select_one"), "Selection Required", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    return;
                }
            }
            else
            {
                string customTitle = (txtCustomTitle != null && !string.IsNullOrWhiteSpace(txtCustomTitle.Text))
                    ? txtCustomTitle.Text.Trim()
                    : null;
                newJobs.Add(new DownloadJob
                {
                    Url = media.Url,
                    RawInputUrl = media.RawInputUrl,
                    Platform = media.Platform,
                    Title = customTitle ?? media.Title,
                    CustomTitle = customTitle,
                    Uploader = media.Uploader,
                    IsAudio = isAudio,
                    AudioFormat = audioFmt,
                    VideoHeight = videoHeight,
                    OutputDir = DownloadFolder,
                    BrowserCookies = cookieBrowser
                });
            }

            // Immediately clear URL box and hide preview so old link never stays in input box
            ClearUrlInputAndPreview();

            if (adConfig.OpenSponsorOnFirstDownload && !sponsorOpenedThisSession)
            {
                sponsorOpenedThisSession = true;
                OpenSponsorLink();
            }

            foreach (var job in newJobs)
            {
                // Deduplicate against already queued/active/paused jobs with identical URL & format
                var existing = jobs.FirstOrDefault(j =>
                    !j.Canceled && !j.Finished &&
                    string.Equals(j.Url, job.Url, StringComparison.OrdinalIgnoreCase) &&
                    j.IsAudio == job.IsAudio &&
                    j.VideoHeight == job.VideoHeight &&
                    string.Equals(j.AudioFormat, job.AudioFormat, StringComparison.OrdinalIgnoreCase));
                if (existing != null)
                {
                    if ((existing.Paused || existing.Failed) && !existing.IsActive)
                    {
                        existing.Paused = false;
                        existing.Failed = false;
                        UpdateJobUi(existing, I18n.T("status_starting"), existing.Progress, "-", "-");
                        var tr = RunDownloadJobAsync(existing);
                    }
                    continue;
                }

                jobs.Add(job);
                var lvi = new ListViewItem(job.Title) { Tag = job };
                lvi.SubItems.Add(job.QualityLabel);
                lvi.SubItems.Add(job.Status);
                lvi.SubItems.Add("0%");
                lvi.SubItems.Add("-");
                lvi.SubItems.Add("-");
                lvQueue.Items.Add(lvi);

                var t = RunDownloadJobAsync(job);
            }

            SaveJobs();
            tabBottom.SelectedIndex = 0;
        }

        private async Task RunDownloadJobAsync(DownloadJob job)
        {
            long myGen = Interlocked.Increment(ref job.RunGeneration);
            job.IsActive = true;
            await downloadLock.WaitAsync();
            if (job.Canceled || job.Paused || Interlocked.Read(ref job.RunGeneration) != myGen)
            {
                if (Interlocked.Read(ref job.RunGeneration) == myGen) job.IsActive = false;
                downloadLock.Release();
                return;
            }

            try
            {
                Directory.CreateDirectory(job.OutputDir);
                UpdateJobUi(job, I18n.T("status_starting"), Math.Max(1f, job.Progress), "-", "-");

                string pathFile = Path.Combine(Path.GetTempPath(), "ytdl_path_" + job.Id + ".txt");
                if (File.Exists(pathFile)) File.Delete(pathFile);

                bool hasCookies = !string.IsNullOrEmpty(job.BrowserCookies);
                bool retryWithoutCookies = false;
                try
                {
                    await Task.Run(() => ExecuteYtDlpDownloadProcess(job, pathFile, hasCookies));
                }
                catch (OperationCanceledException)
                {
                    throw;
                }
                catch
                {
                    if (hasCookies && !job.Canceled && !job.Paused)
                    {
                        retryWithoutCookies = true;
                    }
                    else
                    {
                        throw;
                    }
                }

                if (retryWithoutCookies)
                {
                    if (File.Exists(pathFile)) try { File.Delete(pathFile); } catch { }
                    await Task.Run(() => ExecuteYtDlpDownloadProcess(job, pathFile, false));
                }

                if (Interlocked.Read(ref job.RunGeneration) != myGen) return;

                job.Finished = true;
                job.Paused = false;
                job.Failed = false;
                UpdateJobUi(job, I18n.T("status_done"), 100f, "-", "00:00");

                long sizeBytes = 0;
                if (!string.IsNullOrEmpty(job.OutputFilePath) && File.Exists(job.OutputFilePath))
                {
                    sizeBytes = new FileInfo(job.OutputFilePath).Length;
                }

                Invoke(new Action(() =>
                {
                    string currentBox = txtUrl.Text.Trim();
                    if (!string.IsNullOrEmpty(currentBox) &&
                        (string.Equals(currentBox, job.Url, StringComparison.OrdinalIgnoreCase) ||
                         string.Equals(currentBox, job.RawInputUrl, StringComparison.OrdinalIgnoreCase)))
                    {
                        ClearUrlInputAndPreview();
                    }

                    history.RemoveAll(h => h.Id == job.Id || (h.Status == "FAILED" && string.Equals(h.Url, job.Url, StringComparison.OrdinalIgnoreCase)));
                    history.Insert(0, new HistoryRecord
                    {
                        Id = job.Id,
                        DateStr = DateTime.Now.ToString("yyyy-MM-dd HH:mm"),
                        Title = job.Title,
                        Uploader = job.Uploader ?? "",
                        Quality = job.QualityLabel,
                        FilePath = job.OutputFilePath,
                        Url = job.Url,
                        SizeBytes = sizeBytes,
                        Status = "DONE"
                    });
                    SaveHistory();
                    SaveJobs();
                    RefreshHistoryListView();
                }));
            }
            catch (OperationCanceledException)
            {
                if (Interlocked.Read(ref job.RunGeneration) == myGen)
                {
                    if (job.Paused && !job.Canceled)
                    {
                        UpdateJobUi(job, I18n.T("status_paused"), job.Progress, "-", "-");
                    }
                    else
                    {
                        UpdateJobUi(job, I18n.T("status_canceled"), 0f, "-", "-");
                    }
                    SaveJobs();
                }
            }
            catch (Exception ex)
            {
                if (Interlocked.Read(ref job.RunGeneration) == myGen)
                {
                    job.Failed = true;
                    string errShort = FirstLine(ex.Message);
                    UpdateJobUi(job, I18n.T("status_failed") + errShort, job.Progress, "-", "-");
                    Invoke(new Action(() =>
                    {
                        history.RemoveAll(h => h.Id == job.Id);
                        history.Insert(0, new HistoryRecord
                        {
                            Id = job.Id,
                            DateStr = DateTime.Now.ToString("yyyy-MM-dd HH:mm"),
                            Title = job.Title,
                            Uploader = job.Uploader ?? "Saved Link",
                            Quality = "Failed • " + job.QualityLabel,
                            FilePath = "Failed: " + errShort,
                            Url = job.Url,
                            SizeBytes = 0,
                            Status = "FAILED"
                        });
                        SaveHistory();
                        SaveJobs();
                        RefreshHistoryListView();
                    }));
                }
            }
            finally
            {
                if (Interlocked.Read(ref job.RunGeneration) == myGen)
                {
                    job.IsActive = false;
                }
                downloadLock.Release();
            }
        }

        private void ExecuteYtDlpDownloadProcess(DownloadJob job, string pathFile, bool useCookies)
        {
            string outTemplate = !string.IsNullOrWhiteSpace(job.CustomTitle)
                ? (SanitizeFileName(job.CustomTitle) + ".%(ext)s")
                : "%(title).95B [%(id)s].%(ext)s";
            var args = new List<string>
            {
                "--no-playlist",
                "--continue",
                "--retries", "10",
                "--fragment-retries", "10",
                "--socket-timeout", "30",
                "--no-mtime",
                "--no-check-certificates",
                "--windows-filenames",
                "--newline",
                "--encoding", "utf-8",
                "-o", string.Format("\"{0}\"", Path.Combine(job.OutputDir, outTemplate)),
                "--print-to-file", "after_move:filepath", string.Format("\"{0}\"", pathFile)
            };

            if (useCookies && !string.IsNullOrEmpty(job.BrowserCookies))
            {
                args.Add("--cookies-from-browser");
                args.Add(job.BrowserCookies);
            }

            if (job.Platform == SocialPlatform.Pornhub || job.Platform == SocialPlatform.TikTokOrOther)
            {
                string refUrl = !string.IsNullOrWhiteSpace(job.RawInputUrl) ? job.RawInputUrl : job.Url;
                if (!string.IsNullOrWhiteSpace(refUrl) &&
                    refUrl.StartsWith("http", StringComparison.OrdinalIgnoreCase) &&
                    !string.Equals(refUrl, job.Url, StringComparison.OrdinalIgnoreCase))
                {
                    args.Add("--referer");
                    args.Add(string.Format("\"{0}\"", refUrl));
                }
            }

            if (File.Exists(FfmpegPath))
            {
                args.Add("--ffmpeg-location");
                args.Add(string.Format("\"{0}\"", BinDir));
            }

            if (job.IsAudio)
            {
                args.Add("-f");
                if (job.AudioFormat == "m4a")
                    args.Add("ba[ext=m4a]/ba[acodec*=mp4a]/ba/b");
                else if (job.AudioFormat == "opus")
                    args.Add("ba[acodec*=opus]/ba/b");
                else
                    args.Add("ba[acodec*=opus]/ba[ext=m4a]/ba/b");
                args.Add("-x");
                args.Add("--audio-format");
                args.Add(job.AudioFormat ?? "mp3");
                args.Add("--audio-quality");
                args.Add("0");
                if (File.Exists(FfmpegPath))
                {
                    args.Add("--embed-metadata");
                    if (!string.Equals(job.AudioFormat, "wav", StringComparison.OrdinalIgnoreCase))
                    {
                        args.Add("--embed-thumbnail");
                        args.Add("--convert-thumbnails");
                        args.Add("jpg");
                    }
                }
            }
            else
            {
                args.Add("-f");
                args.Add("bv*+ba/b");
                string resSort = job.VideoHeight.HasValue ? ("res:" + job.VideoHeight.Value + ",") : "";
                args.Add("-S");
                args.Add(resSort + "vcodec:h264,acodec:m4a,ext:mp4:m4a");
                args.Add("--merge-output-format");
                args.Add("mp4");
            }

            args.Add(string.Format("\"{0}\"", job.Url));
            string cmdLine = string.Join(" ", args);

            var psi = new ProcessStartInfo
            {
                FileName = YtDlpPath,
                Arguments = cmdLine,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };

            using (var proc = new Process { StartInfo = psi })
            {
                job.RunningProcess = proc;
                var regexProgress = new Regex(@"\[download\]\s+([\d.]+)%(?:.*?at\s+([^\s]+))?(?:.*?ETA\s+([^\s]+))?");

                proc.OutputDataReceived += (s, e) =>
                {
                    if (string.IsNullOrEmpty(e.Data) || job.Paused || job.Canceled) return;
                    string line = e.Data;
                    var m = regexProgress.Match(line);
                    if (m.Success)
                    {
                        float pct;
                        if (float.TryParse(m.Groups[1].Value, System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out pct))
                        {
                            string spd = m.Groups[2].Success ? m.Groups[2].Value : "-";
                            string eta = m.Groups[3].Success ? m.Groups[3].Value : "-";
                            UpdateJobUi(job, I18n.T("status_downloading"), pct, spd, eta);
                        }
                    }
                    else if (line.StartsWith("[Merger]") || line.StartsWith("[ExtractAudio]"))
                    {
                        UpdateJobUi(job, I18n.T("status_processing"), 99f, "-", "00:00");
                    }
                };

                StringBuilder errBuf = new StringBuilder();
                proc.ErrorDataReceived += (s, e) =>
                {
                    if (!string.IsNullOrEmpty(e.Data)) errBuf.AppendLine(e.Data);
                };

                proc.Start();
                proc.BeginOutputReadLine();
                proc.BeginErrorReadLine();
                proc.WaitForExit();
                job.RunningProcess = null;

                if (job.Paused || job.Canceled) throw new OperationCanceledException();

                if (File.Exists(pathFile))
                {
                    var lines = File.ReadAllLines(pathFile, Encoding.UTF8)
                        .Select(l => l.Trim())
                        .Where(l => !string.IsNullOrEmpty(l) && File.Exists(l))
                        .ToList();
                    if (lines.Count > 0) job.OutputFilePath = lines[0];
                    try { File.Delete(pathFile); } catch { }
                }

                if (proc.ExitCode != 0 && string.IsNullOrEmpty(job.OutputFilePath))
                {
                    // Universal Any-Site / Adult direct stream fallback if yt-dlp could not parse the page directly
                    if (!job.Url.Contains(".mp4") && !job.Url.Contains(".m3u8"))
                    {
                        var fallbackMeta = TryExtractUniversalWebMetadata(job.Url, job.RawInputUrl ?? job.Url, job.Platform);
                        if (fallbackMeta != null && !string.IsNullOrWhiteSpace(fallbackMeta.Url) &&
                            !string.Equals(fallbackMeta.Url, job.Url, StringComparison.OrdinalIgnoreCase))
                        {
                            job.RawInputUrl = job.Url;
                            job.Url = fallbackMeta.Url;
                            if (string.IsNullOrWhiteSpace(job.CustomTitle) && !string.IsNullOrWhiteSpace(fallbackMeta.Title))
                            {
                                job.Title = fallbackMeta.Title;
                                job.CustomTitle = fallbackMeta.Title;
                            }
                            ExecuteYtDlpDownloadProcess(job, pathFile, false);
                            return;
                        }
                    }
                    throw new Exception(errBuf.ToString().Trim());
                }
            }
        }

        internal MediaMetadata TryExtractUniversalWebMetadata(string url, string rawUrl, SocialPlatform platform)
        {
            try
            {
                string cleanPath = (url ?? "").Split('?')[0].Split('#')[0].ToLowerInvariant();
                if (cleanPath.EndsWith(".mp4") || cleanPath.EndsWith(".m3u8") || cleanPath.EndsWith(".webm") || cleanPath.EndsWith(".mov") || cleanPath.EndsWith(".mkv"))
                {
                    string fileTitle = Path.GetFileNameWithoutExtension(url.Split('?')[0]);
                    if (string.IsNullOrWhiteSpace(fileTitle)) fileTitle = "Web Video";
                    return new MediaMetadata
                    {
                        Url = url,
                        RawInputUrl = rawUrl,
                        Platform = platform,
                        Title = fileTitle,
                        Uploader = HostDisplayName(url),
                        VideoHeights = new List<int> { 1080, 720, 480 }
                    };
                }
                if (cleanPath.EndsWith(".mp3") || cleanPath.EndsWith(".m4a") || cleanPath.EndsWith(".flac") ||
                    cleanPath.EndsWith(".wav") || cleanPath.EndsWith(".opus") || cleanPath.EndsWith(".ogg") || cleanPath.EndsWith(".aac"))
                {
                    string fileTitle = Path.GetFileNameWithoutExtension(url.Split('?')[0]);
                    if (string.IsNullOrWhiteSpace(fileTitle)) fileTitle = "Web Audio";
                    return new MediaMetadata
                    {
                        Url = url,
                        RawInputUrl = rawUrl,
                        Platform = platform,
                        Title = fileTitle,
                        Uploader = HostDisplayName(url),
                        IsAudioCentric = true,
                        VideoHeights = new List<int> { 1080 }
                    };
                }

                ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12 | SecurityProtocolType.Tls11 | SecurityProtocolType.Tls;
                var req = (HttpWebRequest)WebRequest.Create(url);
                req.Method = "GET";
                req.UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
                req.Headers.Add("Accept-Language", "en-US,en;q=0.9");
                req.CookieContainer = new CookieContainer();
                req.Timeout = 20000;
                req.ReadWriteTimeout = 20000;
                req.AllowAutoRedirect = true;

                string html;
                using (var resp = (HttpWebResponse)req.GetResponse())
                using (var sr = new StreamReader(resp.GetResponseStream(), Encoding.UTF8))
                {
                    html = sr.ReadToEnd();
                }

                return ParseUniversalWebHtml(url, rawUrl, platform, html);
            }
            catch
            {
                return null;
            }
        }

        internal static MediaMetadata ParseUniversalWebHtml(string pageUrl, string rawUrl, SocialPlatform platform, string html)
        {
            if (string.IsNullOrWhiteSpace(html)) return null;

            // 0. Bandcamp multi-track Album / Playlist parsing
            if ((pageUrl ?? "").IndexOf("bandcamp.com", StringComparison.OrdinalIgnoreCase) >= 0 &&
                (pageUrl ?? "").IndexOf("/album/", StringComparison.OrdinalIgnoreCase) >= 0)
            {
                string decodedBc = WebUtility.HtmlDecode(html);
                var bcEntries = new List<PlaylistEntryItem>();
                string bcThumb = ExtractHtmlMeta(html, "og:image");
                string bcAlbumTitle = WebUtility.HtmlDecode(ExtractHtmlMeta(html, "og:title") ?? "Bandcamp Album");
                var trackMatches = Regex.Matches(
                    decodedBc,
                    @"\{[^{}]*?""title""\s*:\s*""([^""]+)""[^{}]*?""file""\s*:\s*\{\s*""[^""]+""\s*:\s*""(https?:[^""]+)""\s*\}[^{}]*?\}|\{[^{}]*?""file""\s*:\s*\{\s*""[^""]+""\s*:\s*""(https?:[^""]+)""\s*\}[^{}]*?""title""\s*:\s*""([^""]+)""[^{}]*?\}",
                    RegexOptions.IgnoreCase);
                int idx = 0;
                foreach (Match tm in trackMatches)
                {
                    idx++;
                    string rawTrackTitle = !string.IsNullOrEmpty(tm.Groups[1].Value) ? tm.Groups[1].Value : tm.Groups[4].Value;
                    string rawMp3 = !string.IsNullOrEmpty(tm.Groups[2].Value) ? tm.Groups[2].Value : tm.Groups[3].Value;
                    string mp3Url = UnescapeJsonUrl(rawMp3);
                    if (!mp3Url.StartsWith("http", StringComparison.OrdinalIgnoreCase)) continue;
                    long dur = 0;
                    var dm = Regex.Match(tm.Value, @"""duration""\s*:\s*(\d+)");
                    if (dm.Success) long.TryParse(dm.Groups[1].Value, out dur);
                    var linkM = Regex.Match(tm.Value, @"""title_link""\s*:\s*""([^""]+)""");
                    string trackUrl = linkM.Success ? (ResolveRelativeWebUrl(pageUrl, UnescapeJsonUrl(linkM.Groups[1].Value)) ?? mp3Url) : mp3Url;
                    bcEntries.Add(new PlaylistEntryItem
                    {
                        Id = idx.ToString(),
                        Title = WebUtility.HtmlDecode(UnescapeJsonUrl(rawTrackTitle)),
                        Url = trackUrl,
                        DurationSec = dur,
                        Uploader = "Bandcamp",
                        Thumbnail = !string.IsNullOrWhiteSpace(bcThumb) ? UnescapeJsonUrl(bcThumb) : null
                    });
                }
                if (bcEntries.Count >= 2)
                {
                    return new MediaMetadata
                    {
                        Url = pageUrl,
                        RawInputUrl = rawUrl ?? pageUrl,
                        Platform = platform,
                        Title = bcAlbumTitle,
                        Uploader = "Bandcamp",
                        Thumbnail = !string.IsNullOrWhiteSpace(bcThumb) ? UnescapeJsonUrl(bcThumb) : null,
                        IsPlaylist = true,
                        IsAudioCentric = true,
                        Entries = bcEntries
                    };
                }
            }

            var qualityMap = new Dictionary<int, string>();

            // 1. XVideos / XNXX html5player
            var xvHigh = Regex.Match(html, @"setVideoUrlHigh\(\s*['""]([^'""]+)['""]\s*\)");
            var xvLow = Regex.Match(html, @"setVideoUrlLow\(\s*['""]([^'""]+)['""]\s*\)");
            var xvHls = Regex.Match(html, @"setVideoHLS\(\s*['""]([^'""]+)['""]\s*\)");
            if (xvHigh.Success) qualityMap[720] = UnescapeJsonUrl(xvHigh.Groups[1].Value);
            if (xvLow.Success) qualityMap[360] = UnescapeJsonUrl(xvLow.Groups[1].Value);
            if (xvHls.Success && !qualityMap.ContainsKey(1080)) qualityMap[1080] = UnescapeJsonUrl(xvHls.Groups[1].Value);

            // 2. SpankBang / xHamster / KVS (Kernel Video Sharing)
            int[] stdHeights = new int[] { 2160, 1440, 1080, 720, 480, 360, 240 };
            foreach (int h in stdHeights)
            {
                var sbMatch = Regex.Match(html, string.Format(@"['""]{0}p?['""]\s*:\s*\[\s*['""](https?:[^'""]+)['""]", h), RegexOptions.IgnoreCase);
                if (sbMatch.Success && !qualityMap.ContainsKey(h))
                {
                    qualityMap[h] = UnescapeJsonUrl(sbMatch.Groups[1].Value);
                }
                var xhMatch = Regex.Match(html, string.Format(@"""{0}p""\s*:\s*""(https?:[^""]+\.mp4[^""]*)""", h), RegexOptions.IgnoreCase);
                if (xhMatch.Success && !qualityMap.ContainsKey(h))
                {
                    qualityMap[h] = UnescapeJsonUrl(xhMatch.Groups[1].Value);
                }
            }

            var kvsAlt = Regex.Match(html, @"\bvideo_alt_url\s*:\s*['""](https?:[^'""]+)['""]", RegexOptions.IgnoreCase);
            if (kvsAlt.Success && !qualityMap.ContainsKey(1080))
            {
                qualityMap[1080] = UnescapeJsonUrl(kvsAlt.Groups[1].Value);
            }
            var kvsMain = Regex.Match(html, @"\bvideo_url\s*:\s*['""](https?:[^'""]+)['""]", RegexOptions.IgnoreCase);
            if (kvsMain.Success && !qualityMap.ContainsKey(720))
            {
                qualityMap[720] = UnescapeJsonUrl(kvsMain.Groups[1].Value);
            }

            // 3. HTML5 <source src="..." res="1080"> and <video src="..."> and <audio src="...">
            string extractedAudioUrl = null;
            foreach (Match sm in Regex.Matches(html, @"<source\b[^>]*>", RegexOptions.IgnoreCase))
            {
                string tag = sm.Value;
                var srcM = Regex.Match(tag, @"\bsrc\s*=\s*['""]([^'""]+)['""]", RegexOptions.IgnoreCase);
                if (!srcM.Success) continue;
                string resolved = ResolveRelativeWebUrl(pageUrl, UnescapeJsonUrl(srcM.Groups[1].Value));
                if (string.IsNullOrEmpty(resolved)) continue;
                if (IsAudioCentricUrl(resolved) || tag.IndexOf("audio/", StringComparison.OrdinalIgnoreCase) >= 0)
                {
                    if (extractedAudioUrl == null) extractedAudioUrl = resolved;
                    continue;
                }
                var resM = Regex.Match(tag, @"\b(?:res|data-res|label|title|size)\s*=\s*['""]?(\d{3,4})p?['""]?", RegexOptions.IgnoreCase);
                int h = 720;
                if (resM.Success) int.TryParse(resM.Groups[1].Value, out h);
                if (!qualityMap.ContainsKey(h)) qualityMap[h] = resolved;
            }

            var audioTagMatch = Regex.Match(html, @"<audio\b[^>]*\bsrc\s*=\s*['""]([^'""]+)['""]", RegexOptions.IgnoreCase);
            if (audioTagMatch.Success && extractedAudioUrl == null)
            {
                extractedAudioUrl = ResolveRelativeWebUrl(pageUrl, UnescapeJsonUrl(audioTagMatch.Groups[1].Value));
            }
            if (extractedAudioUrl == null)
            {
                string decodedHtml = WebUtility.HtmlDecode(html);
                string[] audioPatterns = new string[]
                {
                    @"property=['""]og:audio:secure_url['""]\s+content=['""](https?:[^'""]+)['""]",
                    @"property=['""]og:audio['""]\s+content=['""](https?:[^'""]+)['""]",
                    @"""mp3-128""\s*:\s*""(https?:[^""]+)""",
                    @"""audioUrl""\s*:\s*""(https?:[^""]+)""",
                    @"""audio_url""\s*:\s*""(https?:[^""]+)""",
                    @"""mp3Url""\s*:\s*""(https?:[^""]+)"""
                };
                foreach (string ap in audioPatterns)
                {
                    var am = Regex.Match(decodedHtml, ap, RegexOptions.IgnoreCase);
                    if (am.Success)
                    {
                        extractedAudioUrl = UnescapeJsonUrl(am.Groups[1].Value);
                        break;
                    }
                }
            }
            if (extractedAudioUrl == null)
            {
                var rawAudioM = Regex.Match(html, @"https?:(?:\\?/\\?/)[^\s""'<>\\]+\.(?:mp3|m4a|flac|wav|opus|ogg|aac)(?:\?[^\s""'<>\\]*)?", RegexOptions.IgnoreCase);
                if (rawAudioM.Success) extractedAudioUrl = UnescapeJsonUrl(rawAudioM.Value);
            }

            foreach (Match vm in Regex.Matches(html, @"<video\b[^>]*\bsrc\s*=\s*['""]([^'""]+)['""]", RegexOptions.IgnoreCase))
            {
                string resolved = ResolveRelativeWebUrl(pageUrl, UnescapeJsonUrl(vm.Groups[1].Value));
                if (!string.IsNullOrEmpty(resolved) && !qualityMap.ContainsKey(1080))
                {
                    qualityMap[1080] = resolved;
                }
            }

            // 4. Common JSON / OpenGraph / Schema.org VideoObject patterns
            string[] patterns = new string[]
            {
                @"""playAddr""\s*:\s*""([^""]+)""",
                @"""downloadAddr""\s*:\s*""([^""]+)""",
                @"__fileurl\s*=\s*['""](https?:[^'""]+)['""]",
                @"""videoUrl""\s*:\s*""(https?:[^""]+\.(?:mp4|m3u8)[^""]*)""",
                @"""video_url""\s*:\s*""(https?:[^""]+)""",
                @"""contentUrl""\s*:\s*""(https?:[^""]+)""",
                @"property=['""]og:video:secure_url['""]\s+content=['""](https?:[^'""]+)['""]",
                @"property=['""]og:video['""]\s+content=['""](https?:[^'""]+)['""]"
            };
            foreach (string pat in patterns)
            {
                var pm = Regex.Match(html, pat, RegexOptions.IgnoreCase);
                if (pm.Success)
                {
                    string u = UnescapeJsonUrl(pm.Groups[1].Value);
                    if (u.StartsWith("http", StringComparison.OrdinalIgnoreCase) && !qualityMap.ContainsKey(1080))
                    {
                        qualityMap[1080] = u;
                        break;
                    }
                }
            }

            // 5. Raw .mp4 / .m3u8 URLs in scripts
            if (qualityMap.Count == 0)
            {
                foreach (Match rm in Regex.Matches(html, @"https?:(?:\\?/\\?/)[^\s""'<>\\]+\.(?:mp4|m3u8)(?:\?[^\s""'<>\\]*)?", RegexOptions.IgnoreCase))
                {
                    string u = UnescapeJsonUrl(rm.Value);
                    string low = u.ToLowerInvariant();
                    if (low.Contains("preview") || low.Contains("thumb") || low.Contains("sprite") || low.Contains("banner")) continue;
                    qualityMap[720] = u;
                    break;
                }
            }

            if (qualityMap.Count == 0 && string.IsNullOrEmpty(extractedAudioUrl)) return null;

            var sortedHeights = qualityMap.Keys.OrderByDescending(k => k).ToList();
            string bestStreamUrl = sortedHeights.Count > 0 ? qualityMap[sortedHeights[0]] : extractedAudioUrl;
            bool audioOnlyPage = sortedHeights.Count == 0 && !string.IsNullOrEmpty(extractedAudioUrl);

            string title = ExtractHtmlMeta(html, "og:title");
            if (string.IsNullOrWhiteSpace(title)) title = ExtractHtmlMeta(html, "twitter:title");
            if (string.IsNullOrWhiteSpace(title))
            {
                var vt = Regex.Match(html, @"setVideoTitle\(\s*['""]([^'""]+)['""]\s*\)");
                if (vt.Success) title = vt.Groups[1].Value;
            }
            if (string.IsNullOrWhiteSpace(title))
            {
                var tt = Regex.Match(html, @"<title[^>]*>([^<]+)</title>", RegexOptions.IgnoreCase);
                if (tt.Success) title = tt.Groups[1].Value;
            }
            if (string.IsNullOrWhiteSpace(title)) title = HostDisplayName(pageUrl) + " Video";
            title = WebUtility.HtmlDecode(Regex.Replace(title, @"\s+", " ").Trim());

            string thumb = ExtractHtmlMeta(html, "og:image");
            if (string.IsNullOrWhiteSpace(thumb))
            {
                var thm = Regex.Match(html, @"setThumbUrl169\(\s*['""]([^'""]+)['""]\s*\)");
                if (thm.Success) thumb = thm.Groups[1].Value;
            }

            return new MediaMetadata
            {
                Url = bestStreamUrl,
                RawInputUrl = rawUrl ?? pageUrl,
                Platform = platform,
                Title = title,
                Uploader = HostDisplayName(pageUrl),
                Thumbnail = !string.IsNullOrWhiteSpace(thumb) ? UnescapeJsonUrl(thumb) : null,
                DurationSec = 0,
                IsPlaylist = false,
                IsAudioCentric = audioOnlyPage || IsAudioCentricUrl(pageUrl),
                VideoHeights = sortedHeights
            };
        }

        private static string ExtractHtmlMeta(string html, string prop)
        {
            var m1 = Regex.Match(html, string.Format(@"<meta[^>]+(?:property|name)=['""]{0}['""][^>]+content=['""]([^'""]+)['""]", Regex.Escape(prop)), RegexOptions.IgnoreCase);
            if (m1.Success) return m1.Groups[1].Value;
            var m2 = Regex.Match(html, string.Format(@"<meta[^>]+content=['""]([^'""]+)['""][^>]+(?:property|name)=['""]{0}['""]", Regex.Escape(prop)), RegexOptions.IgnoreCase);
            return m2.Success ? m2.Groups[1].Value : null;
        }

        private static string UnescapeJsonUrl(string raw)
        {
            if (string.IsNullOrEmpty(raw)) return "";
            return raw.Replace(@"\/", "/")
                .Replace(@"\\/", "/")
                .Replace(@"\u0026", "&")
                .Replace(@"\u003d", "=")
                .Replace(@"\u003D", "=")
                .Replace("&amp;", "&")
                .Trim();
        }

        private static string ResolveRelativeWebUrl(string baseUrl, string candidate)
        {
            if (string.IsNullOrWhiteSpace(candidate)) return null;
            string c = candidate.Trim();
            if (c.StartsWith("blob:") || c.StartsWith("data:") || c.StartsWith("javascript:")) return null;
            if (c.StartsWith("http://", StringComparison.OrdinalIgnoreCase) || c.StartsWith("https://", StringComparison.OrdinalIgnoreCase)) return c;
            try
            {
                var baseUri = new Uri(baseUrl);
                return new Uri(baseUri, c).ToString();
            }
            catch
            {
                return null;
            }
        }

        private static string HostDisplayName(string url)
        {
            try
            {
                string host = new Uri(url).Host.ToLowerInvariant();
                if (host.StartsWith("www.")) host = host.Substring(4);
                if (host.StartsWith("m.")) host = host.Substring(2);
                string first = host.Split('.')[0];
                return first.Length > 0 ? char.ToUpperInvariant(first[0]) + first.Substring(1) : host;
            }
            catch
            {
                return "Web Video";
            }
        }

        private void UpdateJobUi(DownloadJob job, string status, float progress, string speed, string eta)
        {
            if (IsDisposed) return;
            BeginInvoke(new Action(() =>
            {
                job.Status = status;
                job.Progress = progress;
                job.Speed = speed;
                job.Eta = eta;

                foreach (ListViewItem item in lvQueue.Items)
                {
                    if (item.Tag == job)
                    {
                        item.SubItems[2].Text = status;
                        item.SubItems[3].Text = string.Format("{0:0.0}%", progress);
                        item.SubItems[4].Text = speed;
                        item.SubItems[5].Text = eta;
                        break;
                    }
                }

                pbActive.Value = Math.Max(0, Math.Min(100, (int)progress));
                lblActiveProgress.Text = string.Format("{0} — {1} ({2:0.0}%)", job.Title, status, progress);
            }));
        }

        private static void KillProcessTree(Process proc)
        {
            if (proc == null) return;
            try
            {
                if (!proc.HasExited)
                {
                    try
                    {
                        var psi = new ProcessStartInfo("taskkill", "/F /T /PID " + proc.Id)
                        {
                            CreateNoWindow = true,
                            UseShellExecute = false
                        };
                        using (var tk = Process.Start(psi))
                        {
                            if (tk != null) tk.WaitForExit(3000);
                        }
                    }
                    catch { }
                    if (!proc.HasExited) proc.Kill();
                }
            }
            catch { }
        }

        private void TogglePauseResumeSelectedQueueItem()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job == null || job.Finished) return;

            if (!job.Paused && !job.Canceled && !job.Failed && job.IsActive)
            {
                // Pause active/queued job while preserving .part file on disk
                job.Paused = true;
                KillProcessTree(job.RunningProcess);
                UpdateJobUi(job, I18n.T("status_paused"), job.Progress, "-", "-");
                SaveJobs();
            }
            else if (job.Paused || job.Canceled || job.Failed || !job.IsActive)
            {
                // Resume or retry from exact byte offset using yt-dlp --continue
                job.Paused = false;
                job.Canceled = false;
                job.Failed = false;
                UpdateJobUi(job, I18n.T("status_starting"), Math.Max(1f, job.Progress), "-", "-");
                SaveJobs();
                var t = RunDownloadJobAsync(job);
            }
        }

        private void CancelSelectedQueueItem()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job == null) return;
            job.Canceled = true;
            job.Paused = false;
            KillProcessTree(job.RunningProcess);
            UpdateJobUi(job, I18n.T("status_canceled"), 0f, "-", "-");
            SaveJobs();
        }

        internal DownloadJob SaveFailedLink(string url, string rawUrl, SocialPlatform platform, string title, string errorMsg)
        {
            string cleanUrl = NormalizeMediaUrl(!string.IsNullOrWhiteSpace(url) ? url : rawUrl);
            if (string.IsNullOrWhiteSpace(cleanUrl)) return null;

            string defaultTitle = !string.IsNullOrWhiteSpace(title)
                ? title
                : (platform.ToString() + " Video (" + (cleanUrl.Length > 48 ? cleanUrl.Substring(0, 48) + "..." : cleanUrl) + ")");

            string cookieBrowser = (chkUseBrowserCookies != null && chkUseBrowserCookies.Checked && cmbCookieBrowser != null && cmbCookieBrowser.SelectedItem != null)
                ? cmbCookieBrowser.SelectedItem.ToString()
                : null;

            var existing = jobs.FirstOrDefault(j => string.Equals(j.Url, cleanUrl, StringComparison.OrdinalIgnoreCase) && !j.Finished);
            DownloadJob targetJob;
            if (existing != null)
            {
                existing.Failed = true;
                existing.Status = I18n.T("status_failed") + (errorMsg ?? "Link saved for retry");
                targetJob = existing;
                if (lvQueue != null)
                {
                    foreach (ListViewItem item in lvQueue.Items)
                    {
                        if (item.Tag == existing)
                        {
                            item.SubItems[2].Text = existing.Status;
                            break;
                        }
                    }
                }
            }
            else
            {
                targetJob = new DownloadJob
                {
                    Url = cleanUrl,
                    RawInputUrl = !string.IsNullOrWhiteSpace(rawUrl) ? rawUrl : cleanUrl,
                    Platform = platform,
                    Title = defaultTitle,
                    Uploader = "Saved Link",
                    IsAudio = rbAudio != null && rbAudio.Checked,
                    AudioFormat = (cmbAudioFormat != null && cmbAudioFormat.SelectedIndex == 1) ? "m4a" : "mp3",
                    VideoHeight = null,
                    OutputDir = DownloadFolder,
                    BrowserCookies = cookieBrowser,
                    Status = I18n.T("status_failed") + (errorMsg ?? "Link saved for retry"),
                    Failed = true
                };
                jobs.Insert(0, targetJob);
                if (lvQueue != null)
                {
                    var lvi = new ListViewItem(targetJob.Title) { Tag = targetJob };
                    lvi.SubItems.Add(targetJob.QualityLabel);
                    lvi.SubItems.Add(targetJob.Status);
                    lvi.SubItems.Add("0%");
                    lvi.SubItems.Add("-");
                    lvi.SubItems.Add("-");
                    lvQueue.Items.Insert(0, lvi);
                }
            }

            history.RemoveAll(h => h.Status == "FAILED" && string.Equals(h.Url, cleanUrl, StringComparison.OrdinalIgnoreCase));
            history.Insert(0, new HistoryRecord
            {
                Id = targetJob.Id,
                DateStr = DateTime.Now.ToString("yyyy-MM-dd HH:mm"),
                Title = targetJob.Title,
                Uploader = "Saved Link",
                Quality = "Failed • " + targetJob.QualityLabel,
                FilePath = "Failed: " + (errorMsg ?? "Link saved for retry"),
                Url = cleanUrl,
                SizeBytes = 0,
                Status = "FAILED"
            });

            SaveJobs();
            SaveHistory();
            RefreshHistoryListView();
            return targetJob;
        }

        internal DownloadJob EnqueueDirectFromUrl(string rawUrl, string customTitle = null)
        {
            string cleanUrl = NormalizeMediaUrl(rawUrl);
            if (string.IsNullOrWhiteSpace(cleanUrl)) return null;
            SocialPlatform platform = DetectPlatform(cleanUrl);
            string cookieBrowser = (chkUseBrowserCookies != null && chkUseBrowserCookies.Checked && cmbCookieBrowser != null && cmbCookieBrowser.SelectedItem != null)
                ? cmbCookieBrowser.SelectedItem.ToString()
                : null;

            var existing = jobs.FirstOrDefault(j => string.Equals(j.Url, cleanUrl, StringComparison.OrdinalIgnoreCase) && !j.Finished);
            if (existing != null)
            {
                if (!string.IsNullOrWhiteSpace(customTitle))
                {
                    existing.CustomTitle = customTitle.Trim();
                    existing.Title = customTitle.Trim();
                }
                existing.Paused = false;
                existing.Canceled = false;
                existing.Failed = false;
                UpdateJobUi(existing, I18n.T("status_starting"), Math.Max(1f, existing.Progress), "-", "-");
                SaveJobs();
                var tr = RunDownloadJobAsync(existing);
                if (tabBottom != null) tabBottom.SelectedIndex = 0;
                return existing;
            }

            string title = !string.IsNullOrWhiteSpace(customTitle)
                ? customTitle.Trim()
                : (platform.ToString() + " Video (" + (cleanUrl.Length > 45 ? cleanUrl.Substring(0, 45) + "..." : cleanUrl) + ")");

            var job = new DownloadJob
            {
                Url = cleanUrl,
                RawInputUrl = rawUrl,
                Platform = platform,
                Title = title,
                CustomTitle = !string.IsNullOrWhiteSpace(customTitle) ? customTitle.Trim() : null,
                Uploader = platform.ToString(),
                IsAudio = rbAudio != null && rbAudio.Checked,
                AudioFormat = (cmbAudioFormat != null && cmbAudioFormat.SelectedIndex == 1) ? "m4a" : "mp3",
                VideoHeight = null,
                OutputDir = DownloadFolder,
                BrowserCookies = cookieBrowser
            };

            jobs.Insert(0, job);
            if (lvQueue != null)
            {
                var lvi = new ListViewItem(job.Title) { Tag = job };
                lvi.SubItems.Add(job.QualityLabel);
                lvi.SubItems.Add(job.Status);
                lvi.SubItems.Add("0%");
                lvi.SubItems.Add("-");
                lvi.SubItems.Add("-");
                lvQueue.Items.Insert(0, lvi);
            }
            SaveJobs();
            if (tabBottom != null) tabBottom.SelectedIndex = 0;
            var t = RunDownloadJobAsync(job);
            return job;
        }

        private void RetryOrDownloadSelectedQueueItem()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job == null) return;

            job.Finished = false;
            job.Paused = false;
            job.Canceled = false;
            job.Failed = false;
            UpdateJobUi(job, I18n.T("status_starting"), Math.Max(1f, job.Progress), "-", "-");
            SaveJobs();
            var t = RunDownloadJobAsync(job);
        }

        private void LoadSelectedQueueLinkToHome()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job == null || string.IsNullOrWhiteSpace(job.Url)) return;
            txtUrl.Text = job.Url;
            var t = FetchMediaInfoAsync(false);
        }

        internal static string RenameFileOnDisk(string oldPath, string newTitle)
        {
            if (string.IsNullOrWhiteSpace(oldPath) || !File.Exists(oldPath)) return oldPath;
            string cleanBase = SanitizeFileName(newTitle);
            if (string.IsNullOrWhiteSpace(cleanBase)) return oldPath;

            string dir = Path.GetDirectoryName(oldPath) ?? "";
            string ext = Path.GetExtension(oldPath);
            string target = Path.Combine(dir, cleanBase + ext);
            if (string.Equals(oldPath, target, StringComparison.OrdinalIgnoreCase)) return oldPath;

            int n = 1;
            while (File.Exists(target))
            {
                target = Path.Combine(dir, string.Format("{0} ({1}){2}", cleanBase, n, ext));
                n++;
            }

            try
            {
                File.Move(oldPath, target);
                return target;
            }
            catch
            {
                return oldPath;
            }
        }

        private string PromptRenameDialog(string currentName)
        {
            using (Form dlg = new Form())
            {
                dlg.Text = I18n.T("dlg_rename_title");
                dlg.Size = new Size(480, 175);
                dlg.FormBorderStyle = FormBorderStyle.FixedDialog;
                dlg.StartPosition = FormStartPosition.CenterParent;
                dlg.MaximizeBox = false;
                dlg.MinimizeBox = false;
                dlg.BackColor = CardDark;
                dlg.ForeColor = TextPrimary;

                Label lbl = new Label
                {
                    Text = I18n.T("dlg_rename_prompt"),
                    Location = new Point(16, 16),
                    AutoSize = true,
                    ForeColor = TextPrimary,
                    Font = new Font("Segoe UI Semibold", 9.5f)
                };
                dlg.Controls.Add(lbl);

                TextBox txt = new TextBox
                {
                    Text = currentName ?? "",
                    Location = new Point(16, 44),
                    Width = 430,
                    BackColor = CardInput,
                    ForeColor = TextPrimary,
                    BorderStyle = BorderStyle.FixedSingle,
                    Font = new Font("Segoe UI", 10f)
                };
                dlg.Controls.Add(txt);

                Button btnOk = CreateStyledButton("OK", SuccessGreen, 95, 32);
                btnOk.Location = new Point(245, 88);
                btnOk.DialogResult = DialogResult.OK;
                dlg.Controls.Add(btnOk);

                Button btnCancel = CreateStyledButton("Cancel", Color.FromArgb(65, 68, 82), 95, 32);
                btnCancel.Location = new Point(350, 88);
                btnCancel.DialogResult = DialogResult.Cancel;
                dlg.Controls.Add(btnCancel);

                dlg.AcceptButton = btnOk;
                dlg.CancelButton = btnCancel;

                if (dlg.ShowDialog(this) == DialogResult.OK && !string.IsNullOrWhiteSpace(txt.Text))
                {
                    return txt.Text.Trim();
                }
                return null;
            }
        }

        private void RenameSelectedQueueItem()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var lvi = lvQueue.SelectedItems[0];
            var job = lvi.Tag as DownloadJob;
            if (job == null) return;

            string newName = PromptRenameDialog(job.Title);
            if (string.IsNullOrWhiteSpace(newName)) return;

            job.Title = newName;
            job.CustomTitle = newName;
            if (!string.IsNullOrEmpty(job.OutputFilePath) && File.Exists(job.OutputFilePath))
            {
                job.OutputFilePath = RenameFileOnDisk(job.OutputFilePath, newName);
            }
            lvi.Text = newName;

            foreach (var h in history)
            {
                if (h.Id == job.Id)
                {
                    h.Title = newName;
                    if (!string.IsNullOrEmpty(job.OutputFilePath)) h.FilePath = job.OutputFilePath;
                }
            }
            SaveJobs();
            SaveHistory();
            RefreshHistoryListView();
        }

        private void CopySelectedQueueName()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job != null) CopyTextSafe(job.Title, I18n.T("msg_copied_name"));
        }

        private void CopySelectedQueueLink()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job != null) CopyTextSafe(job.Url, I18n.T("msg_copied_link"));
        }

        private void RedownloadSelectedHistoryItem()
        {
            if (lvHistory.SelectedItems.Count == 0) return;
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec == null || string.IsNullOrWhiteSpace(rec.Url)) return;

            EnqueueDirectFromUrl(rec.Url, rec.Title);
        }

        private void RenameSelectedHistoryItem()
        {
            if (lvHistory.SelectedItems.Count == 0) return;
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec == null) return;

            string newName = PromptRenameDialog(rec.Title);
            if (string.IsNullOrWhiteSpace(newName)) return;

            rec.Title = newName;
            if (!string.IsNullOrEmpty(rec.FilePath) && File.Exists(rec.FilePath))
            {
                rec.FilePath = RenameFileOnDisk(rec.FilePath, newName);
            }

            foreach (var job in jobs)
            {
                if (job.Id == rec.Id)
                {
                    job.Title = newName;
                    job.CustomTitle = newName;
                    if (!string.IsNullOrEmpty(rec.FilePath) && File.Exists(rec.FilePath))
                        job.OutputFilePath = rec.FilePath;
                }
            }
            foreach (ListViewItem item in lvQueue.Items)
            {
                var j = item.Tag as DownloadJob;
                if (j != null && j.Id == rec.Id) item.Text = newName;
            }

            SaveHistory();
            SaveJobs();
            RefreshHistoryListView();
        }

        private void CopySelectedHistoryName()
        {
            if (lvHistory.SelectedItems.Count == 0) return;
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec != null) CopyTextSafe(rec.Title, I18n.T("msg_copied_name"));
        }

        private void CopySelectedHistoryLink()
        {
            if (lvHistory.SelectedItems.Count == 0) return;
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec != null) CopyTextSafe(rec.Url, I18n.T("msg_copied_link"));
        }

        private void CopyTextSafe(string text, string statusToast)
        {
            if (string.IsNullOrEmpty(text)) return;
            try
            {
                Clipboard.SetText(text);
                if (lblActiveProgress != null && !string.IsNullOrEmpty(statusToast))
                {
                    lblActiveProgress.Text = statusToast + ": " + (text.Length > 55 ? text.Substring(0, 55) + "..." : text);
                }
            }
            catch { }
        }

        private void LoadSavedJobs()
        {
            try
            {
                if (File.Exists(SavedJobsFilePath))
                {
                    string json = File.ReadAllText(SavedJobsFilePath, Encoding.UTF8);
                    var ser = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                    var loaded = ser.Deserialize<List<DownloadJob>>(json);
                    if (loaded != null)
                    {
                        jobs.Clear();
                        lvQueue.Items.Clear();
                        foreach (var job in loaded)
                        {
                            job.IsActive = false;
                            if (!job.Finished && !job.Failed && !job.Canceled)
                            {
                                job.Paused = true;
                                job.Status = I18n.T("status_paused");
                            }
                            jobs.Add(job);
                            var lvi = new ListViewItem(job.Title ?? "Saved Media") { Tag = job };
                            lvi.SubItems.Add(job.QualityLabel);
                            lvi.SubItems.Add(job.Status ?? I18n.T("status_paused"));
                            lvi.SubItems.Add(string.Format("{0:0.0}%", job.Progress));
                            lvi.SubItems.Add("-");
                            lvi.SubItems.Add("-");
                            lvQueue.Items.Add(lvi);
                        }
                    }
                }
            }
            catch { }
        }

        private void SaveJobs()
        {
            try
            {
                var ser = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                string json = ser.Serialize(jobs.Take(100).ToList());
                File.WriteAllText(SavedJobsFilePath, json, Encoding.UTF8);
            }
            catch { }
        }

        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            try
            {
                if (fetchCts != null) fetchCts.Cancel();
                foreach (var job in jobs)
                {
                    if (!job.Finished && !job.Failed && !job.Canceled)
                    {
                        job.Paused = true;
                    }
                    KillProcessTree(job.RunningProcess);
                }
                SaveJobs();
            }
            catch { }
            base.OnFormClosing(e);
        }

        private void PlaySelectedQueueItem()
        {
            if (lvQueue.SelectedItems.Count == 0) return;
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job != null && !string.IsNullOrEmpty(job.OutputFilePath) && File.Exists(job.OutputFilePath))
            {
                Process.Start(new ProcessStartInfo(job.OutputFilePath) { UseShellExecute = true });
            }
        }

        private void OpenSelectedQueueFolder()
        {
            if (lvQueue.SelectedItems.Count == 0)
            {
                OpenFolder(DownloadFolder);
                return;
            }
            var job = lvQueue.SelectedItems[0].Tag as DownloadJob;
            if (job != null && !string.IsNullOrEmpty(job.OutputFilePath) && File.Exists(job.OutputFilePath))
            {
                Process.Start("explorer.exe", "/select,\"" + job.OutputFilePath + "\"");
            }
            else if (job != null && Directory.Exists(job.OutputDir))
            {
                OpenFolder(job.OutputDir);
            }
            else
            {
                OpenFolder(DownloadFolder);
            }
        }

        private void PlaySelectedHistoryItem()
        {
            if (lvHistory.SelectedItems.Count == 0) return;
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec != null && !string.IsNullOrEmpty(rec.FilePath) && File.Exists(rec.FilePath))
            {
                Process.Start(new ProcessStartInfo(rec.FilePath) { UseShellExecute = true });
            }
        }

        private void OpenSelectedHistoryFolder()
        {
            if (lvHistory.SelectedItems.Count == 0)
            {
                OpenFolder(DownloadFolder);
                return;
            }
            var rec = lvHistory.SelectedItems[0].Tag as HistoryRecord;
            if (rec != null && !string.IsNullOrEmpty(rec.FilePath) && File.Exists(rec.FilePath))
            {
                Process.Start("explorer.exe", "/select,\"" + rec.FilePath + "\"");
            }
            else
            {
                OpenFolder(DownloadFolder);
            }
        }

        private void OpenSponsorLink()
        {
            try
            {
                if (!string.IsNullOrWhiteSpace(adConfig.TargetUrl) && adConfig.TargetUrl.StartsWith("http"))
                {
                    Process.Start(new ProcessStartInfo(adConfig.TargetUrl) { UseShellExecute = true });
                }
            }
            catch { }
        }

        private void LoadAdConfig()
        {
            try
            {
                if (File.Exists(AdConfigFilePath))
                {
                    string json = File.ReadAllText(AdConfigFilePath, Encoding.UTF8);
                    var ser = new JavaScriptSerializer();
                    var loaded = ser.Deserialize<AdConfig>(json);
                    if (loaded != null) adConfig = loaded;
                }
            }
            catch { }
        }

        private void SaveAdConfig()
        {
            try
            {
                var ser = new JavaScriptSerializer();
                string json = ser.Serialize(adConfig);
                File.WriteAllText(AdConfigFilePath, json, Encoding.UTF8);
            }
            catch { }
        }

        private void LoadHistory()
        {
            try
            {
                if (File.Exists(HistoryFilePath))
                {
                    string json = File.ReadAllText(HistoryFilePath, Encoding.UTF8);
                    var ser = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                    var loaded = ser.Deserialize<List<HistoryRecord>>(json);
                    if (loaded != null)
                    {
                        history.Clear();
                        history.AddRange(loaded);
                    }
                }
            }
            catch { }
            RefreshHistoryListView();
        }

        private void SaveHistory()
        {
            try
            {
                var ser = new JavaScriptSerializer { MaxJsonLength = int.MaxValue };
                string json = ser.Serialize(history);
                File.WriteAllText(HistoryFilePath, json, Encoding.UTF8);
            }
            catch { }
        }

        private void RefreshHistoryListView()
        {
            if (lvHistory == null) return;
            string q = ((txtHistorySearch != null ? txtHistorySearch.Text : "") ?? "").Trim().ToLowerInvariant();
            lvHistory.BeginUpdate();
            lvHistory.Items.Clear();
            foreach (var rec in history)
            {
                if (!string.IsNullOrEmpty(q))
                {
                    bool match = (rec.Title ?? "").ToLowerInvariant().Contains(q) ||
                                 (rec.Uploader ?? "").ToLowerInvariant().Contains(q);
                    if (!match) continue;
                }
                var lvi = new ListViewItem(rec.DateStr ?? "") { Tag = rec };
                lvi.SubItems.Add(rec.Title ?? "");
                lvi.SubItems.Add(rec.Uploader ?? "");
                lvi.SubItems.Add(rec.Quality ?? "");
                lvi.SubItems.Add(FormatBytes(rec.SizeBytes));
                lvi.SubItems.Add(rec.FilePath ?? "");
                lvHistory.Items.Add(lvi);
            }
            lvHistory.EndUpdate();
        }

        // Helpers
        private static void OpenFolder(string path)
        {
            try
            {
                Directory.CreateDirectory(path);
                Process.Start("explorer.exe", "\"" + path + "\"");
            }
            catch { }
        }

        private static string RunShortCommand(string exe, string args)
        {
            try
            {
                var psi = new ProcessStartInfo
                {
                    FileName = exe,
                    Arguments = args,
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    CreateNoWindow = true,
                    StandardOutputEncoding = Encoding.UTF8
                };
                using (var p = Process.Start(psi))
                {
                    string output = p.StandardOutput.ReadToEnd();
                    p.WaitForExit(15000);
                    return output;
                }
            }
            catch { return ""; }
        }

        private static string RunCommandCaptureStdout(string exe, string args, CancellationToken token)
        {
            var psi = new ProcessStartInfo
            {
                FileName = exe,
                Arguments = args,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true,
                StandardOutputEncoding = Encoding.UTF8,
                StandardErrorEncoding = Encoding.UTF8
            };
            using (var p = new Process { StartInfo = psi })
            {
                StringBuilder sbOut = new StringBuilder();
                StringBuilder sbErr = new StringBuilder();
                p.OutputDataReceived += (s, e) => { if (e.Data != null) sbOut.AppendLine(e.Data); };
                p.ErrorDataReceived += (s, e) => { if (e.Data != null) sbErr.AppendLine(e.Data); };
                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();

                while (!p.WaitForExit(200))
                {
                    if (token.IsCancellationRequested)
                    {
                        KillProcessTree(p);
                        token.ThrowIfCancellationRequested();
                    }
                }
                if (p.ExitCode != 0 && sbOut.Length == 0)
                {
                    throw new Exception(sbErr.ToString().Trim());
                }
                return sbOut.ToString();
            }
        }

        private static string GetStr(Dictionary<string, object> d, string key)
        {
            if (d != null && d.ContainsKey(key) && d[key] != null) return d[key].ToString();
            return null;
        }

        private static long GetLong(Dictionary<string, object> d, string key)
        {
            if (d == null || !d.ContainsKey(key) || d[key] == null) return 0;
            double v;
            if (double.TryParse(d[key].ToString(), System.Globalization.NumberStyles.Any, System.Globalization.CultureInfo.InvariantCulture, out v))
                return (long)v;
            return 0;
        }

        private static string ExtractLastThumbnail(Dictionary<string, object> d)
        {
            if (d == null || !d.ContainsKey("thumbnails")) return null;
            var arr = d["thumbnails"] as System.Collections.ArrayList;
            if (arr == null || arr.Count == 0) return null;
            var last = arr[arr.Count - 1] as Dictionary<string, object>;
            return GetStr(last, "url");
        }

        private static string FormatDuration(long sec)
        {
            if (sec <= 0) return "--:--";
            var ts = TimeSpan.FromSeconds(sec);
            return ts.TotalHours >= 1
                ? string.Format("{0}:{1:D2}:{2:D2}", (int)ts.TotalHours, ts.Minutes, ts.Seconds)
                : string.Format("{0:D2}:{1:D2}", ts.Minutes, ts.Seconds);
        }

        private static string FormatBytes(long bytes)
        {
            if (bytes <= 0) return "-";
            double mb = bytes / (1024.0 * 1024.0);
            if (mb >= 1024.0) return string.Format("{0:0.00} GB", mb / 1024.0);
            return string.Format("{0:0.1} MB", mb);
        }

        private static string SanitizeFileName(string name)
        {
            foreach (char c in Path.GetInvalidFileNameChars())
                name = name.Replace(c, '_');
            return name.Trim();
        }

        private static string FirstLine(string s)
        {
            if (string.IsNullOrWhiteSpace(s)) return "Unknown error";
            var lines = s.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            string errLine = lines.LastOrDefault(l => l.Contains("ERROR:")) ?? lines.LastOrDefault() ?? s;
            return errLine.Replace("ERROR:", "").Trim();
        }
    }
}
