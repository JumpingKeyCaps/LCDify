# LCDify


[![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=33)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![AGSL](https://img.shields.io/badge/Shader-AGSL-FF6F00)](https://developer.android.com/develop/ui/views/graphics/agsl)


**Transform any video or image into authentic Game Boy LCD rendering**

LCDify is an Android tool that converts modern media into authentic 4-tone monochrome LCD visuals with pixel-perfect fidelity to the 1990s Game Boy screen. Built with GPU-optimized AGSL shaders for professional-grade performance.

<p align="center">
  <img src="docs/demo.gif" alt="LCDify Demo" width="400"/>
</p>

---

## Why LCDify?

### A Creative Production Tool
LCDify isn't just another filter. it's an authoring pipeline for creating authentic Game Boy-style content from real video sources.

**Use Cases:**
- Retro cinematics for games and applications
- Nostalgic social media stories and content
- 90s-style music videos and short films
- Visual novels and interactive narratives
- Artistic projects and experimental visuals

### Key Features
-  **Authentic** - Faithful to original Game Boy DMG rendering (4-tone green palette, Bayer dithering)
-  **Flexible** - Works with any video or image source
-  **Performant** - GPU-accelerated single-pass AGSL shader
-  **Adjustable** - Real-time pixelation control via Scale Factor
-  **Production-Ready** - Async preprocessing pipeline for long videos

---

## Features

### Media Processing
- **Multi-format support**: MP4, MOV, JPG, PNG
- **Async preprocessing**: Background processing with progress tracking
- **Real-time preview**: See effects before final render
- **High-quality export**: Save processed videos and images

### Visual Effects
The shader applies three transformations in a single GPU pass:

1. **Pixelation** - Controlled resolution reduction (adjustable Scale Factor)
2. **Palette Quantization** - Authentic 4-color Game Boy palette
3. **Bayer Dithering** - 4x4 ordered matrix for grayscale simulation

### User Interface
-  **Scale Factor Slider** - Control pixelation level (4.0 to 48.0)
-  **Palette Preview** - Visualize the 4 green tones in use
-  **Game Boy Theme** - UI consistent with retro aesthetic
-  **Easy Export** - Intuitive selection and save buttons

---

## Technical Architecture

### Processing Pipeline
```
1. Media Selection (video or image)
          ↓
2. Frame Extraction (MediaCodec/MediaExtractor)
          ↓
3. AGSL Shader Application (GPU processing)
          ↓
4. Re-encoding (MediaMuxer)
          ↓
5. Save and Share
```

### Tech Stack

**Android**
- Min SDK: 33 (Android 13+) - required for RenderEffect and AGSL
- Target SDK: 34+
- Language: Kotlin

**Key Components**
- **AGSL Shader** - Android Graphics Shading Language for GPU processing
- **RenderEffect** - Native shader application on surfaces
- **MediaCodec** - Hardware-accelerated video encoding/decoding
- **MediaMuxer** - Processed frame multiplexing
- **Jetpack Compose** - Modern reactive UI
- **Kotlin Coroutines** - Async processing with progress tracking

---

## The AGSL Shader

### How It Works
The shader performs virtual downsampling followed by nearest-neighbor upsampling to create the pixelation effect, then applies palette quantization and dithering.

### Scale Factor
The Scale Factor determines the size of "virtual pixels". Higher values create larger color blocks.

**Examples:**
- `SF = 8.0` → Subtle pixelation (HD pixel art style)
- `SF = 16.0` → Classic Game Boy effect  **Recommended**
- `SF = 32.0` → Heavy pixelation (Minecraft-style)

**Effective Resolution:**
```
Virtual Resolution = Source Resolution / Scale Factor
```

Example with 1920x1080 and SF=16:
- → 120×67 virtual pixels
- → Stretched back to 1920x1080 with large square pixels

This is NOT a zoom—it's a controlled resolution degradation to recreate the aesthetic of limited LCD screens.

### Authentic Game Boy Palette
- **Color 0**: `#0F381F` (Almost black-green)
- **Color 1**: `#306230` (Dark green)
- **Color 2**: `#7BAC7D` (Light green)
- **Color 3**: `#AED9AE` (Almost white-green)

### Bayer Dithering
Uses a 4×4 ordered matrix to distribute quantization error and simulate grayscale nuances with only 4 colors.

---

## Roadmap

### Phase 1 - MVP  
- [ ] Functional AGSL shader
- [ ] Basic UI (selection, preview, export)
- [ ] Simple image processing
- [ ] Video processing with progress

### Phase 2 - Enhancements
- [ ] Multiple palettes (NES, CGA, Amber, etc.)
- [ ] Scale Factor presets ("Game Boy Classic", "Retro Soft", "Pixel Art")
- [ ] Additional effects (scanlines, LCD blur, ghosting)
- [ ] Custom resolution support (not just 160×144)

### Phase 3 - Production
- [ ] Batch processing (multiple videos)
- [ ] FFmpeg integration for exotic formats
- [ ] Audio preservation in export
- [ ] Direct social media sharing

### Phase 4 - Advanced
- [ ] Real-time mode for live camera
- [ ] Post-processing filter support
- [ ] API for third-party app integration
- [ ] Desktop version (Kotlin Multiplatform)

---

## Technical Limitations

### Compatibility
- Requires Android 13+ (AGSL and RenderEffect)
- No support for older Android versions (no OpenGL ES fallback planned)

### Performance
- Video preprocessing can be time-consuming (depends on length and resolution)
- Intensive GPU usage during processing
- Moderate memory consumption (frame-by-frame processing)

### Formats
- **Video**: MP4, MOV (H.264/H.265 codec)
- **Image**: JPEG, PNG
- **Audio**: Preserved but not processed (passthrough)

---

## Project Philosophy

LCDify is designed as a creative production tool, not just a simple filter. The goal is to provide creators with a professional, performant way to generate authentic Game Boy content from real video sources.

The shader prioritizes visual authenticity (fidelity to original hardware) while offering the flexibility needed for modern creative projects.

---

## Credits

**Technologies:**
- Android Graphics Shading Language (AGSL)
- Jetpack Compose
- Kotlin Coroutines
- MediaCodec API

**Inspiration:**
- Nintendo Game Boy DMG-01 (1989)
- Authentic monochrome green LCD palette
- Bayer ordered dithering algorithm

---
