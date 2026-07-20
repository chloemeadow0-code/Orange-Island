package com.orangeisland.app.data

import java.io.File

/**
 * A plugin that has been installed on disk: its parsed [manifest] plus the [dir] it lives in
 * and whether the user has toggled it [enabled].
 *
 * `enabled` is resolved at scan time from the persisted enabled-id set in DataStore; the
 * filesystem itself is the source of truth for "what's installed", and DataStore only stores
 * the user's enable/disable preference.
 */
data class InstalledPlugin(
    val manifest: PluginManifest,
    val dir: File,
    val enabled: Boolean,
) {
    val id: String get() = manifest.id
    val mainJsFile: File get() = File(dir, "main.js")
    /** HTML page backing the plugin's UI, or null if [PluginManifest.ui] is unset or the file
     *  is missing on disk. The settings page uses this to decide whether to show the
     *  "Open UI" affordance. */
    val uiHtmlFile: File? get() = manifest.ui?.let { File(dir, it).takeIf { f -> f.exists() } }
}
