# Hoja de ruta de Cemail

Las versiones van siempre por debajo de 1.0. La **1.0** será cuando funcionen bien los tres tipos de cuenta (Gmail, IMAP y Microsoft) y lo principal esté probado en un móvil real. Todo se mantiene a **coste cero** (sin servidores propios ni facturación).

Cada versión termina con la app compilando, sus tests en verde, probada en el emulador y el APK en Drive.

## Dónde estamos (0.25.2)

Hecho: menú del icono (0.20), widgets Inbox y Tools (0.21), firma por cuenta (0.22), sincronización incremental con el historial de Gmail, conversaciones (tarjeta con el resto del hilo), etiquetas desde el mensaje y sugerencias de contactos (0.25), cuenta de Google (varias) con bandeja unificada, sincronización (manual, con la app abierta y en segundo plano), etiquetas como carpetas, lectura de HTML saneado, imágenes incrustadas, adjuntos (ver, abrir, compartir, adjuntar), redactar con formato y destinatarios en píldoras con CC/CCO, enviar de verdad por Gmail, responder en el mismo hilo y «responder a todos», notificaciones con «Leído» y «Borrar», registro de actividad compartible, fijar, posponer (local) y deshacer, ajustes de aspecto.

Todavía sin probar con una cuenta real en el móvil: adjuntos (ver y enviar), envío con formato y CC/CCO, respuestas en hilo. Se prueban con el teléfono conectado por adb (ver más abajo).

## El plan, por bloques

### A. Atajos del icono y widgets (bloque rápido y visible)
| Versión | Qué | Notas |
|---|---|---|
| 0.20.0 | **Menú al dejar pulsado el icono** (App Shortcuts): Redactar, Bandeja unificada, Buscar, Destacados; y un atajo «Redactar desde …» por cada cuenta. | Es un `shortcuts.xml` más atajos dinámicos. Exige que `MainActivity` entienda acciones de entrada (redactar, abrir carpeta, buscar), que luego usan los widgets. Los textos van en es/en. Esfuerzo: bajo. |
| 0.21.0 | **Widget 1 – Bandeja**: lista de los últimos correos (remitente, asunto, hora, punto de no leído), toque abre el correo, cabecera con botón de redactar y selector de cuenta/carpeta. | Con Jetpack Glance (gratuito). Los widgets no pueden desenfocar el fondo: llevarán un fondo translúcido con el color de acento y esquinas redondeadas, en claro/oscuro. Se refresca con la sincronización y al recibir correo. Configuración al añadirlo: cuenta y carpeta. Esfuerzo: medio. |
| 0.22.0 | **Widget 2 – Herramientas**: botones redondos (Redactar, Bandeja, Buscar, Destacados), contador de no leídos y, opcional, el último correo. Tamaños 4×1 y 2×2. | Reutiliza los atajos de la 0.20.0. Esfuerzo: bajo-medio. |

### B. Cerrar lo básico del correo (firma hecha en 0.22.0; el resto pendiente)
| Versión | Qué | Notas |
|---|---|---|
| 0.23.0 | **Conversaciones**: agrupar los mensajes de un hilo en una sola tarjeta y mostrarlos apilados al abrir. | Hoy cada mensaje es una fila. Gmail ya da `threadId`; para IMAP se agrupa por `References`/`In-Reply-To`. Esfuerzo: medio-alto. |
| 0.23.0 | **Borradores completos**: guardar formato, CC/CCO y adjuntos, y sincronizarlos con Gmail (API de borradores). | Hoy se guardan solo en el teléfono y pierden formato y adjuntos. |
| 0.23.0 | **Firma** por cuenta, y **etiquetas**: poner y quitar desde el mensaje. | |
| 0.24.0 | **Sincronización incremental** (Gmail `history`): solo lo que cambió, mucho más rápido y con menos batería. **Contactos** (permiso): autocompletar al escribir y fotos de remitente en el círculo. | |

### C. Más cuentas: IMAP y Microsoft
Para soportar otros proveedores primero hay que separar a Cemail de Gmail, porque hoy `RoomMailRepository` habla directamente con la API de Gmail.

**Paso 0 (0.25.0): capa de proveedores, sin cambios visibles.**
- Una interfaz `MailBackend` (listar y bajar mensajes, cambiar estado y carpeta, enviar, adjuntos, carpetas/etiquetas, borradores, búsqueda) con `GmailBackend` (lo que ya hay) y los nuevos.
- Room sigue siendo la única fuente de verdad; la UI no cambia. Se añade el tipo de proveedor a la cuenta (migración de la base de datos, sin perder nada).
- Modelo: en IMAP un mensaje está en **una** carpeta; en Gmail tiene **varias** etiquetas. Se mantiene `folder` + `labelIds` y las carpetas IMAP se muestran como etiquetas.

**Paso 1 (0.26.0): IMAP/SMTP (cualquier proveedor: Yahoo, iCloud, GMX, Outlook por IMAP, servidores propios…).**
- Librería: Jakarta Mail para Android (gratuita). Cliente de IMAP para leer/cambiar estado y SMTP para enviar.
- **Alta de cuenta**: autodescubrimiento de servidores (base de datos de configuraciones de proveedores + `autoconfig` del dominio) y formulario manual (servidor, puerto, SSL/STARTTLS). Contraseña o **contraseña de aplicación**, guardada cifrada con el Keystore como los tokens de Google.
- **Sincronización**: por UID de IMAP y `UIDVALIDITY`; cabeceras primero y cuerpo bajo demanda; `IDLE` (aviso al instante) solo con la app abierta, y sondeo con WorkManager en segundo plano.
- **Particularidades**: guardar copia en «Enviados» tras enviar (IMAP no lo hace solo), borradores en la carpeta Borradores, hilos por `References`, papelera/archivo según las carpetas «especiales» de cada servidor (`SPECIAL-USE`), búsqueda con `SEARCH` en el servidor.
- Esfuerzo: alto, es lo más grande del plan. Se prueba con una cuenta real tuya (Yahoo/iCloud/otra).

**Paso 2 (0.27.0): Microsoft (Outlook.com, Hotmail, Microsoft 365).**
- Dos caminos. Recomiendo **Microsoft Graph API** (REST, parecido a lo que ya hacemos con Gmail, con consultas «delta» para sincronizar solo cambios) en vez de IMAP con OAuth, que Microsoft desaconseja y complica.
- Inicio de sesión: **AppAuth** (el mismo que usamos para Google) contra el punto de acceso v2 de Microsoft, con ámbitos `Mail.ReadWrite`, `Mail.Send`, `offline_access`, `User.Read`.
- **Lo que hace falta de tu parte (gratis):** registrar la app en el portal de Azure (Microsoft Entra) con una cuenta Microsoft, tipo «cuentas personales y de cualquier organización», con el identificador de redirección de la app y la huella de la firma del APK. Como con Google, lo hago yo con el navegador integrado cuando me des permiso. Las cuentas personales funcionan sin verificar el editor; las de empresa pueden pedir aprobación del administrador.
- Carpetas de Microsoft → carpetas/etiquetas; «categorías» de Outlook → etiquetas. Hilos con `conversationId`. Adjuntos grandes con sesión de subida.
- Esfuerzo: alto, pero con la capa de proveedores ya hecha es más corto que IMAP.

### D. Funciones interesantes y complicadas (en el orden en que las haría)
1. **Búsqueda sin conexión en todo el texto** (Room FTS) además de la de Gmail; filtros guardados («carpetas inteligentes»).
2. **Deshacer envío** (retraso de 5–30 s configurable) y **envío programado** (WorkManager).
3. **Posponer de verdad**: hoy solo oculta; que a la hora elegida vuelva a la bandeja y avise con una notificación. **Recordarme si no responden**.
4. **Cancelar suscripción en un toque** (cabecera `List-Unsubscribe`) y **silenciar hilo**.
5. **Invitaciones de calendario**: leer los `.ics` y añadirlos a **Celander**; botón «Guardar como nota» hacia **Cenotes** (la familia «Ce»).
6. **Vista previa de adjuntos** dentro de la app (imágenes, PDF) y «Guardar en Descargas».
7. **Reglas y remitentes VIP**: avisos solo de ciertos remitentes, horas de silencio, avisos distintos por cuenta.
8. **Tableta y plegables** (dos paneles: lista y mensaje), **bloqueo con huella**, modo oscuro revisado a fondo y más idiomas.
9. **Push real** (avisos al instante de Gmail): sigue pendiente de decidir si se activa la facturación de Google Cloud (ver abajo).

### E. Pulido y 1.0
Ajustes finos con el diseño de Celander/Cenotes, accesibilidad, icono definitivo, APK firmado, revisión de textos en todos los idiomas. Se llega a la **1.0** cuando funcionen las cuentas de Gmail, IMAP y Microsoft en tu móvil y lo principal esté probado con correo real.

## Cómo se prueba mientras no hay cable
- Con el móvil conectado por adb en casa: iniciar sesión con las cuentas reales y comprobar sincronización, adjuntos, envíos y respuestas, con el registro de actividad como apoyo.
- Mientras tanto, el registro de **Ajustes → Diagnóstico → Compartir registro** me da lo que hace la app en el teléfono (sin textos de correo).

## Decisiones pendientes tuyas
- ¿Activar facturación en Google Cloud para el push real? (No se cobra con este uso, pero obliga a registrar una tarjeta.) Mientras tanto: comprobación cada minuto con la app abierta y cada ~15 min en segundo plano.
- Orden del bloque C si cambia de prioridad (Microsoft antes que IMAP, por ejemplo): la capa de proveedores va primero en cualquier caso.

## Notas técnicas
- Notificaciones (hecho en 0.9.0, botones en 0.10.0): canal «Correo nuevo», permiso `POST_NOTIFICATIONS`, una notificación por cuenta, solo con la app cerrada, con «Leído» y «Borrar». Posponer lo ofrece el propio Android.
- Los widgets y los atajos comparten el enrutado de intents de `MainActivity`.
- Más idiomas: solo hay que añadir `values-xx/strings.xml`.
