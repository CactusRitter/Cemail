# Traspaso de Cemail

Lo que necesita una sesión nueva (o tras `/clear`) además de `CLAUDE.md`. Mantener al día al cerrar cada bloque.
Última actualización: 2026-11-18.

## Estado
- Última versión publicada: **2.6.0** (versionCode 100: Redactar con campos fijos y píldora, Cuentas reordenada con predeterminada por cuenta), release `v2.6.0` en GitHub con APK; también en `G:\Mi unidad\Ce Apps\Cemail\`.
- Nada pendiente de publicar. Sin probar: Redactar y Cuentas en oscuro, Cristal 99 % y los otros diseños.

## En curso
- **Cierre libhwui (SIGSEGV ~7 s tras abrir, Cristal con transparencia 65/99 %)**, encontrado por Cenotes: tarjetas con fuente Haze CONTENT dentro de la fuente BACKGROUND (se desenfocaban a su padre). Orden de Ce Líder: comprobar Cemail.
  - Código revisado: **no hay anidado**. Solo `ui/mail/ListScreen.kt` declara fuentes (fondo = `GlassBackground` vacío con `BACKGROUND`, `LazyColumn` con `CONTENT`, hermanos). Lector, Ajustes, Redactar y menú no declaran fuentes.
  - **Resuelto (2026-11-18):** pruebas al 65 % y al 99 % (lista 16 s + lector 16 s cada una), sin SIGSEGV ni excepciones en logcat. Sin cambios de código. Ruta de prueba: demo → menú (105,228) → engranaje (664,265) → Apariencia (350,640) → deslizar → barra y≈1885 (x=995 ≈ 99 %).
- El emulador se cae solo a veces (dos veces hoy). Arrancarlo con `scripts/emulator.ps1 start` en segundo plano; se perdió el modo demo al parar la app (hay que volver a pulsar «Look around»).

## Pendiente
- **PRIORITARIO (tras el reinicio del límite semanal; no tocar antes): fallo en Redactar 2.6.0, visto por César en el OPPO.** Al hacer scroll del cuerpo, la píldora comprimida y el cuerpo «se vuelven locos»: se superponen De/Para/Asunto y el texto del mensaje, la píldora aparece sobre el cuerpo y el contenido salta (captura: «cesaragus91@gmail.com, Test» encima de «De», Asunto y «Escribe tu mensaje» mezclados). Sin pasos exactos. Sospecha: bucle entre el scroll y la compresión. En `ui/mail/ComposeScreen.kt`: `collapsed = !headerOpen && (writingBody || scrolled)` con `scrolled`/`headerOpen` guiados por `bodyScroll` (`snapshotFlow`), y `AnimatedContent` + `SizeTransform` en la cabecera; al comprimirse crece el área con scroll, lo que cambia `bodyScroll.value`/`maxValue` y puede reabrir o recomprimir. En el emulador (Pixel API 36, 25–30 líneas con teclado) no se reprodujo. Probar con teclado, mensaje largo y scroll con el dedo (no solo `adb swipe`), y revisar si la cabecera animada se solapa con el cuerpo durante la `SizeTransform`.
- **2.º PENDIENTE (tras el reinicio del límite semanal; no tocar antes): lector en el OPPO.** Al abrir el menú de acciones del mensaje (rejilla responder/reenviar/archivar…), el contenido del correo HTML (`WebView`) se pinta ENCIMA del menú: se ven logo, título y botón del correo mezclados con los iconos. Además, el cuerpo del correo sale con fondo gris plano en vez de cristal. Sospecha: orden de capas del `WebView` frente al popup (menú de acciones en `ReadScreen.kt`, `glass(... GlassKind.Menu)` sobre el `WebView`); el `WebView` es una vista nativa que puede dibujar por encima de la capa Compose del popup según el dispositivo. El gris plano puede ser el fondo opaco del `WebView` (`darkMail`/fondo del HTML) en lugar de dejar ver el cristal. No reproducido todavía.
- Revisión Cristal V2 (2026-11-18, emulador): lista y lector de los 6 diseños (claro), Ajustes → Apariencia en Cristal, transparencia 0/99 % en Cristal, y oscuro en Cristal y Brutalista (lista y lector): sin fallos. NO probado: Ajustes en los otros 5 diseños, transparencia en otro diseño. **Pendiente de César: probar la tableta.**

## Cómo se trabaja (decisiones de César que no están en el código)
- Las órdenes llegan de la sesión **Ce Líder** (local_b7aafb42-6a3f-45fc-8dac-f868a2852636). Responderle al id `local_…`, no a la dirección uds. Mensajes cortos.
- **Publicar por lotes:** acumular en «Sin publicar» y publicar solo cuando lo ordenen (salvo fallo grave). Con la orden, publicar sin preguntar.
- **Notas del release:** secciones `## en`, `## es`, `## de` (copiadas de `res/raw*/changelog.txt`), o la app las muestra mezcladas.
- Tras cada versión con cambios compartidos, avisar a **Cenotes** (local_8b5ac323-fc11-487c-b7a3-7d8205a98116).
- Archivos compartidos (`Theme.kt`, `Skin.kt`, `ui/glass/*`, `Motion.kt`): necesitan el visto bueno de Ce.
- Sin capas de compatibilidad hacia atrás, pero nunca perder datos de César.
- Hablar a César en español.

## Dispositivos
- Probar **solo en el emulador**: `export MSYS_NO_PATHCONV=1 ANDROID_SERIAL=emulator-5554` siempre. El emulador se comparte con Cenotes: avisar «libre»/«terminé».
- **Nunca** instalar en la tableta R52Y806MWSP ni en el móvil RFGL107KERL (están conectados; `connectedAndroidTest` borra la app).
- Dejar el emulador como estaba: Cemail en Cristal con 3D, escalas de animación a 1, red activada, apagado si lo estaba.
- `scripts/emulator.ps1` con `-ExecutionPolicy Bypass` y `$env:ANDROID_SERIAL='emulator-5554'`. Ajustes de la app en `shared_prefs/settings.xml` (claves `skin`, `theme_mode`, `depth_3d`), copiados con `run-as` con la app parada.

## Lecciones técnicas
- `AnimatedVisibility`/`AnimatedContent`/`animateItem` hacen el fundido en una capa del tamaño del contenido y recortan las sombras que salen de él: el hueco de la sombra tiene que ir **dentro** de la capa animada.
- Para ver animaciones: `animator_duration_scale 10` y capturas por fotograma.
