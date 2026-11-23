import React, { useMemo } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';
import { auditMonorepo, type RepoFile } from '../utils/monorepoAuditor';

interface BuildAuditViewProps {
  files: RepoFile[];
  onInspectSource: (path: string) => void;
}

const BUILD_MATRIX_ROWS = [
  { component: 'JDK Runtime', version: '17 (Temurin)', target: '.github/workflows/build-release-apk.yml' },
  { component: 'Gradle Wrapper', version: '8.5', target: 'gradle/wrapper/gradle-wrapper.properties' },
  { component: 'Android Gradle Plugin (AGP)', version: '8.2.2', target: 'build.gradle.kts' },
  { component: 'Kotlin + Serialization', version: '1.9.22', target: 'build.gradle.kts' },
  { component: 'KSP Compiler Plugin', version: '1.9.22-1.0.17', target: 'build.gradle.kts' },
  { component: 'Jetpack Compose Compiler', version: '1.5.8', target: 'app/build.gradle.kts' },
  { component: 'Jetpack Compose BOM', version: '2024.02.02', target: 'app/build.gradle.kts' },
  { component: 'Android SDK Matrix', version: 'compile 34 · target 34 · min 26', target: 'app/build.gradle.kts' },
  { component: 'Room Persistence (KSP)', version: '2.6.1', target: 'app/build.gradle.kts' },
  { component: 'Security Crypto & Biometric', version: '1.1.0-alpha06 · 1.1.0', target: 'app/build.gradle.kts' },
  { component: 'OkHttp3 + SSE', version: '4.12.0', target: 'app/build.gradle.kts' },
  { component: 'AndroidX Media3 (ExoPlayer + Transformer)', version: '1.2.1', target: 'app/build.gradle.kts' },
];

export const BuildAuditView: React.FC<BuildAuditViewProps> = ({ files, onInspectSource }) => {
  const checks = useMemo(() => auditMonorepo(files), [files]);
  const passedCount = checks.filter((c) => c.passed).length;
  const totalLines = useMemo(() => files.reduce((acc, f) => acc + f.lineCount, 0), [files]);
  const totalKb = useMemo(
    () => (files.reduce((acc, f) => acc + f.byteSize, 0) / 1024).toFixed(1),
    [files]
  );

  return (
    <div className="flex-1 overflow-y-auto bg-[#080A0E] px-6 py-8">
      <div className="mx-auto max-w-5xl space-y-8">
        {/* Header Summary */}
        <div className="flex flex-wrap items-end justify-between gap-4 border-b border-zinc-800/90 pb-6">
          <div>
            <h1 className="text-2xl font-semibold tracking-tight text-zinc-100">
              Build Matrix & Security Verification Audit
            </h1>
            <p className="mt-1 text-sm text-zinc-400">
              Live static inspection executed directly against the real files stored in the OmniAI-Studio monorepo.
            </p>
          </div>
          <div className="flex items-center gap-6 text-right font-mono tabular-nums">
            <div>
              <div className="text-lg font-semibold text-emerald-400">
                {passedCount}/{checks.length}
              </div>
              <div className="text-xs text-zinc-500">Checks Passed</div>
            </div>
            <div>
              <div className="text-lg font-semibold text-zinc-100">{files.length}</div>
              <div className="text-xs text-zinc-500">Monorepo Files</div>
            </div>
            <div>
              <div className="text-lg font-semibold text-zinc-100">{totalLines.toLocaleString()}</div>
              <div className="text-xs text-zinc-500">Lines ({totalKb} KB)</div>
            </div>
          </div>
        </div>

        {/* Automated Static Verification Checklist */}
        <section className="space-y-3">
          <h2 className="text-sm font-semibold text-zinc-200">
            Automated Integrity Checks
          </h2>
          <div className="divide-y divide-zinc-800/80 rounded-xl border border-zinc-800 bg-[#0B0E14]">
            {checks.map((check) => (
              <div
                key={check.id}
                className="flex flex-col justify-between gap-2 px-4 py-3.5 sm:flex-row sm:items-center"
              >
                <div className="flex items-start gap-3">
                  {check.passed ? (
                    <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0 text-emerald-400" />
                  ) : (
                    <XCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-400" />
                  )}
                  <div>
                    <div className="text-sm font-medium text-zinc-100">{check.label}</div>
                    <div className="mt-0.5 text-xs text-zinc-400">
                      {check.group} · {check.detail}
                    </div>
                  </div>
                </div>
                <span
                  className={`font-mono text-xs font-medium shrink-0 ${
                    check.passed ? 'text-emerald-400' : 'text-red-400'
                  }`}
                >
                  {check.passed ? 'VERIFIED' : 'FAILED'}
                </span>
              </div>
            ))}
          </div>
        </section>

        {/* Pinned Build Matrix Table */}
        <section className="space-y-3">
          <h2 className="text-sm font-semibold text-zinc-200">
            Pinned Zero-Error Build Matrix (Ubuntu GitHub Runners)
          </h2>
          <div className="overflow-x-auto rounded-xl border border-zinc-800 bg-[#0B0E14]">
            <table className="w-full text-left text-xs">
              <thead className="border-b border-zinc-800 text-zinc-400">
                <tr>
                  <th className="px-4 py-3 font-medium">Component</th>
                  <th className="px-4 py-3 font-medium">Pinned Version</th>
                  <th className="px-4 py-3 font-medium text-right">Source File</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/70">
                {BUILD_MATRIX_ROWS.map((row) => (
                  <tr key={row.component} className="hover:bg-zinc-900/50">
                    <td className="px-4 py-2.5 font-medium text-zinc-200">{row.component}</td>
                    <td className="px-4 py-2.5 font-mono text-emerald-300 tabular-nums">{row.version}</td>
                    <td className="px-4 py-2.5 text-right">
                      <button
                        type="button"
                        onClick={() => onInspectSource(row.target)}
                        className="font-mono text-zinc-400 hover:text-emerald-300 transition-colors"
                      >
                        {row.target}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        {/* Kaggle Dual-GPU & CI/CD Pipeline Architecture */}
        <section className="grid gap-6 md:grid-cols-2">
          <div className="space-y-2 rounded-xl border border-zinc-800 bg-[#0B0E14] p-5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-zinc-100">Dual-GPU Kaggle Worker</h3>
              <button
                type="button"
                onClick={() => onInspectSource('kaggle_worker/video_worker.py')}
                className="font-mono text-xs text-emerald-400 hover:text-emerald-300"
              >
                video_worker.py
              </button>
            </div>
            <p className="text-xs leading-6 text-zinc-400">
              Reads <code className="text-zinc-200">/kaggle/input/video-tasks-input/scenes.json</code>, synthesizes Edge-TTS narration and timed word-boundary SRT subtitles, shards scenes across isolated <code className="text-zinc-200">CUDA_VISIBLE_DEVICES=0</code> and <code className="text-zinc-200">1</code> subprocesses running <code className="text-zinc-200">Wan-AI/Wan2.1-T2V-1.3B-Diffusers</code>, outputs <code className="text-zinc-200">final_video.mp4</code>, and directly uploads to <code className="text-zinc-200">YouTube Data API v3</code> (with custom <code className="text-zinc-200">thumb.jpg</code> &amp; first comment), optional <code className="text-zinc-200">Meta FB/IG Reels</code>, and up to <code className="text-zinc-200">2 GB</code> Telegram via headless Telethon MTProto.
            </p>
          </div>

          <div className="space-y-2 rounded-xl border border-zinc-800 bg-[#0B0E14] p-5">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-zinc-100">Automated GitHub CI/CD</h3>
              <button
                type="button"
                onClick={() => onInspectSource('.github/workflows/build-release-apk.yml')}
                className="font-mono text-xs text-emerald-400 hover:text-emerald-300"
              >
                build-release-apk.yml
              </button>
            </div>
            <p className="text-xs leading-6 text-zinc-400">
              Auto-increments <code className="text-zinc-200">VERSION_CODE</code> and <code className="text-zinc-200">VERSION_NAME=5.5.&lt;run_number&gt;</code>, signs release builds when keystore secrets exist or falls back cleanly to <code className="text-zinc-200">assembleDebug</code>, publishes <code className="text-zinc-200">OmniAI-Studio-v5.5.N.apk</code> via <code className="text-zinc-200">softprops/action-gh-release@v2</code>, pushes Kaggle worker updates on <code className="text-zinc-200">kaggle_worker/**</code> changes, and runs <code className="text-zinc-200">keep-supabase-alive.yml</code> every 4 days to keep Supabase PostgreSQL active.
            </p>
          </div>
        </section>
      </div>
    </div>
  );
};
