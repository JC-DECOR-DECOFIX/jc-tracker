package br.com.jcdecor.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.jcdecor.tracker.tracking.SessionStatus
import br.com.jcdecor.tracker.util.Formats
import br.com.jcdecor.tracker.util.Iso8601

private val Ok = Color(0xFF1F6B62)
private val Warn = Color(0xFF8A5A00)
private val Bad = Color(0xFF8E2F2F)
private val Muted = Color(0xFF5C6B68)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    state: TrackerUiState,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onEndInterrupted: () -> Unit,
    onToggleDebug: () -> Unit,
    onCopyDebug: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("JC Tracker", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF123C3A),
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Status:", fontWeight = FontWeight.Medium)
            if (state.interrupted) {
                Text("Uma sessão de rastreamento foi interrompida.", fontWeight = FontWeight.Medium)
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("RETOMAR", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onEndInterrupted, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("ENCERRAR")
                }
            } else if (state.running) {
                ActiveStatus(state)
                Button(
                    onClick = onStop,
                    enabled = state.session?.status != SessionStatus.STOPPING,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Bad),
                ) {
                    Text("PARAR TRACKING", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dot(Muted)
                    Text("Rastreamento parado", modifier = Modifier.padding(start = 8.dp), fontSize = 18.sp)
                }
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("INICIAR TRACKING", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(onClick = onToggleDebug, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("DEBUG", fontWeight = FontWeight.Bold)
            }
            state.message?.let { Text(it, color = Bad, fontWeight = FontWeight.Medium) }
            if (!state.gpsAvailable && !state.running) {
                Text(
                    "GPS indisponível. Ative a localização para continuar o rastreamento.",
                    color = Warn,
                )
                OutlinedButton(onClick = onOpenLocationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("ABRIR CONFIGURAÇÕES")
                }
            }
            if (!state.hasFineLocation) {
                Text("Permissão de localização necessária.", color = Bad)
                OutlinedButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("ABRIR CONFIGURAÇÕES")
                }
            }
            if (state.showDebug) {
                DebugPanel(state, onCopyDebug, onOpenBatterySettings)
            }
        }
    }
}

@Composable
private fun ActiveStatus(state: TrackerUiState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(Ok)
        Text(
            "Rastreamento ativo",
            modifier = Modifier.padding(start = 8.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    val fix = state.lastFix
    if (fix == null) {
        Text("Precisão: —")
        Text("Última localização: aguardando")
    } else {
        Text(
            "Precisão: ± ${Formats.accuracy(fix.accuracyMeters)} m",
            color = if (fix.acceptable) Color.Unspecified else Warn,
        )
        Text(locationLine(fix.receivedAtEpochMs, state.nowEpochMs))
    }
    Text(if (state.internetAvailable) "Internet: Online" else "Internet: Offline")
    Text("Pontos pendentes: ${state.pendingCount}")
}

@Composable
private fun DebugPanel(
    state: TrackerUiState,
    onCopyDebug: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tracking", fontWeight = FontWeight.Bold)
            DebugLine("state", state.session?.status?.name ?: "IDLE")
            DebugLine("trackingSessionId", state.session?.trackingSessionId ?: "null")
            DebugLine("routeId", state.session?.routeId ?: "null")
            DebugLine("deviceId", state.session?.deviceId ?: "null")
            DebugLine("startedAt", state.session?.startedAtEpochMs?.let(Iso8601::formatUtc) ?: "null")
            DebugLine("lastLocationAt", state.session?.lastLocationAtEpochMs?.let(Iso8601::formatUtc) ?: "null")
            DebugLine("sequence", state.session?.sequence?.toString() ?: "0")

            Spacer(Modifier.height(8.dp))
            Text("GPS", fontWeight = FontWeight.Bold)
            val fix = state.lastFix
            DebugLine("latitude", fix?.latitude?.let(Formats::coordinate) ?: "null")
            DebugLine("longitude", fix?.longitude?.let(Formats::coordinate) ?: "null")
            DebugLine("accuracy", fix?.accuracyMeters?.let { "${Formats.accuracy(it)} m" } ?: "null")
            DebugLine("speed", fix?.speedMetersPerSecond?.toString() ?: "null")
            DebugLine("bearing", fix?.bearingDegrees?.toString() ?: "null")
            DebugLine("altitude", fix?.altitudeMeters?.toString() ?: "null")
            DebugLine("recordedAt", fix?.recordedAtEpochMs?.let(Iso8601::formatUtc) ?: "null")

            Spacer(Modifier.height(8.dp))
            Text("Service", fontWeight = FontWeight.Bold)
            DebugLine("foreground service", state.serviceActive.toString())
            DebugLine("location updates", state.locationUpdatesActive.toString())
            DebugLine("heartbeat", state.heartbeatActive.toString())
            DebugLine("app", if (state.appInForeground) "foreground" else "background")

            Spacer(Modifier.height(8.dp))
            Text("Network", fontWeight = FontWeight.Bold)
            DebugLine("link", if (state.internetAvailable) "online" else "offline")
            DebugLine("mode", state.trackingMode.ifBlank { "MOCK" })
            DebugLine("base URL", state.apiBaseUrl.ifBlank { "null" })
            DebugLine("último envio", state.lastUploadAtEpochMs?.let(Iso8601::formatUtc) ?: "null")
            DebugLine("último erro HTTP", state.lastHttpError ?: "null")
            DebugLine("pontos pendentes", state.pendingCount.toString())

            Spacer(Modifier.height(8.dp))
            Text("Battery", fontWeight = FontWeight.Bold)
            DebugLine("otimização ativa", state.batteryRestricted.toString())
            DebugLine("unrestricted", (!state.batteryRestricted).toString())
            if (state.batteryRestricted) {
                Text("Restrição de bateria pode interromper tracking em background.", color = Warn)
                OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                    Text("ABRIR CONFIGURAÇÕES DE BATERIA")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Storage", fontWeight = FontWeight.Bold)
            DebugLine("pending locations", state.pendingCount.toString())
            DebugLine("sessão persistida", if (state.session != null) "sim" else "não")

            Spacer(Modifier.height(8.dp))
            Button(onClick = onCopyDebug, modifier = Modifier.fillMaxWidth()) {
                Text("COPIAR DEBUG", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Text("$label: $value", fontSize = 14.sp)
}

private fun locationLine(atEpochMs: Long, nowEpochMs: Long): String {
    val seconds = ((nowEpochMs - atEpochMs) / 1000L).coerceAtLeast(0L)
    return when {
        seconds < 5 -> "Última localização: agora"
        seconds < 60 -> "Última localização: há ${seconds}s"
        else -> "Última localização: há ${seconds / 60} min"
    }
}

@Composable
private fun Dot(color: Color) {
    Spacer(
        Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}
