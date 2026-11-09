# Cemail

Aplicación de correo para Android: cuentas IMAP/SMTP de cualquier proveedor, inicio de sesión con Google y Microsoft, varias cuentas con bandeja unificada, adjuntos, búsqueda y notificaciones. Interfaz en el idioma del sistema (inglés y español por ahora).

## Requisitos
- Android Studio 2026.2 o posterior (incluye JDK)
- Android SDK 36
- Para OAuth: ver [docs/OAUTH_SETUP.md](docs/OAUTH_SETUP.md)

## Empezar
1. Abre esta carpeta en Android Studio.
2. Si no existe, crea `local.properties` con `sdk.dir=...` (Android Studio lo hace solo).
3. Ejecuta la configuración `app` en el emulador `Pixel_API_36`.

Desde la terminal:

```bash
./gradlew assembleDebug
```

Más detalles en [CLAUDE.md](CLAUDE.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) y [docs/ROADMAP.md](docs/ROADMAP.md).
