package com.omniai.studio.ui.guides

enum class PlatformGuideId(val shortTitle: String) {
    UNCENSORED_TUNNEL("Uncensored AI Tunnel"),
    HUGGING_FACE("Hugging Face Serverless"),
    GEMINI("Google Gemini API"),
    GROQ_OPENROUTER("Groq / OpenRouter"),
    KAGGLE_WORKER("Kaggle T4 x2 Worker"),
    GITHUB_CICD("GitHub CI/CD & Auto-Push"),
    TELEGRAM_2GB("Telegram Bot & 2 GB Upload"),
    YOUTUBE_API("YouTube Data API v3"),
    META_REELS("Meta FB/IG Reels API"),
    SUPABASE_CLOUD("Supabase Cloud DB & RBAC")
}

data class GuidePortalLink(
    val label: String,
    val url: String
)

data class GuideCommandBlock(
    val title: String,
    val command: String,
    val canRunInTerminal: Boolean = false,
    val note: String = ""
)

data class PlatformGuideSpec(
    val id: PlatformGuideId,
    val title: String,
    val badge: String,
    val summary: String,
    val faqNote: String = "",
    val portals: List<GuidePortalLink>,
    val steps: List<String>,
    val commands: List<GuideCommandBlock>
)

object PlatformSetupGuides {

    private val colabUncensoredTunnelScript = """
!pip install -q llama-cpp-python[server] huggingface_hub
!wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O /usr/local/bin/cloudflared && chmod +x /usr/local/bin/cloudflared

import subprocess, threading, time, re
from huggingface_hub import hf_hub_download

model_path = hf_hub_download(
    repo_id="bartowski/Meta-Llama-3.1-8B-Instruct-abliterated-GGUF",
    filename="Meta-Llama-3.1-8B-Instruct-abliterated-Q4_K_M.gguf"
)

server = subprocess.Popen([
    "python3", "-m", "llama_cpp.server",
    "--model", model_path,
    "--model_alias", "dolphin-mistral",
    "--n_gpu_layers", "-1",
    "--n_ctx", "8192",
    "--host", "0.0.0.0",
    "--port", "8080"
])

time.sleep(8)
tunnel = subprocess.Popen(
    ["cloudflared", "tunnel", "--url", "http://127.0.0.1:8080"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True
)
for line in tunnel.stdout:
    m = re.search(r"https://[a-zA-Z0-9-]+\.trycloudflare\.com", line)
    if m:
        print("\n=== COPY THIS BASE URL INTO OMNIAI VAULT ===")
        print(m.group(0) + "/v1\n", flush=True)
        break
server.wait()
    """.trimIndent()

    private val termuxLocalGgufCommand = """
echo "allow-external-apps=true" >> ~/.termux/termux.properties && termux-reload-settings
pkg update -y && pkg install -y llama-cpp
llama-server -m ~/models/llama-3-8b-uncensored-Q4_K_M.gguf --host 127.0.0.1 --port 8080 -c 4096
    """.trimIndent()

    private val hfCurlTestCommand = """
curl -sS https://router.huggingface.co/v1/chat/completions \
  -H "Authorization: Bearer YOUR_HF_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"model":"Qwen/Qwen2.5-72B-Instruct","messages":[{"role":"user","content":"Reply with OK"}],"max_tokens":16}'
    """.trimIndent()

    private val geminiCurlTestCommand = """
curl -sS "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=YOUR_AIza_KEY" \
  -H "Content-Type: application/json" \
  -d '{"contents":[{"parts":[{"text":"Reply with OK"}]}]}'
    """.trimIndent()

    private val groqOpenRouterCurlCommand = """
# Groq verification (Base URL: https://api.groq.com/openai/v1)
curl -sS https://api.groq.com/openai/v1/models \
  -H "Authorization: Bearer YOUR_GROQ_KEY"

# OpenRouter verification (Base URL: https://openrouter.ai/api/v1)
curl -sS https://openrouter.ai/api/v1/models \
  -H "Authorization: Bearer YOUR_OPENROUTER_KEY"
    """.trimIndent()

    private val kagglePushCommand = """
kaggle kernels push -p kaggle_worker
    """.trimIndent()

    private val kaggleSecretsReference = """
TG_BOT_TOKEN
TG_CHAT_ID
TG_API_ID
TG_API_HASH
    """.trimIndent()

    private val githubVerifyCommand = """
curl -sS -H "Authorization: Bearer YOUR_TOKEN" https://api.github.com/user
    """.trimIndent()

    private val telegramBotFatherCommands = """
/newbot
OmniAI Studio Uploader
omniai_studio_uploader_bot
    """.trimIndent()

    private val telegramGetUpdatesCommand = """
curl -sS https://api.telegram.org/bot<YOUR_TOKEN>/getUpdates
    """.trimIndent()

    private val youtubeRefreshTokenPythonScript = """
pip install -q google-auth-oauthlib
python3 - <<'EOF'
from google_auth_oauthlib.flow import InstalledAppFlow

SCOPES = [
    "https://www.googleapis.com/auth/youtube.upload",
    "https://www.googleapis.com/auth/youtube.force-ssl",
    "https://www.googleapis.com/auth/youtube.readonly",
]
flow = InstalledAppFlow.from_client_secrets_file("client_secret.json", SCOPES)
creds = flow.run_local_server(port=0, access_type="offline", prompt="consent")
print("YT_CLIENT_ID     =", creds.client_id)
print("YT_CLIENT_SECRET =", creds.client_secret)
print("YT_REFRESH_TOKEN =", creds.refresh_token)
EOF
    """.trimIndent()

    private val youtubeKaggleSecretsReference = """
YT_CLIENT_ID
YT_CLIENT_SECRET
YT_REFRESH_TOKEN
    """.trimIndent()

    private val metaExchangeLongTokenCurl = """
curl -sS -G "https://graph.facebook.com/v19.0/oauth/access_token" \
  --data-urlencode "grant_type=fb_exchange_token" \
  --data-urlencode "client_id=YOUR_META_APP_ID" \
  --data-urlencode "client_secret=YOUR_META_APP_SECRET" \
  --data-urlencode "fb_exchange_token=YOUR_SHORT_LIVED_TOKEN"
    """.trimIndent()

    private val metaKaggleSecretsReference = """
META_PAGE_ID
META_IG_USER_ID
META_PAGE_ACCESS_TOKEN
    """.trimIndent()

    private val supabaseCurlVerifyCommand = """
curl -sS "https://<project-ref>.supabase.co/rest/v1/user_profiles?select=count" \
  -H "apikey: YOUR_SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer YOUR_SUPABASE_ANON_KEY"
    """.trimIndent()

    private val supabaseSqlQuickBootstrap = """
-- Paste full supabase/schema_and_rls.sql inside Supabase SQL Editor.
-- Key redirect & callback URLs to configure:
-- 1) Supabase Auth -> URL Configuration -> Redirect URLs:
--    omniaistudio://auth-callback
-- 2) Google Cloud Console -> Authorized redirect URI:
--    https://<project-ref>.supabase.co/auth/v1/callback
-- 3) GitHub Repo Secrets (4-day anti-pause workflow):
--    SUPABASE_URL
--    SUPABASE_ANON_KEY
    """.trimIndent()

    val allGuides: List<PlatformGuideSpec> = listOf(
        PlatformGuideSpec(
            id = PlatformGuideId.UNCENSORED_TUNNEL,
            title = "Uncensored AI (Colab / Termux Tunnel)",
            badge = "Primary LLM",
            summary = "Run an uncensored Llama-3 8B GGUF server on a free Google Colab T4 GPU or locally in Android Termux and connect via OpenAI-compatible /v1 URL.",
            portals = listOf(
                GuidePortalLink("Open Google Colab", "https://colab.research.google.com")
            ),
            steps = listOf(
                "Open Google Colab, create a new notebook, and switch Runtime -> Change runtime type -> T4 GPU.",
                "Paste Command 1 below into a single code cell and press Run. Wait ~45 seconds for the model weights and Cloudflare Tunnel to start.",
                "Copy the printed https://xxxx.trycloudflare.com/v1 URL and paste it into Primary Uncensored Tunnel URL in OmniAI Vault.",
                "Or, to run 100% offline on-device via Termux, paste Command 2 into Termux, then tap 'Scan Localhost' in Vault to auto-bind http://127.0.0.1:8080/v1."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Command 1 · Colab 1-Cell Uncensored Llama-3 8B + Cloudflare Tunnel",
                    command = colabUncensoredTunnelScript,
                    canRunInTerminal = false,
                    note = "Paste into a single Google Colab T4 cell. Prints your live https://xxxx.trycloudflare.com/v1 endpoint."
                ),
                GuideCommandBlock(
                    title = "Command 2 · Termux Local GGUF Server + External Apps Bridge",
                    command = termuxLocalGgufCommand,
                    canRunInTerminal = true,
                    note = "Enables Termux RUN_COMMAND bridge and starts a local llama-server on 127.0.0.1:8080."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.HUGGING_FACE,
            title = "Hugging Face Serverless Router",
            badge = "Secondary Fallback",
            summary = "Zero-setup serverless inference fallback via router.huggingface.co with automatic failover when your primary tunnel sleeps.",
            portals = listOf(
                GuidePortalLink("Open HF Tokens Portal", "https://huggingface.co/settings/tokens")
            ),
            steps = listOf(
                "Sign in to Hugging Face and open Settings -> Access Tokens via the Open Portal button below.",
                "Click 'Create new token', choose Fine-grained (or Read), and ensure 'Make calls to Inference Providers' is checked.",
                "Copy the generated hf_... token and paste it into Secondary Hugging Face Token in Vault.",
                "Optional: verify your token right now in Terminal using the curl test command below."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Verify HF_TOKEN against router.huggingface.co/v1",
                    command = hfCurlTestCommand,
                    canRunInTerminal = true,
                    note = "Replace YOUR_HF_TOKEN before running to verify chat completions."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.GEMINI,
            title = "Google Gemini API & Multi-Key Pool",
            badge = "Tertiary Fallback",
            summary = "Fast, reliable Gemini 2.5 Flash fallback for Storyboards, Script Auditing, and Chat. Supports comma-separated backup keys.",
            portals = listOf(
                GuidePortalLink("Open AI Studio API Keys", "https://aistudio.google.com/apikey")
            ),
            steps = listOf(
                "Tap 'Open AI Studio API Keys' and sign in with your Google account.",
                "Click 'Create API key' -> select or create a project -> copy the AIza... key.",
                "Paste the key into Tertiary Gemini 2.5 Flash Key in Vault. To enable Multi-Key Pool rotation on 429 rate limits, paste multiple comma-separated keys (e.g., AIzaKey1,AIzaKey2).",
                "Test your key directly against gemini-2.5-flash with the curl command below."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Test Gemini 2.5 Flash Key via curl",
                    command = geminiCurlTestCommand,
                    canRunInTerminal = true,
                    note = "Replace YOUR_AIza_KEY with your generated key."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.GROQ_OPENROUTER,
            title = "Groq & OpenRouter Presets",
            badge = "OpenAI-Compatible",
            summary = "Connect ultra-fast Groq LPUs or 200+ OpenRouter models as your Primary Endpoint using one-tap Base URL presets.",
            portals = listOf(
                GuidePortalLink("Open Groq Console", "https://console.groq.com/keys"),
                GuidePortalLink("Open OpenRouter Keys", "https://openrouter.ai/keys")
            ),
            steps = listOf(
                "Generate an API key from Groq Console (gsk_...) or OpenRouter (sk-or-v1-...).",
                "In Vault -> Quick Provider Presets, tap the 'Groq' chip (Base URL: https://api.groq.com/openai/v1) or 'OpenRouter' chip (Base URL: https://openrouter.ai/api/v1).",
                "Paste your key into Primary Tunnel API Key and pick your preferred Model ID (e.g., llama-3.3-70b-versatile or deepseek/deepseek-chat).",
                "Run the /models curl probe below or tap 'Test All' in Vault to confirm 200 OK."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Verify Groq / OpenRouter /models Endpoint",
                    command = groqOpenRouterCurlCommand,
                    canRunInTerminal = true,
                    note = "Preserves OpenAI-compatible Bearer auth for both providers."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.KAGGLE_WORKER,
            title = "Kaggle T4 x2 Dual-GPU Worker",
            badge = "Video Renderer",
            summary = "Provision your free 30h/week Dual T4 Wan 2.1 video worker with private dataset triggers and direct 2 GB Telegram output.",
            portals = listOf(
                GuidePortalLink("Open Kaggle Settings", "https://www.kaggle.com/settings")
            ),
            steps = listOf(
                "Open Kaggle Settings -> scroll down to the API section -> click 'Create New Token' to download kaggle.json.",
                "Open kaggle.json and copy your 'username' and 'key' into OmniAI Vault (you can also add backup accounts into the Multi-Account Pool).",
                "Create a private Kaggle Dataset named 'video-tasks-input' and a GPU T4 x2 Notebook named 'video-render-worker' (or 'omniai-wan21-dual-gpu-worker') with Internet turned ON.",
                "Inside your Kaggle Notebook -> Add-ons -> Secrets, add the 4 exact secret labels below so Kaggle uploads renders up to 2 GB directly to Telegram with zero mobile data."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Push Worker Script to Kaggle via CLI",
                    command = kagglePushCommand,
                    canRunInTerminal = true,
                    note = "Pushes kaggle_worker/video_worker.py + kernel-metadata.json."
                ),
                GuideCommandBlock(
                    title = "Exact Kaggle Notebook Secret Names (Add-ons -> Secrets)",
                    command = kaggleSecretsReference,
                    canRunInTerminal = false,
                    note = "Toggle all 4 secrets ON inside your Kaggle Notebook. Never paste secret values into scenes.json."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.GITHUB_CICD,
            title = "GitHub CI/CD, OTA & Atomic Auto-Push",
            badge = "DevOps & Sync",
            summary = "Enable 1-Tap Git Auto-Push from Projects, live GitHub Actions APK builds, In-App OTA updates, and Private Encrypted Vault Backups.",
            portals = listOf(
                GuidePortalLink("Open GitHub PAT Generator", "https://github.com/settings/tokens/new")
            ),
            steps = listOf(
                "Tap 'Open GitHub PAT Generator' and choose 'Generate new token (classic)'.",
                "Check BOTH the 'repo' and 'workflow' scopes, set an expiration, and click Generate Token.",
                "Paste the ghp_... token and your 'owner/repo' slug into OmniAI Vault and tap Save.",
                "In your GitHub repository -> Settings -> Secrets and variables -> Actions, add KAGGLE_USERNAME and KAGGLE_KEY so pushing kaggle_worker/** auto-syncs your Kaggle kernel."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Verify GitHub Token Scopes & Identity",
                    command = githubVerifyCommand,
                    canRunInTerminal = true,
                    note = "Replace YOUR_TOKEN with your ghp_... Personal Access Token."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.TELEGRAM_2GB,
            title = "Telegram Bot & 2 GB Direct Kaggle Upload",
            badge = "MTProto 2 GB",
            summary = "Deliver full HD Wan 2.1 renders directly from Kaggle's servers to your private Telegram chat or channel without using phone mobile data.",
            faqNote = "Why 50 MB vs 2 GB? Standard Telegram HTTP Bot API (requests.post) hard-caps uploads at 50 MB. Adding TG_API_ID + TG_API_HASH unlocks Telethon MTProto bot mode on Kaggle — supporting up to 2 GB single-file streaming uploads with NO phone OTP and zero mobile data. Without API ID/Hash, the worker automatically falls back to HTTP (<=49 MB) or splits >49 MB renders into 45 MB parts.",
            portals = listOf(
                GuidePortalLink("Open @BotFather", "https://t.me/BotFather"),
                GuidePortalLink("Open my.telegram.org/apps", "https://my.telegram.org/apps")
            ),
            steps = listOf(
                "Open @BotFather on Telegram, send /newbot, choose a display name and a username ending in 'bot', and copy your TG_BOT_TOKEN.",
                "Send any test message (e.g. 'hello') to your new bot (or add it as Admin to your private archive channel), then tap 'Auto-Detect Chat ID' inside OmniAI Vault to fill TG_CHAT_ID automatically.",
                "To unlock 2 GB direct Kaggle uploads, open https://my.telegram.org/apps, log in, click 'API development tools', and copy App api_id (TG_API_ID) and App api_hash (TG_API_HASH).",
                "Save all 4 values in OmniAI Vault AND add the exact same 4 keys in Kaggle Notebook -> Add-ons -> Secrets."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "BotFather Setup Sequence",
                    command = telegramBotFatherCommands,
                    canRunInTerminal = false,
                    note = "Send /newbot first, then reply with your bot display name and unique *_bot username."
                ),
                GuideCommandBlock(
                    title = "Manual Chat ID Fetcher (Or use 1-Tap Auto-Detect in Vault)",
                    command = telegramGetUpdatesCommand,
                    canRunInTerminal = true,
                    note = "Replace <YOUR_TOKEN> after sending a message to your bot. Look for \"chat\":{\"id\":...}."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.YOUTUBE_API,
            title = "YouTube Data API v3 (Direct Upload, Analytics & Comments)",
            badge = "OAuth2 Resumable",
            summary = "Enable 1-tap resumable MP4 upload, custom JPEG thumbnails, first-comment posting, closed-loop Analytics, and Direct Kaggle-to-YouTube uploads across multiple channels.",
            faqNote = "IMPORTANT — Unverified App Private Lock Rule & Daily 10,000 Quota: Newly created Google Cloud OAuth apps start in 'Testing/Unverified' mode, which locks API-uploaded videos to Private until you either complete Google's free verification audit or toggle Visibility -> Public inside YouTube Studio. Keep Privacy set to 'private' by default for zero upload rejections. Free daily quota is 10,000 units/day (~1,650 units per video + thumbnail + comment = ~6 full uploads/day free).",
            portals = listOf(
                GuidePortalLink("Enable YouTube Data API v3", "https://console.cloud.google.com/apis/library/youtube.googleapis.com"),
                GuidePortalLink("Open OAuth 2.0 Playground", "https://developers.google.com/oauthplayground")
            ),
            steps = listOf(
                "Tap 'Enable YouTube Data API v3' in Google Cloud Console, create/select a project, and click ENABLE.",
                "Go to APIs & Services -> OAuth consent screen -> External -> add your own Google account under 'Test users' -> Save.",
                "Go to Credentials -> Create Credentials -> OAuth Client ID -> Desktop App (or Web App with redirect https://developers.google.com/oauthplayground) and copy Client ID & Client Secret.",
                "Generate your permanent YT_REFRESH_TOKEN either via the 15-line Python script below OR in OAuth 2.0 Playground (gear icon -> Use your own OAuth credentials -> authorize youtube.upload + youtube.force-ssl + youtube.readonly -> Exchange authorization code for tokens).",
                "Paste YT_CLIENT_ID, YT_CLIENT_SECRET, and YT_REFRESH_TOKEN into OmniAI Vault (and into Kaggle Notebook -> Add-ons -> Secrets for direct cloud uploads)."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "15-Line Python Refresh Token Generator (Run Locally / Colab / Termux)",
                    command = youtubeRefreshTokenPythonScript,
                    canRunInTerminal = true,
                    note = "Place your downloaded client_secret.json in the same folder. Prints YT_CLIENT_ID, YT_CLIENT_SECRET, and permanent YT_REFRESH_TOKEN."
                ),
                GuideCommandBlock(
                    title = "Exact Kaggle Notebook Secret Names (Direct Kaggle -> YouTube)",
                    command = youtubeKaggleSecretsReference,
                    canRunInTerminal = false,
                    note = "Add all 3 secrets under Kaggle Notebook -> Add-ons -> Secrets and toggle them ON. Never hardcode tokens inside scenes.json."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.META_REELS,
            title = "Meta Graph API (Facebook Page Reels & Instagram Reels)",
            badge = "FB + IG Reels",
            summary = "Auto-publish rendered 9:16 vertical videos directly to your Facebook Page Reels (rupload.facebook.com) and optional Instagram Business Reels.",
            faqNote = "Instagram Business Requirement: Your Instagram account must be switched to a Professional/Business account and linked to a Facebook Page. Facebook Page Reels supports direct binary MP4 upload from Kaggle; Instagram Reels Graph API requires a publicly reachable MP4 URL.",
            portals = listOf(
                GuidePortalLink("Open Meta Graph API Explorer", "https://developers.facebook.com/tools/explorer/")
            ),
            steps = listOf(
                "Open Meta for Developers -> create a Business App -> open Graph API Explorer via the portal link below.",
                "Under Permissions, add 'pages_show_list', 'pages_manage_posts', 'pages_read_engagement', and 'instagram_content_publish', then click Generate Access Token.",
                "Exchange the short-lived token for a 60-day Long-Lived Token using the curl command below, then query GET /me/accounts to copy your Page ID (META_PAGE_ID) and Page Access Token (META_PAGE_ACCESS_TOKEN).",
                "Query GET /{META_PAGE_ID}?fields=instagram_business_account to copy your META_IG_USER_ID, then save all 3 in OmniAI Vault and Kaggle Secrets."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Exchange Short-Lived Token for 60-Day Long-Lived Token",
                    command = metaExchangeLongTokenCurl,
                    canRunInTerminal = true,
                    note = "Replace YOUR_META_APP_ID, YOUR_META_APP_SECRET, and YOUR_SHORT_LIVED_TOKEN."
                ),
                GuideCommandBlock(
                    title = "Exact Kaggle Notebook Secret Names (Meta Reels Auto-Upload)",
                    command = metaKaggleSecretsReference,
                    canRunInTerminal = false,
                    note = "Toggle ON inside Kaggle Notebook -> Add-ons -> Secrets when Direct Kaggle -> Meta Reels Upload is enabled."
                )
            )
        ),
        PlatformGuideSpec(
            id = PlatformGuideId.SUPABASE_CLOUD,
            title = "Supabase PostgreSQL Cloud DB, Google PKCE & RBAC",
            badge = "GZIP Text Sync",
            summary = "Sync Chats, Video Scripts, Social SEO, Niches, Projects, Tasks & AI Memory across devices using GZIP-compressed text rows (<=256 KB) with Google OAuth PKCE and server-enforced PostgreSQL RLS.",
            faqNote = "First-Owner Rule: The first Google account to create a profile while no OWNER exists becomes OWNER (is_approved = true). Every subsequent account defaults to PENDING until an OWNER approves it and assigns EDITOR or VIEWER inside Cloud DB & Roles. Zero binary videos or images are ever uploaded to Supabase.",
            portals = listOf(
                GuidePortalLink("Open Supabase Dashboard", "https://supabase.com/dashboard"),
                GuidePortalLink("Open Google Cloud OAuth Credentials", "https://console.cloud.google.com/apis/credentials")
            ),
            steps = listOf(
                "Create a free Supabase project, open SQL Editor, paste the complete contents of supabase/schema_and_rls.sql from this repository (or run 'supabase schema' in an OWNER/debug app), and click Run.",
                "Go to Project Settings -> API and add the Project URL and anon public key to omniai-studio/local.properties as SUPABASE_URL and SUPABASE_ANON_KEY, then rebuild the app. For GitHub Actions builds, add them as repository Actions secrets with those same names.",
                "In Supabase Authentication -> URL Configuration, add the mobile deep-link Redirect URL: omniaistudio://auth-callback",
                "In Supabase Authentication -> Providers -> Google, enable Google using a Google Cloud Web OAuth Client ID & Secret whose Authorized redirect URI is https://<project-ref>.supabase.co/auth/v1/callback.",
                "Add SUPABASE_URL and SUPABASE_ANON_KEY to your GitHub Repo -> Settings -> Secrets and variables -> Actions so Android builds bundle the project connection and .github/workflows/keep-supabase-alive.yml can ping every 4 days."
            ),
            commands = listOf(
                GuideCommandBlock(
                    title = "Verify Supabase PostgREST Health Endpoint (curl)",
                    command = supabaseCurlVerifyCommand,
                    canRunInTerminal = true,
                    note = "Replace <project-ref> and YOUR_SUPABASE_ANON_KEY to verify live PostgREST reachability."
                ),
                GuideCommandBlock(
                    title = "Redirect URIs, Callback & Anti-Pause Secrets Checklist (SQL Reference)",
                    command = supabaseSqlQuickBootstrap,
                    canRunInTerminal = false,
                    note = "Execute supabase/schema_and_rls.sql in Supabase SQL Editor first to activate tables, triggers, and strict RLS."
                )
            )
        )
    )

    fun get(id: PlatformGuideId): PlatformGuideSpec =
        allGuides.first { it.id == id }
}
