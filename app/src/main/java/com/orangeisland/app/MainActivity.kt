package com.orangeisland.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.key
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.AccessibilityManager
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orangeisland.app.data.MemoryManager
import com.orangeisland.app.data.SettingsManager
import com.orangeisland.app.service.OrangeIslandForegroundService
import com.orangeisland.app.service.AppForegroundTracker
import com.orangeisland.app.data.local.ChatDatabase
import com.orangeisland.app.di.AppContainer
import com.orangeisland.app.ui.auth.AuthScreen
import com.orangeisland.app.ui.auth.AuthViewModel
import com.orangeisland.app.ui.chat.ChatApp
import com.orangeisland.app.ui.chat.FullScreenMediaViewer
import com.orangeisland.app.ui.settings.SettingsScreen
import com.orangeisland.app.ui.components.ColorMath
import com.orangeisland.app.ui.theme.FontSizeTiers
import com.orangeisland.app.ui.theme.OrangeIslandTheme
import com.orangeisland.app.util.CrashReporter
import com.orangeisland.app.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    /** Holds text received from an external app via SEND intent or deep-link.
     *  Consumed by the Composable layer once the ViewModel is ready. */
    private val externalTextState = mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: Context) {
        val langCode = kotlinx.coroutines.runBlocking {
            SettingsManager(newBase).appLanguage.first()
        }
        val locale = when (langCode) {
            "zh" -> java.util.Locale("zh", "CN")
            "en" -> java.util.Locale("en")
            "es" -> java.util.Locale("es")
            "fr" -> java.util.Locale("fr")
            "de" -> java.util.Locale("de")
            "ru" -> java.util.Locale("ru")
            "pt-BR" -> java.util.Locale("pt", "BR")
            "ja" -> java.util.Locale("ja")
            "ko" -> java.util.Locale("ko")
            "ar" -> java.util.Locale("ar")
            "zh-Hant" -> java.util.Locale.forLanguageTag("zh-Hant")
            else -> null
        }
        if (locale != null) {
            java.util.Locale.setDefault(locale)
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.setLocale(locale)
            super.attachBaseContext(newBase.createConfigurationContext(config))
        } else {
            super.attachBaseContext(newBase)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        com.orangeisland.app.util.DebugLog.init(this)
        OrangeIslandForegroundService.createChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        val storedVersion = ChatDatabase.getStoredVersion(this)
        val needsErrorDialog = storedVersion > ChatDatabase.CURRENT_VERSION

        val memoryManager = MemoryManager(applicationContext)
        val settingsManager = SettingsManager(applicationContext)
        runBlocking(Dispatchers.IO) {
            settingsManager.initializeFirstInstallDefaults(locale = java.util.Locale.getDefault())
        }

        // Parse external intent on cold start
        parseExternalIntent(intent)

        enableEdgeToEdge()
        // Remove navigation bar scrim so it blends with app content
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeMode by settingsManager.themeMode.collectAsState(initial = "FOLLOW_DEVICE")
            val colorSchemeName by settingsManager.colorScheme.collectAsState(initial = "DEFAULT")
            val schemeStyleName by settingsManager.schemeStyle.collectAsState(initial = "TONAL_SPOT")
            val dynamicColor by settingsManager.dynamicColor.collectAsState(initial = true)
            val fontPreference by settingsManager.fontPreference.collectAsState(initial = "app_default")
            val customFontPath by settingsManager.customFontPath.collectAsState(initial = "")
            val fontSizeTier by settingsManager.fontSizeTier.collectAsState(initial = FontSizeTiers.DEFAULT)
            val customGlobalTextColorArgb by settingsManager.customColorGlobalText.collectAsState(initial = null)
            val customGlobalTextColor = customGlobalTextColorArgb?.let { ColorMath.argbToColor(it) }

            val themeModeEnum = try { com.orangeisland.app.ui.theme.ThemeMode.valueOf(themeMode) } catch (_: Exception) { com.orangeisland.app.ui.theme.ThemeMode.FOLLOW_DEVICE }
            val colorSchemePreset = try { com.orangeisland.app.ui.theme.ColorSchemePreset.valueOf(colorSchemeName) } catch (_: Exception) { com.orangeisland.app.ui.theme.ColorSchemePreset.ORANGE_ISLAND }
            val schemeStyle = try { com.orangeisland.app.ui.theme.SchemeStyle.valueOf(schemeStyleName) } catch (_: Exception) { com.orangeisland.app.ui.theme.SchemeStyle.TONAL_SPOT }

            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeModeEnum) {
                com.orangeisland.app.ui.theme.ThemeMode.LIGHT -> false
                com.orangeisland.app.ui.theme.ThemeMode.DARK -> true
                com.orangeisland.app.ui.theme.ThemeMode.FOLLOW_DEVICE -> systemDark
            }

            SideEffect {
                val window = this@MainActivity.window
                val insetsController = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }

            OrangeIslandTheme(
                themeMode = themeModeEnum,
                colorSchemePreset = colorSchemePreset,
                schemeStyle = schemeStyle,
                dynamicColor = dynamicColor,
                fontPreference = fontPreference,
                customFontPath = customFontPath,
                fontScale = FontSizeTiers.scaleFor(fontSizeTier),
                customGlobalTextColor = customGlobalTextColor
            ) {
                val activity = LocalActivity.current

                if (needsErrorDialog) {
                    AlertDialog(
                        onDismissRequest = { activity?.finish() },
                        title = { Text(stringResource(R.string.database_incompatible), fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.database_incompatible_desc)) },
                        dismissButton = {
                            TextButton(onClick = { activity?.finish() }) { Text(stringResource(R.string.quit)) }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                applicationContext.deleteDatabase(ChatDatabase.DB_NAME)
                                activity?.recreate()
                            }) { Text(stringResource(R.string.clear_database)) }
                        }
                    )
                } else {
                    val privacyAccepted by settingsManager.privacyPolicyAccepted.collectAsState(initial = false)

                    if (!privacyAccepted) {
                        // Privacy policy gate — must accept before entering the app.
                        Dialog(
                            onDismissRequest = { },
                            properties = androidx.compose.ui.window.DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                                usePlatformDefaultWidth = false
                            )
                        ) {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                tonalElevation = 6.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .fillMaxHeight(0.85f)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    "用户协议与隐私声明",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "欢迎使用橘子岛。\n\n" +
                                    "1. 本应用使用设备本地存储保存您的对话记录与记忆内容，所有数据仅在您的设备上处理。\n" +
                                    "2. 调用第三方大模型 API 时，仅传输必要的对话内容，我们不会收集或存储您的个人信息。\n" +
                                    "3. 位置、通知、使用统计等敏感权限仅在您主动开启相关功能时申请，您可以随时在系统设置中撤回。\n" +
                                    "4. 本应用提供的自动化工作流、UI 操作等功能仅供个人辅助使用，因使用不当造成的任何后果由用户自行承担。\n" +
                                    "5. 未成年人应在监护人指导下使用本应用。\n\n" +
                                    "点击\"同意\"即表示您已阅读并同意以上内容。如不同意，请退出应用。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    TextButton(
                                        onClick = { activity?.finish() }
                                    ) {
                                        Text(
                                            "不同意并退出",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            kotlinx.coroutines.runBlocking {
                                                settingsManager.savePrivacyPolicyAccepted(true)
                                            }
                                        }
                                    ) {
                                        Text("同意")
                                    }
                                }
                            }
                        }
                        }
                    } else {
                        // Pull the app-lifetime container from the Application so the
                        // Supabase session + all shared singletons survive activity recreation.
                        val container = remember {
                            (this@MainActivity.application as com.orangeisland.app.OrangeIslandApplication).container
                        }
                        val factory = remember { container.chatViewModelFactory() }
                        val viewModel: ChatViewModel = viewModel(factory = factory)
                        val workflowViewModel = remember { container.workflowViewModel() }

                        // Auth gate: if not logged in, show the login/register screen
                        // instead of the main app. Once the flag flips, recomposition
                        // takes the user straight in — no restart needed.
                        val isLoggedIn by container.authRepository.isLoggedIn.collectAsState()

                        if (!isLoggedIn) {
                            val authViewModel: AuthViewModel = viewModel(
                                key = "authViewModel",
                                factory = viewModelFactory { initializer { AuthViewModel(container.authRepository) } }
                            )
                            AuthScreen(authViewModel)
                        } else {
                            // Onboarding flow disabled — mark it complete (so first-install
                            // defaults don't re-run on every launch) and go straight to the app.
                            LaunchedEffect(Unit) {
                                if (!settingsManager.onboardingCompleted.first()) {
                                    settingsManager.saveOnboardingCompleted(true)
                                }
                            }

                            MainNavigation(
                                viewModel,
                                settingsManager,
                                workflowViewModel,
                                container.pluginMemoryProvider
                            )

                            // Process external text (from SHARE intent or deep-link) once the UI is ready.
                            LaunchedEffect(externalTextState.value) {
                                externalTextState.value?.let { text ->
                                    viewModel.sendMessage(text)
                                    externalTextState.value = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseExternalIntent(intent)
    }

    /** Extract text from ACTION_SEND or orangeisland:// deep-link intents. */
    private fun parseExternalIntent(intent: Intent) {
        when {
            Intent.ACTION_SEND == intent.action && "text/plain" == intent.type -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!sharedText.isNullOrBlank()) {
                    externalTextState.value = sharedText
                }
            }
            Intent.ACTION_VIEW == intent.action -> {
                val data = intent.data
                if (data?.scheme == "orangeisland") {
                    val text = data.getQueryParameter("text") ?: ""
                    if (text.isNotBlank()) {
                        externalTextState.value = text
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppForegroundTracker.setInForeground(true)
        (application as? OrangeIslandApplication)?.container?.appContextCollector?.start()
    }

    override fun onPause() {
        super.onPause()
        AppForegroundTracker.setInForeground(false)
        (application as? OrangeIslandApplication)?.container?.appContextCollector?.stop()
    }
}

private const val SettingsOverlayScrimAlpha = 0.45f
private const val SettingsOverlayEnterOffsetFraction = 0.25f
private const val SettingsOverlayEnterScale = 0.92f
private const val SettingsOverlayExitScale = 0.94f
private const val SettingsOverlaySpringVisibilityThreshold = 0.001f

@Composable
private fun SettingsOverlayHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scrimAlpha = remember { Animatable(0f) }
    val pageOffsetFraction = remember { Animatable(0f) }
    val pageAlpha = remember { Animatable(1f) }
    val pageScale = remember { Animatable(1f) }
    var renderOverlay by remember { mutableStateOf(visible) }

    LaunchedEffect(visible) {
        if (visible) {
            renderOverlay = true
            scrimAlpha.snapTo(0f)
            pageOffsetFraction.snapTo(SettingsOverlayEnterOffsetFraction)
            pageAlpha.snapTo(0f)
            pageScale.snapTo(SettingsOverlayEnterScale)
            listOf(
                launch {
                    scrimAlpha.animateTo(
                        SettingsOverlayScrimAlpha,
                        animationSpec = tween(300, delayMillis = 50)
                    )
                },
                launch {
                    pageOffsetFraction.animateTo(
                        0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = SettingsOverlaySpringVisibilityThreshold
                        )
                    )
                },
                launch { pageAlpha.animateTo(1f, animationSpec = tween(300)) },
                launch {
                    pageScale.animateTo(
                        1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow,
                            visibilityThreshold = SettingsOverlaySpringVisibilityThreshold
                        )
                    )
                }
            ).joinAll()
        } else if (renderOverlay) {
            listOf(
                launch {
                    scrimAlpha.animateTo(
                        0f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                launch {
                    pageOffsetFraction.animateTo(
                        1f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                launch {
                    pageAlpha.animateTo(
                        0f,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                },
                launch {
                    pageScale.animateTo(
                        SettingsOverlayExitScale,
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    )
                }
            ).joinAll()
            renderOverlay = false
        }
    }

    if (!renderOverlay) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val pageOffsetX = (widthPx * pageOffsetFraction.value).roundToInt()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .background(Color.Black.copy(alpha = scrimAlpha.value.coerceIn(0f, SettingsOverlayScrimAlpha)))
                .pointerInput(onDismiss) {
                    detectTapGestures { onDismiss() }
                }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .offset { IntOffset(pageOffsetX, 0) }
                .alpha(pageAlpha.value.coerceIn(0f, 1f))
                .graphicsLayer {
                    scaleX = pageScale.value
                    scaleY = pageScale.value
                }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }

            if (!visible) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .consumePointerInput()
                )
            }
        }
    }
}

private fun Modifier.consumePointerInput(): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    viewModel: ChatViewModel,
    settingsManager: SettingsManager,
    workflowViewModel: com.orangeisland.app.viewmodel.WorkflowViewModel,
    memoryProvider: com.orangeisland.app.plugin.PluginMemoryProvider? = null,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var settingsInitialCategory by remember { mutableStateOf<String?>(null) }
    var showMiniAppList by rememberSaveable { mutableStateOf(false) }
    var selectedMiniApp by remember { mutableStateOf<com.orangeisland.app.data.MiniAppEntry?>(null) }
    var showHealthPage by rememberSaveable { mutableStateOf(false) }
    var showVoiceCall by rememberSaveable { mutableStateOf(false) }
    var fullScreenMediaUrls by remember { mutableStateOf<List<String>?>(null) }
    var fullScreenMediaIndex by remember { mutableIntStateOf(0) }
    var pdfViewerSelection by remember { mutableStateOf(setOf<Int>()) }
    val onTogglePdfSelection: (Int) -> Unit = { page ->
        pdfViewerSelection = if (page in pdfViewerSelection) pdfViewerSelection - page else pdfViewerSelection + page
    }
    val onInitPdfSelection: (Set<Int>) -> Unit = { selection ->
        pdfViewerSelection = selection
    }
    var pdfPreviewFromDialog by remember { mutableStateOf(false) }
    val hapticsEnabled by viewModel.settings.hapticsEnabled.collectAsState()
    val pdfPages by viewModel.previewPdfPages.collectAsState()
    val pdfIndex by viewModel.previewPdfIndex.collectAsState()
    var savedPdfPages by remember { mutableStateOf<List<String>>(emptyList()) }
    if (pdfPages.isNotEmpty()) { savedPdfPages = pdfPages } else { savedPdfPages = emptyList() }
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarVersion by remember { mutableIntStateOf(0) }
    val accessibilityManager = LocalAccessibilityManager.current
    var chatSnackbarOffset by remember { mutableStateOf(0.dp) }
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Full-screen media viewer (and settings) drop the snackbar to the bottom (nav-bar inset only);
    // in chat it floats above the bottom bar. The animateDpAsState below turns the change into a
    // rise/fall animation as the viewer opens/closes.
    val targetSnackbarPadding = if (showSettings || fullScreenMediaUrls != null) navBarPadding else chatSnackbarOffset
    val snackbarBottomPadding by animateDpAsState(
        targetValue = targetSnackbarPadding,
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 1000f),
        label = "snackbarPadding"
    )
    val focusManager = LocalFocusManager.current
    val ratingScope = rememberCoroutineScope()

    // Update dialog
    val updateDialogData by viewModel.updateDialogData.collectAsState()
    if (updateDialogData != null) {
        val info = updateDialogData!!
        val ctx = LocalContext.current
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            icon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = stringResource(R.string.about_update_available, info.version),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.about_available_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (info.body.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column {
                            // Lightweight markdown render of the release notes, kept on the
                            // shared type scale: '## ' → bold section label, '- ' → indented
                            // bullet, blank line → vertical gap, everything else → paragraph.
                            info.body.split("\n").forEach { line ->
                                when {
                                    line.startsWith("## ") -> Text(
                                        text = line.removePrefix("## "),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
                                    )
                                    line.startsWith("- ") -> Text(
                                        text = "•  ${line.removePrefix("- ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 3.dp, start = 2.dp)
                                    )
                                    line.isBlank() -> Spacer(modifier = Modifier.height(4.dp))
                                    else -> Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    viewModel.dismissUpdateDialog()
                }) { Text(stringResource(R.string.about_view_release)) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text(stringResource(R.string.about_later))
                }
            }
        )
    }

    // Remote shell action confirmation gate
    val pendingShellCommand by viewModel.pendingShellCommand.collectAsState()
    pendingShellCommand?.let { pending ->
        var alwaysAllow by remember(pending) { mutableStateOf(false) }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { viewModel.resolveShellConfirmation(allow = false) },
            icon = { Icon(Icons.Default.Terminal, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.shell_confirm_title, pending.server), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            pending.summary,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) { detectTapGestures { alwaysAllow = !alwaysAllow } }
                    ) {
                        Checkbox(checked = alwaysAllow, onCheckedChange = { alwaysAllow = it })
                        Text(stringResource(R.string.shell_confirm_always), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.resolveShellConfirmation(allow = true, alwaysAllowServer = alwaysAllow) }) {
                    Text(stringResource(R.string.shell_confirm_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.resolveShellConfirmation(allow = false) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.shell_confirm_deny)) }
            }
        )
    }

    // Crash report — opt-in, shown once on the first launch after an unexpected exit
    val crashContext = LocalContext.current
    var pendingCrash by remember { mutableStateOf<String?>(null) }
    val crashSubmittedMsg = stringResource(R.string.crash_submitted)
    LaunchedEffect(Unit) {
        pendingCrash = withContext(Dispatchers.IO) { CrashReporter.pendingReport(crashContext) }
    }
    pendingCrash?.let { report ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            onDismissRequest = { CrashReporter.clear(crashContext); pendingCrash = null },
            icon = { Icon(Icons.Default.BugReport, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.crash_title), fontWeight = FontWeight.Bold) },
            text = {
                val trace = runCatching { org.json.JSONObject(report).optString("trace", "") }.getOrDefault("")
                val clipboard = LocalClipboardManager.current
                Column {
                    Text(
                        stringResource(R.string.crash_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))
                    // Privacy reassurance as a distinct fine-print block, not just smaller text.
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                null,
                                modifier = Modifier.size(15.dp).padding(top = 1.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.crash_privacy_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (trace.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                stringResource(R.string.crash_log_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { clipboard.setText(AnnotatedString(trace)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    stringResource(R.string.copy),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = trace,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily(Font(R.font.jetbrains_mono_regular)),
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingCrash = null
                    CrashReporter.clear(crashContext)
                    ratingScope.launch {
                        val ok = withContext(Dispatchers.IO) { CrashReporter.submit(report) }
                        if (ok) {
                            try {
                                snackbarHostState.showSnackbar(crashSubmittedMsg)
                            } finally {
                                snackbarVersion++
                            }
                        }
                    }
                }) { Text(stringResource(R.string.crash_submit)) }
            },
            dismissButton = {
                TextButton(onClick = { CrashReporter.clear(crashContext); pendingCrash = null }) {
                    Text(stringResource(R.string.crash_dismiss))
                }
            }
        )
    }

    // Sandbox events piped into the same global SnackbarHost.
    // Uses a launch+Job pattern so a new message cancels the
    // previous showSnackbar suspension immediately.
    LaunchedEffect(Unit) {
        var snackbarJob: Job? = null
        viewModel.sandboxManager?.snackbarMessage?.collect { msg ->
            if (msg != null) {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarJob?.cancel()
                snackbarJob = launch {
                    try {
                        snackbarHostState.showSnackbar(msg)
                    } finally {
                        snackbarVersion++
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        var snackbarJob: Job? = null
        viewModel.snackbarMessage.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarJob?.cancel()
            snackbarJob = launch {
                try {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        duration = if (event.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        event.onAction?.invoke()
                    }
                } finally {
                    snackbarVersion++
                }
            }
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ChatApp(
                viewModel = viewModel,
                onOpenSettings = {
                    settingsInitialCategory = null
                    showSettings = true
                },
                onOpenMiniApp = {
                    showMiniAppList = true
                },
                onMediaClick = { urls, index ->
                    focusManager.clearFocus()
                    fullScreenMediaUrls = urls
                    fullScreenMediaIndex = index
                },
                onFileContentClick = { name, content ->
                    focusManager.clearFocus()
                    viewModel.showFilePreview(name, content)
                },
                onPdfPagesClick = { pages, idx ->
                    focusManager.clearFocus()
                    viewModel.showPdfPreview(pages, idx)
                    fullScreenMediaUrls = pages
                    fullScreenMediaIndex = idx
                    pdfPreviewFromDialog = false
                },
                onPdfPreviewSelect = { pages, idx ->
                    focusManager.clearFocus()
                    viewModel.showPdfPreview(pages, idx)
                    fullScreenMediaUrls = pages
                    fullScreenMediaIndex = idx
                    pdfPreviewFromDialog = true
                },
                pdfViewerSelection = pdfViewerSelection,
                onTogglePdfSelection = onTogglePdfSelection,
                onInitPdfSelection = onInitPdfSelection,
                fullScreenViewerUrls = fullScreenMediaUrls,
                onSnackbarOffsetChanged = { chatSnackbarOffset = it }
            )

            // Mini App list overlay
            if (showMiniAppList) {
                val miniAppEntries by viewModel.settings.miniAppEntries.collectAsState()
                com.orangeisland.app.ui.chat.MiniAppListPage(
                    entries = miniAppEntries,
                    onBack = { showMiniAppList = false },
                    onAdd = { entry ->
                        viewModel.settings.setMiniAppEntries(miniAppEntries + entry)
                    },
                    onDelete = { id ->
                        viewModel.settings.setMiniAppEntries(miniAppEntries.filter { it.id != id })
                    },
                    onOpen = { entry -> selectedMiniApp = entry }
                )
            }

            // Mini App browser overlay
            selectedMiniApp?.let { entry ->
                com.orangeisland.app.ui.chat.MiniAppPage(
                    name = entry.name,
                    url = entry.url,
                    onBack = { selectedMiniApp = null }
                )
            }

            SettingsOverlayHost(
                visible = showSettings,
                onDismiss = { showSettings = false }
            ) {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = {
                        settingsInitialCategory = null
                        showSettings = false
                    },
                    workflowViewModel = workflowViewModel,
                    initialCategory = settingsInitialCategory,
                    onEditWorkflowInChat = { prompt ->
                        // Prefill the chat input then close the settings overlay so the user lands
                        // in the chat ready to send (or edit) the "help me edit this workflow" text.
                        viewModel.setPendingPrefillInput(prompt)
                        settingsInitialCategory = null
                        showSettings = false
                    },
                    onOpenHealthPage = {
                        // Stack the health page ABOVE settings (the AnimatedVisibility is declared
                        // after SettingsOverlayHost, so it draws on top). Don't hide settings here —
                        // otherwise the health back button (which only flips showHealthPage) would
                        // reveal the chat instead of returning the user to this settings page.
                        showHealthPage = true
                    },
                    memoryProvider = memoryProvider
                )
            }

            // Workflow v2 approval card: pops when the model calls an AI authoring tool
            // (workflow_create / _update / _delete / _set_enabled). The gate suspends the tool
            // call until the user approves or rejects. Overlay so it appears above both chat
            // and settings.
            com.orangeisland.app.ui.chat.WorkflowApprovalDialog(viewModel.workflowApprovalGate)

            // User interaction card: pops when the model calls ask_user_choice.
            // Renders a card-style option list (single/multiple) and suspends until confirmed.
            com.orangeisland.app.ui.chat.UserInteractionDialog(viewModel.userInteractionGate)

            // Health data page
            AnimatedVisibility(
                visible = showHealthPage,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val ctx = LocalContext.current
                val container = remember {
                    (ctx.applicationContext as com.orangeisland.app.OrangeIslandApplication).container
                }
                val factory = remember { container.healthViewModelFactory() }
                val healthViewModel: com.orangeisland.app.ui.health.HealthViewModel = viewModel(factory = factory)
                com.orangeisland.app.ui.health.HealthPage(
                    viewModel = healthViewModel,
                    onBack = { showHealthPage = false }
                )
            }

            // ── AI 语音通话 (voice call) ─────────────────────────────────────────
            // Two stacked overlays, both gate-driven (no user-facing entry button):
            //  1. IncomingCallScreen — rings the user when the AI calls make_voice_call. Shown
            //     whenever the VoiceCallGate has a pending request.
            //  2. VoiceCallScreen — the actual call loop, shown only after the user answers.
            // The gate is the single shared instance from AppContainer (same one wired to the
            // ToolDispatcher's make_voice_call tool), pulled here from the Application.
            val voiceCtx = LocalContext.current
            val voiceContainer = remember {
                (voiceCtx.applicationContext as com.orangeisland.app.OrangeIslandApplication).container
            }
            val voiceCallGate = voiceContainer.voiceCallGate
            val incomingCall by voiceCallGate.pending.collectAsState()

            // Incoming-call ringing screen. Renders only while there is a pending request AND the
            // user hasn't answered into the call screen yet.
            if (incomingCall.isNotEmpty() && !showVoiceCall) {
                com.orangeisland.app.ui.voicecall.IncomingCallScreen(
                    gate = voiceCallGate,
                    onAnswer = { showVoiceCall = true }
                )
            }

            // The call itself (after answering). Mounts only while showVoiceCall is true; the
            // screen owns its VoiceCallViewModel via the container factory (Health-page pattern).
            AnimatedVisibility(
                visible = showVoiceCall,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                com.orangeisland.app.ui.voicecall.VoiceCallScreen(
                    viewModel = viewModel,
                    onBack = { showVoiceCall = false }
                )
            }

            // Full screen image preview
            AnimatedVisibility(
                visible = fullScreenMediaUrls != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                // Keep the last values for the duration of the exit animation
                var lastUrls by remember { mutableStateOf<List<String>?>(null) }
                var lastIndex by remember { mutableIntStateOf(0) }
                var lastPdfPages by remember { mutableStateOf<List<String>>(emptyList()) }
                var lastPdfTogglePage by remember { mutableStateOf<((Int) -> Unit)?>(null) }
                LaunchedEffect(fullScreenMediaUrls) {
                    if (fullScreenMediaUrls != null) {
                        lastUrls = fullScreenMediaUrls
                        lastIndex = fullScreenMediaIndex
                        lastPdfPages = savedPdfPages
                        lastPdfTogglePage = if (pdfPreviewFromDialog) onTogglePdfSelection else null
                    }
                }

                val urls = lastUrls ?: return@AnimatedVisibility
                FullScreenMediaViewer(
                    urls = urls,
                    initialIndex = lastIndex,
                    pdfPages = lastPdfPages,
                    pdfSelectedPages = if (lastPdfPages.isNotEmpty() && pdfPreviewFromDialog) pdfViewerSelection else null,
                    onTogglePdfPage = lastPdfTogglePage,
                    onClose = { viewModel.clearPreviews(); fullScreenMediaUrls = null; pdfPreviewFromDialog = false },
                    onNavigate = { idx -> fullScreenMediaIndex = idx },
                    onMessage = { viewModel.emitSnackbar(it) },
                    hapticsEnabled = hapticsEnabled
                )
            }

            // Text file viewer
            val fileContent by viewModel.previewFileContent.collectAsState()
            val fileName by viewModel.previewFileName.collectAsState()
            var savedContent by remember { mutableStateOf(fileContent) }
            var savedName by remember { mutableStateOf(fileName) }
            if (fileContent != null) { savedContent = fileContent; savedName = fileName }
            AnimatedVisibility(
                visible = fileContent != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (savedContent != null && savedName != null) {
                    com.orangeisland.app.ui.chat.TextFileViewer(content = savedContent!!, fileName = savedName!!, onClose = { viewModel.clearPreviews() })
                }
            }

            val current = snackbarHostState.currentSnackbarData
            var showing by remember { mutableStateOf(false) }
            var content by remember { mutableStateOf<SnackbarData?>(null) }

            LaunchedEffect(current, snackbarVersion) {
                if (current != null) {
                    if (showing) { showing = false; delay(200) }
                    content = current
                    showing = true
                } else {
                    showing = false
                    delay(400)
                    content = null
                }
            }

            LaunchedEffect(content, accessibilityManager) {
                val data = content ?: return@LaunchedEffect
                val timeoutMillis = snackbarTimeoutMillis(data.visuals, accessibilityManager)
                if (timeoutMillis != Long.MAX_VALUE) {
                    delay(timeoutMillis)
                    if (snackbarHostState.currentSnackbarData === data) {
                        data.dismiss()
                    }
                }
            }

            AnimatedVisibility(
                visible = showing,
                enter = fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.8f),
                exit = fadeOut(tween(400)) + scaleOut(tween(400), targetScale = 0.8f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = (snackbarBottomPadding + 2.dp).coerceAtLeast(0.dp))
            ) {
                content?.let { data ->
                    Snackbar(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(vertical = 10.dp).shadow(6.dp, RoundedCornerShape(12.dp), clip = false),
                        shape = RoundedCornerShape(12.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionContentColor = MaterialTheme.colorScheme.primary,
                        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        dismissAction = @Composable {
                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                IconButton(onClick = { data.dismiss() }, modifier = Modifier.size(28.dp).clip(CircleShape)) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cancel), modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        action = data.visuals.actionLabel?.let { label ->
                            @Composable { TextButton(onClick = { data.performAction() }) { Text(label) } }
                        },
                        content = { Text(data.visuals.message) }
                    )
                }
            }
        }
    }
}

private fun snackbarTimeoutMillis(
    visuals: SnackbarVisuals,
    accessibilityManager: AccessibilityManager?
): Long {
    val durationMillis = when (visuals.duration) {
        SnackbarDuration.Short -> 4000L
        SnackbarDuration.Long -> 10000L
        SnackbarDuration.Indefinite -> Long.MAX_VALUE
    }
    if (durationMillis == Long.MAX_VALUE) return durationMillis
    return accessibilityManager?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = durationMillis,
        containsIcons = true,
        containsText = true,
        containsControls = visuals.actionLabel != null
    ) ?: durationMillis
}
