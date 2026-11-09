# Cemail

Cliente de correo **solo para Gmail** (de momento) para Android. Forma parte de las apps "Ce" de César Caballero y su amigo Jonathan, junto con **Celander** (calendario) y **Cenotes** (notas): las tres comparten el mismo diseño. No se publica en Play Store: es para uso propio y de amigos (APK).

- Paquete / applicationId: `com.cemail.app`
- Kotlin + Jetpack Compose (Material 3), un único módulo `:app`
- minSdk 26 · compileSdk 37 (lo exigen las librerías AndroidX actuales) · targetSdk 36 · Java 17
- AGP 9.5 (Kotlin integrado: **no** se aplica `org.jetbrains.kotlin.android`) · Gradle 9.9 · Kotlin 2.4
- Las versiones de todas las dependencias están en [gradle/libs.versions.toml](gradle/libs.versions.toml); no pongas versiones en los `build.gradle.kts`.

Documentación:
- [docs/DESIGN.md](docs/DESIGN.md): diseño compartido con Celander y Cenotes (**leer antes de tocar la interfaz**)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): decisiones, capas, modelo de datos y sincronización
- [docs/ROADMAP.md](docs/ROADMAP.md): fases de la v1 y su estado
- [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md): registro en Google y firma del APK

## Comandos

```bash
./gradlew assembleDebug          # compilar APK de depuración
./gradlew installDebug           # instalar en el emulador/dispositivo conectado
./gradlew testDebugUnitTest      # tests unitarios (JVM)
./gradlew connectedDebugAndroidTest  # tests instrumentados (requiere emulador)
./gradlew lintDebug              # lint de Android
./gradlew assembleRelease        # APK firmado (necesita keystore.properties)
```

En Windows, `gradlew.bat`. Emulador disponible: `Pixel_API_36`. El SDK está en `%LOCALAPPDATA%\Android\Sdk` (ver `local.properties`).

### Emulador sin ventana
Usa siempre [scripts/emulator.ps1](scripts/emulator.ps1) en lugar de lanzar `emulator.exe` a mano: arranca el emulador **oculto y sin ventana ni consola** (`-no-window`), y las capturas de pantalla funcionan igual.

```bash
powershell -File scripts/emulator.ps1 start          # arrancar (≈45 s)
powershell -File scripts/emulator.ps1 shot out.png   # captura de pantalla
powershell -File scripts/emulator.ps1 stop           # apagar al terminar
powershell -File scripts/emulator.ps1 window         # arrancar CON ventana, solo si el usuario quiere verlo
```

## Reglas del proyecto

### Diseño (obligatorio)
- Misma identidad visual que Celander (versión 1.07) y Cenotes: cristal esmerilado, resortes, acentos, claro/oscuro. Referencia en código: `D:\cesar\Documents\Claude Projects\Cenotes` (`ui/theme/Theme.kt`, `ui/glass/Glass.kt`).
- Usa `CemailTheme`, `Modifier.glass(...)`, `GlassBackground`, `GlassPill`, etc. No inventes colores, radios ni animaciones nuevas: si falta algo, se añade al sistema de diseño y se avisa.
- Nada de colores fijos en las pantallas: siempre `MaterialTheme.colorScheme` y `LocalGlass`.

### Idiomas (obligatorio)
- La app usa el idioma del sistema. **Ningún texto visible al usuario puede ir escrito en el código**: todo en `res/values*/strings.xml` y se usa con `stringResource(...)` / `context.getString(...)`.
- `values/strings.xml` es inglés (idioma por defecto y de reserva). `values-es/strings.xml` es español. Cada clave nueva se añade **a la vez en todos** los `values-xx`.
- Plurales con `<plurals>`, nunca concatenando. Textos con variables con `%1$s`, no con concatenación.
- Fechas, horas y tamaños de archivo con los formateadores de la plataforma según el locale (`DateUtils`, `java.time.format` con `FormatStyle`, `Formatter.formatShortFileSize`).
- Añadir un idioma nuevo = crear `values-xx/strings.xml`. `generateLocaleConfig` registra el idioma automáticamente para el selector de idioma por app de Android 13+.
- Diseño compatible con RTL (`start`/`end`, nunca `left`/`right`).

### Arquitectura
- Simple, como Cenotes: **sin Hilt**. Las dependencias se crean de forma perezosa en `CemailApplication`; los ViewModels usan un `ViewModelProvider.Factory`. Navegación con estado de Compose, sin `navigation-compose`.
- Offline-first: **Room es la única fuente de verdad** para la UI. La UI observa `Flow`s de la base de datos; la red solo escribe en la base de datos.
- La UI nunca llama a la API de Gmail ni a Room directamente: pasa por los repositorios.
- Toda E/S de red en `Dispatchers.IO` (OkHttp síncrono, o `suspend` con `await`).
- El trabajo en segundo plano (sincronización, envío pendiente) va con WorkManager.

### Seguridad y privacidad
- Tokens OAuth cifrados con una clave del Android Keystore (AES-GCM). Nunca en texto plano, nunca en logs.
- El HTML de los correos se sanea con jsoup y se muestra en un `WebView` con JavaScript desactivado. Las imágenes remotas se bloquean por defecto (píxeles de rastreo).
- Solo HTTPS. Sin copia de seguridad en la nube (`data_extraction_rules.xml`).
- El ID de cliente de OAuth va en `local.properties` y la firma en `keystore.properties` (ninguno se sube a git) y llega al código vía `BuildConfig`.

### Estilo de código
- Identificadores, nombres de clases y comentarios de API en inglés; comentarios explicativos pueden ir en español.
- Estilo oficial de Kotlin (`.editorconfig`). Compose: un `@Composable` sin estado por pantalla + su ViewModel; `@Preview` para los componentes.
- Tests: lógica de dominio y parseo (MIME, respuestas de Gmail) con JUnit; DAO de Room con tests instrumentados. Para la API de Gmail usa dobles de prueba de la interfaz de `data/gmail`.
