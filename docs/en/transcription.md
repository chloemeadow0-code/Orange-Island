# Image Transcription

Let a vision model describe images so that text-only models can understand them.

## What It Does

When you send an image to a text-only model, 橘子岛 can use a separate vision model to generate a text description of the image first. This description is then included in the prompt sent to your main model.

This lets you use images with any model, even ones that don't natively support vision.

## Setup

1. Go to **Settings → Image Transcription**
2. Choose a **Transcription Model** — this should be a vision-capable model (e.g., GPT-4o, Gemini Flash, Qwen-VL)
3. Add models to **Enabled Models** — these are the text-only models that will receive image descriptions
4. Adjust **Batch Size** if you send many images at once (how many images to describe per API call)

!!! tip "Local Vision Models"
    You can use a local vision model (with mmproj) as the transcription model. This keeps image processing on-device.

## How It Works

1. You attach an image to your message
2. 橘子岛 detects that your current model doesn't support vision
3. The image is sent to the transcription model first
4. The transcription model generates a text description
5. This description is prepended to your message text
6. The combined text is sent to your main model

---

## Batch Size

Controls how many images are described per API call to the transcription model.

- **1** — Describe one image at a time (more API calls, more accurate)
- **5–10** — Describe multiple images per call (fewer API calls, may lose detail)

Default is device-dependent. Lower values give better results but cost more.

---

## Model Selection

### Transcription Model

This is the vision model that generates image descriptions. Choose the most capable vision model available to you.

### Enabled Models

These are the text-only models that will use image transcription. Only models in this list will receive transcribed image descriptions. Other models will receive images directly (if they support them) or not at all.
