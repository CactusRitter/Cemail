# Traspaso de Cemail

Lo que necesita una sesión nueva (o tras `/clear`) además de `CLAUDE.md`. Mantener al día al cerrar cada bloque.
Última actualización: 2026-11-17.

## Estado
- Última versión publicada: **2.4.1** (versionCode 97), release `v2.4.1` en GitHub con APK; también en `G:\Mi unidad\Ce Apps\Cemail\`.
- `main` limpio y subido. Lo hecho después de 2.4.1 está en `CHANGELOG.md` → «Sin publicar».

## En curso
- Nada.

## Pendiente
- «Sin publicar»: sombra de la barra de formato de Redactar (commit edd4798). Se publica cuando César lo ordene.

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
