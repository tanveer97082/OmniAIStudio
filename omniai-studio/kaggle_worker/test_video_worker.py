import unittest

from video_worker import normalize_dialogue_lines


class DialogueNormalizationTests(unittest.TestCase):
    def test_keeps_supported_character_voices_and_cleans_names(self):
        lines = normalize_dialogue_lines(
            [
                {
                    "character_name": "  Maya\nSingh  ",
                    "voice": "Hindi Female",
                    "text": "Namaste.",
                }
            ],
            default_voice="English Male",
        )

        self.assertEqual(
            lines,
            [
                {
                    "character_name": "Maya Singh",
                    "voice": "Hindi Female",
                    "text": "Namaste.",
                }
            ],
        )

    def test_uses_default_voice_and_ignores_empty_or_invalid_lines(self):
        lines = normalize_dialogue_lines(
            [
                {"character_name": "", "voice": "unsupported", "text": "  Hello.  "},
                {"character_name": "Empty", "text": "  "},
                "not a line",
            ],
            default_voice="English Female",
        )

        self.assertEqual(
            lines,
            [
                {
                    "character_name": "Character",
                    "voice": "English Female",
                    "text": "Hello.",
                }
            ],
        )


if __name__ == "__main__":
    unittest.main()
