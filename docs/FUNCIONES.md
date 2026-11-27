# Cemail: funciones

Inventario de lo que la app YA hace (2026-11-27). Mirarlo antes de proponer o programar algo; actualizarlo en el mismo commit que añada, cambie o quite una función. Sacado del código y los changelogs, sin probar en dispositivo.
Rutas relativas a `app/src/main/java/com/cemail/app/`. Lo compartido: `CeKit/docs/FUNCIONES.md`.

## Cuentas
- Gmail con sincronización incremental (data/gmail/GmailApi.kt), Outlook (data/graph/GraphApi.kt), IMAP/SMTP sin búsqueda en servidor ni etiquetas (data/imap/)
- Varias cuentas: orden, predeterminada, color y foto (ui/mail/AccountSwitcher.kt)
- Push de Gmail (data/push/Push.kt) y sincronización en segundo plano (sync/SyncWorker.kt)

## Bandeja y organización
- Bandeja unificada; Recibidos, Enviados, Borradores, Salida, Programados, Pospuestos, Destacados, Spam, Papelera, Archivo (ui/mail/MailApp.kt, ListScreen.kt)
- Etiquetas y subetiquetas (ui/mail/LabelSection.kt); tirar para actualizar; vaciar papelera y spam
- Gestos izquierdo/derecho configurables (data/SwipeAction.kt); deshacer archivar, borrar y spam
- Posponer (SnoozeWorker.kt); silenciar conversación; darse de baja de listas (data/mail/Unsubscribe.kt)

## Lectura
- HTML saneado, imágenes incrustadas, bloqueo de imágenes externas (data/gmail/HtmlSanitizer.kt, ui/mail/RichHtml.kt)
- Adjuntos (ui/mail/AttachmentViews.kt); guardar .eml y ver original; responder, a todos, reenviar
- Invitaciones .ics (parte text/calendar o adjunto; Gmail, Outlook, IMAP): tarjeta con título, fecha, lugar y «Añadir al calendario» (cualquier app, ACTION_INSERT + extra `com.celander.app.extra.ICS`); canceladas: aviso y, con Celander, «Abrir en Celander» (data/mail/Ics.kt, CalendarParts.kt, ui/mail/InviteCard.kt)
- Citas escritas en el texto (en, es, de; solo si no hay .ics): ML Kit Entity Extraction en el móvil, referencia la fecha del correo, la misma tarjeta con «Visto en el texto», máx. 2 (data/mail/Appointments.kt, TextDates.kt). En inglés las fechas numéricas son día/mes; sin año solo con barra y tras «on», «date» o un día de la semana, o seguidas de una hora. Reglas: solo lo escrito por el remitente (sin citas ni firma); no pasado ni a más de ~400 días; una palabra de cita cerca o en el asunto; nada de facturas, ofertas, vencimientos; hora sin día, fuera; sin listas (List-Unsubscribe/List-Id), spam ni papelera; sin modelo, red o cabeceras no se muestra nada. Título = asunto sin Re:/Fwd:; +1 h; solo fecha = todo el día

## Redactar y enviar
- Para/Cc/Cco, selector de remitente, formato, adjuntos (ui/mail/ComposeScreen.kt); contactos del teléfono (data/mail/Contacts.kt)
- Enviar más tarde (OutboxWorker.kt); deshacer envío 5-30 s (data/mail/Outbox.kt); borradores en servidor y teléfono
- Firmas HTML por cuenta, importables (data/gmail/SignatureHtml.kt)

## Búsqueda, avisos y accesos
- Búsqueda sin conexión con from: e is:unread (data/mail/Search.kt) y en el servidor
- Botón rápido sobre Redactar (ui/mail/QuickButton.kt)
- Notificaciones con Leído, Posponer, Eliminar (notify/)
- 2 widgets (widget/Widgets.kt), 4 atajos, recibe «Compartir» y mailto:

## Ajustes y datos
- Apariencia (kit), mensajes, gestos, notificaciones, sincronización, imágenes, firma, cuentas
- Copia .zip manual (data/backup/Backup.kt); Diagnóstico y actualizaciones (kit)
- Modo de ejemplo solo en depuración (data/mail/DemoData.kt)

## No tiene
VIP, horas de silencio, plantillas, respuestas rápidas, bloqueo con huella, copia automática, exportar PDF/imprimir, aceptar/rechazar invitaciones, crear evento o nota desde un correo, tile de ajustes rápidos, Wear OS.
