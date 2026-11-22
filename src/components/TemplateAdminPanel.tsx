import React, { useState } from 'react';
import {
  ArrowDown,
  ArrowUp,
  Copy,
  Pencil,
  Plus,
  Save,
  SlidersHorizontal,
  Trash2,
  X,
} from 'lucide-react';
import {
  cloneTemplateToDraft,
  createBlankSceneBlueprint,
  createEmptyTemplateDraft,
  filterVideoTemplates,
  reorderTemplateScenes,
  TEMPLATE_CATEGORIES,
  validateTemplateDraft,
  VISUAL_STYLE_OPTIONS,
  VOICEOVER_OPTIONS,
  type TemplateCategory,
  type TemplateDraft,
  type VideoTemplateRecord,
  type VisualStyleOption,
  type VoiceoverOption,
} from '../utils/templateAdmin';

interface TemplateAdminPanelProps {
  builtinTemplates: VideoTemplateRecord[];
  customTemplates: VideoTemplateRecord[];
  onApplyTemplate: (template: VideoTemplateRecord) => void;
  onSaveCustomTemplate: (draft: TemplateDraft) => VideoTemplateRecord;
  onDeleteCustomTemplate: (id: string) => void;
}

export const TemplateAdminPanel: React.FC<TemplateAdminPanelProps> = ({
  builtinTemplates,
  customTemplates,
  onApplyTemplate,
  onSaveCustomTemplate,
  onDeleteCustomTemplate,
}) => {
  const [mode, setMode] = useState<'library' | 'admin'>('library');
  const [selectedCategory, setSelectedCategory] = useState<'All' | TemplateCategory>('All');
  const [searchQuery, setSearchQuery] = useState('');
  const [draft, setDraft] = useState<TemplateDraft | null>(null);
  const [validationErrors, setValidationErrors] = useState<string[]>([]);
  const [statusNotice, setStatusNotice] = useState<string | null>(null);

  const allTemplates = [...customTemplates, ...builtinTemplates];
  const visibleTemplates = filterVideoTemplates(allTemplates, selectedCategory, searchQuery);

  const openCreateForm = () => {
    setMode('admin');
    setDraft(createEmptyTemplateDraft());
    setValidationErrors([]);
    setStatusNotice(null);
  };

  const openEditForm = (template: VideoTemplateRecord) => {
    setMode('admin');
    setDraft(cloneTemplateToDraft(template, 'edit'));
    setValidationErrors([]);
    setStatusNotice(null);
  };

  const openCloneForm = (template: VideoTemplateRecord) => {
    setMode('admin');
    setDraft(cloneTemplateToDraft(template, 'clone'));
    setValidationErrors([]);
    setStatusNotice(null);
  };

  const handleSaveDraft = (e: React.FormEvent) => {
    e.preventDefault();
    if (!draft) return;

    const validation = validateTemplateDraft(draft);
    if (!validation.valid) {
      setValidationErrors(validation.errors);
      return;
    }

    const saved = onSaveCustomTemplate(draft);
    setValidationErrors([]);
    setDraft(null);
    setStatusNotice(`Saved custom template "${saved.name}" (${saved.scenes.length} scenes).`);
  };

  const updateDraftScene = (
    index: number,
    patch: Partial<TemplateDraft['scenes'][number]>
  ) => {
    setDraft((prev) => {
      if (!prev) return prev;
      return {
        ...prev,
        scenes: prev.scenes.map((sc, i) => (i === index ? { ...sc, ...patch } : sc)),
      };
    });
  };

  return (
    <section
      aria-label="Video Templates Section"
      className="space-y-2.5 rounded-lg border border-zinc-800 bg-[#0B0E14] p-2.5"
    >
      {/* Header Bar */}
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <h3 className="text-xs font-semibold text-zinc-100">Templates</h3>
          <p className="font-mono text-[10px] text-zinc-400 tabular-nums">
            {builtinTemplates.length} built-in · {customTemplates.length} custom
          </p>
        </div>

        <div className="flex items-center gap-1.5">
          <div
            role="tablist"
            aria-label="Template view mode"
            className="flex rounded border border-zinc-800 bg-black p-0.5 text-[10px]"
          >
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'library'}
              onClick={() => {
                setMode('library');
                setDraft(null);
                setValidationErrors([]);
              }}
              className={`rounded px-2 py-0.5 font-medium transition-colors ${
                mode === 'library'
                  ? 'bg-zinc-800 text-zinc-100'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              Library
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'admin'}
              onClick={() => setMode('admin')}
              className={`inline-flex items-center gap-1 rounded px-2 py-0.5 font-medium transition-colors ${
                mode === 'admin'
                  ? 'bg-emerald-500/20 text-emerald-300'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <SlidersHorizontal className="h-3 w-3" />
              <span>Template Admin</span>
            </button>
          </div>

          <button
            type="button"
            onClick={openCreateForm}
            className="inline-flex items-center gap-1 rounded bg-emerald-500 px-2 py-1 text-[10px] font-semibold text-zinc-950 hover:bg-emerald-400 whitespace-nowrap"
          >
            <Plus className="h-3 w-3" />
            <span>New Template</span>
          </button>
        </div>
      </div>

      {statusNotice && (
        <div
          role="status"
          className="flex items-center justify-between gap-2 rounded border border-emerald-500/30 bg-emerald-950/25 px-2.5 py-1.5 text-[10px] text-emerald-200"
        >
          <span>{statusNotice}</span>
          <button
            type="button"
            aria-label="Dismiss template notice"
            onClick={() => setStatusNotice(null)}
            className="text-emerald-300 hover:text-emerald-100"
          >
            <X className="h-3 w-3" />
          </button>
        </div>
      )}

      {/* Admin Create / Customize Form */}
      {mode === 'admin' && draft && (
        <form
          onSubmit={handleSaveDraft}
          className="space-y-2.5 rounded-lg border border-emerald-500/40 bg-black p-2.5"
        >
          <div className="flex items-center justify-between border-b border-zinc-800 pb-2">
            <div>
              <h4 className="text-xs font-semibold text-emerald-300">
                {draft.id ? 'Customize Scene Template' : 'Create Custom Scene Template'}
              </h4>
              <p className="text-[10px] text-zinc-400">
                Define default visual style, voiceover voice, and reusable placeholder scenes.
              </p>
            </div>
            <button
              type="button"
              aria-label="Close template editor"
              onClick={() => {
                setDraft(null);
                setValidationErrors([]);
              }}
              className="rounded p-1 text-zinc-400 hover:bg-zinc-900 hover:text-zinc-100"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>

          {validationErrors.length > 0 && (
            <div
              role="alert"
              className="space-y-0.5 rounded border border-red-500/40 bg-red-950/30 px-2.5 py-2 text-[10px] text-red-200"
            >
              {validationErrors.map((err, i) => (
                <div key={i}>• {err}</div>
              ))}
            </div>
          )}

          {/* Core Template Metadata */}
          <div className="grid grid-cols-1 gap-2">
            <div>
              <label
                htmlFor="tpl-admin-name"
                className="mb-1 block text-[10px] font-medium text-zinc-300"
              >
                Template Name
              </label>
              <input
                id="tpl-admin-name"
                type="text"
                value={draft.name}
                onChange={(e) => {
                  setDraft({ ...draft, name: e.target.value });
                  if (validationErrors.length > 0) setValidationErrors([]);
                }}
                placeholder="e.g., Tech Unboxing Blueprint"
                className="w-full rounded border border-zinc-800 bg-[#0B0E14] px-2 py-1.5 text-[11px] text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
              />
            </div>

            <div>
              <label
                htmlFor="tpl-admin-desc"
                className="mb-1 block text-[10px] font-medium text-zinc-300"
              >
                Summary / Use Case
              </label>
              <input
                id="tpl-admin-desc"
                type="text"
                value={draft.description}
                onChange={(e) => setDraft({ ...draft, description: e.target.value })}
                placeholder="Short description of this scene flow"
                className="w-full rounded border border-zinc-800 bg-[#0B0E14] px-2 py-1.5 text-[11px] text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label
                  htmlFor="tpl-admin-category"
                  className="mb-1 block text-[10px] font-medium text-zinc-300"
                >
                  Category
                </label>
                <select
                  id="tpl-admin-category"
                  value={draft.category}
                  onChange={(e) =>
                    setDraft({ ...draft, category: e.target.value as TemplateCategory })
                  }
                  className="w-full rounded border border-zinc-800 bg-[#0B0E14] px-2 py-1.5 text-[11px] text-zinc-100 focus:border-emerald-500/60 focus:outline-none"
                >
                  {TEMPLATE_CATEGORIES.map((cat) => (
                    <option key={cat} value={cat}>
                      {cat}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label
                  htmlFor="tpl-admin-style"
                  className="mb-1 block text-[10px] font-medium text-zinc-300"
                >
                  Default Visual Style
                </label>
                <select
                  id="tpl-admin-style"
                  value={draft.defaultVisualStyle}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      defaultVisualStyle: e.target.value as VisualStyleOption,
                    })
                  }
                  className="w-full rounded border border-zinc-800 bg-[#0B0E14] px-2 py-1.5 text-[11px] text-zinc-100 focus:border-emerald-500/60 focus:outline-none"
                >
                  {VISUAL_STYLE_OPTIONS.map((style) => (
                    <option key={style} value={style}>
                      {style}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label
                  htmlFor="tpl-admin-voice"
                  className="mb-1 block text-[10px] font-medium text-zinc-300"
                >
                  Voiceover Voice
                </label>
                <select
                  id="tpl-admin-voice"
                  value={draft.defaultVoice}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      defaultVoice: e.target.value as VoiceoverOption,
                    })
                  }
                  className="w-full rounded border border-zinc-800 bg-[#0B0E14] px-2 py-1.5 text-[11px] text-zinc-100 focus:border-emerald-500/60 focus:outline-none"
                >
                  {VOICEOVER_OPTIONS.map((voice) => (
                    <option key={voice} value={voice}>
                      {voice}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <span className="mb-1 block text-[10px] font-medium text-zinc-300">
                  Aspect &amp; SRT
                </span>
                <div className="flex items-center gap-1">
                  {(['9:16', '16:9'] as const).map((ratio) => (
                    <button
                      key={ratio}
                      type="button"
                      onClick={() => setDraft({ ...draft, aspectRatio: ratio })}
                      className={`rounded px-2 py-1 font-mono text-[10px] ${
                        draft.aspectRatio === ratio
                          ? 'bg-emerald-500/20 text-emerald-300'
                          : 'bg-zinc-900 text-zinc-400'
                      }`}
                    >
                      {ratio}
                    </button>
                  ))}
                  <label className="ml-auto inline-flex items-center gap-1 text-[10px] text-zinc-300">
                    <input
                      type="checkbox"
                      checked={draft.burnSubtitlesDefault}
                      onChange={(e) =>
                        setDraft({ ...draft, burnSubtitlesDefault: e.target.checked })
                      }
                      className="accent-emerald-500"
                    />
                    <span>SRT</span>
                  </label>
                </div>
              </div>
            </div>
          </div>

          {/* Scene Structure Builder */}
          <div className="space-y-2 border-t border-zinc-800 pt-2">
            <div className="flex items-center justify-between">
              <span className="text-[11px] font-semibold text-zinc-200">
                Scene Structure ({draft.scenes.length})
              </span>
              <button
                type="button"
                onClick={() =>
                  setDraft({
                    ...draft,
                    scenes: [...draft.scenes, createBlankSceneBlueprint(draft.scenes.length)],
                  })
                }
                className="inline-flex items-center gap-1 text-[10px] font-medium text-emerald-400 hover:text-emerald-300"
              >
                <Plus className="h-3 w-3" />
                <span>Add Blueprint Scene</span>
              </button>
            </div>

            {draft.scenes.map((scene, idx) => (
              <div
                key={scene.id}
                className="space-y-1.5 rounded border border-zinc-800 bg-[#0B0E14] p-2"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-mono text-[10px] font-medium text-emerald-400">
                    Scene {idx + 1}
                  </span>
                  <div className="flex items-center gap-0.5">
                    <button
                      type="button"
                      aria-label={`Move template scene ${idx + 1} up`}
                      disabled={idx === 0}
                      onClick={() =>
                        setDraft({
                          ...draft,
                          scenes: reorderTemplateScenes(draft.scenes, idx, -1),
                        })
                      }
                      className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                    >
                      <ArrowUp className="h-3 w-3" />
                    </button>
                    <button
                      type="button"
                      aria-label={`Move template scene ${idx + 1} down`}
                      disabled={idx === draft.scenes.length - 1}
                      onClick={() =>
                        setDraft({
                          ...draft,
                          scenes: reorderTemplateScenes(draft.scenes, idx, 1),
                        })
                      }
                      className="p-1 text-zinc-400 hover:text-zinc-100 disabled:opacity-30"
                    >
                      <ArrowDown className="h-3 w-3" />
                    </button>
                    <button
                      type="button"
                      aria-label={`Remove template scene ${idx + 1}`}
                      disabled={draft.scenes.length <= 1}
                      onClick={() =>
                        setDraft({
                          ...draft,
                          scenes: draft.scenes.filter((_, i) => i !== idx),
                        })
                      }
                      className="p-1 text-red-400 hover:text-red-300 disabled:opacity-30"
                    >
                      <Trash2 className="h-3 w-3" />
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-1.5">
                  <div className="col-span-2">
                    <label
                      htmlFor={`tpl-scene-beat-${idx}`}
                      className="mb-0.5 block text-[10px] text-zinc-400"
                    >
                      Beat Title
                    </label>
                    <input
                      id={`tpl-scene-beat-${idx}`}
                      type="text"
                      value={scene.beatName}
                      onChange={(e) => updateDraftScene(idx, { beatName: e.target.value })}
                      placeholder="e.g., 01 · Hook"
                      className="w-full rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-100 focus:border-emerald-500/60 focus:outline-none"
                    />
                  </div>
                  <div>
                    <label
                      htmlFor={`tpl-scene-dur-${idx}`}
                      className="mb-0.5 block text-[10px] text-zinc-400"
                    >
                      Sec
                    </label>
                    <input
                      id={`tpl-scene-dur-${idx}`}
                      type="number"
                      min={2}
                      max={15}
                      value={scene.durationSec}
                      onChange={(e) =>
                        updateDraftScene(idx, { durationSec: Number(e.target.value) || 5 })
                      }
                      className="w-full rounded border border-zinc-800 bg-black px-2 py-1 font-mono text-[11px] text-zinc-100 tabular-nums focus:border-emerald-500/60 focus:outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label
                    htmlFor={`tpl-scene-visual-${idx}`}
                    className="mb-0.5 block text-[10px] text-zinc-400"
                  >
                    Placeholder Visual Description
                  </label>
                  <textarea
                    id={`tpl-scene-visual-${idx}`}
                    rows={2}
                    value={scene.visualDescription}
                    onChange={(e) =>
                      updateDraftScene(idx, { visualDescription: e.target.value })
                    }
                    placeholder="e.g., Macro orbital shot of [SUBJECT] under volumetric rim lighting"
                    className="w-full rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
                  />
                </div>

                <div>
                  <label
                    htmlFor={`tpl-scene-vo-${idx}`}
                    className="mb-0.5 block text-[10px] text-zinc-400"
                  >
                    Placeholder Narration Description
                  </label>
                  <textarea
                    id={`tpl-scene-vo-${idx}`}
                    rows={2}
                    value={scene.narrationPlaceholder}
                    onChange={(e) =>
                      updateDraftScene(idx, { narrationPlaceholder: e.target.value })
                    }
                    placeholder="e.g., Introduce [TOPIC] with a punchy one-line question"
                    className="w-full rounded border border-zinc-800 bg-black px-2 py-1 text-[11px] text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
                  />
                </div>
              </div>
            ))}
          </div>

          <div className="flex items-center gap-2 pt-1">
            <button
              type="submit"
              className="flex flex-1 items-center justify-center gap-1.5 rounded bg-emerald-500 py-1.5 text-[11px] font-semibold text-zinc-950 hover:bg-emerald-400"
            >
              <Save className="h-3.5 w-3.5" />
              <span>{draft.id ? 'Update Template' : 'Save Custom Template'}</span>
            </button>
            <button
              type="button"
              onClick={() => {
                setDraft(null);
                setValidationErrors([]);
              }}
              className="rounded border border-zinc-700 px-3 py-1.5 text-[11px] text-zinc-300 hover:bg-zinc-900"
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {/* Admin Search Bar */}
      {mode === 'admin' && !draft && (
        <input
          type="search"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search templates by name, style, or voice..."
          aria-label="Search templates"
          className="w-full rounded border border-zinc-800 bg-black px-2.5 py-1.5 text-[11px] text-zinc-100 placeholder:text-zinc-500 focus:border-emerald-500/60 focus:outline-none"
        />
      )}

      {/* Category Filter Strip */}
      <div className="flex flex-wrap gap-1">
        {(['All', ...TEMPLATE_CATEGORIES] as const).map((cat) => (
          <button
            key={cat}
            type="button"
            onClick={() => setSelectedCategory(cat)}
            className={`rounded px-2 py-0.5 text-[10px] font-medium transition-colors ${
              selectedCategory === cat
                ? 'bg-emerald-500/20 text-emerald-300'
                : 'bg-zinc-900 text-zinc-400 hover:text-zinc-200'
            }`}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Template List (Library & Admin Registry) */}
      <div className="space-y-1.5">
        {visibleTemplates.length === 0 ? (
          <div className="rounded border border-dashed border-zinc-800 bg-black/50 p-3 text-center text-[11px] text-zinc-500">
            No templates match this filter. Click <strong className="text-zinc-300">New Template</strong> to build one.
          </div>
        ) : (
          visibleTemplates.map((tpl) => (
            <div
              key={tpl.id}
              className={`rounded border p-2 text-[11px] ${
                tpl.isCustom
                  ? 'border-emerald-500/35 bg-black'
                  : 'border-zinc-800/80 bg-black'
              }`}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex items-center gap-1.5">
                    <span className="truncate font-medium text-zinc-100">{tpl.name}</span>
                    {tpl.isCustom && (
                      <span className="shrink-0 rounded bg-emerald-500/15 px-1.5 py-0.5 font-mono text-[9px] text-emerald-300">
                        Custom
                      </span>
                    )}
                  </div>
                  <div className="mt-0.5 font-mono text-[10px] text-zinc-400">
                    {tpl.category} · {tpl.scenes.length} scenes · {tpl.aspectRatio}
                  </div>
                </div>

                <button
                  type="button"
                  aria-label={`Use template ${tpl.name}`}
                  onClick={() => {
                    onApplyTemplate(tpl);
                    setStatusNotice(`Applied "${tpl.name}" to Video Director.`);
                  }}
                  className="shrink-0 rounded border border-emerald-500/40 bg-emerald-500/10 px-2 py-1 text-[10px] font-medium text-emerald-300 hover:bg-emerald-500/20"
                >
                  Use Template
                </button>
              </div>

              <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-0.5 font-mono text-[10px] text-zinc-300">
                <span>
                  <span className="text-zinc-500">Style:</span> {tpl.defaultVisualStyle}
                </span>
                <span className="text-zinc-700">·</span>
                <span>
                  <span className="text-zinc-500">Voice:</span> {tpl.defaultVoice}
                </span>
              </div>

              {mode === 'admin' && (
                <div className="mt-2 flex flex-wrap items-center justify-between gap-2 border-t border-zinc-900 pt-1.5">
                  <span className="truncate text-[10px] text-zinc-500">
                    {tpl.scenes.map((s) => s.beatName).join(' → ')}
                  </span>
                  <div className="flex items-center gap-1 shrink-0">
                    {tpl.isCustom ? (
                      <>
                        <button
                          type="button"
                          aria-label={`Edit template ${tpl.name}`}
                          onClick={() => openEditForm(tpl)}
                          className="inline-flex items-center gap-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-0.5 text-[10px] text-zinc-200 hover:border-emerald-500/50 hover:text-emerald-300"
                        >
                          <Pencil className="h-2.5 w-2.5" />
                          <span>Edit</span>
                        </button>
                        <button
                          type="button"
                          aria-label={`Delete template ${tpl.name}`}
                          onClick={() => {
                            onDeleteCustomTemplate(tpl.id);
                            setStatusNotice(`Deleted custom template "${tpl.name}".`);
                          }}
                          className="inline-flex items-center gap-1 rounded border border-red-500/35 bg-red-950/30 px-2 py-0.5 text-[10px] text-red-300 hover:bg-red-950/60"
                        >
                          <Trash2 className="h-2.5 w-2.5" />
                          <span>Delete</span>
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        aria-label={`Clone template ${tpl.name}`}
                        onClick={() => openCloneForm(tpl)}
                        className="inline-flex items-center gap-1 rounded border border-zinc-700 bg-zinc-900 px-2 py-0.5 text-[10px] text-zinc-200 hover:border-emerald-500/50 hover:text-emerald-300"
                      >
                        <Copy className="h-2.5 w-2.5" />
                        <span>Clone &amp; Customize</span>
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))
        )}
      </div>
    </section>
  );
};
