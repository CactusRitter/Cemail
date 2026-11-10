# Historial de versiones de Cemail

Siempre por debajo de 1.0: la 1.0 será cuando funcionen todas las funciones principales.
Cada versión se guarda en Google Drive (`Mi unidad/Cemail/Cemail-x.y.z.apk`) y no se reemplaza la anterior.

## 0.2.0 — 2026-11-10
- Los correos HTML se ven con su formato (HTML saneado en un WebView sin JavaScript); las imágenes remotas siguen bloqueadas hasta pulsar «Mostrar imágenes».
- Las etiquetas de Gmail aparecen como carpetas en el menú; las que tienen subcarpetas ("Compras/Amazon") se expanden y contraen.
- La barra de responder cabe en pantallas estrechas.
- La base de datos pasa a la versión 3 (migración automática); la primera sincronización vuelve a descargar los mensajes para guardar su HTML.
- APK de release sin reducir (más grande, pero sin riesgo de fallos por la reducción de código).

## 0.1.0 — 2026-11-10
- Interfaz estilo Thunderbird con funciones de Spark (fijar, posponer, deshacer), inicio de sesión con Google y primera sincronización de Gmail (mensajes como texto).
