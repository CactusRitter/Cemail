# Historial de versiones de Cemail

Siempre por debajo de 1.0: la 1.0 será cuando funcionen todas las funciones principales.
Cada versión se guarda en Google Drive (`Mi unidad/Cemail/Cemail-x.y.z.apk`) y no se reemplaza la anterior.

## 0.23.0 — 2026-11-10
- **Descargar un correo como .eml**: en el menú «⋮» de la lectura, «Guardar como .eml» baja el mensaje tal cual lo guarda Gmail (cabeceras, HTML, adjuntos) y abre el menú de compartir (Drive, Telegram, correo...). Hace falta conexión.
- **Firma en HTML**: Ajustes → Firma → «Importar desde un mensaje (.eml) o archivo .html». Se busca el bloque de firma del mensaje (Gmail, Outlook, Thunderbird) o se usa todo el HTML; se limpia (sin scripts) y las imágenes incrustadas se conservan. Se ve como se diseñó, con vista previa en Ajustes y debajo del editor (con una X para quitarla en ese mensaje), y se añade al enviar en las dos mitades del correo (HTML y texto); las imágenes de la firma van como partes incrustadas.
- Si no hay firma HTML, sigue valiendo la firma de texto.

## 0.22.0 — 2026-11-10
- Firma por cuenta (Ajustes → Firma): se añade una sola vez al escribir un mensaje nuevo o una respuesta (debajo del cursor y encima del texto citado). Al reabrir un borrador no se repite.

## 0.21.0 — 2026-11-10
- Dos widgets (Jetpack Glance, gratis): **Cemail · Inbox** (4×3: los últimos correos de todas tus cuentas con remitente, asunto y hora, en negrita los no leídos; botones de buscar y redactar; tocar un correo lo abre; tocar el título abre la bandeja) y **Cemail · Tools** (4×1: botones redondos de redactar, bandeja con el número de no leídos, buscar y destacados).
- Se redibujan solos cuando cambia la bandeja (con una pausa corta para que una sincronización cuente como un cambio).
- Los widgets llevan un panel translúcido en tonos suaves, en claro y oscuro (un widget no puede desenfocar lo que tiene detrás).

## 0.20.0 — 2026-11-10
- Menú del icono (al dejarlo pulsado): Redactar, Bandeja de entrada, Buscar y Destacados. Con dos o más cuentas añade «Redactar desde …» por cada una (hasta 3).
- La app entiende peticiones de fuera (menú del icono y, próximamente, widgets): cambia a la cuenta que toca y abre la carpeta, la búsqueda, un mensaje o el editor.

## 0.19.0 — 2026-11-10
- Responder en el mismo hilo: la respuesta lleva `In-Reply-To`/`References` y el identificador de hilo de Gmail, así que queda dentro de la conversación (en Gmail y en cualquier otra app de correo). Hace falta conexión para leer las cabeceras; sin ella, la respuesta sale como mensaje nuevo.
- «Responder» va a Reply-To (o al remitente); «Responder a todos» añade en CC a todos los demás destinatarios menos tú; si respondes a un mensaje tuyo, va a sus destinatarios.
- El asunto no repite «Re:» ni «Fwd:» (también reconoce AW:, RV:, etc.).
- Arreglo: al abrir una respuesta, los destinatarios podían quedarse como texto en vez de píldoras por un cambio simultáneo del borrador; ahora cada campo edita el borrador desde su último estado.
- Plan completo en docs/ROADMAP.md: atajos del icono, dos widgets, IMAP y Microsoft, y funciones avanzadas.

## 0.18.0 — 2026-11-10
- Varios destinatarios: Para, CC y CCO muestran cada dirección como una píldora con su X. La píldora se forma al escribir coma, espacio o Intro, o al salir del campo; Retroceso en un campo vacío recupera la última para editarla. Las direcciones no válidas salen en rojo.
- Al enviar se comprueba que haya al menos un destinatario en «Para» y que todas las direcciones sean válidas.
- Las respuestas ya salen con el remitente como píldora.

## 0.17.0 — 2026-11-10
- Editor con formato al escribir: negrita, cursiva, subrayado, cuatro fuentes (sin serifa, con serifa, monoespaciada, manuscrita), listas con viñetas y numeradas, y «quitar formato». Se ve al instante (nada de asteriscos) y la barra flota sobre el teclado. Se envía como HTML con una copia en texto plano; si no hay formato, sale como texto plano de siempre.
- CC y CCO desplegables bajo «Para» (flecha a la derecha del campo). Se abren solos si ya tienen contenido.
- Se añade la librería compose-rich-editor (licencia Apache, gratuita).
- Pendiente conocido: al guardar un borrador se pierden el formato, CC/CCO y los adjuntos.

## 0.16.0 — 2026-11-10
- El botón «⋮» baja a la esquina, encima del de responder. La píldora de arriba se queda con atrás, asunto/remitente y (si hay) el clip de adjuntos.
- El menú es una ventana cuadrada de 3 x 3 iconos, sin texto, que se abre encima del «⋮»: responder, a todos, reenviar · archivar, posponer, destacar · fijar, no leído, papelera (roja). Si el mensaje no está en Recibidos, el hueco de archivar pasa a «mover a Recibidos».

## 0.15.0 — 2026-11-10
- Menú «⋮» más escueto: una tira vertical de cristal solo con iconos, sin texto, que baja desde el botón (responder, a todos, reenviar · archivar, posponer, destacar, fijar, no leído, a recibidos · papelera en rojo). Los iconos de destacar y fijar se resaltan cuando están activos.
- Adjuntos: ya no hay tarjeta aparte. Si el mensaje tiene archivos, aparece un clip con el número en la píldora de arriba; al tocarlo baja una lista pequeña para abrir o compartir cada uno.

## 0.14.1 — 2026-11-10
- Se quita la tarjeta de cabecera: el asunto y el remitente ya no salen dos veces. La píldora de arriba lleva el asunto y, debajo, remitente y fecha.
- El mensaje ocupa todo el espacio. Los avisos (mensaje pospuesto, imágenes bloqueadas) y los adjuntos aparecen en una tarjeta pequeña encima del texto solo cuando hay algo que mostrar.

## 0.14.0 — 2026-11-10
- Lectura: una sola píldora flotante arriba con atrás, el asunto (algo más grande) con el remitente debajo, y el menú «⋮». El botón flotante de abajo es solo Responder. Se quita la otra variante y su ajuste.
- El mensaje gana espacio: la tarjeta de cabecera ya no repite el asunto ni el nombre; solo remitente, fecha y avisos.
- Panel de acciones más ordenado: todas las teclas del mismo tamaño en tres columnas (Responder: responder, a todos, reenviar; Organizar: archivar, posponer, destacar, fijar, no leído, mover a recibidos) y «Mover a la papelera» sola, en rojo, abajo.

## 0.13.0 — 2026-11-10
- Lectura de correo, segunda versión: todo flotante. Arriba, el botón atrás y el asunto; abajo a la derecha, responder y un círculo «⋮» que abre el panel con todas las demás acciones (ahora incluye Mover a la papelera).
- Para comparar, hay dos formas de la parte de arriba (Ajustes → Parte superior del mensaje): «Botón y píldora» (círculo de atrás + píldora con el asunto) o «Una sola píldora».
- El panel sale justo encima de los botones, al alcance del pulgar.

## 0.12.0 — 2026-11-10
- Lectura de correo con menos botones: arriba solo atrás, responder, archivar y un menú «⋮»; abajo a la derecha, un círculo para borrar (en el mismo sitio que el de redactar en la lista).
- El menú «⋮» abre un panel de cristal con todas las acciones, con nombre bajo cada icono y agrupadas en «Responder» (responder, a todos, reenviar) y «Organizar» (archivar, posponer, destacar, fijar, marcar como no leído, mover a recibidos). Tocar fuera lo cierra.
- El mensaje gana el espacio de la barra inferior de respuesta.

## 0.11.0 — 2026-11-10
- **Enviar ahora envía de verdad**: hasta ahora el botón Enviar solo guardaba el mensaje en el teléfono, no llegaba a Gmail. Ahora se entrega por la API de Gmail; si falla, queda en Borradores (y el registro dice por qué).
- Adjuntos al leer: aparecen como chips de cristal en la cabecera del mensaje. Tocar uno lo descarga (una vez) y lo abre con la app adecuada; el icono de compartir lo manda a Drive, Archivos, Telegram...
- Adjuntar al redactar: botón del clip, varios archivos, hasta 10 MB en total; se quitan con la X. Aún no se guardan en los borradores.
- Pendiente conocido: las respuestas salen como mensaje nuevo (sin conservar el hilo) y «Responder a todos» responde solo al remitente.

## 0.10.1 — 2026-11-10
- Arreglado el cierre inesperado «Couldn't read row … from CursorWindow» (visto en el registro del móvil): la lista leía los correos enteros y uno muy grande (boletines con HTML enorme) rompía la lectura. Ahora la lista solo carga el principio del texto, sin el HTML, y el mensaje completo se lee al abrirlo, por trozos. Al guardar, los textos gigantes se recortan.
- Probado en el emulador con un mensaje de 5 MB: la lista carga y el mensaje se abre entero.

## 0.10.0 — 2026-11-10
- Cabecera: el título queda centrado entre el botón del menú y la píldora y ya no se pega a ella; Ajustes pasa al menú de las tres barras (primera fila). La píldora se queda con actualizar y buscar.
- Los botones de arriba usan el mismo fondo con desenfoque que Cenotes (el fondo con colores también desenfoca), en vez de un blanco liso.
- Botón de redactar: solo el icono, redondo.
- Bandeja unificada: el menú solo ofrece Recibidos y Destacados; borradores, enviados, pospuestos, archivados, papelera y etiquetas aparecen al elegir una cuenta.
- Notificaciones: botones «Leído» y «Borrar» (actúan sobre todos los mensajes de esa notificación sin abrir la app). Posponer lo da el propio Android.
- Registro de actividad (Ajustes → Diagnóstico): sincronizaciones, notificaciones, cuentas y errores, sin texto de los mensajes. Se puede compartir como .txt, copiar o borrar.

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
