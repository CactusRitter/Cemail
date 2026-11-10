# Hoja de ruta de Cemail v1

Versión sencilla: solo Gmail. Cada fase termina con la app compilando, sus tests en verde y probada en el emulador.

- [x] **Fase 0 — Base del proyecto**: Gradle, dependencias, tema y cristal de las apps "Ce", recursos en inglés y español, documentación.
- [x] **Fase 1 — Cuenta de Google** (hecha 2026-11-10; falta que el usuario pruebe el inicio de sesión real): registrar la app en Google ([OAUTH_SETUP.md](OAUTH_SETUP.md)), Room, almacén cifrado de tokens, pantalla para iniciar sesión, añadir/quitar cuentas (una o varias).
- [~] **Fase 2 — Sincronizar y leer** (código hecho 2026-11-10, sin probar con Gmail real; sincroniza los últimos 120 mensajes con la API, aplica archivar/papelera/destacar/leído en Gmail y envía; falta historial incremental, HTML y adjuntos): etiquetas, lista de la bandeja, lectura (texto y HTML saneado), marcar leído y destacado, archivar, mover a la papelera.
  - **Interfaz hecha (con datos de prueba, 2026-11-10)**: carpetas en menú lateral, lista estilo Thunderbird con avatares y deslizar para archivar/papelera, selección múltiple, lectura, redactar/responder/reenviar, búsqueda con `from:`/`is:unread`/`has:attachment`, y de Spark: fijar, posponer y deshacer. Falta conectarla a Gmail y Room.
- [ ] **Fase 3 — Redactar y enviar**: nuevo mensaje, responder, responder a todos, reenviar, firma, envío con reintentos sin red.
- [ ] **Fase 4 — Adjuntos**: ver, descargar y compartir; imágenes incrustadas; adjuntar archivos al redactar.
- [ ] **Fase 5 — Búsqueda**: con la sintaxis de Gmail y, sin red, sobre lo guardado.
- [ ] **Fase 6 — Notificaciones y segundo plano**: sincronización periódica, notificaciones con acciones, permiso `POST_NOTIFICATIONS`.
- [ ] **Fase 7 — Pulido y APK para amigos**: ajustes (acento, transparencia), accesibilidad, tablets, icono definitivo, ajuste fino con el diseño de Celander, APK de release firmado con clave propia.

## Para después
- Outlook / Hotmail y otros proveedores (IMAP).
- Bandeja unificada de varias cuentas, envío programado, deshacer envío (el deshacer de acciones sobre mensajes ya está).
- **Push real (avisos instantáneos)**: Gmail solo avisa al momento a un servidor (Cloud Pub/Sub → servidor → Firebase Cloud Messaging → móvil). Requiere activar facturación en Google Cloud aunque el uso quepa en la capa gratuita, por eso no está hecho. Mientras tanto: comprobación cada minuto con la app abierta y cada ~15 min en segundo plano (`sync/SyncWorker.kt`).
- **Notificaciones (fase 6, hecho en 0.9.0 salvo acciones)**: canal «Correo nuevo», permiso `POST_NOTIFICATIONS` pedido al abrir, una notificación por cuenta, solo con la app cerrada. Pendiente: acciones (archivar, marcar leído, responder) y agrupar con las anteriores sin leer.
- Widgets al estilo de Celander.
- Más idiomas (solo hay que añadir `values-xx/strings.xml`).
