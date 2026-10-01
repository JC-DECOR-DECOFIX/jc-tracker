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
import androidx.compose.material3.OutlinedTextField
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
import br.com.jcdecor.tracker.util.RelativeTime

private val Ok = Color(0xFF1F6B62)
private val Warn = Color(0xFF8A5A00)
private val Bad = Color(0xFF8E2F2F)
private val Muted = Color(0xFF5C6B68)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    state: TrackerUiState,
    onRouteChange: (String) -> Unit,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onEndInterrupted: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
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
            when {
                state.interrupted -> InterruptedCard(
                    routeId = state.session?.routeId.orEmpty(),
                    pendingCount = state.pendingCount,
                    onResume = onResume,
                    onEnd = onEndInterrupted,
                )
                state.running -> ActiveCard(
                    state = state,
                    onStop = onStop,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                )
                else -> IdleCard(
                    state = state,
                    onRouteChange = onRouteChange,
                    onStart = onStart,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }
        }
    }
}

@Composable
private fun IdleCard(
    state: TrackerUiState,
    onRouteChange: (String) -> Unit,
    onStart: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Text(
        text = "Rastreamento de localização durante a rota de entrega.",
        color = Muted,
        fontSize = 15.sp,
    )
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = state.routeInput,
                onValueChange = onRouteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Rota") },
                placeholder = { Text("ex. 1234") },
                singleLine = true,
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("INICIAR RASTREAMENTO", fontWeight = FontWeight.Bold)
            }
            state.message?.let { Text(it, color = Bad, fontWeight = FontWeight.Medium) }
        }
    }
    StatusLine(if (state.gpsAvailable) "GPS disponível" else "GPS indisponível", state.gpsAvailable)
    StatusLine(if (state.internetAvailable) "Internet disponível" else "Internet indisponível", state.internetAvailable)
    if (!state.gpsAvailable) {
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
    if (state.pendingCount > 0) {
        Text("${state.pendingCount} pontos aguardando envio", color = Warn, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActiveCard(
    state: TrackerUiState,
    onStop: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val route = state.session?.routeId.orEmpty()
    val stopping = state.session?.status == SessionStatus.STOPPING
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(Ok)
                Text(
                    "RASTREAMENTO ATIVO",
                    color = Ok,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text("Rota #$route", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            val fix = state.lastFix
            if (fix == null) {
                Text("Aguardando primeira posição", color = Muted)
            } else if (!fix.acceptable) {
                Text(
                    "GPS com baixa precisão ± ${Formats.accuracy(fix.accuracyMeters)} m",
                    color = Warn,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text("Precisão ± ${Formats.accuracy(fix.accuracyMeters)} m")
            }
            Text(RelativeTime.updateLabel(fix?.receivedAtEpochMs ?: state.session?.lastLocationAtEpochMs, state.nowEpochMs))
            if (fix != null) {
                Metric("Latitude", Formats.coordinate(fix.latitude))
                Metric("Longitude", Formats.coordinate(fix.longitude))
                Metric("Velocidade", Formats.speedKmh(fix.speedMetersPerSecond))
            }
            if (!state.internetAvailable) {
                Spacer(Modifier.height(4.dp))
                Text("⚠ Sem internet", color = Warn, fontWeight = FontWeight.Bold)
                Text("${state.pendingCount} pontos aguardando envio", color = Warn)
            } else {
                Text("Pontos pendentes: ${state.pendingCount}")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStop,
                enabled = !stopping,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Bad),
            ) {
                Text("ENCERRAR RASTREAMENTO", fontWeight = FontWeight.Bold)
            }
        }
    }
    StatusLine(if (state.gpsAvailable) "GPS disponível" else "GPS indisponível", state.gpsAvailable)
    StatusLine(if (state.internetAvailable) "Internet disponível" else "Internet indisponível", state.internetAvailable)
    if (!state.gpsAvailable) {
        Text("GPS indisponível. Ative a localização para continuar o rastreamento.", color = Warn)
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
    if (!state.playServicesAvailable) {
        Text(
            "Serviços do Google Play indisponíveis. O rastreamento precisa deles para o GPS de alta precisão.",
            color = Bad,
        )
    }
}

@Composable
private fun InterruptedCard(
    routeId: String,
    pendingCount: Int,
    onResume: () -> Unit,
    onEnd: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EE))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Uma sessão de rastreamento da rota #$routeId foi interrompida.",
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            if (pendingCount > 0) {
                Text("$pendingCount pontos aguardando envio", color = Warn)
            }
            Button(
                onClick = onResume,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("RETOMAR", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("ENCERRAR")
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (ok) Ok else Bad)
        Text(text, modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Muted)
        Text(value, fontWeight = FontWeight.Medium)
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
