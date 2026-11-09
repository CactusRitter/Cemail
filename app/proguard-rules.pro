# Reglas de R8 para Cemail.

# Jakarta Mail / Angus carga proveedores (IMAP, SMTP) por reflexión vía META-INF.
-keep class org.eclipse.angus.mail.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-keep class org.eclipse.angus.activation.** { *; }
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.**
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
