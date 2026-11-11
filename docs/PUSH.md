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

El móvil se da de alta con `POST /register` (cabecera `X-Cemail-Key`) enviando su token y las direcciones de Gmail. Si se quita una cuenta o se apaga el interruptor, la app lo da de baja y llama a `users.stop`.

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
- Solo Gmail por ahora. Microsoft (suscripciones de Graph, caducan a los 3 días) y IMAP IDLE quedan para más adelante.
- Necesita Google Play Services en el móvil.
- El Worker ve la dirección y el token, nunca el contenido del correo (ver la política de privacidad).
