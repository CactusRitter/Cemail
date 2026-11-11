# Historial de versiones de Cemail

Siempre por debajo de 1.0: la 1.0 será cuando funcionen todas las funciones principales.
Cada versión se guarda en Google Drive (`Mi unidad/Cemail/Cemail-x.y.z.apk`) y no se reemplaza la anterior.

## 0.33.1 — 2026-11-11
- Al abrir un correo, este **crece desde la tarjeta que tocas** hasta ocupar la pantalla, con un muelle suave, mientras la lista se desvanece detrás (antes aparecía de golpe). Al cerrarlo ocurre lo opuesto: **se pliega de vuelta hasta su tarjeta** mientras la lista aparece detrás.
- El título de la bandeja («Recibidos», «Inbox», etiquetas…) está ahora **centrado de verdad en la pantalla**, aunque el botón de la izquierda y el de la derecha midan distinto.

## 0.33.0 — 2026-11-11
Notificaciones instantáneas (push) para Gmail, y mejoras de los gestos.
- Arreglado **Deshacer**: el gesto de deslizar podía disparar la acción dos veces y la segunda borraba el punto de deshacer; además, la sincronización que provoca el push podía devolver el estado antiguo justo después de pulsar Deshacer. Ahora una acción que no cambia nada no toca el deshacer, los cambios hacia Gmail se envían en orden y la sincronización ignora los mensajes tocados hace unos segundos.
- Arreglado el envío diferido/con deshacer: el trabajo que enviaba el correo se cancelaba a sí mismo en bucle y no llegaba a salir. Probado de punta a punta: el aviso llega unos 4 segundos después de enviar.
- La confirmación al deslizar es ahora una pregunta corta («¿Archivar?», «¿Borrar?»…) con Sí / No.
- **Push de verdad**: Gmail avisa al móvil en el momento (Pub/Sub + un Worker gratuito de Cloudflare + Firebase Cloud Messaging), sin esperar a la comprobación de 15 minutos. Interruptor «Notificaciones instantáneas» en Ajustes → Notificaciones y sincronización. Ver `docs/PUSH.md`. Sin facturación y sin Analytics.
- Gestos: opción **Confirmar antes de actuar** (pregunta antes de archivar, borrar…) y **Tiempo para deshacer** configurable (3, 5, 8 o 12 s). Destacar con el gesto también se puede deshacer.
- Política de privacidad actualizada con el relé de notificaciones.

## 0.32.1 — 2026-11-11
- El logotipo de Google de la pantalla «Añadir una cuenta» se parece más al real: la «G» queda abierta arriba a la derecha, con la barra azul.

## 0.32.0 — 2026-11-11
Ajustes reorganizados, pantalla para añadir cuentas, ajustes por cuenta y gestos configurables.
- Ajustes ahora es una pantalla corta con una fila grande por sección: Cuentas, Mensajes, Gestos de deslizar, Notificaciones y sincronización, Apariencia y Diagnóstico.
- Cuentas: lista de cuentas con un único botón «Añadir», que abre una pantalla con botones grandes de Google, Microsoft e IMAP (con sus logotipos).
- Cada cuenta tiene sus propios ajustes: firma (texto, HTML pegado del portapapeles o importado de .eml/.html), frecuencia de comprobación propia (o la general) y, en Gmail, qué etiquetas se sincronizan.
- Gestos de deslizar configurables: acción para cada lado (archivar, papelera, leído/no leído, spam, destacar, posponer o nada; puede ser la misma en los dos) y cuánto hay que deslizar (corto, normal, largo; por defecto hay que deslizar algo más que antes para evitar accidentes).
- El trabajo en segundo plano se despierta con el intervalo más corto de las cuentas y cada cuenta respeta el suyo.

## 0.31.3 — 2026-11-11
Tirar para actualizar más natural.
- Al tirar hacia abajo, los mensajes bajan con el dedo (hasta un poco más allá del umbral) y el indicador queda en el hueco; al soltar pasado el umbral se actualiza y la lista vuelve a su sitio con un muelle.
- Un pequeño toque háptico avisa al llegar al umbral.
- Comprobado en el OPPO y en el Samsung.

## 0.31.2 — 2026-11-11
Detalles tras probar en el Samsung con las cuentas reales.
- El botón «atrás» desde cualquier carpeta o etiqueta vuelve primero a Recibidos (antes salía de la app).
- La papelera ya no muestra el contador de no leídos.
- Comprobado en el móvil: tirar para actualizar, el menú lateral con las carpetas nuevas (Programados, Spam), la búsqueda por texto completo, el menú de acciones del mensaje y la ventana de «Enviar más tarde».

## 0.31.1 — 2026-11-11
- **Tirar hacia abajo para actualizar** en todas las bandejas y etiquetas: el indicador (con el cristal de la app) solo sale cuando lo pides tú, no en las comprobaciones automáticas, y no funciona mientras buscas o seleccionas.

## 0.31.0 — 2026-11-11
Animaciones nuevas y coherentes, todas con resortes (documentadas en docs/DESIGN.md, «Movimiento»).
- Los botones se encogen al pulsarlos y vuelven con rebote; los botones flotantes (redactar, responder, menú) aparecen desde el centro creciendo hasta su tamaño.
- Las ventanas salen de su botón (el menú de acciones del mensaje, etiquetas, adjuntos) y los iconos del menú aparecen en cascada; posponer y enviar más tarde crecen desde el centro.
- Las pantallas ya no cambian de golpe: la nueva crece con un rebote suave y la anterior se desvanece (y al volver atrás, al revés).
- El menú lateral hace aparecer sus filas una tras otra cada vez que se abre.
- La lista mueve los mensajes con resorte al reordenarse o desaparecer; la barra superior (buscar, seleccionar) cambia con un pequeño pop; la estrella da un «pop» al marcarla.
- El aviso de deshacer sube con rebote y baja suave.

## 0.30.1 — 2026-11-11
- **Menú lateral rehecho** (como referencia, el de Samsung Email): todas las cuentas a la vista, cada una con su color y una marca en la activa, y «Todas las cuentas» al final de la lista; los ajustes pasan a un icono pequeño arriba a la derecha y una línea separa las cuentas de las carpetas. A diferencia de Samsung, tocar una cuenta no cierra el menú: se queda abierto con las carpetas y etiquetas de esa cuenta.

## 0.30.0 — 2026-11-10
Cinco funciones nuevas para el uso diario.
- **Posponer de verdad:** a la hora elegida el mensaje vuelve a la bandeja como no leído y Cemail avisa con una notificación (también con la app cerrada, con WorkManager).
- **Spam y vaciar papelera:** carpeta Spam en el menú (en Gmail, Microsoft e IMAP), acción «Spam» en el menú del mensaje (en la carpeta Spam sirve para sacarlo) y botón «Vaciar la papelera» / «Vaciar el spam» con confirmación. En Gmail el borrado definitivo necesita un permiso más amplio que el actual: hasta pedirlo, avisa de que el servidor lo rechazó.
- **Deshacer envío y envío programado:** un mensaje espera en una bandeja de salida guardada en el teléfono (5, 10, 20 o 30 s, o desactivado, en Ajustes) y puede deshacerse devolviéndolo al editor; sale igualmente si cierras la app. Botón del reloj en la redacción para elegir hora (más tarde hoy, mañana, fin de semana, semana que viene, o fecha y hora propias). Carpeta «Programados» en el menú; tocar uno lo abre para editarlo.
- **Búsqueda sin conexión en todo el texto** (Room FTS4, sin distinguir mayúsculas ni acentos): encuentra cualquier palabra del mensaje, no solo del principio, en todas las cuentas. Los operadores (`from:`, `is:unread`) siguen funcionando como antes.
- **Cancelar suscripción en un toque** (cabecera `List-Unsubscribe`: un clic, página web o mensaje) y **silenciar conversación** (archiva el hilo y los mensajes nuevos de él), ambos en el menú ⋮ del mensaje.
- **Añadir cuenta pasa a Ajustes** (Google, Microsoft u otra) y el menú lateral queda más ligero; al tocar una cuenta el menú ya no se cierra, para elegir enseguida sus etiquetas.
- Base de datos a la v10 (columna de envío, búsqueda de texto completo y conversaciones silenciadas). Tests nuevos de búsqueda, cancelar suscripción, etiquetas de spam y caché.

## 0.29.1 — 2026-11-10
- **Arreglo de la sincronización al arrancar:** la pantalla que lanzaba la sincronización la cancelaba al cambiar las cuentas mostradas, la app lo contaba como fallo y la pantalla nueva, al ver que ya había una en marcha, no hacía otra: no se sincronizaba nada hasta la siguiente comprobación automática (y salía el aviso de «no se pudo sincronizar»). Ahora la sincronización corre en el ámbito de la app y no depende de la pantalla que la pidió; una cancelación ya no se cuenta como error.

## 0.29.0 — 2026-11-10
- **Imágenes incrustadas** (las que viajan dentro del correo, `cid:`) también en las cuentas Microsoft e IMAP: se descargan al abrir el mensaje, se guardan en la caché del teléfono y se muestran siempre, como en Gmail.
- Tests nuevos de la caché de imágenes.
- Revisión de reglas del proyecto: sin colores fijos fuera del sistema de diseño y sin textos escritos en el código.

## 0.28.1 — 2026-11-10
- Microsoft: se prueban primero las direcciones IPv4 y se reintenta una vez si la primera conexión falla (en algunas redes la ruta IPv6 a Microsoft no funciona y la primera sincronización fallaba).
- Un borrador se abre para editarlo desde cualquier lista (también desde la búsqueda), no solo desde Borradores.

## 0.28.0 — 2026-11-10
- **Borradores completos (en el teléfono):** al guardar un borrador se conservan ahora Cc, Cco, el formato (negrita, listas…) y los datos de la respuesta (hilo, In-Reply-To, References); al reabrirlo vuelve todo. Los adjuntos siguen sin guardarse (el aviso lo dice). Base de datos v7.
- Aviso de error de sincronización más claro: dice que fue alguna cuenta y dónde mirar los detalles (Ajustes, Registro de actividad).

## 0.27.0 — 2026-11-10
Cuentas Microsoft (Outlook.com, Hotmail, Microsoft 365) con Microsoft Graph, e IMAP (ver 0.26.0). Primera versión: sin probar todavía con una cuenta real.
- App registrada en Azure («Cemail», cuentas personales y de empresa, cliente público con PKCE y sin secreto). El identificador va en `local.properties` (`cemail.oauth.microsoft.clientId`) y llega por `BuildConfig`.
- Nuevas opciones «Microsoft» en el menú de cuentas y «Iniciar sesión con Microsoft» en la pantalla de inicio. Permisos: leer y escribir el correo, enviar correo y perfil básico.
- Sincronización por carpetas (Entrada, Enviados, Borradores, Papelera, Archivo), con los hilos de Microsoft (`conversationId`) en la tarjeta de conversación; leer, destacar, archivar y borrar; enviar (se guarda en Enviados), responder, guardar como .eml y adjuntos.
- Aún sin: imágenes incrustadas, categorías como etiquetas y sincronización con consultas delta.
- AppAuth atiende ahora dos esquemas de retorno (Google y Microsoft).

## 0.26.0 — 2026-11-10 (no repartida: va dentro de la 0.27.0)
Cuentas IMAP/SMTP (Yahoo, iCloud, GMX, servidores propios…). Primera versión: sin probar todavía con una cuenta real.
- Nueva opción «Otra cuenta (IMAP)» en el menú de cuentas y en la pantalla de inicio: correo y contraseña (o contraseña de aplicación); los servidores de los proveedores conocidos se rellenan solos y, si no se reconocen, se pueden escribir a mano (servidor, puerto, SSL/TLS o STARTTLS). La conexión se comprueba antes de guardar la cuenta.
- Los ajustes y la contraseña se guardan cifrados con el Keystore. La base de datos pasa a la v6 (tipo de proveedor por cuenta).
- Sincronización por carpetas (Entrada, Enviados, Borradores, Papelera, Archivo, detectadas por SPECIAL-USE o por nombre), por UID y UIDVALIDITY; lee, marca, destaca, archiva y borra moviendo entre carpetas.
- Enviar por SMTP con copia en Enviados, responder (con los encabezados del original), guardar como .eml y ver/abrir adjuntos.
- Aún sin: imágenes incrustadas, etiquetas, hilos y búsqueda en el servidor para IMAP.
- Tests nuevos de los servidores conocidos y del cifrado de ajustes.

## 0.25.2 — 2026-11-10
- Lint sin errores: textos leídos con los recursos que respetan el cambio de idioma/configuración (responder, ajustes) y limpieza de una marca de orden de bytes en el importador de firma.

## 0.25.1 — 2026-11-10
- «Etiquetas» del mensaje: ahora usa las etiquetas de la cuenta a la que pertenece el mensaje también desde la bandeja unificada (antes salía vacío).
- Firma HTML en la redacción: la ✕ para quitarla va en su propia fila encima, sin tapar los iconos.
- El registro de actividad también se copia a logcat (sin textos de correo) para poder seguir la sincronización con el cable.

## 0.25.0 — 2026-11-10
Cierre del bloque B: sincronización incremental, conversaciones, etiquetas desde el mensaje y contactos.
- **Sincronización incremental** con el historial de Gmail (`history`): solo se descarga lo que cambió (correos nuevos, etiquetas, borrados), en pocas peticiones. Cada 6 horas, o si Gmail ya no guarda tan atrás, se hace un listado completo. Los mensajes que pasan a spam se retiran de la copia local.
- **Conversaciones**: cada mensaje guarda el hilo de Gmail (migración de la base de datos a la v5; los mensajes se vuelven a bajar una vez). Al abrir un mensaje aparece «N mensajes más en esta conversación»; se despliega y un toque abre cualquiera.
- **Etiquetas desde el mensaje**: nueva acción «Etiquetas» en el menú ⋮ para poner y quitar tus etiquetas de Gmail (con deshacer implícito al cambiarlas de nuevo).
- **Contactos**: al escribir un destinatario se sugieren direcciones de tus contactos (permiso pedido una sola vez) y de la gente que te ha escrito.
- La causa de los dos cierres inesperados del registro del móvil (lectura de la base de datos con un correo enorme) ya estaba corregida desde antes; no ha habido más desde entonces.
- Tests nuevos: etiquetas enviadas a Gmail y remitentes sugeridos.

## 0.24.1 — 2026-11-10
Arreglos tras la prueba de envío real desde el móvil (correo con negrita y adjunto entre dos cuentas: llegó bien).
- La parte de texto plano de un correo ya no lleva las marcas `*negrita*`: se saca del HTML del editor, con viñetas (`• `) y numeración (`1. `) en las listas. Se nota en la vista previa de la lista y en los clientes que enseñan el texto plano.
- Pantalla de redacción: el autocompletado de Android ya no escribe a la vez tu correo en Para, Cc, Cco, asunto y cuerpo (la pantalla se excluye de Autofill).
- Campos de destinatarios: el teclado ya no autocorrige mientras se escribe una dirección.
- Tests nuevos del texto plano (negrita, párrafos, listas, entidades).

## 0.24.0 — 2026-11-10
- El menú de las tres barras se cierra al tocar fuera de él (antes solo con el botón atrás: el panel transparente se comía los toques).
- Botones flotantes de la lectura (responder y «⋮»): sin la sombra que se transparentaba por el cristal y dejaba formas raras sobre el mensaje; ahora llevan una base opaca bajo el cristal.
- Firma HTML: probada con una firma real de Gmail (tablas anidadas, línea de color, iconos enlazados desde internet); en la parte de texto cada línea de la firma va en su sitio.

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
