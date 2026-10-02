package br.com.jcdecor.tracker.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.jcdecor.tracker.TrackerApplication
import br.com.jcdecor.tracker.tracking.TrackingIntents
import br.com.jcdecor.tracker.ui.theme.JCTrackerTheme
import br.com.jcdecor.tracker.util.LocationStatus
import br.com.jcdecor.tracker.util.PermissionStatus

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application as TrackerApplication)
    }

    private var pendingAction = PendingAction.NONE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshStatus()
        val action = pendingAction
        pendingAction = PendingAction.NONE
        if (action != PendingAction.NONE) {
            continueIfReady(action)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JCTrackerTheme {
                val state by viewModel.ui.collectAsStateWithLifecycle()
                TrackingScreen(
                    state = state,
                    onStart = { begin(PendingAction.START) },
                    onResume = { begin(PendingAction.RESUME) },
                    onStop = { TrackingIntents.stop(this) },
                    onEndInterrupted = viewModel::endInterrupted,
                    onToggleDebug = viewModel::toggleDebug,
                    onCopyDebug = { copyDebug() },
                    onOpenLocationSettings = {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onOpenAppSettings = { openAppSettings() },
                    onOpenBatterySettings = { openBatterySettings() },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setAppInForeground(true)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    override fun onStop() {
        viewModel.setAppInForeground(false)
        super.onStop()
    }

    private fun begin(action: PendingAction) {
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            pendingAction = action
            permissionLauncher.launch(missing)
            return
        }
        continueIfReady(action)
    }

    private fun continueIfReady(action: PendingAction) {
        if (!PermissionStatus.hasFineLocation(this)) {
            viewModel.showMessage("Permissão de localização necessária.")
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && !PermissionStatus.hasNotification(this)) {
            viewModel.showMessage("Permissão de notificação necessária para mostrar o rastreamento.")
            return
        }
        if (!LocationStatus.isEnabled(this)) {
            viewModel.showMessage("GPS indisponível. Ative a localização para continuar o rastreamento.")
            return
        }
        when (action) {
            PendingAction.START -> TrackingIntents.start(this)
            PendingAction.RESUME -> TrackingIntents.resume(this)
            PendingAction.NONE -> Unit
        }
    }

    private fun missingPermissions(): Array<String> {
        val missing = mutableListOf<String>()
        if (!PermissionStatus.hasFineLocation(this)) {
            missing += Manifest.permission.ACCESS_FINE_LOCATION
            missing += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 && !PermissionStatus.hasNotification(this)) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        return missing.toTypedArray()
    }

    private fun copyDebug() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("JC Tracker debug", viewModel.debugText()))
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            viewModel.showMessage("Não foi possível abrir as configurações de bateria.")
        }
    }

    private enum class PendingAction { NONE, START, RESUME }
}
