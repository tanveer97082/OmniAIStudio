import { detectCategory, detectLanguage, type RepoFile } from './monorepoAuditor';

const rawModules = import.meta.glob(
  ['/omniai-studio/**/*', '/omniai-studio/.github/**/*'],
  {
    query: '?raw',
    import: 'default',
    eager: true,
  }
) as Record<string, string>;

export const MONOREPO_FILES: RepoFile[] = Object.entries(rawModules)
  .filter(([fullPath]) => !fullPath.includes('__pycache__') && !fullPath.endsWith('.pyc'))
  .map(([fullPath, content]) => {
    const cleanPath = fullPath.replace(/^\/omniai-studio\//, '');
    const normalizedContent = typeof content === 'string' ? content : String(content);
    return {
      path: cleanPath,
      content: normalizedContent,
      language: detectLanguage(cleanPath),
      category: detectCategory(cleanPath),
      lineCount: normalizedContent.split('\n').length,
      byteSize: new TextEncoder().encode(normalizedContent).length,
    };
  })
  .sort((a, b) => a.path.localeCompare(b.path));
