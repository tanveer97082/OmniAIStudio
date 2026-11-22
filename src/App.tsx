import { useState } from 'react';
import { Download } from 'lucide-react';
import { AndroidSimulatorView } from './components/AndroidSimulatorView';
import { BuildAuditView } from './components/BuildAuditView';
import { CodeExplorerView } from './components/CodeExplorerView';
import { MONOREPO_FILES } from './utils/monorepoRegistry';
import { buildZipArchive } from './utils/zipBuilder';

type WorkspaceMode = 'repository' | 'preview' | 'audit';

export default function App() {
  const [mode, setMode] = useState<WorkspaceMode>('repository');
  const [selectedPath, setSelectedPath] = useState<string>(
    'app/build.gradle.kts'
  );
  const [downloadDone, setDownloadDone] = useState(false);

  const handleDownloadZip = () => {
    const zipBytes = buildZipArchive(
      MONOREPO_FILES.map((file) => ({
        path: `OmniAI-Studio/${file.path}`,
        content: file.content,
      }))
    );
    const blob = new Blob([zipBytes], { type: 'application/zip' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'OmniAI-Studio-monorepo.zip';
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setDownloadDone(true);
    window.setTimeout(() => setDownloadDone(false), 2000);
  };

  const handleInspectSource = (path: string) => {
    setSelectedPath(path);
    setMode('repository');
  };

  return (
    <div className="flex h-screen flex-col overflow-hidden bg-[#080A0E] text-zinc-100">
      {/* Strict 3-Zone Top Bar Contract */}
      <header className="flex items-center justify-between gap-8 border-b border-zinc-800/90 bg-[#0B0E14] px-6 py-3.5 shrink-0">
        {/* Zone 1: Single-line Brand Wordmark */}
        <a
          href="#repository"
          onClick={(e) => {
            e.preventDefault();
            setMode('repository');
          }}
          className="text-base font-bold tracking-tight text-zinc-100 whitespace-nowrap shrink-0"
        >
          OmniAI-Studio
        </a>

        {/* Zone 2: Clean Single-Line Navigation Links */}
        <nav aria-label="Primary workspace navigation" className="flex items-center gap-6 text-sm font-medium">
          {(
            [
              { id: 'repository', label: 'Repository' },
              { id: 'preview', label: 'Android Preview' },
              { id: 'audit', label: 'Build Audit' },
            ] as const
          ).map((item) => {
            const active = mode === item.id;
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setMode(item.id)}
                className={`border-b-2 py-1 transition-colors whitespace-nowrap shrink-0 ${
                  active
                    ? 'border-emerald-400 text-zinc-100'
                    : 'border-transparent text-zinc-400 hover:text-zinc-200'
                }`}
              >
                {item.label}
              </button>
            );
          })}
        </nav>

        {/* Zone 3: Single Primary Action */}
        <div className="flex items-center shrink-0">
          <button
            type="button"
            onClick={handleDownloadZip}
            className="inline-flex items-center gap-2 rounded-lg bg-emerald-500 px-4 py-2 text-xs font-semibold text-zinc-950 hover:bg-emerald-400 transition-colors whitespace-nowrap shrink-0"
          >
            <Download className="h-3.5 w-3.5" />
            <span>{downloadDone ? 'Exported ZIP' : 'Download ZIP'}</span>
          </button>
        </div>
      </header>

      {/* Main Workspace Viewport */}
      <main className="flex min-h-0 flex-1 flex-col">
        {mode === 'repository' && (
          <CodeExplorerView
            files={MONOREPO_FILES}
            selectedPath={selectedPath}
            onSelectPath={setSelectedPath}
          />
        )}
        {mode === 'preview' && (
          <AndroidSimulatorView onInspectSource={handleInspectSource} />
        )}
        {mode === 'audit' && (
          <BuildAuditView
            files={MONOREPO_FILES}
            onInspectSource={handleInspectSource}
          />
        )}
      </main>
    </div>
  );
}
