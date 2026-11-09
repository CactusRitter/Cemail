# Hoja de ruta de Cemail v1

Cada fase termina con la app compilando, sus tests en verde y probada en el emulador.

- [x] **Fase 0 — Base del proyecto**: Gradle, dependencias, Hilt, tema, recursos en inglés/español, documentación.
- [ ] **Fase 1 — Datos locales**: entidades y DAOs de Room, `CredentialStore` cifrado, módulos de Hilt.
- [ ] **Fase 2 — Añadir cuenta**: pantalla de onboarding, autodetección de servidores, login con contraseña, OAuth de Gmail y Outlook, verificación de conexión IMAP y SMTP.
- [ ] **Fase 3 — Sincronizar y leer**: listado de carpetas, sincronización de cabeceras, lista de mensajes con paginación, lectura de mensajes (texto y HTML saneado), marcar leído/destacado, borrar, archivar, mover.
- [ ] **Fase 4 — Redactar y enviar**: nuevo mensaje, responder, responder a todos, reenviar, borradores, firma, bandeja de salida con reintentos.
- [ ] **Fase 5 — Varias cuentas**: cambiar de cuenta, bandeja unificada, color por cuenta, ajustes por cuenta, eliminar cuenta.
- [ ] **Fase 6 — Adjuntos**: ver/descargar/compartir adjuntos, imágenes incrustadas, adjuntar archivos al redactar (selector del sistema).
- [ ] **Fase 7 — Búsqueda**: búsqueda local con FTS y, como alternativa, búsqueda en el servidor (`IMAP SEARCH`).
- [ ] **Fase 8 — Notificaciones y segundo plano**: sincronización periódica, IDLE con la app abierta, notificaciones por cuenta con acciones, permiso `POST_NOTIFICATIONS`.
- [ ] **Fase 9 — Pulido y publicación**: icono definitivo, accesibilidad, modo oscuro, tablets, R8, firma de release, ficha de Play Store, verificación OAuth de Google.

## Para después de la v1
- Más idiomas (solo hay que añadir `values-xx/strings.xml`).
- Vista de conversación (hilos) avanzada, reglas/filtros, envío programado, deshacer envío.
- Exchange/EWS o Microsoft Graph, JMAP.
- Cifrado PGP/S-MIME.
