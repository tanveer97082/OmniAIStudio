import React, { useState } from 'react';
import {
  AlertTriangle,
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Bug,
  Calculator,
  Check,
  ChevronDown,
  ChevronUp,
  Copy,
  Download,
  Eye,
  EyeOff,
  FileText,
  Film,
  FolderKanban,
  Globe,
  Home,
  Image,
  Languages,
  Lock,
  Menu,
  MessageSquare,
  Mic,
  Paperclip,
  Play,
  Radar,
  RefreshCw,
  Rss,
  ScanText,
  Search,
  Send,
  Settings,
  Share2,
  Shield,
  ShieldAlert,
  Sparkles,
  Square,
  Terminal,
  Trash2,
  UploadCloud,
  UserCheck,
  Video,
  Volume2,
  X,
  Zap,
} from 'lucide-react';
import {
  BUILTIN_VIDEO_TEMPLATES,
  deleteCustomTemplate,
  loadStoredCustomTemplates,
  persistCustomTemplates,
  upsertCustomTemplate,
  VISUAL_STYLE_OPTIONS,
  VOICEOVER_OPTIONS,
  type TemplateDraft,
  type VideoTemplateRecord,
  type VisualStyleOption,
  type VoiceoverOption,
} from '../utils/templateAdmin';
import { TemplateAdminPanel } from './TemplateAdminPanel';

type AndroidTab = 'home' | 'chat' | 'director' | 'projects' | 'merger' | 'gallery' | 'vault';

type IconVariantId = 'DEFAULT' | 'CYBER' | 'CALCULATOR' | 'NOTES';
type AccentId = 'CYBER_PURPLE' | 'MATRIX_GREEN' | 'ELECTRIC_CYAN' | 'CRIMSON_RED';

interface IconVariantMeta {
  id: IconVariantId;
  title: string;
  launcherLabel: string;
  subtitle: string;
  accentHex: string;
  stealth: boolean;
}

interface AccentMeta {
  id: AccentId;
  label: string;
  hex: string;
}

const APP_ICON_VARIANTS: IconVariantMeta[] = [
  { id: 'DEFAULT', title: 'Default Core', launcherLabel: 'OmniAI Studio', subtitle: 'Neural Prism', accentHex: '#00E676', stealth: false },
  { id: 'CYBER', title: 'Cyber Neon', launcherLabel: 'OmniAI', subtitle: 'Minimal "O"', accentHex: '#00E5FF', stealth: false },
  { id: 'CALCULATOR', title: 'Stealth Calc', launcherLabel: 'Calculator', subtitle: 'Disguise Grid', accentHex: '#F59E0B', stealth: true },
  { id: 'NOTES', title: 'Stealth Notes', launcherLabel: 'Quick Notes', subtitle: 'Disguise Pad', accentHex: '#FBBF24', stealth: true },
];

const THEME_ACCENTS: AccentMeta[] = [
  { id: 'CYBER_PURPLE', label: 'Purple', hex: '#BB86FC' },
  { id: 'MATRIX_GREEN', label: 'Matrix', hex: '#00E676' },
  { id: 'ELECTRIC_CYAN', label: 'Cyan', hex: '#00E5FF' },
  { id: 'CRIMSON_RED', label: 'Crimson', hex: '#FF5252' },
];

const QUICK_PROVIDER_PRESETS = [
  { label: 'Termux/Ollama', url: 'http://127.0.0.1:8080/v1', model: 'llama3.1:8b' },
  { label: 'Cloudflare Tunnel', url: 'https://omniai-tunnel.trycloudflare.com/v1', model: 'mistral-nemo' },
  { label: 'HF Router', url: 'https://router.huggingface.co/v1', model: 'Qwen/Qwen2.5-72B-Instruct' },
  { label: 'Groq', url: 'https://api.groq.com/openai/v1', model: 'llama-3.3-70b-versatile' },
  { label: 'OpenRouter', url: 'https://openrouter.ai/api/v1', model: 'deepseek/deepseek-chat' },
  { label: 'Gemini API', url: 'https://generativelanguage.googleapis.com/v1beta/openai', model: 'gemini-2.5-flash' },
];

const SAMPLE_CRASH_LOG = `====================================================
Timestamp : 2026-11-21 09:41:12 UTC
App Build : v1.0.14 (code 14)
Device    : Pixel 8 Pro (API 34)
Thread    : DefaultDispatcher-worker-2
Exception : java.net.SocketTimeoutException: Tunnel timeout
----------------------------------------------------
at com.omniai.studio.data.remote.AiStreamingClient.streamOpenAiCompatible(AiStreamingClient.kt:129)
at com.omniai.studio.data.remote.AiStreamingClient$streamChat$1.invokeSuspend(AiStreamingClient.kt:64)`;

interface SimMessage {
  id: number;
  role: 'user' | 'assistant';
  content: string;
  provider?: string;
}

interface SimScene {
  id: number;
  visualPrompt: string;
  narration: string;
}

interface SimClip {
  id: string;
  name: string;
  duration: string;
  startSec: string;
  endSec: string;
}

interface SimDownload {
  id: number;
  name: string;
  category: 'RENDER' | 'SCENE' | 'MERGED';
  sizeMb: number;
  status: 'COMPLETED' | 'FAILED';
  savedToGallery: boolean;
  thumbSaved?: boolean;
}

const INITIAL_MESSAGES: SimMessage[] = [
  {
    id: 1,
    role: 'user',
    content: 'Generate a Kotlin Media3 Transformer snippet to merge 3 MP4 clips with start/end trimming.',
  },
  {
    id: 2,
    role: 'assistant',
    provider: 'Primary Tunnel · dolphin-mistral',
    content:
      'Use MediaItem.ClippingConfiguration inside EditedMediaItemSequence:\n```kotlin\nval item = MediaItem.Builder().setUri(uri)\n  .setClippingConfiguration(\n    MediaItem.ClippingConfiguration.Builder()\n      .setStartPositionMs(1000L).setEndPositionMs(5000L).build()\n  ).build()\n```',
  },
];

const INITIAL_SCENES: SimScene[] = [
  {
    id: 1,
    visualPrompt: 'Drone dolly over rain-soaked Tokyo alley at midnight, volumetric neon reflections, 35mm anamorphic',
    narration: 'In a city that never sleeps, every frame is forged locally.',
  },
  {
    id: 2,
    visualPrompt: 'Close-up of dual GPU workstation racks humming with warm amber LED telemetry, shallow DOF',
    narration: 'Two parallel T4 GPUs split every shot in half the render time.',
  },
  {
    id: 3,
    visualPrompt: 'Macro shot of smartphone AMOLED screen exporting final H.264 timeline into gallery',
    narration: 'Zero cloud lock-in. Hardware encrypted from prompt to MP4.',
  },
];

const INITIAL_CLIPS: SimClip[] = [
  { id: 'c1', name: 'tokyo_noir_scene_1.mp4', duration: '00:05', startSec: '0.5', endSec: '4.5' },
  { id: 'c2', name: 'tokyo_noir_scene_2.mp4', duration: '00:05', startSec: '', endSec: '' },
  { id: 'c3', name: 'tokyo_noir_scene_3.mp4', duration: '00:05', startSec: '0.0', endSec: '4.0' },
];

const INITIAL_DOWNLOADS: SimDownload[] = [
  { id: 1, name: 'TokyoNoir_job12_final.mp4', category: 'RENDER', sizeMb: 18.4, status: 'COMPLETED', savedToGallery: true },
  { id: 2, name: 'TokyoNoir_job12_scene_1.mp4', category: 'SCENE', sizeMb: 6.1, status: 'COMPLETED', savedToGallery: false },
  { id: 3, name: 'CyberBrief_job11_final.mp4', category: 'RENDER', sizeMb: 14.2, status: 'FAILED', savedToGallery: false },
];

export const AndroidSimulatorView: React.FC<{ onInspectSource: (path: string) => void }> = ({
  onInspectSource,
}) => {
  const [activeTab, setActiveTab] = useState<AndroidTab>('home');
  const [showSettingsHub, setShowSettingsHub] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeNicheLabel, setActiveNicheLabel] = useState('🎬 3D Stories');
  const [pendingGitChanges, setPendingGitChanges] = useState(3);
  const [toastBanner, setToastBanner] = useState<string | null>(null);

  // Upgrade 1 & 2: Global App Settings State
  const [iconVariant, setIconVariant] = useState<IconVariantId>('DEFAULT');
  const [pendingIconConfirm, setPendingIconConfirm] = useState<IconVariantId | null>(null);
  const [accentId, setAccentId] = useState<AccentId>('MATRIX_GREEN');
  const [chatFontSize, setChatFontSize] = useState<number>(13);
  const [temperature, setTemperature] = useState<number>(0.7);
  const [maxTokens, setMaxTokens] = useState<number>(2048);
  const [contextWindow, setContextWindow] = useState<number>(12);
  const [autoReadTts, setAutoReadTts] = useState<boolean>(false);
  const [ttsSpeed, setTtsSpeed] = useState<number>(1.0);
  const [wifiOnlyDownload, setWifiOnlyDownload] = useState<boolean>(false);
  const [kagglePollSec, setKagglePollSec] = useState<15 | 20 | 30 | 60>(20);
  const [autoCleanMerge, setAutoCleanMerge] = useState<boolean>(true);
  const [crashLogText, setCrashLogText] = useState<string>(SAMPLE_CRASH_LOG);
  const [showCrashViewer, setShowCrashViewer] = useState<boolean>(false);

  // Upgrade 3: Offline TTS + Share-to-AI popup state
  const [speakingMsgId, setSpeakingMsgId] = useState<number | null>(null);
  const [incomingShareText, setIncomingShareText] = useState<string | null>(null);

  // Upgrade 4: Character DNA, Social SEO Kit, Kaggle 30-Hr Quota & Watermark/BGM
  const [characterProfiles] = useState([
    {
      id: 'arya',
      name: 'Arya Cyber-Host',
      appearancePrompt: '28-year-old South Asian tech narrator, emerald rim-lit glasses, matte charcoal jacket, consistent facial geometry',
      defaultStyle: 'Cyberpunk Neon' as VisualStyleOption,
    },
    {
      id: 'dev',
      name: 'Dr. Dev Documentary',
      appearancePrompt: '42-year-old documentary filmmaker in dark studio coat, soft tungsten key light, 35mm cinema lens',
      defaultStyle: 'Cinematic Realism' as VisualStyleOption,
    },
  ]);
  const [selectedCharacterId, setSelectedCharacterId] = useState<string>('');
  const [weeklyGpuHours, setWeeklyGpuHours] = useState<number>(11.4);
  const [watermarkText, setWatermarkText] = useState<string>('@OmniAIStudio');
  const [bgmUrl, setBgmUrl] = useState<string>('https://cdn.omniai.local/lofi_ducked_bed.mp3');
  const [expandedKitIds, setExpandedKitIds] = useState<number[]>([1]);

  // Upgrade 5: Offline Doc Brain, Web URL Reader, Side-by-Side Translate, RSS News Agent & Localhost Scanner
  const [attachedDoc, setAttachedDoc] = useState<{ name: string; pages: number; chunks: number } | null>(null);
  const [translatedKeys, setTranslatedKeys] = useState<Record<string, { lang: string; text: string }>>({});
  const [rssFeeds, setRssFeeds] = useState([
    { id: 1, name: 'Hacker News AI', url: 'https://hnrss.org/newest?q=AI' },
  ]);
  const [newRssName, setNewRssName] = useState('');
  const [newRssUrl, setNewRssUrl] = useState('');
  const [newsAgentEnabled, setNewsAgentEnabled] = useState(true);
  const [newsIntervalHours, setNewsIntervalHours] = useState<12 | 24>(24);
  const [pendingDigest, setPendingDigest] = useState<{ title: string; topic: string; summary: string } | null>({
    title: 'Daily AI Brief (4 headlines)',
    topic: 'Local 1.3B Video Diffusion Breakthroughs Running on Dual T4 GPUs',
    summary: '• Wan 2.1 bfloat16 sharding halves dual-GPU latency\n• Media3 Transformer eliminates FFmpegKit ABI crashes',
  });
  const [localhostScanResults, setLocalhostScanResults] = useState<
    Array<{ port: number; url: string; hint: string; online: boolean; model: string; ms: number }>
  >([]);

  // Upgrade 6: Duress / Decoy PIN Mode
  const [lockOverlayOpen, setLockOverlayOpen] = useState(false);
  const [decoySandboxOpen, setDecoySandboxOpen] = useState(false);
  const [pinEntry, setPinEntry] = useState('');
  const [pinError, setPinError] = useState<string | null>(null);

  // Tab 1 Chat State
  const [messages, setMessages] = useState<SimMessage[]>(INITIAL_MESSAGES);
  const [rollingSummary, setRollingSummary] = useState<string>('');
  const [summarizedCount, setSummarizedCount] = useState<number>(0);
  const [chatInput, setChatInput] = useState('');
  const [simulateFailover, setSimulateFailover] = useState(false);
  const [voiceLang, setVoiceLang] = useState<'EN' | 'HI'>('EN');

  // Tab 2 Director State
  const [topic, setTopic] = useState('Cyberpunk Noir Documentary on Edge AI');
  const [aspectRatio, setAspectRatio] = useState<'9:16' | '16:9'>('9:16');
  const [visualStyle, setVisualStyle] = useState<VisualStyleOption>('Cinematic Realism');
  const [voiceover, setVoiceover] = useState<VoiceoverOption>('English Male');
  const [burnSubtitles, setBurnSubtitles] = useState(true);
  const [customTemplates, setCustomTemplates] = useState<VideoTemplateRecord[]>(() =>
    loadStoredCustomTemplates()
  );
  const [scenes, setScenes] = useState<SimScene[]>(INITIAL_SCENES);
  const [batchQueue, setBatchQueue] = useState<Array<{ id: number; title: string; status: string; progress: number }>>([
    { id: 101, title: 'Tokyo Noir Short', status: 'RENDERING · T4 x2 (Poll #4)', progress: 58 },
  ]);

  // Tab 3 Media3 Merger State
  const [clips, setClips] = useState<SimClip[]>(INITIAL_CLIPS);
  const [muteOriginal, setMuteOriginal] = useState(false);
  const [bgTrack, setBgTrack] = useState<string | null>('midnight_synth_loop.m4a');
  const [mergeName, setMergeName] = useState('OmniAI_Master_Cut');
  const [mergeProgress, setMergeProgress] = useState<number>(0);
  const [mergeStatus, setMergeStatus] = useState<string | null>(null);

  // Tab 4 Gallery State
  const [downloads, setDownloads] = useState<SimDownload[]>(INITIAL_DOWNLOADS);
  const [cacheMb, setCacheMb] = useState(42.8);
  const [previewVideo, setPreviewVideo] = useState<string | null>(null);

  // Tab 5 Vault & Security State
  const [primaryUrl, setPrimaryUrl] = useState('http://127.0.0.1:8080/v1');
  const [primaryModel, setPrimaryModel] = useState('llama3.1:8b');
  const [flagSecure, setFlagSecure] = useState(true);
  const [appLock, setAppLock] = useState(false);
  const [autoLock, setAutoLock] = useState<'Immediate' | '1 Min' | '5 Min' | 'Never'>('1 Min');
  const [keysRevealed, setKeysRevealed] = useState(false);
  const [pingTested, setPingTested] = useState(false);
  const [otaChecked, setOtaChecked] = useState(false);
  const [panicStage, setPanicStage] = useState<0 | 1 | 2>(0);
  const [wipedNotice, setWipedNotice] = useState<string | null>(null);

  // v5.2/v5.3/v5.5 In-App Setup Guides, YouTube API v3, Meta Reels, 2 GB Telegram & Supabase Cloud RBAC State
  const [activeGuideKey, setActiveGuideKey] = useState<
    null | 'telegram' | 'colab' | 'kaggle' | 'github' | 'hf' | 'gemini' | 'groq' | 'youtube' | 'meta' | 'supabase'
  >(null);
  const [simCloudRole, setSimCloudRole] = useState<'OWNER' | 'EDITOR' | 'VIEWER' | 'PENDING' | 'OFFLINE'>('OWNER');
  const [directTelegramUpload, setDirectTelegramUpload] = useState(true);
  const [skipPhoneDownload, setSkipPhoneDownload] = useState(true);
  const [simTgChatId, setSimTgChatId] = useState('');
  const [directYoutubeUpload, setDirectYoutubeUpload] = useState(true);
  const [ytPrivacy, setYtPrivacy] = useState<'private' | 'unlisted' | 'public'>('private');
  const [ytDailyQuotaUsed, setYtDailyQuotaUsed] = useState(1650);
  const [adobeReturnBannerSource, setAdobeReturnBannerSource] = useState<string | null>(null);

  const SIM_PLATFORM_GUIDES = {
    telegram: {
      title: 'Telegram Bot & 2 GB Direct Kaggle Upload',
      portalLabel: 'Open my.telegram.org',
      portalUrl: 'https://my.telegram.org',
      faq: 'Standard HTTP Bot API caps uploads at 50 MB. Adding TG_API_ID + TG_API_HASH unlocks headless Telethon MTProto up to 2 GB directly from Kaggle GPU!',
      steps: [
        'Message @BotFather -> /newbot -> copy TG_BOT_TOKEN.',
        'Tap Auto-Detect Chat ID (or send /start to your bot) to populate TG_CHAT_ID.',
        'Sign in at my.telegram.org -> API development tools -> copy TG_API_ID & TG_API_HASH.',
        'Add all 4 labels in Kaggle Notebook -> Add-ons -> Secrets and check Attach.',
      ],
      commandTitle: 'Verify TG_BOT_TOKEN & Chat ID',
      command: 'curl -s "https://api.telegram.org/bot$TG_BOT_TOKEN/getUpdates" | head -c 400',
    },
    colab: {
      title: 'Uncensored AI (Colab / Termux Tunnel)',
      portalLabel: 'Open Google Colab',
      portalUrl: 'https://colab.research.google.com',
      faq: 'Run the 1-cell Colab script on a free T4 GPU or start llama-server locally in Termux.',
      steps: [
        'Open Google Colab -> Runtime -> T4 GPU -> paste the 1-cell script below.',
        'Copy the printed https://xxxx.trycloudflare.com/v1 URL into Vault.',
        'For local Termux: enable allow-external-apps=true and launch llama-server on :8080.',
      ],
      commandTitle: 'Termux External Apps + Local GGUF Server',
      command: 'echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings',
    },
    kaggle: {
      title: 'Kaggle T4 x2 GPU Worker',
      portalLabel: 'Open Kaggle Settings',
      portalUrl: 'https://www.kaggle.com/settings',
      faq: 'Attach TG_BOT_TOKEN, TG_CHAT_ID, TG_API_ID & TG_API_HASH in Notebook Add-ons -> Secrets for direct 2 GB Telegram uploads.',
      steps: [
        'Open kaggle.com/settings -> API -> Create New Token (kaggle.json).',
        'Enable Accelerator: GPU T4 x2 and Internet: On in your worker notebook.',
        'Paste Username + API Key into Vault to enable background Dual-T4 renders.',
      ],
      commandTitle: 'Push Kaggle Worker Kernel',
      command: 'kaggle kernels push -p kaggle_worker/',
    },
    github: {
      title: 'GitHub CI/CD & Auto-Push',
      portalLabel: 'Open GitHub Token Settings',
      portalUrl: 'https://github.com/settings/tokens/new?scopes=repo,workflow',
      faq: 'Zero-JGit atomic Git Data API push uses repo + workflow scopes for 1-tap APK builds.',
      steps: [
        'Create Classic PAT with repo and workflow scopes checked.',
        'Enter owner/OmniAI-Studio and PAT in Vault.',
        'Push from Projects tab -> Actions auto-builds v5.3.N APK.',
      ],
      commandTitle: 'Verify GitHub PAT & Scopes',
      command: 'curl -sI -H "Authorization: Bearer $GITHUB_TOKEN" https://api.github.com/user | grep -i x-oauth-scopes',
    },
    hf: {
      title: 'Hugging Face Serverless',
      portalLabel: 'Open HF Tokens',
      portalUrl: 'https://huggingface.co/settings/tokens',
      faq: 'Check "Make calls to Inference Providers" when creating a Fine-grained token.',
      steps: [
        'Open huggingface.co/settings/tokens -> Create new token.',
        'Enable Make calls to Inference Providers and paste hf_... into Vault.',
      ],
      commandTitle: 'Test HF Router Inference',
      command: 'curl -s https://router.huggingface.co/v1/chat/completions -H "Authorization: Bearer $HF_TOKEN"',
    },
    gemini: {
      title: 'Google Gemini API & Multi-Key Pool',
      portalLabel: 'Open Google AI Studio',
      portalUrl: 'https://aistudio.google.com/apikey',
      faq: 'Enter comma-separated AIza... keys in Vault for automatic HTTP 429 pool rotation.',
      steps: [
        'Open aistudio.google.com/apikey -> Create API Key.',
        'Paste one or comma-separated backup keys into Tertiary Gemini Key.',
      ],
      commandTitle: 'Test Gemini 2.5 Flash Key',
      command: 'curl -s "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$GEMINI_API_KEY"',
    },
    groq: {
      title: 'Groq / OpenRouter OpenAI Presets',
      portalLabel: 'Open Groq Console',
      portalUrl: 'https://console.groq.com/keys',
      faq: 'Tap Groq or OpenRouter chip in Quick Provider Presets to auto-fill Base URL & Model ID.',
      steps: [
        'Generate gsk_... (Groq) or sk-or-... (OpenRouter) key.',
        'Tap the matching Quick Provider Preset in Vault and paste your key.',
      ],
      commandTitle: 'Verify Groq Models Endpoint',
      command: 'curl -s https://api.groq.com/openai/v1/models -H "Authorization: Bearer $GROQ_API_KEY"',
    },
    youtube: {
      title: 'YouTube Data API v3 (OAuth2 + 2 MB Resumable Upload)',
      portalLabel: 'Open Google Cloud Console',
      portalUrl: 'https://console.cloud.google.com/apis/library/youtube.googleapis.com',
      faq: 'Unverified Personal App Rule: Videos uploaded via an unverified GCP OAuth app default to Private until toggled Public in YouTube Studio (1 tap). Free daily quota is 10,000 units (~6 full uploads/day at ~1,650 units each).',
      steps: [
        'Enable YouTube Data API v3 in Google Cloud Console.',
        'Configure OAuth Consent Screen -> External -> Add your Gmail as Test User.',
        'Create OAuth 2.0 Client ID -> Desktop App -> copy Client ID & Secret.',
        'Run the 15-line Python google_auth_oauthlib script once to print YT_REFRESH_TOKEN.',
        'Add YT_CLIENT_ID, YT_CLIENT_SECRET & YT_REFRESH_TOKEN to Vault and Kaggle Secrets.',
      ],
      commandTitle: '1-Time OAuth2 Refresh Token Script (Python)',
      command:
        'python3 -c "from google_auth_oauthlib.flow import InstalledAppFlow; flow=InstalledAppFlow.from_client_secrets_file(\'client_secret.json\',[\'https://www.googleapis.com/auth/youtube.upload\',\'https://www.googleapis.com/auth/youtube.force-ssl\']); c=flow.run_local_server(port=0); print(\'YT_REFRESH_TOKEN=\',c.refresh_token)"',
    },
    meta: {
      title: 'Meta Reels Graph API (FB Page & IG Business)',
      portalLabel: 'Open Meta Developers',
      portalUrl: 'https://developers.facebook.com/apps/',
      faq: 'Requires a Facebook Page linked to an Instagram Professional/Business account and a 60-day long-lived Page Access Token.',
      steps: [
        'Create a Meta Business App and generate a User Token with pages_show_list, publish_video & instagram_content_publish.',
        'Exchange for a 60-day long-lived Page Access Token using the Graph API curl below.',
        'Paste META_PAGE_ID, META_IG_USER_ID & META_PAGE_ACCESS_TOKEN into Vault or Kaggle Secrets.',
      ],
      commandTitle: 'Exchange 60-Day Long-Lived Meta Token',
      command:
        'curl -G "https://graph.facebook.com/v19.0/oauth/access_token" -d "grant_type=fb_exchange_token" -d "client_id=$META_APP_ID" -d "client_secret=$META_APP_SECRET" -d "fb_exchange_token=$SHORT_TOKEN"',
    },
    supabase: {
      title: 'Supabase PostgreSQL Cloud DB, Google OAuth PKCE & RBAC',
      portalLabel: 'Open Supabase Dashboard',
      portalUrl: 'https://supabase.com/dashboard',
      faq: 'Zero extra Gradle SDKs! Pure OkHttp3 PKCE S256 + GZIP text-only delta sync (<=256 KB/row, zero MP4/images) + first-user OWNER auto-bootstrap with server-enforced RLS.',
      steps: [
        'Create a free Supabase project and paste supabase/schema_and_rls.sql in SQL Editor -> Run.',
        'In Supabase Authentication -> Providers -> Google, enable Google OAuth with your GCP Web Client ID & Secret.',
        'In Authentication -> URL Configuration -> Redirect URLs, add omniaistudio://auth-callback.',
        'Paste SUPABASE_URL and SUPABASE_ANON_KEY in OmniAI Vault or Cloud DB & Roles.',
        'Add SUPABASE_URL & SUPABASE_ANON_KEY to GitHub Repo Secrets for the 4-day keep-supabase-alive.yml cron.',
      ],
      commandTitle: 'Verify Supabase PostgREST & Anti-Pause Endpoint',
      command:
        'curl -s "$SUPABASE_URL/rest/v1/user_profiles?select=count" -H "apikey: $SUPABASE_ANON_KEY"',
    },
  } as const;

  const activeAccent = THEME_ACCENTS.find((a) => a.id === accentId) ?? THEME_ACCENTS[1];
  const activeIconMeta = APP_ICON_VARIANTS.find((v) => v.id === iconVariant) ?? APP_ICON_VARIANTS[0];
  const selectedCharacter = characterProfiles.find((c) => c.id === selectedCharacterId) ?? null;

  const toggleTranslateKey = (key: string, source: string) => {
    setTranslatedKeys((prev) => {
      if (prev[key]) {
        const next = { ...prev };
        delete next[key];
        return next;
      }
      const isHindi = /[\u0900-\u097F]/.test(source);
      return {
        ...prev,
        [key]: isHindi
          ? { lang: 'English', text: 'Translated cleanly to English for side-by-side review.' }
          : { lang: 'Hindi (Devanagari)', text: 'यह पाठ आपके द्विभाषी (Side-by-Side) दृश्य के लिए हिंदी में अनुवादित है।' },
      };
    });
  };

  const triggerToast = (msg: string) => {
    setToastBanner(msg);
  };

  const tabSourceMap: Record<AndroidTab, string> = {
    home: 'app/src/main/java/com/omniai/studio/ui/screens/HomeBentoScreen.kt',
    chat: 'app/src/main/java/com/omniai/studio/ui/screens/ChatStudioScreen.kt',
    director: 'app/src/main/java/com/omniai/studio/ui/screens/VideoDirectorScreen.kt',
    projects: 'app/src/main/java/com/omniai/studio/ui/screens/ProjectDashboardScreen.kt',
    merger: 'app/src/main/java/com/omniai/studio/ui/screens/VideoMergerScreen.kt',
    gallery: 'app/src/main/java/com/omniai/studio/ui/screens/DownloadsGalleryScreen.kt',
    vault: 'app/src/main/java/com/omniai/studio/ui/screens/VaultSettingsScreen.kt',
  };

  const estimatedTokens =
    Math.ceil(
      (rollingSummary.length + messages.reduce((acc, m) => acc + m.content.length, 0)) / 4
    ) + 48;

  const handleCompactMemory = () => {
    if (messages.length < 2) return;
    const earlier = messages.slice(0, Math.max(1, messages.length - 2));
    const digest = earlier
      .map((m) => `• ${m.role === 'user' ? 'User' : 'AI'}: ${m.content.replace(/\n/g, ' ').slice(0, 68)}`)
      .join('\n');
    setRollingSummary(digest);
    setSummarizedCount((prev) => prev + earlier.length);
  };

  const handleSendChat = () => {
    const text = chatInput.trim();
    if (!text) return;
    const provider = simulateFailover
      ? 'Failover → Gemini 2.5 Flash (HTTP 429 bypassed)'
      : `Primary · ${primaryModel}`;
    const priorTopic = messages.find((m) => m.role === 'user')?.content.slice(0, 48) ?? 'this thread';
    const newReplyId = Date.now() + 1;
    setMessages((prev) => [
      ...prev,
      { id: Date.now(), role: 'user', content: text },
      {
        id: newReplyId,
        role: 'assistant',
        provider,
        content: `Continuing from context ("${priorTopic}...") using window=${contextWindow}, temp=${temperature.toFixed(2)}, max_tokens=${maxTokens}: streamed via ${provider}.`,
      },
    ]);
    if (autoReadTts) {
      setSpeakingMsgId(newReplyId);
      triggerToast(`Auto-Reading reply via Offline TTS (${ttsSpeed.toFixed(2)}x)`);
    }
    setChatInput('');
  };

  const handleApplyTemplate = (tpl: VideoTemplateRecord) => {
    setTopic(tpl.name);
    setAspectRatio(tpl.aspectRatio);
    setVisualStyle(tpl.defaultVisualStyle);
    setVoiceover(tpl.defaultVoice);
    setBurnSubtitles(tpl.burnSubtitlesDefault);
    setScenes(
      tpl.scenes.map((s, i) => ({
        id: Date.now() + i,
        visualPrompt: s.visualDescription,
        narration: s.narrationPlaceholder,
      }))
    );
  };

  const handleSaveCustomTemplate = (draft: TemplateDraft): VideoTemplateRecord => {
    const { templates, saved } = upsertCustomTemplate(customTemplates, draft);
    setCustomTemplates(templates);
    persistCustomTemplates(templates);
    return saved;
  };

  const handleDeleteCustomTemplate = (id: string) => {
    const next = deleteCustomTemplate(customTemplates, id);
    setCustomTemplates(next);
    persistCustomTemplates(next);
  };

  const moveScene = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= scenes.length) return;
    const next = [...scenes];
    const [removed] = next.splice(index, 1);
    next.splice(target, 0, removed);
    setScenes(next);
  };

  const enqueueBatch = () => {
    setBatchQueue((prev) => [
      {
        id: Date.now(),
        title: topic.slice(0, 32) || 'Untitled Storyboard',
        status: `QUEUED · ${scenes.length} scenes · Poll ${kagglePollSec}s · ${wifiOnlyDownload ? 'Wi-Fi Only' : 'Any Net'}`,
        progress: 12,
      },
      ...prev,
    ]);
  };

  const moveClip = (index: number, delta: number) => {
    const target = index + delta;
    if (target < 0 || target >= clips.length) return;
    const next = [...clips];
    const [removed] = next.splice(index, 1);
    next.splice(target, 0, removed);
    setClips(next);
  };

  const updateClipTrim = (index: number, field: 'startSec' | 'endSec', value: string) => {
    setClips((prev) =>
      prev.map((c, idx) => (idx === index ? { ...c, [field]: value } : c))
    );
  };

  const runMergePreview = () => {
    if (clips.length < 2) return;
    setMergeProgress(100);
    const outFile = `${mergeName || 'OmniAI_Master_Cut'}.mp4`;
    const trimmedCount = clips.filter((c) => c.startSec || c.endSec).length;
    setMergeStatus(
      `Exported to Movies/OmniAIStudio/${outFile}${trimmedCount > 0 ? ` (${trimmedCount} trimmed)` : ''}`
    );
    if (autoCleanMerge) setCacheMb(0);
    setDownloads((prev) => [
      {
        id: Date.now(),
        name: outFile,
        category: 'MERGED',
        sizeMb: Number((clips.length * 5.8).toFixed(1)),
        status: 'COMPLETED',
        savedToGallery: true,
      },
      ...prev,
    ]);
  };

  const executePanicWipe = () => {
    setMessages([]);
    setScenes([]);
    setDownloads([]);
    setCacheMb(0);
    setCrashLogText('');
    setKeysRevealed(false);
    setPanicStage(0);
    setWipedNotice('Vault shredded, Room tables cleared, crash_log.txt & caches purged.');
  };

  const renderIconGlyph = (id: IconVariantId, color: string) => {
    if (id === 'DEFAULT') return <Sparkles className="h-4 w-4" style={{ color }} />;
    if (id === 'CYBER') return <Zap className="h-4 w-4" style={{ color }} />;
    if (id === 'CALCULATOR') return <Calculator className="h-4 w-4" style={{ color }} />;
    return <FileText className="h-4 w-4" style={{ color }} />;
  };

  return (
    <div className="flex flex-1 min-h-0 flex-col overflow-y-auto bg-[#080A0E] p-6 lg:flex-row lg:items-start lg:justify-center lg:gap-10">
      {/* Left Column: Android AMOLED Device Frame */}
      <div className="relative mx-auto flex w-full max-w-[400px] shrink-0 flex-col overflow-hidden rounded-2xl border border-[#232330] bg-[#09090C]">
        {/* Slim 56dp TopAppBar (v5.1 Hub-and-Spoke) */}
        <div className="flex h-12 items-center justify-between border-b border-[#232330] bg-[#13131A] px-2.5 text-[11px] text-zinc-400">
          <div className="flex items-center gap-1.5 min-w-0">
            {showSettingsHub ? (
              <button
                type="button"
                aria-label="Back from Settings Hub"
                onClick={() => setShowSettingsHub(false)}
                className="rounded-lg p-1.5 text-zinc-200 hover:bg-[#1B1B25]"
              >
                <ArrowLeft className="h-3.5 w-3.5" />
              </button>
            ) : (
              <button
                type="button"
                aria-label="Open Navigation Drawer"
                onClick={() => setDrawerOpen((v) => !v)}
                className="rounded-lg p-1.5 text-zinc-200 hover:bg-[#1B1B25]"
              >
                <Menu className="h-4 w-4" />
              </button>
            )}
            {showSettingsHub ? (
              <span className="truncate font-semibold text-zinc-100">Settings Hub</span>
            ) : (
              <button
                type="button"
                onClick={() => {
                  const niches = ['🎬 3D Stories', '🌌 Space Facts', '🤖 AI & Tech', '🕵️ Dark History'];
                  const next = niches[(niches.indexOf(activeNicheLabel) + 1) % niches.length];
                  setActiveNicheLabel(next);
                  triggerToast(`Switched Active Niche → ${next}`);
                }}
                className="flex items-center gap-1 rounded-full border border-[#232330] bg-[#1B1B25] px-2.5 py-1 text-[11px] font-medium text-zinc-100 hover:border-emerald-500/40"
              >
                <span className="truncate">{activeNicheLabel}</span>
                <ChevronDown className="h-3 w-3 text-zinc-400" />
              </button>
            )}
          </div>
          {!showSettingsHub && (
            <div className="flex items-center gap-1.5 shrink-0">
              <button
                type="button"
                aria-label="Open Command Palette"
                onClick={() => triggerToast('Command Palette (Spotlight): Search settings, chats, > terminal')}
                className="rounded-lg p-1.5 text-zinc-300 hover:bg-[#1B1B25]"
              >
                <Search className="h-4 w-4" />
              </button>
              <button
                type="button"
                aria-label="Live Studio Activity"
                onClick={() => {
                  setActiveTab('director');
                  triggerToast('Live Activity: 1 Kaggle Dual-T4 Render Active · 11.4/30h used');
                }}
                className="flex items-center gap-1.5 rounded-full border px-2 py-0.5 text-[10px] font-semibold"
                style={{
                  borderColor: `${activeAccent.hex}55`,
                  backgroundColor: `${activeAccent.hex}1F`,
                  color: activeAccent.hex,
                }}
              >
                <span
                  className="h-1.5 w-1.5 rounded-full animate-pulse"
                  style={{ backgroundColor: activeAccent.hex }}
                />
                <span>GPU 1</span>
              </button>
            </div>
          )}
        </div>

        {/* Slide-Out ModalNavigationDrawer Overlay */}
        {drawerOpen && !showSettingsHub && (
          <div className="absolute inset-0 z-30 flex bg-black/70">
            <div className="flex h-full w-64 flex-col border-r border-[#232330] bg-[#13131A] p-3 text-xs text-zinc-200 overflow-y-auto">
              <div className="flex items-center justify-between border-b border-[#232330] pb-2.5">
                <div>
                  <div className="font-semibold text-zinc-100">{activeIconMeta.launcherLabel}</div>
                  <div className="text-[10px] text-zinc-400">Hub &amp; Power Tools · v5.5 · {simCloudRole}</div>
                </div>
                <button
                  type="button"
                  aria-label="Close Drawer"
                  onClick={() => setDrawerOpen(false)}
                  className="rounded p-1 text-zinc-400 hover:bg-[#1B1B25] hover:text-zinc-100"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="mt-3 space-y-3">
                <div>
                  <div className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    Create &amp; Media
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab('merger');
                      setDrawerOpen(false);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Video className="h-3.5 w-3.5 text-emerald-400" />
                    <span>Media Studio (Merge/Trim)</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab('gallery');
                      setDrawerOpen(false);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Download className="h-3.5 w-3.5 text-emerald-400" />
                    <span>Offline Gallery ({downloads.length})</span>
                  </button>
                </div>
                <div>
                  <div className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    Developer &amp; Agents
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setDrawerOpen(false);
                      triggerToast('Opened Hybrid Terminal & OmniCLI (/system/bin/sh)');
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Terminal className="h-3.5 w-3.5 text-cyan-400" />
                    <span>Hybrid Terminal &amp; Bridge</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setShowSettingsHub(true);
                      setDrawerOpen(false);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Rss className="h-3.5 w-3.5 text-amber-400" />
                    <span>RSS News Agent</span>
                  </button>
                </div>
                <div>
                  <div className="px-2 pb-1 text-[10px] font-semibold uppercase tracking-wider text-zinc-500">
                    System &amp; Vault
                  </div>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveTab('vault');
                      setDrawerOpen(false);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Shield className="h-3.5 w-3.5 text-emerald-400" />
                    <span>API Vault &amp; Multi-Key Pool</span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setShowSettingsHub(true);
                      setDrawerOpen(false);
                    }}
                    className="flex w-full items-center gap-2 rounded-lg px-2 py-1.5 text-left hover:bg-[#1B1B25]"
                  >
                    <Settings className="h-3.5 w-3.5 text-zinc-300" />
                    <span>Settings &amp; Stealth Icons</span>
                  </button>
                </div>
              </div>
            </div>
            <div className="flex-1" onClick={() => setDrawerOpen(false)} />
          </div>
        )}

        {/* Transient Toast Strip */}
        {toastBanner && (
          <div className="flex items-center justify-between border-b border-[#232330] bg-[#13131A] px-3 py-1.5 text-[11px] text-zinc-200">
            <span className="truncate">{toastBanner}</span>
            <button
              type="button"
              aria-label="Dismiss notification"
              onClick={() => setToastBanner(null)}
              className="ml-2 text-zinc-400 hover:text-zinc-100"
            >
              <X className="h-3 w-3" />
            </button>
          </div>
        )}

        {/* v5.5 Viewer Role Read-Only Guard Banner */}
        {simCloudRole === 'VIEWER' && !showSettingsHub && (
          <div className="mx-3 mt-2 flex items-center gap-2 rounded-lg border border-amber-500/45 bg-amber-500/12 px-2.5 py-1.5 text-[10px] font-medium text-amber-300">
            <Eye className="h-3.5 w-3.5 shrink-0" />
            <span>Read-Only (Viewer Role) — Edit, Delete, Render, Git Push &amp; Vault writes disabled</span>
          </div>
        )}

        {/* Active Screen Viewport */}
        <div className="flex h-[580px] flex-col overflow-y-auto bg-[#09090C] p-4 text-zinc-100">
          {showSettingsHub ? (
            <div className="space-y-3 text-xs">
              {/* Section 1: Appearance & App Icon */}
              <div className="space-y-2.5 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="font-semibold" style={{ color: activeAccent.hex }}>
                  1. Appearance & App Icon Disguise
                </div>
                <div className="grid grid-cols-2 gap-2">
                  {APP_ICON_VARIANTS.map((variant) => {
                    const selected = iconVariant === variant.id;
                    return (
                      <button
                        key={variant.id}
                        type="button"
                        onClick={() => {
                          if (!selected) setPendingIconConfirm(variant.id);
                        }}
                        className="rounded-lg border p-2 text-left transition-colors"
                        style={{
                          borderColor: selected ? activeAccent.hex : '#27272a',
                          backgroundColor: selected ? '#141923' : '#000000',
                        }}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-zinc-900">
                            {renderIconGlyph(variant.id, variant.accentHex)}
                          </div>
                          {selected && <Check className="h-3.5 w-3.5" style={{ color: activeAccent.hex }} />}
                        </div>
                        <div className="mt-1.5 font-medium text-zinc-100">{variant.title}</div>
                        <div className="text-[10px] text-zinc-400">"{variant.launcherLabel}"</div>
                      </button>
                    );
                  })}
                </div>

                {pendingIconConfirm && (
                  <div className="rounded border border-amber-500/40 bg-amber-950/20 p-2 text-[11px]">
                    <div className="font-medium text-amber-200">
                      Switch launcher icon to "{APP_ICON_VARIANTS.find((v) => v.id === pendingIconConfirm)?.launcherLabel}"?
                    </div>
                    <div className="mt-1.5 flex gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          const chosen = APP_ICON_VARIANTS.find((v) => v.id === pendingIconConfirm)!;
                          setIconVariant(chosen.id);
                          setPendingIconConfirm(null);
                          triggerToast(`Activity-alias switched to "${chosen.launcherLabel}"`);
                        }}
                        className="flex-1 rounded py-1 font-semibold text-zinc-950"
                        style={{ backgroundColor: activeAccent.hex }}
                      >
                        Apply Icon
                      </button>
                      <button
                        type="button"
                        onClick={() => setPendingIconConfirm(null)}
                        className="rounded border border-zinc-700 px-2.5 py-1 text-zinc-300"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}

                <div className="space-y-1 pt-1">
                  <div className="text-[10px] text-zinc-400">Theme Accent Color</div>
                  <div className="grid grid-cols-4 gap-1.5">
                    {THEME_ACCENTS.map((acc) => (
                      <button
                        key={acc.id}
                        type="button"
                        onClick={() => setAccentId(acc.id)}
                        className="flex flex-col items-center gap-1 rounded border py-1.5 text-[10px]"
                        style={{
                          borderColor: accentId === acc.id ? acc.hex : '#27272a',
                          backgroundColor: accentId === acc.id ? '#141923' : '#000000',
                        }}
                      >
                        <span className="h-3.5 w-3.5 rounded-full" style={{ backgroundColor: acc.hex }} />
                        <span>{acc.label}</span>
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <div className="flex justify-between text-[11px]">
                    <span className="text-zinc-300">Chat Font Size</span>
                    <span className="font-mono" style={{ color: activeAccent.hex }}>
                      {chatFontSize}sp
                    </span>
                  </div>
                  <input
                    type="range"
                    min={12}
                    max={20}
                    value={chatFontSize}
                    onChange={(e) => setChatFontSize(Number(e.target.value))}
                    aria-label="Chat font size"
                    className="w-full"
                    style={{ accentColor: activeAccent.hex }}
                  />
                </div>
              </div>

              {/* Section 2: AI Engine & Generation Controls */}
              <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="font-semibold" style={{ color: activeAccent.hex }}>
                  2. AI Engine & Offline TTS
                </div>
                <div>
                  <div className="flex justify-between text-[11px]">
                    <span>Temperature</span>
                    <span className="font-mono">{temperature.toFixed(2)}</span>
                  </div>
                  <input
                    type="range"
                    min={0}
                    max={1.5}
                    step={0.05}
                    value={temperature}
                    onChange={(e) => setTemperature(Number(e.target.value))}
                    aria-label="Temperature"
                    className="w-full"
                    style={{ accentColor: activeAccent.hex }}
                  />
                </div>
                <div>
                  <div className="flex justify-between text-[11px]">
                    <span>Max Output Tokens</span>
                    <span className="font-mono">{maxTokens}</span>
                  </div>
                  <input
                    type="range"
                    min={256}
                    max={8192}
                    step={256}
                    value={maxTokens}
                    onChange={(e) => setMaxTokens(Number(e.target.value))}
                    aria-label="Max output tokens"
                    className="w-full"
                    style={{ accentColor: activeAccent.hex }}
                  />
                </div>
                <div>
                  <div className="flex justify-between text-[11px]">
                    <span>Chat Context Window</span>
                    <span className="font-mono">{contextWindow} msgs</span>
                  </div>
                  <input
                    type="range"
                    min={4}
                    max={30}
                    value={contextWindow}
                    onChange={(e) => setContextWindow(Number(e.target.value))}
                    aria-label="Chat context window"
                    className="w-full"
                    style={{ accentColor: activeAccent.hex }}
                  />
                </div>
                <label className="flex items-center justify-between pt-1">
                  <span>Auto-Read AI Replies (Offline TTS)</span>
                  <input
                    type="checkbox"
                    checked={autoReadTts}
                    onChange={(e) => setAutoReadTts(e.target.checked)}
                    style={{ accentColor: activeAccent.hex }}
                  />
                </label>
                <div>
                  <div className="flex justify-between text-[11px]">
                    <span>Hindi/English TTS Speed</span>
                    <span className="font-mono">{ttsSpeed.toFixed(2)}x</span>
                  </div>
                  <input
                    type="range"
                    min={0.75}
                    max={1.5}
                    step={0.05}
                    value={ttsSpeed}
                    onChange={(e) => setTtsSpeed(Number(e.target.value))}
                    aria-label="TTS speed"
                    className="w-full"
                    style={{ accentColor: activeAccent.hex }}
                  />
                </div>
              </div>

              {/* Section 3: Automation & Download Rules */}
              <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="font-semibold" style={{ color: activeAccent.hex }}>
                  3. Automation & Download Rules
                </div>
                <label className="flex items-center justify-between">
                  <span>Wi-Fi Only Auto-Download</span>
                  <input
                    type="checkbox"
                    checked={wifiOnlyDownload}
                    onChange={(e) => setWifiOnlyDownload(e.target.checked)}
                    style={{ accentColor: activeAccent.hex }}
                  />
                </label>
                <div className="flex items-center justify-between">
                  <span className="text-zinc-400">Kaggle Poll Interval</span>
                  <div className="flex gap-1">
                    {([15, 20, 30, 60] as const).map((sec) => (
                      <button
                        key={sec}
                        type="button"
                        onClick={() => setKagglePollSec(sec)}
                        className="rounded px-2 py-0.5 font-mono text-[10px]"
                        style={{
                          backgroundColor: kagglePollSec === sec ? `${activeAccent.hex}30` : '#18181b',
                          color: kagglePollSec === sec ? activeAccent.hex : '#a1a1aa',
                        }}
                      >
                        {sec}s
                      </button>
                    ))}
                  </div>
                </div>
                <label className="flex items-center justify-between">
                  <span>Auto-Clean Temp After Merge</span>
                  <input
                    type="checkbox"
                    checked={autoCleanMerge}
                    onChange={(e) => setAutoCleanMerge(e.target.checked)}
                    style={{ accentColor: activeAccent.hex }}
                  />
                </label>

                {/* Kaggle 30-Hr Weekly GPU Quota Meter */}
                <div className="space-y-1 border-t border-zinc-800 pt-2">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="font-medium text-zinc-200">Kaggle Weekly GPU Quota</span>
                    <span
                      className="font-mono text-[10px]"
                      style={{ color: 30 - weeklyGpuHours < 2 ? '#FF5252' : activeAccent.hex }}
                    >
                      Used: {weeklyGpuHours.toFixed(1)} / 30.0 hrs
                    </span>
                  </div>
                  <div className="h-1.5 w-full overflow-hidden rounded bg-zinc-900">
                    <div
                      className="h-full"
                      style={{
                        width: `${Math.min(100, (weeklyGpuHours / 30) * 100)}%`,
                        backgroundColor: 30 - weeklyGpuHours < 2 ? '#FF5252' : activeAccent.hex,
                      }}
                    />
                  </div>
                  {30 - weeklyGpuHours < 2 && (
                    <div className="flex items-center gap-1 text-[10px] text-red-400">
                      <AlertTriangle className="h-3 w-3 shrink-0" />
                      <span>Warning: Less than 2.0 hrs remaining this week!</span>
                    </div>
                  )}
                </div>
              </div>

              {/* Section 3B: News Tracker (Scheduled RSS WorkManager Agent) */}
              <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="flex items-center justify-between">
                  <span className="inline-flex items-center gap-1 font-semibold" style={{ color: activeAccent.hex }}>
                    <Rss className="h-3.5 w-3.5" />
                    <span>News Tracker (RSS Agent)</span>
                  </span>
                  <div className="flex gap-1">
                    {([12, 24] as const).map((h) => (
                      <button
                        key={h}
                        type="button"
                        onClick={() => setNewsIntervalHours(h)}
                        className="rounded px-1.5 py-0.5 font-mono text-[10px]"
                        style={{
                          backgroundColor: newsIntervalHours === h ? `${activeAccent.hex}30` : '#18181b',
                          color: newsIntervalHours === h ? activeAccent.hex : '#a1a1aa',
                        }}
                      >
                        {h}h
                      </button>
                    ))}
                  </div>
                </div>
                <label className="flex items-center justify-between">
                  <span>WorkManager RSS Poll (Human-in-the-Loop)</span>
                  <input
                    type="checkbox"
                    checked={newsAgentEnabled}
                    onChange={(e) => setNewsAgentEnabled(e.target.checked)}
                    style={{ accentColor: activeAccent.hex }}
                  />
                </label>
                <div className="flex gap-1">
                  <input
                    type="text"
                    value={newRssName}
                    onChange={(e) => setNewRssName(e.target.value)}
                    placeholder="Feed name"
                    className="w-24 rounded border border-zinc-800 bg-black px-1.5 py-1 text-[10px] text-zinc-100"
                  />
                  <input
                    type="text"
                    value={newRssUrl}
                    onChange={(e) => setNewRssUrl(e.target.value)}
                    placeholder="https://site/rss.xml"
                    className="flex-1 rounded border border-zinc-800 bg-black px-1.5 py-1 text-[10px] text-zinc-100"
                  />
                  <button
                    type="button"
                    onClick={() => {
                      if (!newRssUrl.trim()) return;
                      setRssFeeds((prev) => [
                        ...prev,
                        { id: Date.now(), name: newRssName.trim() || 'Custom RSS', url: newRssUrl.trim() },
                      ]);
                      setNewRssName('');
                      setNewRssUrl('');
                      triggerToast('Saved RSS feed to Room News Tracker.');
                    }}
                    className="rounded px-2 py-1 text-[10px] font-semibold text-zinc-950"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    Add
                  </button>
                </div>
                <div className="text-[10px] text-zinc-400">
                  Feeds ({rssFeeds.length}): {rssFeeds.map((f) => f.name).join(', ')}
                </div>
                {pendingDigest && (
                  <div className="space-y-1 rounded border border-emerald-500/30 bg-emerald-950/20 p-2">
                    <div className="font-semibold text-emerald-300">
                      Daily AI Brief Ready for Approval
                    </div>
                    <p className="whitespace-pre-line text-[10px] text-zinc-300">{pendingDigest.summary}</p>
                    <button
                      type="button"
                      onClick={() => {
                        setTopic(pendingDigest.topic);
                        setShowSettingsHub(false);
                        setActiveTab('director');
                        triggerToast('Approved RSS Brief -> loaded into Video Director.');
                      }}
                      className="w-full rounded py-1 text-[10px] font-semibold text-zinc-950"
                      style={{ backgroundColor: activeAccent.hex }}
                    >
                      Approve &amp; Open in Director
                    </button>
                  </div>
                )}
              </div>

              {/* Section 4: Duress / Decoy PIN Security */}
              <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="font-semibold" style={{ color: activeAccent.hex }}>
                  4. Duress / Decoy PIN Security
                </div>
                <p className="text-[10px] text-zinc-400">
                  Master PIN (<span className="font-mono text-zinc-200">1234</span>) unlocks Studio · Decoy PIN (
                  <span className="font-mono text-amber-300">0000</span>) opens harmless Quick Notes sandbox without wiping real data.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    setLockOverlayOpen(true);
                    setPinEntry('');
                    setPinError(null);
                  }}
                  className="w-full rounded border border-zinc-700 bg-zinc-900 py-1.5 text-[11px] font-medium text-zinc-200 hover:bg-zinc-800"
                >
                  Lock &amp; Test Master / Decoy PIN
                </button>
              </div>

              {/* Section 4 & 5: Storage, OTA & Crash Diagnostics */}
              <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                <div className="font-semibold" style={{ color: activeAccent.hex }}>
                  5. Storage, OTA & Crash Diagnostics
                </div>
                <div className="font-mono text-[10px] text-zinc-400 tabular-nums">
                  Videos: 38.7 MB · Cache: {cacheMb.toFixed(1)} MB · Room DB: 1.4 MB
                </div>
                <div className="grid grid-cols-2 gap-1.5">
                  <button
                    type="button"
                    onClick={() => {
                      setCacheMb(0);
                      triggerToast('Cleared temporary Media3 & OTA cache.');
                    }}
                    className="rounded border border-zinc-700 bg-zinc-900 py-1.5 text-[11px] font-medium text-zinc-200 hover:bg-zinc-800"
                  >
                    1-Tap Clear Cache
                  </button>
                  <button
                    type="button"
                    onClick={() => setShowCrashViewer((v) => !v)}
                    className="inline-flex items-center justify-center gap-1 rounded border border-zinc-700 bg-zinc-900 py-1.5 text-[11px] font-medium text-zinc-200 hover:bg-zinc-800"
                  >
                    <Bug className="h-3 w-3" />
                    <span>{showCrashViewer ? 'Hide Log' : 'Crash Log'}</span>
                  </button>
                </div>
                {showCrashViewer && (
                  <div className="space-y-1.5 rounded border border-zinc-800 bg-black p-2">
                    <pre className="max-h-28 overflow-auto font-mono text-[10px] leading-4 text-zinc-300">
                      {crashLogText || 'No crashes recorded in filesDir/crash_log.txt.'}
                    </pre>
                    <div className="flex gap-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          navigator.clipboard?.writeText(crashLogText).catch(() => {});
                          triggerToast('Crash log copied to clipboard.');
                        }}
                        disabled={!crashLogText}
                        className="flex-1 rounded border border-zinc-700 py-1 text-[10px] text-zinc-200 disabled:opacity-40"
                      >
                        Copy Log
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setCrashLogText('');
                          triggerToast('Cleared crash_log.txt.');
                        }}
                        disabled={!crashLogText}
                        className="rounded border border-red-500/40 px-2.5 py-1 text-[10px] text-red-300 disabled:opacity-40"
                      >
                        Clear
                      </button>
                    </div>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <>
              {activeTab === 'home' && (
                <div className="flex flex-1 flex-col gap-4">
                  {/* Hero Card: Active Niche + Next Suggested Episode */}
                  <div className="rounded-2xl border border-[#232330] bg-[#13131A] p-4 space-y-2.5">
                    <div className="flex items-center justify-between">
                      <span
                        className="rounded-full border px-2 py-0.5 text-[10px] font-semibold"
                        style={{
                          borderColor: `${activeAccent.hex}55`,
                          backgroundColor: `${activeAccent.hex}1F`,
                          color: activeAccent.hex,
                        }}
                      >
                        {activeNicheLabel}
                      </span>
                      <span className="text-[10px] font-mono text-zinc-400">Next Ep #05</span>
                    </div>
                    <div>
                      <div className="text-sm font-semibold text-zinc-100">
                        Why Dual T4 Sharding Cuts Wan 2.1 Render Time in Half
                      </div>
                      <p className="mt-0.5 text-[11px] text-zinc-400">
                        9:16 · Cinematic Realism · Auto-continuity ready
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        setTopic('Why Dual T4 Sharding Cuts Wan 2.1 Render Time in Half');
                        setActiveTab('director');
                        triggerToast('Auto-typed Next Episode into Video Studio Step 1');
                      }}
                      className="flex w-full items-center justify-center gap-1.5 rounded-xl py-2.5 text-xs font-semibold text-black transition-opacity hover:opacity-90"
                      style={{ backgroundColor: activeAccent.hex }}
                    >
                      <Sparkles className="h-3.5 w-3.5" />
                      <span>Auto-Type &amp; Create</span>
                    </button>
                  </div>

                  {/* 2-Column Bento Row 1: GPU Quota Ring + Active Project Push */}
                  <div className="grid grid-cols-2 gap-3">
                    <div className="rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 flex flex-col justify-between gap-2">
                      <div className="text-[11px] font-semibold text-zinc-200">Kaggle GPU</div>
                      <div className="flex items-center gap-2.5">
                        <svg viewBox="0 0 36 36" className="h-11 w-11 shrink-0 -rotate-90">
                          <circle cx="18" cy="18" r="15" fill="none" stroke="#232330" strokeWidth="3.5" />
                          <circle
                            cx="18"
                            cy="18"
                            r="15"
                            fill="none"
                            stroke={activeAccent.hex}
                            strokeWidth="3.5"
                            strokeDasharray={`${Math.min(100, (weeklyGpuHours / 30) * 94.2)} 94.2`}
                            strokeLinecap="round"
                          />
                        </svg>
                        <div>
                          <div className="font-mono text-xs font-bold text-zinc-100">
                            {weeklyGpuHours.toFixed(1)}/30h
                          </div>
                          <div className="text-[10px] text-zinc-400">Weekly T4</div>
                        </div>
                      </div>
                    </div>

                    <div className="rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 flex flex-col justify-between gap-2">
                      <div className="flex items-center justify-between">
                        <span className="text-[11px] font-semibold text-zinc-200">Project</span>
                        <span className="font-mono text-[10px] text-amber-400">
                          {pendingGitChanges > 0 ? `${pendingGitChanges} diff` : 'Synced'}
                        </span>
                      </div>
                      <div className="truncate text-xs font-medium text-zinc-100">OmniAI-Studio</div>
                      <button
                        type="button"
                        onClick={() => {
                          setPendingGitChanges(0);
                          triggerToast('Atomic 6-step Git Auto-Push completed → main');
                        }}
                        className="flex items-center justify-center gap-1 rounded-lg border border-emerald-500/40 bg-emerald-500/10 py-1.5 text-[10px] font-semibold text-emerald-300 hover:bg-emerald-500/20"
                      >
                        <UploadCloud className="h-3 w-3" />
                        <span>1-Tap Push</span>
                      </button>
                    </div>
                  </div>

                  {/* 2-Column Bento Row 2: Quick AI Voice + Open Terminal */}
                  <div className="grid grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => {
                        setActiveTab('chat');
                        triggerToast('Quick AI Voice listening (EN / HI)...');
                      }}
                      className="flex items-center gap-2.5 rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 text-left hover:border-emerald-500/40"
                    >
                      <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-400">
                        <Mic className="h-4 w-4" />
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-zinc-100">Quick Voice</div>
                        <div className="text-[10px] text-zinc-400">EN / HI Live</div>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => triggerToast('Hybrid Terminal ready · /system/bin/sh + OmniCLI')}
                      className="flex items-center gap-2.5 rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 text-left hover:border-cyan-500/40"
                    >
                      <div className="flex h-8 w-8 items-center justify-center rounded-xl bg-cyan-500/15 text-cyan-400">
                        <Terminal className="h-4 w-4" />
                      </div>
                      <div>
                        <div className="text-xs font-semibold text-zinc-100">Terminal</div>
                        <div className="text-[10px] text-zinc-400">OmniCLI</div>
                      </div>
                    </button>
                  </div>

                  {/* Recent Activity Strip */}
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-[11px] font-semibold text-zinc-300">Recent Activity</span>
                      <button
                        type="button"
                        onClick={() => setActiveTab('gallery')}
                        className="text-[10px] font-medium"
                        style={{ color: activeAccent.hex }}
                      >
                        Gallery
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => setActiveTab('chat')}
                      className="flex w-full items-center justify-between rounded-xl border border-[#232330] bg-[#13131A] px-3 py-2.5 text-left hover:border-zinc-700"
                    >
                      <div className="min-w-0">
                        <div className="truncate text-xs font-medium text-zinc-100">Media3 Transformer Clip Merge</div>
                        <div className="text-[10px] text-zinc-400">Chat · Primary Tunnel</div>
                      </div>
                      <MessageSquare className="h-3.5 w-3.5 shrink-0 text-zinc-400" />
                    </button>
                    <button
                      type="button"
                      onClick={() => setActiveTab('gallery')}
                      className="flex w-full items-center justify-between rounded-xl border border-[#232330] bg-[#13131A] px-3 py-2.5 text-left hover:border-zinc-700"
                    >
                      <div className="min-w-0">
                        <div className="truncate text-xs font-medium text-zinc-100">TokyoNoir_job12_final.mp4</div>
                        <div className="text-[10px] text-zinc-400">Rendered · 18.4 MB</div>
                      </div>
                      <Film className="h-3.5 w-3.5 shrink-0 text-emerald-400" />
                    </button>
                  </div>
                </div>
              )}

              {activeTab === 'projects' && (
                <div className="relative flex min-h-full flex-1 flex-col gap-3 pb-1">
                  <div className="flex items-center justify-between">
                    <div>
                      <h2 className="text-sm font-semibold text-zinc-100">Projects &amp; Git Hub</h2>
                      <p className="text-[10px] text-zinc-400">Kanban · Fullscreen Code Editor · Atomic Push</p>
                    </div>
                    <span className="rounded-full border border-amber-500/40 bg-amber-500/10 px-2 py-0.5 font-mono text-[10px] text-amber-300">
                      {pendingGitChanges > 0 ? `+2 ~${Math.max(0, pendingGitChanges - 2)} -0` : 'Synced'}
                    </span>
                  </div>
                  <div className="rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 space-y-2">
                    <div className="flex items-center justify-between">
                      <span className="text-xs font-semibold text-zinc-100">OmniAI-Studio</span>
                      <span className="text-[10px] font-mono text-emerald-400">main · v5.1</span>
                    </div>
                    <p className="text-[11px] text-zinc-400">
                      Jetpack Compose AMOLED Suite + Zero-Dependency GitHub Git Data API Engine.
                    </p>
                    <div className="space-y-1">
                      <div className="flex justify-between text-[10px] text-zinc-400">
                        <span>Kanban Progress</span>
                        <span className="text-emerald-400">8 / 10 done</span>
                      </div>
                      <div className="h-1.5 w-full overflow-hidden rounded bg-[#1B1B25]">
                        <div className="h-full w-4/5 bg-emerald-400" />
                      </div>
                    </div>
                  </div>
                  <div className="rounded-2xl border border-[#232330] bg-[#13131A] p-3.5 space-y-1.5">
                    <div className="text-xs font-semibold text-zinc-200">Changed Files Ready for Atomic Push</div>
                    <div className="font-mono text-[10px] text-emerald-400">+ ui/theme/DesignTokens.kt</div>
                    <div className="font-mono text-[10px] text-emerald-400">+ ui/components/AppNavigationShell.kt</div>
                    <div className="font-mono text-[10px] text-amber-300">~ ui/screens/HomeBentoScreen.kt</div>
                  </div>
                  {pendingGitChanges > 0 && (
                    <button
                      type="button"
                      onClick={() => {
                        setPendingGitChanges(0);
                        triggerToast('Sticky Git Auto-Push: Committed 3 files atomically to GitHub!');
                      }}
                      className="sticky bottom-1 mt-auto ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald-400 px-3.5 py-2 text-xs font-semibold text-black shadow-lg hover:bg-emerald-300"
                    >
                      <UploadCloud className="h-3.5 w-3.5" />
                      <span>Git Auto-Push ({pendingGitChanges} changes)</span>
                    </button>
                  )}
                </div>
              )}

              {activeTab === 'chat' && (
                <div className="flex flex-1 flex-col gap-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-semibold">AI Chat Studio</h2>
                    <button
                      type="button"
                      onClick={() => setSimulateFailover((v) => !v)}
                      className={`rounded px-2 py-1 text-[11px] font-medium transition-colors ${
                        simulateFailover
                          ? 'bg-amber-500/20 text-amber-300'
                          : 'bg-zinc-900 text-zinc-400 hover:text-zinc-200'
                      }`}
                    >
                      {simulateFailover ? 'Failover: Gemini 2.5' : 'Primary Tunnel'}
                    </button>
                  </div>

                  {/* Room Conversation Memory Strip */}
                  <div className="flex items-center justify-between rounded border border-zinc-800/90 bg-[#0B0E14] px-2.5 py-1.5 text-[10px]">
                    <span className="font-mono text-zinc-400 tabular-nums">
                      Memory: {messages.length}/{contextWindow} win · {summarizedCount} sum · ~{estimatedTokens} tok
                    </span>
                    <button
                      type="button"
                      onClick={handleCompactMemory}
                      className="font-medium whitespace-nowrap shrink-0"
                      style={{ color: activeAccent.hex }}
                    >
                      Compact
                    </button>
                  </div>

                  {rollingSummary && (
                    <div className="rounded border border-emerald-500/30 bg-emerald-950/20 px-2.5 py-1.5 text-[10px] text-emerald-200">
                      <div className="font-semibold text-emerald-300">Rolling Summary (Persisted in Room)</div>
                      <p className="mt-0.5 whitespace-pre-line text-zinc-300">{rollingSummary}</p>
                    </div>
                  )}

                  <div className="flex-1 space-y-2.5 overflow-y-auto pr-1">
                    {messages.length === 0 ? (
                      <p className="py-12 text-center text-xs text-zinc-500">
                        Chat history cleared. Start a new SSE session.
                      </p>
                    ) : (
                      messages.map((m) => {
                        const codeMatch = m.content.match(/```(\w*)\n([\s\S]*?)```/);
                        const leadText = codeMatch
                          ? m.content.slice(0, codeMatch.index).trim()
                          : m.content;
                        const codeLang = codeMatch?.[1] || 'code';
                        const codeBody = codeMatch?.[2]?.trimEnd() || '';
                        const isSpeaking = speakingMsgId === m.id;
                        return (
                          <div
                            key={m.id}
                            className={`rounded-lg border p-2.5 ${
                              m.role === 'user' ? 'bg-zinc-900/90 text-zinc-100' : 'border-zinc-800 bg-[#0B0E14] text-zinc-200'
                            }`}
                            style={{
                              borderColor: m.role === 'user' ? `${activeAccent.hex}66` : undefined,
                              fontSize: `${chatFontSize}px`,
                            }}
                          >
                            <div className="mb-1 flex items-center justify-between gap-2 text-[10px] text-zinc-400">
                              <span style={m.role === 'user' ? { color: activeAccent.hex } : undefined}>
                                {m.role === 'user' ? 'You' : 'OmniAI'}
                              </span>
                              <div className="flex items-center gap-1.5 min-w-0">
                                {m.provider && <span className="truncate font-mono">{m.provider}</span>}
                                {m.role === 'assistant' && (
                                  <>
                                    <button
                                      type="button"
                                      aria-label={`Translate message ${m.id} side-by-side`}
                                      onClick={() => toggleTranslateKey(`msg_${m.id}`, m.content)}
                                      className="rounded p-0.5 hover:bg-zinc-800"
                                      style={{ color: translatedKeys[`msg_${m.id}`] ? '#00E676' : activeAccent.hex }}
                                    >
                                      <Languages className="h-3.5 w-3.5" />
                                    </button>
                                    <button
                                      type="button"
                                      aria-label={isSpeaking ? 'Stop TTS read aloud' : 'Read message aloud via offline TTS'}
                                      onClick={() => {
                                        if (isSpeaking) {
                                          setSpeakingMsgId(null);
                                        } else {
                                          setSpeakingMsgId(m.id);
                                          triggerToast(`Offline TTS speaking (${ttsSpeed.toFixed(2)}x EN/HI auto-detect)`);
                                        }
                                      }}
                                      className="rounded p-0.5 hover:bg-zinc-800"
                                      style={{ color: isSpeaking ? '#F59E0B' : activeAccent.hex }}
                                    >
                                      {isSpeaking ? <Square className="h-3 w-3" /> : <Volume2 className="h-3.5 w-3.5" />}
                                    </button>
                                  </>
                                )}
                              </div>
                            </div>
                            {translatedKeys[`msg_${m.id}`] ? (
                              <div className="grid grid-cols-2 gap-1.5 text-[11px]">
                                <div className="rounded border border-zinc-800 bg-black p-1.5">
                                  <div className="mb-0.5 text-[9px] text-zinc-400">Original</div>
                                  <p className="whitespace-pre-wrap leading-4">{leadText}</p>
                                </div>
                                <div className="rounded border border-emerald-500/40 bg-emerald-950/20 p-1.5">
                                  <div className="mb-0.5 text-[9px] text-emerald-300">
                                    {translatedKeys[`msg_${m.id}`].lang}
                                  </div>
                                  <p className="whitespace-pre-wrap leading-4 text-zinc-100">
                                    {translatedKeys[`msg_${m.id}`].text}
                                  </p>
                                </div>
                              </div>
                            ) : (
                              <>
                                {leadText && (
                                  <p className="whitespace-pre-wrap leading-5">{leadText}</p>
                                )}
                                {codeBody && (
                                  <div className="mt-2 overflow-hidden rounded border border-zinc-800 bg-black">
                                    <div className="flex items-center justify-between border-b border-zinc-900 px-2.5 py-1 text-[10px]">
                                      <span className="font-mono" style={{ color: activeAccent.hex }}>
                                        {codeLang}
                                      </span>
                                      <button
                                        type="button"
                                        onClick={() => {
                                          navigator.clipboard?.writeText(codeBody).catch(() => {});
                                        }}
                                        className="font-medium text-zinc-400 hover:text-zinc-100"
                                      >
                                        Copy
                                      </button>
                                    </div>
                                    <pre className="overflow-x-auto p-2.5 font-mono text-[11px] leading-5 text-zinc-200">
                                      {codeBody}
                                    </pre>
                                  </div>
                                )}
                              </>
                            )}
                          </div>
                        );
                      })
                    )}
                  </div>

                  <div className="space-y-2 border-t border-zinc-900 pt-2">
                    {attachedDoc && (
                      <div className="flex items-center justify-between rounded border border-emerald-500/40 bg-emerald-950/20 px-2 py-1 text-[10px] text-emerald-300">
                        <span className="truncate">
                          Attached: {attachedDoc.name} ({attachedDoc.pages}p · {attachedDoc.chunks} chunks via PdfRenderer + ML Kit)
                        </span>
                        <button
                          type="button"
                          aria-label="Remove attached document"
                          onClick={() => setAttachedDoc(null)}
                          className="ml-2 text-zinc-400 hover:text-white"
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </div>
                    )}
                    <div className="flex items-center justify-between gap-1 text-[10px] text-zinc-400">
                      <div className="flex gap-1">
                        {(['EN', 'HI'] as const).map((lang) => (
                          <button
                            key={lang}
                            type="button"
                            onClick={() => setVoiceLang(lang)}
                            className="rounded px-1.5 py-0.5 font-medium"
                            style={{
                              backgroundColor: voiceLang === lang ? `${activeAccent.hex}30` : '#18181b',
                              color: voiceLang === lang ? activeAccent.hex : '#a1a1aa',
                            }}
                          >
                            {lang}
                          </button>
                        ))}
                      </div>
                      <div className="flex gap-1">
                        <button
                          type="button"
                          onClick={() => {
                            setAttachedDoc({ name: 'Architecture_Spec.pdf', pages: 4, chunks: 2 });
                            triggerToast('Offline PdfRenderer + ML Kit OCR extracted 4 PDF pages.');
                          }}
                          className="inline-flex items-center gap-1 rounded border border-zinc-800 bg-zinc-950 px-2 py-0.5 text-zinc-200 hover:bg-zinc-900"
                        >
                          <Paperclip className="h-3 w-3" style={{ color: activeAccent.hex }} />
                          <span>Attach File</span>
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setChatInput('Read & summarize https://blog.omniai.dev/wan21-dual-gpu (Jsoup clean article extracted)');
                            triggerToast('Jsoup Web Reader stripped HTML boilerplate cleanly.');
                          }}
                          className="inline-flex items-center gap-1 rounded border border-zinc-800 bg-zinc-950 px-2 py-0.5 text-zinc-200 hover:bg-zinc-900"
                        >
                          <Globe className="h-3 w-3" style={{ color: activeAccent.hex }} />
                          <span>Read URL</span>
                        </button>
                      </div>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <button
                        type="button"
                        aria-label="Scan image with offline OCR"
                        onClick={() => {
                          setChatInput(
                            voiceLang === 'HI'
                              ? 'OCR स्कैन: भारत में AI और क्वांटम कंप्यूटिंग का भविष्य'
                              : 'OCR Scan: Explain AndroidX Media3 ClippingConfiguration pipeline'
                          );
                          triggerToast('ML Kit Offline OCR extracted text (Hindi + English).');
                        }}
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-800 bg-zinc-950 hover:bg-zinc-900"
                        style={{ color: activeAccent.hex }}
                      >
                        <ScanText className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        aria-label="Voice input dictation"
                        onClick={() =>
                          setChatInput(
                            voiceLang === 'HI'
                              ? 'Dual GPU Kaggle worker ka architecture samjhao'
                              : 'Explain PBKDF2 + AES-256-GCM .omniai backup format'
                          )
                        }
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md border border-zinc-800 bg-zinc-950 hover:bg-zinc-900"
                        style={{ color: activeAccent.hex }}
                      >
                        <Mic className="h-4 w-4" />
                      </button>
                      <input
                        type="text"
                        value={chatInput}
                        onChange={(e) => setChatInput(e.target.value)}
                        onKeyDown={(e) => e.key === 'Enter' && handleSendChat()}
                        placeholder="Message OmniAI..."
                        aria-label="Chat prompt input"
                        className="h-9 flex-1 rounded-md border border-zinc-800 bg-[#0B0E14] px-2.5 text-xs text-zinc-100 placeholder:text-zinc-500 focus:outline-none"
                      />
                      <button
                        type="button"
                        aria-label="Send chat message"
                        onClick={handleSendChat}
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md text-zinc-950"
                        style={{ backgroundColor: activeAccent.hex }}
                      >
                        <Send className="h-4 w-4" />
                      </button>
                    </div>
                  </div>
                </div>
              )}

              {activeTab === 'director' && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-semibold">Automated Video Director</h2>
                    <button
                      type="button"
                      onClick={() => setWeeklyGpuHours((h) => (h < 28 ? 28.6 : 11.4))}
                      className="rounded border border-zinc-800 bg-zinc-950 px-2 py-0.5 font-mono text-[10px] text-zinc-300"
                    >
                      {weeklyGpuHours < 28 ? 'Sim Low Quota' : 'Reset Quota'}
                    </button>
                  </div>

                  {/* Kaggle 30-Hr Weekly GPU Quota Meter */}
                  <div
                    className="space-y-1 rounded-lg border bg-[#0B0E14] p-2 text-[11px]"
                    style={{ borderColor: 30 - weeklyGpuHours < 2 ? '#FF5252' : '#27272a' }}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-medium text-zinc-200">Kaggle Weekly GPU Quota</span>
                      <span
                        className="font-mono text-[10px]"
                        style={{ color: 30 - weeklyGpuHours < 2 ? '#FF5252' : activeAccent.hex }}
                      >
                        Used: {weeklyGpuHours.toFixed(1)} hrs / 30.0 hrs — Resets weekly
                      </span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded bg-zinc-900">
                      <div
                        className="h-full"
                        style={{
                          width: `${Math.min(100, (weeklyGpuHours / 30) * 100)}%`,
                          backgroundColor: 30 - weeklyGpuHours < 2 ? '#FF5252' : activeAccent.hex,
                        }}
                      />
                    </div>
                    {30 - weeklyGpuHours < 2 && (
                      <div className="flex items-center gap-1 text-[10px] text-red-400">
                        <AlertTriangle className="h-3 w-3 shrink-0" />
                        <span>Low quota warning: {(30 - weeklyGpuHours).toFixed(1)} hrs left!</span>
                      </div>
                    )}
                  </div>

                  <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                    {/* Lock Character DNA Dropdown */}
                    <div className="flex items-center gap-1.5 text-[11px]">
                      <UserCheck className="h-3.5 w-3.5 shrink-0" style={{ color: activeAccent.hex }} />
                      <select
                        aria-label="Lock Character DNA"
                        value={selectedCharacterId}
                        onChange={(e) => {
                          const id = e.target.value;
                          setSelectedCharacterId(id);
                          const found = characterProfiles.find((c) => c.id === id);
                          if (found) {
                            setVisualStyle(found.defaultStyle);
                            setScenes((prev) =>
                              prev.map((s) =>
                                s.visualPrompt.includes('[Character DNA:')
                                  ? s
                                  : { ...s, visualPrompt: `[Character DNA: ${found.appearancePrompt}] ${s.visualPrompt}` }
                              )
                            );
                            triggerToast(`Locked Character DNA '${found.name}' across all scenes.`);
                          }
                        }}
                        className="flex-1 rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-100 focus:outline-none"
                      >
                        <option value="">Lock Character DNA (None)</option>
                        {characterProfiles.map((c) => (
                          <option key={c.id} value={c.id}>
                            DNA: {c.name} ({c.defaultStyle})
                          </option>
                        ))}
                      </select>
                    </div>
                    {selectedCharacter && (
                      <div className="rounded border border-emerald-500/30 bg-emerald-950/20 px-2 py-1 text-[10px] text-emerald-300">
                        Injected into every scene: {selectedCharacter.appearancePrompt}
                      </div>
                    )}

                    <div className="flex items-center gap-1.5">
                      <input
                        type="text"
                        value={topic}
                        onChange={(e) => setTopic(e.target.value)}
                        aria-label="Video topic"
                        className="flex-1 rounded border border-zinc-800 bg-black px-2.5 py-1.5 text-xs text-zinc-100 focus:outline-none"
                      />
                      <button
                        type="button"
                        aria-label="Read Web URL into video topic"
                        onClick={() => {
                          setTopic('Jsoup Article: Dual T4 GPU Sharding for 1.3B Video Models');
                          triggerToast('Jsoup Web Reader loaded clean article into Topic.');
                        }}
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-zinc-800 bg-black hover:bg-zinc-900"
                        style={{ color: activeAccent.hex }}
                      >
                        <Globe className="h-3.5 w-3.5" />
                      </button>
                      <button
                        type="button"
                        aria-label="Scan image OCR into video topic"
                        onClick={() => {
                          setTopic('Scanned Notes: 5 Breakthroughs in Local Edge AI');
                          triggerToast('Offline OCR populated Video Topic.');
                        }}
                        className="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-zinc-800 bg-black hover:bg-zinc-900"
                        style={{ color: activeAccent.hex }}
                      >
                        <ScanText className="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <div className="flex items-center justify-between gap-2 text-[11px]">
                      <div className="flex gap-1">
                        {(['9:16', '16:9'] as const).map((r) => (
                          <button
                            key={r}
                            type="button"
                            onClick={() => setAspectRatio(r)}
                            className="rounded px-2 py-1 font-mono"
                            style={{
                              backgroundColor: aspectRatio === r ? `${activeAccent.hex}30` : '#18181b',
                              color: aspectRatio === r ? activeAccent.hex : '#a1a1aa',
                            }}
                          >
                            {r}
                          </button>
                        ))}
                      </div>
                      <button
                        type="button"
                        onClick={() => setBurnSubtitles((v) => !v)}
                        className="rounded px-2 py-1"
                        style={{
                          backgroundColor: burnSubtitles ? `${activeAccent.hex}30` : '#18181b',
                          color: burnSubtitles ? activeAccent.hex : '#a1a1aa',
                        }}
                      >
                        Subtitles: {burnSubtitles ? 'ON' : 'OFF'}
                      </button>
                    </div>
                    <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                      <div>
                        <label htmlFor="director-style" className="mb-0.5 block text-zinc-400">
                          Visual Style
                        </label>
                        <select
                          id="director-style"
                          value={visualStyle}
                          onChange={(e) => setVisualStyle(e.target.value as VisualStyleOption)}
                          className="w-full rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-200 focus:outline-none"
                        >
                          {VISUAL_STYLE_OPTIONS.map((style) => (
                            <option key={style} value={style}>
                              {style}
                            </option>
                          ))}
                        </select>
                      </div>
                      <div>
                        <label htmlFor="director-voice" className="mb-0.5 block text-zinc-400">
                          Voiceover
                        </label>
                        <select
                          id="director-voice"
                          value={voiceover}
                          onChange={(e) => setVoiceover(e.target.value as VoiceoverOption)}
                          className="w-full rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-200 focus:outline-none"
                        >
                          {VOICEOVER_OPTIONS.map((v) => (
                            <option key={v} value={v}>
                              {v}
                            </option>
                          ))}
                        </select>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-1.5 text-[10px]">
                      <input
                        type="text"
                        value={watermarkText}
                        onChange={(e) => setWatermarkText(e.target.value)}
                        placeholder="Watermark text"
                        aria-label="Watermark text"
                        className="rounded border border-zinc-800 bg-black px-2 py-1 text-zinc-200 focus:outline-none"
                      />
                      <input
                        type="text"
                        value={bgmUrl}
                        onChange={(e) => setBgmUrl(e.target.value)}
                        placeholder="BGM URL (15% ducked)"
                        aria-label="Background music URL"
                        className="rounded border border-zinc-800 bg-black px-2 py-1 text-zinc-200 focus:outline-none"
                      />
                    </div>
                    <button
                      type="button"
                      onClick={() => setTopic((t) => `${t} — ${visualStyle}, 24fps Wan 2.1 motion`)}
                      className="flex w-full items-center justify-center gap-1.5 rounded border border-zinc-700 bg-zinc-900 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-800"
                    >
                      <Sparkles className="h-3.5 w-3.5" style={{ color: activeAccent.hex }} />
                      <span>Enhance Prompt</span>
                    </button>
                  </div>

                  {/* Templates Library + Admin Studio Section */}
                  <TemplateAdminPanel
                    builtinTemplates={BUILTIN_VIDEO_TEMPLATES}
                    customTemplates={customTemplates}
                    onApplyTemplate={handleApplyTemplate}
                    onSaveCustomTemplate={handleSaveCustomTemplate}
                    onDeleteCustomTemplate={handleDeleteCustomTemplate}
                  />

                  <div className="space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="font-medium text-zinc-300">Scene Editor ({scenes.length})</span>
                      <button
                        type="button"
                        onClick={() =>
                          setScenes((prev) => [
                            ...prev,
                            {
                              id: Date.now(),
                              visualPrompt: 'Wide tracking shot across obsidian glass lab',
                              narration: `Scene ${prev.length + 1} voiceover script.`,
                            },
                          ])
                        }
                        style={{ color: activeAccent.hex }}
                      >
                        + Add Scene
                      </button>
                    </div>
                    {scenes.map((sc, idx) => (
                      <div key={sc.id} className="rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                        <div className="mb-1 flex items-center justify-between">
                          <span className="font-mono text-[11px]" style={{ color: activeAccent.hex }}>
                            Scene {idx + 1}
                          </span>
                          <div className="flex items-center gap-1">
                            <button
                              type="button"
                              aria-label={`Translate scene ${idx + 1} narration`}
                              onClick={() => toggleTranslateKey(`scene_${sc.id}`, sc.narration)}
                              className="p-1 hover:text-zinc-100"
                              style={{ color: translatedKeys[`scene_${sc.id}`] ? '#00E676' : activeAccent.hex }}
                            >
                              <Languages className="h-3.5 w-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Move scene ${idx + 1} up`}
                              disabled={idx === 0}
                              onClick={() => moveScene(idx, -1)}
                              className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                            >
                              <ArrowUp className="h-3.5 w-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Move scene ${idx + 1} down`}
                              disabled={idx === scenes.length - 1}
                              onClick={() => moveScene(idx, 1)}
                              className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                            >
                              <ArrowDown className="h-3.5 w-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Delete scene ${idx + 1}`}
                              onClick={() => setScenes((prev) => prev.filter((_, i) => i !== idx))}
                              className="p-1 text-red-400 hover:text-red-300"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        </div>
                        <p className="text-zinc-200">{sc.visualPrompt}</p>
                        <p className="mt-1 text-[11px] text-zinc-400">VO: {sc.narration}</p>
                        {translatedKeys[`scene_${sc.id}`] && (
                          <div className="mt-1.5 grid grid-cols-2 gap-1.5 rounded border border-emerald-500/30 bg-black p-1.5 text-[10px]">
                            <div>
                              <span className="text-zinc-500">Original: </span>
                              <span className="text-zinc-300">{sc.narration}</span>
                            </div>
                            <div>
                              <span className="text-emerald-400">{translatedKeys[`scene_${sc.id}`].lang}: </span>
                              <span className="text-zinc-100">{translatedKeys[`scene_${sc.id}`].text}</span>
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>

                  <button
                    type="button"
                    onClick={enqueueBatch}
                    disabled={scenes.length === 0}
                    className="w-full rounded-md py-2 text-xs font-semibold text-zinc-950 disabled:opacity-40"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    Queue Background Render (Kaggle T4 x2)
                  </button>

                  <div className="space-y-1.5 border-t border-zinc-900 pt-2">
                    <div className="text-[11px] font-medium text-zinc-400">
                      ForegroundService Batch Queue ({batchQueue.length})
                    </div>
                    {batchQueue.map((job) => (
                      <div key={job.id} className="rounded border border-zinc-800 bg-[#0B0E14] p-2 text-xs">
                        <div className="flex justify-between">
                          <span className="font-medium text-zinc-200">{job.title}</span>
                          <span className="font-mono tabular-nums" style={{ color: activeAccent.hex }}>
                            {job.progress}%
                          </span>
                        </div>
                        <div className="mt-0.5 text-[11px] text-zinc-400">{job.status}</div>
                        <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded bg-zinc-900">
                          <div className="h-full" style={{ width: `${job.progress}%`, backgroundColor: activeAccent.hex }} />
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {activeTab === 'merger' && (
                <div className="space-y-3">
                  <div>
                    <h2 className="text-sm font-semibold">Media3 On-Device Merger & Trimmer</h2>
                    <p className="text-[11px] text-zinc-400">
                      Media3 Transformer · ClippingConfiguration Start/End (sec)
                    </p>
                  </div>

                  <div className="space-y-2">
                    {clips.map((clip, idx) => (
                      <div
                        key={clip.id}
                        className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] px-2.5 py-2 text-xs"
                      >
                        <div className="flex items-center justify-between">
                          <div className="min-w-0">
                            <div className="truncate font-mono text-zinc-200">
                              #{idx + 1} {clip.name}
                            </div>
                            <div className="text-[10px] text-zinc-500 tabular-nums">{clip.duration}</div>
                          </div>
                          <div className="flex items-center gap-1 shrink-0">
                            <button
                              type="button"
                              aria-label={`Move clip ${idx + 1} up`}
                              disabled={idx === 0}
                              onClick={() => moveClip(idx, -1)}
                              className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                            >
                              <ArrowUp className="h-3.5 w-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Move clip ${idx + 1} down`}
                              disabled={idx === clips.length - 1}
                              onClick={() => moveClip(idx, 1)}
                              className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                            >
                              <ArrowDown className="h-3.5 w-3.5" />
                            </button>
                            <button
                              type="button"
                              aria-label={`Remove clip ${clip.name}`}
                              onClick={() => setClips((prev) => prev.filter((_, i) => i !== idx))}
                              className="p-1 text-red-400 hover:text-red-300"
                            >
                              <X className="h-3.5 w-3.5" />
                            </button>
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-2 text-[10px]">
                          <label className="flex items-center gap-1.5 rounded border border-zinc-800 bg-black px-2 py-1">
                            <span className="text-zinc-500">Start(s)</span>
                            <input
                              type="text"
                              value={clip.startSec}
                              onChange={(e) => updateClipTrim(idx, 'startSec', e.target.value)}
                              placeholder="0.0"
                              aria-label={`Clip ${idx + 1} start seconds`}
                              className="w-full bg-transparent font-mono text-zinc-100 focus:outline-none"
                            />
                          </label>
                          <label className="flex items-center gap-1.5 rounded border border-zinc-800 bg-black px-2 py-1">
                            <span className="text-zinc-500">End(s)</span>
                            <input
                              type="text"
                              value={clip.endSec}
                              onChange={(e) => updateClipTrim(idx, 'endSec', e.target.value)}
                              placeholder="Full"
                              aria-label={`Clip ${idx + 1} end seconds`}
                              className="w-full bg-transparent font-mono text-zinc-100 focus:outline-none"
                            />
                          </label>
                        </div>
                      </div>
                    ))}
                  </div>

                  <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                    <label className="flex items-center justify-between">
                      <span>Mute Original Clip Audio</span>
                      <input
                        type="checkbox"
                        checked={muteOriginal}
                        onChange={(e) => setMuteOriginal(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <div className="flex items-center justify-between">
                      <span className="text-zinc-400">BG Track</span>
                      <button
                        type="button"
                        onClick={() => setBgTrack((t) => (t ? null : 'midnight_synth_loop.m4a'))}
                        className="font-mono text-[11px]"
                        style={{ color: activeAccent.hex }}
                      >
                        {bgTrack ?? '+ Attach .m4a'}
                      </button>
                    </div>
                    <input
                      type="text"
                      value={mergeName}
                      onChange={(e) => setMergeName(e.target.value)}
                      aria-label="Merged output filename"
                      className="w-full rounded border border-zinc-800 bg-black px-2.5 py-1.5 font-mono text-xs text-zinc-100 focus:outline-none"
                    />
                    {mergeProgress > 0 && (
                      <div className="space-y-1">
                        <div className="h-1.5 w-full overflow-hidden rounded bg-zinc-900">
                          <div className="h-full" style={{ width: `${mergeProgress}%`, backgroundColor: activeAccent.hex }} />
                        </div>
                        <div className="font-mono text-[11px]" style={{ color: activeAccent.hex }}>
                          {mergeStatus}
                        </div>
                      </div>
                    )}
                    <button
                      type="button"
                      onClick={runMergePreview}
                      disabled={clips.length < 2}
                      className="w-full rounded-md py-2 font-semibold text-zinc-950 disabled:opacity-40"
                      style={{ backgroundColor: activeAccent.hex }}
                    >
                      Merge {clips.length} Clips On-Device
                    </button>
                  </div>
                </div>
              )}

              {activeTab === 'gallery' && (
                <div className="space-y-3">
                  <h2 className="text-sm font-semibold">Downloads & Offline Gallery</h2>

                  <div className="rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                    <div className="flex items-center justify-between">
                      <span className="text-zinc-400">Temp & OTA Cache · DB 1.4 MB</span>
                      <span className="font-mono text-zinc-200 tabular-nums">{cacheMb.toFixed(1)} MB</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setCacheMb(0)}
                      className="mt-2 w-full rounded border border-zinc-700 bg-zinc-900 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-800"
                    >
                      1-Tap Clean Temp Cache
                    </button>
                  </div>

                  {previewVideo && (
                    <div className="rounded-lg border border-emerald-500/40 bg-[#0B0E14] p-3 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="font-mono text-emerald-300">ExoPlayer · {previewVideo}</span>
                        <button
                          type="button"
                          aria-label="Close ExoPlayer preview"
                          onClick={() => setPreviewVideo(null)}
                          className="text-zinc-400 hover:text-zinc-100"
                        >
                          <X className="h-4 w-4" />
                        </button>
                      </div>
                      <div className="mt-2 flex h-28 items-center justify-center rounded bg-black font-mono text-[11px] text-zinc-400">
                        Playing local H.264/AAC MP4 offline (Media3 ExoPlayer 1.2.1)
                      </div>
                    </div>
                  )}

                  <div className="space-y-2">
                    {downloads.map((d) => (
                      <div key={d.id} className="rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="truncate font-medium text-zinc-100">{d.name}</div>
                            <div className="mt-0.5 font-mono text-[11px] text-zinc-400 tabular-nums">
                              {d.category} · {d.status} · {d.sizeMb} MB
                            </div>
                          </div>
                          <button
                            type="button"
                            aria-label={`Delete ${d.name}`}
                            onClick={() => setDownloads((prev) => prev.filter((item) => item.id !== d.id))}
                            className="text-red-400 hover:text-red-300"
                          >
                            <Trash2 className="h-3.5 w-3.5" />
                          </button>
                        </div>
                        <div className="mt-2 flex gap-1.5">
                          {d.status === 'COMPLETED' ? (
                            <>
                              <button
                                type="button"
                                onClick={() => setPreviewVideo(d.name)}
                                className="flex flex-1 items-center justify-center gap-1 rounded py-1 font-medium text-zinc-950"
                                style={{ backgroundColor: activeAccent.hex }}
                              >
                                <Play className="h-3 w-3" />
                                <span>ExoPlayer</span>
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setDownloads((prev) =>
                                    prev.map((item) =>
                                      item.id === d.id ? { ...item, thumbSaved: true } : item
                                    )
                                  );
                                  triggerToast(`Saved HD Thumbnail to Pictures/OmniAIStudio/`);
                                }}
                                className="inline-flex items-center gap-1 rounded border border-zinc-700 px-2 py-1 text-zinc-200 hover:bg-zinc-800"
                              >
                                <Image className="h-3 w-3" />
                                <span>{d.thumbSaved ? 'Saved' : 'HD Thumb'}</span>
                              </button>
                              <button
                                type="button"
                                onClick={() =>
                                  setDownloads((prev) =>
                                    prev.map((item) =>
                                      item.id === d.id ? { ...item, savedToGallery: true } : item
                                    )
                                  )
                                }
                                className="rounded border border-zinc-700 px-2 py-1 text-zinc-200 hover:bg-zinc-800"
                              >
                                {d.savedToGallery ? 'Saved' : 'Gallery'}
                              </button>
                            </>
                          ) : (
                            <button
                              type="button"
                              onClick={() =>
                                setDownloads((prev) =>
                                  prev.map((item) =>
                                    item.id === d.id ? { ...item, status: 'COMPLETED' } : item
                                  )
                                )
                              }
                              className="flex w-full items-center justify-center gap-1 rounded border border-amber-500/40 bg-amber-500/10 py-1 text-amber-300"
                            >
                              <RefreshCw className="h-3 w-3" />
                              <span>Retry Download</span>
                            </button>
                          )}
                        </div>
                        {d.status === 'COMPLETED' && (
                          <div className="mt-2 border-t border-zinc-800/80 pt-1.5">
                            <button
                              type="button"
                              onClick={() =>
                                setExpandedKitIds((prev) =>
                                  prev.includes(d.id) ? prev.filter((id) => id !== d.id) : [...prev, d.id]
                                )
                              }
                              className="flex w-full items-center justify-between text-[10px] font-medium"
                              style={{ color: activeAccent.hex }}
                            >
                              <span>Publishing Kit (Social Media SEO)</span>
                              {expandedKitIds.includes(d.id) ? (
                                <ChevronUp className="h-3.5 w-3.5" />
                              ) : (
                                <ChevronDown className="h-3.5 w-3.5" />
                              )}
                            </button>
                            {expandedKitIds.includes(d.id) && (
                              <div className="mt-1.5 space-y-1.5 rounded border border-zinc-800 bg-black p-2 text-[10px]">
                                <div className="font-medium text-zinc-100">
                                  Title: {d.name.replace(/_/g, ' ').replace('.mp4', '')} — Edge AI Breakdown
                                </div>
                                <div className="text-zinc-400">
                                  Rendered locally with Dual-GPU Wan 2.1 + Edge-TTS. #OmniAI #AIVideo #Shorts #Wan21
                                </div>
                                <div className="grid grid-cols-3 gap-1">
                                  <button
                                    type="button"
                                    onClick={() => triggerToast('Copied Viral Title to clipboard.')}
                                    className="inline-flex items-center justify-center gap-1 rounded border border-zinc-700 py-1 text-zinc-200 hover:bg-zinc-900"
                                  >
                                    <Copy className="h-2.5 w-2.5" />
                                    <span>Title</span>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => triggerToast('Copied Description + Hashtags.')}
                                    className="inline-flex items-center justify-center gap-1 rounded border border-zinc-700 py-1 text-zinc-200 hover:bg-zinc-900"
                                  >
                                    <Copy className="h-2.5 w-2.5" />
                                    <span>Desc+Tags</span>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => triggerToast('Opened Android Share Sheet via FileProvider.')}
                                    className="inline-flex items-center justify-center gap-1 rounded py-1 font-semibold text-zinc-950"
                                    style={{ backgroundColor: activeAccent.hex }}
                                  >
                                    <Share2 className="h-2.5 w-2.5" />
                                    <span>Share</span>
                                  </button>
                                </div>
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {activeTab === 'vault' && (
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <h2 className="text-sm font-semibold">Vault & Security</h2>
                    <button
                      type="button"
                      onClick={() => setKeysRevealed((v) => !v)}
                      className="inline-flex items-center gap-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-1 text-[11px] text-zinc-200"
                    >
                      {keysRevealed ? <EyeOff className="h-3 w-3" /> : <Eye className="h-3 w-3" />}
                      <span>{keysRevealed ? 'Mask Keys' : 'Biometric Reveal'}</span>
                    </button>
                  </div>

                  {wipedNotice && (
                    <div className="rounded border border-red-500/40 bg-red-950/30 p-2 text-[11px] text-red-200">
                      {wipedNotice}
                    </div>
                  )}

                  {/* Quick Provider Preset Chips & Localhost Auto-Scanner */}
                  <div className="space-y-1.5 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5">
                    <div className="flex items-center justify-between">
                      <span className="text-[10px] font-medium text-zinc-400">Quick Provider Presets</span>
                      <button
                        type="button"
                        onClick={() => {
                          setLocalhostScanResults([
                            { port: 8080, url: 'http://127.0.0.1:8080', hint: 'llama.cpp / Termux', online: true, model: 'dolphin-mistral-7b.gguf', ms: 14 },
                            { port: 11434, url: 'http://127.0.0.1:11434', hint: 'Ollama OpenAI API', online: true, model: 'qwen2.5:14b', ms: 22 },
                            { port: 8001, url: 'http://127.0.0.1:8001', hint: 'vLLM / FastChat', online: false, model: '', ms: 1000 },
                            { port: 5000, url: 'http://127.0.0.1:5000', hint: 'KoboldCpp', online: false, model: '', ms: 1000 },
                          ]);
                          triggerToast('Scanned 127.0.0.1 ports 8080, 11434, 8001, 5000 (1s timeout).');
                        }}
                        className="inline-flex items-center gap-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-0.5 text-[10px] font-medium text-zinc-200 hover:bg-zinc-800"
                      >
                        <Radar className="h-3 w-3" style={{ color: activeAccent.hex }} />
                        <span>Scan Localhost</span>
                      </button>
                    </div>
                    {localhostScanResults.length > 0 && (
                      <div className="space-y-1 rounded border border-zinc-800 bg-black p-1.5 text-[10px]">
                        {localhostScanResults.map((cand) => (
                          <div key={cand.port} className="flex items-center justify-between gap-1">
                            <span className="truncate font-mono text-zinc-300">
                              :{cand.port} {cand.online ? `ONLINE (${cand.ms}ms · ${cand.model})` : 'OFFLINE'}
                            </span>
                            {cand.online && (
                              <button
                                type="button"
                                onClick={() => {
                                  setPrimaryUrl(cand.url);
                                  setPrimaryModel(cand.model);
                                  triggerToast(`Auto-filled local server ${cand.url}`);
                                }}
                                className="rounded px-1.5 py-0.5 font-semibold text-zinc-950"
                                style={{ backgroundColor: activeAccent.hex }}
                              >
                                Use
                              </button>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                    <div className="flex flex-wrap gap-1">
                      {QUICK_PROVIDER_PRESETS.map((preset) => {
                        const active = primaryUrl === preset.url;
                        return (
                          <button
                            key={preset.label}
                            type="button"
                            onClick={() => {
                              setPrimaryUrl(preset.url);
                              setPrimaryModel(preset.model);
                              triggerToast(`Preset applied: ${preset.label}`);
                            }}
                            className="rounded px-2 py-1 text-[10px] font-medium transition-colors"
                            style={{
                              backgroundColor: active ? `${activeAccent.hex}30` : '#18181b',
                              color: active ? activeAccent.hex : '#a1a1aa',
                            }}
                          >
                            {preset.label}
                          </button>
                        );
                      })}
                    </div>
                    <div className="truncate font-mono text-[10px] text-zinc-300">
                      {primaryUrl} · {primaryModel}
                    </div>
                  </div>

                  <div className="space-y-1.5 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 font-mono text-[11px]">
                    <div className="flex justify-between">
                      <span className="text-zinc-400">Primary Key</span>
                      <span className="text-zinc-100">{keysRevealed ? 'sk-tunnel-live-9f82' : '••••9f82'}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-400">HF Token</span>
                      <span className="text-zinc-100">{keysRevealed ? 'hf_OmNiAi_7741' : '••••7741'}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-400">Gemini Key</span>
                      <span className="text-zinc-100">{keysRevealed ? 'AIzaSyD-omni-4410' : '••••4410'}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-zinc-400">Kaggle Key</span>
                      <span className="text-zinc-100">{keysRevealed ? 'kg_dual_t4_2819' : '••••2819'}</span>
                    </div>
                    <div className="flex flex-wrap gap-1.5 border-t border-zinc-800 pt-2">
                      {(
                        [
                          { id: 'colab', label: '? Colab/Termux' },
                          { id: 'hf', label: '? HF' },
                          { id: 'gemini', label: '? Gemini' },
                          { id: 'kaggle', label: '? Kaggle' },
                          { id: 'github', label: '? GitHub' },
                        ] as const
                      ).map((g) => (
                        <button
                          key={g.id}
                          type="button"
                          onClick={() => setActiveGuideKey(g.id)}
                          className="rounded-full border border-emerald-500/35 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium text-emerald-300 hover:bg-emerald-500/20"
                        >
                          {g.label}
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* v5.2 Telegram Bot & 2 GB Direct Kaggle Upload Card */}
                  <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-zinc-100">Telegram 2 GB Direct Upload</span>
                      <button
                        type="button"
                        onClick={() => setActiveGuideKey('telegram')}
                        className="rounded-full border border-emerald-500/40 bg-emerald-500/10 px-2 py-0.5 text-[10px] font-semibold text-emerald-300 hover:bg-emerald-500/20"
                      >
                        ? Setup Guide &amp; Commands
                      </button>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <input
                        type="text"
                        value={simTgChatId}
                        onChange={(e) => setSimTgChatId(e.target.value)}
                        placeholder="TG_CHAT_ID (-100...)"
                        className="flex-1 rounded border border-zinc-800 bg-black px-2 py-1 font-mono text-[11px] text-zinc-100"
                      />
                      <button
                        type="button"
                        onClick={() => {
                          setSimTgChatId('-1002389412055');
                          triggerToast('Auto-detected Chat ID -1002389412055 (@OmniAI_Archive) via /getUpdates');
                        }}
                        className="rounded border border-emerald-500/40 bg-emerald-500/15 px-2 py-1 text-[10px] font-semibold text-emerald-300 hover:bg-emerald-500/25"
                      >
                        Auto-Detect
                      </button>
                    </div>
                    <label className="flex items-center justify-between text-[11px]">
                      <span>Direct Kaggle → Telegram (2 GB MTProto)</span>
                      <input
                        type="checkbox"
                        checked={directTelegramUpload}
                        onChange={(e) => setDirectTelegramUpload(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <label className="flex items-center justify-between text-[11px]">
                      <span>Save Mobile Data (Skip Phone Download)</span>
                      <input
                        type="checkbox"
                        checked={skipPhoneDownload}
                        disabled={!directTelegramUpload}
                        onChange={(e) => setSkipPhoneDownload(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        triggerToast('Copied Kaggle Secrets Setup (TG_BOT_TOKEN, TG_CHAT_ID, TG_API_ID, TG_API_HASH)')
                      }
                      className="w-full rounded border border-zinc-700 bg-zinc-900 py-1 text-[11px] font-medium text-zinc-200 hover:bg-zinc-800"
                    >
                      Copy Kaggle Secrets Setup
                    </button>
                  </div>

                  {/* v5.3 YouTube Data API v3 & Meta Reels Auto-Uploader Card */}
                  <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold text-zinc-100">YouTube API v3 &amp; Meta Reels</span>
                      <div className="flex items-center gap-1">
                        <button
                          type="button"
                          onClick={() => setActiveGuideKey('youtube')}
                          className="rounded-full border border-red-500/40 bg-red-500/10 px-2 py-0.5 text-[10px] font-semibold text-red-300 hover:bg-red-500/20"
                        >
                          ? YT Guide
                        </button>
                        <button
                          type="button"
                          onClick={() => setActiveGuideKey('meta')}
                          className="rounded-full border border-cyan-500/40 bg-cyan-500/10 px-2 py-0.5 text-[10px] font-semibold text-cyan-300 hover:bg-cyan-500/20"
                        >
                          ? Meta
                        </button>
                      </div>
                    </div>
                    <label className="flex items-center justify-between text-[11px]">
                      <span>Direct Kaggle → YouTube Auto-Upload</span>
                      <input
                        type="checkbox"
                        checked={directYoutubeUpload}
                        onChange={(e) => setDirectYoutubeUpload(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="text-zinc-400">Privacy</span>
                      <div className="flex gap-1">
                        {(['private', 'unlisted', 'public'] as const).map((p) => (
                          <button
                            key={p}
                            type="button"
                            onClick={() => setYtPrivacy(p)}
                            className="rounded px-1.5 py-0.5 text-[10px] capitalize"
                            style={{
                              backgroundColor: ytPrivacy === p ? `${activeAccent.hex}30` : '#18181b',
                              color: ytPrivacy === p ? activeAccent.hex : '#a1a1aa',
                            }}
                          >
                            {p}
                          </button>
                        ))}
                      </div>
                    </div>
                    <div className="space-y-1">
                      <div className="flex justify-between font-mono text-[10px] text-zinc-400">
                        <span>Daily YT Quota</span>
                        <span className="text-emerald-400">{ytDailyQuotaUsed.toLocaleString()} / 10,000 units</span>
                      </div>
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-zinc-800">
                        <div
                          className="h-full rounded-full bg-emerald-400"
                          style={{ width: `${Math.min(100, (ytDailyQuotaUsed / 10000) * 100)}%` }}
                        />
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          triggerToast('YouTube OAuth2 OK: Connected to Main Tech Channel (14.2K subs)');
                        }}
                        className="rounded border border-emerald-500/40 bg-emerald-500/15 py-1 text-[10px] font-semibold text-emerald-300 hover:bg-emerald-500/25"
                      >
                        Test YT OAuth
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setYtDailyQuotaUsed((q) => Math.min(10000, q + 1650));
                          triggerToast('Simulated 2 MB Resumable Upload -> https://youtu.be/oMn1Ai53 (+1,650 units)');
                        }}
                        className="rounded border border-zinc-700 bg-zinc-900 py-1 text-[10px] font-medium text-zinc-200 hover:bg-zinc-800"
                      >
                        Sim YT Upload
                      </button>
                    </div>
                  </div>

                  <div className="space-y-2 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5 text-xs">
                    <label className="flex items-center justify-between">
                      <span>Privacy Shield (FLAG_SECURE)</span>
                      <input
                        type="checkbox"
                        checked={flagSecure}
                        onChange={(e) => setFlagSecure(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <label className="flex items-center justify-between">
                      <span>Biometric / Device PIN Lock</span>
                      <input
                        type="checkbox"
                        checked={appLock}
                        onChange={(e) => setAppLock(e.target.checked)}
                        style={{ accentColor: activeAccent.hex }}
                      />
                    </label>
                    <div className="flex items-center justify-between pt-1">
                      <span className="text-zinc-400">Auto-Lock</span>
                      <div className="flex gap-1">
                        {(['Immediate', '1 Min', '5 Min', 'Never'] as const).map((t) => (
                          <button
                            key={t}
                            type="button"
                            onClick={() => setAutoLock(t)}
                            className="rounded px-1.5 py-0.5 text-[10px]"
                            style={{
                              backgroundColor: autoLock === t ? `${activeAccent.hex}30` : '#18181b',
                              color: autoLock === t ? activeAccent.hex : '#a1a1aa',
                            }}
                          >
                            {t}
                          </button>
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2 text-xs">
                    <button
                      type="button"
                      onClick={() => setPingTested(true)}
                      className="rounded border border-zinc-700 bg-zinc-900 py-1.5 font-medium text-zinc-200 hover:bg-zinc-800"
                    >
                      Test Endpoints
                    </button>
                    <button
                      type="button"
                      onClick={() => setOtaChecked(true)}
                      className="rounded border border-zinc-700 bg-zinc-900 py-1.5 font-medium text-zinc-200 hover:bg-zinc-800"
                    >
                      Check GitHub OTA
                    </button>
                  </div>

                  {pingTested && (
                    <div className="rounded border border-zinc-800 bg-[#0B0E14] p-2 font-mono text-[11px] space-y-1 tabular-nums">
                      <div className="flex justify-between">
                        <span>Primary Endpoint</span>
                        <span style={{ color: activeAccent.hex }}>200 OK · 84 ms</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Gemini 2.5 Flash</span>
                        <span style={{ color: activeAccent.hex }}>200 OK · 142 ms</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Kaggle T4 API</span>
                        <span style={{ color: activeAccent.hex }}>200 OK · 196 ms</span>
                      </div>
                    </div>
                  )}

                  {otaChecked && (
                    <div className="rounded border border-emerald-500/30 bg-emerald-950/20 p-2 text-[11px] text-emerald-200">
                      GitHub Release v1.0.14 ready · Signed APK via FileProvider installer
                    </div>
                  )}

                  {panicStage === 0 && (
                    <button
                      type="button"
                      onClick={() => setPanicStage(1)}
                      className="flex w-full items-center justify-center gap-1.5 rounded-md bg-red-600 py-2 text-xs font-semibold text-white hover:bg-red-500"
                    >
                      <ShieldAlert className="h-3.5 w-3.5" />
                      <span>Panic Wipe Vault & Room DB</span>
                    </button>
                  )}
                  {panicStage === 1 && (
                    <div className="space-y-1.5 rounded border border-red-500/50 bg-red-950/30 p-2 text-xs">
                      <p className="text-red-200">Step 1/2: Shred EncryptedSharedPreferences and Room DB?</p>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={() => setPanicStage(2)}
                          className="flex-1 rounded bg-red-600 py-1 font-medium text-white"
                        >
                          Confirm Step 1
                        </button>
                        <button
                          type="button"
                          onClick={() => setPanicStage(0)}
                          className="rounded border border-zinc-700 px-3 py-1 text-zinc-300"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  )}
                  {panicStage === 2 && (
                    <div className="space-y-1.5 rounded border border-red-500 bg-red-950/50 p-2 text-xs">
                      <p className="font-medium text-red-100">Final Warning (2/2): Shred all keys now?</p>
                      <div className="flex gap-2">
                        <button
                          type="button"
                          onClick={executePanicWipe}
                          className="flex-1 rounded bg-red-600 py-1 font-semibold text-white"
                        >
                          SHRED NOW
                        </button>
                        <button
                          type="button"
                          onClick={() => setPanicStage(0)}
                          className="rounded border border-zinc-700 px-3 py-1 text-zinc-300"
                        >
                          Abort
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )}
            </>
          )}
        </div>

        {/* v5.5 Server-Enforced RBAC Pending Approval Lock Screen */}
        {simCloudRole === 'PENDING' && !lockOverlayOpen && !decoySandboxOpen && (
          <div className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-[#09090C]/95 p-6 text-center">
            <div className="w-full space-y-3 rounded-2xl border border-[#232330] bg-[#13131A] p-5">
              <ShieldAlert className="mx-auto h-10 w-10 text-amber-400" />
              <h3 className="text-base font-semibold text-zinc-100">Awaiting Owner Approval</h3>
              <p className="text-[11px] leading-relaxed text-zinc-400">
                Signed in via Google OAuth PKCE (<span className="font-mono text-zinc-200">teammate@studio.dev</span>). The workspace OWNER must approve your account in Supabase RBAC before cloud sync unlocks.
              </p>
              <button
                type="button"
                onClick={() => {
                  setSimCloudRole('EDITOR');
                  triggerToast('Role refreshed from Supabase: EDITOR (Approved) — Delta Syncing...');
                }}
                className="w-full rounded-lg py-2 text-xs font-semibold text-zinc-950"
                style={{ backgroundColor: activeAccent.hex }}
              >
                Refresh Approval Status
              </button>
              <button
                type="button"
                onClick={() => {
                  setSimCloudRole('OFFLINE');
                  triggerToast('Switched to Offline Local Mode (100% local Room DB).');
                }}
                className="w-full rounded-lg border border-zinc-700 bg-zinc-900 py-1.5 text-xs font-medium text-zinc-200 hover:bg-zinc-800"
              >
                Switch to Offline Local Mode
              </button>
            </div>
          </div>
        )}

        {/* Upgrade 6: Duress / Decoy PIN Lock Overlay */}
        {lockOverlayOpen && (
          <div className="absolute inset-0 z-30 flex flex-col items-center justify-center bg-black p-6 text-center">
            <Lock className="h-10 w-10" style={{ color: activeAccent.hex }} />
            <h3 className="mt-3 text-base font-semibold text-zinc-100">OmniAI-Studio Locked</h3>
            <p className="mt-1 text-[11px] text-zinc-400">
              Enter Master PIN (<span className="font-mono text-zinc-200">1234</span>) or Decoy PIN (
              <span className="font-mono text-amber-300">0000</span>)
            </p>
            <input
              type="password"
              value={pinEntry}
              onChange={(e) => {
                setPinEntry(e.target.value.replace(/\D/g, '').slice(0, 6));
                setPinError(null);
              }}
              aria-label="Duress or Master PIN input"
              placeholder="Enter 4-digit PIN"
              className="mt-4 w-full rounded border border-zinc-700 bg-[#0B0E14] px-3 py-2 text-center font-mono text-sm tracking-widest text-zinc-100"
            />
            {pinError && <p className="mt-1.5 text-[11px] text-red-400">{pinError}</p>}
            <div className="mt-3 flex w-full gap-2">
              <button
                type="button"
                onClick={() => {
                  if (pinEntry === '1234') {
                    setLockOverlayOpen(false);
                    setDecoySandboxOpen(false);
                    triggerToast('Master PIN verified (Salted SHA-256).');
                  } else if (pinEntry === '0000') {
                    setLockOverlayOpen(false);
                    setDecoySandboxOpen(true);
                  } else {
                    setPinError('Incorrect PIN. Use 1234 (Master) or 0000 (Decoy).');
                  }
                }}
                className="flex-1 rounded py-1.5 text-xs font-semibold text-zinc-950"
                style={{ backgroundColor: activeAccent.hex }}
              >
                Unlock PIN
              </button>
              <button
                type="button"
                onClick={() => setLockOverlayOpen(false)}
                className="rounded border border-zinc-700 px-3 py-1.5 text-xs text-zinc-300"
              >
                Biometric
              </button>
            </div>
          </div>
        )}

        {/* Upgrade 6: Harmless Isolated Quick Notes Decoy Sandbox */}
        {decoySandboxOpen && (
          <div className="absolute inset-0 z-30 flex flex-col bg-[#0A0A0F] text-zinc-100">
            <div className="flex items-center justify-between border-b border-zinc-800 bg-[#11131A] px-3.5 py-2.5">
              <span className="inline-flex items-center gap-1.5 text-sm font-semibold text-amber-400">
                <FileText className="h-4 w-4" />
                <span>Quick Notes</span>
              </span>
              <button
                type="button"
                onClick={() => {
                  setDecoySandboxOpen(false);
                  setLockOverlayOpen(true);
                  setPinEntry('');
                }}
                className="inline-flex items-center gap-1 rounded border border-zinc-700 px-2 py-0.5 text-[10px] text-zinc-300"
              >
                <Lock className="h-3 w-3" />
                <span>Lock</span>
              </button>
            </div>
            <div className="flex-1 space-y-2.5 p-3.5 text-xs">
              <div className="rounded-lg border border-zinc-800 bg-[#141720] p-3">
                <div className="font-medium text-zinc-100">Grocery List</div>
                <p className="mt-1 text-zinc-400">Milk, bread, coffee beans, bananas, olive oil</p>
              </div>
              <div className="rounded-lg border border-zinc-800 bg-[#141720] p-3">
                <div className="font-medium text-zinc-100">Weekend Errands</div>
                <p className="mt-1 text-zinc-400">Pick up laundry at 4 PM, renew gym pass</p>
              </div>
              <p className="pt-2 text-[10px] text-zinc-500">
                Duress Sandbox active: Real Vault keys, chats, and videos are untouched and completely inaccessible here.
              </p>
            </div>
          </div>
        )}

        {/* Android Share-to-AI Receiver Modal Overlay */}
        {incomingShareText && (
          <div className="absolute inset-0 z-20 flex items-center justify-center bg-black/80 p-4">
            <div className="w-full space-y-2.5 rounded-xl border border-zinc-700 bg-[#0B0E14] p-3.5 text-xs">
              <div className="font-semibold text-zinc-100">Share to OmniAI Studio (ACTION_SEND)</div>
              <p className="rounded border border-zinc-800 bg-black p-2 font-mono text-[11px] text-zinc-300">
                {incomingShareText}
              </p>
              <button
                type="button"
                onClick={() => {
                  setChatInput(incomingShareText);
                  setIncomingShareText(null);
                  setShowSettingsHub(false);
                  setActiveTab('chat');
                  triggerToast('Routed shared text to AI Chat Studio.');
                }}
                className="w-full rounded py-1.5 font-semibold text-zinc-950"
                style={{ backgroundColor: activeAccent.hex }}
              >
                Send to AI Chat
              </button>
              <button
                type="button"
                onClick={() => {
                  setTopic(incomingShareText);
                  setIncomingShareText(null);
                  setShowSettingsHub(false);
                  setActiveTab('director');
                  triggerToast('Routed shared text to Video Storyboard.');
                }}
                className="w-full rounded border border-zinc-700 bg-zinc-900 py-1.5 font-medium text-zinc-200 hover:bg-zinc-800"
              >
                Create Video Storyboard
              </button>
              <button
                type="button"
                onClick={() => setIncomingShareText(null)}
                className="w-full py-1 text-[11px] text-zinc-400 hover:text-zinc-200"
              >
                Cancel
              </button>
            </div>
          </div>
        )}

        {/* v5.2 In-App Platform Setup Guide ModalBottomSheet Preview */}
        {activeGuideKey && (
          <div className="absolute inset-0 z-30 flex items-end bg-black/75">
            <div className="max-h-[85%] w-full overflow-y-auto rounded-t-2xl border-t border-[#232330] bg-[#13131A] p-4 text-xs text-zinc-200 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="font-semibold text-zinc-100">
                    {SIM_PLATFORM_GUIDES[activeGuideKey].title}
                  </div>
                  <div className="text-[10px] text-emerald-400">
                    In-App Interactive Setup Guide · v5.5
                  </div>
                </div>
                <button
                  type="button"
                  aria-label="Close Setup Guide"
                  onClick={() => setActiveGuideKey(null)}
                  className="rounded p-1 text-zinc-400 hover:bg-[#1B1B25] hover:text-zinc-100"
                >
                  <X className="h-4 w-4" />
                </button>
              </div>
              <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-2 text-[11px] text-amber-200">
                {SIM_PLATFORM_GUIDES[activeGuideKey].faq}
              </div>
              <ol className="space-y-1.5 text-[11px] text-zinc-300">
                {SIM_PLATFORM_GUIDES[activeGuideKey].steps.map((step, i) => (
                  <li key={i} className="flex gap-2">
                    <span className="font-mono text-emerald-400">{i + 1}.</span>
                    <span>{step}</span>
                  </li>
                ))}
              </ol>
              <div className="space-y-1.5 rounded-lg border border-[#232330] bg-[#09090C] p-2.5">
                <div className="text-[10px] font-semibold text-zinc-400">
                  {SIM_PLATFORM_GUIDES[activeGuideKey].commandTitle}
                </div>
                <pre className="overflow-x-auto whitespace-pre-wrap break-all font-mono text-[10px] leading-relaxed text-emerald-300">
                  {SIM_PLATFORM_GUIDES[activeGuideKey].command}
                </pre>
                <div className="flex gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => triggerToast('Copied command to clipboard!')}
                    className="flex-1 rounded border border-zinc-700 bg-zinc-900 py-1 text-[10px] font-medium text-zinc-200 hover:bg-zinc-800"
                  >
                    Copy
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setActiveGuideKey(null);
                      triggerToast('Dispatched guide command to Hybrid Terminal!');
                    }}
                    className="flex-1 rounded py-1 text-[10px] font-semibold text-zinc-950"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    Run in Terminal
                  </button>
                </div>
              </div>
              <button
                type="button"
                onClick={() =>
                  triggerToast(`LocalUriHandler -> ${SIM_PLATFORM_GUIDES[activeGuideKey].portalUrl}`)
                }
                className="w-full rounded-lg border border-emerald-500/40 bg-emerald-500/15 py-1.5 text-[11px] font-semibold text-emerald-300 hover:bg-emerald-500/25"
              >
                {SIM_PLATFORM_GUIDES[activeGuideKey].portalLabel}
              </button>
            </div>
          </div>
        )}

        {/* v5.4 Seamless Return-from-Adobe ON_RESUME Banner Preview */}
        {adobeReturnBannerSource && !showSettingsHub && (
          <div className="mx-2.5 mb-2 flex items-center gap-2 rounded-xl border border-emerald-500/60 bg-emerald-500/15 px-3 py-2 text-[11px]">
            <div className="min-w-0 flex-1">
              <div className="font-semibold text-emerald-300">
                Back from {adobeReturnBannerSource}!
              </div>
              <div className="text-[10px] text-zinc-200">
                Pick your exported thumbnail to attach to YouTube
              </div>
            </div>
            <button
              type="button"
              onClick={() => {
                setAdobeReturnBannerSource(null);
                triggerToast(
                  'PickVisualMedia -> copied to filesDir/thumbnails/script_1.jpg & bound to YouTube Upload!'
                );
              }}
              className="rounded-md bg-emerald-500 px-2.5 py-1 text-[10px] font-semibold text-zinc-950 hover:bg-emerald-400"
            >
              Pick
            </button>
            <button
              type="button"
              aria-label="Dismiss Adobe return banner"
              onClick={() => setAdobeReturnBannerSource(null)}
              className="rounded p-0.5 text-zinc-400 hover:text-zinc-100"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        )}

        {/* Material 3 Bottom Navigation Bar (v5.1 Strictly 4 Clean Tabs) */}
        {!showSettingsHub && (
          <nav
            aria-label="Android app tabs"
            className="grid grid-cols-4 border-t border-[#232330] bg-[#13131A] py-1.5"
          >
            {(
              [
                { id: 'home', label: 'Home', icon: Home },
                { id: 'chat', label: 'Chat', icon: MessageSquare },
                { id: 'director', label: 'Studio', icon: Film },
                { id: 'projects', label: 'Projects', icon: FolderKanban },
              ] as const
            ).map((tab) => {
              const Icon = tab.icon;
              const selected = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => setActiveTab(tab.id)}
                  className="flex flex-col items-center gap-0.5 py-1 text-[10px] font-medium transition-colors"
                  style={{ color: selected ? activeAccent.hex : '#71717a' }}
                >
                  <Icon className="h-4 w-4" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </nav>
        )}
      </div>

      {/* Right Column: Native Implementation Architecture & Source Jump */}
      <div className="mt-6 w-full max-w-xl space-y-5 lg:mt-0">
        <div className="space-y-3">
          <div>
            <h2 className="text-xl font-semibold tracking-tight text-zinc-100">
              OmniAI-Studio v5.5 · Supabase Cloud DB, Google OAuth PKCE &amp; Server-Enforced RBAC
            </h2>
            <p className="mt-1.5 text-sm leading-6 text-zinc-400">
              Adds a pure-OkHttp3 <strong className="text-zinc-200">Supabase PostgreSQL Compressed Text-Only Cloud DB</strong> (<code className="text-emerald-300">GZIP + Base64 &lt;= 256 KB/row</code>, zero MP4/images), <strong className="text-zinc-200">Google OAuth 2.0 PKCE (S256)</strong> via system browser (<code className="text-emerald-300">omniaistudio://auth-callback</code>), server-enforced <strong className="text-zinc-200">PostgreSQL RLS RBAC</strong> (<code className="text-emerald-300">OWNER / EDITOR / VIEWER / PENDING</code>), and a 4-day anti-pause CI cron — with zero Firebase, Play Services, or Supabase SDK Gradle dependencies.
            </p>
          </div>

          {/* Interactive v5.5 RBAC Role Preview Toggle */}
          <div className="rounded-xl border border-zinc-800 bg-[#0B0E14] p-3 space-y-2">
            <div className="flex items-center justify-between gap-2 text-xs">
              <span className="font-semibold text-zinc-200">Preview RBAC Role State</span>
              <span className="font-mono text-[11px] text-emerald-400">
                Active: {simCloudRole} · Delta Sync 78% GZIP saved
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5">
              {(['OWNER', 'EDITOR', 'VIEWER', 'PENDING', 'OFFLINE'] as const).map((role) => {
                const active = simCloudRole === role;
                return (
                  <button
                    key={role}
                    type="button"
                    onClick={() => {
                      setSimCloudRole(role);
                      setLockOverlayOpen(false);
                      setDecoySandboxOpen(false);
                      triggerToast(
                        role === 'PENDING'
                          ? 'RBAC Gate: PendingApprovalLockScreen active until OWNER approves'
                          : role === 'VIEWER'
                            ? 'RBAC Guard: Read-Only (Viewer Role) — writes/renders/Vault locked'
                            : `RBAC Role Preview -> ${role}`
                      );
                    }}
                    className={`rounded-md border px-2.5 py-1 font-mono text-[11px] font-medium transition-colors ${
                      active
                        ? 'border-emerald-500/70 bg-emerald-500/20 text-emerald-300'
                        : 'border-zinc-700 bg-zinc-900 text-zinc-300 hover:border-zinc-500'
                    }`}
                  >
                    {role}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <button
              type="button"
              onClick={() => {
                setDecoySandboxOpen(false);
                setLockOverlayOpen(false);
                setShowSettingsHub(false);
                setSimCloudRole('OWNER');
                setActiveTab('vault');
                setActiveGuideKey('supabase');
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-emerald-500/60 bg-emerald-500/15 px-2.5 py-1.5 text-xs font-medium text-emerald-300 hover:bg-emerald-500/25 transition-colors whitespace-nowrap"
            >
              <Shield className="h-3.5 w-3.5" />
              <span>? Supabase Cloud Guide</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setDecoySandboxOpen(false);
                setLockOverlayOpen(false);
                setShowSettingsHub(false);
                setSimCloudRole('OWNER');
                setActiveGuideKey(null);
                setActiveTab('director');
                setAdobeReturnBannerSource('Adobe Firefly');
                triggerToast(
                  'Thumbnail prompt copied! Native Adobe Firefly launch -> ON_RESUME return banner ready'
                );
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-rose-500/50 bg-rose-500/15 px-2.5 py-1.5 text-xs font-medium text-rose-300 hover:bg-rose-500/25 transition-colors whitespace-nowrap"
            >
              <Sparkles className="h-3.5 w-3.5" />
              <span>Test Adobe Firefly Bridge</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setDecoySandboxOpen(false);
                setLockOverlayOpen(false);
                setShowSettingsHub(false);
                setSimCloudRole('OWNER');
                setActiveTab('vault');
                setActiveGuideKey('youtube');
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-red-500/50 bg-red-500/15 px-2.5 py-1.5 text-xs font-medium text-red-300 hover:bg-red-500/25 transition-colors whitespace-nowrap"
            >
              <UploadCloud className="h-3.5 w-3.5" />
              <span>? YouTube API v3 Guide</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setDecoySandboxOpen(false);
                setLockOverlayOpen(false);
                setShowSettingsHub(false);
                setSimCloudRole('OWNER');
                setActiveTab('vault');
                setActiveGuideKey('telegram');
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-emerald-500/50 bg-emerald-500/15 px-2.5 py-1.5 text-xs font-medium text-emerald-300 hover:bg-emerald-500/25 transition-colors whitespace-nowrap"
            >
              <Sparkles className="h-3.5 w-3.5" />
              <span>? 2 GB Telegram Guide</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setLockOverlayOpen(true);
                setPinEntry('');
                setPinError(null);
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 bg-zinc-900 px-2.5 py-1.5 text-xs font-medium text-zinc-200 hover:border-amber-500/50 hover:text-amber-300 transition-colors whitespace-nowrap"
            >
              <Lock className="h-3.5 w-3.5" />
              <span>Test Duress PIN</span>
            </button>
            <button
              type="button"
              onClick={() =>
                setIncomingShareText('Breakthrough open-source 1.3B video diffusion models running on dual T4 GPUs')
              }
              className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 bg-zinc-900 px-2.5 py-1.5 text-xs font-medium text-zinc-200 hover:border-emerald-500/50 hover:text-emerald-300 transition-colors whitespace-nowrap"
            >
              <Share2 className="h-3.5 w-3.5" />
              <span>Share-to-AI</span>
            </button>
          </div>
        </div>

        <div className="divide-y divide-zinc-800/80 rounded-xl border border-zinc-800 bg-[#0B0E14]">
          {(
            [
              {
                key: 'supabase-sql-rls',
                title: 'v5.5 · Supabase PostgreSQL Schema, Auto-Owner Trigger & RLS RBAC',
                detail:
                  'supabase/schema_and_rls.sql creates user_profiles (first signup = OWNER, others = PENDING), prevents non-OWNER role self-escalation, enforces <=256 KB gz: text-only omni_sync_records, and restricts VAULT_META to OWNER only.',
                source: 'supabase/schema_and_rls.sql',
              },
              {
                key: 'google-oauth-pkce',
                title: 'v5.5 · Pure OkHttp3 Google OAuth 2.0 PKCE (S256) & Deep-Link Auth',
                detail:
                  'SupabaseAuthManager.kt generates 64-char PKCE verifier + SHA-256 URL-safe Base64 challenge, opens system browser via Intent.ACTION_VIEW, captures omniaistudio://auth-callback in MainActivity, and auto-refreshes JWTs.',
                source: 'app/src/main/java/com/omniai/studio/cloud/SupabaseAuthManager.kt',
              },
              {
                key: 'gzip-delta-sync',
                title: 'v5.5 · GZIP Text-Only Codec & Multi-Device LWW Delta Sync Engine',
                detail:
                  'PayloadGzipCodec.kt strips local media paths, compresses JSON (~70–85% savings) under a strict 256 KB cap with 1 MB decompression guard, and syncs via CloudSyncRepository + CloudSyncWorker (15m / 60m / Manual Wi-Fi).',
                source: 'app/src/main/java/com/omniai/studio/cloud/CloudSyncRepository.kt',
              },
              {
                key: 'rbac-account-ui',
                title: 'v5.5 · Cloud DB & Roles Admin Panel, Pending Gate & Viewer Guard',
                detail:
                  'CloudAccountScreen.kt + RolePermissionGuard.kt enforce PendingApprovalLockScreen, Read-Only Viewer banners, Owner-Only Vault locks, and live Team & Device role management.',
                source: 'app/src/main/java/com/omniai/studio/ui/screens/CloudAccountScreen.kt',
              },
              {
                key: 'adobe-native-bridge',
                title: 'v5.4 · Native Adobe Express & Firefly Installed-App Bridge (Zero Browser)',
                detail:
                  'AdobeAppBridge.kt resolves installed com.adobe.spark.post & com.adobe.firefly with API 30+ FLAG_ACTIVITY_REQUIRE_NON_BROWSER and FileProvider ACTION_SEND HD frame handoff.',
                source: 'app/src/main/java/com/omniai/studio/media/AdobeAppBridge.kt',
              },
            ] as const
          ).map((item) => (
            <div key={item.key} className="flex items-start justify-between gap-4 p-4">
              <div>
                <h3 className="text-sm font-medium text-zinc-100">{item.title}</h3>
                <p className="mt-1 text-xs leading-5 text-zinc-400">{item.detail}</p>
              </div>
              <button
                type="button"
                onClick={() => onInspectSource(item.source)}
                className="shrink-0 rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:border-emerald-500/50 hover:text-emerald-300 transition-colors whitespace-nowrap"
              >
                Open Source
              </button>
            </div>
          ))}
        </div>

        <div className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-zinc-800 bg-[#0B0E14] px-4 py-3 text-xs text-zinc-400">
          <div className="flex items-center gap-2">
            <Lock className="h-4 w-4 text-emerald-400 shrink-0" />
            <span>v5.5 Zero-New-Gradle-Deps Modules:</span>
          </div>
          <div className="flex flex-wrap items-center gap-3 shrink-0">
            <button
              type="button"
              onClick={() => onInspectSource('supabase/schema_and_rls.sql')}
              className="font-mono text-emerald-400 hover:text-emerald-300 whitespace-nowrap"
            >
              schema_and_rls.sql
            </button>
            <button
              type="button"
              onClick={() => onInspectSource('app/src/main/java/com/omniai/studio/cloud/SupabaseAuthManager.kt')}
              className="font-mono text-emerald-400 hover:text-emerald-300 whitespace-nowrap"
            >
              SupabaseAuthManager.kt
            </button>
            <button
              type="button"
              onClick={() => onInspectSource('app/src/main/java/com/omniai/studio/cloud/PayloadGzipCodec.kt')}
              className="font-mono text-emerald-400 hover:text-emerald-300 whitespace-nowrap"
            >
              PayloadGzipCodec.kt
            </button>
            <button
              type="button"
              onClick={() => onInspectSource('app/src/main/java/com/omniai/studio/cloud/CloudSyncRepository.kt')}
              className="font-mono text-emerald-400 hover:text-emerald-300 whitespace-nowrap"
            >
              CloudSyncRepository.kt
            </button>
            <button
              type="button"
              onClick={() => onInspectSource('.github/workflows/keep-supabase-alive.yml')}
              className="font-mono text-emerald-400 hover:text-emerald-300 whitespace-nowrap"
            >
              keep-supabase-alive.yml
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
