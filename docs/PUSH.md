# Notificaciones instantáneas (push)

Gmail no avisa a un móvil por sí solo. El camino que usa Cemail (coste cero, sin facturación):

```
Gmail ──watch──▶ Pub/Sub (tema gmail-push) ──push HTTPS──▶ Worker de Cloudflare ──FCM──▶ móvil ──▶ sincroniza
```

1. La app llama a `users.watch` de Gmail con el tema `projects/cemail-514220/topics/gmail-push` (etiqueta INBOX). Dura unos 7 días, así que `PushRenewWorker` la renueva **cada día** (y al abrir la app).
2. Gmail publica `{emailAddress, historyId}` en el tema (la cuenta de servicio `gmail-api-push@system.gserviceaccount.com` tiene el rol *Publicador de Pub/Sub*).
3. La suscripción `gmail-push-sub` (tipo push) llama a `POST /pubsub/<PUBSUB_SECRET>` del Worker.
4. El Worker (`push-worker/worker.js`) busca en KV los tokens de FCM de esa dirección y manda un mensaje de datos de prioridad alta.
5. `PushService` lo recibe y hace una sincronización rápida (`refresh(quick = true)`) de esa cuenta; los avisos de correo nuevo salen por el camino de siempre (`MailNotifier`).

El móvil se da de alta con `POST /register` (cabecera `X-Cemail-Key`) enviando su token y las direcciones de Gmail y Microsoft. Si se quita una cuenta o se apaga el interruptor, la app lo da de baja y llama a `users.stop`.

## Microsoft (Outlook, Hotmail…)
```
Graph ──notificación de cambio──▶ Worker (POST /graph) ──FCM──▶ móvil ──▶ sincroniza
```
1. La app crea una suscripción en `POST https://graph.microsoft.com/v1.0/subscriptions` (recurso `me/mailFolders('inbox')/messages`, tipo `created`, `notificationUrl` = `<PUSH_URL>/graph`, un `clientState` aleatorio por suscripción). Funciona con cuentas personales (probado con outlook.es).
2. Microsoft valida la dirección llamando a `/graph?validationToken=…`; el Worker devuelve el token como texto plano.
3. La app avisa al Worker con `POST /register-ms` (`subscriptionId`, `email`, `clientState`); queda en KV como `ms:<id>` con caducidad de 4 días.
4. Cuando llega un correo, Microsoft llama a `POST /graph`; el Worker comprueba el `clientState`, responde 202 y manda el mensaje FCM `type=microsoft`. `PushService` hace la sincronización rápida de esa cuenta.
5. Las suscripciones de correo duran menos de 3 días (la app pide 4200 min): `PushRenewWorker` las renueva cada día (PATCH) y las crea de nuevo si caducaron (404). Al apagar el interruptor se borran.

Medido: ~6 s entre el envío y la sincronización en el móvil.

## Piezas y dónde viven
| Pieza | Dónde |
|---|---|
| Proyecto de Google Cloud / Firebase | `cemail-514220` (plan Spark, sin Analytics) |
| Worker + KV | cuenta de Cloudflare del autor; código en `push-worker/`; se despliega con `wrangler` |
| Secretos del Worker | `APP_KEY`, `PUBSUB_SECRET`, `FCM_SA` (Cloudflare), copias locales ignoradas por git en `push-worker/` |
| Claves en la app | `local.properties`: `cemail.firebase.*`, `cemail.push.url`, `cemail.push.appKey` (llegan por `BuildConfig`) |

Sin esas claves la app funciona igual, con comprobaciones cada 15 min (`Push.available` es falso).

## Desplegar el Worker
Requiere Node.js y `wrangler login`. Desde una carpeta con nombre neutro (el subdominio `workers.dev` se saca del nombre de la carpeta o del correo):

```bash
cd push-worker
npx wrangler deploy
npx wrangler secret put APP_KEY            # el mismo valor que cemail.push.appKey
npx wrangler secret put PUBSUB_SECRET      # parte final de la URL de la suscripción
npx wrangler secret put FCM_SA < fcm-service-account.local.json
```

## Limitaciones
- Gmail y Microsoft. IMAP no tiene push sin una conexión permanente: queda para un posible servicio IDLE más adelante.
- Necesita Google Play Services en el móvil.
- El Worker ve la dirección y el token, nunca el contenido del correo (ver la política de privacidad).

## Calendario de Celander (rutas `/cal/*`, `/gcal`)
Mismo Worker, prefijos de KV `cal:` y secreto propio `CAL_APP_KEY` (cabecera `X-Celander-Key`); el push de correo no cambia.
- `POST /cal/register` `{token, accounts:[{email, provider:"google"|"microsoft", channels:[{id,token}] | subscription:{id,clientState}}], previous?:[email]}`; `POST /cal/unregister` `{token, emails:[...]}`. Máx. 20 cuentas y 50 canales; canales Google caducan en 8 días, suscripciones Graph en 4.
- `POST /gcal`: webhook de Google Calendar; ignora `sync` y tokens que no coinciden (200 sin hacer nada); manda FCM `type=gcal, email, channelId`.
- `POST /graph` (compartido): si la suscripción está en `cal:ms:<id>` manda `type=mscal`; si no, `ms:<id>` como antes.
- Pruebas sin red: `node push-worker/test.mjs`.
- Despliegue desde PowerShell: `npx.cmd --yes wrangler deploy` (`npx.ps1` está bloqueado). `wrangler login` es interactivo (abre el navegador). Los secretos se suben con `wrangler secret bulk archivo.local.json`.
