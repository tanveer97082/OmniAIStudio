# OmniAI-Studio v4.5

Hardened Native Android Application (Kotlin + Jetpack Compose + Material 3 Live Theme Studio), **v4.5 Inbuilt Project Management System (Kanban Board + Monospace Code Editor + AI `### FILE:` Multi-File Splitter + Chat-to-Project Bridge), Zero-Dependency Atomic GitHub Git Data API Auto-Push/Pull Engine, Live GitHub Actions Build Monitor & 1-Tap OTA APK Installer**, v4.0 Master Niche Context Engine & Auto-Next Video Continuity, Normalized Relational Room DB v9, Unified Content DB & Social Publishing Hub, Built-in Hybrid Terminal (`LocalShellExecutor` + `OmniCLI` + Termux Bridge + Live Masked `netlog`), 4-Layer Encrypted Backup & Cloud Sync Suite, Dual-GPU Kaggle Wan 2.1 Video Rendering Worker, and Automated GitHub Actions CI/CD Monorepo.

## Locked Build Matrix (Zero-Error on Ubuntu GitHub Runners)
- **JDK**: 17 (Temurin) | **Gradle**: 8.5 | **AGP**: 8.2.2
- **Kotlin**: 1.9.22 | **KSP**: 1.9.22-1.0.17 | **Compose Compiler**: 1.5.8 | **Compose BOM**: 2024.02.02
- **SDKs**: `compileSdk = 34`, `targetSdk = 34`, `minSdk = 26`
- **Strict Zero-New-Dependency Stack (Zero JGit)**: Room `2.6.1` (KSP, Schema v9), Security Crypto `1.1.0-alpha06`, Biometric `1.1.0`, OkHttp3 `4.12.0` + `okhttp-sse:4.12.0`, Kotlinx Serialization `1.6.3`, AndroidX Media3 `1.2.1` (`ExoPlayer` + `Transformer`), ML Kit Text Recognition `16.0.0` (+ Devanagari `16.0.0`), WorkManager `2.9.0`, Jsoup `1.17.2`, Material Icons Extended. All v3.0–v4.5 additions use existing libraries and native Android SDK APIs (`MessageDigest`, `android.util.Base64`) only.

## v4.5 Project Management System, AI File Splitter & Atomic Git Auto-Push Engine
- **Relational PMS Database (`data/local/Entities.kt`, `Daos.kt`, `OmniAiDatabase.kt` v9)**:
  - `ProjectEntity` (`projects`), `ProjectTaskEntity` (`project_tasks` with `CASCADE` FK + `@Index("projectId")`), `ProjectFileIndexEntity` (`project_file_indexes`), and `GitCommitLogEntity` (`git_commit_logs`).
- **Project Dashboard & Workspace (`ui/screens/ProjectDashboardScreen.kt`, `projects/AiMultiFileSplitter.kt`)**:
  - Accessible via customizable Bottom Navigation (`Projects`), Universal Command Palette, Quick FAB (`Git Auto-Push`), and the Android Home Screen Widget.
  - Project Cards with live `Done / Total` Kanban progress bar, GitHub `owner/repo@branch` badge, pending local file diff count (`N modified`), and 1-tap Git Auto-Push button.
  - 4 workspace sub-tabs inside every project:
    1. **Kanban Tasks**: `TO_DO | IN_PROGRESS | REVIEW | DONE` filters, quick move-stage arrows, priority badges, Add/Edit Task dialog, and **"AI Auto-Plan Tasks"**.
    2. **Files & Code Editor**: Local file explorer under `filesDir/projects/{slug}/` with `[NEW]` / `[MOD]` / `[SYNCED]` SHA-256 diff badges, monospace Code Editor (Save + Undo), New File/Folder, Delete File, and **"Paste AI Multi-File Output (`### FILE: ...`) Splitter"** with canonical path confinement.
    3. **Git & CI/CD Control**: Pending diff preview, Commit Message field with **"AI Auto-Generate Commit Msg"**, **"Git Auto-Push Now"**, **"Pull Latest from GitHub"**, **"Create New Private GitHub Repo"**, Commit History log, and live **GitHub Actions Build Monitor** (`QUEUED` → `IN_PROGRESS` → `SUCCESS` / `FAILURE`, elapsed timer, 1-tap **"Install Updated APK"** OTA button on success, **"Copy Build Run URL & Failure Summary"** on failure).
    4. **Project Notes & Linked Content**: Architecture notes + clickable links to Niche Video Scripts and Chat threads.
- **Zero-Dependency Atomic GitHub Git Data API Engine (`git/GitHubGitEngine.kt`, `worker/GitAutoPushWorker.kt`)**:
  - Uses ONLY `OkHttp3 4.12.0` + `Kotlinx Serialization` + `MessageDigest` SHA-256 + `Base64.NO_WRAP` + keystore-encrypted `GITHUB_TOKEN`.
  - Executes the full 6-step GitHub Git Data API transaction (Branch Ref + empty-repo 404/409 `README.md` bootstrap → Base Commit Tree → Base64 Blobs → Tree with `sha: null` deletions → Commit → Branch Ref PATCH) so N changed files trigger ONLY ONE GitHub Actions run.
  - `GitAutoPushWorker` (`CoroutineWorker` with `NetworkType.CONNECTED`) runs automatic background atomic pushes when `project.autoPushOnSave == true`.
- **Chat-to-Project Bridge, OmniCLI Git Commands, Settings & Backup v45**:
  - **"Save to Project"** chip on AI chat replies containing code blocks or `### FILE:` markers (`ui/screens/SaveToProjectDialog.kt`).
  - Hybrid Terminal `OmniCliInterpreter.kt`: `proj list`, `proj switch <slug>`, `git status`, `git push "<message>"`, `git pull`, `git actions`.
  - Settings section **3D. Project & Git Automation** (`Default GitHub Username`, `Default Branch`, `Auto-Watch GitHub Actions After Push`, `AI Auto-Commit Message`) + full AES-256-GCM Backup/Restore sync (`schemaVersion = 45`).

## v4.0 Master Niche Context Engine, Auto-Next Video Continuity & Auto-Typer
- **Master Niche Selector & Context Binding (`niche/NicheContextBuilder.kt`, `ui/components/NicheSelectorPill.kt`)**:
  - Room `NicheEntity` (`niches`) pre-seeded with 5 customizable starter niches plus 1-tap **"AI Auto-Fill Niche Blueprint"**, indexed `nicheId` scoping across Chats, Video Scripts, Social Posts, Memory Facts, Storyboards, and Render Jobs, and Smart Rolling Context Compression.
- **Auto-Next Video Continuity & Auto-Typing Prompt Selector (`niche/ContinuityEngine.kt`, `ui/components/AutoTypePromptBar.kt`)**:
  - Post-video & post-chat non-repeating command suggestions, character-by-character typewriter animation, 30-Day Series Planner (`SeriesPlannerSheet.kt`), Pre-Render Viral Hook Auditor (`ScriptAuditorDialog.kt`), and Seamless Loop Mode (`kaggle_worker/video_worker.py`).

## v1.0–v3.5 Core Modules Preserved 100%
- **Normalized Relational Content DBs & Creator Publishing Suite (`ContentHubScreen.kt`, `ContentDbExporter.kt`, `PublishReminderWorker.kt`)**, **Built-in Hybrid Terminal (`terminal/` + `TerminalScreen.kt`)**, **Deep UI Theme Studio (`UiConfigRepository.kt` + `UiThemeStudioScreen.kt`)**, **4-Layer Encrypted Backup Suite**, **Sandboxed HTML Live Previewer**, **Multi-Step Prompt Chains**, **Multi-Workspace Profiles**, **HF Model Explorer**, **Media3 Video Compressor & Merger**, **Compose Canvas Analytics**, **Webhooks**, **Dynamic Shortcuts**, **4 Pure-XML Adaptive Stealth Icons**, **Biometric + Master/Decoy Duress PIN**, **Failed PIN Auto-Wipe**, **Multi-Model Arena**, **Incognito RAM Chat**, **ML Kit OCR (HI/EN)**, **Offline PdfRenderer**, **Jsoup Web Reader**, **Character DNA**, **Kaggle Pool Auto-Rotation**, **Audio Studio**, **GitHub Remote Worker Editor**, **Tunnel Watchdog**, **RSS News Agent**, **Quick Settings Voice Tile**, and **AMOLED Home Screen Widget**.

## GitHub Secrets Setup
1. **Kaggle Worker Sync (`.github/workflows/sync-kaggle-worker.yml`)**:
   - `KAGGLE_USERNAME`
   - `KAGGLE_KEY`
2. **Optional Signed Release APK (`.github/workflows/build-release-apk.yml`)**:
   - `KEYSTORE_BASE64`, `KEYSTORE_PASS`, `KEY_ALIAS`, `KEY_PASS`
   - If omitted, the workflow automatically falls back to `gradle assembleDebug` and publishes `OmniAI-Studio-v4.5.<run_number>.apk` to GitHub Releases.

## Local Build
```bash
gradle assembleDebug -PVERSION_CODE=1 -PVERSION_NAME=4.5.1
```
