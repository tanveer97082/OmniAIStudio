import React, { useMemo, useState } from 'react';
import {
  Check,
  ChevronDown,
  ChevronRight,
  Copy,
  Download,
  FileCode2,
  Folder,
  FolderOpen,
  ListTree,
  Search,
  X,
} from 'lucide-react';
import {
  buildDirectoryTree,
  formatDirectoryTreeAscii,
  type RepoFile,
  type TreeNode,
} from '../utils/monorepoAuditor';

interface CodeExplorerViewProps {
  files: RepoFile[];
  selectedPath: string;
  onSelectPath: (path: string) => void;
}

type CategoryFilter = 'all' | RepoFile['category'];

const CATEGORY_LABELS: Record<CategoryFilter, string> = {
  all: 'All Files',
  'android-app': 'Android App',
  security: 'Security',
  'kaggle-worker': 'Kaggle GPU',
  'github-ci': 'GitHub CI',
  'gradle-config': 'Gradle',
};

export const CodeExplorerView: React.FC<CodeExplorerViewProps> = ({
  files,
  selectedPath,
  onSelectPath,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>('all');
  const [showAsciiTree, setShowAsciiTree] = useState(false);
  const [copiedPath, setCopiedPath] = useState<string | null>(null);
  const [copiedTree, setCopiedTree] = useState(false);
  const [collapsedDirs, setCollapsedDirs] = useState<Record<string, boolean>>({});

  const filteredFiles = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    return files.filter((file) => {
      if (categoryFilter !== 'all' && file.category !== categoryFilter) return false;
      if (!q) return true;
      return (
        file.path.toLowerCase().includes(q) ||
        file.content.toLowerCase().includes(q)
      );
    });
  }, [files, searchQuery, categoryFilter]);

  const tree = useMemo(() => buildDirectoryTree(filteredFiles), [filteredFiles]);
  const asciiTree = useMemo(() => formatDirectoryTreeAscii(files), [files]);

  const activeFile = useMemo(
    () => files.find((f) => f.path === selectedPath) ?? filteredFiles[0] ?? files[0],
    [files, filteredFiles, selectedPath]
  );

  const toggleDir = (dirPath: string) => {
    setCollapsedDirs((prev) => ({ ...prev, [dirPath]: !prev[dirPath] }));
  };

  const handleCopyFile = async () => {
    if (!activeFile) return;
    try {
      await navigator.clipboard.writeText(activeFile.content);
      setCopiedPath(activeFile.path);
      window.setTimeout(() => setCopiedPath(null), 1800);
    } catch {
      // Clipboard API fallback ignored cleanly in headless contexts
      setCopiedPath(activeFile.path);
      window.setTimeout(() => setCopiedPath(null), 1800);
    }
  };

  const handleCopyAsciiTree = async () => {
    try {
      await navigator.clipboard.writeText(asciiTree);
    } catch {
      // Ignore in restricted clipboard contexts
    }
    setCopiedTree(true);
    window.setTimeout(() => setCopiedTree(false), 1800);
  };

  const handleDownloadSingleFile = () => {
    if (!activeFile) return;
    const blob = new Blob([activeFile.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = activeFile.path.split('/').pop() ?? 'file.txt';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const renderTreeNodes = (nodes: TreeNode[], depth = 0): React.ReactNode =>
    nodes.map((node) => {
      if (node.isDirectory) {
        const isCollapsed = collapsedDirs[node.path] === true && searchQuery.trim() === '';
        return (
          <div key={node.path}>
            <button
              type="button"
              onClick={() => toggleDir(node.path)}
              style={{ paddingLeft: `${depth * 14 + 10}px` }}
              className="flex w-full items-center gap-1.5 py-1.5 pr-2 text-left text-xs font-medium text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200 transition-colors"
            >
              {isCollapsed ? (
                <ChevronRight className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              ) : (
                <ChevronDown className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              )}
              {isCollapsed ? (
                <Folder className="h-3.5 w-3.5 shrink-0 text-zinc-500" />
              ) : (
                <FolderOpen className="h-3.5 w-3.5 shrink-0 text-zinc-400" />
              )}
              <span className="truncate">{node.name}</span>
            </button>
            {!isCollapsed && renderTreeNodes(node.children, depth + 1)}
          </div>
        );
      }

      const isSelected = activeFile?.path === node.path;
      return (
        <button
          key={node.path}
          type="button"
          onClick={() => {
            onSelectPath(node.path);
            setShowAsciiTree(false);
          }}
          style={{ paddingLeft: `${depth * 14 + 22}px` }}
          className={`flex w-full items-center gap-2 py-1.5 pr-3 text-left text-xs transition-colors ${
            isSelected
              ? 'bg-emerald-500/12 text-emerald-300 font-medium'
              : 'text-zinc-300 hover:bg-zinc-900 hover:text-zinc-100'
          }`}
        >
          <FileCode2
            className={`h-3.5 w-3.5 shrink-0 ${
              isSelected ? 'text-emerald-400' : 'text-zinc-500'
            }`}
          />
          <span className="truncate">{node.name}</span>
        </button>
      );
    });

  const codeLines = useMemo(
    () => (activeFile ? activeFile.content.split('\n') : []),
    [activeFile]
  );

  return (
    <div className="flex flex-1 min-h-0 flex-col lg:flex-row">
      {/* Left Sidebar: Monorepo Tree */}
      <aside className="flex w-full flex-col border-b border-zinc-800/90 bg-[#0B0E14] lg:w-80 lg:shrink-0 lg:border-b-0 lg:border-r">
        <div className="border-b border-zinc-800/80 p-4 space-y-3">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-zinc-500" />
            <input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search paths or symbols..."
              aria-label="Search repository files"
              className="w-full rounded-md border border-zinc-800 bg-[#080A0E] py-2 pl-8 pr-8 text-xs text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
            />
            {searchQuery && (
              <button
                type="button"
                onClick={() => setSearchQuery('')}
                aria-label="Clear search"
                className="absolute right-2.5 top-1/2 -translate-y-1/2 text-zinc-500 hover:text-zinc-200"
              >
                <X className="h-3.5 w-3.5" />
              </button>
            )}
          </div>

          <div className="flex flex-wrap gap-1">
            {(Object.keys(CATEGORY_LABELS) as CategoryFilter[]).map((cat) => (
              <button
                key={cat}
                type="button"
                onClick={() => setCategoryFilter(cat)}
                className={`rounded px-2 py-1 text-[11px] font-medium whitespace-nowrap shrink-0 transition-colors ${
                  categoryFilter === cat
                    ? 'bg-emerald-500/15 text-emerald-300'
                    : 'text-zinc-400 hover:bg-zinc-900 hover:text-zinc-200'
                }`}
              >
                {CATEGORY_LABELS[cat]}
              </button>
            ))}
          </div>

          <div className="flex items-center justify-between text-xs text-zinc-500">
            <span className="tabular-nums">
              {filteredFiles.length} of {files.length} files
            </span>
            <button
              type="button"
              onClick={() => setShowAsciiTree((prev) => !prev)}
              className="inline-flex items-center gap-1.5 text-zinc-300 hover:text-emerald-300 transition-colors whitespace-nowrap shrink-0"
            >
              <ListTree className="h-3.5 w-3.5" />
              <span>{showAsciiTree ? 'Source View' : 'Directory Tree'}</span>
            </button>
          </div>
        </div>

        <nav
          aria-label="Repository file tree"
          className="flex-1 overflow-y-auto py-2 max-h-72 lg:max-h-none"
        >
          {tree.length === 0 ? (
            <div className="px-4 py-8 text-center text-xs text-zinc-500">
              No files match "{searchQuery}".
            </div>
          ) : (
            renderTreeNodes(tree)
          )}
        </nav>
      </aside>

      {/* Main Code Viewer */}
      <section className="flex min-w-0 flex-1 flex-col bg-[#080A0E]">
        {showAsciiTree ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800/80 bg-[#0B0E14] px-6 py-3.5">
              <div>
                <h2 className="text-sm font-semibold text-zinc-100">
                  Complete Monorepo Directory Tree
                </h2>
                <p className="mt-0.5 text-xs text-zinc-500 tabular-nums">
                  {files.length} source files · Zero TODOs · Gradle 8.5 / AGP 8.2.2 / Media3 1.2.1
                </p>
              </div>
              <button
                type="button"
                onClick={handleCopyAsciiTree}
                className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:border-zinc-600 hover:bg-zinc-800 transition-colors whitespace-nowrap shrink-0"
              >
                {copiedTree ? <Check className="h-3.5 w-3.5 text-emerald-400" /> : <Copy className="h-3.5 w-3.5" />}
                <span>{copiedTree ? 'Copied Tree' : 'Copy Tree'}</span>
              </button>
            </div>
            <div className="flex-1 overflow-auto p-6">
              <pre className="font-mono text-xs leading-6 text-zinc-200">{asciiTree}</pre>
            </div>
          </>
        ) : activeFile ? (
          <>
            <div className="flex flex-wrap items-center justify-between gap-4 border-b border-zinc-800/80 bg-[#0B0E14] px-6 py-3.5">
              <div className="min-w-0">
                <div className="flex items-center gap-2 font-mono text-xs text-zinc-100">
                  <span className="text-zinc-500">OmniAI-Studio /</span>
                  <span className="truncate font-medium text-emerald-300">{activeFile.path}</span>
                </div>
                <div className="mt-1 flex items-center gap-2 text-xs text-zinc-500 tabular-nums">
                  <span>{activeFile.language}</span>
                  <span aria-hidden="true">·</span>
                  <span>{activeFile.lineCount} lines</span>
                  <span aria-hidden="true">·</span>
                  <span>{(activeFile.byteSize / 1024).toFixed(1)} KB</span>
                </div>
              </div>

              <div className="flex items-center gap-2 shrink-0">
                <button
                  type="button"
                  onClick={handleCopyFile}
                  className="inline-flex items-center gap-1.5 rounded-md border border-zinc-700 bg-zinc-900 px-3 py-1.5 text-xs font-medium text-zinc-200 hover:border-zinc-600 hover:bg-zinc-800 transition-colors whitespace-nowrap shrink-0"
                >
                  {copiedPath === activeFile.path ? (
                    <Check className="h-3.5 w-3.5 text-emerald-400" />
                  ) : (
                    <Copy className="h-3.5 w-3.5" />
                  )}
                  <span>{copiedPath === activeFile.path ? 'Copied' : 'Copy File'}</span>
                </button>
                <button
                  type="button"
                  onClick={handleDownloadSingleFile}
                  className="inline-flex items-center gap-1.5 rounded-md border border-zinc-800 px-3 py-1.5 text-xs font-medium text-zinc-300 hover:bg-zinc-900 hover:text-zinc-100 transition-colors whitespace-nowrap shrink-0"
                >
                  <Download className="h-3.5 w-3.5" />
                  <span>Save</span>
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-auto bg-[#080A0E] p-4">
              <table className="w-full border-collapse font-mono text-[12.5px] leading-6">
                <tbody>
                  {codeLines.map((line, index) => (
                    <tr key={index} className="hover:bg-zinc-900/50">
                      <td className="w-12 select-none pr-4 text-right align-top text-zinc-600 tabular-nums">
                        {index + 1}
                      </td>
                      <td className="whitespace-pre pr-4">
                        {renderHighlightedLine(line, activeFile.language)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        ) : null}
      </section>
    </div>
  );
};

function renderHighlightedLine(line: string, lang: RepoFile['language']): React.ReactNode {
  if (!line) return ' ';
  const trimmed = line.trimStart();
  if (
    trimmed.startsWith('//') ||
    trimmed.startsWith('#') ||
    trimmed.startsWith('*') ||
    trimmed.startsWith('/*') ||
    trimmed.startsWith('<!--')
  ) {
    return <span className="text-zinc-500">{line}</span>;
  }

  if (lang === 'yaml' && trimmed.includes(':')) {
    const colonIdx = line.indexOf(':');
    return (
      <>
        <span className="text-sky-300">{line.slice(0, colonIdx + 1)}</span>
        <span className="text-zinc-200">{line.slice(colonIdx + 1)}</span>
      </>
    );
  }

  const tokenRegex =
    /("(?:\\.|[^"\\])*"|@\w+|\b(?:package|import|class|data|object|interface|fun|val|var|private|override|suspend|return|if|else|when|for|while|try|catch|finally|def|async|await|from|as|with|plugins|android|dependencies|implementation|ksp|true|false|null|None|True|False)\b)/g;

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = tokenRegex.exec(line)) !== null) {
    if (match.index > lastIndex) {
      parts.push(
        <span key={`t-${lastIndex}`} className="text-zinc-200">
          {line.slice(lastIndex, match.index)}
        </span>
      );
    }
    const token = match[0];
    let cls = 'text-sky-300';
    if (token.startsWith('"')) cls = 'text-emerald-300';
    else if (token.startsWith('@')) cls = 'text-amber-300';

    parts.push(
      <span key={`m-${match.index}`} className={cls}>
        {token}
      </span>
    );
    lastIndex = match.index + token.length;
  }

  if (lastIndex < line.length) {
    parts.push(
      <span key={`e-${lastIndex}`} className="text-zinc-200">
        {line.slice(lastIndex)}
      </span>
    );
  }

  return parts.length > 0 ? parts : <span className="text-zinc-200">{line}</span>;
}
