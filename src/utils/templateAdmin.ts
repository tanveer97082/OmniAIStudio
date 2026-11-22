export type TemplateCategory =
  | 'Educational'
  | 'Promotional'
  | 'Storytelling'
  | 'Documentary'
  | 'Custom';

export type VisualStyleOption =
  | 'Cinematic Realism'
  | 'Noir Thriller'
  | 'Anime 4K'
  | 'Documentary'
  | 'Sci-Fi Epic'
  | 'Minimalist Studio';

export type VoiceoverOption =
  | 'English Male'
  | 'English Female'
  | 'Hindi Male'
  | 'Hindi Female';

export interface TemplateSceneBlueprint {
  id: string;
  beatName: string;
  visualDescription: string;
  narrationPlaceholder: string;
  durationSec: number;
}

export interface VideoTemplateRecord {
  id: string;
  name: string;
  category: TemplateCategory;
  description: string;
  aspectRatio: '9:16' | '16:9';
  defaultVisualStyle: VisualStyleOption;
  defaultVoice: VoiceoverOption;
  burnSubtitlesDefault: boolean;
  isCustom: boolean;
  scenes: TemplateSceneBlueprint[];
}

export interface TemplateDraft {
  id: string | null;
  name: string;
  category: TemplateCategory;
  description: string;
  aspectRatio: '9:16' | '16:9';
  defaultVisualStyle: VisualStyleOption;
  defaultVoice: VoiceoverOption;
  burnSubtitlesDefault: boolean;
  scenes: TemplateSceneBlueprint[];
}

export interface TemplateValidationResult {
  valid: boolean;
  errors: string[];
}

export const TEMPLATE_CATEGORIES: TemplateCategory[] = [
  'Educational',
  'Promotional',
  'Storytelling',
  'Documentary',
  'Custom',
];

export const VISUAL_STYLE_OPTIONS: VisualStyleOption[] = [
  'Cinematic Realism',
  'Noir Thriller',
  'Anime 4K',
  'Documentary',
  'Sci-Fi Epic',
  'Minimalist Studio',
];

export const VOICEOVER_OPTIONS: VoiceoverOption[] = [
  'English Male',
  'English Female',
  'Hindi Male',
  'Hindi Female',
];

export const CUSTOM_TEMPLATES_STORAGE_KEY = 'omniai_custom_video_templates_v1';

export const BUILTIN_VIDEO_TEMPLATES: VideoTemplateRecord[] = [
  {
    id: 'edu_concept_breakdown',
    name: '4-Step Concept Explainer',
    category: 'Educational',
    description:
      'Hook curiosity, visualize the core mechanism, compare a real-world analogy, and recap the key takeaway.',
    aspectRatio: '16:9',
    defaultVisualStyle: 'Documentary',
    defaultVoice: 'English Male',
    burnSubtitlesDefault: true,
    isCustom: false,
    scenes: [
      {
        id: 'edu-1',
        beatName: '01 · Curiosity Hook',
        visualDescription:
          'Macro cinematic slow zoom into [CORE SUBJECT], studio key light on dark slate backdrop',
        narrationPlaceholder:
          'Most people think [COMMON MYTH], but what happens inside [TOPIC] is far more surprising.',
        durationSec: 5,
      },
      {
        id: 'edu-2',
        beatName: '02 · Core Mechanism',
        visualDescription:
          'Clean isometric cutaway showing [COMPONENT A] feeding [COMPONENT B], orbital camera',
        narrationPlaceholder:
          'When [TRIGGER EVENT] occurs, [MECHANISM] reacts in milliseconds to produce [RESULT].',
        durationSec: 6,
      },
      {
        id: 'edu-3',
        beatName: '03 · Real-World Analogy',
        visualDescription:
          'Daylight documentary shot of [EVERYDAY ANALOGY], shallow depth of field',
        narrationPlaceholder:
          'Think of it like [RELATABLE ANALOGY]—balanced without wasting resources.',
        durationSec: 5,
      },
      {
        id: 'edu-4',
        beatName: '04 · Key Takeaway',
        visualDescription:
          'Wide hero shot of [FINAL APPLICATION] at golden hour, gentle dolly pull-back',
        narrationPlaceholder:
          'Remember this single rule about [TOPIC]: [ONE-SENTENCE SUMMARY].',
        durationSec: 5,
      },
    ],
  },
  {
    id: 'promo_product_launch',
    name: 'High-Impact Product Launch',
    category: 'Promotional',
    description:
      'Problem-agitation-reveal structure engineered for product ads, SaaS trailers, and hardware launches.',
    aspectRatio: '9:16',
    defaultVisualStyle: 'Cinematic Realism',
    defaultVoice: 'English Female',
    burnSubtitlesDefault: true,
    isCustom: false,
    scenes: [
      {
        id: 'promo-1',
        beatName: '01 · Pain Point',
        visualDescription:
          'Moody desaturated close-up of user struggling with [OLD PROBLEM], cool overcast light',
        narrationPlaceholder:
          'Still wasting hours fighting [PAIN POINT]? There is a faster way built for [AUDIENCE].',
        durationSec: 4,
      },
      {
        id: 'promo-2',
        beatName: '02 · Hero Product Reveal',
        visualDescription:
          'Sleek studio hero reveal of [PRODUCT NAME] rotating on matte obsidian surface, rim light',
        narrationPlaceholder:
          'Meet [PRODUCT NAME]—engineered to deliver [PRIMARY BENEFIT] in one tap.',
        durationSec: 5,
      },
      {
        id: 'promo-3',
        beatName: '03 · Proof in Action',
        visualDescription:
          'Lifestyle tracking shot of [USER] effortlessly completing [WORKFLOW] with [PRODUCT NAME]',
        narrationPlaceholder:
          'From [FEATURE 1] to [FEATURE 2], experience [MEASURABLE OUTCOME].',
        durationSec: 5,
      },
      {
        id: 'promo-4',
        beatName: '04 · Call to Action',
        visualDescription:
          'Centered minimalist product shot with clean space for burned CTA captions',
        narrationPlaceholder:
          'Try [PRODUCT NAME] today at [LINK] and unlock [OFFER].',
        durationSec: 4,
      },
    ],
  },
  {
    id: 'story_cinematic_arc',
    name: '5-Act Cinematic Storytelling Arc',
    category: 'Storytelling',
    description:
      'Classic narrative progression: world-building, inciting spark, rising tension, turning point, and resolution.',
    aspectRatio: '16:9',
    defaultVisualStyle: 'Noir Thriller',
    defaultVoice: 'English Male',
    burnSubtitlesDefault: false,
    isCustom: false,
    scenes: [
      {
        id: 'story-1',
        beatName: '01 · Establishing World',
        visualDescription:
          'Sweeping aerial drone shot over [SETTING] at dusk, mist drifting, 35mm anamorphic',
        narrationPlaceholder:
          'Every legend begins in a quiet place nobody expects—[SETTING DESCRIPTION].',
        durationSec: 5,
      },
      {
        id: 'story-2',
        beatName: '02 · Inciting Moment',
        visualDescription:
          'Intimate close-up of [PROTAGONIST] discovering [ARTIFACT], flickering light',
        narrationPlaceholder:
          'On the night [INCITING EVENT] happened, everything [PROTAGONIST] believed changed.',
        durationSec: 5,
      },
      {
        id: 'story-3',
        beatName: '03 · Rising Tension',
        visualDescription:
          'Fast dolly tracking shot through [CONFLICT ENVIRONMENT], rain streaks and deep shadows',
        narrationPlaceholder:
          'With [OBSTACLE] closing in, turning back was no longer an option.',
        durationSec: 5,
      },
      {
        id: 'story-4',
        beatName: '04 · Turning Point',
        visualDescription:
          'Low-angle heroic wide shot as [PROTAGONIST] executes [DECISIVE ACTION], dawn light',
        narrationPlaceholder:
          'In that decisive second, [KEY CHOICE] rewrote the outcome.',
        durationSec: 5,
      },
    ],
  },
  {
    id: 'doc_mini_profile',
    name: 'Mini Documentary Case Study',
    category: 'Documentary',
    description:
      'Editorial documentary flow highlighting a real-world breakthrough, technical challenge, and future impact.',
    aspectRatio: '16:9',
    defaultVisualStyle: 'Documentary',
    defaultVoice: 'English Female',
    burnSubtitlesDefault: true,
    isCustom: false,
    scenes: [
      {
        id: 'doc-1',
        beatName: '01 · Cold Open',
        visualDescription:
          'High-contrast documentary wide shot of [LAB OR LOCATION], Leica 35mm film grain',
        narrationPlaceholder:
          'Inside [LOCATION], a small team set out to solve [CHALLENGE].',
        durationSec: 5,
      },
      {
        id: 'doc-2',
        beatName: '02 · The Bottleneck',
        visualDescription:
          'Tight detail shots of [HARDWARE OR FIELDWORK], authentic natural shadows',
        narrationPlaceholder:
          'For years, [LEGACY LIMITATION] forced builders to choose between [A] and [B].',
        durationSec: 5,
      },
      {
        id: 'doc-3',
        beatName: '03 · Breakthrough & Future',
        visualDescription:
          'Sunlit architectural wide shot of [NEW SYSTEM IN USE], steadicam glide',
        narrationPlaceholder:
          'By rethinking [INNOVATION], [PROJECT] opened a new chapter for [IMPACT].',
        durationSec: 5,
      },
    ],
  },
];

export function createBlankSceneBlueprint(index: number): TemplateSceneBlueprint {
  const padded = String(index + 1).padStart(2, '0');
  return {
    id: `scene-${Date.now()}-${index}-${Math.random().toString(36).slice(2, 6)}`,
    beatName: `${padded} · Scene Beat`,
    visualDescription: '',
    narrationPlaceholder: '',
    durationSec: 5,
  };
}

export function createEmptyTemplateDraft(): TemplateDraft {
  return {
    id: null,
    name: '',
    category: 'Custom',
    description: '',
    aspectRatio: '9:16',
    defaultVisualStyle: 'Cinematic Realism',
    defaultVoice: 'English Male',
    burnSubtitlesDefault: true,
    scenes: [
      {
        id: `scene-init-${Date.now()}`,
        beatName: '01 · Opening Hook',
        visualDescription: '',
        narrationPlaceholder: '',
        durationSec: 5,
      },
    ],
  };
}

export function cloneTemplateToDraft(
  source: VideoTemplateRecord,
  mode: 'edit' | 'clone'
): TemplateDraft {
  const isClone = mode === 'clone' || !source.isCustom;
  return {
    id: isClone ? null : source.id,
    name: isClone ? `${source.name} (Custom)` : source.name,
    category: source.category,
    description: source.description,
    aspectRatio: source.aspectRatio,
    defaultVisualStyle: source.defaultVisualStyle,
    defaultVoice: source.defaultVoice,
    burnSubtitlesDefault: source.burnSubtitlesDefault,
    scenes: source.scenes.map((sc, idx) => ({
      ...sc,
      id: isClone ? `clone-${Date.now()}-${idx}` : sc.id,
    })),
  };
}

export function validateTemplateDraft(draft: TemplateDraft): TemplateValidationResult {
  const errors: string[] = [];

  if (!draft.name.trim()) {
    errors.push('Template name is required.');
  }
  if (!VISUAL_STYLE_OPTIONS.includes(draft.defaultVisualStyle)) {
    errors.push('Select a valid default visual style.');
  }
  if (!VOICEOVER_OPTIONS.includes(draft.defaultVoice)) {
    errors.push('Select a valid voiceover voice.');
  }
  if (draft.scenes.length === 0) {
    errors.push('Add at least one scene to the template structure.');
  }

  draft.scenes.forEach((scene, idx) => {
    const num = idx + 1;
    if (!scene.beatName.trim()) {
      errors.push(`Scene ${num}: beat title is required.`);
    }
    if (!scene.visualDescription.trim()) {
      errors.push(`Scene ${num}: placeholder visual description is required.`);
    }
    if (!scene.narrationPlaceholder.trim()) {
      errors.push(`Scene ${num}: placeholder narration description is required.`);
    }
    if (!Number.isFinite(scene.durationSec) || scene.durationSec < 2 || scene.durationSec > 15) {
      errors.push(`Scene ${num}: duration must be between 2 and 15 seconds.`);
    }
  });

  return { valid: errors.length === 0, errors };
}

export function upsertCustomTemplate(
  existingCustom: VideoTemplateRecord[],
  draft: TemplateDraft
): { templates: VideoTemplateRecord[]; saved: VideoTemplateRecord } {
  const validation = validateTemplateDraft(draft);
  if (!validation.valid) {
    throw new Error(validation.errors[0]);
  }

  const saved: VideoTemplateRecord = {
    id: draft.id ?? `custom_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`,
    name: draft.name.trim(),
    category: draft.category,
    description:
      draft.description.trim() ||
      `Custom ${draft.scenes.length}-scene ${draft.defaultVisualStyle} structure.`,
    aspectRatio: draft.aspectRatio,
    defaultVisualStyle: draft.defaultVisualStyle,
    defaultVoice: draft.defaultVoice,
    burnSubtitlesDefault: draft.burnSubtitlesDefault,
    isCustom: true,
    scenes: draft.scenes.map((sc) => ({
      ...sc,
      beatName: sc.beatName.trim(),
      visualDescription: sc.visualDescription.trim(),
      narrationPlaceholder: sc.narrationPlaceholder.trim(),
      durationSec: Math.round(sc.durationSec),
    })),
  };

  const exists = existingCustom.some((t) => t.id === saved.id);
  const templates = exists
    ? existingCustom.map((t) => (t.id === saved.id ? saved : t))
    : [saved, ...existingCustom];

  return { templates, saved };
}

export function deleteCustomTemplate(
  existingCustom: VideoTemplateRecord[],
  id: string
): VideoTemplateRecord[] {
  return existingCustom.filter((t) => t.id !== id);
}

export function reorderTemplateScenes(
  scenes: TemplateSceneBlueprint[],
  index: number,
  delta: number
): TemplateSceneBlueprint[] {
  const target = index + delta;
  if (index < 0 || index >= scenes.length || target < 0 || target >= scenes.length) {
    return scenes;
  }
  const copy = [...scenes];
  const [moved] = copy.splice(index, 1);
  copy.splice(target, 0, moved);
  return copy;
}

export function filterVideoTemplates(
  templates: VideoTemplateRecord[],
  category: 'All' | TemplateCategory,
  searchQuery = ''
): VideoTemplateRecord[] {
  const q = searchQuery.trim().toLowerCase();
  return templates.filter((tpl) => {
    const matchesCat = category === 'All' || tpl.category === category;
    if (!matchesCat) return false;
    if (!q) return true;
    return (
      tpl.name.toLowerCase().includes(q) ||
      tpl.description.toLowerCase().includes(q) ||
      tpl.defaultVisualStyle.toLowerCase().includes(q) ||
      tpl.defaultVoice.toLowerCase().includes(q)
    );
  });
}

export function loadStoredCustomTemplates(): VideoTemplateRecord[] {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return [];
    const raw = window.localStorage.getItem(CUSTOM_TEMPLATES_STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is VideoTemplateRecord =>
        Boolean(item && typeof item.id === 'string' && typeof item.name === 'string' && Array.isArray(item.scenes))
    );
  } catch {
    return [];
  }
}

export function persistCustomTemplates(templates: VideoTemplateRecord[]): void {
  try {
    if (typeof window === 'undefined' || !window.localStorage) return;
    window.localStorage.setItem(CUSTOM_TEMPLATES_STORAGE_KEY, JSON.stringify(templates));
  } catch {
    // Ignore storage quota or private mode errors
  }
}
