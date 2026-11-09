# Arquitectura de Cemail

## Visión general

```
 ┌──────────── ui (Compose + ViewModels) ────────────┐
 │  Onboarding · Bandeja · Lectura · Redactar ·      │
 │  Búsqueda · Ajustes                               │
 └───────────────▲───────────────────────────────────┘
                 │ Flow<estado>        acciones
 ┌───────────────┴──── domain ───────────────────────┐
 │  Modelos (Account, Folder, Message, Attachment)   │
 │  Casos de uso (SyncFolder, SendMessage, Search…)  │
 └───────────────▲───────────────────────────────────┘
                 │
 ┌───────────────┴──── data ─────────────────────────┐
 │  Repositorios ──► Room (fuente de verdad)         │
 │       │                                            │
 │       └──► mail: ImapClient / SmtpClient           │
 │                 (Jakarta Mail · Eclipse Angus)     │
 │       └──► auth: OAuth (AppAuth) + credenciales    │
 │                 cifradas con Android Keystore      │
 └───────────────▲───────────────────────────────────┘
                 │
        sync (WorkManager) · notifications
```

Principio rector: **offline-first**. La UI solo lee de Room. La sincronización descarga del servidor y escribe en Room; las acciones del usuario (marcar leído, mover, borrar, enviar) se aplican primero en local y se encolan para el servidor.

## Paquetes (`com.cemail.app`)

| Paquete | Contenido |
|---|---|
| `ui/theme` | Tema Material 3 (color dinámico en Android 12+) |
| `ui/navigation` | Grafo de navegación de Compose |
| `ui/onboarding` | Añadir cuenta: elegir proveedor, OAuth o contraseña, configuración manual |
| `ui/inbox` | Lista de mensajes por carpeta y bandeja unificada; cajón con cuentas y carpetas |
| `ui/message` | Lectura de un hilo/mensaje, adjuntos, acciones |
| `ui/compose` | Redactar, responder, reenviar, adjuntar |
| `ui/search` | Búsqueda |
| `ui/settings` | Ajustes globales y por cuenta |
| `domain/model` | Modelos inmutables usados por la UI |
| `domain/usecase` | Casos de uso que combinan repositorios |
| `data/db` | Room: entidades, DAOs, `CemailDatabase`, FTS |
| `data/mail` | Interfaces `ImapClient`, `SmtpClient` y su implementación con Jakarta Mail; conversión MIME ↔ modelo |
| `data/auth` | Flujo OAuth (AppAuth), refresco de tokens, `CredentialStore` cifrado |
| `data/discovery` | Autoconfiguración de servidores (lista integrada + ISPDB de Thunderbird) |
| `data/repository` | `AccountRepository`, `MessageRepository`, `OutboxRepository` |
| `sync` | Workers de WorkManager: sincronización periódica, cola de acciones pendientes, envío |
| `notifications` | Canales por cuenta, notificaciones de correo nuevo con acciones (leído, archivar) |
| `di` | Módulos de Hilt |

## Cuentas y autenticación

| Proveedor | Autenticación | Servidores |
|---|---|---|
| Gmail | OAuth 2.0 (XOAUTH2), scope `https://mail.google.com/` | imap.gmail.com:993, smtp.gmail.com:465 |
| Outlook / Hotmail / Microsoft 365 | OAuth 2.0 (XOAUTH2), scopes `IMAP.AccessAsUser.All`, `SMTP.Send`, `offline_access` | outlook.office365.com:993, smtp-mail.outlook.com:587 |
| Yahoo, iCloud, GMX, etc. | Contraseña de aplicación | Lista integrada / ISPDB |
| Cualquier otro | Usuario + contraseña | Autodetección o manual |

- OAuth con **AppAuth** usando el navegador del sistema (Custom Tabs) y PKCE. Redirección: `com.cemail.app:/oauth2redirect`.
- El access token se pasa a Jakarta Mail con `mail.imap.auth.mechanisms=XOAUTH2`. Antes de cada conexión se refresca si ha caducado.
- Autodetección: 1) lista integrada de proveedores conocidos, 2) `https://autoconfig.thunderbird.net/v1.1/<dominio>`, 3) `autoconfig.<dominio>`, 4) formulario manual.
- `CredentialStore`: clave AES-256-GCM en Android Keystore; los secretos cifrados se guardan en Room/DataStore.

## Modelo de datos (Room)

- `account`: id, email, nombre visible, proveedor, tipo de auth, host/puerto/seguridad IMAP y SMTP, firma, color, frecuencia de sync.
- `folder`: id, accountId, nombre completo IMAP, rol (INBOX, SENT, DRAFTS, TRASH, SPAM, ARCHIVE, otro — por atributos SPECIAL-USE), `uidValidity`, `uidNext`, nº no leídos.
- `message`: id, folderId, accountId, `uid`, `messageId`, `inReplyTo`, `references`, `threadKey`, remitente, destinatarios, asunto, fecha, flags (leído, destacado, respondido), `snippet`, `hasAttachments`, estado de descarga del cuerpo.
- `message_body`: messageId, texto plano, HTML (cargado bajo demanda, separado para que la lista sea ligera).
- `attachment`: id, messageId, nombre, tipo MIME, tamaño, `partId` IMAP, ruta local si descargado.
- `pending_action`: cola de cambios locales aún no enviados al servidor (flags, mover, borrar).
- `outbox`: mensajes por enviar (reintentos si no hay red).
- `message_fts` (FTS4): asunto, remitente, destinatarios, snippet y texto — búsqueda local rápida.

Esquemas exportados en `app/schemas/` (plugin de Room). Toda migración con su test.

## Sincronización

1. **Inicial**: listar carpetas (`LIST` + SPECIAL-USE), descargar cabeceras de los últimos N días (por defecto 30) de INBOX y carpetas principales. Los cuerpos se descargan al abrir el mensaje (y los de los más recientes se precargan con Wi-Fi).
2. **Incremental**: por carpeta, comprobar `UIDVALIDITY`; si no cambia, traer UIDs > último conocido y resincronizar flags del rango local. Si el servidor soporta `CONDSTORE`, usar `MODSEQ` para traer solo cambios.
3. **Segundo plano**: `PeriodicWorkRequest` (mínimo 15 min, configurable) con restricción de red. Con la app abierta, `IMAP IDLE` sobre INBOX para recibir correo al instante.
4. **Acciones del usuario**: se aplican en Room al momento y se guardan en `pending_action`; un Worker las vacía contra el servidor con reintentos exponenciales.
5. **Envío**: el mensaje va a `outbox`; un Worker lo envía por SMTP y, si el proveedor no lo hace solo (Gmail sí), lo añade a la carpeta Enviados con `APPEND`.

## Mostrar correos

- Texto plano o HTML saneado con jsoup (`Safelist.relaxed()` + estilos inline permitidos).
- `WebView` con JavaScript, acceso a archivos y contenido desactivados; enlaces abiertos fuera de la app.
- Imágenes remotas bloqueadas por defecto, con botón "Mostrar imágenes" y opción "siempre para este remitente".
- Imágenes `cid:` incrustadas se sirven desde los adjuntos descargados.

## Idiomas

Ver la sección *Idiomas* de [CLAUDE.md](../CLAUDE.md). Resumen: idioma del sistema, recursos en `values-xx`, inglés por defecto, español incluido, añadir uno es crear una carpeta.
