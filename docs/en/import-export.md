# Data Portability

橘子岛 stores all your data on-device and provides full import/export capabilities. You own your data — move it in, move it out, back it up.

## Export

Export your data to a single `.橘子岛` file — a portable archive that contains everything 橘子岛 stores.

### What Gets Exported

You choose what to include:

| Category | Contents |
|----------|----------|
| **Conversations & Messages** | All chat history, message trees, branches |
| **Memories** | Active memory and all saved memory files |
| **System Prompts** | All custom system prompt templates |
| **Settings** | App configuration and preferences |
| **API Keys** | All configured API keys |

!!! danger "API Keys Warning"
    API keys are exported in **plain text**. Anyone with the `.橘子岛` file can read your keys. Only enable API key export if you trust the destination and handle the file securely.

### How to Export

1. Go to **Settings → Data Control**
2. Tap **Export Data**
3. Select which categories to include
4. Tap **Export**
5. Choose where to save the `.橘子岛` file

---

## Import

Restore data from a previous `.橘子岛` export.

### Import Strategies

When importing, you choose how 橘子岛 handles data that already exists on your device:

| Strategy | Behavior |
|----------|----------|
| **Merge** | Add new items, keep existing ones. If an item with the same ID exists, the import version overwrites it. |
| **Replace** | Clear all existing data in the selected categories, then import. A fresh start. |
| **Skip** | Only import items that have no conflict. Existing items are untouched. |

!!! tip
    Use **Merge** for most cases — it safely adds new data while preserving what's already on your device.

### How to Import

1. Go to **Settings → Data Control**
2. Tap **Import Data**
3. Select a `.橘子岛` file
4. Review the import preview — see what's in the file (export date, version, content counts)
5. Choose an import strategy
6. Tap **Import**

!!! danger "API Keys Warning"
    If the export file contains API keys, 橘子岛 warns you before importing. Keys are imported in plain text. Only proceed if you trust the source of the file.

---

## Third-Party Import

Import conversations from other AI chat platforms.

Both Claude and ChatGPT export your data as a **`.zip` archive**. 橘子岛 imports that `.zip` directly — there is no need to unzip it first, and 橘子岛 does **not** accept loose `.json` files.

### Import from Claude

**1. Export from Claude.** Go to [Claude](https://claude.ai/) → **Settings → Data Controls → Export data**. Claude prepares the archive quickly — usually in **under an hour** — and emails you a download link.

!!! warning "Download promptly"
    Claude's download link **expires quickly**. Grab the `.zip` as soon as the email arrives — if you wait too long, the link goes dead and you'll have to request a new export.

**2. Import into 橘子岛.**

1. Go to **Settings → Data Control → Third Party → Import from Claude**
2. Select the exported `.zip` file
3. Review the preview — see conversation count and message count
4. Choose **Merge** or **Replace** strategy
5. Tap **Import**

!!! note
    橘子岛 reads the conversation data straight out of Claude's `.zip` export. Attachments are detected and shown in the preview, but only the message text is imported — attachment files themselves are not.

### Import from ChatGPT

**1. Export from ChatGPT.** Go to [ChatGPT](https://chatgpt.com/) → **Settings → Data Controls → Export data**. ChatGPT processes the request and emails you a download link when it's ready.

!!! info "Be patient"
    ChatGPT's export typically takes **1–2 days** to arrive. This is normal — wait for the email rather than re-requesting.

**2. Import into 橘子岛.**

1. Go to **Settings → Data Control → Third Party → Import from ChatGPT**
2. Select the downloaded `.zip` file
3. Review the preview
4. Choose **Merge** or **Replace** strategy
5. Tap **Import**

!!! note
    Both user and assistant messages are imported. Message roles are preserved.

---

## File Format

The `.橘子岛` file is a JSON-based archive. If you're technically inclined, you can inspect or process it with standard tools. The format is designed for forward and backward compatibility.

---

## Auto Backup

橘子岛 can automatically back up your data on a schedule. You don't need to remember to export — 橘子岛 handles it for you.

### How It Works

- Auto backup runs periodically in the background using Android WorkManager
- When a backup is due, 橘子岛 exports your selected categories to the configured directory
- A notification appears only if a backup fails — successful backups are silent
- Old backups are automatically deleted based on your retention settings

### Configuration

1. Go to **Settings → Data Control → Auto Backup**
2. Toggle **Auto Backup** on/off
3. Set **Backup every** — choose 1 day, 3 days, 5 days, 1 week, or 1 month
4. Choose **Export content** — select which categories to include. API keys **can** be included (a warning is shown when you tick that box) — only enable it if the backup location is private and secure. API keys are **not** included by default.
5. Set **Backup location** — tap to pick a folder (defaults to `Download/橘子岛/Backup`)
6. Toggle **Auto delete old backups** on/off, and set **Delete older than** period

!!! info "Auto Delete Constraint"
    The delete period must be longer than the backup period. For example, if you back up every week, backups can be auto-deleted after 1 month or 1 year — never sooner. This prevents deleting your only backup before a new one is created.

!!! note
    Auto backup uses Android's WorkManager to ensure reliability even if the app is closed or the device restarts. Backups may be slightly delayed during Doze mode to conserve battery.

---

## Best Practices

- **Export regularly** as a backup — keep the file somewhere safe
- **Enable Auto Backup** for hands-off scheduled protection
- **Don't include API keys** in routine exports — enable key export only for full device migrations
- **Use Merge for incremental imports** — Replace is destructive
- **Preview before importing** — check the export date and content counts to confirm it's the right file
