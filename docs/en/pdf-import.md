# PDF Import

橘子岛 can extract and send selected pages from PDF files as images to vision-capable models.

## How It Works

1. Attach a PDF file in chat
2. A dialog opens showing all pages as thumbnails
3. Select which pages to send
4. Confirm — 橘子岛 extracts the pages as images and sends them to the model

The model receives the pages as vision input, letting it read and analyze PDF content.

---

## Page Selection

### Choosing Pages

- Each page thumbnail has a **checkbox** in the top-left corner
- Tap the checkbox to toggle a page on/off
- **Select All** / **Deselect All** buttons for bulk operations
- The top bar shows the selected count (e.g., "3 selected")
- Maximum **50 pages** per PDF (larger files are clipped)

### Fullscreen Preview

Tap any thumbnail to open the fullscreen viewer:

| Gesture | Action |
|---------|--------|
| Swipe left/right | Navigate between pages |
| Tap | Show/hide the overlay controls |
| Pinch | Zoom in/out |
| Bottom-left capsule | Toggle page selection |

The preview lets you inspect pages before deciding which to send.

---

## Sending Pages

After selecting pages, tap the confirm button. 橘子岛:

1. Renders each selected PDF page as a high-resolution image
2. Attaches the images to your message
3. Sends them to the model (requires a vision-capable model)

---

## Limitations

- **Max 50 pages** per PDF — larger files are truncated
- **Image-only**: Text is not extracted; the model reads pages visually
- **Vision model required**: The active chat model must support image inputs
- **File size**: While there's no hard PDF size limit, very large files may be slow to render

---

## Use Cases

- **Document analysis** — upload a contract, report, or paper for the model to review
- **Research** — share specific pages from academic papers
- **Translation** — send foreign-language documents for translation
- **Summarization** — get summaries of long documents, page by page
