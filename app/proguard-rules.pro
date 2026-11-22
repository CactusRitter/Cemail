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

# Glance creates widget action callbacks by reflection and finds a widget's content by its class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class * extends androidx.glance.appwidget.GlanceAppWidget

# Android Mail (IMAP/SMTP) loads its providers, protocols and activation handlers by name from META-INF.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class myjava.awt.datatransfer.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn org.apache.harmony.**

# Workers and receivers are created by the system by name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# WorkManager (Glance refreshes widgets through it) creates its input mergers by reflection from their class name;
# without this the widget stays on its loading spinner ("OverwritingInputMerger has no zero argument constructor").
-keep class * extends androidx.work.InputMerger { <init>(); }
