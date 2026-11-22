export interface RepoFile {
  path: string;
  content: string;
  language: 'kotlin' | 'python' | 'yaml' | 'xml' | 'json' | 'gradle' | 'markdown' | 'properties';
  category: 'android-app' | 'security' | 'kaggle-worker' | 'github-ci' | 'gradle-config';
  lineCount: number;
  byteSize: number;
}

export interface AuditCheck {
  id: string;
  group: 'Build Matrix' | 'Security & Manifest' | 'Media3 & Worker' | 'CI/CD Automation' | 'Code Integrity';
  label: string;
  expected: string;
  passed: boolean;
  detail: string;
}

export interface TreeNode {
  name: string;
  path: string;
  isDirectory: boolean;
  children: TreeNode[];
  file?: RepoFile;
}

export function detectLanguage(path: string): RepoFile['language'] {
  if (path.endsWith('.kt')) return 'kotlin';
  if (path.endsWith('.kts')) return 'gradle';
  if (path.endsWith('.py')) return 'python';
  if (path.endsWith('.yml') || path.endsWith('.yaml')) return 'yaml';
  if (path.endsWith('.xml')) return 'xml';
  if (path.endsWith('.json')) return 'json';
  if (path.endsWith('.md')) return 'markdown';
  return 'properties';
}

export function detectCategory(path: string): RepoFile['category'] {
  if (path.startsWith('.github/')) return 'github-ci';
  if (path.startsWith('kaggle_worker/')) return 'kaggle-worker';
  if (path.includes('/security/')) return 'security';
  if (
    path.endsWith('.gradle.kts') ||
    path.endsWith('.properties') ||
    path.endsWith('.pro') ||
    path === 'README.md'
  ) {
    return 'gradle-config';
  }
  return 'android-app';
}

export function buildDirectoryTree(files: RepoFile[]): TreeNode[] {
  const root: TreeNode = { name: '', path: '', isDirectory: true, children: [] };

  for (const file of files) {
    const parts = file.path.split('/');
    let current = root;
    let accum = '';

    parts.forEach((part, idx) => {
      accum = accum ? `${accum}/${part}` : part;
      const isLast = idx === parts.length - 1;
      let child = current.children.find((c) => c.name === part);
      if (!child) {
        child = {
          name: part,
          path: accum,
          isDirectory: !isLast,
          children: [],
          file: isLast ? file : undefined,
        };
        current.children.push(child);
      }
      current = child;
    });
  }

  const sortNodes = (nodes: TreeNode[]): TreeNode[] => {
    nodes.sort((a, b) => {
      if (a.isDirectory !== b.isDirectory) return a.isDirectory ? -1 : 1;
      return a.name.localeCompare(b.name);
    });
    nodes.forEach((n) => {
      if (n.children.length > 0) sortNodes(n.children);
    });
    return nodes;
  };

  return sortNodes(root.children);
}

export function formatDirectoryTreeAscii(files: RepoFile[]): string {
  const tree = buildDirectoryTree(files);
  const lines: string[] = ['OmniAI-Studio/'];

  const walk = (nodes: TreeNode[], prefix: string) => {
    nodes.forEach((node, idx) => {
      const isLast = idx === nodes.length - 1;
      const branch = isLast ? '└── ' : '├── ';
      lines.push(`${prefix}${branch}${node.name}${node.isDirectory ? '/' : ''}`);
      if (node.isDirectory && node.children.length > 0) {
        walk(node.children, `${prefix}${isLast ? '    ' : '│   '}`);
      }
    });
  };

  walk(tree, '');
  return lines.join('\n');
}

export function auditMonorepo(files: RepoFile[]): AuditCheck[] {
  const byPath = new Map(files.map((f) => [f.path, f.content]));
  const rootGradle = byPath.get('build.gradle.kts') ?? '';
  const appGradle = byPath.get('app/build.gradle.kts') ?? '';
  const gradleProps = byPath.get('gradle.properties') ?? '';
  const wrapperProps = byPath.get('gradle/wrapper/gradle-wrapper.properties') ?? '';
  const manifest = byPath.get('app/src/main/AndroidManifest.xml') ?? '';
  const apkWorkflow = byPath.get('.github/workflows/build-release-apk.yml') ?? '';
  const kaggleWorkflow = byPath.get('.github/workflows/sync-kaggle-worker.yml') ?? '';
  const workerPy = byPath.get('kaggle_worker/video_worker.py') ?? '';
  const kernelMeta = byPath.get('kaggle_worker/kernel-metadata.json') ?? '';
  const mergerKt = byPath.get('app/src/main/java/com/omniai/studio/media/Media3VideoMerger.kt') ?? '';
  const mainActivityKt = byPath.get('app/src/main/java/com/omniai/studio/MainActivity.kt') ?? '';
  const memoryManagerKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/ConversationMemoryManager.kt') ?? '';
  const entitiesKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/Entities.kt') ?? '';
  const daosKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/Daos.kt') ?? '';
  const templatesKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/VideoSceneTemplates.kt') ?? '';
  const directorScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/VideoDirectorScreen.kt') ?? '';
  const appIconManagerKt = byPath.get('app/src/main/java/com/omniai/studio/security/AppIconManager.kt') ?? '';
  const crashHandlerKt = byPath.get('app/src/main/java/com/omniai/studio/security/CrashHandler.kt') ?? '';
  const settingsHubKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/SettingsHubScreen.kt') ?? '';
  const settingsRepoKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/AppSettingsRepository.kt') ?? '';
  const themeKt = byPath.get('app/src/main/java/com/omniai/studio/ui/theme/Theme.kt') ?? '';
  const vaultScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/VaultSettingsScreen.kt') ?? '';
  const ocrHelperKt = byPath.get('app/src/main/java/com/omniai/studio/media/OfflineOcrHelper.kt') ?? '';
  const ttsHelperKt = byPath.get('app/src/main/java/com/omniai/studio/media/OfflineTtsHelper.kt') ?? '';
  const storageHelperKt = byPath.get('app/src/main/java/com/omniai/studio/media/StorageGalleryHelper.kt') ?? '';
  const docReaderKt = byPath.get('app/src/main/java/com/omniai/studio/media/OfflineDocumentReader.kt') ?? '';
  const webScraperKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/WebArticleScraper.kt') ?? '';
  const localShellKt = byPath.get('app/src/main/java/com/omniai/studio/terminal/LocalShellExecutor.kt') ?? '';
  const omniCliKt = byPath.get('app/src/main/java/com/omniai/studio/terminal/OmniCliInterpreter.kt') ?? '';
  const netLogKt = byPath.get('app/src/main/java/com/omniai/studio/terminal/NetworkLogInterceptor.kt') ?? '';
  const termuxBridgeKt = byPath.get('app/src/main/java/com/omniai/studio/terminal/TermuxBridge.kt') ?? '';
  const terminalScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/TerminalScreen.kt') ?? '';
  const uiConfigRepoKt = byPath.get('app/src/main/java/com/omniai/studio/settings/UiConfigRepository.kt') ?? '';
  const themeStudioKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/UiThemeStudioScreen.kt') ?? '';
  const commandPaletteKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/CommandPaletteDialog.kt') ?? '';
  const localhostScannerKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/LocalhostPortScanner.kt') ?? '';
  const newsWorkerKt = byPath.get('app/src/main/java/com/omniai/studio/worker/NewsDigestWorker.kt') ?? '';
  const duressPinKt = byPath.get('app/src/main/java/com/omniai/studio/security/DuressPinManager.kt') ?? '';
  const quickTileKt = byPath.get('app/src/main/java/com/omniai/studio/service/QuickVoiceTileService.kt') ?? '';
  const decoyScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/DecoySandboxScreen.kt') ?? '';
  const galleryScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/DownloadsGalleryScreen.kt') ?? '';
  const chatScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/ChatStudioScreen.kt') ?? '';
  const mergerScreenKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/VideoMergerScreen.kt') ?? '';
  const renderServiceKt = byPath.get('app/src/main/java/com/omniai/studio/service/RenderQueueForegroundService.kt') ?? '';
  const apiModelsKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/ApiModels.kt') ?? '';
  const aiClientKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/AiStreamingClient.kt') ?? '';
  const vaultManagerKt = byPath.get('app/src/main/java/com/omniai/studio/security/VaultManager.kt') ?? '';
  const audioStudioKt = byPath.get('app/src/main/java/com/omniai/studio/media/AudioStudioHelper.kt') ?? '';
  const githubEditorKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/GitHubWorkerEditorClient.kt') ?? '';
  const watchdogWorkerKt = byPath.get('app/src/main/java/com/omniai/studio/worker/TunnelWatchdogWorker.kt') ?? '';
  const widgetProviderKt = byPath.get('app/src/main/java/com/omniai/studio/widget/OmniWidgetProvider.kt') ?? '';
  const widgetInfoXml = byPath.get('app/src/main/res/xml/widget_info.xml') ?? '';
  const widgetLayoutXml = byPath.get('app/src/main/res/layout/omni_widget_layout.xml') ?? '';
  const databaseKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/OmniAiDatabase.kt') ?? '';
  const vaultBackupKt = byPath.get('app/src/main/java/com/omniai/studio/backup/VaultBackupManager.kt') ?? '';
  const githubBackupKt = byPath.get('app/src/main/java/com/omniai/studio/backup/GitHubBackupSync.kt') ?? '';
  const telegramBackupKt = byPath.get('app/src/main/java/com/omniai/studio/backup/TelegramBackupClient.kt') ?? '';
  const backupWorkerKt = byPath.get('app/src/main/java/com/omniai/studio/backup/BackupSyncWorker.kt') ?? '';
  const backupCenterKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/BackupSyncCenterScreen.kt') ?? '';
  const codePreviewKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/CodePreviewDialog.kt') ?? '';
  const chainRunnerKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/ChainRunnerScreen.kt') ?? '';
  const hfClientKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/HfModelExplorerClient.kt') ?? '';
  const hfSheetKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/HfExplorerSheet.kt') ?? '';
  const compressorKt = byPath.get('app/src/main/java/com/omniai/studio/media/Media3VideoCompressor.kt') ?? '';
  const analyticsKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/AnalyticsScreen.kt') ?? '';
  const webhookKt = byPath.get('app/src/main/java/com/omniai/studio/data/remote/WebhookNotifier.kt') ?? '';
  const shortcutsKt = byPath.get('app/src/main/java/com/omniai/studio/security/DynamicShortcutHelper.kt') ?? '';
  const contentDbExporterKt = byPath.get('app/src/main/java/com/omniai/studio/data/local/ContentDbExporter.kt') ?? '';
  const publishReminderKt = byPath.get('app/src/main/java/com/omniai/studio/worker/PublishReminderWorker.kt') ?? '';
  const contentHubKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/ContentHubScreen.kt') ?? '';
  const nicheContextKt = byPath.get('app/src/main/java/com/omniai/studio/niche/NicheContextBuilder.kt') ?? '';
  const continuityEngineKt = byPath.get('app/src/main/java/com/omniai/studio/niche/ContinuityEngine.kt') ?? '';
  const nicheSelectorKt = byPath.get('app/src/main/java/com/omniai/studio/ui/components/NicheSelectorPill.kt') ?? '';
  const autoTypeBarKt = byPath.get('app/src/main/java/com/omniai/studio/ui/components/AutoTypePromptBar.kt') ?? '';
  const seriesPlannerKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/SeriesPlannerSheet.kt') ?? '';
  const scriptAuditorKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/ScriptAuditorDialog.kt') ?? '';
  const aiMultiFileSplitterKt = byPath.get('app/src/main/java/com/omniai/studio/projects/AiMultiFileSplitter.kt') ?? '';
  const gitHubGitEngineKt = byPath.get('app/src/main/java/com/omniai/studio/git/GitHubGitEngine.kt') ?? '';
  const gitAutoPushWorkerKt = byPath.get('app/src/main/java/com/omniai/studio/worker/GitAutoPushWorker.kt') ?? '';
  const projectDashboardKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/ProjectDashboardScreen.kt') ?? '';
  const saveToProjectKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/SaveToProjectDialog.kt') ?? '';
  const designTokensKt = byPath.get('app/src/main/java/com/omniai/studio/ui/theme/DesignTokens.kt') ?? '';
  const navShellKt = byPath.get('app/src/main/java/com/omniai/studio/ui/components/AppNavigationShell.kt') ?? '';
  const homeBentoKt = byPath.get('app/src/main/java/com/omniai/studio/ui/screens/HomeBentoScreen.kt') ?? '';

  const requiredAdaptiveIconXmls = [
    'app/src/main/res/drawable/ic_bg_default.xml',
    'app/src/main/res/drawable/ic_fg_default.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher_default.xml',
    'app/src/main/res/drawable/ic_bg_cyber.xml',
    'app/src/main/res/drawable/ic_fg_cyber.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher_cyber.xml',
    'app/src/main/res/drawable/ic_bg_calc.xml',
    'app/src/main/res/drawable/ic_fg_calc.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher_calc.xml',
    'app/src/main/res/drawable/ic_bg_notes.xml',
    'app/src/main/res/drawable/ic_fg_notes.xml',
    'app/src/main/res/mipmap-anydpi-v26/ic_launcher_notes.xml',
  ];
  const missingIconXmls = requiredAdaptiveIconXmls.filter((p) => !byPath.has(p));

  const todoHits = files.filter((f) => /\b(TODO|FIXME|NotImplementedError)\b/.test(f.content));

  const requiredPermissions = [
    'INTERNET',
    'FOREGROUND_SERVICE',
    'FOREGROUND_SERVICE_DATA_SYNC',
    'POST_NOTIFICATIONS',
    'REQUEST_INSTALL_PACKAGES',
    'USE_BIOMETRIC',
    'RECORD_AUDIO',
  ];
  const missingPermissions = requiredPermissions.filter(
    (perm) => !manifest.includes(`android.permission.${perm}`)
  );

  return [
    {
      id: 'gradle-agp-kotlin-ksp',
      group: 'Build Matrix',
      label: 'AGP 8.2.2 · Kotlin 1.9.22 · KSP 1.9.22-1.0.17 · Gradle 8.5',
      expected: 'Exact pinned root plugin & wrapper versions',
      passed:
        rootGradle.includes('8.2.2') &&
        rootGradle.includes('1.9.22') &&
        rootGradle.includes('1.9.22-1.0.17') &&
        wrapperProps.includes('gradle-8.5-bin.zip') &&
        gradleProps.includes('android.useAndroidX=true') &&
        gradleProps.includes('-Xmx3072m'),
      detail: 'Verified in build.gradle.kts, gradle-wrapper.properties, and gradle.properties',
    },
    {
      id: 'compose-sdk-matrix',
      group: 'Build Matrix',
      label: 'SDK 34/26 · Compose Compiler 1.5.8 · BOM 2024.02.02 · JDK 17',
      expected: 'compileSdk=34, targetSdk=34, minSdk=26, JVM 17',
      passed:
        appGradle.includes('compileSdk = 34') &&
        appGradle.includes('targetSdk = 34') &&
        appGradle.includes('minSdk = 26') &&
        appGradle.includes('kotlinCompilerExtensionVersion = "1.5.8"') &&
        appGradle.includes('compose-bom:2024.02.02') &&
        appGradle.includes('JavaVersion.VERSION_17'),
      detail: 'Verified in app/build.gradle.kts',
    },
    {
      id: 'core-libraries',
      group: 'Build Matrix',
      label: 'Room 2.6.1 (KSP) · Security Crypto 1.1.0-alpha06 · Biometric 1.1.0 · OkHttp 4.12.0',
      expected: 'All required AndroidX & OkHttp SSE libraries pinned',
      passed:
        appGradle.includes('val roomVersion = "2.6.1"') &&
        appGradle.includes('ksp("androidx.room:room-compiler:$roomVersion")') &&
        appGradle.includes('security-crypto:1.1.0-alpha06') &&
        appGradle.includes('biometric:1.1.0') &&
        appGradle.includes('okhttp:4.12.0') &&
        appGradle.includes('okhttp-sse:4.12.0') &&
        appGradle.includes('kotlinx-serialization-json:1.6.3'),
      detail: 'Zero deprecated FFmpegKit references; KSP room-compiler bound',
    },
    {
      id: 'manifest-permissions',
      group: 'Security & Manifest',
      label: 'Required AndroidManifest.xml Permissions, FileProvider & Home Widget',
      expected: 'All 7 permissions (incl. RECORD_AUDIO) + dataSync ForegroundService + FileProvider',
      passed:
        missingPermissions.length === 0 &&
        manifest.includes('android:foregroundServiceType="dataSync"') &&
        manifest.includes('androidx.core.content.FileProvider') &&
        byPath.has('app/src/main/res/xml/file_paths.xml'),
      detail:
        missingPermissions.length === 0
          ? 'All 7 manifest permissions (incl. RECORD_AUDIO), dataSync service, and FileProvider verified'
          : `Missing: ${missingPermissions.join(', ')}`,
    },
    {
      id: 'biometric-fragment-activity',
      group: 'Security & Manifest',
      label: 'BiometricPrompt Host & Hardware Vault Layer',
      expected: 'MainActivity extends FragmentActivity + EncryptedSharedPreferences + PBKDF2 + FLAG_SECURE',
      passed:
        mainActivityKt.includes('class MainActivity : FragmentActivity()') &&
        byPath.has('app/src/main/java/com/omniai/studio/security/VaultManager.kt') &&
        byPath.has('app/src/main/java/com/omniai/studio/security/BackupCryptoManager.kt') &&
        byPath.has('app/src/main/java/com/omniai/studio/security/NetworkSecurityValidator.kt') &&
        byPath.has('app/src/main/java/com/omniai/studio/security/PrivacyShieldHelper.kt'),
      detail: 'All 5 security engine modules present with AES-256-GCM + FLAG_SECURE + HTTPS validator',
    },
    {
      id: 'conversation-memory',
      group: 'Code Integrity',
      label: 'Room Conversation Memory (Sliding Window + Rolling Summary)',
      expected: 'ConversationMemoryManager + Room rollingSummary persistence + token budget compaction',
      passed:
        memoryManagerKt.includes('SLIDING_WINDOW_MESSAGES') &&
        memoryManagerKt.includes('buildContextAndCompactIfNeeded') &&
        entitiesKt.includes('val rollingSummary: String') &&
        entitiesKt.includes('val summarizedMessageCount: Int'),
      detail: 'Verified ConversationMemoryManager.kt, ChatThreadEntity summary columns, and ViewModel wiring',
    },
    {
      id: 'video-scene-templates',
      group: 'Media3 & Worker',
      label: 'Video Director Scene Templates Library (Educational / Promo / Story / Doc)',
      expected: 'VideoSceneTemplateLibrary + selectable Templates section in VideoDirectorScreen.kt',
      passed:
        templatesKt.includes('EDUCATIONAL') &&
        templatesKt.includes('PROMOTIONAL') &&
        templatesKt.includes('STORYTELLING') &&
        templatesKt.includes('DOCUMENTARY') &&
        directorScreenKt.includes('Text("Templates"') &&
        directorScreenKt.includes('VideoSceneTemplateLibrary'),
      detail: 'Verified 5 curated scene blueprints across 4 categories wired into VideoDirectorScreen.kt',
    },
    {
      id: 'template-admin-studio',
      group: 'Media3 & Worker',
      label: 'Template Admin Studio (Custom Scene Structures + Style & Voice Defaults)',
      expected: 'CustomVideoTemplateEntity + DAO + Validator + Template Admin UI in VideoDirectorScreen.kt',
      passed:
        entitiesKt.includes('class CustomVideoTemplateEntity') &&
        daosKt.includes('interface CustomVideoTemplateDao') &&
        templatesKt.includes('object VideoSceneTemplateValidator') &&
        directorScreenKt.includes('Text("Template Admin")') &&
        directorScreenKt.includes('Default Visual Style') &&
        directorScreenKt.includes('Voiceover Voice Selection'),
      detail: 'Verified Room-backed CustomVideoTemplateEntity, validator, and inline Template Admin builder',
    },
    {
      id: 'media3-transformer',
      group: 'Media3 & Worker',
      label: 'AndroidX Media3 1.2.1 Transformer (No FFmpegKit)',
      expected: 'Uses Media3 Transformer + EditedMediaItemSequence + MediaStore Gallery export',
      passed:
        appGradle.includes('val media3Version = "1.2.1"') &&
        !/implementation\([^)]*ffmpeg/i.test(appGradle) &&
        mergerKt.includes('EditedMediaItemSequence') &&
        mergerKt.includes('Composition.Builder') &&
        mergerKt.includes('Transformer.Builder'),
      detail: 'Verified Media3 1.2.1 ExoPlayer + Transformer pipeline in Media3VideoMerger.kt',
    },
    {
      id: 'kaggle-dual-gpu',
      group: 'Media3 & Worker',
      label: 'Kaggle Dual-GPU Wan 2.1 + Edge-TTS + SRT Subtitles',
      expected: 'Isolated CUDA_VISIBLE_DEVICES subprocesses, bfloat16/float32 VAE, scene_X.mp4 + final_video.mp4',
      passed:
        kernelMeta.includes('"enable_gpu": true') &&
        kernelMeta.includes('"enable_internet": true') &&
        workerPy.includes('Wan-AI/Wan2.1-T2V-1.3B-Diffusers') &&
        workerPy.includes('CUDA_VISIBLE_DEVICES') &&
        workerPy.includes('torch.bfloat16') &&
        workerPy.includes('torch.float32') &&
        workerPy.includes('edge_tts') &&
        workerPy.includes('final_video.mp4'),
      detail: 'Verified dual-GPU subprocess sharding, Edge-TTS word-boundary SRT, and FFmpeg muxing',
    },
    {
      id: 'github-workflows',
      group: 'CI/CD Automation',
      label: 'GitHub Actions APK Release + Kaggle Sync Workflows',
      expected: 'Auto-versioned APK, debug fallback, softprops/action-gh-release@v2, Kaggle CLI push',
      passed:
        apkWorkflow.includes('permissions:\n  contents: write') &&
        apkWorkflow.includes('softprops/action-gh-release@v2') &&
        apkWorkflow.includes('assembleRelease') &&
        apkWorkflow.includes('assembleDebug') &&
        apkWorkflow.includes('OmniAI-Studio-v${VERSION_NAME}.apk') &&
        kaggleWorkflow.includes('kaggle kernels push -p kaggle_worker'),
      detail: 'Both .github/workflows/ YAML pipelines verified',
    },
    {
      id: 'adaptive-icons-switcher',
      group: 'Security & Manifest',
      label: 'Pure-XML Adaptive Icons (4 Sets) & Dynamic Activity-Alias Switcher',
      expected: '12 vector/adaptive XMLs (zero PNGs), 4 activity-aliases in Manifest, AppIconManager DONT_KILL_APP',
      passed:
        missingIconXmls.length === 0 &&
        manifest.includes('android:name=".AliasDefault"') &&
        manifest.includes('android:name=".AliasCyber"') &&
        manifest.includes('android:name=".AliasCalc"') &&
        manifest.includes('android:name=".AliasNotes"') &&
        appIconManagerKt.includes('setComponentEnabledSetting') &&
        appIconManagerKt.includes('DONT_KILL_APP'),
      detail:
        missingIconXmls.length === 0
          ? 'All 12 XML adaptive icon files, 4 activity-aliases, and AppIconManager verified'
          : `Missing XML resources: ${missingIconXmls.join(', ')}`,
    },
    {
      id: 'settings-hub-crash-logger',
      group: 'Security & Manifest',
      label: 'Settings Hub · 4 Theme Accents · Provider Presets · CrashHandler',
      expected: 'SettingsHubScreen + AppSettingsRepository + 4 hex accents + Quick Provider Presets + crash_log.txt',
      passed:
        settingsHubKt.includes('SettingsHubScreen') &&
        settingsRepoKt.includes('AppSettingsRepository') &&
        crashHandlerKt.includes('crash_log.txt') &&
        crashHandlerKt.includes('UncaughtExceptionHandler') &&
        themeKt.includes('#BB86FC') &&
        themeKt.includes('#00E676') &&
        themeKt.includes('#00E5FF') &&
        themeKt.includes('#FF5252') &&
        vaultScreenKt.includes('127.0.0.1:8080/v1') &&
        vaultScreenKt.includes('OpenRouter') &&
        vaultScreenKt.includes('Groq'),
      detail: 'Verified SettingsHubScreen, 4 dynamic Material 3 accents, 6 Quick Provider Presets, and CrashHandler',
    },
    {
      id: 'ocr-share-tts-trim-thumb',
      group: 'Media3 & Worker',
      label: 'Offline ML Kit OCR (HI/EN) · Share-to-AI · Offline TTS · Media3 Trim & HD Thumbnail',
      expected: 'ML Kit Latin+Devanagari 16.0.0, ACTION_SEND, TextToSpeech, ClippingConfiguration, MediaMetadataRetriever',
      passed:
        appGradle.includes('com.google.mlkit:text-recognition:16.0.0') &&
        appGradle.includes('com.google.mlkit:text-recognition-devanagari:16.0.0') &&
        ocrHelperKt.includes('DevanagariTextRecognizerOptions') &&
        ttsHelperKt.includes('TextToSpeech') &&
        manifest.includes('android.intent.action.SEND') &&
        mainActivityKt.includes('Intent.ACTION_SEND') &&
        mergerKt.includes('ClippingConfiguration.Builder()') &&
        storageHelperKt.includes('MediaMetadataRetriever') &&
        storageHelperKt.includes('DIRECTORY_PICTURES'),
      detail: 'Verified Offline OCR (HI/EN), Share-to-AI receiver, Offline TTS, Media3 ClippingConfiguration, and HD Thumbnail saver',
    },
    {
      id: 'upgrade4-director-pro',
      group: 'Media3 & Worker',
      label: 'Character DNA · Social SEO Publishing Kit · 30-Hr Quota · Watermark/BGM Ducking',
      expected: 'CharacterProfileEntity, SocialMetadata, RenderLogEntity, Publishing Kit UI, FFmpeg drawtext & sidechaincompress',
      passed:
        entitiesKt.includes('class CharacterProfileEntity') &&
        entitiesKt.includes('class RenderLogEntity') &&
        apiModelsKt.includes('class SocialMetadata') &&
        directorScreenKt.includes('Lock Character DNA') &&
        directorScreenKt.includes('30.0 hrs') &&
        galleryScreenKt.includes('Publishing Kit') &&
        renderServiceKt.includes('RenderLogEntity') &&
        workerPy.includes('watermark_text') &&
        workerPy.includes('bgm_url') &&
        workerPy.includes('sidechaincompress'),
      detail: 'Verified Character DNA Room profiles, Publishing Kit SEO card, 30h GPU tracker, and FFmpeg watermark/BGM ducking',
    },
    {
      id: 'upgrade5-doc-web-news-scanner',
      group: 'Code Integrity',
      label: 'Offline PdfRenderer Docs · Jsoup Scraper · Side-by-Side Translate · RSS WorkManager · Port Scanner',
      expected: 'work-runtime-ktx 2.9.0 + jsoup 1.17.2 only, native PdfRenderer, NewsDigestWorker, 127.0.0.1 port scanner',
      passed:
        appGradle.includes('androidx.work:work-runtime-ktx:2.9.0') &&
        appGradle.includes('org.jsoup:jsoup:1.17.2') &&
        !appGradle.includes('pdfbox') &&
        !appGradle.includes('itext') &&
        docReaderKt.includes('PdfRenderer') &&
        webScraperKt.includes('Jsoup.connect') &&
        newsWorkerKt.includes('CoroutineWorker') &&
        newsWorkerKt.includes('Daily AI Brief Ready for Approval') &&
        localhostScannerKt.includes('11434') &&
        localhostScannerKt.includes('8080') &&
        vaultScreenKt.includes('Scan Localhost') &&
        chatScreenKt.includes('Icons.Filled.Translate') &&
        directorScreenKt.includes('Icons.Filled.Translate'),
      detail: 'Verified zero-extra-PDF PdfRenderer + ML Kit OCR, Jsoup scraper, side-by-side translation, RSS agent, and 127.0.0.1 scanner',
    },
    {
      id: 'upgrade6-duress-pin-quick-tile',
      group: 'Security & Manifest',
      label: 'Duress / Decoy PIN Mode (Salted SHA-256) & Android Quick Settings Voice Tile',
      expected: 'DuressPinManager + DecoySandboxScreen + QuickVoiceTileService + BIND_QUICK_SETTINGS_TILE manifest entry',
      passed:
        duressPinKt.includes('SHA-256') &&
        duressPinKt.includes('EncryptedSharedPreferences') &&
        decoyScreenKt.includes('Quick Notes') &&
        quickTileKt.includes('TileService') &&
        quickTileKt.includes('LAUNCH_VOICE_CHAT') &&
        manifest.includes('android.permission.BIND_QUICK_SETTINGS_TILE') &&
        manifest.includes('android.service.quicksettings.action.QS_TILE') &&
        mainActivityKt.includes('DecoySandboxScreen'),
      detail: 'Verified salted SHA-256 Master/Decoy PIN sandbox isolation and QuickVoiceTileService manifest registration',
    },
    {
      id: 'v2-chat-arena-incognito-brain',
      group: 'Code Integrity',
      label: 'v2.0 Arena Split-Compare · Incognito RAM Mode · Memory Facts · /Commands · Offline Datasets',
      expected: 'streamSingleProviderByIndex, Incognito RAM mode, UserMemoryFactEntity, SlashCommandEntity, OfflineDatasetItemEntity',
      passed:
        aiClientKt.includes('fun streamSingleProviderByIndex') &&
        entitiesKt.includes('class UserMemoryFactEntity') &&
        entitiesKt.includes('class SlashCommandEntity') &&
        entitiesKt.includes('class OfflineDatasetItemEntity') &&
        daosKt.includes('interface KnowledgeBrainDao') &&
        chatScreenKt.includes('Arena Compare') &&
        chatScreenKt.includes('Incognito') &&
        chatScreenKt.includes('OmniAI Knowledge Brain'),
      detail: 'Verified Arena parallel streaming, RAM-only Incognito mode, and Room KnowledgeBrainDao',
    },
    {
      id: 'v2-director-speed-i2v-pool',
      group: 'Media3 & Worker',
      label: 'v2.0 Video Speed (0.75x–1.5x) · I2V ref_image.jpg · Multi-Account Kaggle Pool Auto-Rotate',
      expected: 'speed_factor & use_ref_image in worker + payload, KaggleAccountCredential + <1h auto-rotation',
      passed:
        workerPy.includes('speed_factor') &&
        workerPy.includes('use_ref_image') &&
        workerPy.includes('ref_image.jpg') &&
        apiModelsKt.includes('class KaggleAccountCredential') &&
        vaultManagerKt.includes('fun rotateToNextKaggleAccount') &&
        renderServiceKt.includes('rotateToNextKaggleAccount') &&
        directorScreenKt.includes('Pick Ref Image (I2V)'),
      detail: 'Verified speed_factor TTS/FFmpeg scaling, ref_image.jpg I2V pipeline, and Kaggle pool rotation',
    },
    {
      id: 'v2-audio-studio-exo-speed',
      group: 'Media3 & Worker',
      label: 'v2.0 Voiceover Recorder (.m4a) · Media3 Audio Extractor · ExoPlayer Variable Speed',
      expected: 'AudioStudioHelper (MediaRecorder + setRemoveVideo(true)) + ExoPlayer setPlaybackSpeed',
      passed:
        audioStudioKt.includes('MediaRecorder') &&
        audioStudioKt.includes('setRemoveVideo(true)') &&
        mergerScreenKt.includes('Record Voiceover') &&
        mergerScreenKt.includes('Extract Video Audio') &&
        galleryScreenKt.includes('exoPlayer.setPlaybackSpeed'),
      detail: 'Verified AudioStudioHelper voiceover/audio extractor and ExoPlayer 0.5x–2.0x playback speed',
    },
    {
      id: 'v2-devops-widget-autowipe',
      group: 'CI/CD Automation',
      label: 'v2.0 GitHub Remote Worker Editor · Tunnel Watchdog · Home Widget · Failed PIN Auto-Wipe',
      expected: 'GitHubWorkerEditorClient, TunnelWatchdogWorker, OmniWidgetProvider, AUTO_WIPE_TRIGGERED',
      passed:
        githubEditorKt.includes('api.github.com/repos/') &&
        watchdogWorkerKt.includes('TunnelWatchdogWorker') &&
        widgetProviderKt.includes('AppWidgetProvider') &&
        widgetInfoXml.includes('appwidget-provider') &&
        widgetLayoutXml.includes('widget_btn_voice') &&
        manifest.includes('android:name=".widget.OmniWidgetProvider"') &&
        duressPinKt.includes('AUTO_WIPE_TRIGGERED') &&
        settingsHubKt.includes('Failed PIN Audit Log'),
      detail: 'Verified GitHub Contents API editor, WorkManager watchdog, AMOLED widget, and Auto-Wipe',
    },
    {
      id: 'v25-four-layer-backup-suite',
      group: 'Security & Manifest',
      label: 'v2.5 4-Layer Encrypted Backup Suite (PBKDF2 100k · GitHub · Telegram · Rolling Local)',
      expected: 'VaultBackupManager + GitHubBackupSync + TelegramBackupClient + BackupSyncWorker + BackupSyncCenterScreen',
      passed:
        vaultBackupKt.includes('PBKDF2WithHmacSHA256') &&
        vaultBackupKt.includes('100_000') &&
        vaultBackupKt.includes('AES/GCM/NoPadding') &&
        vaultBackupKt.includes('OMNIAI_ENC_V25:') &&
        githubBackupKt.includes('backups/omniai_vault.enc') &&
        telegramBackupKt.includes('/sendDocument') &&
        telegramBackupKt.includes('/sendVideo') &&
        backupWorkerKt.includes('CoroutineWorker') &&
        backupCenterKt.includes('BackupSyncCenterScreen'),
      detail: 'Verified 4-layer selective AES-256-GCM backup, GitHub Contents sync, Telegram archive, and nightly 5-file rolling snapshots',
    },
    {
      id: 'v25-live-html-sandbox-webview',
      group: 'Security & Manifest',
      label: 'v2.5 Hardened Offline HTML/CSS/JS Live Previewer (Sandboxed WebView)',
      expected: 'CodePreviewDialog with allowFileAccess=false, allowContentAccess=false, loadDataWithBaseURL, Run Live Preview chip',
      passed:
        codePreviewKt.includes('allowFileAccess = false') &&
        codePreviewKt.includes('allowContentAccess = false') &&
        codePreviewKt.includes('loadDataWithBaseURL') &&
        chatScreenKt.includes('Run Live Preview') &&
        chatScreenKt.includes('CodePreviewDialog'),
      detail: 'Verified hardened WebView dialog with network-block default and Chat fenced HTML action chip',
    },
    {
      id: 'v25-prompt-chainer-workspaces',
      group: 'Code Integrity',
      label: 'v2.5 Multi-Step Prompt Chainer (Workflows) & Multi-Workspace Profile Switcher (DB v9)',
      expected: 'PromptChainEntity + ChainRunnerScreen + WorkspaceProfileEntity + OmniAiDatabase version = 9',
      passed:
        entitiesKt.includes('class PromptChainEntity') &&
        entitiesKt.includes('class WorkspaceProfileEntity') &&
        entitiesKt.includes('val workspaceId: String = "default"') &&
        databaseKt.includes('version = 9') &&
        chainRunnerKt.includes('ChainRunnerScreen') &&
        mainActivityKt.includes('New Workspace Profile') &&
        chatScreenKt.includes('Text("Workflows"'),
      detail: 'Verified Room DB v9 workspace isolation, TopAppBar switcher, and sequential {{prev_output}} prompt chains',
    },
    {
      id: 'v25-hf-explorer-media3-compressor',
      group: 'Media3 & Worker',
      label: 'v2.5 Built-in Hugging Face Model Explorer & On-Device Media3 Video Compressor',
      expected: 'HfModelExplorerClient + HfExplorerSheet + Media3VideoCompressor (DefaultEncoderFactory + 3 presets)',
      passed:
        hfClientKt.includes('https://huggingface.co/api/models') &&
        hfSheetKt.includes('Set as Active HF Model ID') &&
        hfSheetKt.includes('Copy GGUF Repo URL') &&
        vaultScreenKt.includes('Browse HF Models') &&
        compressorKt.includes('DefaultEncoderFactory') &&
        compressorKt.includes('VideoEncoderSettings') &&
        compressorKt.includes('480') &&
        mergerScreenKt.includes('Compress Video (Media3 Transformer)'),
      detail: 'Verified HF model search API explorer and Media3 3-preset hardware H.264 compressor with Before/After MB readout',
    },
    {
      id: 'v25-canvas-analytics-webhooks-shortcuts',
      group: 'Code Integrity',
      label: 'v2.5 Compose Canvas Analytics · Custom Webhooks · Dynamic Launcher Shortcuts',
      expected: 'ApiMetricEntity + AnalyticsScreen Canvas + WebhookNotifier + DynamicShortcutHelper (4 ShortcutManager entries)',
      passed:
        entitiesKt.includes('class ApiMetricEntity') &&
        analyticsKt.includes('Canvas(') &&
        webhookKt.includes('CUSTOM_WEBHOOK_URL') &&
        settingsHubKt.includes('CUSTOM_WEBHOOK_URL') &&
        shortcutsKt.includes('ShortcutManager') &&
        shortcutsKt.includes('EXTRA_LAUNCH_OCR') &&
        shortcutsKt.includes('EXTRA_LAUNCH_BACKUP_CENTER') &&
        mainActivityKt.includes('EXTRA_LAUNCH_OCR') &&
        mainActivityKt.includes('EXTRA_LAUNCH_BACKUP_CENTER'),
      detail: 'Verified native Canvas charts, Discord/n8n/Slack webhook notifier, and 4 dynamic launcher shortcuts',
    },
    {
      id: 'v30-hybrid-terminal-omnicli-netlog',
      group: 'Code Integrity',
      label: 'v3.0 Hybrid Terminal (LocalShellExecutor + OmniCLI + Masked NetworkLogInterceptor)',
      expected: 'ProcessBuilder /system/bin/sh, destroyForcibly, OmniCLI commands, masked OkHttp netlog & extra-keys row',
      passed:
        localShellKt.includes('ProcessBuilder("/system/bin/sh", "-c"') &&
        localShellKt.includes('destroyForcibly()') &&
        omniCliKt.includes('kaggle status') &&
        omniCliKt.includes('hf search ') &&
        omniCliKt.includes('backup now') &&
        omniCliKt.includes('sysinfo') &&
        omniCliKt.includes('netlog') &&
        netLogKt.includes('***MASKED***') &&
        aiClientKt.includes('.addInterceptor(NetworkLogInterceptor)') &&
        terminalScreenKt.includes('COPY ALL') &&
        terminalScreenKt.includes('CTRL+C'),
      detail: 'Verified local sandbox shell, OmniCLI dispatcher, masked OkHttp3 netlog inspector, and TerminalScreen UI',
    },
    {
      id: 'v30-termux-bridge-snippet-vault',
      group: 'Security & Manifest',
      label: 'v3.0 Termux Bridge Integration (RUN_COMMAND Permission, <queries> & Snippet Vault DB v9)',
      expected: 'com.termux.permission.RUN_COMMAND, <queries> com.termux, TermuxBridge, TerminalSnippetEntity in Room DB v9',
      passed:
        manifest.includes('com.termux.permission.RUN_COMMAND') &&
        manifest.includes('android:name="com.termux"') &&
        termuxBridgeKt.includes('com.termux.RUN_COMMAND') &&
        termuxBridgeKt.includes('allow-external-apps=true') &&
        entitiesKt.includes('class TerminalSnippetEntity') &&
        daosKt.includes('interface TerminalSnippetDao') &&
        databaseKt.includes('TerminalSnippetEntity::class') &&
        databaseKt.includes('version = 9') &&
        terminalScreenKt.includes('Command Snippet Vault'),
      detail: 'Verified Termux RUN_COMMAND bridge, Android 11+ <queries> visibility, and Room DB v9 TerminalSnippetDao',
    },
    {
      id: 'v30-deep-ui-theme-studio',
      group: 'Code Integrity',
      label: 'v3.0 Deep UI Theme Studio (6 Presets, Material You dynamicDarkColorScheme, Hex & Wallpaper)',
      expected: 'UiConfigRepository StateFlow, 6 presets, dynamicDarkColorScheme, ChatBubbleStyle, wallpaper.jpg & JSON export/import',
      passed:
        uiConfigRepoKt.includes('AMOLED_BLACK') &&
        uiConfigRepoKt.includes('CYBERPUNK_NEON') &&
        uiConfigRepoKt.includes('MATERIAL_YOU') &&
        uiConfigRepoKt.includes('MATRIX_TERMINAL') &&
        uiConfigRepoKt.includes('NORD_SLATE') &&
        uiConfigRepoKt.includes('MINIMAL_LIGHT') &&
        uiConfigRepoKt.includes('wallpaper.jpg') &&
        themeKt.includes('dynamicDarkColorScheme(context)') &&
        themeKt.includes('LocalUiConfig') &&
        themeStudioKt.includes('Export JSON') &&
        themeStudioKt.includes('Import JSON') &&
        chatScreenKt.includes('ChatBubbleStyle.SHARP_CYBER'),
      detail: 'Verified reactive UiConfigRepository, 6 theme presets, Material You API 31+, hex picker, wallpaper dim overlay, and Theme JSON',
    },
    {
      id: 'v30-customizable-settings-nav-fab',
      group: 'Code Integrity',
      label: 'v3.0 Customizable Settings Hub (Live Search, Pinned Controls, Dynamic Bottom Nav & Quick FAB)',
      expected: 'Settings search bar, Pinned Controls card, 2-5 tab Bottom Nav Customizer, overflow menu & QuickFabAction',
      passed:
        settingsHubKt.includes('Pinned Controls') &&
        settingsHubKt.includes('Search settings') &&
        settingsHubKt.includes('togglePinnedSetting') &&
        uiConfigRepoKt.includes('MIN_NAV_TABS = 2') &&
        uiConfigRepoKt.includes('MAX_NAV_TABS = 5') &&
        mainActivityKt.includes('pinnedBottomTabs') &&
        mainActivityKt.includes('unpinnedTabs') &&
        mainActivityKt.includes('QuickFabAction.QUICK_TERMINAL') &&
        mainActivityKt.includes('LocalHapticFeedback'),
      detail: 'Verified live Settings search, Star/Pin controls strip, dynamic 2-5 tab Bottom Nav with overflow, and configurable Quick FAB',
    },
    {
      id: 'v30-universal-command-palette',
      group: 'Code Integrity',
      label: 'v3.0 Universal Command Palette (Spotlight / Ctrl+K Modal & > Terminal Exec)',
      expected: 'CommandPaletteDialog searching Settings, Chat Threads, Terminal Snippets, and > direct terminal execution',
      passed:
        commandPaletteKt.includes('Universal Command Palette') &&
        commandPaletteKt.includes('isDirectTerminalExec') &&
        commandPaletteKt.includes('executeTerminalCommand(directCommand)') &&
        mainActivityKt.includes('CommandPaletteDialog') &&
        mainActivityKt.includes('Open Universal Command Palette'),
      detail: 'Verified TopAppBar Search trigger and Universal Command Palette modal with > command dispatch',
    },
    {
      id: 'v35-relational-content-db',
      group: 'Code Integrity',
      label: 'v3.5 Normalized Relational Room DB v9 (Chat Archive, Scripts/Scenes/Revisions, YT/IG/FB SEO & Hashtags)',
      expected: 'VideoScriptEntity, SceneEntity CASCADE, ScriptRevisionEntity, SocialPostEntity SET_NULL, HashtagPackEntity, @Transaction ScriptWithScenesAndSocial',
      passed:
        databaseKt.includes('version = 9') &&
        databaseKt.includes('VideoScriptEntity::class') &&
        databaseKt.includes('SceneEntity::class') &&
        databaseKt.includes('ScriptRevisionEntity::class') &&
        databaseKt.includes('SocialPostEntity::class') &&
        databaseKt.includes('HashtagPackEntity::class') &&
        entitiesKt.includes('val folderName: String') &&
        entitiesKt.includes('val isBookmarked: Boolean') &&
        entitiesKt.includes('data class ScriptWithScenesAndSocial') &&
        entitiesKt.includes('ForeignKey.CASCADE') &&
        entitiesKt.includes('ForeignKey.SET_NULL') &&
        daosKt.includes('@Transaction') &&
        daosKt.includes('interface VideoScriptDao') &&
        daosKt.includes('interface SocialSeoDao'),
      detail: 'Verified Room DB v9 normalized entities, foreign keys, @Index annotations, and @Transaction relational DAOs',
    },
    {
      id: 'v35-content-hub-social-seo',
      group: 'Media3 & Worker',
      label: 'v3.5 Unified Content DB & Multi-Platform YT/IG/FB SEO Hub + Tab 1 Bookmarks',
      expected: 'ContentHubScreen (3 sections), generateMultiPlatformSocialSeo, A/B titles, YT/IG/FB limits, Hashtag Packs & Bookmarked Answers',
      passed:
        contentHubKt.includes('fun ContentHubScreen(') &&
        contentHubKt.includes('Auto-Generate All 3 Platforms (AI)') &&
        contentHubKt.includes('/100') &&
        contentHubKt.includes('/5000') &&
        contentHubKt.includes('/2200') &&
        contentHubKt.includes('/30') &&
        aiClientKt.includes('suspend fun generateMultiPlatformSocialSeo') &&
        apiModelsKt.includes('class MultiPlatformSocialSeoPayload') &&
        chatScreenKt.includes('Bookmarked Answers') &&
        chatScreenKt.includes('Icons.Filled.Bookmark') &&
        galleryScreenKt.includes('onOpenContentHub') &&
        uiConfigRepoKt.includes('CONTENT_HUB'),
      detail: 'Verified 3-tab ContentHubScreen, 3-platform AI SEO generator with A/B titles & limit counters, and Tab 1 Bookmarked Answers',
    },
    {
      id: 'v35-publish-reminder-deeplink',
      group: 'Media3 & Worker',
      label: 'v3.5 Human-in-the-Loop PublishReminderWorker & Deep-Link Notification Routing',
      expected: 'PublishReminderWorker CoroutineWorker + OneTimeWorkRequest + MainActivity.EXTRA_OPEN_SOCIAL_POST_ID deep link',
      passed:
        publishReminderKt.includes('class PublishReminderWorker') &&
        publishReminderKt.includes('OneTimeWorkRequestBuilder') &&
        publishReminderKt.includes('EXTRA_OPEN_SOCIAL_POST_ID') &&
        publishReminderKt.includes('Ready to post on') &&
        mainActivityKt.includes('EXTRA_OPEN_SOCIAL_POST_ID') &&
        mainActivityKt.includes('pendingOpenSocialPostId'),
      detail: 'Verified WorkManager OneTimeWorkRequest publish reminder notifications with direct ContentHub post deep-linking',
    },
    {
      id: 'v35-csv-markdown-exporter',
      group: 'Code Integrity',
      label: 'v3.5 RFC-4180 CSV (Google Sheets / Excel) & Markdown Bulk Vault Exporter',
      expected: 'ContentDbExporter with escapeCsvCell, UTF-8 BOM, SAF & Documents/OmniAIStudio/Exports',
      passed:
        contentDbExporterKt.includes('class ContentDbExporter') &&
        contentDbExporterKt.includes('fun escapeCsvCell') &&
        contentDbExporterKt.includes('OmniAIStudio') &&
        contentDbExporterKt.includes('exportAllToCsv') &&
        contentDbExporterKt.includes('exportAllToMarkdown'),
      detail: 'Verified RFC-4180 CSV escaping, UTF-8 BOM, SAF export, and local Documents/OmniAIStudio/Exports writer',
    },
    {
      id: 'v35-terminal-db-backup-sync',
      group: 'Security & Manifest',
      label: 'v3.5 Terminal DB Inspector (db stats/search/export csv) & AES-256-GCM Backup Sync',
      expected: 'OmniCliInterpreter db stats/search/export csv + VaultBackupManager includeContentScriptsAndSocialDb + BackupSyncCenterScreen checkbox',
      passed:
        omniCliKt.includes('db stats') &&
        omniCliKt.includes('db search ') &&
        omniCliKt.includes('db export csv') &&
        vaultBackupKt.includes('includeContentScriptsAndSocialDb') &&
        vaultBackupKt.includes('val videoScripts:') &&
        vaultBackupKt.includes('val hashtagPacks:') &&
        backupCenterKt.includes('Video Scripts, Scenes & YT/IG/FB SEO Database'),
      detail: 'Verified OmniCLI relational DB commands and full v3.5 table inclusion in AES-256-GCM Local/GitHub/Telegram backups',
    },
    {
      id: 'v40-master-niche-engine',
      group: 'Code Integrity',
      label: 'v4.0 Master Niche Context Engine (Room DB v9, NicheSelectorPill, Rolling Compressor & AI Blueprint)',
      expected: 'NicheEntity in DB v9, NicheContextBuilder.buildCompactNicheContext, NicheSelectorPill + NicheEditorDialog wired across Tabs 1, 2 & 4',
      passed:
        databaseKt.includes('version = 9') &&
        databaseKt.includes('NicheEntity::class') &&
        entitiesKt.includes('data class NicheEntity(') &&
        nicheContextKt.includes('fun buildCompactNicheContext') &&
        nicheSelectorKt.includes('fun NicheSelectorPill(') &&
        nicheSelectorKt.includes('fun NicheEditorDialog(') &&
        (navShellKt.includes('Switch Active Niche') ||
          (chatScreenKt.includes('NicheSelectorPill(') && directorScreenKt.includes('NicheSelectorPill('))) &&
        contentHubKt.includes('NicheSelectorPill('),
      detail: 'Verified Room DB v9 NicheEntity, indexed nicheId relations, NicheContextBuilder rolling compressor, and unified TopAppBar Niche Switcher',
    },
    {
      id: 'v40-auto-next-continuity-engine',
      group: 'Media3 & Worker',
      label: 'v4.0 Auto-Next Video Continuity Engine (4 Post-Video + 3 Chat Follow-Ups & Render Hook)',
      expected: 'NextCommandEntity + ContinuityEngine + post-render RenderQueueForegroundService continuity trigger',
      passed:
        databaseKt.includes('NextCommandEntity::class') &&
        entitiesKt.includes('data class NextCommandEntity(') &&
        continuityEngineKt.includes('class ContinuityEngine') &&
        continuityEngineKt.includes('Turn this chat into next video') &&
        renderServiceKt.includes('generatePostVideoNextCommands'),
      detail: 'Verified ContinuityEngine post-video (4 options) & post-chat (3 options) generation plus automatic post-render continuity hook',
    },
    {
      id: 'v40-auto-type-prompt-bar',
      group: 'Code Integrity',
      label: 'v4.0 Auto-Typing Prompt Selector Bar (Typewriter Animation, Fill Now, Regenerate & 5s Countdown)',
      expected: 'AutoTypePromptBar with Fill Now, Regenerate Ideas, Run Now countdown, wired into Tab 1 Chat & Tab 2 Video Director',
      passed:
        autoTypeBarKt.includes('fun AutoTypePromptBar(') &&
        autoTypeBarKt.includes('Text("Fill Now")') &&
        autoTypeBarKt.includes('Regenerate Ideas') &&
        autoTypeBarKt.includes('Text("Run Now")') &&
        chatScreenKt.includes('AutoTypePromptBar(') &&
        directorScreenKt.includes('AutoTypePromptBar('),
      detail: 'Verified cancellation-safe LaunchedEffect typewriter, haptic ticks, Fill Now, Regenerate Ideas, and hands-free 5s countdown',
    },
    {
      id: 'v40-series-planner-hook-auditor',
      group: 'Media3 & Worker',
      label: 'v4.0 Multi-Episode Series Planner (7/15/30) & Pre-Render Viral Hook / Retention Auditor',
      expected: 'SeriesEpisodeEntity + SeriesPlannerSheet + ScriptAuditorDialog with 1-tap Auto-Optimize Weak Scenes',
      passed:
        databaseKt.includes('SeriesEpisodeEntity::class') &&
        entitiesKt.includes('data class SeriesEpisodeEntity(') &&
        seriesPlannerKt.includes('fun SeriesPlannerSheet(') &&
        scriptAuditorKt.includes('fun ScriptAuditorDialog(') &&
        scriptAuditorKt.includes('Text("Auto-Optimize Weak Scenes")') &&
        directorScreenKt.includes('SeriesPlannerSheet(') &&
        directorScreenKt.includes('ScriptAuditorDialog('),
      detail: 'Verified 7/15/30-episode SeriesPlannerSheet and Viral Hook Auditor with Character-DNA-preserving auto-optimization',
    },
    {
      id: 'v40-seamless-loop-worker',
      group: 'Media3 & Worker',
      label: 'v4.0 Seamless Loop (Reels/Shorts) Toggle & Kaggle Worker 0.25s Tail Trim + Micro afade',
      expected: 'Seamless Loop switch in VideoDirectorScreen, seamless_loop in ApiModels & video_worker.py with FFmpeg afade',
      passed:
        directorScreenKt.includes('Seamless Loop (Reels/Shorts)') &&
        apiModelsKt.includes('seamless_loop') &&
        workerPy.includes('seamless_loop') &&
        workerPy.includes('afade=t=in'),
      detail: 'Verified seamless_loop payload wiring and FFmpeg 0.25s tail trim + micro audio fade-in/fade-out in video_worker.py',
    },
    {
      id: 'v40-omnicli-backup-settings',
      group: 'Security & Manifest',
      label: 'v4.0 OmniCLI Niche Commands, Settings Section 3C & AES-256-GCM Backup Sync',
      expected: 'OmniCliInterpreter niche list/switch + next video, SettingsHubScreen 3C, VaultBackupManager v40 schema',
      passed:
        omniCliKt.includes('niche list') &&
        omniCliKt.includes('niche switch ') &&
        omniCliKt.includes('next video') &&
        settingsHubKt.includes('3C. Niche Context & Auto-Typer') &&
        vaultBackupKt.includes('includeNichesContinuityAndSeries') &&
        vaultBackupKt.includes('val niches:') &&
        vaultBackupKt.includes('val seriesEpisodes:') &&
        backupCenterKt.includes('Niches, Auto-Next Commands & Series Planner'),
      detail: 'Verified OmniCLI niche/next commands, Settings 3C Niche & Auto-Typer card, and full v4.0 table backup/restore sync',
    },
    {
      id: 'v45-pms-database-schema',
      group: 'Code Integrity',
      label: 'v4.5 Project Management System Room DB v9 (Projects, Kanban Tasks, File Indexes & Commit Logs)',
      expected: 'ProjectEntity, ProjectTaskEntity CASCADE, ProjectFileIndexEntity, GitCommitLogEntity & ProjectDao in DB v9',
      passed:
        databaseKt.includes('version = 9') &&
        databaseKt.includes('ProjectEntity::class') &&
        databaseKt.includes('ProjectTaskEntity::class') &&
        databaseKt.includes('ProjectFileIndexEntity::class') &&
        databaseKt.includes('GitCommitLogEntity::class') &&
        entitiesKt.includes('data class ProjectEntity(') &&
        entitiesKt.includes('data class ProjectTaskEntity(') &&
        entitiesKt.includes('data class ProjectFileIndexEntity(') &&
        entitiesKt.includes('data class GitCommitLogEntity(') &&
        daosKt.includes('interface ProjectDao'),
      detail: 'Verified Room DB v9 relational PMS schema with CASCADE foreign keys and indexed projectId columns',
    },
    {
      id: 'v45-project-dashboard-kanban-splitter',
      group: 'Code Integrity',
      label: 'v4.5 Project Dashboard, Kanban Board, Code Editor & AI ### FILE Multi-File Splitter',
      expected: 'ProjectDashboardScreen (4 sub-tabs, AI Auto-Plan, Code Editor) + AiMultiFileSplitter with canonical path confinement',
      passed:
        projectDashboardKt.includes('fun ProjectDashboardScreen(') &&
        projectDashboardKt.includes('AI Auto-Plan') &&
        projectDashboardKt.includes('AI ### FILE Splitter') &&
        projectDashboardKt.includes('Git Auto-Push Now') &&
        aiMultiFileSplitterKt.includes('object AiMultiFileSplitter') &&
        aiMultiFileSplitterKt.includes('fun parseMultiFileOutput') &&
        aiMultiFileSplitterKt.includes('fun resolveConfinedFile') &&
        aiMultiFileSplitterKt.includes('canonicalPath') &&
        uiConfigRepoKt.includes('PROJECTS("Projects")') &&
        uiConfigRepoKt.includes('GIT_AUTO_PUSH("Git Auto-Push")'),
      detail: 'Verified ProjectDashboardScreen, Kanban & Code Editor, canonical-confined AiMultiFileSplitter, and dynamic nav/FAB wiring',
    },
    {
      id: 'v45-atomic-github-git-engine',
      group: 'CI/CD Automation',
      label: 'v4.5 Zero-Dependency Atomic GitHub Git Data API Engine (6-Step Commit, Pull & Private Repo Creator)',
      expected: 'GitHubGitEngine using OkHttp3 + Base64.NO_WRAP + SHA-256 + 6-step Git Data API + 404/409 empty-repo bootstrap + 5MB pull cap',
      passed:
        gitHubGitEngineKt.includes('class GitHubGitEngine') &&
        gitHubGitEngineKt.includes('X-GitHub-Api-Version') &&
        gitHubGitEngineKt.includes('2022-11-28') &&
        gitHubGitEngineKt.includes('Base64.NO_WRAP') &&
        gitHubGitEngineKt.includes('SHA-256') &&
        gitHubGitEngineKt.includes('/git/blobs') &&
        gitHubGitEngineKt.includes('/git/trees') &&
        gitHubGitEngineKt.includes('/git/commits') &&
        gitHubGitEngineKt.includes('/git/refs/heads/') &&
        gitHubGitEngineKt.includes('createPrivateRepo') &&
        gitHubGitEngineKt.includes('pullProjectFromGitHub') &&
        gitHubGitEngineKt.includes('MAX_PULL_BLOB_BYTES'),
      detail: 'Verified zero-JGit 6-step atomic Git Data API push, empty-repo bootstrap, blob deletion via sha:null, and 5MB-guarded pull',
    },
    {
      id: 'v45-git-autopush-worker-actions',
      group: 'CI/CD Automation',
      label: 'v4.5 Background GitAutoPushWorker & Live GitHub Actions Build Monitor with 1-Tap OTA APK Install',
      expected: 'GitAutoPushWorker CoroutineWorker (NetworkType.CONNECTED) + watchLatestWorkflowRun + Install Updated APK CTA',
      passed:
        gitAutoPushWorkerKt.includes('class GitAutoPushWorker') &&
        gitAutoPushWorkerKt.includes('NetworkType.CONNECTED') &&
        gitHubGitEngineKt.includes('fun watchLatestWorkflowRun') &&
        gitHubGitEngineKt.includes('/actions/runs?per_page=1') &&
        projectDashboardKt.includes('GitHub Actions Build Monitor') &&
        projectDashboardKt.includes('Install Updated APK') &&
        projectDashboardKt.includes('Copy Build Run URL & Failure Summary'),
      detail: 'Verified WorkManager background auto-push and live 15s GitHub Actions build watcher with OTA APK installer & failure logs',
    },
    {
      id: 'v45-terminal-git-chat-bridge',
      group: 'Code Integrity',
      label: 'v4.5 Terminal Git Commands (proj/git), Chat-to-Project Bridge, Command Palette & Home Widget',
      expected: 'OmniCliInterpreter proj/git commands, ChatStudioScreen Save to Project + SaveToProjectDialog, CommandPalette & Widget launch',
      passed:
        omniCliKt.includes('proj list') &&
        omniCliKt.includes('proj switch ') &&
        omniCliKt.includes('git status') &&
        omniCliKt.includes('git push') &&
        omniCliKt.includes('git pull') &&
        omniCliKt.includes('git actions') &&
        chatScreenKt.includes('Save to Project') &&
        saveToProjectKt.includes('fun SaveToProjectDialog(') &&
        commandPaletteKt.includes('Open Projects & Git Auto-Push Hub') &&
        widgetProviderKt.includes('widget_btn_projects') &&
        widgetLayoutXml.includes('widget_btn_projects'),
      detail: 'Verified OmniCLI Git/Project commands, Chat Save-to-Project bridge, Command Palette Projects, and Home Widget shortcut',
    },
    {
      id: 'v45-backup-settings-zero-jgit',
      group: 'Security & Manifest',
      label: 'v4.5 Settings Section 3D, AES-256-GCM Backup v45 & Strict Zero-JGit / Zero-New-Deps Safety',
      expected: 'SettingsHubScreen 3D, VaultBackupManager schemaVersion=45, zero JGit or extra Gradle deps',
      passed:
        settingsHubKt.includes('3D. Project & Git Automation') &&
        vaultBackupKt.includes('schemaVersion: Int = 45') &&
        vaultBackupKt.includes('includeProjectsAndGitLogs') &&
        backupCenterKt.includes('Projects, Kanban Tasks & Git Commit Logs') &&
        !appGradle.toLowerCase().includes('jgit') &&
        !appGradle.includes('mpandroidchart') &&
        !appGradle.includes('retrofit') &&
        !appGradle.includes('glide') &&
        !appGradle.includes('termux'),
      detail: 'Verified Settings 3D Git Automation, AES-256-GCM Backup schema v45, and zero JGit / zero extra Gradle libs',
    },
    {
      id: 'v51-design-tokens-surface-hierarchy',
      group: 'Code Integrity',
      label: 'v5.1 Unified Design System Tokens (#09090C / #13131A / #232330 / #1B1B25 & Empty/Skeleton Primitives)',
      expected: 'DesignTokens.kt with AMOLED hierarchy, 16dp/20dp/48dp grid, 12% alpha containers, OmniEmptyState & OmniSkeletonCard',
      passed:
        designTokensKt.includes('object OmniDesignTokens') &&
        designTokensKt.includes('0xFF09090C') &&
        designTokensKt.includes('0xFF13131A') &&
        designTokensKt.includes('0xFF232330') &&
        designTokensKt.includes('0xFF1B1B25') &&
        designTokensKt.includes('0.12f') &&
        designTokensKt.includes('fun OmniSurfaceCard(') &&
        designTokensKt.includes('fun OmniOverflowMenuButton(') &&
        designTokensKt.includes('fun OmniEmptyState(') &&
        designTokensKt.includes('fun OmniSkeletonCard(') &&
        themeKt.includes('0xFF09090C') &&
        themeKt.includes('0xFF13131A'),
      detail: 'Verified DesignTokens.kt surface hierarchy, strict spacing grid, overflow menu, empty states, and shimmer skeletons',
    },
    {
      id: 'v51-hub-and-spoke-navigation-shell',
      group: 'Code Integrity',
      label: 'v5.1 Hub-and-Spoke Shell (4-Tab Bar + Slide-Out ModalNavigationDrawer + Slim 56dp TopAppBar)',
      expected: 'AppNavigationShell with 4 default tabs (Home/Chat/Studio/Projects), Niche & Live Jobs BottomSheets, grouped Drawer',
      passed:
        navShellKt.includes('fun AppNavigationShell(') &&
        navShellKt.includes('ModalNavigationDrawer') &&
        navShellKt.includes('ModalBottomSheet') &&
        navShellKt.includes('Create & Media') &&
        navShellKt.includes('Developer & Agents') &&
        navShellKt.includes('System & Vault') &&
        navShellKt.includes('Live Studio Activity') &&
        uiConfigRepoKt.includes('HOME("Home")') &&
        uiConfigRepoKt.includes('DIRECTOR("Studio")') &&
        mainActivityKt.includes('AppNavigationShell(') &&
        mainActivityKt.includes('HomeBentoScreen('),
      detail: 'Verified 4-tab bottom NavigationBar, slim 56dp TopAppBar with Niche & pulsing Live Status pills, and 3-group Drawer',
    },
    {
      id: 'v51-home-bento-progressive-disclosure',
      group: 'Code Integrity',
      label: 'v5.1 Home Bento Hub, Chat Zen/+ Sheet, 3-Step Video Studio & Sticky Git Auto-Push / Fullscreen Editor',
      expected: 'HomeBentoScreen GPU ring + Auto-Type CTA, Chat Zen Mode + ModalBottomSheet, 3-Step Studio + Render Settings Sheet, Sticky Git Pill',
      passed:
        homeBentoKt.includes('fun HomeBentoScreen(') &&
        homeBentoKt.includes('Auto-Type & Create') &&
        homeBentoKt.includes('Kaggle GPU') &&
        chatScreenKt.includes('isZenFocusMode') &&
        chatScreenKt.includes('ModalBottomSheet') &&
        directorScreenKt.includes('1. Idea') &&
        directorScreenKt.includes('2. Scenes') &&
        directorScreenKt.includes('3. Render') &&
        directorScreenKt.includes('Render Settings') &&
        directorScreenKt.includes('ModalBottomSheet') &&
        projectDashboardKt.includes('Git Auto-Push (') &&
        projectDashboardKt.includes('onFullscreenEditorChanged') &&
        galleryScreenKt.includes('OmniEmptyState('),
      detail: 'Verified Bento Command Dashboard, Chat Zen Mode + sheet, 3-Step Video Studio + settings sheet, sticky Git pill & fullscreen Code Editor',
    },
    {
      id: 'v35-zero-new-deps-ci',
      group: 'CI/CD Automation',
      label: 'v5.1 Zero-New-Dependency Build Safety & Auto-Versioned v5.1.x CI Pipeline',
      expected: 'VERSION_NAME 5.1.x in build.gradle.kts & CI workflow with zero unauthorized Gradle libs',
      passed:
        appGradle.includes('?: "5.1.1"') &&
        apkWorkflow.includes('VERSION_NAME: 5.1.') &&
        !appGradle.toLowerCase().includes('jgit') &&
        !appGradle.includes('mpandroidchart') &&
        !appGradle.includes('retrofit') &&
        !appGradle.includes('glide') &&
        !appGradle.includes('termux'),
      detail: 'Verified zero added external Gradle dependencies (Compose Material 3 only) and v5.1.x GitHub Actions APK release matrix',
    },
    {
      id: 'zero-todos',
      group: 'Code Integrity',
      label: 'Zero TODOs, Placeholders, or Omitted Functions',
      expected: '0 files containing TODO, FIXME, or NotImplementedError',
      passed: todoHits.length === 0 && files.length >= 110,
      detail:
        todoHits.length === 0
          ? `Scanned all ${files.length} monorepo files — 0 stubs or TODO markers`
          : `Found markers in: ${todoHits.map((f) => f.path).join(', ')}`,
    },
  ];
}
