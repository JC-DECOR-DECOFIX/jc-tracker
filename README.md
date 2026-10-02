# JC Tracker

Rastreador de localização dos motoristas da **JC DECOR** enquanto uma rota de entrega está em andamento.

O app não é um sistema de logística. Não há pedidos, fotos, bipagem, mapa, navegação, ETA nem cadastro de motorista. O motorista toca em **INICIAR TRACKING** e o aparelho continua enviando a posição com a tela apagada, até ele tocar em **PARAR TRACKING**. O `route_id` fica vazio nesta versão e virá depois da integração com a Logística. Não é digitado na tela.

## Como rodar

1. Instale o [Android Studio](https://developer.android.com/studio) (versão recente estável) com Android SDK 35.
2. Abra esta pasta. O Android Studio faz o Gradle sync.
3. Copie a configuração de desenvolvimento:

```bash
cp local.properties.example local.properties
```

4. Ajuste `sdk.dir` para o SDK da sua máquina. No modo MOCK, deixe `API_TOKEN` vazio.
5. Rode o app num **aparelho Android físico** (o emulador não é o teste principal do GPS).

Requisitos desta versão: `minSdk` 26, `compileSdk`/`targetSdk` 35, JDK 17 ou superior (o projeto foi compilado com JDK 21).

## Modo MOCK (sem backend)

O padrão é `TRACKING_MODE=MOCK`. A coleta de GPS continua, o payload é escrito no log com a tag `JCTracker` e nenhum servidor é chamado. Sem rota, o log traz `route=null`. O heartbeat aparece como:

```text
tracking heartbeat session=<uuid> route=null
```

No Android Studio, filtre o Logcat por `JCTracker`. O APK MOCK é o que se instala no celular para validar o rastreamento antes da API existir.

## Modo REAL

No `local.properties` (não commitado):

```properties
TRACKING_MODE=REAL
API_BASE_URL=https://api.exemplo.jcdecor.com.br/
API_TOKEN=cole-o-token-aqui
```

`API_BASE_URL` precisa ser HTTPS e terminar com `/`. O token vai no header `Authorization: Bearer <token>` e **não é escrito em log**. Se o endpoint ainda não existir, o build não quebra: a posição válida fica no buffer Room e é reenviada depois.

As mesmas chaves estão documentadas em `.env.example`. O app lê só o `local.properties`, via `BuildConfig`.

Depois de mudar o `local.properties`, rode o Gradle sync de novo.

## Teste de GPS no aparelho

1. Instale o APK debug (comandos abaixo).
2. Conceda localização precisa e notificação.
3. Ative a localização do sistema.
4. Toque em **INICIAR TRACKING**. A tela mostra o status no mesmo lugar, sem abrir outra tela.
5. Confira a notificação persistente. Troque de app, apague a tela ou feche a activity: o rastreamento continua.
6. Acompanhe precisão, última localização, internet e pontos pendentes. Se precisar do detalhe técnico, toque em **DEBUG**.
7. Desligue a internet: a tela continua ativa e os pontos ficam pendentes. Religue: no MOCK eles saem do buffer; no REAL o POST só ocorre quando houver `route_id`.
8. Toque em **PARAR TRACKING**. A notificação some. Pontos que não confirmaram continuam no aparelho.

O aparelho precisa dos serviços do Google Play, porque a coleta usa o Fused Location Provider.

## Testes automatizados

```bash
./gradlew test
```

Os testes de JVM cobrem o filtro de precisão, a sequência, o buffer, a remoção depois do envio confirmado, a recuperação de sessão e o bloqueio de duas sessões ao mesmo tempo. Não há teste instrumentado de Room: isso exigiria aparelho ou emulador. A regra do buffer é testada numa implementação em memória com o mesmo contrato do Room.

## Gerar e instalar o APK

```bash
./gradlew assembleDebug
```

O APK fica em:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalação com o aparelho conectado e a depuração USB ligada:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O APK não entra no git. `local.properties` e o token também não.

Detalhes de arquitetura, permissões, buffer e protocolo: [JC_TRACKER_IMPLEMENTATION.md](JC_TRACKER_IMPLEMENTATION.md).
