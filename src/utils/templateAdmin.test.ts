import test from 'node:test';
import assert from 'node:assert/strict';
import {
  BUILTIN_VIDEO_TEMPLATES,
  cloneTemplateToDraft,
  createEmptyTemplateDraft,
  deleteCustomTemplate,
  filterVideoTemplates,
  reorderTemplateScenes,
  upsertCustomTemplate,
  validateTemplateDraft,
  type VideoTemplateRecord,
} from './templateAdmin';

test('validateTemplateDraft rejects empty name and blank placeholder descriptions without bypassing validation', () => {
  const emptyDraft = createEmptyTemplateDraft();
  const result = validateTemplateDraft(emptyDraft);

  assert.equal(result.valid, false);
  assert.ok(result.errors.some((e) => e.includes('Template name is required')));
  assert.ok(result.errors.some((e) => e.includes('placeholder visual description is required')));
  assert.ok(result.errors.some((e) => e.includes('placeholder narration description is required')));
});

test('upsertCustomTemplate creates, updates, and deletes custom templates cleanly', () => {
  const draft = createEmptyTemplateDraft();
  draft.name = 'Tech Unboxing Blueprint';
  draft.category = 'Promotional';
  draft.defaultVisualStyle = 'Sci-Fi Epic';
  draft.defaultVoice = 'Hindi Female';
  draft.aspectRatio = '9:16';
  draft.scenes = [
    {
      id: 's1',
      beatName: '01 · Box Reveal',
      visualDescription: 'Overhead macro reveal of [GADGET] packaging on dark obsidian desk',
      narrationPlaceholder: 'Aaj hum unbox kar rahe hain [GADGET]—dekhiye kya hai box ke andar.',
      durationSec: 5,
    },
    {
      id: 's2',
      beatName: '02 · Killer Feature',
      visualDescription: 'Slow orbital close-up highlighting [HERO SPEC] under rim light',
      narrationPlaceholder: 'Iska sabse bada highlight hai [HERO SPEC], jo speed ko double karta hai.',
      durationSec: 6,
    },
  ];

  const created = upsertCustomTemplate([], draft);
  assert.equal(created.templates.length, 1);
  assert.equal(created.saved.isCustom, true);
  assert.equal(created.saved.defaultVisualStyle, 'Sci-Fi Epic');
  assert.equal(created.saved.defaultVoice, 'Hindi Female');
  assert.equal(created.saved.scenes.length, 2);

  // Update existing custom template
  const editDraft = cloneTemplateToDraft(created.saved, 'edit');
  editDraft.name = 'Tech Unboxing Blueprint v2';
  const updated = upsertCustomTemplate(created.templates, editDraft);
  assert.equal(updated.templates.length, 1);
  assert.equal(updated.saved.id, created.saved.id);
  assert.equal(updated.saved.name, 'Tech Unboxing Blueprint v2');

  // Delete custom template
  const afterDelete = deleteCustomTemplate(updated.templates, updated.saved.id);
  assert.equal(afterDelete.length, 0);
});

test('cloneTemplateToDraft duplicates a built-in template into an editable custom draft', () => {
  const source = BUILTIN_VIDEO_TEMPLATES[0];
  const cloned = cloneTemplateToDraft(source, 'clone');

  assert.equal(cloned.id, null);
  assert.equal(cloned.name, `${source.name} (Custom)`);
  assert.equal(cloned.defaultVisualStyle, source.defaultVisualStyle);
  assert.equal(cloned.defaultVoice, source.defaultVoice);
  assert.equal(cloned.scenes.length, source.scenes.length);
  assert.equal(validateTemplateDraft(cloned).valid, true);
});

test('reorderTemplateScenes moves scenes safely and preserves bounds', () => {
  const scenes = BUILTIN_VIDEO_TEMPLATES[0].scenes;
  const moved = reorderTemplateScenes(scenes, 0, 1);
  assert.equal(moved[0].beatName, scenes[1].beatName);
  assert.equal(moved[1].beatName, scenes[0].beatName);

  // Out-of-bounds move returns unchanged order
  const unchanged = reorderTemplateScenes(scenes, 0, -1);
  assert.equal(unchanged[0].beatName, scenes[0].beatName);
});

test('filterVideoTemplates filters by category and search query across style and voice', () => {
  const custom: VideoTemplateRecord = {
    id: 'c1',
    name: 'AI Shorts Framework',
    category: 'Custom',
    description: 'Rapid vertical hooks',
    aspectRatio: '9:16',
    defaultVisualStyle: 'Anime 4K',
    defaultVoice: 'Hindi Male',
    burnSubtitlesDefault: true,
    isCustom: true,
    scenes: BUILTIN_VIDEO_TEMPLATES[0].scenes.slice(0, 2),
  };
  const all = [custom, ...BUILTIN_VIDEO_TEMPLATES];

  assert.equal(filterVideoTemplates(all, 'Custom').length, 1);
  assert.equal(filterVideoTemplates(all, 'All', 'Anime 4K').length, 1);
  assert.equal(filterVideoTemplates(all, 'All', 'Hindi Male').length, 1);
  assert.equal(filterVideoTemplates(all, 'Educational', 'Explainer').length, 1);
});
