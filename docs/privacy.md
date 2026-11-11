---
title: Privacy Policy - Cemail
permalink: /privacy/
---

# Privacy Policy

*Last updated: 11 November 2026* · [Español](#español)

Cemail ("the app") is an Android email client for Gmail, developed by César Caballero ("I", "me"). This policy explains what information the app accesses, how it is used and stored, and your choices. Cemail is a small personal project distributed to a few people as an APK; it is not published on Google Play.

## Summary
- Cemail talks **directly** between your phone and Google. The only server of mine is an optional, free relay for instant notifications (see below) that never receives your messages.
- Your email stays **on your device**. I never see it, store it, sell it or share it.
- No advertising, no analytics, no tracking, no crash-reporting services.

## Information the app accesses
When you sign in with Google, you grant the app these permissions:

| Permission | What it is used for |
|---|---|
| Your Google account email address and basic profile (`openid`, `email`, `profile`) | To show which account is signed in and to tell accounts apart, with your name and profile picture. The picture is downloaded once and kept only on your phone. |
| Gmail access (`https://www.googleapis.com/auth/gmail.modify`) | To read your messages, labels and attachments; to compose and send messages; and to mark as read, star, archive and move messages to the trash. The app cannot permanently delete messages. |

The app uses this access only to provide the features you see in the app, at your request.

## How information is stored
- Messages, labels and attachments you open are cached in a **local database on your phone** so the app works quickly and offline.
- Sign-in tokens are stored on your phone **encrypted** with a key kept in the Android Keystore.
- What you set for each account in Settings (its picture, the name it goes by, the name you send with and its colour) stays on your phone. A picture you pick from your gallery is copied, small, into the app; the original is not changed.
- Cloud backup of the app's data is disabled.
- Nothing is sent to me or to any third party, except what the optional instant-notification relay receives (see below). The only network connections the app makes are to Google's services (for example `accounts.google.com`, `oauth2.googleapis.com`, `gmail.googleapis.com`, and `openidconnect.googleapis.com` / `googleusercontent.com` for the profile picture), to Microsoft's (`login.microsoftonline.com`, `graph.microsoft.com`) if you add a Microsoft account, and, only if you choose to show remote images in a message, to the servers hosting those images. Remote images are blocked by default.

## Instant notifications (optional)
If you leave "Instant notifications" on (Settings → Notifications and sync), Gmail (and Microsoft) tell your phone when mail arrives instead of the app checking every few minutes. For that to work:
- The app asks Gmail to publish a "something changed" note to a Google Cloud Pub/Sub topic of this project. The note holds only your address and a history number, no message content.
- For Microsoft accounts the app asks Microsoft Graph to send a change notification to the same relay when mail arrives in your Inbox. It holds only a subscription id and the id of the new message, no message content.
- A small relay that I run on Cloudflare Workers (free plan) receives that note and wakes your phone through Firebase Cloud Messaging (Google).
- The relay stores, for as long as the feature is on, your **email address**, a random secret that identifies your Microsoft subscription (Microsoft accounts only) and your phone's **Firebase Cloud Messaging token** (an anonymous identifier of the app on your phone). It never receives or stores message subjects, bodies, attachments, contacts or Google sign-in tokens.
- Firebase Analytics is not used. You can turn the feature off at any time: the app stops Gmail's and Microsoft's notifications and removes your address and token from the relay.

## Sharing
I do not sell, rent, share or transfer your data to anyone. Google receives the requests the app makes to its services, as described in [Google's Privacy Policy](https://policies.google.com/privacy).

## Google API Services User Data Policy
Cemail's use and transfer of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements. In particular, the data is used only to provide and improve the user-facing features of the app, is not used for advertising, and is not read by humans except where you explicitly share it or it is required for security or by law.

## Retention and deletion
- Data stays on your phone until you remove the account in the app or uninstall it. Removing an account deletes its local messages and tokens.
- You can revoke Cemail's access to your Google account at any time at [myaccount.google.com/permissions](https://myaccount.google.com/permissions).

## Children
The app is not directed at children under 13 and I do not knowingly collect information from them.

## Changes
If this policy changes, the new version will be published on this page with an updated date.

## Contact
César Caballero · cesar.a.caballerot@gmail.com

---

# Política de privacidad {#español}

*Última actualización: 11 de noviembre de 2026* · [English](#privacy-policy)

Cemail ("la app") es un cliente de correo para Android que funciona con Gmail, desarrollado por César Caballero ("yo"). Esta política explica a qué información accede la app, cómo se usa y se guarda, y qué puedes decidir tú. Cemail es un pequeño proyecto personal que se reparte como APK a unas pocas personas; no está publicada en Google Play.

## Resumen
- Cemail se comunica **directamente** entre tu teléfono y Google. El único servidor mío es un servicio gratuito y opcional para las notificaciones instantáneas (ver más abajo) que nunca recibe tus mensajes.
- Tu correo se queda **en tu dispositivo**. Yo nunca lo veo, lo guardo, lo vendo ni lo comparto.
- Sin publicidad, sin analíticas, sin seguimiento y sin servicios de informes de errores.

## Información a la que accede la app
Al iniciar sesión con Google concedes a la app estos permisos:

| Permiso | Para qué se usa |
|---|---|
| Dirección de correo de tu cuenta de Google y perfil básico (`openid`, `email`, `profile`) | Para mostrar qué cuenta está activa y distinguir cuentas, con tu nombre y tu foto de perfil. La foto se descarga una vez y se guarda solo en tu teléfono. |
| Acceso a Gmail (`https://www.googleapis.com/auth/gmail.modify`) | Para leer tus mensajes, etiquetas y adjuntos; redactar y enviar mensajes; y marcar como leídos, destacar, archivar y mover a la papelera. La app no puede borrar mensajes de forma permanente. |

La app usa este acceso solo para ofrecer las funciones que ves en ella, a petición tuya.

## Cómo se guarda la información
- Los mensajes, etiquetas y adjuntos que abres se guardan en una **base de datos local en tu teléfono** para que la app funcione rápido y sin conexión.
- Los tokens de inicio de sesión se guardan en tu teléfono **cifrados** con una clave del Android Keystore.
- Lo que configuras para cada cuenta en Ajustes (su foto, el nombre con que aparece, el nombre para enviar y su color) se queda en tu teléfono. Una foto que elijas de la galería se copia, en pequeño, dentro de la app; el original no se modifica.
- La copia de seguridad en la nube de los datos de la app está desactivada.
- No se envía nada a mí ni a terceros, salvo lo que recibe el servicio opcional de notificaciones instantáneas (ver más abajo). Las únicas conexiones de red de la app son con los servicios de Google (por ejemplo `accounts.google.com`, `oauth2.googleapis.com`, `gmail.googleapis.com`, y `openidconnect.googleapis.com` / `googleusercontent.com` para la foto de perfil), con los de Microsoft (`login.microsoftonline.com`, `graph.microsoft.com`) si añades una cuenta de Microsoft y, solo si decides mostrar las imágenes remotas de un mensaje, con los servidores que las alojan. Las imágenes remotas están bloqueadas por defecto.

## Notificaciones instantáneas (opcional)
Si dejas activadas las «Notificaciones instantáneas» (Ajustes → Notificaciones y sincronización), Gmail (y Microsoft) avisan a tu teléfono cuando llega correo en lugar de que la app lo compruebe cada pocos minutos. Para que funcione:
- La app pide a Gmail que publique una nota de «algo ha cambiado» en un tema de Google Cloud Pub/Sub de este proyecto. La nota solo contiene tu dirección y un número de historial, nada del contenido de los mensajes.
- En las cuentas de Microsoft la app pide a Microsoft Graph que envíe al mismo servicio una notificación de cambio cuando llega correo a tu bandeja de entrada. Solo contiene el identificador de la suscripción y el del mensaje nuevo, nada del contenido.
- Un pequeño servicio que gestiono en Cloudflare Workers (plan gratuito) recibe esa nota y despierta tu teléfono a través de Firebase Cloud Messaging (Google).
- Ese servicio guarda, mientras la función esté activada, tu **dirección de correo**, un secreto aleatorio que identifica tu suscripción de Microsoft (solo cuentas de Microsoft) y el **token de Firebase Cloud Messaging** de tu teléfono (un identificador anónimo de la app en tu teléfono). Nunca recibe ni guarda asuntos, cuerpos, adjuntos, contactos ni tokens de inicio de sesión de Google.
- No se usa Firebase Analytics. Puedes desactivar la función cuando quieras: la app detiene los avisos de Gmail y de Microsoft y borra tu dirección y tu token del servicio.

## Compartir datos
No vendo, alquilo, comparto ni transfiero tus datos a nadie. Google recibe las peticiones que la app hace a sus servicios, según su [Política de Privacidad](https://policies.google.com/privacy).

## Política de datos de usuario de los servicios de las API de Google
El uso y la transferencia que Cemail hace de la información recibida de las API de Google cumple la [Política de datos de usuario de los servicios de las API de Google](https://developers.google.com/terms/api-services-user-data-policy), incluidos los requisitos de uso limitado. En concreto, los datos se usan solo para ofrecer y mejorar las funciones de la app visibles para el usuario, no se usan para publicidad y no los lee ninguna persona, salvo que tú lo compartas expresamente o sea necesario por seguridad o por ley.

## Conservación y eliminación
- Los datos permanecen en tu teléfono hasta que quites la cuenta en la app o la desinstales. Quitar una cuenta borra sus mensajes locales y sus tokens.
- Puedes revocar el acceso de Cemail a tu cuenta de Google en cualquier momento en [myaccount.google.com/permissions](https://myaccount.google.com/permissions).

## Menores
La app no está dirigida a menores de 13 años y no recopilo conscientemente información de ellos.

## Cambios
Si esta política cambia, la nueva versión se publicará en esta página con la fecha actualizada.

## Contacto
César Caballero · cesar.a.caballerot@gmail.com
