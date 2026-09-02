package com.orangeisland.app.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.orangeisland.app.R

/** Asset texts shipped only with builds that bundle the sandbox components. */
private val SANDBOX_LICENSE_ASSETS = listOf(
    "licenses/sandbox-notice.txt",
    "licenses/gpl-2.0.txt",   // PRoot (GPL-2.0+)
    "licenses/lgpl-3.0.txt",  // talloc (LGPL-3.0+); LGPLv3 incorporates GPLv3
    "licenses/gpl-3.0.txt",   // by reference, so its full text ships too
    "licenses/third-party-notices.txt"
)

/** True when this build bundles the sandbox components (fdroid flavor only). */
fun hasSandboxLicenseAssets(context: android.content.Context): Boolean {
    return try {
        SANDBOX_LICENSE_ASSETS.all { path ->
            runCatching { context.assets.open(path).close() }.isSuccess
        }
    } catch (_: Throwable) { false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSandboxLicensesPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val texts = remember {
        SANDBOX_LICENSE_ASSETS.mapNotNull { path ->
            runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_sandbox_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                stringResource(R.string.about_sandbox_licenses_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            texts.forEach { text ->
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
