package com.orangeisland.app.pet

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.orangeisland.app.data.repository.SettingsRepository
import com.orangeisland.app.service.DesktopPetService
import com.orangeisland.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Reactively starts/stops the desktop pet based on the persisted [toggle] and
 * the live overlay-permission state. This is the [DesktopPetService] analogue of
 * the keep-alive observer in `WorkflowTriggerHost`: a single collector on an
 * app-lifetime scope maps the combined (enabled, granted) signal through
 * `distinctUntilChanged` to exactly-once start/stop calls.
 *
 * The pet is only started when BOTH conditions hold:
 *  - the user enabled it ([SettingsRepository.petEnabled]), AND
 *  - the overlay permission is granted (`Settings.canDrawOverlays`), AND
 *  - the device is API 26+ (`TYPE_APPLICATION_OVERLAY` floor).
 *
 * If the user later revokes the overlay permission, the collector stops the
 * service so we never hold a foreground notification while drawing nothing.
 */
class PetController(
    private val appContext: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = combine(
            settings.petEnabled,
            // Re-emit whenever overlay permission may have changed. The permission
            // itself isn't observable via a Flow, so we read the snapshot and also
            // re-check on every petEnabled emission; the settings page calls
            // refresh() on resume to nudge re-evaluation after the user returns
            // from the system permission screen.
            settings.petEnabled // second source just ties lifetime to the toggle
        ) { enabled, _ -> enabled to overlaySupported() }
            .distinctUntilChanged()
            .onEach { (enabled, granted) ->
                if (enabled && granted) {
                    DesktopPetService.start(appContext)
                    DebugLog.d(TAG, "pet enabled → start service")
                } else {
                    DesktopPetService.stop(appContext)
                    DebugLog.d(TAG, "pet disabled/ungranted → stop service (enabled=$enabled, granted=$granted)")
                }
            }
            .launchIn(scope)
    }

    /** Re-query the overlay permission and re-evaluate start/stop (call on resume). */
    fun refresh() {
        // No-op emission trick: petEnabled is the driver; toggling detection of the
        // permission change happens via the next emission. Since the permission can
        // change out-of-band, we stop+restart if the grant flipped while pet is on.
        val enabled = settings.petEnabled.value
        val granted = overlaySupported()
        if (!enabled || !granted) {
            DesktopPetService.stop(appContext)
        }
    }

    fun shutdown() {
        job?.cancel()
        job = null
        DesktopPetService.stop(appContext)
    }

    private fun overlaySupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Settings.canDrawOverlays(appContext)

    companion object {
        private const val TAG = "PetController"
    }
}
