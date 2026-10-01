package br.com.jcdecor.tracker.ui

import android.Manifest
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
import br.com.jcdecor.tracker.tracking.RouteId
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
                    onRouteChange = viewModel::onRouteChange,
                    onStart = { begin(PendingAction.START) },
                    onResume = { begin(PendingAction.RESUME) },
                    onStop = { TrackingIntents.stop(this) },
                    onEndInterrupted = viewModel::endInterrupted,
                    onOpenLocationSettings = {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    onOpenAppSettings = { openAppSettings() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshStatus()
    }

    private fun begin(action: PendingAction) {
        if (action == PendingAction.START) {
            val route = viewModel.ui.value.routeInput
            if (route.isBlank()) {
                viewModel.showMessage("Informe o identificador da rota.")
                return
            }
            if (RouteId.normalize(route) == null) {
                viewModel.showMessage("Use apenas letras, números, hífen ou sublinhado.")
                return
            }
        }
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
            PendingAction.START -> {
                val route = RouteId.normalize(viewModel.ui.value.routeInput) ?: return
                TrackingIntents.start(this, route)
            }
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

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            },
        )
    }

    private enum class PendingAction { NONE, START, RESUME }
}
