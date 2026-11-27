#!/usr/bin/env python3
"""
OmniAI-Studio v5.3 Dual-GPU Kaggle Video Rendering Worker
=========================================================
Reads `/kaggle/input/video-tasks-input/scenes.json`, generates Edge-TTS voiceovers
and synchronized `.srt` subtitles per scene, auto-detects dual GPUs (T4 x2) and
spawns isolated CUDA subprocesses running `Wan-AI/Wan2.1-T2V-1.3B-Diffusers`
(bfloat16 UNet/Transformer + float32 VAE) in parallel, muxes individual
`/kaggle/working/scene_X.mp4` clips with optional burned yellow/white bottom-center
subtitles, subtle bottom-right `watermark_text` drawtext overlay, and optional
`bgm_url` ducked at 15% volume via FFmpeg `sidechaincompress`, concatenates
`/kaggle/working/final_video.mp4`, and supports:
- v5.3 Direct Kaggle-to-YouTube chunked resumable upload (`google-api-python-client`
  + `google-auth`, reading `YT_CLIENT_ID`, `YT_CLIENT_SECRET`, `YT_REFRESH_TOKEN`
  from Kaggle Secrets) with custom thumbnail & first comment.
- v5.3 Optional Meta Graph API Facebook Page Reels (`rupload.facebook.com`) &
  Instagram Reels upload (`META_PAGE_ID`, `META_IG_USER_ID`, `META_PAGE_ACCESS_TOKEN`).
- v5.2 Direct Kaggle-to-Telegram 2 GB MTProto upload (including the live YouTube URL
  inside the completion alert) with automatic HTTP Bot API / 45 MB FFmpeg split fallback.
"""

import argparse
import asyncio
import concurrent.futures
import json
import math
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any, Coroutine, Dict, List, Optional, Tuple, TypeVar

INPUT_JSON_PATH = Path("/kaggle/input/video-tasks-input/scenes.json")
REF_IMAGE_PATH = Path("/kaggle/input/video-tasks-input/ref_image.jpg")
WORK_DIR = Path("/kaggle/working")
TEMP_DIR = WORK_DIR / "_tmp_render"
MODEL_ID = "Wan-AI/Wan2.1-T2V-1.3B-Diffusers"
I2V_MODEL_ID = "Wan-AI/Wan2.1-I2V-14B-480P-Diffusers"
HTTP_BOT_MAX_BYTES = 49 * 1024 * 1024  # 49 MB safety threshold for HTTP Bot API

VOICE_MAP = {
    "Hindi Male": "hi-IN-MadhurNeural",
    "Hindi Female": "hi-IN-SwaraNeural",
    "English Male": "en-US-AndrewNeural",
    "English Female": "en-US-AvaNeural",
}
SUPPORTED_DIALOGUE_VOICES = set(VOICE_MAP) | set(VOICE_MAP.values())

T = TypeVar("T")


def run_async(coro: Coroutine[Any, Any, T]) -> T:
    """
    Safely execute an asyncio coroutine in both standalone CLI scripts and
    Kaggle/Jupyter notebooks that already own a running event loop.
    """
    try:
        asyncio.get_running_loop()
    except RuntimeError:
        return asyncio.run(coro)

    try:
        import nest_asyncio  # type: ignore

        nest_asyncio.apply()
        loop = asyncio.get_event_loop()
        return loop.run_until_complete(coro)
    except Exception:
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            return pool.submit(asyncio.run, coro).result()


def mask_secrets(text: str, secrets: List[str]) -> str:
    """Redact any configured secret values before printing exception logs."""
    sanitized = str(text)
    for sec in secrets:
        cleaned = (sec or "").strip()
        if len(cleaned) >= 4:
            sanitized = sanitized.replace(cleaned, "[REDACTED]")
    return sanitized


def load_kaggle_secret(secret_name: str) -> str:
    """
    Read a secret securely from Kaggle Notebook Add-ons -> Secrets via
    `kaggle_secrets.UserSecretsClient()`, falling back to environment variables.
    Never logs secret values.
    """
    try:
        from kaggle_secrets import UserSecretsClient  # type: ignore

        val = UserSecretsClient().get_secret(secret_name)
        if val is not None and str(val).strip():
            return str(val).strip()
    except Exception:
        pass
    return os.environ.get(secret_name, "").strip()


def ensure_dependencies() -> None:
    """Install required runtime packages if missing in the Kaggle container."""
    required = [
        ("edge_tts", "edge-tts==6.1.12"),
        ("diffusers", "git+https://github.com/huggingface/diffusers.git"),
        ("transformers", "transformers>=4.49.0"),
        ("accelerate", "accelerate>=1.2.0"),
        ("imageio", "imageio[ffmpeg]>=2.34.0"),
        ("ftfy", "ftfy>=6.2.0"),
        ("telethon", "telethon>=1.36.0"),
        ("requests", "requests>=2.31.0"),
        ("googleapiclient", "google-api-python-client>=2.120.0"),
        ("google.oauth2", "google-auth>=2.28.0"),
    ]
    missing = []
    for mod, pkg in required:
        try:
            __import__(mod)
        except ImportError:
            missing.append(pkg)
    if missing:
        print(f"[Bootstrap] Installing missing packages: {missing}", flush=True)
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-q", *missing],
            check=True,
        )

    # Install optional C accelerator `cryptg` for fast 2 GB MTProto encryption
    try:
        __import__("cryptg")
    except ImportError:
        try:
            print("[Bootstrap] Installing optional MTProto accelerator: cryptg", flush=True)
            subprocess.run(
                [sys.executable, "-m", "pip", "install", "-q", "cryptg"],
                check=False,
            )
        except Exception:
            pass


def format_srt_timestamp(seconds: float) -> str:
    ms = int(round(max(0.0, seconds) * 1000))
    hours = ms // 3_600_000
    ms %= 3_600_000
    minutes = ms // 60_000
    ms %= 60_000
    secs = ms // 1_000
    millis = ms % 1_000
    return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"


def probe_duration(media_path: Path) -> float:
    cmd = [
        "ffprobe",
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        str(media_path),
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return max(1.0, float(res.stdout.strip()))


def probe_video_metadata(video_path: Path) -> Tuple[int, int, int]:
    """Return (width, height, duration_sec) for Telegram DocumentAttributeVideo."""
    width, height, duration = 480, 832, 5
    try:
        cmd = [
            "ffprobe",
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=width,height:format=duration",
            "-of",
            "json",
            str(video_path),
        ]
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        info = json.loads(res.stdout)
        streams = info.get("streams") or []
        if streams:
            width = int(streams[0].get("width") or width)
            height = int(streams[0].get("height") or height)
        dur_raw = (info.get("format") or {}).get("duration")
        if dur_raw is not None:
            duration = max(1, int(round(float(dur_raw))))
    except Exception:
        try:
            duration = max(1, int(round(probe_duration(video_path))))
        except Exception:
            pass
    return width, height, duration


def extract_video_thumbnail(video_path: Path, thumb_path: Path) -> Optional[Path]:
    """Extract a crisp JPEG thumbnail frame via FFmpeg for Telegram video preview."""
    try:
        cmd = [
            "ffmpeg",
            "-y",
            "-ss",
            "00:00:01",
            "-i",
            str(video_path),
            "-vframes",
            "1",
            "-vf",
            "scale=320:-2",
            "-q:v",
            "3",
            str(thumb_path),
        ]
        subprocess.run(cmd, capture_output=True, check=True)
        if thumb_path.exists() and thumb_path.stat().st_size > 0:
            return thumb_path
    except Exception:
        pass
    # Fallback to frame 0 for ultra-short clips
    try:
        cmd = [
            "ffmpeg",
            "-y",
            "-i",
            str(video_path),
            "-vframes",
            "1",
            "-vf",
            "scale=320:-2",
            "-q:v",
            "3",
            str(thumb_path),
        ]
        subprocess.run(cmd, capture_output=True, check=True)
        if thumb_path.exists() and thumb_path.stat().st_size > 0:
            return thumb_path
    except Exception:
        pass
    return None


def download_bgm_if_provided(bgm_url: str, target_dir: Path) -> Optional[Path]:
    """Download optional background music track from HTTPS URL for FFmpeg ducking."""
    cleaned = (bgm_url or "").strip()
    if not cleaned.startswith("https://") and not cleaned.startswith("http://"):
        return None
    dest = target_dir / "bgm_track.audio"
    try:
        print(f"[BGM] Downloading background music from {cleaned}...", flush=True)
        req = urllib.request.Request(cleaned, headers={"User-Agent": "OmniAI-Studio-Worker/5.2"})
        with urllib.request.urlopen(req, timeout=30) as resp, open(dest, "wb") as out:
            shutil.copyfileobj(resp, out)
        if dest.exists() and dest.stat().st_size > 1024:
            print(f"[BGM] Downloaded {dest.stat().st_size / 1024:.1f} KB", flush=True)
            return dest
    except Exception as exc:
        print(f"[BGM] Warning: failed to fetch bgm_url ({exc}); continuing without BGM.", flush=True)
    return None


def escape_drawtext(raw: str) -> str:
    return (
        raw.replace("\\", "\\\\")
        .replace(":", "\\:")
        .replace("'", "\\'")
        .replace("%", "\\%")
    )


def format_tts_rate(speed_factor: float) -> str:
    clamped = max(0.75, min(1.5, float(speed_factor)))
    pct = int(round((clamped - 1.0) * 100))
    return f"+{pct}%" if pct >= 0 else f"{pct}%"


async def synthesize_voice_and_srt(
    text: str,
    voice_name: str,
    audio_out: Path,
    srt_out: Path,
    speed_factor: float = 1.0,
) -> float:
    """Generate MP3 voiceover via edge-tts and build a timed SRT file from word boundaries."""
    import edge_tts

    resolved_voice = VOICE_MAP.get(voice_name, voice_name or "en-US-AndrewNeural")
    clean_text = text.strip() or "Cinematic visual sequence."
    rate_str = format_tts_rate(speed_factor)
    communicate = edge_tts.Communicate(clean_text, resolved_voice, rate=rate_str)

    word_events: List[Tuple[float, float, str]] = []
    with open(audio_out, "wb") as audio_file:
        async for chunk in communicate.stream():
            if chunk["type"] == "audio":
                audio_file.write(chunk["data"])
            elif chunk["type"] == "WordBoundary":
                start_sec = chunk["offset"] / 10_000_000.0
                dur_sec = chunk["duration"] / 10_000_000.0
                word_events.append((start_sec, start_sec + dur_sec, chunk["text"]))

    total_duration = probe_duration(audio_out)

    # Group word boundaries into punchy 5-word subtitle captions
    srt_lines: List[str] = []
    if word_events:
        chunk_size = 5
        idx = 1
        for i in range(0, len(word_events), chunk_size):
            group = word_events[i : i + chunk_size]
            t_start = group[0][0]
            t_end = max(group[-1][1], t_start + 0.35)
            caption = " ".join(w[2] for w in group).strip()
            srt_lines.append(
                f"{idx}\n{format_srt_timestamp(t_start)} --> {format_srt_timestamp(t_end)}\n{caption}\n"
            )
            idx += 1
    else:
        srt_lines.append(
            f"1\n{format_srt_timestamp(0.0)} --> {format_srt_timestamp(total_duration)}\n{clean_text}\n"
        )

    srt_out.write_text("\n".join(srt_lines), encoding="utf-8")
    return total_duration


def normalize_dialogue_lines(dialogue: Any, default_voice: str) -> List[Dict[str, str]]:
    if not isinstance(dialogue, list):
        return []
    normalized: List[Dict[str, str]] = []
    for line in dialogue:
        if not isinstance(line, dict):
            continue
        text = str(line.get("text") or "").strip()
        if not text:
            continue
        voice = str(line.get("voice") or default_voice).strip()
        if voice not in SUPPORTED_DIALOGUE_VOICES:
            voice = default_voice
        normalized.append(
            {
                "character_name": " ".join(
                    str(line.get("character_name") or "Character").split()
                )[:80] or "Character",
                "voice": voice,
                "text": text,
            }
        )
    return normalized


def parse_srt_timestamp(timestamp: str) -> float:
    hours, minutes, seconds = timestamp.replace(",", ".").split(":")
    return int(hours) * 3600 + int(minutes) * 60 + float(seconds)


async def synthesize_dialogue_and_srt(
    dialogue: List[Dict[str, str]],
    audio_out: Path,
    srt_out: Path,
    speed_factor: float = 1.0,
) -> float:
    """Render each speaker separately, then concatenate audio and offset captions."""
    audio_parts: List[Path] = []
    subtitle_blocks: List[str] = []
    time_offset = 0.0
    subtitle_index = 1

    for line_index, line in enumerate(dialogue, start=1):
        part_audio = audio_out.with_name(f"{audio_out.stem}_speaker_{line_index}.mp3")
        part_srt = srt_out.with_name(f"{srt_out.stem}_speaker_{line_index}.srt")
        duration = await synthesize_voice_and_srt(
            text=line["text"],
            voice_name=line["voice"],
            audio_out=part_audio,
            srt_out=part_srt,
            speed_factor=speed_factor,
        )
        audio_parts.append(part_audio)

        blocks = part_srt.read_text(encoding="utf-8").strip().split("\n\n")
        for block in blocks:
            rows = block.splitlines()
            if len(rows) < 3 or " --> " not in rows[1]:
                continue
            start_text, end_text = rows[1].split(" --> ", 1)
            start = format_srt_timestamp(parse_srt_timestamp(start_text) + time_offset)
            end = format_srt_timestamp(parse_srt_timestamp(end_text) + time_offset)
            caption = " ".join(rows[2:]).strip()
            subtitle_blocks.append(
                f"{subtitle_index}\n{start} --> {end}\n{line['character_name']}: {caption}\n"
            )
            subtitle_index += 1
        time_offset += duration

    concat_list = audio_out.with_name(f"{audio_out.stem}_concat.txt")
    concat_list.write_text(
        "".join(f"file '{part.as_posix()}'\n" for part in audio_parts),
        encoding="utf-8",
    )
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_list),
            "-c:a",
            "libmp3lame",
            "-q:a",
            "4",
            str(audio_out),
        ],
        capture_output=True,
        check=True,
    )
    srt_out.write_text("\n".join(subtitle_blocks), encoding="utf-8")
    return probe_duration(audio_out)


def resolve_dimensions(aspect_ratio: str) -> Tuple[int, int]:
    """Wan 2.1 1.3B native dimensions (multiples of 16)."""
    if aspect_ratio.strip() == "9:16":
        return (480, 832)
    return (832, 480)


def render_worker_shard(
    gpu_index: int,
    shard_path: Path,
    aspect_ratio: str,
    custom_negative_prompt: str = "",
    use_ref_image: bool = False,
) -> None:
    """Executed inside an isolated subprocess bound to CUDA_VISIBLE_DEVICES=<gpu_index>."""
    import torch
    from diffusers import AutoencoderKLWan, WanPipeline
    from diffusers.utils import export_to_video, load_image

    shard_items: List[Dict[str, Any]] = json.loads(shard_path.read_text(encoding="utf-8"))
    if not shard_items:
        print(f"[GPU {gpu_index}] No scenes assigned to this shard. Exiting.", flush=True)
        return

    width, height = resolve_dimensions(aspect_ratio)
    ref_img = None
    if use_ref_image and REF_IMAGE_PATH.exists():
        try:
            ref_img = load_image(str(REF_IMAGE_PATH)).resize((width, height))
            print(
                f"[GPU {gpu_index}] Loaded optional reference frame from {REF_IMAGE_PATH} ({width}x{height}).",
                flush=True,
            )
        except Exception as exc:
            print(f"[GPU {gpu_index}] Warning: could not load ref_image.jpg ({exc}); falling back to T2V.", flush=True)

    print(
        f"[GPU {gpu_index}] Loading {MODEL_ID} (bfloat16 transformer + float32 VAE) for {len(shard_items)} scenes ({width}x{height})...",
        flush=True,
    )

    vae = AutoencoderKLWan.from_pretrained(
        MODEL_ID,
        subfolder="vae",
        torch_dtype=torch.float32,
    )
    pipe = WanPipeline.from_pretrained(
        MODEL_ID,
        vae=vae,
        torch_dtype=torch.bfloat16,
    )
    pipe.enable_model_cpu_offload(gpu_id=0)
    if hasattr(pipe.vae, "enable_tiling"):
        pipe.vae.enable_tiling()

    base_negative = (
        "blurry, distorted anatomy, watermark, text overlay, low quality, "
        "flickering artifacts, overexposed, jpeg compression"
    )
    negative_prompt = (
        f"{custom_negative_prompt.strip()}, {base_negative}"
        if custom_negative_prompt.strip()
        else base_negative
    )

    for item in shard_items:
        scene_idx = int(item["scene_index"])
        visual_prompt = item["visual_prompt"]
        if ref_img is not None:
            visual_prompt = f"Consistent with reference character and lighting frame, {visual_prompt}"
        duration_sec = float(item.get("duration_sec", 5.0))
        raw_out = Path(item["raw_video_path"])

        # Wan 2.1 uses 4k + 1 frame counts at 16 fps; clamp to T4 16GB safe range (33..81 frames)
        target_frames = int(min(81, max(33, math.floor(duration_sec * 16))))
        num_frames = ((target_frames - 1) // 4) * 4 + 1

        print(
            f"[GPU {gpu_index}] Rendering scene_{scene_idx} ({num_frames} frames, ref_image={ref_img is not None}): {visual_prompt[:90]}...",
            flush=True,
        )
        t0 = time.time()
        generator = torch.Generator(device="cuda:0").manual_seed(1000 + scene_idx * 37)
        pipe_kwargs: Dict[str, Any] = {
            "prompt": visual_prompt,
            "negative_prompt": negative_prompt,
            "height": height,
            "width": width,
            "num_frames": num_frames,
            "num_inference_steps": 30,
            "guidance_scale": 5.0,
            "generator": generator,
        }
        output = pipe(**pipe_kwargs)
        frames = output.frames[0]
        if ref_img is not None and len(frames) > 0:
            try:
                from PIL import Image

                first = frames[0]
                if isinstance(first, Image.Image):
                    frames[0] = Image.blend(first.convert("RGB"), ref_img.convert("RGB"), 0.15)
            except Exception:
                pass
        export_to_video(frames, str(raw_out), fps=16)
        torch.cuda.empty_cache()
        print(
            f"[GPU {gpu_index}] Completed scene_{scene_idx} in {time.time() - t0:.1f}s -> {raw_out}",
            flush=True,
        )


def mux_scene_with_audio_and_subtitles(
    raw_video: Path,
    audio_path: Path,
    srt_path: Path,
    output_mp4: Path,
    burn_subtitles: bool,
    width: int,
    height: int,
    watermark_text: str = "",
    bgm_path: Optional[Path] = None,
    speed_factor: float = 1.0,
) -> None:
    """
    Loop or trim video to match voiceover duration, apply optional speed_factor pacing,
    optionally burn styled SRT subtitles, apply optional subtle bottom-right FFmpeg
    drawtext watermark, and duck optional BGM at 15% volume under Edge-TTS narration.
    """
    audio_dur = probe_duration(audio_path)
    target_dur = f"{audio_dur + 0.25:.3f}"
    clamped_speed = max(0.75, min(1.5, float(speed_factor)))

    vf_filters = [
        f"setpts=PTS/{clamped_speed:.3f}",
        f"scale={width}:{height}:force_original_aspect_ratio=decrease",
        f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:black",
        "fps=24",
    ]
    if burn_subtitles and srt_path.exists():
        escaped_srt = str(srt_path).replace("\\", "/").replace(":", "\\:")
        style = (
            "FontName=DejaVu Sans,FontSize=20,Bold=1,"
            "PrimaryColour=&H0000FFFF,SecondaryColour=&H00FFFFFF,"
            "OutlineColour=&H00000000,BackColour=&H80000000,"
            "BorderStyle=1,Outline=2,Shadow=1,Alignment=2,MarginV=34"
        )
        vf_filters.append(f"subtitles='{escaped_srt}':force_style='{style}'")

    wm = (watermark_text or "").strip()
    if wm:
        escaped_wm = escape_drawtext(wm)
        vf_filters.append(
            f"drawtext=text='{escaped_wm}':fontcolor=white@0.55:fontsize=16:"
            "shadowcolor=black@0.65:shadowx=1:shadowy=1:x=w-tw-16:y=h-th-14"
        )

    vf_chain = ",".join(vf_filters)

    if bgm_path is not None and bgm_path.exists():
        filter_complex = (
            f"[0:v]{vf_chain}[vout];"
            "[1:a]asplit=2[voice_sc][voice_mix];"
            "[2:a]volume=0.15[bgm_quiet];"
            "[bgm_quiet][voice_sc]sidechaincompress=threshold=0.02:ratio=6:attack=20:release=250[bgm_ducked];"
            "[voice_mix][bgm_ducked]amix=inputs=2:duration=first:dropout_transition=2[aout]"
        )
        cmd = [
            "ffmpeg",
            "-y",
            "-stream_loop",
            "-1",
            "-i",
            str(raw_video),
            "-i",
            str(audio_path),
            "-stream_loop",
            "-1",
            "-i",
            str(bgm_path),
            "-t",
            target_dur,
            "-filter_complex",
            filter_complex,
            "-map",
            "[vout]",
            "-map",
            "[aout]",
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "19",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-ar",
            "44100",
            "-ac",
            "2",
            "-movflags",
            "+faststart",
            str(output_mp4),
        ]
    else:
        cmd = [
            "ffmpeg",
            "-y",
            "-stream_loop",
            "-1",
            "-i",
            str(raw_video),
            "-i",
            str(audio_path),
            "-t",
            target_dur,
            "-vf",
            vf_chain,
            "-c:v",
            "libx264",
            "-preset",
            "medium",
            "-crf",
            "19",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-b:a",
            "192k",
            "-ar",
            "44100",
            "-ac",
            "2",
            "-movflags",
            "+faststart",
            str(output_mp4),
        ]

    subprocess.run(cmd, check=True)


def concatenate_scenes(
    scene_paths: List[Path],
    final_output: Path,
    seamless_loop: bool = False,
) -> None:
    """
    Concatenate all normalized scene MP4s into final_video.mp4.
    When seamless_loop is True (Reels/Shorts infinite loop mode), applies a tight
    0.25s audio/video tail trim + micro audio fade so loop wraparound plays invisibly.
    """
    concat_file = TEMP_DIR / "concat_list.txt"
    entries = [f"file '{p.resolve().as_posix()}'" for p in scene_paths]
    concat_file.write_text("\n".join(entries) + "\n", encoding="utf-8")

    if not seamless_loop:
        cmd = [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_file),
            "-c",
            "copy",
            "-movflags",
            "+faststart",
            str(final_output),
        ]
        subprocess.run(cmd, check=True)
        return

    raw_concat = TEMP_DIR / "concat_pre_loop.mp4"
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_file),
            "-c",
            "copy",
            str(raw_concat),
        ],
        check=True,
    )
    total_dur = probe_duration(raw_concat)
    trimmed_dur = max(1.0, total_dur - 0.25)
    fade_out_start = max(0.0, trimmed_dur - 0.04)
    print(
        f"[SeamlessLoop] Trimming 0.25s tail ({total_dur:.2f}s -> {trimmed_dur:.2f}s) for invisible Reels/Shorts loop...",
        flush=True,
    )
    cmd = [
        "ffmpeg",
        "-y",
        "-i",
        str(raw_concat),
        "-t",
        f"{trimmed_dur:.3f}",
        "-af",
        f"afade=t=in:st=0:d=0.03,afade=t=out:st={fade_out_start:.3f}:d=0.04",
        "-c:v",
        "libx264",
        "-preset",
        "medium",
        "-crf",
        "19",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-b:a",
        "192k",
        "-movflags",
        "+faststart",
        str(final_output),
    ]
    subprocess.run(cmd, check=True)


# =========================================================================
# v5.2 DIRECT KAGGLE -> TELEGRAM 2 GB MTPROTO UPLOADER + HTTP FALLBACK
# =========================================================================

def parse_telegram_chat_target(chat_id_raw: str) -> Any:
    """Convert numeric chat/channel IDs to int for Telethon, keeping @usernames as str."""
    cleaned = chat_id_raw.strip()
    if cleaned.lstrip("-").isdigit():
        return int(cleaned)
    return cleaned


async def upload_videos_via_mtproto(
    api_id: int,
    api_hash: str,
    bot_token: str,
    chat_id_raw: str,
    final_video: Path,
    caption: str,
    scene_paths: List[Path],
    upload_individual_scenes: bool,
) -> str:
    """
    Headless MTProto Bot upload via Telethon (supports up to 2 GB per file,
    NO phone OTP required). Extracts duration/width/height/thumb for native
    streaming playback inside Telegram.
    """
    from telethon import TelegramClient
    from telethon.tl.types import DocumentAttributeVideo

    session_prefix = str(WORK_DIR / "tg_bot")
    target = parse_telegram_chat_target(chat_id_raw)
    client = TelegramClient(session_prefix, api_id, api_hash)
    await client.start(bot_token=bot_token)
    try:
        w, h, dur = probe_video_metadata(final_video)
        thumb_path = extract_video_thumbnail(final_video, TEMP_DIR / "final_thumb.jpg")
        size_mb = final_video.stat().st_size / (1024 * 1024)
        print(
            f"[Telegram MTProto] Uploading {final_video.name} ({size_mb:.1f} MB, {w}x{h}, {dur}s) directly to Telegram (2 GB limit)...",
            flush=True,
        )
        await client.send_file(
            entity=target,
            file=str(final_video),
            caption=caption[:1024],
            supports_streaming=True,
            thumb=str(thumb_path) if thumb_path else None,
            attributes=[
                DocumentAttributeVideo(
                    duration=dur,
                    w=w,
                    h=h,
                    supports_streaming=True,
                )
            ],
        )
        uploaded_count = 1
        if upload_individual_scenes:
            for idx, scene_mp4 in enumerate(scene_paths, start=1):
                if not scene_mp4.exists():
                    continue
                sw, sh, sdur = probe_video_metadata(scene_mp4)
                sthumb = extract_video_thumbnail(scene_mp4, TEMP_DIR / f"scene_{idx}_thumb.jpg")
                await client.send_file(
                    entity=target,
                    file=str(scene_mp4),
                    caption=f"Scene {idx}/{len(scene_paths)} · {scene_mp4.name}",
                    supports_streaming=True,
                    thumb=str(sthumb) if sthumb else None,
                    attributes=[
                        DocumentAttributeVideo(
                            duration=sdur,
                            w=sw,
                            h=sh,
                            supports_streaming=True,
                        )
                    ],
                )
                uploaded_count += 1
        print(
            f"[Telegram MTProto] Direct upload complete ({uploaded_count} video file(s) delivered).",
            flush=True,
        )
        return f"MTPROTO_2GB_OK ({uploaded_count} files)"
    finally:
        await client.disconnect()
        # Clean up ephemeral session files so they are never exposed in Kaggle Output
        for sfile in WORK_DIR.glob("tg_bot.session*"):
            try:
                sfile.unlink()
            except Exception:
                pass


def split_video_for_http_fallback(video_path: Path, parts_dir: Path) -> List[Path]:
    """
    Split videos larger than 49 MB into <=45 MB numbered MP4 parts using
    stream-copy (`ffmpeg -fs 45M -c copy`) so zero renders are lost when
    TG_API_ID / TG_API_HASH are not configured.
    """
    parts_dir.mkdir(parents=True, exist_ok=True)
    total_dur = probe_duration(video_path)
    parts: List[Path] = []
    offset_sec = 0.0
    part_idx = 1

    while offset_sec < total_dur - 0.35 and part_idx <= 50:
        out_part = parts_dir / f"{video_path.stem}_part{part_idx}.mp4"
        cmd = [
            "ffmpeg",
            "-y",
            "-ss",
            f"{offset_sec:.3f}",
            "-i",
            str(video_path),
            "-fs",
            "45M",
            "-c",
            "copy",
            "-movflags",
            "+faststart",
            str(out_part),
        ]
        subprocess.run(cmd, capture_output=True, check=True)
        if not out_part.exists() or out_part.stat().st_size < 1024:
            break
        part_dur = probe_duration(out_part)
        parts.append(out_part)
        if part_dur <= 0.25:
            break
        offset_sec += part_dur
        part_idx += 1

    return parts


def upload_single_video_http(
    bot_token: str,
    chat_id: str,
    video_path: Path,
    caption: str,
    secrets_to_mask: List[str],
) -> None:
    import requests

    url = f"https://api.telegram.org/bot{bot_token}/sendVideo"
    with open(video_path, "rb") as fh:
        resp = requests.post(
            url,
            data={
                "chat_id": chat_id,
                "caption": caption[:1024],
                "supports_streaming": "true",
            },
            files={"video": (video_path.name, fh, "video/mp4")},
            timeout=180,
        )
    if not resp.ok:
        raise RuntimeError(
            mask_secrets(
                f"HTTP Bot API sendVideo failed (HTTP {resp.status_code}): {resp.text[:180]}",
                secrets_to_mask,
            )
        )


def upload_videos_via_http_fallback(
    bot_token: str,
    chat_id: str,
    final_video: Path,
    caption: str,
    scene_paths: List[Path],
    upload_individual_scenes: bool,
    secrets_to_mask: List[str],
) -> str:
    """Automatic fallback when TG_API_ID / TG_API_HASH are omitted."""
    file_size = final_video.stat().st_size
    if file_size <= HTTP_BOT_MAX_BYTES:
        print(
            f"[Telegram HTTP Fallback] Uploading {final_video.name} ({file_size / (1024 * 1024):.1f} MB <= 49 MB)...",
            flush=True,
        )
        upload_single_video_http(bot_token, chat_id, final_video, caption, secrets_to_mask)
        status = "HTTP_BOT_SINGLE_OK"
    else:
        print(
            f"[Telegram HTTP Fallback] {final_video.name} is {file_size / (1024 * 1024):.1f} MB (>49 MB) and TG_API_ID/TG_API_HASH not set. Splitting via ffmpeg -fs 45M -c copy...",
            flush=True,
        )
        parts = split_video_for_http_fallback(final_video, TEMP_DIR / "tg_split_parts")
        total_parts = len(parts)
        for idx, part_file in enumerate(parts, start=1):
            part_caption = f"Part {idx}/{total_parts} · {caption}"
            print(f"[Telegram HTTP Fallback] Sending Part {idx}/{total_parts} ({part_file.name})...", flush=True)
            upload_single_video_http(bot_token, chat_id, part_file, part_caption, secrets_to_mask)
        status = f"HTTP_BOT_SPLIT_OK ({total_parts} parts)"

    if upload_individual_scenes:
        for idx, scene_mp4 in enumerate(scene_paths, start=1):
            if scene_mp4.exists() and scene_mp4.stat().st_size <= HTTP_BOT_MAX_BYTES:
                upload_single_video_http(
                    bot_token,
                    chat_id,
                    scene_mp4,
                    f"Scene {idx}/{len(scene_paths)} · {scene_mp4.name}",
                    secrets_to_mask,
                )
    return status


def perform_direct_telegram_upload(
    final_video: Path,
    scene_paths: List[Path],
    caption: str,
    upload_individual_scenes: bool,
) -> str:
    """
    Orchestrate Direct Kaggle -> Telegram upload using Kaggle Secrets:
    1) Primary Mode: Telethon MTProto (up to 2 GB) when TG_API_ID + TG_API_HASH + TG_BOT_TOKEN + TG_CHAT_ID are set.
    2) Automatic Fallback: HTTP Bot API (<=49 MB) or ffmpeg -fs 45M -c copy split parts (>49 MB).
    Never prints secrets and never crashes the Kaggle Output preservation step.
    """
    bot_token = load_kaggle_secret("TG_BOT_TOKEN")
    chat_id = load_kaggle_secret("TG_CHAT_ID")
    api_id_raw = load_kaggle_secret("TG_API_ID")
    api_hash = load_kaggle_secret("TG_API_HASH")
    secrets_to_mask = [bot_token, chat_id, api_id_raw, api_hash]

    if not bot_token or not chat_id:
        print(
            "[Telegram] direct_telegram_upload=true, but TG_BOT_TOKEN or TG_CHAT_ID secret is missing in Kaggle Secrets. Skipping Telegram upload (final_video.mp4 preserved in Kaggle Output).",
            flush=True,
        )
        return "SKIPPED_MISSING_SECRETS"

    has_mtproto = api_id_raw.isdigit() and bool(api_hash)
    if has_mtproto:
        try:
            return run_async(
                upload_videos_via_mtproto(
                    api_id=int(api_id_raw),
                    api_hash=api_hash,
                    bot_token=bot_token,
                    chat_id_raw=chat_id,
                    final_video=final_video,
                    caption=caption,
                    scene_paths=scene_paths,
                    upload_individual_scenes=upload_individual_scenes,
                )
            )
        except Exception as exc:
            print(
                f"[Telegram MTProto] Warning: MTProto upload encountered an error ({mask_secrets(str(exc), secrets_to_mask)}); attempting automatic HTTP fallback...",
                flush=True,
            )

    try:
        return upload_videos_via_http_fallback(
            bot_token=bot_token,
            chat_id=chat_id,
            final_video=final_video,
            caption=caption,
            scene_paths=scene_paths,
            upload_individual_scenes=upload_individual_scenes,
            secrets_to_mask=secrets_to_mask,
        )
    except Exception as exc:
        print(
            f"[Telegram] Error: direct upload failed ({mask_secrets(str(exc), secrets_to_mask)}). final_video.mp4 remains saved in Kaggle Output.",
            flush=True,
        )
        return "UPLOAD_FAILED_OUTPUT_KEPT"


# =========================================================================
# v5.3 DIRECT KAGGLE -> YOUTUBE DATA API v3 RESUMABLE UPLOADER
# =========================================================================

def perform_direct_youtube_upload(
    final_video: Path,
    thumb_path: Optional[Path],
    youtube_metadata: Dict[str, Any],
    fallback_title: str,
) -> Tuple[str, str]:
    """
    Reconstruct OAuth2 credentials from Kaggle Secrets (`YT_CLIENT_ID`, `YT_CLIENT_SECRET`,
    `YT_REFRESH_TOKEN`), execute a 5 MB chunked resumable upload to YouTube Data API v3,
    attach custom thumbnail `thumb.jpg`, and optionally post the first comment.
    Returns (status_label, watch_url_or_empty). Never logs raw tokens.
    """
    yt_client_id = load_kaggle_secret("YT_CLIENT_ID")
    yt_client_secret = load_kaggle_secret("YT_CLIENT_SECRET")
    yt_refresh_token = load_kaggle_secret("YT_REFRESH_TOKEN")
    secrets_to_mask = [yt_client_id, yt_client_secret, yt_refresh_token]

    if not yt_client_id or not yt_client_secret or not yt_refresh_token:
        print(
            "[YouTube] direct_youtube_upload=true, but YT_CLIENT_ID / YT_CLIENT_SECRET / YT_REFRESH_TOKEN missing in Kaggle Secrets. Skipping YouTube upload.",
            flush=True,
        )
        return ("SKIPPED_MISSING_SECRETS", "")

    try:
        from google.auth.transport.requests import Request as GoogleAuthRequest
        from google.oauth2.credentials import Credentials
        from googleapiclient.discovery import build
        from googleapiclient.http import MediaFileUpload

        creds = Credentials(
            token=None,
            refresh_token=yt_refresh_token,
            token_uri="https://oauth2.googleapis.com/token",
            client_id=yt_client_id,
            client_secret=yt_client_secret,
            scopes=[
                "https://www.googleapis.com/auth/youtube.upload",
                "https://www.googleapis.com/auth/youtube.force-ssl",
            ],
        )
        creds.refresh(GoogleAuthRequest())
        youtube = build("youtube", "v3", credentials=creds, cache_discovery=False)

        meta = youtube_metadata or {}
        title = str(meta.get("title") or fallback_title or "OmniAI Studio Video").strip()[:100]
        description = str(meta.get("description") or "").strip()[:4950]
        raw_tags = meta.get("tags") or []
        tags = [str(t).strip().lstrip("#") for t in raw_tags if str(t).strip()][:30]
        category_id = str(meta.get("category_id") or "22").strip() or "22"
        publish_at = str(meta.get("publish_at") or "").strip()
        privacy = str(meta.get("privacy_status") or "private").strip().lower()
        if publish_at:
            privacy = "private"
        elif privacy not in ("private", "unlisted", "public"):
            privacy = "private"
        first_comment = str(meta.get("first_comment") or "").strip()

        status_body: Dict[str, Any] = {
            "privacyStatus": privacy,
            "selfDeclaredMadeForKids": False,
        }
        if publish_at:
            status_body["publishAt"] = publish_at

        body = {
            "snippet": {
                "title": title,
                "description": description,
                "tags": tags,
                "categoryId": category_id,
            },
            "status": status_body,
        }

        media = MediaFileUpload(
            str(final_video),
            mimetype="video/mp4",
            chunksize=5 * 1024 * 1024,  # 5 MB resumable chunks
            resumable=True,
        )
        print(
            f"[YouTube] Starting chunked resumable upload for '{title}' (privacy={privacy})...",
            flush=True,
        )
        request = youtube.videos().insert(
            part="snippet,status",
            body=body,
            media_body=media,
        )

        response = None
        while response is None:
            status, response = request.next_chunk()
            if status is not None:
                pct = int(status.progress() * 100)
                print(f"[YouTube] Upload progress: {pct}%", flush=True)

        video_id = str((response or {}).get("id") or "").strip()
        if not video_id:
            return ("UPLOAD_FAILED_NO_ID", "")

        watch_url = f"https://youtu.be/{video_id}"
        print(f"[YouTube] Video uploaded successfully: {watch_url}", flush=True)

        if thumb_path is not None and thumb_path.exists() and thumb_path.stat().st_size > 0:
            try:
                youtube.thumbnails().set(
                    videoId=video_id,
                    media_body=MediaFileUpload(str(thumb_path), mimetype="image/jpeg"),
                ).execute()
                print(f"[YouTube] Custom thumbnail attached to {video_id}.", flush=True)
            except Exception as thumb_exc:
                print(
                    f"[YouTube] Warning: thumbnail upload skipped ({mask_secrets(str(thumb_exc), secrets_to_mask)})",
                    flush=True,
                )

        if first_comment:
            try:
                youtube.commentThreads().insert(
                    part="snippet",
                    body={
                        "snippet": {
                            "videoId": video_id,
                            "topLevelComment": {
                                "snippet": {
                                    "textOriginal": first_comment[:2000],
                                }
                            },
                        }
                    },
                ).execute()
                print(f"[YouTube] First comment posted on {video_id}.", flush=True)
            except Exception as cmt_exc:
                print(
                    f"[YouTube] Warning: first comment skipped ({mask_secrets(str(cmt_exc), secrets_to_mask)})",
                    flush=True,
                )

        return (f"YOUTUBE_OK ({video_id})", watch_url)
    except Exception as exc:
        print(
            f"[YouTube] Error: direct YouTube upload failed ({mask_secrets(str(exc), secrets_to_mask)}). final_video.mp4 preserved in Kaggle Output.",
            flush=True,
        )
        return ("YOUTUBE_UPLOAD_FAILED", "")


# =========================================================================
# v5.3 OPTIONAL META GRAPH API REELS UPLOADER (FACEBOOK PAGE & INSTAGRAM)
# =========================================================================

def perform_direct_meta_reels_upload(
    final_video: Path,
    description: str,
    public_video_url: str = "",
) -> str:
    """
    Uploads `final_video.mp4` to Facebook Page Reels via 3-phase Graph API
    (`POST /{page_id}/video_reels` -> `rupload.facebook.com` -> finish) and
    optionally creates/publishes an Instagram Reel container if `public_video_url`
    is provided. Reads `META_PAGE_ID`, `META_IG_USER_ID`, and `META_PAGE_ACCESS_TOKEN`
    from Kaggle Secrets and masks tokens in all logs.
    """
    import requests

    page_id = load_kaggle_secret("META_PAGE_ID")
    ig_user_id = load_kaggle_secret("META_IG_USER_ID")
    access_token = load_kaggle_secret("META_PAGE_ACCESS_TOKEN")
    secrets_to_mask = [access_token]

    if not access_token or (not page_id and not ig_user_id):
        print(
            "[Meta Reels] direct_fb_ig_upload=true, but META_PAGE_ACCESS_TOKEN / META_PAGE_ID missing in Kaggle Secrets. Skipping.",
            flush=True,
        )
        return "SKIPPED_MISSING_SECRETS"

    statuses: List[str] = []
    graph_base = "https://graph.facebook.com/v19.0"

    if page_id:
        try:
            start_resp = requests.post(
                f"{graph_base}/{page_id}/video_reels",
                data={"upload_phase": "start", "access_token": access_token},
                timeout=60,
            )
            start_resp.raise_for_status()
            start_json = start_resp.json()
            fb_video_id = str(start_json.get("video_id") or "")
            upload_url = str(
                start_json.get("upload_url")
                or f"https://rupload.facebook.com/video-upload/v19.0/{fb_video_id}"
            )
            file_size = final_video.stat().st_size
            with open(final_video, "rb") as fh:
                up_resp = requests.post(
                    upload_url,
                    headers={
                        "Authorization": f"OAuth {access_token}",
                        "offset": "0",
                        "file_size": str(file_size),
                    },
                    data=fh,
                    timeout=300,
                )
            up_resp.raise_for_status()

            finish_resp = requests.post(
                f"{graph_base}/{page_id}/video_reels",
                data={
                    "upload_phase": "finish",
                    "video_id": fb_video_id,
                    "video_state": "PUBLISHED",
                    "description": description[:2200],
                    "access_token": access_token,
                },
                timeout=60,
            )
            finish_resp.raise_for_status()
            statuses.append(f"FB_REEL_OK({fb_video_id})")
            print(f"[Meta Reels] Published Facebook Page Reel: {fb_video_id}", flush=True)
        except Exception as exc:
            print(
                f"[Meta Reels] Facebook Page Reel upload failed ({mask_secrets(str(exc), secrets_to_mask)})",
                flush=True,
            )
            statuses.append("FB_REEL_FAILED")

    if ig_user_id and public_video_url.strip():
        try:
            c_resp = requests.post(
                f"{graph_base}/{ig_user_id}/media",
                data={
                    "media_type": "REELS",
                    "video_url": public_video_url.strip(),
                    "caption": description[:2200],
                    "access_token": access_token,
                },
                timeout=60,
            )
            c_resp.raise_for_status()
            creation_id = str(c_resp.json().get("id") or "")
            p_resp = requests.post(
                f"{graph_base}/{ig_user_id}/media_publish",
                data={"creation_id": creation_id, "access_token": access_token},
                timeout=60,
            )
            p_resp.raise_for_status()
            ig_media_id = str(p_resp.json().get("id") or creation_id)
            statuses.append(f"IG_REEL_OK({ig_media_id})")
            print(f"[Meta Reels] Published Instagram Reel: {ig_media_id}", flush=True)
        except Exception as exc:
            print(
                f"[Meta Reels] Instagram Reel upload failed ({mask_secrets(str(exc), secrets_to_mask)})",
                flush=True,
            )
            statuses.append("IG_REEL_FAILED")

    return " | ".join(statuses) if statuses else "SKIPPED"


def run_orchestrator() -> None:
    ensure_dependencies()
    import torch

    if not INPUT_JSON_PATH.exists():
        raise FileNotFoundError(f"Storyboard input not found at {INPUT_JSON_PATH}")

    payload = json.loads(INPUT_JSON_PATH.read_text(encoding="utf-8"))
    title = payload.get("title", "OmniAI Video")
    aspect_ratio = payload.get("aspect_ratio", "9:16")
    voice = payload.get("voice", "English Male")
    burn_subtitles = bool(payload.get("burn_subtitles", True))
    watermark_text = str(payload.get("watermark_text", "") or "")
    bgm_url = str(payload.get("bgm_url", "") or "")
    speed_factor = max(0.75, min(1.5, float(payload.get("speed_factor", 1.0) or 1.0)))
    use_ref_image = bool(payload.get("use_ref_image", False)) and REF_IMAGE_PATH.exists()
    seamless_loop = bool(payload.get("seamless_loop", False))
    custom_negative_prompt = str(payload.get("negative_prompt", "") or "")
    direct_telegram_upload = bool(payload.get("direct_telegram_upload", False))
    upload_individual_scenes = bool(payload.get("upload_individual_scenes", False))
    direct_youtube_upload = bool(payload.get("direct_youtube_upload", False))
    youtube_metadata = payload.get("youtube_metadata") or {}
    direct_fb_ig_upload = bool(payload.get("direct_fb_ig_upload", False))
    public_video_url = str(payload.get("public_video_url", "") or "").strip()
    telegram_caption = str(payload.get("telegram_caption", "") or "").strip()
    if not telegram_caption:
        social = payload.get("social_metadata") or {}
        viral_title = str(social.get("viralTitle") or title).strip()
        tags = social.get("hashtags") or []
        tag_str = " ".join(str(t) for t in tags if str(t).strip())
        telegram_caption = f"{viral_title}\n{tag_str}".strip()

    scenes = payload.get("scenes", [])
    if not scenes:
        raise ValueError("scenes.json contains an empty 'scenes' array.")

    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)

    print(
        f"[Orchestrator] Starting job '{title}' | scenes={len(scenes)} | aspect={aspect_ratio} | voice={voice} | speed={speed_factor}x | ref_image={use_ref_image} | seamless_loop={seamless_loop} | direct_tg={direct_telegram_upload} | direct_yt={direct_youtube_upload} | direct_meta={direct_fb_ig_upload}",
        flush=True,
    )

    bgm_path = download_bgm_if_provided(bgm_url, TEMP_DIR)

    # Step 1: Generate Edge-TTS voiceovers & synchronized SRT files for every scene
    prepared_scenes: List[Dict[str, Any]] = []
    for idx, scene in enumerate(scenes, start=1):
        narration = str(scene.get("narration", "") or "")
        dialogue = normalize_dialogue_lines(scene.get("dialogue"), voice)
        speech_text = " ".join(
            f"{line['character_name']}: {line['text']}" for line in dialogue
        ) if dialogue else narration
        visual_prompt = scene.get("visual_prompt", scene.get("prompt", ""))
        audio_path = TEMP_DIR / f"scene_{idx}.mp3"
        srt_path = TEMP_DIR / f"scene_{idx}.srt"
        raw_video_path = TEMP_DIR / f"scene_{idx}_raw.mp4"

        if dialogue:
            duration = run_async(
                synthesize_dialogue_and_srt(
                    dialogue,
                    audio_path,
                    srt_path,
                    speed_factor=speed_factor,
                )
            )
        else:
            duration = run_async(
                synthesize_voice_and_srt(narration, voice, audio_path, srt_path, speed_factor=speed_factor)
            )
        prepared_scenes.append(
            {
                "scene_index": idx,
                "visual_prompt": visual_prompt,
                "narration": speech_text,
                "duration_sec": duration,
                "audio_path": str(audio_path),
                "srt_path": str(srt_path),
                "raw_video_path": str(raw_video_path),
            }
        )
        print(
            f"[TTS] scene_{idx}: {duration:.2f}s voiceover ({speed_factor}x) + SRT generated.",
            flush=True,
        )

    # Step 2: Detect GPUs and spawn isolated dual-GPU worker subprocesses
    gpu_count = torch.cuda.device_count()
    active_gpus = 2 if gpu_count >= 2 else max(1, gpu_count)
    print(f"[Orchestrator] Detected {gpu_count} CUDA GPU(s). Launching {active_gpus} worker subprocess(es).", flush=True)

    shards: List[List[Dict[str, Any]]] = [[] for _ in range(active_gpus)]
    for i, item in enumerate(prepared_scenes):
        shards[i % active_gpus].append(item)

    processes: List[subprocess.Popen] = []
    for gpu_id in range(active_gpus):
        shard_file = TEMP_DIR / f"shard_gpu_{gpu_id}.json"
        shard_file.write_text(json.dumps(shards[gpu_id], indent=2), encoding="utf-8")

        env = os.environ.copy()
        env["CUDA_VISIBLE_DEVICES"] = str(gpu_id)
        env["PYTORCH_CUDA_ALLOC_CONF"] = "expandable_segments:True"

        cmd = [
            sys.executable,
            str(Path(__file__).resolve()),
            "--worker-gpu",
            str(gpu_id),
            "--shard-file",
            str(shard_file),
            "--aspect-ratio",
            aspect_ratio,
            "--negative-prompt",
            custom_negative_prompt,
        ]
        if use_ref_image:
            cmd.append("--use-ref-image")
        proc = subprocess.Popen(cmd, env=env)
        processes.append(proc)

    for gpu_id, proc in enumerate(processes):
        code = proc.wait()
        if code != 0:
            raise RuntimeError(f"Worker subprocess on CUDA GPU {gpu_id} failed with exit code {code}")

    # Step 3: Mux individual scenes with audio, optional subtitles, watermark, speed & BGM ducking
    width, height = resolve_dimensions(aspect_ratio)
    final_scene_mp4s: List[Path] = []
    for item in prepared_scenes:
        idx = item["scene_index"]
        out_mp4 = WORK_DIR / f"scene_{idx}.mp4"
        mux_scene_with_audio_and_subtitles(
            raw_video=Path(item["raw_video_path"]),
            audio_path=Path(item["audio_path"]),
            srt_path=Path(item["srt_path"]),
            output_mp4=out_mp4,
            burn_subtitles=burn_subtitles,
            width=width,
            height=height,
            watermark_text=watermark_text,
            bgm_path=bgm_path,
            speed_factor=speed_factor,
        )
        final_scene_mp4s.append(out_mp4)
        print(f"[Mux] Exported {out_mp4.name}", flush=True)

    # Step 4: Concatenate into final_video.mp4 (always preserved in Kaggle Output)
    final_video = WORK_DIR / "final_video.mp4"
    concatenate_scenes(final_scene_mp4s, final_video, seamless_loop=seamless_loop)
    generated_thumb = extract_video_thumbnail(final_video, WORK_DIR / "thumb.jpg")

    # Step 5: Optional v5.3 Direct Kaggle -> YouTube Data API v3 Resumable Upload
    youtube_upload_status = "DISABLED"
    youtube_watch_url = ""
    if direct_youtube_upload:
        youtube_upload_status, youtube_watch_url = perform_direct_youtube_upload(
            final_video=final_video,
            thumb_path=generated_thumb,
            youtube_metadata=youtube_metadata,
            fallback_title=title,
        )

    # Step 6: Optional v5.3 Direct Kaggle -> Meta FB/IG Reels Graph API Upload
    meta_reels_status = "DISABLED"
    if direct_fb_ig_upload:
        meta_caption = str(youtube_metadata.get("description") or telegram_caption or title)
        meta_reels_status = perform_direct_meta_reels_upload(
            final_video=final_video,
            description=meta_caption,
            public_video_url=public_video_url,
        )

    # Step 7: Optional v5.2 Direct Kaggle -> Telegram 2 GB MTProto Upload (with YouTube URL included)
    telegram_upload_status = "DISABLED"
    if direct_telegram_upload:
        enriched_caption = telegram_caption
        if youtube_watch_url:
            enriched_caption = f"{enriched_caption}\nYouTube: {youtube_watch_url}".strip()
        if meta_reels_status not in ("DISABLED", "SKIPPED", "SKIPPED_MISSING_SECRETS"):
            enriched_caption = f"{enriched_caption}\nMeta Reels: {meta_reels_status}".strip()
        telegram_upload_status = perform_direct_telegram_upload(
            final_video=final_video,
            scene_paths=final_scene_mp4s,
            caption=enriched_caption,
            upload_individual_scenes=upload_individual_scenes,
        )

    manifest = {
        "title": title,
        "aspect_ratio": aspect_ratio,
        "voice": voice,
        "speed_factor": speed_factor,
        "use_ref_image": use_ref_image,
        "seamless_loop": seamless_loop,
        "burn_subtitles": burn_subtitles,
        "watermark_text": watermark_text,
        "bgm_applied": bgm_path is not None,
        "direct_telegram_upload": direct_telegram_upload,
        "upload_individual_scenes": upload_individual_scenes,
        "telegram_upload_status": telegram_upload_status,
        "direct_youtube_upload": direct_youtube_upload,
        "youtube_upload_status": youtube_upload_status,
        "youtube_watch_url": youtube_watch_url,
        "direct_fb_ig_upload": direct_fb_ig_upload,
        "meta_reels_status": meta_reels_status,
        "final_video": final_video.name,
        "thumbnail_file": generated_thumb.name if generated_thumb else "",
        "scene_files": [p.name for p in final_scene_mp4s],
        "completed_at_epoch": int(time.time()),
    }
    (WORK_DIR / "render_manifest.json").write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )
    shutil.rmtree(TEMP_DIR, ignore_errors=True)
    print(
        f"[Orchestrator] Render complete: {final_video} + {len(final_scene_mp4s)} scenes | youtube={youtube_upload_status} | meta={meta_reels_status} | telegram={telegram_upload_status}.",
        flush=True,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="OmniAI-Studio v5.3 Dual-GPU Wan 2.1 Worker")
    parser.add_argument("--worker-gpu", type=int, default=None, help="Isolated CUDA worker index")
    parser.add_argument("--shard-file", type=str, default=None, help="JSON shard path for worker subprocess")
    parser.add_argument("--aspect-ratio", type=str, default="9:16", help="Target video aspect ratio")
    parser.add_argument("--negative-prompt", type=str, default="", help="Optional Character DNA negative prompt")
    parser.add_argument("--use-ref-image", action="store_true", help="Condition render with ref_image.jpg")
    args = parser.parse_args()

    if args.worker_gpu is not None and args.shard_file is not None:
        render_worker_shard(
            args.worker_gpu,
            Path(args.shard_file),
            args.aspect_ratio,
            args.negative_prompt,
            args.use_ref_image,
        )
    else:
        run_orchestrator()


if __name__ == "__main__":
    main()
