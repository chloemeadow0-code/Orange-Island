package com.orangeisland.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Documentation FAB disabled.
 *
 * Previously this opened https://docs.orangeisland.app/<locale>/<page> — the
 * upstream project's docs site. For the commercial fork we don't want any
 * outbound links to the upstream project, so this composable now renders
 * nothing. All call sites are kept as-is; they pass a `docPath` that is
 * simply ignored.
 *
 * To re-enable with your own docs site later, restore the original body and
 * point `baseUrl` at your domain.
 */
@Composable
fun DocumentationFab(docPath: String, modifier: Modifier = Modifier) {
    // No-op.
}
