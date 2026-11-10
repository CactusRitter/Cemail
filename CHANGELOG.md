# Historial de versiones de Cemail

Siempre por debajo de 1.0: la 1.0 será cuando funcionen todas las funciones principales.
Cada versión se guarda en Google Drive (`Mi unidad/Cemail/Cemail-x.y.z.apk`) y no se reemplaza la anterior.

## 0.9.0 — 2026-11-10
- Notificaciones de correo nuevo: cuando la comprobación en segundo plano encuentra mensajes no leídos en la Bandeja de entrada, se avisa con una notificación por cuenta (un mensaje: remitente, asunto y texto; varios: lista). No avisa con la app abierta ni en la primera sincronización de una cuenta ni con mensajes de más de 3 días.
- Ajustes → Correo nuevo: interruptor de notificaciones, botón para enviar una de prueba y aviso si Android las tiene bloqueadas. El permiso se pide al abrir la app (Android 13+).

## 0.8.0 — 2026-11-10
- Nueva tarjeta de correo: el remitente va en una etiqueta pequeña (acento si no está leído, gris si está leído), debajo el asunto en negrita y una sola línea de resumen. Ocupa bastante menos.
- En la bandeja unificada, el color de la cuenta aparece como una media luna en la esquina superior que se afina a lo largo del borde y del lateral, con un velo suave de cristal.
- Ajustes: el interruptor ahora es «Etiqueta del remitente» (apagado = texto normal).

## 0.7.0 — 2026-11-10
- Cabecera de la lista como en Celander y Cenotes: botón redondo del menú a la izquierda, título grande en negrita en el centro y una píldora a la derecha con actualizar, buscar y ajustes. Ajustes ya no está al final del menú.
- Menú: «Todas las cuentas» siempre es la primera fila; debajo, una píldora desplegable con las cuentas.
- Ajustes: nueva opción para ocultar el círculo del remitente en la lista.

## 0.6.0 — 2026-11-10
- Correos HTML: se conservan sus hojas de estilo (saneadas), así los botones y columnas se ven como en Gmail; ya no salen iconos de imagen rota ni «[image: …]» en la vista previa.
- Menú: la cuenta actual va en una píldora desplegable (todas las cuentas, cada cuenta, añadir cuenta). Quitar una cuenta pasa a Ajustes, con confirmación.
- Bandeja unificada: cada correo lleva una línea con el color de su cuenta.
- Ajustes: frecuencia de comprobación de correo nuevo (15 min, 30 min, 1 h, 2 h o Manual).
- Si la app se cierra por un error, el detalle queda guardado en Ajustes → Diagnóstico para poder copiarlo. Los errores del trabajo en segundo plano ya no cierran la app.

## 0.5.0 — 2026-11-10
- Varias cuentas: bandeja unificada («Todas las cuentas») y cada cuenta por separado, desde el menú lateral. Las etiquetas se ven al elegir una cuenta.
- Correo nuevo automático: cada minuto con la app abierta y cada ~15 minutos en segundo plano (WorkManager). Aún sin notificaciones.
- Las imágenes incrustadas se descargan durante la sincronización, no al abrir el correo.
- Cabecera del correo: al desplazar, el asunto se reduce y debajo aparece el remitente en gris.
- Un remitente que llega solo con su dirección se muestra con el nombre que tenga en otros correos.

## 0.4.0 — 2026-11-10
- Caracteres raros corregidos (ü, é, ñ…): el texto se lee como UTF-8 y solo si no lo es se usa la codificación declarada. La primera sincronización vuelve a descargar los mensajes guardados.
- Las imágenes incrustadas en el correo (logos como el de Booking) se descargan de Gmail y se muestran siempre; no son imágenes externas.
- Los fondos con imagen externa también cuentan para el aviso «Mostrar imágenes» y se cargan al permitirlo.
- El título de la página y las hojas de estilo ya no aparecen como texto al principio del correo.
- Al desplazar el correo, la cabecera se encoge poco a poco hasta dejar solo el asunto.
- Ajustes: nueva opción de transparencia del cristal.

## 0.3.0 — 2026-11-10
- Zoom del correo corregido: los correos de ancho fijo se abren ajustados a la pantalla (se pueden ampliar con los dedos).
- Pantalla de Ajustes (en el menú lateral) con dos opciones: mostrar siempre las imágenes externas (desactivada por defecto) y agrupar los mensajes por fecha (activada por defecto).
- Secciones Hoy, Ayer, Última semana y Antiguos (más de una semana), cada una contraíble.

## 0.2.0 — 2026-11-10
- Los correos HTML se ven con su formato (HTML saneado en un WebView sin JavaScript); las imágenes remotas siguen bloqueadas hasta pulsar «Mostrar imágenes».
- Las etiquetas de Gmail aparecen como carpetas en el menú; las que tienen subcarpetas ("Compras/Amazon") se expanden y contraen.
- La barra de responder cabe en pantallas estrechas.
- La base de datos pasa a la versión 3 (migración automática); la primera sincronización vuelve a descargar los mensajes para guardar su HTML.
- APK de release sin reducir (más grande, pero sin riesgo de fallos por la reducción de código).

## 0.1.0 — 2026-11-10
- Interfaz estilo Thunderbird con funciones de Spark (fijar, posponer, deshacer), inicio de sesión con Google y primera sincronización de Gmail (mensajes como texto).
