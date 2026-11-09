# Configurar OAuth (Gmail y Outlook)

Cemail funciona sin esto con cualquier cuenta IMAP con contraseña. Para que el botón "Iniciar sesión con Google / Microsoft" funcione hay que registrar la app en cada proveedor y poner los client IDs en `local.properties`:

```properties
cemail.oauth.google.clientId=XXXXXXXX.apps.googleusercontent.com
cemail.oauth.microsoft.clientId=00000000-0000-0000-0000-000000000000
```

Llegan al código como `BuildConfig.GOOGLE_OAUTH_CLIENT_ID` y `BuildConfig.MICROSOFT_OAUTH_CLIENT_ID`.

## Google (Gmail)

1. En https://console.cloud.google.com crea un proyecto "Cemail".
2. *APIs y servicios → Pantalla de consentimiento OAuth*: tipo **Externo**, estado **Pruebas**, y añade tu cuenta como usuario de prueba.
3. Añade el scope `https://mail.google.com/` (acceso IMAP/SMTP completo).
4. *Credenciales → Crear ID de cliente OAuth → Android*:
   - Nombre del paquete: `com.cemail.app`
   - Huella SHA-1 del certificado de depuración. Obtenla con:
     ```bash
     keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
     ```
   - (Más adelante, otra credencial con la SHA-1 del certificado de release / Play App Signing.)
5. Copia el client ID en `local.properties`. La redirección es `com.cemail.app:/oauth2redirect`.

> **Importante para publicar**: `https://mail.google.com/` es un scope *restringido*. En modo Pruebas funciona para hasta 100 usuarios de prueba (el token caduca a los 7 días). Para publicar en Play Store para todo el mundo, Google exige verificación de la app y una evaluación de seguridad anual (CASA). Mientras tanto, los usuarios de Gmail siempre pueden usar una contraseña de aplicación.

## Microsoft (Outlook, Hotmail, Microsoft 365)

1. En https://entra.microsoft.com → *Registros de aplicaciones → Nuevo registro*.
2. Tipos de cuenta: **Cuentas en cualquier directorio organizativo y cuentas personales de Microsoft**.
3. *Autenticación → Agregar plataforma → Aplicaciones móviles y de escritorio* → URI de redirección personalizada: `com.cemail.app://oauth2redirect`.
4. *Permisos de API → Agregar → APIs que mi organización usa → Office 365 Exchange Online* (o Microsoft Graph, permisos delegados): `IMAP.AccessAsUser.All`, `SMTP.Send`, `offline_access`, `openid`, `email`.
5. Copia el *Id. de aplicación (cliente)* en `local.properties`.

Microsoft ya no permite IMAP con contraseña normal en Outlook.com, así que OAuth es imprescindible para esas cuentas.
