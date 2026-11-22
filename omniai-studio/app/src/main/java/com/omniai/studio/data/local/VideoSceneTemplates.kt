package com.omniai.studio.data.local

import com.omniai.studio.data.remote.StoryboardScene
import kotlinx.serialization.Serializable

enum class VideoTemplateCategory(val label: String) {
    EDUCATIONAL("Educational"),
    PROMOTIONAL("Promotional"),
    STORYTELLING("Storytelling"),
    DOCUMENTARY("Documentary"),
    CUSTOM("Custom");

    companion object {
        fun fromStoredName(raw: String): VideoTemplateCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true) }
                ?: CUSTOM
    }
}

@Serializable
data class TemplateSceneBlueprint(
    val beatName: String,
    val visualIdea: String,
    val narrationPlaceholder: String,
    val durationSec: Int = 5
)

data class VideoSceneTemplate(
    val id: String,
    val name: String,
    val category: VideoTemplateCategory,
    val description: String,
    val recommendedAspectRatio: String,
    val recommendedStyle: String,
    val recommendedVoice: String,
    val burnSubtitlesDefault: Boolean,
    val scenes: List<TemplateSceneBlueprint>,
    val isCustom: Boolean = false,
    val customDbId: Long? = null
) {
    fun toStoryboardScenes(): List<StoryboardScene> = scenes.mapIndexed { index, bp ->
        StoryboardScene(
            sceneIndex = index + 1,
            visualPrompt = bp.visualIdea,
            narration = bp.narrationPlaceholder,
            durationSec = bp.durationSec
        )
    }
}

object VideoSceneTemplateOptions {
    val visualStyles: List<String> = listOf(
        "Cinematic Realism",
        "Noir Thriller",
        "Anime 4K",
        "Documentary",
        "Sci-Fi Epic",
        "Minimalist Studio"
    )

    val voiceoverVoices: List<String> = listOf(
        "English Male",
        "English Female",
        "Hindi Male",
        "Hindi Female"
    )

    val aspectRatios: List<String> = listOf("9:16", "16:9")
}

object VideoSceneTemplateValidator {
    fun validate(template: VideoSceneTemplate): String? {
        if (template.name.isBlank()) {
            return "Template name is required."
        }
        if (template.recommendedStyle.isBlank()) {
            return "Select a default visual style."
        }
        if (template.recommendedVoice.isBlank()) {
            return "Select a voiceover voice."
        }
        if (template.scenes.isEmpty()) {
            return "Add at least one scene to the template structure."
        }
        template.scenes.forEachIndexed { idx, scene ->
            if (scene.beatName.isBlank()) {
                return "Scene ${idx + 1} needs a beat title."
            }
            if (scene.visualIdea.isBlank()) {
                return "Scene ${idx + 1} needs a placeholder visual description."
            }
            if (scene.narrationPlaceholder.isBlank()) {
                return "Scene ${idx + 1} needs placeholder narration text."
            }
        }
        return null
    }
}

object VideoSceneTemplateLibrary {

    val allTemplates: List<VideoSceneTemplate> = listOf(
        VideoSceneTemplate(
            id = "edu_concept_breakdown",
            name = "4-Step Concept Explainer",
            category = VideoTemplateCategory.EDUCATIONAL,
            description = "Hook curiosity, visualize the core mechanism, compare a real-world analogy, and recap the key takeaway.",
            recommendedAspectRatio = "16:9",
            recommendedStyle = "Documentary",
            recommendedVoice = "English Male",
            burnSubtitlesDefault = true,
            scenes = listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Curiosity Hook",
                    visualIdea = "Macro cinematic slow zoom into [CORE SUBJECT], dramatic studio key light on dark slate backdrop, crisp floating light particles, 4K sharpness",
                    narrationPlaceholder = "Most people think [COMMON MYTH], but what actually happens inside [TOPIC] is far more surprising.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "02 · Core Mechanism",
                    visualIdea = "Clean isometric cutaway visualization showing [KEY COMPONENT A] transferring energy into [KEY COMPONENT B], smooth orbital camera movement, scientific precision",
                    narrationPlaceholder = "Here is how it works: when [TRIGGER EVENT] occurs, [MECHANISM] reacts in milliseconds to produce [RESULT].",
                    durationSec = 6
                ),
                TemplateSceneBlueprint(
                    beatName = "03 · Real-World Analogy",
                    visualIdea = "Daylight documentary handheld shot of [EVERYDAY ANALOGY SCENE], natural window lighting, shallow depth of field, warm color grade",
                    narrationPlaceholder = "Think of it like [RELATABLE ANALOGY]—every single step keeps the system balanced without wasting resources.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "04 · Key Takeaway",
                    visualIdea = "Wide symmetrical hero shot of [FINAL APPLICATION] in motion at golden hour, gentle dolly pull-back, cinematic lens clarity",
                    narrationPlaceholder = "Remember this single rule about [TOPIC]: [MEMORABLE ONE-SENTENCE SUMMARY].",
                    durationSec = 5
                )
            )
        ),
        VideoSceneTemplate(
            id = "edu_hindi_micro_lesson",
            name = "60s Hindi/Bilingual Micro-Lesson",
            category = VideoTemplateCategory.EDUCATIONAL,
            description = "Fast-paced vertical explainer tailored for Reels/Shorts with clear Hindi narration and bold burned captions.",
            recommendedAspectRatio = "9:16",
            recommendedStyle = "Cinematic Realism",
            recommendedVoice = "Hindi Male",
            burnSubtitlesDefault = true,
            scenes = listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Bold Question",
                    visualIdea = "Vertical 9:16 dynamic push-in on [SUBJECT] under high-contrast rim lighting, crisp architectural background, modern editorial grade",
                    narrationPlaceholder = "Kya aap jaante hain ki [TOPIC] sirf 30 seconds mein kaise kaam karta hai?",
                    durationSec = 4
                ),
                TemplateSceneBlueprint(
                    beatName = "02 · Visual Demonstration",
                    visualIdea = "Overhead vertical close-up demonstrating [STEP-BY-STEP ACTION], crisp hand/object focus, smooth 24fps motion",
                    narrationPlaceholder = "Pehla step hai [STEP ONE], jisse [IMMEDIATE BENEFIT] turant shuru ho jaata hai.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "03 · Actionable Tip",
                    visualIdea = "Clean futuristic HUD-free studio shot showing the finished outcome of [TOPIC], warm tungsten accent light, vertical framing",
                    narrationPlaceholder = "Agli baar jab aap [SITUATION] face karein, toh yeh [PRO TIP] zaroor apply karein.",
                    durationSec = 5
                )
            )
        ),
        VideoSceneTemplate(
            id = "promo_product_launch",
            name = "High-Impact Product Launch",
            category = VideoTemplateCategory.PROMOTIONAL,
            description = "Problem-agitation-reveal structure engineered for product ads, SaaS trailers, and hardware launches.",
            recommendedAspectRatio = "9:16",
            recommendedStyle = "Cinematic Realism",
            recommendedVoice = "English Female",
            burnSubtitlesDefault = true,
            scenes = listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Pain Point",
                    visualIdea = "Moody desaturated close-up of a cluttered desk and frustrated user struggling with [OLD PROBLEM], cool overcast lighting, slow handheld drift",
                    narrationPlaceholder = "Still wasting hours fighting [PAIN POINT]? There is a faster way built for [TARGET AUDIENCE].",
                    durationSec = 4
                ),
                TemplateSceneBlueprint(
                    beatName = "02 · Hero Product Reveal",
                    visualIdea = "Sleek commercial studio hero reveal of [PRODUCT NAME] rotating slowly on dark matte obsidian surface, soft specular rim light, razor-sharp macro detail",
                    narrationPlaceholder = "Meet [PRODUCT NAME]—engineered from the ground up to deliver [PRIMARY BENEFIT] in one tap.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "03 · Proof in Action",
                    visualIdea = "Crisp lifestyle tracking shot of [USER PERSONA] effortlessly completing [CORE WORKFLOW] with [PRODUCT NAME], sunlit modern workspace",
                    narrationPlaceholder = "From [FEATURE ONE] to [FEATURE TWO], experience [MEASURABLE OUTCOME] without compromise.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "04 · Call to Action",
                    visualIdea = "Minimalist centered hero composition of [PRODUCT NAME] with clean negative space at center for captions, subtle volumetric sweep",
                    narrationPlaceholder = "Upgrade your workflow today. Try [PRODUCT NAME] at [WEBSITE OR LINK] and unlock [OFFER].",
                    durationSec = 4
                )
            )
        ),
        VideoSceneTemplate(
            id = "story_cinematic_arc",
            name = "5-Act Cinematic Storytelling Arc",
            category = VideoTemplateCategory.STORYTELLING,
            description = "Classic dramatic narrative progression: atmospheric world-building, inciting spark, tension peak, transformation, and resonant ending.",
            recommendedAspectRatio = "16:9",
            recommendedStyle = "Noir Thriller",
            recommendedVoice = "English Male",
            burnSubtitlesDefault = false,
            scenes = listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Establishing World",
                    visualIdea = "Sweeping aerial drone shot over [SETTING LOCATION] at dusk, mist drifting across rooftops, 35mm anamorphic lens flare, moody twilight palette",
                    narrationPlaceholder = "Every legend begins in a quiet place nobody expects—[SETTING DESCRIPTION].",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "02 · Inciting Moment",
                    visualIdea = "Intimate medium close-up of [PROTAGONIST] discovering [INCITING ARTIFACT OR SIGNAL], flickering ambient light reflecting in their eyes",
                    narrationPlaceholder = "On the night [INCITING EVENT] happened, everything [PROTAGONIST] believed changed forever.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "03 · Rising Tension",
                    visualIdea = "Fast dolly tracking shot moving through [CONFLICT ENVIRONMENT], rain streaks and dramatic chiaroscuro shadows, heightened cinematic pace",
                    narrationPlaceholder = "With [OBSTACLE OR RIVAL] closing in, turning back was no longer an option.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "04 · Turning Point",
                    visualIdea = "Low-angle heroic wide shot as [PROTAGONIST] executes [DECISIVE ACTION], warm golden light breaking through storm clouds, slow-motion drama",
                    narrationPlaceholder = "In that decisive second, [KEY REALIZATION OR CHOICE] rewrote the outcome.",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "05 · Resonant Resolution",
                    visualIdea = "Calm dawn wide-angle shot of [PROTAGONIST] overlooking [TRANSFORMED WORLD], gentle wind motion, serene orchestral atmosphere",
                    narrationPlaceholder = "Because in the end, [CLOSING MORAL OR THEME] outlives every storm.",
                    durationSec = 5
                )
            )
        ),
        VideoSceneTemplate(
            id = "doc_mini_profile",
            name = "Mini Documentary Case Study",
            category = VideoTemplateCategory.DOCUMENTARY,
            description = "Editorial documentary flow highlighting a real-world breakthrough, technical challenge, and future impact.",
            recommendedAspectRatio = "16:9",
            recommendedStyle = "Documentary",
            recommendedVoice = "English Female",
            burnSubtitlesDefault = true,
            scenes = listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Cold Open",
                    visualIdea = "High-contrast documentary wide shot of [LOCATION OR LAB] with subtle camera pan, authentic ambient texture, Leica 35mm film grain",
                    narrationPlaceholder = "Inside [LOCATION], a small team set out to solve a problem experts called impossible: [CHALLENGE].",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "02 · The Bottleneck",
                    visualIdea = "Tight detail shots of [TECHNICAL HARDWARE OR FIELDWORK], natural shadows, unpolished authentic engineering environment",
                    narrationPlaceholder = "For years, [LEGACY LIMITATION] forced builders to choose between [TRADEOFF A] and [TRADEOFF B].",
                    durationSec = 5
                ),
                TemplateSceneBlueprint(
                    beatName = "03 · Breakthrough & Future",
                    visualIdea = "Sunlit architectural wide shot of [NEW SYSTEM IN REAL USE], smooth steadicam glide forward, inspiring natural palette",
                    narrationPlaceholder = "By rethinking [CORE INNOVATION], [PROJECT NAME] opened a new chapter for [FUTURE IMPACT].",
                    durationSec = 5
                )
            )
        )
    )
}
