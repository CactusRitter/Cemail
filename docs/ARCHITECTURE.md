# Arquitectura de Cemail

Cemail es deliberadamente sencilla, como Cenotes: **solo Gmail**, **sin Hilt**, un único módulo.

## Decisiones clave
| Tema | Decisión | Por qué |
|---|---|---|
| Proveedor | Solo Gmail (varias cuentas de Google, sin bandeja unificada) | Alcance pequeño; Outlook y otros se añaden después |
| Transporte | **API REST de Gmail** (OkHttp + kotlinx.serialization), no IMAP | JSON sobre HTTPS, hilos y etiquetas nativos, sincronización incremental con `history`, sin cliente IMAP |
| Inicio de sesión | OAuth de Google con AppAuth (ver [OAUTH_SETUP.md](OAUTH_SETUP.md)) | Sin contraseñas guardadas |
| Datos locales | Room como única fuente de verdad (offline-first) | La interfaz siempre lee de Room |
| Inyección | Contenedor manual en `CemailApplication` + `ViewModelProvider.Factory` | Igual que Cenotes, sin Hilt |
| Navegación | Estado en Compose (`AnimatedContent`), sin `navigation-compose` | Igual que Cenotes |
| Diseño | Cristal esmerilado de las apps "Ce" ([DESIGN.md](DESIGN.md)) | Coherencia con Celander y Cenotes |

## Capas
```
 ui (Compose + ViewModels)  ──►  data/repository  ──►  Room (fuente de verdad)
                                        │
                                        └──►  data/gmail (API REST)  ◄── data/auth (OAuth + tokens cifrados)
 sync (WorkManager) y notifications usan los repositorios
```
La interfaz nunca llama a la red ni a Room directamente.

## Paquetes (`com.cemail.app`)
| Paquete | Contenido |
|---|---|
| `ui/theme`, `ui/glass` | Tema y componentes de cristal (ya hechos, copiados de Cenotes) |
| `ui/...` | Pantallas: cuentas, bandeja, lectura, redactar, búsqueda, ajustes |
| `data` | `SettingsRepository` (hecho) y repositorios de cuentas y mensajes |
| `data/db` | Entidades, DAOs y `CemailDatabase` de Room |
| `data/gmail` | Cliente de la API de Gmail y conversión de sus respuestas a las entidades |
| `data/auth` | Inicio de sesión (AppAuth), renovación de tokens y almacén cifrado con Android Keystore |
| `sync` | Workers de WorkManager (sincronización periódica, envío pendiente) |
| `notifications` | Canal y notificaciones de correo nuevo |

## Modelo de datos (Room)
- `account`: id, email, nombre, color, última sincronización, `historyId` de Gmail.
- `label`: id de Gmail, accountId, nombre, tipo (sistema/usuario), no leídos.
- `message`: id de Gmail, `threadId`, accountId, remitente, destinatarios, asunto, fecha, `snippet`, etiquetas (`INBOX`, `UNREAD`, `STARRED`…), `hasAttachments`.
- `message_body`: texto plano y HTML (se descarga al abrir; separado para que la lista sea ligera).
- `attachment`: id, messageId, nombre, tipo MIME, tamaño, `attachmentId` de Gmail, ruta local si está descargado.
- `outbox`: mensajes por enviar y cambios pendientes (marcar leído, archivar, borrar) que se reintentan sin red.

Esquemas exportados en `app/schemas/`; cada migración lleva su test.

## Sincronización
1. **Primera vez**: `labels.list` y `messages.list` (INBOX, últimos N días, por defecto 30) + `messages.get` en formato `metadata` para las cabeceras. Se guarda el `historyId` del perfil.
2. **Después**: `history.list` desde el último `historyId` → solo mensajes añadidos, borrados o con etiquetas cambiadas. Si Gmail responde 404 (historial caducado), se hace una sincronización completa.
3. **Al abrir un mensaje**: `messages.get` en formato `full`, se interpreta el MIME y se guarda el cuerpo.
4. **Acciones del usuario**: se aplican en Room al instante y se encolan en `outbox`; un Worker las manda con `messages.modify` / `trash` con reintentos.
5. **Enviar**: se construye el mensaje MIME (texto + adjuntos), se codifica en base64url y se manda con `messages.send`. Gmail lo guarda solo en Enviados.
6. **Segundo plano**: `PeriodicWorkRequest` (mínimo 15 min, configurable). El aviso de correo nuevo llega con ese retraso; el aviso instantáneo exigiría un servidor propio (Pub/Sub + FCM) y no entra en esta versión.

## Búsqueda
Se usa el parámetro `q` de Gmail (con su sintaxis: `from:`, `has:attachment`…) y, sin red, un filtro sencillo sobre lo guardado en Room.

## Mostrar correos
- Texto plano, o HTML saneado con jsoup.
- `WebView` con JavaScript, acceso a archivos y contenido desactivados; los enlaces se abren fuera de la app.
- Imágenes remotas bloqueadas por defecto (píxeles de rastreo), con botón para mostrarlas.
- Imágenes `cid:` incrustadas: se sirven desde los adjuntos descargados.

## Idiomas
Ver la sección *Idiomas* de [CLAUDE.md](../CLAUDE.md): idioma del sistema, recursos en `values-xx`, inglés por defecto, español incluido; añadir uno es crear una carpeta.
