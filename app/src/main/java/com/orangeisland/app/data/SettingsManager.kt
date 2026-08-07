package com.orangeisland.app.data

import android.content.Context
import com.orangeisland.app.model.ThinkingLevels
import com.orangeisland.app.model.ToolCallDisplayModes
import com.orangeisland.app.ui.theme.FontSizeTiers
import com.orangeisland.app.util.Constants
import com.orangeisland.app.util.DebugLog
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

@Serializable
data class ApiKeyEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val key: String,
    val provider: String = Constants.PROVIDER_GOOGLE
)

@Serializable
data class CustomProviderConfig(
    val name: String
)

/** A single App Lock entry. Persisted in [SettingsManager.APP_LOCK_ENTRIES_JSON] keyed by package name. */
@Serializable
data class AppLockEntry(
    val packageName: String,
    val label: String,
    /** AI's message shown on the lock screen explaining why the app is locked. */
    val message: String,
    /** Epoch millis when the lock was created (for diagnostics/auditing). */
    val createdAt: Long = 0L
)

@Serializable
data class ShellDeviceConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val type: String = "conch",          // "conch" | "ssh"
    // Conch fields (type=conch)
    val serverUrl: String = "",
    val apiKey: String = "",
    val timeout: Int = 30,
    val conchPublicKey: String = "",
    // SSH fields (type=ssh)
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUser: String = "root",
    val sshPassword: String = "",
    // Pinned SSH host key (base64 of the server public-key blob). Blank = not yet
    // pinned (trust-on-first-use); once set, connections must match or are rejected.
    val sshHostKey: String = ""
)

@Serializable
data class SystemPromptEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String = "",
    val systemItems: List<PromptTemplateItem> = emptyList(),
    val userPrependItems: List<PromptTemplateItem> = emptyList(),
    val userPostpendItems: List<PromptTemplateItem> = emptyList()
) {
    val resolvedSystemItems: List<PromptTemplateItem>
        get() = if (systemItems.isNotEmpty()) systemItems
        else if (content.isNotBlank()) listOf(PromptTemplateItem(type = PromptItemType.CUSTOM, value = content))
        else emptyList()
}

@Serializable
data class ConversationSettings(
    val contextWindow: Int? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val codeExecutionEnabled: Boolean? = null,
    val googleSearchEnabled: Boolean? = null,
    val thinkingEnabled: Boolean? = null,
    val thinkingLevel: String? = null,
    val thinkingBudgetEnabled: Boolean? = null,
    val thinkingBudgetTokens: Int? = null,
    val webSearchEnabled: Boolean? = null,
    val shellEnabled: Boolean? = null,
    val videoNarrationEnabled: Boolean? = null,
    /** Per-conversation MCP server activation.
     *  `null`  = inherit globally enabled servers (default).
     *  empty   = MCP tools disabled for this conversation.
     *  non-empty = activate exactly these server ids (regardless of global enabled flag). */
    val mcpServerIds: List<String>? = null,
    /** Per-conversation JS-plugin activation (same semantics as [mcpServerIds]). */
    val pluginIds: List<String>? = null
) {
    fun isAllNull() = contextWindow == null && temperature == null && maxTokens == null && topP == null
        && frequencyPenalty == null && presencePenalty == null
        && codeExecutionEnabled == null && googleSearchEnabled == null && thinkingEnabled == null
        && thinkingLevel == null && thinkingBudgetEnabled == null && thinkingBudgetTokens == null
        && webSearchEnabled == null && shellEnabled == null && videoNarrationEnabled == null && mcpServerIds == null && pluginIds == null
}

class SettingsManager(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        val SELECTED_MODEL = stringPreferencesKey("selected_model")
        val AVAILABLE_MODELS_JSON = stringPreferencesKey("available_models_json")
        val ENABLED_MODELS = stringSetPreferencesKey("enabled_models")
        
        val API_KEYS_JSON = stringPreferencesKey("api_keys_json")
        val ACTIVE_API_KEY_IDS_JSON = stringPreferencesKey("active_api_key_ids_json")
        
        val SYSTEM_PROMPTS_JSON = stringPreferencesKey("system_prompts_json")
        val ACTIVE_SYSTEM_PROMPT_ID = stringPreferencesKey("active_system_prompt_id")
        val MODEL_ALIASES_JSON = stringPreferencesKey("model_aliases_json")
        val MODEL_CONTEXT_LIMITS_JSON = stringPreferencesKey("model_context_limits_json")
        val MAX_CONTEXT_WINDOW = stringPreferencesKey("max_context_window")
        val VISUALIZE_CONTEXT_ROLLOUT = booleanPreferencesKey("visualize_context_rollout")
        val SHOW_MESSAGE_USAGE_STATS = booleanPreferencesKey("show_message_usage_stats")
        val REMEMBER_LAST_CONVERSATION = booleanPreferencesKey("remember_last_conversation")
        val LAST_ACTIVE_CONVERSATION_ID = stringPreferencesKey("last_active_conversation_id")
        val CODE_EXECUTION_ENABLED = booleanPreferencesKey("code_execution_enabled")
        val WORKFLOW_ENABLED = booleanPreferencesKey("workflow_enabled")
        val WORKFLOW_MAX_RUN_MS = longPreferencesKey("workflow_max_run_ms")
        val WORKFLOW_MAX_TOOL_CALLS = intPreferencesKey("workflow_max_tool_calls")
        val WORKFLOW_CONFIRM_DESTRUCTIVE = booleanPreferencesKey("workflow_confirm_destructive")
        val WORKFLOW_BACKGROUND_SAFE_ONLY = booleanPreferencesKey("workflow_background_safe_only")
        val GOOGLE_SEARCH_ENABLED = booleanPreferencesKey("google_search_enabled")
        val THINKING_ENABLED = booleanPreferencesKey("thinking_enabled")
        val THINKING_LEVEL = stringPreferencesKey("thinking_level")
        val THINKING_BUDGET_ENABLED = booleanPreferencesKey("thinking_budget_enabled")
        val THINKING_BUDGET_TOKENS = intPreferencesKey("thinking_budget_tokens")
        val PROVIDER_BASE_URLS = stringPreferencesKey("provider_base_urls")
        val PROVIDER_CUSTOM_HEADERS_JSON = stringPreferencesKey("provider_custom_headers_json")
        val TRUSTED_HTTP_HOSTS = stringSetPreferencesKey("trusted_http_hosts")
        val HTTP_REMINDER_SILENCED_HOSTS = stringSetPreferencesKey("http_reminder_silenced_hosts")
        val TITLE_GENERATION_ENABLED = booleanPreferencesKey("title_generation_enabled")
        val TITLE_GENERATION_MODEL = stringPreferencesKey("title_generation_model")
        val TITLE_GENERATION_PROMPT = stringPreferencesKey("title_generation_prompt")
        val AUTO_COMPRESS_MODEL = stringPreferencesKey("auto_compress_model")
        val AUTO_COMPRESS_PROMPT = stringPreferencesKey("auto_compress_prompt")
        val IMAGE_TRANSCRIPTION_ENABLED_MODELS = stringSetPreferencesKey("image_transcription_enabled_models")
        val IMAGE_TRANSCRIPTION_MODEL = stringPreferencesKey("image_transcription_model")
        val IMAGE_TRANSCRIPTION_BATCH_SIZE = intPreferencesKey("image_transcription_batch_size")
        val IMAGE_TRANSCRIPTION_PROMPT = stringPreferencesKey("image_transcription_prompt")
        val VIDEO_NARRATION_ENABLED_MODELS = stringSetPreferencesKey("video_narration_enabled_models")
        val VIDEO_NARRATION_MODEL = stringPreferencesKey("video_narration_model")
        val VIDEO_NARRATION_PROMPT = stringPreferencesKey("video_narration_prompt")
        val VIDEO_NARRATION_FPS = stringPreferencesKey("video_narration_fps")
        val VIDEO_NARRATION_DETAIL = stringPreferencesKey("video_narration_detail")
        val VIDEO_NARRATION_MAX_LONG_SIDE = intPreferencesKey("video_narration_max_long_side")
        val ACCESS_PAST_CONVERSATIONS = booleanPreferencesKey("access_past_conversations")
        val ACCESS_SAVED_MEMORIES = booleanPreferencesKey("access_saved_memories")
        val ACCESS_ACTIVE_MEMORY = booleanPreferencesKey("access_active_memory")
        val RAG_SEARCH_ENABLED = booleanPreferencesKey("rag_search_enabled")
        val MODEL_SEARCH_METHOD = stringPreferencesKey("model_search_method")
        val MANUAL_SEARCH_METHOD = stringPreferencesKey("manual_search_method")
        val EMBEDDING_MODELS_JSON = stringPreferencesKey("embedding_models_json")
        val ACTIVE_EMBEDDING_MODEL_ID = stringPreferencesKey("active_embedding_model_id")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val WEB_SEARCH_API_KEYS_JSON = stringPreferencesKey("web_search_api_keys_json")
        val WEB_SEARCH_NUM_RESULTS = intPreferencesKey("web_search_num_results")
        val WEB_SEARCH_BASE_URL = stringPreferencesKey("web_search_base_url")
        val IMAGE_GEN_ENABLED = booleanPreferencesKey("image_gen_enabled")
        // Selected image model as "Provider:modelId"; provider creds are reused (no separate key/url).
        val IMAGE_GEN_MODEL = stringPreferencesKey("image_gen_model")
        val IMAGE_GEN_SIZE = stringPreferencesKey("image_gen_size")
        val SEARCH_CONTEXT_WINDOW = intPreferencesKey("search_context_window")
        val SEARCH_MATCH_LIMIT = intPreferencesKey("search_match_limit")
        val RAG_THRESHOLD = stringPreferencesKey("rag_threshold")
        val AUTO_CACHE_ENABLED = booleanPreferencesKey("auto_cache_enabled")
        val AUTO_UPDATE_CHECK = booleanPreferencesKey("auto_update_check")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val LOCAL_CHAT_MODELS_JSON = stringPreferencesKey("local_chat_models_json")
        val CUSTOM_PROVIDERS_JSON = stringPreferencesKey("custom_providers_json")
        // Manually-added model IDs per custom provider (provider -> [modelId, ...]).
        // Stored separately from AVAILABLE_MODELS_JSON so the fetch/sync pipeline
        // (which overwrites availableModels on success) cannot clobber user entries.
        val MANUAL_MODELS_JSON = stringPreferencesKey("manual_models_json")
        val SHELL_ENABLED = booleanPreferencesKey("shell_enabled")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val PROXY_TYPE = stringPreferencesKey("proxy_type")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = stringPreferencesKey("proxy_port")
        val PROXY_USERNAME = stringPreferencesKey("proxy_username")
        val PROXY_PASSWORD = stringPreferencesKey("proxy_password")
        val PROXY_BYPASS = stringPreferencesKey("proxy_bypass")
        const val DEFAULT_PROXY_HOST = "127.0.0.1"
        const val DEFAULT_PROXY_PORT = "7890"
        const val DEFAULT_PROXY_BYPASS = "localhost\n127.0.0.1\n10.0.0.0/8\n172.16.0.0/12\n192.168.0.0/16\n::1"
        val SHELL_CONFIRM_ENABLED = booleanPreferencesKey("shell_confirm_enabled")
        val SHELL_DEVICES_JSON = stringPreferencesKey("shell_devices_json")
        val SANDBOX_ENABLED = booleanPreferencesKey("sandbox_enabled")
        // ── Device Access tools (per-tool on/off) ─────────────────
        // Each is off by default; the user opts in from Settings → Device Access.
        val DEVICE_INFO_ENABLED = booleanPreferencesKey("device_info_enabled")
        val CAMERA_TOOL_ENABLED = booleanPreferencesKey("camera_tool_enabled")
        val LOCATION_ENABLED = booleanPreferencesKey("location_enabled")
        val CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val NOTIFICATION_ENABLED = booleanPreferencesKey("notification_enabled")
        val USAGE_STATS_ENABLED = booleanPreferencesKey("usage_stats_enabled")
        val NAVIGATION_ENABLED = booleanPreferencesKey("navigation_enabled")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        // AppLock state: JSON map of package_name -> {message, label}.
        val APP_LOCK_ENTRIES_JSON = stringPreferencesKey("app_lock_entries_json")
        val TOAST_ENABLED = booleanPreferencesKey("toast_enabled")
        // ── Desktop Pet (悬浮桌宠 Mikan) ──────────────────────────
        // Master switch. When on AND the overlay permission is granted, a foreground
        // service draws the pet character over other apps. Position keys persist the
        // last user-dragged location so the pet reappears where it was left.
        val PET_ENABLED = booleanPreferencesKey("pet_enabled")
        val PET_POS_X = intPreferencesKey("pet_pos_x")
        val PET_POS_Y = intPreferencesKey("pet_pos_y")
        val ALARM_ENABLED = booleanPreferencesKey("alarm_enabled")
        val HEALTH_TOOL_ENABLED = booleanPreferencesKey("health_tool_enabled")
        val TIME_TOOL_ENABLED = booleanPreferencesKey("time_tool_enabled")
        // UI Automation (tap/swipe/scroll/global-action/inspect) — the most powerful tool surface,
        // gated behind a separate accessibility service the user must enable explicitly.
        val UI_AUTOMATION_ENABLED = booleanPreferencesKey("ui_automation_enabled")
        val USER_INTERACTION_ENABLED = booleanPreferencesKey("user_interaction_enabled")
        // Media control (read/controllable MediaSession of other apps, e.g. NetEase Cloud Music).
        // Same authorization path as the notification listener: a bound NotificationListenerService
        // is what lets MediaSessionManager see other apps' sessions reliably.
        val MEDIA_CONTROL_ENABLED = booleanPreferencesKey("media_control_enabled")
        // Amap (高德) REST API key for the location tool's reverse geocoding + nearby search.
        val AMAP_API_KEY = stringPreferencesKey("amap_api_key")
        val MCP_SERVERS_JSON = stringPreferencesKey("mcp_servers_json")
        val PLUGINS_ENABLED = stringSetPreferencesKey("plugins_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BLUR_EFFECTS_ENABLED = booleanPreferencesKey("blur_effects_enabled")
        val CODE_BLOCK_WRAP_ENABLED = booleanPreferencesKey("code_block_wrap_enabled")
        // When on, a finished (non-streaming) assistant reply is split at the model's own \n
        // boundaries into multiple stacked bubbles. Generic helper lives in BubbleSplitter.kt.
        val SPLIT_ASSISTANT_BUBBLE_BY_LINE = booleanPreferencesKey("split_assistant_bubble_by_line")
        val NON_STREAM_OUTPUT_ENABLED = booleanPreferencesKey("non_stream_output_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val TOOL_CALL_DISPLAY_MODE = stringPreferencesKey("tool_call_display_mode")
        val SCHEME_STYLE = stringPreferencesKey("scheme_style")
        // ── Custom colors (nullable ARGB Long; null = follow theme default) ──────
        val CUSTOM_COLOR_CHAT_TEXT = longPreferencesKey("custom_color_chat_text")
        val CUSTOM_COLOR_GLOBAL_TEXT = longPreferencesKey("custom_color_global_text")
        val CUSTOM_COLOR_USER_BUBBLE = longPreferencesKey("custom_color_user_bubble")
        val CUSTOM_COLOR_ASSISTANT_BUBBLE = longPreferencesKey("custom_color_assistant_bubble")
        val CUSTOM_COLOR_REASONING_PANEL = longPreferencesKey("custom_color_reasoning_panel")
        val CUSTOM_COLOR_CHAT_BACKGROUND = longPreferencesKey("custom_color_chat_background")
        val CUSTOM_COLOR_ACCENT = longPreferencesKey("custom_color_accent")
        val CUSTOM_COLOR_INPUT_FIELD = longPreferencesKey("custom_color_input_field")
        // ── Illustration backgrounds (local file paths; empty string = none) ──────
        val ILLUSTRATION_CHAT_BACKGROUND_PATH = stringPreferencesKey("illustration_chat_background_path")
        val ILLUSTRATION_INPUT_BACKGROUND_PATH = stringPreferencesKey("illustration_input_background_path")
        val ILLUSTRATION_DRAWER_BACKGROUND_PATH = stringPreferencesKey("illustration_drawer_background_path")
        val ILLUSTRATION_USER_BUBBLE_BACKGROUND_PATH = stringPreferencesKey("illustration_user_bubble_background_path")
        val ILLUSTRATION_USER_BUBBLE_CORNER_RADIUS = stringPreferencesKey("illustration_user_bubble_corner_radius")
        val ILLUSTRATION_TOPBAR_BACKGROUND_PATH = stringPreferencesKey("illustration_topbar_background_path")
        val ILLUSTRATION_REASONING_BACKGROUND_PATH = stringPreferencesKey("illustration_reasoning_background_path")
        val TRANSPARENCY_TOPBAR = stringPreferencesKey("transparency_topbar")
        val TOPBAR_CAPSULE_SCALE = stringPreferencesKey("topbar_capsule_scale")
        // ── Transparency (0f..1f) ──────────────────────────────────
        val TRANSPARENCY_MESSAGE_BUBBLE = stringPreferencesKey("transparency_message_bubble")
        val TRANSPARENCY_USER_BUBBLE_MASK = stringPreferencesKey("transparency_user_bubble_mask")
        val TRANSPARENCY_REASONING_PANEL = stringPreferencesKey("transparency_reasoning_panel")
        val TRANSPARENCY_DRAWER_ITEM = stringPreferencesKey("transparency_drawer_item")
        // ── Recent custom colors (comma-separated ARGB Long list, most-recent-first) ──
        val RECENT_CUSTOM_COLORS = stringPreferencesKey("recent_custom_colors")
        val FONT_PREFERENCE = stringPreferencesKey("font_preference")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val FONT_SIZE_TIER = stringPreferencesKey("font_size_tier")
        val FIRST_LAUNCH_TIME = longPreferencesKey("first_launch_time")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PRIVACY_POLICY_ACCEPTED = booleanPreferencesKey("privacy_policy_accepted")
        // ── Account / Auth (local mirror of the Supabase session) ──────────────
        // Drives the login gate in MainActivity; persisted so the user stays
        // logged in across process restarts. Cleared by AuthRepository.logout().
        val LOGGED_IN = booleanPreferencesKey("logged_in")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val LAST_FORCE_LOGOUT_VERSION = intPreferencesKey("last_force_logout_version")
        val RATING_PROMPT_SUBMITTED = booleanPreferencesKey("rating_prompt_submitted")
        val RATING_PROMPT_DISMISSED = booleanPreferencesKey("rating_prompt_dismissed")
        val SHOW_DOCUMENTATION_FAB = booleanPreferencesKey("show_documentation_fab")
        val TOTAL_MESSAGES_SENT = intPreferencesKey("total_messages_sent")
        val DEFAULT_TEMPERATURE = stringPreferencesKey("default_temperature")
        val DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
        val DEFAULT_TOP_P = stringPreferencesKey("default_top_p")
        val DEFAULT_FREQUENCY_PENALTY = stringPreferencesKey("default_frequency_penalty")
        val DEFAULT_PRESENCE_PENALTY = stringPreferencesKey("default_presence_penalty")
        val CONVERSATION_SETTINGS_JSON = stringPreferencesKey("conversation_settings_json")
        // ── Auto Backup ───────────────────────────────────────────
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_PERIOD_HOURS = intPreferencesKey("auto_backup_period_hours")
        val AUTO_BACKUP_CATEGORIES = stringPreferencesKey("auto_backup_categories")
        val AUTO_BACKUP_DIRECTORY = stringPreferencesKey("auto_backup_directory")
        val AUTO_DELETE_ENABLED = booleanPreferencesKey("auto_delete_enabled")
        val AUTO_DELETE_PERIOD_HOURS = intPreferencesKey("auto_delete_period_hours")
        val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
        val LAST_MODELS_FETCH_FINGERPRINT = stringPreferencesKey("last_models_fetch_fingerprint")
        // ── Health / Gadgetbridge / Sync ──────────────────────────
        val GADGETBRIDGE_ENABLED = booleanPreferencesKey("gadgetbridge_enabled")
        val GADGETBRIDGE_DB_PATH = stringPreferencesKey("gadgetbridge_db_path")
        val HEALTH_SYNC_ENABLED = booleanPreferencesKey("health_sync_enabled")
        val HEALTH_SYNC_SUPABASE_URL = stringPreferencesKey("health_sync_supabase_url")
        val HEALTH_SYNC_SUPABASE_API_KEY = stringPreferencesKey("health_sync_supabase_api_key")
        val HEALTH_SYNC_TABLE_NAME = stringPreferencesKey("health_sync_table_name")
        val AUTO_APPROVE_SENSITIVE_TOOLS = booleanPreferencesKey("auto_approve_sensitive_tools")
        // ── Environment awareness ─────────────────────────────────
        val ENVIRONMENT_AWARENESS_ENABLED = booleanPreferencesKey("environment_awareness_enabled")
        // ── Mini App ──────────────────────────────────────────────
        val MINI_APP_ENTRIES_JSON = stringPreferencesKey("mini_app_entries_json")
        val ANNIVERSARIES_JSON = stringPreferencesKey("anniversaries_json")
        // ── Plugin device id ──────────────────────────────────────
        // A stable per-install UUID auto-injected into every plugin sandbox / WebView as
        // __OI_USER_ID so plugins can attribute actions to this device without learning
        // anything else about the user. No UI — generated lazily on first read, never edited.
        val APP_USER_ID = stringPreferencesKey("app_user_id")
        // ── Plugin configs ────────────────────────────────────────
        // Per-plugin config values keyed by plugin id. Outer JSON: { "<pluginId>": { "<field>": "<value>" } }.
        // Plugins declare their fields in manifest.config; this only stores what the user filled in.
        val PLUGIN_CONFIGS_JSON = stringPreferencesKey("plugin_configs_json")
        // ── Text-to-Speech ──────────────────────────────────────────
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        val TTS_API_KEY = stringPreferencesKey("tts_api_key")
        val TTS_VOICE_ID = stringPreferencesKey("tts_voice_id")
        val TTS_MODEL = stringPreferencesKey("tts_model")
        val TTS_SPEED = stringPreferencesKey("tts_speed")
        val TTS_OUTPUT_FORMAT = stringPreferencesKey("tts_output_format")
        val TTS_STABILITY = stringPreferencesKey("tts_stability")
        val TTS_SIMILARITY_BOOST = stringPreferencesKey("tts_similarity_boost")
        val TTS_STYLE = stringPreferencesKey("tts_style")
        val TTS_VOLUME = stringPreferencesKey("tts_volume")
        val TTS_PITCH = stringPreferencesKey("tts_pitch")
        // ── Speech-to-Text (SiliconFlow) ───────────────────────────
        val STT_ENABLED = booleanPreferencesKey("stt_enabled")
        val STT_API_KEY = stringPreferencesKey("stt_api_key")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val STT_BASE_URL = stringPreferencesKey("stt_base_url")
        // ── Music Studio (native music generation) ─────────────────
        val MUSIC_STUDIO_ENABLED = booleanPreferencesKey("music_studio_enabled")
        val MUSIC_STUDIO_PROVIDER = stringPreferencesKey("music_studio_provider")
        val MUSIC_STUDIO_SUNO_API_URL = stringPreferencesKey("music_studio_suno_api_url")
        val MUSIC_STUDIO_SUNO_API_KEY = stringPreferencesKey("music_studio_suno_api_key")
        val MUSIC_STUDIO_REPLICATE_API_KEY = stringPreferencesKey("music_studio_replicate_api_key")
        val MUSIC_STUDIO_REPLICATE_MODEL_VERSION = stringPreferencesKey("music_studio_replicate_model_version")
        val MUSIC_STUDIO_RVC_MODEL_URL = stringPreferencesKey("music_studio_rvc_model_url")
        val MUSIC_STUDIO_VOICE_REPLACEMENT_ENABLED = booleanPreferencesKey("music_studio_voice_replacement_enabled")
    }

    val selectedModel: Flow<String> = context.dataStore.data.map { it[SELECTED_MODEL] ?: Constants.EXAMPLE_MODEL_ID }
    
    val providerBaseUrls: Flow<Map<String, String>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[PROVIDER_BASE_URLS] ?: "{}"
        try { json.decodeFromString<Map<String, String>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode providerBaseUrls", e); emptyMap() }
    }

    /** Per-provider custom HTTP headers (provider name -> {header key -> value}), for gateways/
     *  proxies that require extra auth headers beyond the standard Authorization/x-api-key.
     *  Encrypted like [shellDevices]/[mcpServers] since header values often carry tokens. */
    val providerCustomHeaders: Flow<Map<String, Map<String, String>>> = context.dataStore.data.map { pref ->
        val jsonStr = com.orangeisland.app.util.SecretCrypto.decrypt(pref[PROVIDER_CUSTOM_HEADERS_JSON] ?: "{}")
        try { json.decodeFromString<Map<String, Map<String, String>>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode providerCustomHeaders", e); emptyMap() }
    }

    /** Hosts the user has explicitly confirmed are OK to reach over cleartext HTTP,
     *  despite carrying API credentials. See [com.orangeisland.app.api.HttpClient.guardCleartextCredentials]. */
    val trustedHttpHosts: Flow<Set<String>> = context.dataStore.data.map { it[TRUSTED_HTTP_HOSTS] ?: emptySet() }

    /** Hosts for which the user has opted out of repeated cleartext HTTP reminder dialogs.
     *  Independent from [trustedHttpHosts] — a host can be trusted without silencing reminders,
     *  and vice-versa, but in practice silence is usually set together with trust. */
    val httpReminderSilencedHosts: Flow<Set<String>> = context.dataStore.data.map { it[HTTP_REMINDER_SILENCED_HOSTS] ?: emptySet() }

    val availableModels: Flow<Map<String, List<String>>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[AVAILABLE_MODELS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, List<String>>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode availableModels", e); emptyMap() }
    }

    val enabledModels: Flow<Set<String>> = context.dataStore.data.map { it[ENABLED_MODELS] ?: emptySet() }

    val modelAliases: Flow<Map<String, String>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[MODEL_ALIASES_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, String>>(jsonStr) } catch (e: Exception) { emptyMap() }
    }
    val modelContextLimits: Flow<Map<String, Int>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[MODEL_CONTEXT_LIMITS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, Int>>(jsonStr) } catch (e: Exception) { emptyMap() }
    }

    val apiKeys: Flow<List<ApiKeyEntry>> = context.dataStore.data.map { pref ->
        val jsonStr = com.orangeisland.app.util.SecretCrypto.decrypt(pref[API_KEYS_JSON] ?: "[]")
        try { json.decodeFromString<List<ApiKeyEntry>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    
    val activeApiKeyIds: Flow<Map<String, String>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[ACTIVE_API_KEY_IDS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, String>>(jsonStr) } catch (e: Exception) { emptyMap() }
    }

    val systemPrompts: Flow<List<SystemPromptEntry>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[SYSTEM_PROMPTS_JSON] ?: "[]"
        try { json.decodeFromString<List<SystemPromptEntry>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    
    val activeSystemPromptId: Flow<String?> = context.dataStore.data.map { it[ACTIVE_SYSTEM_PROMPT_ID] }

    val maxContextWindow: Flow<Int> = context.dataStore.data.map { it[MAX_CONTEXT_WINDOW]?.toIntOrNull() ?: 20 }
    val visualizeContextRollout: Flow<Boolean> = context.dataStore.data.map { it[VISUALIZE_CONTEXT_ROLLOUT] ?: false }
    val showMessageUsageStats: Flow<Boolean> = context.dataStore.data.map { it[SHOW_MESSAGE_USAGE_STATS] ?: false }
    val rememberLastConversation: Flow<Boolean> = context.dataStore.data.map { it[REMEMBER_LAST_CONVERSATION] ?: false }
    val lastActiveConversationId: Flow<String?> = context.dataStore.data.map { it[LAST_ACTIVE_CONVERSATION_ID] }
    val codeExecutionEnabled: Flow<Boolean> = context.dataStore.data.map { it[CODE_EXECUTION_ENABLED] ?: false }
    val workflowEnabled: Flow<Boolean> = context.dataStore.data.map { it[WORKFLOW_ENABLED] ?: false }
    val workflowMaxRunMs: Flow<Long> = context.dataStore.data.map { it[WORKFLOW_MAX_RUN_MS] ?: 300_000L }
    val workflowMaxToolCalls: Flow<Int> = context.dataStore.data.map { it[WORKFLOW_MAX_TOOL_CALLS] ?: 100 }
    val workflowConfirmDestructive: Flow<Boolean> = context.dataStore.data.map { it[WORKFLOW_CONFIRM_DESTRUCTIVE] ?: true }
    val workflowBackgroundSafeOnly: Flow<Boolean> = context.dataStore.data.map { it[WORKFLOW_BACKGROUND_SAFE_ONLY] ?: true }
    val googleSearchEnabled: Flow<Boolean> = context.dataStore.data.map { it[GOOGLE_SEARCH_ENABLED] ?: false }
    val thinkingEnabled: Flow<Boolean> = context.dataStore.data.map { it[THINKING_ENABLED] ?: true }
    val thinkingLevel: Flow<String> = context.dataStore.data.map { ThinkingLevels.normalize(it[THINKING_LEVEL]) }
    val thinkingBudgetEnabled: Flow<Boolean> = context.dataStore.data.map { pref ->
        pref[THINKING_BUDGET_ENABLED] ?: (ThinkingLevels.legacyBudgetTokens(pref[THINKING_LEVEL]) != null)
    }
    val thinkingBudgetTokens: Flow<Int> = context.dataStore.data.map { pref ->
        pref[THINKING_BUDGET_TOKENS]
            ?: ThinkingLevels.legacyBudgetTokens(pref[THINKING_LEVEL])
            ?: ThinkingLevels.DefaultBudgetTokens
    }

    val titleGenerationEnabled: Flow<Boolean> = context.dataStore.data.map { it[TITLE_GENERATION_ENABLED] ?: true }
    val titleGenerationModel: Flow<String?> = context.dataStore.data.map { it[TITLE_GENERATION_MODEL] }
    val titleGenerationPrompt: Flow<String> = context.dataStore.data.map { pref ->
        pref[TITLE_GENERATION_PROMPT]?.takeIf { it.isNotBlank() } ?: BuiltInPrompts.TITLE_GENERATION_SYSTEM
    }
    val autoCompressModel: Flow<String?> = context.dataStore.data.map { it[AUTO_COMPRESS_MODEL] }
    val autoCompressPrompt: Flow<String> = context.dataStore.data.map { pref ->
        pref[AUTO_COMPRESS_PROMPT]?.takeIf { it.isNotBlank() } ?: BuiltInPrompts.HISTORY_COMPRESSION_SYSTEM
    }
    val imageTranscriptionEnabledModels: Flow<Set<String>> = context.dataStore.data.map { it[IMAGE_TRANSCRIPTION_ENABLED_MODELS] ?: emptySet() }
    val imageTranscriptionModel: Flow<String?> = context.dataStore.data.map { it[IMAGE_TRANSCRIPTION_MODEL] }
    val imageTranscriptionBatchSize: Flow<Int> = context.dataStore.data.map { it[IMAGE_TRANSCRIPTION_BATCH_SIZE] ?: 3 }
    val imageTranscriptionPrompt: Flow<String> = context.dataStore.data.map { pref ->
        pref[IMAGE_TRANSCRIPTION_PROMPT]?.takeIf { it.isNotBlank() } ?: BuiltInPrompts.IMAGE_TRANSCRIPTION_USER
    }
    val videoNarrationEnabledModels: Flow<Set<String>> = context.dataStore.data.map { it[VIDEO_NARRATION_ENABLED_MODELS] ?: emptySet() }
    val videoNarrationModel: Flow<String?> = context.dataStore.data.map { it[VIDEO_NARRATION_MODEL] }
    val videoNarrationPrompt: Flow<String> = context.dataStore.data.map { pref ->
        pref[VIDEO_NARRATION_PROMPT]?.takeIf { it.isNotBlank() } ?: BuiltInPrompts.VIDEO_NARRATION_USER
    }
    val videoNarrationFps: Flow<Float> = context.dataStore.data.map { pref ->
        pref[VIDEO_NARRATION_FPS]?.toFloatOrNull()?.coerceIn(0.2f, 5f) ?: 1f
    }
    val videoNarrationDetail: Flow<String> = context.dataStore.data.map { pref ->
        pref[VIDEO_NARRATION_DETAIL]?.takeIf { it in setOf("low", "default", "high") } ?: "default"
    }
    val videoNarrationMaxLongSide: Flow<Int> = context.dataStore.data.map { pref ->
        pref[VIDEO_NARRATION_MAX_LONG_SIDE]?.coerceIn(1, 4096) ?: 1280
    }

    val accessPastConversations: Flow<Boolean> = context.dataStore.data.map { it[ACCESS_PAST_CONVERSATIONS] ?: true }
    val accessSavedMemories: Flow<Boolean> = context.dataStore.data.map { it[ACCESS_SAVED_MEMORIES] ?: true }
    val accessActiveMemory: Flow<Boolean> = context.dataStore.data.map { it[ACCESS_ACTIVE_MEMORY] ?: true }
    val ragSearchEnabled: Flow<Boolean> = context.dataStore.data.map { it[RAG_SEARCH_ENABLED] ?: false }
    val modelSearchMethod: Flow<String> = context.dataStore.data.map { it[MODEL_SEARCH_METHOD] ?: "keyword" }
    val manualSearchMethod: Flow<String> = context.dataStore.data.map { it[MANUAL_SEARCH_METHOD] ?: "keyword" }
    val embeddingModels: Flow<List<EmbeddingModelConfig>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[EMBEDDING_MODELS_JSON] ?: "[]"
        try { json.decodeFromString<List<EmbeddingModelConfig>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    val activeEmbeddingModelId: Flow<String> = context.dataStore.data.map { it[ACTIVE_EMBEDDING_MODEL_ID] ?: "" }

    val appLanguage: Flow<String> = context.dataStore.data.map { it[APP_LANGUAGE] ?: "system" }
    val webSearchEnabled: Flow<Boolean> = context.dataStore.data.map { it[WEB_SEARCH_ENABLED] ?: true }
    val webSearchProvider: Flow<String> = context.dataStore.data.map { it[WEB_SEARCH_PROVIDER] ?: "duckduckgo" }
    val webSearchApiKeys: Flow<Map<String, String>> = context.dataStore.data.map { pref ->
        val jsonStr = com.orangeisland.app.util.SecretCrypto.decrypt(pref[WEB_SEARCH_API_KEYS_JSON] ?: "{}")
        try { json.decodeFromString<Map<String, String>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode webSearchApiKeys", e); emptyMap() }
    }
    val webSearchNumResults: Flow<Int> = context.dataStore.data.map { it[WEB_SEARCH_NUM_RESULTS] ?: 5 }
    val webSearchBaseUrl: Flow<String> = context.dataStore.data.map { it[WEB_SEARCH_BASE_URL] ?: "" }

    // ── Image generation ──────────────────────────────────────
    val imageGenEnabled: Flow<Boolean> = context.dataStore.data.map { it[IMAGE_GEN_ENABLED] ?: false }
    // Selected image model "Provider:modelId" (null = none chosen). Creds reused from that provider.
    val imageGenModel: Flow<String?> = context.dataStore.data.map { it[IMAGE_GEN_MODEL] }
    val imageGenSize: Flow<String> = context.dataStore.data.map { it[IMAGE_GEN_SIZE] ?: "1024x1024" }
    val searchContextWindow: Flow<Int> = context.dataStore.data.map { it[SEARCH_CONTEXT_WINDOW] ?: 8 }
    val searchMatchLimit: Flow<Int> = context.dataStore.data.map { it[SEARCH_MATCH_LIMIT] ?: 10 }
    val ragThreshold: Flow<Float> = context.dataStore.data.map { it[RAG_THRESHOLD]?.toFloatOrNull() ?: 0.5f }
    val defaultTemperature: Flow<Float?> = context.dataStore.data.map { it[DEFAULT_TEMPERATURE]?.toFloatOrNull() }
    val defaultMaxTokens: Flow<Int?> = context.dataStore.data.map { it[DEFAULT_MAX_TOKENS] }
    val defaultTopP: Flow<Float?> = context.dataStore.data.map { it[DEFAULT_TOP_P]?.toFloatOrNull() }
    val defaultFrequencyPenalty: Flow<Float?> = context.dataStore.data.map { it[DEFAULT_FREQUENCY_PENALTY]?.toFloatOrNull() }
    val defaultPresencePenalty: Flow<Float?> = context.dataStore.data.map { it[DEFAULT_PRESENCE_PENALTY]?.toFloatOrNull() }
    val conversationSettings: Flow<Map<String, ConversationSettings>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[CONVERSATION_SETTINGS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, ConversationSettings>>(jsonStr) } catch (e: Exception) { emptyMap() }
    }
    val autoCacheEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_CACHE_ENABLED] ?: true }
    val autoUpdateCheck: Flow<Boolean> = context.dataStore.data.map { it[AUTO_UPDATE_CHECK] ?: true }
    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data.map { it[LAST_UPDATE_CHECK_TIME] ?: 0L }
    val localChatModels: Flow<List<LocalChatModelConfig>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[LOCAL_CHAT_MODELS_JSON] ?: "[]"
        try { json.decodeFromString<List<LocalChatModelConfig>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    val customProviders: Flow<List<CustomProviderConfig>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[CUSTOM_PROVIDERS_JSON] ?: "[]"
        try { json.decodeFromString<List<CustomProviderConfig>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    val manualModels: Flow<Map<String, List<String>>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[MANUAL_MODELS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, List<String>>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode manualModels", e); emptyMap() }
    }

    val showDocumentationFab: Flow<Boolean> = context.dataStore.data.map { it[SHOW_DOCUMENTATION_FAB] ?: true }

    val shellEnabled: Flow<Boolean> = context.dataStore.data.map { it[SHELL_ENABLED] ?: true }
    val proxyEnabled: Flow<Boolean> = context.dataStore.data.map { it[PROXY_ENABLED] ?: false }
    val proxyType: Flow<String> = context.dataStore.data.map { it[PROXY_TYPE] ?: "http" }
    val proxyHost: Flow<String> = context.dataStore.data.map { it[PROXY_HOST] ?: DEFAULT_PROXY_HOST }
    val proxyPort: Flow<String> = context.dataStore.data.map { it[PROXY_PORT] ?: DEFAULT_PROXY_PORT }
    val proxyUsername: Flow<String> = context.dataStore.data.map { it[PROXY_USERNAME] ?: "" }
    val proxyPassword: Flow<String> = context.dataStore.data.map { it[PROXY_PASSWORD] ?: "" }
    val proxyBypass: Flow<String> = context.dataStore.data.map { it[PROXY_BYPASS] ?: DEFAULT_PROXY_BYPASS }
    // Confirm before the model runs state-changing commands on remote shell servers. Default on.
    val shellConfirmEnabled: Flow<Boolean> = context.dataStore.data.map { it[SHELL_CONFIRM_ENABLED] ?: true }
    val shellDevices: Flow<List<ShellDeviceConfig>> = context.dataStore.data.map { pref ->
        val jsonStr = com.orangeisland.app.util.SecretCrypto.decrypt(pref[SHELL_DEVICES_JSON] ?: "[]")
        try { json.decodeFromString<List<ShellDeviceConfig>>(jsonStr) } catch (e: Exception) { emptyList() }
    }
    val sandboxEnabled: Flow<Boolean> = context.dataStore.data.map { it[SANDBOX_ENABLED] ?: false }

    // ── Device Access tools (all default off) ─────────────────
    val deviceInfoEnabled: Flow<Boolean> = context.dataStore.data.map { it[DEVICE_INFO_ENABLED] ?: false }
    val cameraToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[CAMERA_TOOL_ENABLED] ?: false }
    val locationEnabled: Flow<Boolean> = context.dataStore.data.map { it[LOCATION_ENABLED] ?: false }
    val calendarEnabled: Flow<Boolean> = context.dataStore.data.map { it[CALENDAR_ENABLED] ?: false }
    val notificationEnabled: Flow<Boolean> = context.dataStore.data.map { it[NOTIFICATION_ENABLED] ?: false }
    val usageStatsEnabled: Flow<Boolean> = context.dataStore.data.map { it[USAGE_STATS_ENABLED] ?: false }
    val navigationEnabled: Flow<Boolean> = context.dataStore.data.map { it[NAVIGATION_ENABLED] ?: false }
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[APP_LOCK_ENABLED] ?: false }
    val appLockEntries: Flow<Map<String, AppLockEntry>> = context.dataStore.data.map { pref ->
        val s = pref[APP_LOCK_ENTRIES_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, AppLockEntry>>(s) } catch (_: Exception) { emptyMap() }
    }
    val toastEnabled: Flow<Boolean> = context.dataStore.data.map { it[TOAST_ENABLED] ?: false }
    val petEnabled: Flow<Boolean> = context.dataStore.data.map { it[PET_ENABLED] ?: false }
    val petPosX: Flow<Int> = context.dataStore.data.map { it[PET_POS_X] ?: Int.MIN_VALUE }
    val petPosY: Flow<Int> = context.dataStore.data.map { it[PET_POS_Y] ?: Int.MIN_VALUE }
    val alarmEnabled: Flow<Boolean> = context.dataStore.data.map { it[ALARM_ENABLED] ?: false }
    val healthToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[HEALTH_TOOL_ENABLED] ?: false }
    val timeToolEnabled: Flow<Boolean> = context.dataStore.data.map { it[TIME_TOOL_ENABLED] ?: false }
    val uiAutomationEnabled: Flow<Boolean> = context.dataStore.data.map { it[UI_AUTOMATION_ENABLED] ?: false }
    val userInteractionEnabled: Flow<Boolean> = context.dataStore.data.map { it[USER_INTERACTION_ENABLED] ?: true }
    val mediaControlEnabled: Flow<Boolean> = context.dataStore.data.map { it[MEDIA_CONTROL_ENABLED] ?: false }
    val amapApiKey: Flow<String> = context.dataStore.data.map { it[AMAP_API_KEY] ?: "" }

    // ── MCP servers ──────────────────────────────────────────
    // Encrypted (headersJson commonly carries bearer tokens), same scheme as apiKeys/shellDevices.
    val mcpServers: Flow<List<McpServerConfig>> = context.dataStore.data.map { pref ->
        val jsonStr = com.orangeisland.app.util.SecretCrypto.decrypt(pref[MCP_SERVERS_JSON] ?: "[]")
        try { json.decodeFromString<List<McpServerConfig>>(jsonStr) } catch (e: Exception) { emptyList() }
    }

    // ── Plugins ──────────────────────────────────────────────
    // Plugin *files* live in filesDir/plugins/<id>/ (managed by PluginLoader); this stores only
    // the user's enable/disable preference (id set = enabled). Plugins installed but missing
    // here are treated as disabled by default.
    val enabledPluginIds: Flow<Set<String>> = context.dataStore.data.map { it[PLUGINS_ENABLED] ?: emptySet() }

    val themeMode: Flow<String> = context.dataStore.data.map { it[THEME_MODE] ?: "FOLLOW_DEVICE" }
    val colorScheme: Flow<String> = context.dataStore.data.map { it[COLOR_SCHEME] ?: "DEFAULT" }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[DYNAMIC_COLOR] ?: false }
    val blurEffectsEnabled: Flow<Boolean> = context.dataStore.data.map { it[BLUR_EFFECTS_ENABLED] ?: true }
    val codeBlockWrapEnabled: Flow<Boolean> = context.dataStore.data.map { it[CODE_BLOCK_WRAP_ENABLED] ?: false }
    val splitAssistantBubbleByLine: Flow<Boolean> = context.dataStore.data.map { it[SPLIT_ASSISTANT_BUBBLE_BY_LINE] ?: false }
    val nonStreamOutputEnabled: Flow<Boolean> = context.dataStore.data.map { it[NON_STREAM_OUTPUT_ENABLED] ?: false }
    val customColorChatText: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_CHAT_TEXT] }
    val customColorGlobalText: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_GLOBAL_TEXT] }
    val customColorUserBubble: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_USER_BUBBLE] }
    val customColorAssistantBubble: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_ASSISTANT_BUBBLE] }
    val customColorReasoningPanel: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_REASONING_PANEL] }
    val customColorChatBackground: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_CHAT_BACKGROUND] }
    val customColorAccent: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_ACCENT] }
    val customColorInputField: Flow<Long?> = context.dataStore.data.map { it[CUSTOM_COLOR_INPUT_FIELD] }
    val illustrationChatBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_CHAT_BACKGROUND_PATH] ?: "" }
    val illustrationInputBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_INPUT_BACKGROUND_PATH] ?: "" }
    val illustrationDrawerBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_DRAWER_BACKGROUND_PATH] ?: "" }
    val illustrationUserBubbleBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_USER_BUBBLE_BACKGROUND_PATH] ?: "" }
    val illustrationUserBubbleCornerRadius: Flow<Float> = context.dataStore.data.map { it[ILLUSTRATION_USER_BUBBLE_CORNER_RADIUS]?.toFloatOrNull() ?: 20f }
    val illustrationTopBarBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_TOPBAR_BACKGROUND_PATH] ?: "" }
    val illustrationReasoningBackgroundPath: Flow<String> = context.dataStore.data.map { it[ILLUSTRATION_REASONING_BACKGROUND_PATH] ?: "" }
    val transparencyTopBar: Flow<Float> = context.dataStore.data.map { it[TRANSPARENCY_TOPBAR]?.toFloatOrNull() ?: 1f }
    val topBarCapsuleScale: Flow<Float> = context.dataStore.data.map { it[TOPBAR_CAPSULE_SCALE]?.toFloatOrNull() ?: 1f }
    val transparencyMessageBubble: Flow<Float> = context.dataStore.data.map { it[TRANSPARENCY_MESSAGE_BUBBLE]?.toFloatOrNull() ?: 1f }
    val transparencyUserBubbleMask: Flow<Float> = context.dataStore.data.map { it[TRANSPARENCY_USER_BUBBLE_MASK]?.toFloatOrNull() ?: 0.55f }
    val transparencyReasoningPanel: Flow<Float> = context.dataStore.data.map { it[TRANSPARENCY_REASONING_PANEL]?.toFloatOrNull() ?: 1f }
    val transparencyDrawerItem: Flow<Float> = context.dataStore.data.map { it[TRANSPARENCY_DRAWER_ITEM]?.toFloatOrNull() ?: 1f }
    val recentCustomColors: Flow<List<Long>> = context.dataStore.data.map { pref ->
        (pref[RECENT_CUSTOM_COLORS] ?: "").split(",").mapNotNull { it.toLongOrNull() }
    }
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[HAPTICS_ENABLED] ?: true }
    val toolCallDisplayMode: Flow<String> = context.dataStore.data.map { ToolCallDisplayModes.normalize(it[TOOL_CALL_DISPLAY_MODE]) }
    val schemeStyle: Flow<String> = context.dataStore.data.map { it[SCHEME_STYLE] ?: "TONAL_SPOT" }
    val fontPreference: Flow<String> = context.dataStore.data.map { it[FONT_PREFERENCE] ?: "app_default" }
    val customFontPath: Flow<String> = context.dataStore.data.map { it[CUSTOM_FONT_PATH] ?: "" }
    val customFontName: Flow<String> = context.dataStore.data.map { it[CUSTOM_FONT_NAME] ?: "" }
    val fontSizeTier: Flow<String> = context.dataStore.data.map { it[FONT_SIZE_TIER] ?: FontSizeTiers.DEFAULT }
    val firstLaunchTime: Flow<Long?> = context.dataStore.data.map { it[FIRST_LAUNCH_TIME] }
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }
    val privacyPolicyAccepted: Flow<Boolean> = context.dataStore.data.map { it[PRIVACY_POLICY_ACCEPTED] ?: false }
    // ── Account / Auth (local session mirror) ───────────────────────────────
    val loggedIn: Flow<Boolean> = context.dataStore.data.map { it[LOGGED_IN] ?: false }
    val userName: Flow<String> = context.dataStore.data.map { it[USER_NAME] ?: "" }
    val userEmail: Flow<String> = context.dataStore.data.map { it[USER_EMAIL] ?: "" }
    val ratingPromptSubmitted: Flow<Boolean> = context.dataStore.data.map { it[RATING_PROMPT_SUBMITTED] ?: false }
    val ratingPromptDismissed: Flow<Boolean> = context.dataStore.data.map { it[RATING_PROMPT_DISMISSED] ?: false }
    val totalMessagesSent: Flow<Int> = context.dataStore.data.map { it[TOTAL_MESSAGES_SENT] ?: 0 }

    // ── Auto Backup ───────────────────────────────────────────
    val autoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_BACKUP_ENABLED] ?: true }
    val autoBackupPeriodHours: Flow<Int> = context.dataStore.data.map { it[AUTO_BACKUP_PERIOD_HOURS] ?: 24 }
    val autoBackupCategories: Flow<String> = context.dataStore.data.map { it[AUTO_BACKUP_CATEGORIES] ?: "conversations,memories,system_prompts,settings,workflows" }
    val autoBackupDirectory: Flow<String> = context.dataStore.data.map { it[AUTO_BACKUP_DIRECTORY] ?: "Download/OrangeIsland/Backup" }
    val autoDeleteEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTO_DELETE_ENABLED] ?: true }
    val autoDeletePeriodHours: Flow<Int> = context.dataStore.data.map { it[AUTO_DELETE_PERIOD_HOURS] ?: 168 }
    val lastBackupTimestamp: Flow<Long> = context.dataStore.data.map { it[LAST_BACKUP_TIMESTAMP] ?: 0L }
    val lastModelsFetchFingerprint: Flow<String> = context.dataStore.data.map { it[LAST_MODELS_FETCH_FINGERPRINT] ?: "" }

    // ── Health / Gadgetbridge / Sync ──────────────────────────
    val gadgetbridgeEnabled: Flow<Boolean> = context.dataStore.data.map { it[GADGETBRIDGE_ENABLED] ?: false }
    val gadgetbridgeDbPath: Flow<String> = context.dataStore.data.map { it[GADGETBRIDGE_DB_PATH] ?: "" }
    val healthSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[HEALTH_SYNC_ENABLED] ?: false }
    val healthSyncSupabaseUrl: Flow<String> = context.dataStore.data.map { it[HEALTH_SYNC_SUPABASE_URL] ?: "" }
    val healthSyncSupabaseApiKey: Flow<String> = context.dataStore.data.map { it[HEALTH_SYNC_SUPABASE_API_KEY] ?: "" }
    val healthSyncTableName: Flow<String> = context.dataStore.data.map { it[HEALTH_SYNC_TABLE_NAME] ?: "device_data" }
    val autoApproveSensitiveTools: Flow<Boolean> = context.dataStore.data.map { it[AUTO_APPROVE_SENSITIVE_TOOLS] ?: true }
    val environmentAwarenessEnabled: Flow<Boolean> = context.dataStore.data.map { it[ENVIRONMENT_AWARENESS_ENABLED] ?: false }
    val miniAppEntries: Flow<List<com.orangeisland.app.data.MiniAppEntry>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[MINI_APP_ENTRIES_JSON] ?: "[]"
        try { Json.decodeFromString(jsonStr) } catch (_: Exception) { emptyList() }
    }

    val anniversaries: Flow<List<AnniversaryEntry>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[ANNIVERSARIES_JSON] ?: "[]"
        try { json.decodeFromString<List<AnniversaryEntry>>(jsonStr) } catch (e: Exception) { DebugLog.e("SettingsManager", "Failed to decode anniversaries", e); emptyList() }
    }

    // ── Text-to-Speech ──────────────────────────────────────────
    val ttsEnabled: Flow<Boolean> = context.dataStore.data.map { it[TTS_ENABLED] ?: false }
    val ttsProvider: Flow<String> = context.dataStore.data.map { it[TTS_PROVIDER] ?: "elevenlabs" }
    val ttsApiKey: Flow<String> = context.dataStore.data.map { pref ->
        com.orangeisland.app.util.SecretCrypto.decrypt(pref[TTS_API_KEY] ?: "")
    }
    val ttsVoiceId: Flow<String> = context.dataStore.data.map { it[TTS_VOICE_ID] ?: "" }
    val ttsModel: Flow<String> = context.dataStore.data.map { it[TTS_MODEL] ?: "" }
    val ttsSpeed: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_SPEED]?.toFloatOrNull() ?: 1.0f }
    val ttsOutputFormat: Flow<String> = context.dataStore.data.map { it[TTS_OUTPUT_FORMAT] ?: "" }
    val ttsStability: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_STABILITY]?.toFloatOrNull() ?: 0.5f }
    val ttsSimilarityBoost: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_SIMILARITY_BOOST]?.toFloatOrNull() ?: 0.75f }
    val ttsStyle: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_STYLE]?.toFloatOrNull() ?: 0.0f }
    val ttsVolume: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_VOLUME]?.toFloatOrNull() ?: 1.0f }
    val ttsPitch: Flow<Float> = context.dataStore.data.map { pref -> pref[TTS_PITCH]?.toFloatOrNull() ?: 0.0f }

    // ── Speech-to-Text (SiliconFlow) ───────────────────────────
    val sttEnabled: Flow<Boolean> = context.dataStore.data.map { it[STT_ENABLED] ?: false }
    val sttApiKey: Flow<String> = context.dataStore.data.map { pref ->
        com.orangeisland.app.util.SecretCrypto.decrypt(pref[STT_API_KEY] ?: "")
    }
    val sttModel: Flow<String> = context.dataStore.data.map { it[STT_MODEL] ?: "" }
    val sttBaseUrl: Flow<String> = context.dataStore.data.map { it[STT_BASE_URL] ?: "" }

    // ── Music Studio (native music generation) ─────────────────
    val musicStudioEnabled: Flow<Boolean> = context.dataStore.data.map { it[MUSIC_STUDIO_ENABLED] ?: false }
    val musicStudioProvider: Flow<String> = context.dataStore.data.map { it[MUSIC_STUDIO_PROVIDER] ?: "suno" }
    val musicStudioSunoApiUrl: Flow<String> = context.dataStore.data.map { it[MUSIC_STUDIO_SUNO_API_URL] ?: "" }
    val musicStudioSunoApiKey: Flow<String> = context.dataStore.data.map { pref ->
        com.orangeisland.app.util.SecretCrypto.decrypt(pref[MUSIC_STUDIO_SUNO_API_KEY] ?: "")
    }
    val musicStudioReplicateApiKey: Flow<String> = context.dataStore.data.map { pref ->
        com.orangeisland.app.util.SecretCrypto.decrypt(pref[MUSIC_STUDIO_REPLICATE_API_KEY] ?: "")
    }
    val musicStudioReplicateModelVersion: Flow<String> = context.dataStore.data.map { it[MUSIC_STUDIO_REPLICATE_MODEL_VERSION] ?: "" }
    val musicStudioRvcModelUrl: Flow<String> = context.dataStore.data.map { it[MUSIC_STUDIO_RVC_MODEL_URL] ?: "" }
    val musicStudioVoiceReplacementEnabled: Flow<Boolean> = context.dataStore.data.map { it[MUSIC_STUDIO_VOICE_REPLACEMENT_ENABLED] ?: false }

    // ── Plugin device id ──────────────────────────────────────
    // Lazily minted per-install UUID, exposed to plugins as __OI_USER_ID. No setter: it is
    // generated once on first read and never changes for the lifetime of the install.
    val appUserId: Flow<String> = context.dataStore.data.map { pref ->
        pref[APP_USER_ID] ?: UUID.randomUUID().toString().also { id ->
            context.dataStore.edit { it[APP_USER_ID] = id }
        }
    }

    /** Per-plugin config maps keyed by plugin id. Missing plugin id = no config stored yet. */
    val pluginConfigs: Flow<Map<String, Map<String, String>>> = context.dataStore.data.map { pref ->
        val jsonStr = pref[PLUGIN_CONFIGS_JSON] ?: "{}"
        try { json.decodeFromString<Map<String, Map<String, String>>>(jsonStr) } catch (e: Exception) { emptyMap() }
    }

    suspend fun saveProviderBaseUrl(provider: String, url: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[PROVIDER_BASE_URLS] ?: "{}"
            val map = try { json.decodeFromString<MutableMap<String, String>>(current) } catch (e: Exception) { mutableMapOf() }
            map[provider] = url
            prefs[PROVIDER_BASE_URLS] = json.encodeToString(map)
        }
    }

    /** Replaces the full custom-header set for [provider] (empty map removes the entry entirely). */
    suspend fun saveProviderCustomHeaders(provider: String, headers: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val current = com.orangeisland.app.util.SecretCrypto.decrypt(prefs[PROVIDER_CUSTOM_HEADERS_JSON] ?: "{}")
            val map = try { json.decodeFromString<MutableMap<String, Map<String, String>>>(current) } catch (e: Exception) { mutableMapOf() }
            if (headers.isEmpty()) map.remove(provider) else map[provider] = headers
            prefs[PROVIDER_CUSTOM_HEADERS_JSON] = com.orangeisland.app.util.SecretCrypto.encrypt(json.encodeToString(map))
        }
    }

    suspend fun addTrustedHttpHost(host: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[TRUSTED_HTTP_HOSTS] ?: emptySet()
            prefs[TRUSTED_HTTP_HOSTS] = current + host.lowercase()
        }
    }

    suspend fun removeTrustedHttpHost(host: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[TRUSTED_HTTP_HOSTS] ?: emptySet()
            prefs[TRUSTED_HTTP_HOSTS] = current - host.lowercase()
        }
    }

    suspend fun setHttpReminderSilenced(host: String, silenced: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[HTTP_REMINDER_SILENCED_HOSTS] ?: emptySet()
            prefs[HTTP_REMINDER_SILENCED_HOSTS] = if (silenced) current + host.lowercase() else current - host.lowercase()
        }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { it[SELECTED_MODEL] = model }
    }

    suspend fun saveAvailableModels(provider: String, models: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[AVAILABLE_MODELS_JSON] ?: "{}"
            val map = try { json.decodeFromString<MutableMap<String, List<String>>>(current) } catch (e: Exception) { mutableMapOf() }
            map[provider] = models
            prefs[AVAILABLE_MODELS_JSON] = json.encodeToString(map)
        }
    }

    suspend fun saveEnabledModels(models: Set<String>) {
        context.dataStore.edit { it[ENABLED_MODELS] = models }
    }

    suspend fun saveModelAliases(aliases: Map<String, String>) {
        context.dataStore.edit { it[MODEL_ALIASES_JSON] = json.encodeToString(aliases) }
    }
    suspend fun saveModelContextLimits(limits: Map<String, Int>) {
        context.dataStore.edit { it[MODEL_CONTEXT_LIMITS_JSON] = json.encodeToString(limits) }
    }

    suspend fun saveApiKeys(keys: List<ApiKeyEntry>) {
        context.dataStore.edit { it[API_KEYS_JSON] = com.orangeisland.app.util.SecretCrypto.encrypt(json.encodeToString(keys)) }
    }

    suspend fun setActiveApiKeyId(provider: String, id: String?) {
        context.dataStore.edit { prefs ->
            val current = prefs[ACTIVE_API_KEY_IDS_JSON] ?: "{}"
            val map = try { json.decodeFromString<MutableMap<String, String>>(current) } catch (e: Exception) { mutableMapOf() }
            if (id == null) map.remove(provider) else map[provider] = id
            prefs[ACTIVE_API_KEY_IDS_JSON] = json.encodeToString(map)
        }
    }

    suspend fun saveSystemPrompts(prompts: List<SystemPromptEntry>) {
        context.dataStore.edit { it[SYSTEM_PROMPTS_JSON] = json.encodeToString(prompts) }
    }

    suspend fun initializeFirstInstallDefaults(
        locale: Locale = Locale.getDefault(),
        now: Long = System.currentTimeMillis()
    ) {
        context.dataStore.edit { prefs ->
            val firstLaunchMissing = prefs[FIRST_LAUNCH_TIME] == null
            val looksLikeFreshInstall = firstLaunchMissing && prefs[ONBOARDING_COMPLETED] != true
            if (firstLaunchMissing) {
                prefs[FIRST_LAUNCH_TIME] = now
            }
            val currentPrompts = try {
                json.decodeFromString<List<SystemPromptEntry>>(prefs[SYSTEM_PROMPTS_JSON] ?: "[]")
            } catch (_: Exception) {
                emptyList()
            }
            val migratedPrompts = migrateLegacyDefaultPromptTitle(currentPrompts, locale)
            if (migratedPrompts != currentPrompts) {
                prefs[SYSTEM_PROMPTS_JSON] = json.encodeToString(migratedPrompts)
            }
            if (looksLikeFreshInstall) {
                if (migratedPrompts.isEmpty()) {
                    val defaultPrompt = DefaultSystemPrompt.create(locale)
                    prefs[SYSTEM_PROMPTS_JSON] = json.encodeToString(listOf(defaultPrompt))
                    if (prefs[ACTIVE_SYSTEM_PROMPT_ID] == null) {
                        prefs[ACTIVE_SYSTEM_PROMPT_ID] = defaultPrompt.id
                    }
                }
            }
        }
    }

    private fun migrateLegacyDefaultPromptTitle(
        prompts: List<SystemPromptEntry>,
        locale: Locale
    ): List<SystemPromptEntry> {
        if (prompts.isEmpty()) return prompts
        val localizedTitle = DefaultSystemPrompt.titleForLocale(locale)
        val defaultPrompt = DefaultSystemPrompt.create(locale)
        return prompts.map { entry ->
            val legacyLowercaseEnglish = entry.title == "default"
            val legacySimplifiedTitleInTraditionalLocale =
                entry.title == "\u9ed8\u8ba4" && localizedTitle == "\u9810\u8a2d"
            if ((legacyLowercaseEnglish || legacySimplifiedTitleInTraditionalLocale) &&
                entry.sameTemplateAs(defaultPrompt)
            ) {
                entry.copy(title = localizedTitle)
            } else {
                entry
            }
        }
    }

    private fun SystemPromptEntry.sameTemplateAs(other: SystemPromptEntry): Boolean =
        resolvedSystemItems.sameTemplateItems(other.resolvedSystemItems) &&
            userPrependItems.sameTemplateItems(other.userPrependItems) &&
            userPostpendItems.sameTemplateItems(other.userPostpendItems)

    private fun List<PromptTemplateItem>.sameTemplateItems(other: List<PromptTemplateItem>): Boolean =
        size == other.size && zip(other).all { (left, right) ->
            left.type == right.type && left.value == right.value
        }

    suspend fun setActiveSystemPromptId(id: String?) {
        context.dataStore.edit { 
            if (id == null) it.remove(ACTIVE_SYSTEM_PROMPT_ID) else it[ACTIVE_SYSTEM_PROMPT_ID] = id 
        }
    }

    suspend fun saveMaxContextWindow(window: Int) {
        context.dataStore.edit { it[MAX_CONTEXT_WINDOW] = window.toString() }
    }

    suspend fun saveVisualizeContextRollout(enabled: Boolean) {
        context.dataStore.edit { it[VISUALIZE_CONTEXT_ROLLOUT] = enabled }
    }

    suspend fun saveShowMessageUsageStats(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_MESSAGE_USAGE_STATS] = enabled }
    }

    suspend fun saveRememberLastConversation(enabled: Boolean) {
        context.dataStore.edit { it[REMEMBER_LAST_CONVERSATION] = enabled }
    }

    suspend fun saveLastActiveConversationId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(LAST_ACTIVE_CONVERSATION_ID) else it[LAST_ACTIVE_CONVERSATION_ID] = id
        }
    }

    suspend fun saveCodeExecutionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CODE_EXECUTION_ENABLED] = enabled }
    }

    suspend fun saveWorkflowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WORKFLOW_ENABLED] = enabled }
    }

    suspend fun saveWorkflowMaxRunMs(ms: Long) {
        context.dataStore.edit { it[WORKFLOW_MAX_RUN_MS] = ms }
    }

    suspend fun saveWorkflowMaxToolCalls(count: Int) {
        context.dataStore.edit { it[WORKFLOW_MAX_TOOL_CALLS] = count }
    }

    suspend fun saveWorkflowConfirmDestructive(enabled: Boolean) {
        context.dataStore.edit { it[WORKFLOW_CONFIRM_DESTRUCTIVE] = enabled }
    }

    suspend fun saveWorkflowBackgroundSafeOnly(enabled: Boolean) {
        context.dataStore.edit { it[WORKFLOW_BACKGROUND_SAFE_ONLY] = enabled }
    }

    suspend fun saveGoogleSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[GOOGLE_SEARCH_ENABLED] = enabled }
    }

    suspend fun saveThinkingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[THINKING_ENABLED] = enabled }
    }

    suspend fun saveThinkingLevel(level: String) {
        context.dataStore.edit { it[THINKING_LEVEL] = ThinkingLevels.normalize(level) }
    }

    suspend fun saveThinkingBudgetEnabled(enabled: Boolean) {
        context.dataStore.edit { it[THINKING_BUDGET_ENABLED] = enabled }
    }

    suspend fun saveThinkingBudgetTokens(tokens: Int) {
        context.dataStore.edit { it[THINKING_BUDGET_TOKENS] = tokens.coerceAtLeast(1) }
    }

    suspend fun saveTitleGenerationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TITLE_GENERATION_ENABLED] = enabled }
    }

    suspend fun saveAccessPastConversations(enabled: Boolean) {
        context.dataStore.edit { it[ACCESS_PAST_CONVERSATIONS] = enabled }
    }

    suspend fun saveAccessSavedMemories(enabled: Boolean) {
        context.dataStore.edit { it[ACCESS_SAVED_MEMORIES] = enabled }
    }
    suspend fun saveAccessActiveMemory(enabled: Boolean) {
        context.dataStore.edit { it[ACCESS_ACTIVE_MEMORY] = enabled }
    }
    suspend fun saveRagSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[RAG_SEARCH_ENABLED] = enabled }
    }
    suspend fun saveAutoCacheEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_CACHE_ENABLED] = enabled }
    }
    suspend fun saveAutoUpdateCheck(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_UPDATE_CHECK] = enabled }
    }
    suspend fun saveLastUpdateCheckTime(time: Long) {
        context.dataStore.edit { it[LAST_UPDATE_CHECK_TIME] = time }
    }
    suspend fun saveModelSearchMethod(method: String) {
        context.dataStore.edit { it[MODEL_SEARCH_METHOD] = method }
    }
    suspend fun saveManualSearchMethod(method: String) {
        context.dataStore.edit { it[MANUAL_SEARCH_METHOD] = method }
    }
    suspend fun saveEmbeddingModels(models: List<EmbeddingModelConfig>) {
        context.dataStore.edit { it[EMBEDDING_MODELS_JSON] = json.encodeToString(models) }
    }
    suspend fun setActiveEmbeddingModelId(id: String) {
        context.dataStore.edit { it[ACTIVE_EMBEDDING_MODEL_ID] = id }
    }
    suspend fun saveAppLanguage(language: String) {
        context.dataStore.edit { it[APP_LANGUAGE] = language }
    }

    suspend fun saveWebSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WEB_SEARCH_ENABLED] = enabled }
    }

    suspend fun saveWebSearchProvider(provider: String) {
        context.dataStore.edit { it[WEB_SEARCH_PROVIDER] = provider }
    }

    suspend fun saveWebSearchApiKey(provider: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            val current = com.orangeisland.app.util.SecretCrypto.decrypt(prefs[WEB_SEARCH_API_KEYS_JSON] ?: "{}")
            val map = try { json.decodeFromString<MutableMap<String, String>>(current) } catch (e: Exception) { mutableMapOf() }
            if (apiKey.isBlank()) map.remove(provider) else map[provider] = apiKey
            prefs[WEB_SEARCH_API_KEYS_JSON] = com.orangeisland.app.util.SecretCrypto.encrypt(json.encodeToString(map))
        }
    }

    suspend fun saveWebSearchNumResults(n: Int) {
        context.dataStore.edit { it[WEB_SEARCH_NUM_RESULTS] = n.coerceIn(1, 10) }
    }
    suspend fun saveWebSearchBaseUrl(url: String) {
        context.dataStore.edit { it[WEB_SEARCH_BASE_URL] = url }
    }

    suspend fun saveImageGenEnabled(enabled: Boolean) {
        context.dataStore.edit { it[IMAGE_GEN_ENABLED] = enabled }
    }
    suspend fun saveImageGenModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(IMAGE_GEN_MODEL) else it[IMAGE_GEN_MODEL] = model
        }
    }
    suspend fun saveImageGenSize(size: String) {
        context.dataStore.edit { it[IMAGE_GEN_SIZE] = size }
    }
    suspend fun saveSearchMatchLimit(n: Int) {
        context.dataStore.edit { it[SEARCH_MATCH_LIMIT] = n }
    }
    suspend fun saveSearchContextWindow(n: Int) {
        context.dataStore.edit { it[SEARCH_CONTEXT_WINDOW] = n }
    }
    suspend fun saveRagThreshold(threshold: Float) {
        context.dataStore.edit { it[RAG_THRESHOLD] = threshold.toString() }
    }
    suspend fun saveDefaultTemperature(value: Float?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(DEFAULT_TEMPERATURE) else prefs[DEFAULT_TEMPERATURE] = value.toString()
        }
    }
    suspend fun saveDefaultMaxTokens(value: Int?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(DEFAULT_MAX_TOKENS) else prefs[DEFAULT_MAX_TOKENS] = value
        }
    }
    suspend fun saveDefaultTopP(value: Float?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(DEFAULT_TOP_P) else prefs[DEFAULT_TOP_P] = value.toString()
        }
    }
    suspend fun saveDefaultFrequencyPenalty(value: Float?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(DEFAULT_FREQUENCY_PENALTY) else prefs[DEFAULT_FREQUENCY_PENALTY] = value.toString()
        }
    }
    suspend fun saveDefaultPresencePenalty(value: Float?) {
        context.dataStore.edit { prefs ->
            if (value == null) prefs.remove(DEFAULT_PRESENCE_PENALTY) else prefs[DEFAULT_PRESENCE_PENALTY] = value.toString()
        }
    }
    suspend fun saveConversationSettings(conversationId: String, settings: ConversationSettings?) {
        context.dataStore.edit { prefs ->
            val current = prefs[CONVERSATION_SETTINGS_JSON] ?: "{}"
            val map = try { json.decodeFromString<MutableMap<String, ConversationSettings>>(current) } catch (e: Exception) { mutableMapOf() }
            if (settings == null || settings.isAllNull()) map.remove(conversationId)
            else map[conversationId] = settings
            prefs[CONVERSATION_SETTINGS_JSON] = json.encodeToString(map)
        }
    }

    suspend fun saveLocalChatModels(models: List<LocalChatModelConfig>) {
        context.dataStore.edit { it[LOCAL_CHAT_MODELS_JSON] = json.encodeToString(models) }
    }
    suspend fun saveCustomProviders(providers: List<CustomProviderConfig>) {
        context.dataStore.edit { it[CUSTOM_PROVIDERS_JSON] = json.encodeToString(providers) }
    }
    suspend fun saveManualModels(models: Map<String, List<String>>) {
        context.dataStore.edit { it[MANUAL_MODELS_JSON] = json.encodeToString(models) }
    }
    /** Replaces the manual model list for a single provider (empty list removes the entry). */
    suspend fun saveManualModelsForProvider(provider: String, models: List<String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[MANUAL_MODELS_JSON] ?: "{}"
            val map = try { json.decodeFromString<MutableMap<String, List<String>>>(current) } catch (e: Exception) { mutableMapOf() }
            if (models.isEmpty()) map.remove(provider) else map[provider] = models
            prefs[MANUAL_MODELS_JSON] = json.encodeToString(map)
        }
    }

    suspend fun saveTitleGenerationModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(TITLE_GENERATION_MODEL)
            else it[TITLE_GENERATION_MODEL] = model
        }
    }

    suspend fun saveTitleGenerationPrompt(prompt: String) {
        context.dataStore.edit {
            if (prompt.isBlank()) it.remove(TITLE_GENERATION_PROMPT)
            else it[TITLE_GENERATION_PROMPT] = prompt
        }
    }

    suspend fun saveAutoCompressModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(AUTO_COMPRESS_MODEL)
            else it[AUTO_COMPRESS_MODEL] = model
        }
    }
    suspend fun saveAutoCompressPrompt(prompt: String) {
        context.dataStore.edit {
            if (prompt.isBlank()) it.remove(AUTO_COMPRESS_PROMPT)
            else it[AUTO_COMPRESS_PROMPT] = prompt
        }
    }

    suspend fun saveImageTranscriptionEnabledModels(models: Set<String>) {
        context.dataStore.edit { it[IMAGE_TRANSCRIPTION_ENABLED_MODELS] = models }
    }

    suspend fun saveImageTranscriptionModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(IMAGE_TRANSCRIPTION_MODEL)
            else it[IMAGE_TRANSCRIPTION_MODEL] = model
        }
    }

    suspend fun saveImageTranscriptionBatchSize(size: Int) {
        context.dataStore.edit { it[IMAGE_TRANSCRIPTION_BATCH_SIZE] = size.coerceIn(1, 10) }
    }

    suspend fun saveImageTranscriptionPrompt(prompt: String) {
        context.dataStore.edit {
            if (prompt.isBlank()) it.remove(IMAGE_TRANSCRIPTION_PROMPT)
            else it[IMAGE_TRANSCRIPTION_PROMPT] = prompt
        }
    }

    suspend fun saveVideoNarrationEnabledModels(models: Set<String>) {
        context.dataStore.edit { it[VIDEO_NARRATION_ENABLED_MODELS] = models }
    }

    suspend fun saveVideoNarrationModel(model: String?) {
        context.dataStore.edit {
            if (model == null) it.remove(VIDEO_NARRATION_MODEL)
            else it[VIDEO_NARRATION_MODEL] = model
        }
    }

    suspend fun saveVideoNarrationPrompt(prompt: String) {
        context.dataStore.edit {
            if (prompt.isBlank()) it.remove(VIDEO_NARRATION_PROMPT)
            else it[VIDEO_NARRATION_PROMPT] = prompt
        }
    }

    suspend fun saveVideoNarrationFps(fps: Float) {
        context.dataStore.edit { it[VIDEO_NARRATION_FPS] = fps.coerceIn(0.2f, 5f).toString() }
    }

    suspend fun saveVideoNarrationDetail(detail: String) {
        context.dataStore.edit { it[VIDEO_NARRATION_DETAIL] = if (detail in setOf("low", "default", "high")) detail else "default" }
    }

    suspend fun saveVideoNarrationMaxLongSide(size: Int) {
        context.dataStore.edit { it[VIDEO_NARRATION_MAX_LONG_SIDE] = size.coerceIn(1, 4096) }
    }

    suspend fun saveShowDocumentationFab(enabled: Boolean) {
        context.dataStore.edit { it[SHOW_DOCUMENTATION_FAB] = enabled }
    }

    suspend fun saveShellEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SHELL_ENABLED] = enabled }
    }
    suspend fun saveProxyEnabled(enabled: Boolean) { context.dataStore.edit { it[PROXY_ENABLED] = enabled } }
    suspend fun saveProxyType(type: String) { context.dataStore.edit { it[PROXY_TYPE] = type } }
    suspend fun saveProxyHost(host: String) { context.dataStore.edit { it[PROXY_HOST] = host } }
    suspend fun saveProxyPort(port: String) { context.dataStore.edit { it[PROXY_PORT] = port } }
    suspend fun saveProxyUsername(user: String) { context.dataStore.edit { it[PROXY_USERNAME] = user } }
    suspend fun saveProxyPassword(pass: String) { context.dataStore.edit { it[PROXY_PASSWORD] = pass } }
    suspend fun saveProxyBypass(bypass: String) { context.dataStore.edit { it[PROXY_BYPASS] = bypass } }

    suspend fun saveShellConfirmEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SHELL_CONFIRM_ENABLED] = enabled }
    }

    suspend fun saveShellDevices(devices: List<ShellDeviceConfig>) {
        context.dataStore.edit { it[SHELL_DEVICES_JSON] = com.orangeisland.app.util.SecretCrypto.encrypt(json.encodeToString(devices)) }
    }

    suspend fun saveMcpServers(servers: List<McpServerConfig>) {
        context.dataStore.edit { it[MCP_SERVERS_JSON] = com.orangeisland.app.util.SecretCrypto.encrypt(json.encodeToString(servers)) }
    }

    suspend fun saveEnabledPluginIds(ids: Set<String>) {
        context.dataStore.edit { it[PLUGINS_ENABLED] = ids }
    }

    suspend fun saveSandboxEnabled(enabled: Boolean) {
        context.dataStore.edit { it[SANDBOX_ENABLED] = enabled }
    }

    // ── Device Access tools ───────────────────────────────────
    suspend fun saveDeviceInfoEnabled(enabled: Boolean) { context.dataStore.edit { it[DEVICE_INFO_ENABLED] = enabled } }
    suspend fun saveCameraToolEnabled(enabled: Boolean) { context.dataStore.edit { it[CAMERA_TOOL_ENABLED] = enabled } }
    suspend fun saveLocationEnabled(enabled: Boolean) { context.dataStore.edit { it[LOCATION_ENABLED] = enabled } }
    suspend fun saveCalendarEnabled(enabled: Boolean) { context.dataStore.edit { it[CALENDAR_ENABLED] = enabled } }
    suspend fun saveNotificationEnabled(enabled: Boolean) { context.dataStore.edit { it[NOTIFICATION_ENABLED] = enabled } }
    suspend fun saveUsageStatsEnabled(enabled: Boolean) { context.dataStore.edit { it[USAGE_STATS_ENABLED] = enabled } }
    suspend fun saveNavigationEnabled(enabled: Boolean) { context.dataStore.edit { it[NAVIGATION_ENABLED] = enabled } }
    suspend fun saveAppLockEnabled(enabled: Boolean) { context.dataStore.edit { it[APP_LOCK_ENABLED] = enabled } }
    suspend fun saveAppLockEntries(entries: Map<String, AppLockEntry>) {
        context.dataStore.edit { it[APP_LOCK_ENTRIES_JSON] = json.encodeToString(entries) }
    }
    suspend fun saveToastEnabled(enabled: Boolean) { context.dataStore.edit { it[TOAST_ENABLED] = enabled } }
    suspend fun savePetEnabled(enabled: Boolean) { context.dataStore.edit { it[PET_ENABLED] = enabled } }
    suspend fun savePetPos(x: Int, y: Int) {
        context.dataStore.edit { it[PET_POS_X] = x; it[PET_POS_Y] = y }
    }
    suspend fun saveAlarmEnabled(enabled: Boolean) { context.dataStore.edit { it[ALARM_ENABLED] = enabled } }
    suspend fun saveHealthToolEnabled(enabled: Boolean) { context.dataStore.edit { it[HEALTH_TOOL_ENABLED] = enabled } }
    suspend fun saveTimeToolEnabled(enabled: Boolean) { context.dataStore.edit { it[TIME_TOOL_ENABLED] = enabled } }
    suspend fun saveUiAutomationEnabled(enabled: Boolean) { context.dataStore.edit { it[UI_AUTOMATION_ENABLED] = enabled } }
    suspend fun saveUserInteractionEnabled(enabled: Boolean) { context.dataStore.edit { it[USER_INTERACTION_ENABLED] = enabled } }
    suspend fun saveMediaControlEnabled(enabled: Boolean) { context.dataStore.edit { it[MEDIA_CONTROL_ENABLED] = enabled } }
    suspend fun saveAmapApiKey(key: String) { context.dataStore.edit { it[AMAP_API_KEY] = key } }

    suspend fun saveThemeMode(mode: String) {
        context.dataStore.edit { it[THEME_MODE] = mode }
    }
    suspend fun saveColorScheme(scheme: String) {
        context.dataStore.edit { it[COLOR_SCHEME] = scheme }
    }
    suspend fun saveDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun saveBlurEffectsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BLUR_EFFECTS_ENABLED] = enabled }
    }

    suspend fun saveCodeBlockWrapEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CODE_BLOCK_WRAP_ENABLED] = enabled }
    }

    suspend fun saveSplitAssistantBubbleByLine(enabled: Boolean) {
        context.dataStore.edit { it[SPLIT_ASSISTANT_BUBBLE_BY_LINE] = enabled }
    }

    suspend fun saveNonStreamOutputEnabled(enabled: Boolean) {
        context.dataStore.edit { it[NON_STREAM_OUTPUT_ENABLED] = enabled }
    }

    suspend fun saveCustomColorChatText(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_CHAT_TEXT) else it[CUSTOM_COLOR_CHAT_TEXT] = value }
    suspend fun saveCustomColorGlobalText(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_GLOBAL_TEXT) else it[CUSTOM_COLOR_GLOBAL_TEXT] = value }
    suspend fun saveCustomColorUserBubble(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_USER_BUBBLE) else it[CUSTOM_COLOR_USER_BUBBLE] = value }
    suspend fun saveCustomColorAssistantBubble(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_ASSISTANT_BUBBLE) else it[CUSTOM_COLOR_ASSISTANT_BUBBLE] = value }
    suspend fun saveCustomColorReasoningPanel(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_REASONING_PANEL) else it[CUSTOM_COLOR_REASONING_PANEL] = value }
    suspend fun saveCustomColorChatBackground(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_CHAT_BACKGROUND) else it[CUSTOM_COLOR_CHAT_BACKGROUND] = value }
    suspend fun saveCustomColorAccent(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_ACCENT) else it[CUSTOM_COLOR_ACCENT] = value }
    suspend fun saveCustomColorInputField(value: Long?) = context.dataStore.edit { if (value == null) it.remove(CUSTOM_COLOR_INPUT_FIELD) else it[CUSTOM_COLOR_INPUT_FIELD] = value }
    suspend fun saveIllustrationChatBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_CHAT_BACKGROUND_PATH] = path }
    suspend fun saveIllustrationInputBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_INPUT_BACKGROUND_PATH] = path }
    suspend fun saveIllustrationDrawerBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_DRAWER_BACKGROUND_PATH] = path }
    suspend fun saveIllustrationUserBubbleBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_USER_BUBBLE_BACKGROUND_PATH] = path }
    suspend fun saveIllustrationUserBubbleCornerRadius(radius: Float) = context.dataStore.edit { it[ILLUSTRATION_USER_BUBBLE_CORNER_RADIUS] = radius.coerceIn(0f, 32f).toString() }
    suspend fun saveIllustrationTopBarBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_TOPBAR_BACKGROUND_PATH] = path }
    suspend fun saveIllustrationReasoningBackgroundPath(path: String) = context.dataStore.edit { it[ILLUSTRATION_REASONING_BACKGROUND_PATH] = path }
    suspend fun saveTransparencyTopBar(value: Float) = context.dataStore.edit { it[TRANSPARENCY_TOPBAR] = value.coerceIn(0f, 1f).toString() }
    suspend fun saveTopBarCapsuleScale(value: Float) = context.dataStore.edit { it[TOPBAR_CAPSULE_SCALE] = value.coerceIn(0.75f, 1.5f).toString() }

    suspend fun saveTransparencyMessageBubble(value: Float) = context.dataStore.edit { it[TRANSPARENCY_MESSAGE_BUBBLE] = value.coerceIn(0f, 1f).toString() }
    suspend fun saveTransparencyUserBubbleMask(value: Float) = context.dataStore.edit { it[TRANSPARENCY_USER_BUBBLE_MASK] = value.coerceIn(0f, 1f).toString() }
    suspend fun saveTransparencyReasoningPanel(value: Float) = context.dataStore.edit { it[TRANSPARENCY_REASONING_PANEL] = value.coerceIn(0f, 1f).toString() }
    suspend fun saveTransparencyDrawerItem(value: Float) = context.dataStore.edit { it[TRANSPARENCY_DRAWER_ITEM] = value.coerceIn(0f, 1f).toString() }

    suspend fun saveRecentCustomColors(colors: List<Long>) = context.dataStore.edit {
        it[RECENT_CUSTOM_COLORS] = colors.take(20).joinToString(",")
    }

    suspend fun saveHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS_ENABLED] = enabled }
    }

    suspend fun saveToolCallDisplayMode(mode: String) {
        context.dataStore.edit { it[TOOL_CALL_DISPLAY_MODE] = ToolCallDisplayModes.normalize(mode) }
    }

    suspend fun saveFontPreference(value: String) {
        context.dataStore.edit { it[FONT_PREFERENCE] = value }
    }
    suspend fun saveCustomFontPath(value: String) {
        context.dataStore.edit { it[CUSTOM_FONT_PATH] = value }
    }
    suspend fun saveCustomFontName(value: String) {
        context.dataStore.edit { it[CUSTOM_FONT_NAME] = value }
    }
    suspend fun setFontSizeTier(tier: String) {
        context.dataStore.edit { it[FONT_SIZE_TIER] = tier }
    }

    suspend fun saveSchemeStyle(style: String) {
        context.dataStore.edit { it[SCHEME_STYLE] = style }
    }

    suspend fun saveFirstLaunchTime(time: Long) {
        context.dataStore.edit { it[FIRST_LAUNCH_TIME] = time }
    }

    suspend fun saveOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun savePrivacyPolicyAccepted(accepted: Boolean) {
        context.dataStore.edit { it[PRIVACY_POLICY_ACCEPTED] = accepted }
    }

    // ── Account / Auth session persistence ───────────────────────────────────
    suspend fun saveAuthSession(loggedIn: Boolean, userName: String, userEmail: String) {
        context.dataStore.edit {
            it[LOGGED_IN] = loggedIn
            it[USER_NAME] = userName
            it[USER_EMAIL] = userEmail
        }
    }

    /** Wipe the local session mirror (logout). */
    suspend fun clearAuthSession() {
        context.dataStore.edit {
            it.remove(LOGGED_IN)
            it.remove(USER_NAME)
            it.remove(USER_EMAIL)
        }
    }

    // ── 一次性强制登出（用于踢掉旧版本用户）────────────────────────────────────
    // 修改 FORCE_LOGOUT_AT_VERSION 的值并发新版，所有 lastForceLogoutVersion
    // 不等于它的人启动时会被清空登录态、弹回登录页。
    private val FORCE_LOGOUT_AT_VERSION: Int = 2

    suspend fun runForceLogoutIfNeeded() {
        val last = context.dataStore.data.map { it[LAST_FORCE_LOGOUT_VERSION] ?: 0 }.first()
        if (last < FORCE_LOGOUT_AT_VERSION) {
            context.dataStore.edit {
                it.remove(LOGGED_IN)
                it.remove(USER_NAME)
                it.remove(USER_EMAIL)
                it[LAST_FORCE_LOGOUT_VERSION] = FORCE_LOGOUT_AT_VERSION
            }
        }
    }

    suspend fun saveRatingPromptSubmitted(submitted: Boolean) {
        context.dataStore.edit { it[RATING_PROMPT_SUBMITTED] = submitted }
    }

    suspend fun saveRatingPromptDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[RATING_PROMPT_DISMISSED] = dismissed }
    }

    suspend fun incrementMessagesSent() {
        context.dataStore.edit { it[TOTAL_MESSAGES_SENT] = (it[TOTAL_MESSAGES_SENT] ?: 0) + 1 }
    }

    // ── Auto Backup ───────────────────────────────────────────
    suspend fun saveAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_BACKUP_ENABLED] = enabled }
    }
    suspend fun saveAutoBackupPeriodHours(hours: Int) {
        context.dataStore.edit { it[AUTO_BACKUP_PERIOD_HOURS] = hours }
    }
    suspend fun saveAutoBackupCategories(categories: String) {
        context.dataStore.edit { it[AUTO_BACKUP_CATEGORIES] = categories }
    }
    suspend fun saveAutoBackupDirectory(path: String) {
        context.dataStore.edit { it[AUTO_BACKUP_DIRECTORY] = path }
    }
    suspend fun saveAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_DELETE_ENABLED] = enabled }
    }
    suspend fun saveAutoDeletePeriodHours(hours: Int) {
        context.dataStore.edit { it[AUTO_DELETE_PERIOD_HOURS] = hours }
    }
    suspend fun saveLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { it[LAST_BACKUP_TIMESTAMP] = timestamp }
    }

    suspend fun saveLastModelsFetchFingerprint(fingerprint: String) {
        context.dataStore.edit { it[LAST_MODELS_FETCH_FINGERPRINT] = fingerprint }
    }

    // ── Health / Gadgetbridge / Sync ──────────────────────────
    suspend fun saveGadgetbridgeEnabled(enabled: Boolean) { context.dataStore.edit { it[GADGETBRIDGE_ENABLED] = enabled } }
    suspend fun saveGadgetbridgeDbPath(path: String) { context.dataStore.edit { it[GADGETBRIDGE_DB_PATH] = path } }
    suspend fun saveHealthSyncEnabled(enabled: Boolean) { context.dataStore.edit { it[HEALTH_SYNC_ENABLED] = enabled } }
    suspend fun saveHealthSyncSupabaseUrl(url: String) { context.dataStore.edit { it[HEALTH_SYNC_SUPABASE_URL] = url } }
    suspend fun saveHealthSyncSupabaseApiKey(key: String) { context.dataStore.edit { it[HEALTH_SYNC_SUPABASE_API_KEY] = key } }
    suspend fun saveHealthSyncTableName(name: String) { context.dataStore.edit { it[HEALTH_SYNC_TABLE_NAME] = name } }
    suspend fun saveAutoApproveSensitiveTools(enabled: Boolean) { context.dataStore.edit { it[AUTO_APPROVE_SENSITIVE_TOOLS] = enabled } }
    suspend fun saveEnvironmentAwarenessEnabled(enabled: Boolean) { context.dataStore.edit { it[ENVIRONMENT_AWARENESS_ENABLED] = enabled } }
    suspend fun saveMiniAppEntries(entries: List<com.orangeisland.app.data.MiniAppEntry>) {
        context.dataStore.edit { it[MINI_APP_ENTRIES_JSON] = json.encodeToString(entries) }
    }

    suspend fun saveAnniversaries(entries: List<AnniversaryEntry>) {
        context.dataStore.edit { it[ANNIVERSARIES_JSON] = json.encodeToString(entries) }
    }

    /**
     * Stores the user-filled config values for one plugin. [values] is a map of field name → value;
     * pass an empty map (or use [clearPluginConfig]) to wipe the plugin's config. Field-level
     * validation (required/type) is the caller's responsibility — the store just persists what it's
     * given so a partially-filled draft can be saved too.
     */
    suspend fun savePluginConfig(pluginId: String, values: Map<String, String>) {
        context.dataStore.edit { prefs ->
            val current = prefs[PLUGIN_CONFIGS_JSON] ?: "{}"
            val map = try { Json.decodeFromString<MutableMap<String, Map<String, String>>>(current) } catch (e: Exception) { mutableMapOf() }
            if (values.isEmpty()) map.remove(pluginId) else map[pluginId] = values
            prefs[PLUGIN_CONFIGS_JSON] = json.encodeToString(map)
        }
    }

    // ── Text-to-Speech ──────────────────────────────────────────
    suspend fun saveTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TTS_ENABLED] = enabled }
    }
    suspend fun saveTtsProvider(provider: String) {
        context.dataStore.edit { it[TTS_PROVIDER] = provider }
    }
    suspend fun saveTtsApiKey(key: String) {
        context.dataStore.edit { it[TTS_API_KEY] = com.orangeisland.app.util.SecretCrypto.encrypt(key) }
    }
    suspend fun saveTtsVoiceId(voiceId: String) {
        context.dataStore.edit { it[TTS_VOICE_ID] = voiceId }
    }
    suspend fun saveTtsModel(model: String) {
        context.dataStore.edit { it[TTS_MODEL] = model }
    }
    suspend fun saveTtsSpeed(speed: Float) {
        context.dataStore.edit { it[TTS_SPEED] = speed.coerceIn(0.5f, 2.0f).toString() }
    }
    suspend fun saveTtsOutputFormat(format: String) {
        context.dataStore.edit { it[TTS_OUTPUT_FORMAT] = format }
    }
    suspend fun saveTtsStability(stability: Float) {
        context.dataStore.edit { it[TTS_STABILITY] = stability.coerceIn(0f, 1f).toString() }
    }
    suspend fun saveTtsSimilarityBoost(value: Float) {
        context.dataStore.edit { it[TTS_SIMILARITY_BOOST] = value.coerceIn(0f, 1f).toString() }
    }
    suspend fun saveTtsStyle(style: Float) {
        context.dataStore.edit { it[TTS_STYLE] = style.coerceIn(0f, 1f).toString() }
    }
    suspend fun saveTtsVolume(volume: Float) {
        context.dataStore.edit { it[TTS_VOLUME] = volume.coerceIn(0.1f, 10.0f).toString() }
    }
    suspend fun saveTtsPitch(pitch: Float) {
        context.dataStore.edit { it[TTS_PITCH] = pitch.coerceIn(-12f, 12f).toString() }
    }

    // ── Speech-to-Text (SiliconFlow) ───────────────────────────
    suspend fun saveSttEnabled(enabled: Boolean) {
        context.dataStore.edit { it[STT_ENABLED] = enabled }
    }
    suspend fun saveSttApiKey(key: String) {
        context.dataStore.edit { it[STT_API_KEY] = com.orangeisland.app.util.SecretCrypto.encrypt(key) }
    }
    suspend fun saveSttModel(model: String) {
        context.dataStore.edit { it[STT_MODEL] = model }
    }
    suspend fun saveSttBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[STT_BASE_URL] = baseUrl }
    }

    // ── Music Studio (native music generation) ─────────────────
    suspend fun saveMusicStudioEnabled(enabled: Boolean) { context.dataStore.edit { it[MUSIC_STUDIO_ENABLED] = enabled } }
    suspend fun saveMusicStudioProvider(provider: String) { context.dataStore.edit { it[MUSIC_STUDIO_PROVIDER] = provider } }
    suspend fun saveMusicStudioSunoApiUrl(url: String) { context.dataStore.edit { it[MUSIC_STUDIO_SUNO_API_URL] = url } }
    suspend fun saveMusicStudioSunoApiKey(key: String) { context.dataStore.edit { it[MUSIC_STUDIO_SUNO_API_KEY] = com.orangeisland.app.util.SecretCrypto.encrypt(key) } }
    suspend fun saveMusicStudioReplicateApiKey(key: String) { context.dataStore.edit { it[MUSIC_STUDIO_REPLICATE_API_KEY] = com.orangeisland.app.util.SecretCrypto.encrypt(key) } }
    suspend fun saveMusicStudioReplicateModelVersion(version: String) { context.dataStore.edit { it[MUSIC_STUDIO_REPLICATE_MODEL_VERSION] = version } }
    suspend fun saveMusicStudioRvcModelUrl(url: String) { context.dataStore.edit { it[MUSIC_STUDIO_RVC_MODEL_URL] = url } }
    suspend fun saveMusicStudioVoiceReplacementEnabled(enabled: Boolean) { context.dataStore.edit { it[MUSIC_STUDIO_VOICE_REPLACEMENT_ENABLED] = enabled } }
}
