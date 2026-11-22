import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {
  auditMonorepo,
  buildDirectoryTree,
  detectCategory,
  detectLanguage,
  formatDirectoryTreeAscii,
  type RepoFile,
} from './monorepoAuditor';
import { buildZipArchive, computeCrc32 } from './zipBuilder';

function loadDiskMonorepoFiles(rootDir: string): RepoFile[] {
  const results: RepoFile[] = [];

  function walk(dir: string) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.isFile()) {
        const relPath = path.relative(rootDir, fullPath).replace(/\\/g, '/');
        const content = fs.readFileSync(fullPath, 'utf8');
        results.push({
          path: relPath,
          content,
          language: detectLanguage(relPath),
          category: detectCategory(relPath),
          lineCount: content.split('\n').length,
          byteSize: Buffer.byteLength(content, 'utf8'),
        });
      }
    }
  }

  walk(rootDir);
  return results.sort((a, b) => a.path.localeCompare(b.path));
}

test('CRC-32 matches standard IEEE 802.3 test vector ("123456789")', () => {
  const bytes = new TextEncoder().encode('123456789');
  assert.equal(computeCrc32(bytes), 0xcbf43926);
});

test('buildZipArchive produces valid PKZIP headers and EOCD record', () => {
  const archive = buildZipArchive([
    { path: 'OmniAI-Studio/gradle.properties', content: 'android.useAndroidX=true\n' },
    { path: 'OmniAI-Studio/.github/workflows/test.yml', content: 'name: CI\n' },
  ]);

  const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength);
  // Local file header signature 0x04034b50
  assert.equal(view.getUint32(0, true), 0x04034b50);
  // End of central directory signature 0x06054b50 at end - 22
  assert.equal(view.getUint32(archive.byteLength - 22, true), 0x06054b50);
  // Entry count = 2
  assert.equal(view.getUint16(archive.byteLength - 12, true), 2);
});

test('Real OmniAI-Studio monorepo on disk passes every Build Matrix, Security, Media3, Kaggle, and CI/CD audit check', () => {
  const repoRoot = path.resolve(process.cwd(), 'omniai-studio');
  const files = loadDiskMonorepoFiles(repoRoot);

  assert.ok(files.length >= 110, `Expected >= 110 monorepo files, got ${files.length}`);
  assert.equal(
    files.some((f) => f.path.includes('__pycache__') || f.path.endsWith('.pyc')),
    false,
    'Monorepo must not contain compiled Python __pycache__ or .pyc artifacts'
  );
  assert.equal(
    files.some((f) => f.path.endsWith('.png')),
    false,
    'Adaptive launcher icons must be pure XML with zero binary PNG files'
  );

  const checks = auditMonorepo(files);
  assert.equal(checks.length, 53, `Expected 53 audit checks, got ${checks.length}`);
  for (const check of checks) {
    assert.equal(check.passed, true, `Audit check failed: ${check.label} (${check.detail})`);
  }

  const tree = buildDirectoryTree(files);
  assert.ok(tree.some((node) => node.name === '.github' && node.isDirectory));
  assert.ok(tree.some((node) => node.name === 'kaggle_worker' && node.isDirectory));
  assert.ok(tree.some((node) => node.name === 'app' && node.isDirectory));

  const ascii = formatDirectoryTreeAscii(files);
  assert.ok(ascii.includes('OmniAI-Studio/'));
  assert.ok(ascii.includes('DesignTokens.kt'));
  assert.ok(ascii.includes('AppNavigationShell.kt'));
  assert.ok(ascii.includes('HomeBentoScreen.kt'));
  assert.ok(ascii.includes('ProjectDashboardScreen.kt'));
  assert.ok(ascii.includes('SaveToProjectDialog.kt'));
  assert.ok(ascii.includes('AiMultiFileSplitter.kt'));
  assert.ok(ascii.includes('GitHubGitEngine.kt'));
  assert.ok(ascii.includes('GitAutoPushWorker.kt'));
  assert.ok(ascii.includes('NicheContextBuilder.kt'));
  assert.ok(ascii.includes('ContinuityEngine.kt'));
  assert.ok(ascii.includes('NicheSelectorPill.kt'));
  assert.ok(ascii.includes('AutoTypePromptBar.kt'));
  assert.ok(ascii.includes('SeriesPlannerSheet.kt'));
  assert.ok(ascii.includes('ScriptAuditorDialog.kt'));
  assert.ok(ascii.includes('ContentDbExporter.kt'));
  assert.ok(ascii.includes('PublishReminderWorker.kt'));
  assert.ok(ascii.includes('ContentHubScreen.kt'));
  assert.ok(ascii.includes('LocalShellExecutor.kt'));
  assert.ok(ascii.includes('OmniCliInterpreter.kt'));
  assert.ok(ascii.includes('NetworkLogInterceptor.kt'));
  assert.ok(ascii.includes('TermuxBridge.kt'));
  assert.ok(ascii.includes('TerminalScreen.kt'));
  assert.ok(ascii.includes('UiConfigRepository.kt'));
  assert.ok(ascii.includes('UiThemeStudioScreen.kt'));
  assert.ok(ascii.includes('CommandPaletteDialog.kt'));
  assert.ok(ascii.includes('VaultBackupManager.kt'));
  assert.ok(ascii.includes('GitHubBackupSync.kt'));
  assert.ok(ascii.includes('TelegramBackupClient.kt'));
  assert.ok(ascii.includes('BackupSyncWorker.kt'));
  assert.ok(ascii.includes('BackupSyncCenterScreen.kt'));
  assert.ok(ascii.includes('CodePreviewDialog.kt'));
  assert.ok(ascii.includes('ChainRunnerScreen.kt'));
  assert.ok(ascii.includes('HfModelExplorerClient.kt'));
  assert.ok(ascii.includes('HfExplorerSheet.kt'));
  assert.ok(ascii.includes('Media3VideoCompressor.kt'));
  assert.ok(ascii.includes('AnalyticsScreen.kt'));
  assert.ok(ascii.includes('WebhookNotifier.kt'));
  assert.ok(ascii.includes('DynamicShortcutHelper.kt'));
  assert.ok(ascii.includes('ConversationMemoryManager.kt'));
  assert.ok(ascii.includes('VideoSceneTemplates.kt'));
  assert.ok(ascii.includes('AppIconManager.kt'));
  assert.ok(ascii.includes('CrashHandler.kt'));
  assert.ok(ascii.includes('SettingsHubScreen.kt'));
  assert.ok(ascii.includes('OfflineOcrHelper.kt'));
  assert.ok(ascii.includes('OfflineTtsHelper.kt'));
  assert.ok(ascii.includes('OfflineDocumentReader.kt'));
  assert.ok(ascii.includes('WebArticleScraper.kt'));
  assert.ok(ascii.includes('LocalhostPortScanner.kt'));
  assert.ok(ascii.includes('NewsDigestWorker.kt'));
  assert.ok(ascii.includes('DuressPinManager.kt'));
  assert.ok(ascii.includes('QuickVoiceTileService.kt'));
  assert.ok(ascii.includes('DecoySandboxScreen.kt'));
  assert.ok(ascii.includes('AudioStudioHelper.kt'));
  assert.ok(ascii.includes('GitHubWorkerEditorClient.kt'));
  assert.ok(ascii.includes('TunnelWatchdogWorker.kt'));
  assert.ok(ascii.includes('OmniWidgetProvider.kt'));
  assert.ok(ascii.includes('widget_info.xml'));
  assert.ok(ascii.includes('omni_widget_layout.xml'));
  assert.ok(ascii.includes('ic_launcher_default.xml'));
  assert.ok(ascii.includes('ic_launcher_calc.xml'));
  assert.ok(ascii.includes('Media3VideoMerger.kt'));
  assert.ok(ascii.includes('video_worker.py'));
  assert.ok(ascii.includes('build-release-apk.yml'));
});
