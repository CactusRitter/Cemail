# Cemail

Cliente de correo para Android. Cuentas IMAP/SMTP de cualquier proveedor, con inicio de sesión OAuth 2.0 para Gmail y Outlook.

- Paquete / applicationId: `com.cemail.app`
- Kotlin + Jetpack Compose (Material 3), un único módulo `:app`
- minSdk 26 · compileSdk 37 (lo exigen las librerías AndroidX actuales) · targetSdk 36 · Java 17
- AGP 9.5 (Kotlin integrado: **no** se aplica `org.jetbrains.kotlin.android`) · Gradle 9.9 · Kotlin 2.4
- Las versiones de todas las dependencias están en [gradle/libs.versions.toml](gradle/libs.versions.toml); no pongas versiones en los `build.gradle.kts`.

Documentación de diseño:
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md): capas, paquetes, modelo de datos y sincronización
- [docs/ROADMAP.md](docs/ROADMAP.md): fases de la v1 y su estado
- [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md): cómo registrar los client IDs de Google y Microsoft

## Comandos

```bash
./gradlew assembleDebug          # compilar APK de depuración
./gradlew installDebug           # instalar en el emulador/dispositivo conectado
./gradlew testDebugUnitTest      # tests unitarios (JVM)
./gradlew connectedDebugAndroidTest  # tests instrumentados (requiere emulador)
./gradlew lintDebug              # lint de Android
```

En Windows, `gradlew.bat`. Emulador disponible: `Pixel_API_36`. El SDK está en `%LOCALAPPDATA%\Android\Sdk` (ver `local.properties`).

## Reglas del proyecto

### Idiomas (obligatorio)
- La app usa el idioma del sistema. **Ningún texto visible al usuario puede ir escrito en el código**: todo en `res/values*/strings.xml` y se usa con `stringResource(...)` / `context.getString(...)`.
- `values/strings.xml` es inglés (idioma por defecto y de reserva). `values-es/strings.xml` es español. Cada clave nueva se añade **a la vez en todos** los `values-xx`.
- Plurales con `<plurals>`, nunca concatenando. Textos con variables con `%1$s`, no con concatenación.
- Fechas, horas y tamaños de archivo con los formateadores de la plataforma según el locale (`DateUtils`, `java.time.format` con `FormatStyle`, `Formatter.formatShortFileSize`).
- Añadir un idioma nuevo = crear `values-xx/strings.xml`. `generateLocaleConfig` registra el idioma automáticamente para el selector de idioma por app de Android 13+.
- Diseño compatible con RTL (`start`/`end`, nunca `left`/`right`).

### Arquitectura
- Offline-first: **Room es la única fuente de verdad** para la UI. La UI observa `Flow`s de la base de datos; la red solo escribe en la base de datos.
- Capas: `ui` → `domain` → `data`. La UI nunca toca Jakarta Mail ni Room directamente.
- Inyección de dependencias con Hilt. ViewModels con `@HiltViewModel`; Workers con `@HiltWorker`.
- Toda E/S de red (IMAP/SMTP) en `Dispatchers.IO`. Jakarta Mail es bloqueante: envuélvelo, no lo llames desde el hilo principal.
- El trabajo en segundo plano (sincronización, envío diferido) va con WorkManager.

### Seguridad y privacidad
- Contraseñas y tokens OAuth cifrados con una clave del Android Keystore (AES-GCM). Nunca en texto plano, nunca en logs.
- El HTML de los correos se sanea con jsoup y se muestra en un `WebView` con JavaScript desactivado. Las imágenes remotas se bloquean por defecto (pixeles de rastreo).
- Solo conexiones TLS (IMAPS 993 / SMTPS 465 o STARTTLS 587). No se acepta un certificado inválido sin que el usuario lo confirme explícitamente.
- La copia de seguridad en la nube está desactivada (`data_extraction_rules.xml`).
- Los client IDs de OAuth van en `local.properties` (no en git) y llegan al código vía `BuildConfig`.

### Estilo de código
- Identificadores, nombres de clases y comentarios de API en inglés; comentarios explicativos pueden ir en español.
- Estilo oficial de Kotlin (`.editorconfig`). Compose: un `@Composable` sin estado por pantalla + su ViewModel; `@Preview` para los componentes.
- Tests: lógica de dominio y parseo con JUnit; DAO de Room con tests instrumentados. Para IMAP/SMTP usa dobles de prueba de las interfaces de `data/mail`.
