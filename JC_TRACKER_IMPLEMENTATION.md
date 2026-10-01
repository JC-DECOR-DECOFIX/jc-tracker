# JC Tracker — implementação da V1

App Android nativo (Kotlin, Jetpack Compose, um único módulo `:app`). Package `br.com.jcdecor.tracker`. Não há mapa, pedido, login complexo nem cadastro.

## 1. Arquitetura

Camadas pequenas, sem Clean Architecture:

```text
app/src/main/java/br/com/jcdecor/tracker/
  data/local        Room (pending_location) e DataStore (sessão, device id)
  data/remote       Retrofit, MOCK e REAL
  data/repository   TrackingRepository — único acesso da UI e do service ao motor
  location          LocationProvider e AndroidLocationProvider (Fused Location)
  tracking          TrackingEngine, TrackingForegroundService, sessão e estados
  ui                MainActivity, TrackingScreen, MainViewModel
  util              ISO-8601, formatação, status de GPS/rede/permissão
```

O `TrackingEngine` não importa Android. Ele decide qualidade, sequência, buffer e envio. O service só orquestra GPS, notificação, rede e ciclo de vida. A UI observa DataStore e um `StateFlow` em memória.

Estados da sessão: `IDLE`, `STARTING`, `TRACKING`, `DEGRADED`, `STOPPING`, `STOPPED`.

`DEGRADED` significa rastreamento ligado com GPS ausente, sem internet ou última posição acima de 50 m. Perder a rede não encerra a sessão.

## 2. Fluxo de tracking

1. O motorista digita o `route_id` (letras, números, hífen ou sublinhado).
2. O app exige localização precisa, GPS ligado e, no Android 13+, permissão de notificação.
3. Só então a activity chama `startForegroundService` em `TrackingForegroundService`.
4. O service promove a notificação na hora, grava a sessão (`STARTING` → `TRACKING`) e pede localização de alta precisão.
5. Cada leitura passa pelo filtro. Posição aceita ganha `sequence` (1, 2, 3… na sessão), entra no Room e segue para o envio.
6. A fila sai em ordem de `created_at` e `sequence`. Se o ponto mais antigo falha, os seguintes esperam.
7. Heartbeat a cada 30 s.
8. **ENCERRAR** para o GPS, tenta esvaziar a fila (no máximo 8 s), grava o horário de encerramento, tira a notificação e marca `STOPPED`.

Não existem duas sessões ativas. Um segundo início devolve a sessão que já está correndo.

## 3. Foreground service

`TrackingForegroundService` usa `foregroundServiceType="location"` e `stopWithTask="false"`.

Ele inicia o Fused Location Provider, valida a leitura no engine, grava o buffer, envia, atualiza a notificação, mantém o status, dispara o heartbeat e para de forma explícita. Não há tela dentro do service.

A notificação é contínua:

```text
JC Tracker
Rota #1234
Última posição há X segundos
```

O texto expandido também diz que o rastreamento da rota está ativo e que a localização está sendo compartilhada. A ação **ENCERRAR** manda um `PendingIntent` para o próprio service parar.

`START_STICKY`: se o processo morrer no meio do rastreamento, o sistema pode recriar o service e o GPS volta, porque a sessão ainda está `TRACKING` no DataStore. Isso não acontece depois de um reboot (ver seção 8).

## 4. Permissões

| Permissão | Uso |
| --- | --- |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Posição precisa. Sem a precisa, o rastreamento não começa. |
| `FOREGROUND_SERVICE` e `FOREGROUND_SERVICE_LOCATION` | Serviço em primeiro plano do tipo location (Android 14+). |
| `POST_NOTIFICATIONS` | Android 13+. A notificação não fica escondida. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Envio e o aviso de “sem internet”. |
| `RECEIVE_BOOT_COMPLETED` | Só marca a sessão como interrompida. |

Não pedimos `ACCESS_BACKGROUND_LOCATION`. Com o serviço de localização em primeiro plano, a coleta segue com a tela apagada enquanto a notificação está visível. Diferenças de versão ficam no código: notificação a partir da API 33, tipo do FGS a partir da API 29 e `startForeground` com tipo location.

## 5. Armazenamento offline

Tabela Room `pending_location`: `id`, `route_id`, `device_id`, `sequence`, `latitude`, `longitude`, `accuracy`, `speed`, `bearing`, `altitude`, `recorded_at`, `created_at`, `retry_count`.

Uma posição válida não é descartada se a rede caiu, o backend não respondeu, deu timeout ou voltou 5xx (4xx também fica na fila, para não perder o ponto). A linha só sai depois de resposta 2xx.

O reenvio usa backoff simples: 1 s, 2 s, 4 s… até 60 s, sem biblioteca extra. Durante o rastreamento a fila é drenada a cada 15 s e também quando a rede volta. Ao abrir o app, a fila também é tentada, mesmo sem sessão ativa.

Encerrar a sessão **não** apaga `pending_location`.

WorkManager não entra nesta V1. O serviço já drena a fila enquanto rastreia, e a abertura do app reenvia o que sobrou. Um agendador extra não muda o comportamento pedido e aumentaria a superfície do projeto.

Sessão e device id ficam no DataStore. O device id é um UUID criado na primeira execução. Não usamos IMEI nem número de telefone.

## 6. Protocolo HTTP

`POST /api/tracking/routes/{routeId}/locations`

```json
{
  "device_id": "uuid",
  "sequence": 21,
  "latitude": -23.5,
  "longitude": -46.6,
  "accuracy": 4.8,
  "speed": 1.2,
  "bearing": 90.0,
  "altitude": 760.0,
  "recorded_at": "2026-10-01T18:00:00.000Z"
}
```

`recorded_at` é ISO-8601 em UTC. `speed` vai em metros por segundo, como o Fused Location entrega; a tela mostra km/h. `bearing` e `altitude` podem ser `null`.

Header: `Authorization: Bearer <token>` e `Content-Type: application/json`. O token sai de `local.properties` → `BuildConfig.API_TOKEN`. O cliente nunca registra esse valor.

`API_BASE_URL` e `API_TOKEN` não são segredo de produção no repositório. Veja `local.properties.example` e `.env.example`.

## 7. Heartbeat

`POST /api/tracking/routes/{routeId}/heartbeat` a cada 30 s, com `device_id` e `recorded_at`.

No MOCK só há log: `tracking heartbeat route=1234`. No REAL a chamada existe; se o endpoint ainda não estiver no ar, a falha é registrada sem o token e o rastreamento continua. Heartbeat não entra na fila offline.

## 8. Comportamento em background

Com a sessão ativa, trocar de app, apagar a tela ou fechar a activity não para o service. Se só a UI morrer, o processo e o service seguem. Se a activity for recriada, ela lê a sessão e mostra **RASTREAMENTO ATIVO** e a rota.

Depois de um reboot o tracking **não** volta sozinho. `BOOT_COMPLETED` apenas marca a sessão como interrompida. Na próxima abertura:

```text
Uma sessão de rastreamento da rota #1234 foi interrompida.
[ RETOMAR ] [ ENCERRAR ]
```

**RETOMAR** passa de novo pela checagem de permissão e GPS e continua a mesma `sequence`. **ENCERRAR** grava `STOPPED` e ainda tenta enviar o que estiver pendente.

## 9. Comportamento sem internet

A sessão permanece ativa, em estado interno `DEGRADED`. A tela mostra rastreamento ativo, o aviso de sem internet e quantos pontos esperam envio. Cada posição aceita é inserida no Room com motivo `NETWORK`. Quando a rede volta, o envio recomeça pelo ponto mais antigo.

## 10. Limitações

- Sem mapa, navegação, ETA, pedido, foto ou login.
- O GPS de alta precisão depende dos serviços do Google Play no aparelho.
- Fabricantes que matam serviço em segundo plano podem interromper a coleta. A V1 não pede isenção de otimização de bateria.
- Leitura com precisão pior que 50 m aparece na tela e não é enviada como posição válida.
- Leitura com mais de 30 s, ou com relógio mais de 5 s à frente, é descartada.
- Deslocamento menor que 10 m só gera novo envio se a última posição aceita tiver mais de 20 s. Isso evita rajada com o veículo parado; o heartbeat cobre o “ainda estou rastreando”.
- O teste instrumentado do Room não roda neste ambiente, porque não há emulador. As regras equivalentes estão nos testes JVM.
- `speed` no JSON é m/s. Se o backend quiser km/h, isso precisa ser combinado antes de ligar o modo REAL.

## 11. O que depende do backend real

- O endpoint de localização responder 2xx para a linha sair da fila.
- O endpoint de heartbeat pode ainda não existir. O app tolera a falha.
- O formato do token Bearer e a base HTTPS.
- Confirmar se `speed` deve permanecer em m/s.
- Não há refresh de token nem login. O token é configuração local até a autenticação real entrar no mesmo ponto (`ApiAuth`).

Constantes fáceis de mudar ficam em `TrackingConfig`: intervalo de 5 s, distância mínima de 10 m, precisão máxima de 50 m, heartbeat de 30 s.

## 12. Como gerar e instalar o APK

Ambiente usado para gerar este APK: JDK 21, Android SDK 35, Build-Tools 35.0.0, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Gradle 8.11.1. O `sdkmanager` avisou que o XML do SDK está numa versão mais nova do que a ferramenta entende; o `assembleDebug` mesmo assim concluiu.

```bash
cp local.properties.example local.properties
# edite sdk.dir; para teste sem servidor deixe TRACKING_MODE=MOCK

./gradlew test
./gradlew assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Não commitar o APK, o `local.properties` nem um token real.

### Logs (tag `JCTracker`)

```text
TRACKING_STARTED route=1234
LOCATION_RECEIVED accuracy=4.8
LOCATION_ACCEPTED seq=21
LOCATION_BUFFERED seq=21 reason=NETWORK
LOCATION_SENT seq=21
LOCATION_REJECTED accuracy=132
TRACKING_STOPPED route=1234
tracking heartbeat route=1234
```

No MOCK, o payload completo da posição também é logado, sem o token.
