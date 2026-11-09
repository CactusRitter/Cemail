# Reglas de R8 para Cemail.

# kotlinx.serialization: conservar los serializadores generados.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cemail.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.cemail.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp usa estos paquetes opcionales si existen.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
