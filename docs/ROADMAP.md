# Hoja de ruta de Cemail v1

Versión sencilla: solo Gmail. Cada fase termina con la app compilando, sus tests en verde y probada en el emulador.

- [x] **Fase 0 — Base del proyecto**: Gradle, dependencias, tema y cristal de las apps "Ce", recursos en inglés y español, documentación.
- [ ] **Fase 1 — Cuenta de Google**: registrar la app en Google ([OAUTH_SETUP.md](OAUTH_SETUP.md)), Room, almacén cifrado de tokens, pantalla para iniciar sesión, añadir/quitar cuentas (una o varias).
- [ ] **Fase 2 — Sincronizar y leer**: etiquetas, lista de la bandeja, lectura (texto y HTML saneado), marcar leído y destacado, archivar, mover a la papelera.
- [ ] **Fase 3 — Redactar y enviar**: nuevo mensaje, responder, responder a todos, reenviar, firma, envío con reintentos sin red.
- [ ] **Fase 4 — Adjuntos**: ver, descargar y compartir; imágenes incrustadas; adjuntar archivos al redactar.
- [ ] **Fase 5 — Búsqueda**: con la sintaxis de Gmail y, sin red, sobre lo guardado.
- [ ] **Fase 6 — Notificaciones y segundo plano**: sincronización periódica, notificaciones con acciones, permiso `POST_NOTIFICATIONS`.
- [ ] **Fase 7 — Pulido y APK para amigos**: ajustes (acento, transparencia), accesibilidad, tablets, icono definitivo, ajuste fino con el diseño de Celander, APK de release firmado con clave propia.

## Para después
- Outlook / Hotmail y otros proveedores (IMAP).
- Bandeja unificada de varias cuentas, borradores, envío programado, deshacer envío.
- Avisos instantáneos (requiere servidor propio), widgets al estilo de Celander.
- Más idiomas (solo hay que añadir `values-xx/strings.xml`).
