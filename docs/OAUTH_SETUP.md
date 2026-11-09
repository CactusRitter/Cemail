# Configurar el inicio de sesión con Google

Cemail se publica solo para uso propio y de amigos (se instala con un APK, sin Play Store). Solo admite **Gmail**; Outlook se deja para más adelante.

## Estado (hecho el 2026-11-09)
Todo lo de abajo **ya está hecho** para tu cuenta:
- Proyecto de Google Cloud **Cemail** (`cemail-514220`), con la **Gmail API** habilitada. (Tienes otros proyectos, como "Celander"; no se tocan.)
- Pantalla de consentimiento: nombre "Cemail", público **externo**, permisos `openid`, `userinfo.email` y `gmail.modify`.
- Cliente OAuth **Android** `Cemail Android` (paquete `com.cemail.app`, SHA-1 de la clave de release, esquema de URI personalizado habilitado). Su ID ya está en `local.properties`.
- Clave de firma `cemail-release.jks` + `keystore.properties` creadas en la raíz del proyecto (ignoradas por git). **Haz copia de seguridad de ambas.**
- Las compilaciones de **depuración también se firman con esa clave**, así que un solo ID de cliente sirve para probar en el emulador y para el APK de los amigos.
- Estado de publicación: **In production, sin verificar** (hecho el 2026-11-09). Google pidió una página principal y una política de privacidad, así que se creó una web en GitHub Pages (gratis): inicio `https://cactusritter.github.io/Cemail/` y política `https://cactusritter.github.io/Cemail/privacy/` (fuente en `docs/index.md` y `docs/privacy.md`, repositorio público `CactusRitter/Cemail`). Se añadió `cactusritter.github.io` como dominio autorizado. La consola muestra "tu app requiere verificación": se ignora, no se enviará a revisión. Consecuencias: al iniciar sesión sale el aviso "Google no ha verificado esta aplicación" (*Avanzado → Ir a Cemail*), tope de 100 usuarios, y la sesión ya no caduca a los 7 días.
- Si se cambia el nombre de usuario de GitHub o el repositorio, hay que actualizar esas URLs y el dominio autorizado en *Información de la marca*.
- Pendiente: probar el inicio de sesión real en la fase 1. Google puede tardar entre 5 minutos y unas horas en aplicar un cliente nuevo.

El resto del documento explica cómo se hizo, por si hay que repetirlo.

Hay que hacerlo **una vez**, antes de la fase 1 del [ROADMAP](ROADMAP.md). Al terminar tendrás un *ID de cliente* que se pone en `local.properties`:

```properties
cemail.oauth.google.clientId=XXXXXXXX-yyyyyyyy.apps.googleusercontent.com
```

Llega al código como `BuildConfig.GOOGLE_OAUTH_CLIENT_ID`. El esquema de redirección del manifiesto se calcula solo a partir de él (`com.googleusercontent.apps.<ID>`), que es lo que Google exige para clientes Android.

## 0. Una clave de firma propia

El ID de cliente de Google para Android está atado a la huella **SHA-1** del certificado con el que se firma el APK. El certificado de depuración es distinto en cada ordenador, así que para que el APK que pases a tus amigos funcione, fírmalo siempre con la misma clave.

1. Crea la clave (te pedirá una contraseña y tus datos):
   ```bash
   keytool -genkeypair -v -keystore cemail-release.jks -alias cemail -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Guarda `cemail-release.jks` y su contraseña en un sitio seguro, **con copia de seguridad**. Si la pierdes, tus amigos tendrán que desinstalar e instalar de nuevo. Está en `.gitignore`: no se sube a git.
3. Crea `keystore.properties` en la raíz del proyecto (también ignorado por git):
   ```properties
   storeFile=cemail-release.jks
   storePassword=TU_CONTRASEÑA
   keyAlias=cemail
   keyPassword=TU_CONTRASEÑA
   ```
   Con ese archivo, `./gradlew assembleRelease` genera el APK firmado.
4. Obtén las huellas SHA-1 (la de release y la de depuración, para probar desde Android Studio):
   ```bash
   keytool -list -v -keystore cemail-release.jks -alias cemail
   keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```

## 1. Proyecto en Google Cloud

1. En https://console.cloud.google.com crea un proyecto "Cemail".
2. *APIs y servicios → Biblioteca*: busca **Gmail API** y pulsa **Habilitar**.
3. *Google Auth Platform → Branding*: nombre "Cemail" y tu correo de contacto.
4. *Público (Audience)*: tipo **Externo**.
5. *Acceso a los datos (Data access) → Añadir o quitar permisos*: añade `.../auth/gmail.modify` (leer, redactar, enviar, archivar y mover a la papelera; no borra de forma permanente) y los básicos `openid` y `email`.
6. *Clientes → Crear cliente → Android*:
   - Nombre del paquete: `com.cemail.app`
   - SHA-1: la de tu clave de release (puedes crear otro cliente con la de depuración para probar).
   - En *Configuración avanzada* activa **"Habilitar esquema de URI personalizado"**.
7. Copia el ID de cliente en `local.properties`.

> Como `app/build.gradle.kts` firma también el APK de depuración con la clave de release (cuando existe `keystore.properties`), basta con un único cliente. Si alguien compila sin esa clave, el APK saldrá firmado con su clave de depuración local y el inicio de sesión de Google fallará.

## 2. Estado de publicación: elige uno

| | Testing (por defecto) | In production sin verificar (recomendado) |
|---|---|---|
| Quién puede entrar | Solo los correos que añadas como *usuarios de prueba* (máx. 100) | Cualquier cuenta de Google, hasta 100 usuarios nuevos |
| Caducidad de sesión | **A los 7 días hay que volver a iniciar sesión** | No caduca por esto |
| Pantalla de aviso | Sí | Sí: "Google no ha verificado esta aplicación" → *Avanzado → Ir a Cemail (no seguro)* |

Para ti y unos pocos amigos, pasa la app a **In production** (botón *Publicar app*) sin enviarla a verificación. Solo sale el aviso al iniciar sesión y existe el tope de 100 usuarios. Si prefieres no hacerlo, deja Testing y añade a cada amigo en *Usuarios de prueba*, sabiendo que cada semana tendrá que volver a entrar.

## Más adelante: Outlook
Microsoft ya no permite IMAP con contraseña en Outlook.com, así que requerirá su propio registro en https://entra.microsoft.com (OAuth con Microsoft Graph o IMAP XOAUTH2). Se documentará cuando se decida hacerlo.
