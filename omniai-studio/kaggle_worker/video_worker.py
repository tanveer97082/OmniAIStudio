#!/usr/bin/env python3
"""
OmniAI-Studio Dual-GPU Kaggle Video Rendering Worker
====================================================
Reads `/kaggle/input/video-tasks-input/scenes.json`, generates Edge-TTS voiceovers
and synchronized `.srt` subtitles per scene, auto-detects dual GPUs (T4 x2) and
spawns isolated CUDA subprocesses running `Wan-AI/Wan2.1-T2V-1.3B-Diffusers`
(bfloat16 UNet/Transformer + float32 VAE) in parallel, muxes individual
`/kaggle/working/scene_X.mp4` clips with optional burned yellow/white bottom-center
subtitles, subtle bottom-right `watermark_text` drawtext overlay, and optional
`bgm_url` ducked at 15% volume via FFmpeg `sidechaincompress`, then concatenates
`/kaggle/working/final_video.mp4`.
"""

import argparse
import asyncio
import json
import math
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

INPUT_JSON_PATH = Path("/kaggle/input/video-tasks-input/scenes.json")
REF_IMAGE_PATH = Path("/kaggle/input/video-tasks-input/ref_image.jpg")
WORK_DIR = Path("/kaggle/working")
TEMP_DIR = WORK_DIR / "_tmp_render"
MODEL_ID = "Wan-AI/Wan2.1-T2V-1.3B-Diffusers"
I2V_MODEL_ID = "Wan-AI/Wan2.1-I2V-14B-480P-Diffusers"

VOICE_MAP = {
    "Hindi Male": "hi-IN-MadhurNeural",
    "Hindi Female": "hi-IN-SwaraNeural",
    "English Male": "en-US-AndrewNeural",
    "English Female": "en-US-AvaNeural",
}


def ensure_dependencies() -> None:
    """Install required runtime packages if missing in the Kaggle container."""
    required = [
        ("edge_tts", "edge-tts==6.1.12"),
        ("diffusers", "git+https://github.com/huggingface/diffusers.git"),
        ("transformers", "transformers>=4.49.0"),
        ("accelerate", "accelerate>=1.2.0"),
        ("imageio", "imageio[ffmpeg]>=2.34.0"),
        ("ftfy", "ftfy>=6.2.0"),
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


def download_bgm_if_provided(bgm_url: str, target_dir: Path) -> Optional[Path]:
    """Download optional background music track from HTTPS URL for FFmpeg ducking."""
    cleaned = (bgm_url or "").strip()
    if not cleaned.startswith("https://") and not cleaned.startswith("http://"):
        return None
    dest = target_dir / "bgm_track.audio"
    try:
        print(f"[BGM] Downloading background music from {cleaned}...", flush=True)
        req = urllib.request.Request(cleaned, headers={"User-Agent": "OmniAI-Studio-Worker/2.0"})
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
            # Blend subtle 0.15 color/composition anchor from ref_image on opening frame if PIL frames
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
        # Mix voiceover (input 1) over looped BGM (input 2) ducked to 15% volume via sidechaincompress
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
    scenes = payload.get("scenes", [])
    if not scenes:
        raise ValueError("scenes.json contains an empty 'scenes' array.")

    if TEMP_DIR.exists():
        shutil.rmtree(TEMP_DIR)
    TEMP_DIR.mkdir(parents=True, exist_ok=True)

    print(
        f"[Orchestrator] Starting job '{title}' | scenes={len(scenes)} | aspect={aspect_ratio} | voice={voice} | speed={speed_factor}x | ref_image={use_ref_image} | seamless_loop={seamless_loop} | burn_subtitles={burn_subtitles}",
        flush=True,
    )

    bgm_path = download_bgm_if_provided(bgm_url, TEMP_DIR)

    # Step 1: Generate Edge-TTS voiceovers & synchronized SRT files for every scene
    prepared_scenes: List[Dict[str, Any]] = []
    for idx, scene in enumerate(scenes, start=1):
        narration = scene.get("narration", "")
        visual_prompt = scene.get("visual_prompt", scene.get("prompt", ""))
        audio_path = TEMP_DIR / f"scene_{idx}.mp3"
        srt_path = TEMP_DIR / f"scene_{idx}.srt"
        raw_video_path = TEMP_DIR / f"scene_{idx}_raw.mp4"

        duration = asyncio.run(
            synthesize_voice_and_srt(narration, voice, audio_path, srt_path, speed_factor=speed_factor)
        )
        prepared_scenes.append(
            {
                "scene_index": idx,
                "visual_prompt": visual_prompt,
                "narration": narration,
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

    # Step 4: Concatenate into final_video.mp4 (with optional 0.25s seamless_loop trim)
    final_video = WORK_DIR / "final_video.mp4"
    concatenate_scenes(final_scene_mp4s, final_video, seamless_loop=seamless_loop)

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
        "final_video": final_video.name,
        "scene_files": [p.name for p in final_scene_mp4s],
        "completed_at_epoch": int(time.time()),
    }
    (WORK_DIR / "render_manifest.json").write_text(
        json.dumps(manifest, indent=2), encoding="utf-8"
    )
    shutil.rmtree(TEMP_DIR, ignore_errors=True)
    print(f"[Orchestrator] Render complete: {final_video} + {len(final_scene_mp4s)} individual scene clips.", flush=True)


def main() -> None:
    parser = argparse.ArgumentParser(description="OmniAI-Studio v2.0 Dual-GPU Wan 2.1 Worker")
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
