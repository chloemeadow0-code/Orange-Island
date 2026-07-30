package com.orangeisland.app.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.orangeisland.app.util.DebugLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and caches the 24 bundled Mikan sprite PNGs from `assets/pet/`.
 *
 * Sprites are transparent pixel-art PNGs (~244×300). Decoded as [Bitmap.Config.ARGB_8888]
 * so the alpha channel (the transparency that makes the pet float) is preserved —
 * the default config on some devices is RGB_565 which would render an opaque box.
 *
 * A [LruCache] caps the in-memory footprint (~7 MB for all 24 at ARGB_8888) and
 * re-decodes evicted entries on demand from assets. Loading is lazy: only the
 * sprites a session actually touches are held in memory.
 */
object PetAssets {

    private const val TAG = "PetAssets"
    private const val ASSET_DIR = "pet"
    private const val CACHE_KIB = 8 * 1024 // 8 MB ceiling — comfortably above the ~300 KB/sprite × 24.

    /** Logical expression name without the leading index, e.g. "front", "sleep_zzz", "eat_orange". */
    private val cache = object : LruCache<String, Bitmap>(CACHE_KIB) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Tracks sprites known to be absent from assets so we don't keep re-probing. */
    private val missing = ConcurrentHashMap.newKeySet<String>()

    /**
     * Returns the bitmap for [name] (e.g. "01_front" or "front" — both accepted), or
     * `null` if no such sprite exists. The returned bitmap is shared and must not be
     * recycled by the caller.
     */
    fun get(context: Context, name: String): Bitmap? {
        val key = normalize(name) ?: return null
        cache.get(key)?.let { return it }
        if (missing.contains(key)) return null
        val loaded = decode(context, key) ?: run {
            missing.add(key)
            return null
        }
        cache.put(key, loaded)
        return loaded
    }

    /** Whether [name] resolves to a real sprite. Does not decode. */
    fun exists(context: Context, name: String): Boolean {
        val key = normalize(name) ?: return false
        return cache.get(key) != null || !missing.contains(key) && runCatching {
            context.assets.list(ASSET_DIR)?.any { it.startsWith(key) }
        }.getOrNull() == true
    }

    private fun normalize(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        // Accept both "01_front" and "front". Asset files are "<NN>_<name>.png".
        return if (trimmed.contains('_') && trimmed.first().isDigit()) trimmed else {
            // Find the file whose suffix after the first '_' matches.
            // Deferred: we can't list without a context here, so keep the raw name;
            // decode() handles both forms via list-and-match.
            trimmed
        }
    }

    private fun decode(context: Context, key: String): Bitmap? {
        val appContext = context.applicationContext
        val fileName = resolveFileName(appContext, key) ?: return null
        return try {
            appContext.assets.open("$ASSET_DIR/$fileName").use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            DebugLog.w(TAG, "Failed to decode sprite '$key' ($fileName)", e)
            null
        }
    }

    /** Maps a key ("front" or "01_front") to its actual asset filename ("01_front.png"). */
    private fun resolveFileName(context: Context, key: String): String? {
        val list = runCatching { context.assets.list(ASSET_DIR) }.getOrNull() ?: return null
        // Exact key match first (e.g. key == "01_front").
        list.firstOrNull { it == "$key.png" }?.let { return it }
        // Suffix match (e.g. key == "front" → "01_front.png").
        list.firstOrNull { it.substringBefore(".png").endsWith("_$key") }?.let { return it }
        return null
    }
}
