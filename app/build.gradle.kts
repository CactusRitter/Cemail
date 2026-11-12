import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Los secretos (client IDs de OAuth, firma) viven en local.properties, que no se sube a git.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProps.getProperty(key, "")

android {
    namespace = "com.cemail.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cemail.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 61
        versionName = "1.5.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google exige que la redirección de un cliente Android sea "com.googleusercontent.apps.<ID>".
        // El ID de cliente es "<ID>.apps.googleusercontent.com". Vacío hasta registrar la app (docs/OAUTH_SETUP.md).
        val googleClientId = localProp("cemail.oauth.google.clientId")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"$googleClientId\"")
        buildConfigField("String", "MICROSOFT_OAUTH_CLIENT_ID", "\"${localProp("cemail.oauth.microsoft.clientId")}\"")
        // Avisos instantaneos (push): Firebase + el relay de Cloudflare; vacios = la app funciona sin push (docs/PUSH.md).
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${localProp("cemail.firebase.projectId")}\"")
        buildConfigField("String", "FIREBASE_SENDER_ID", "\"${localProp("cemail.firebase.senderId")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${localProp("cemail.firebase.appId")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${localProp("cemail.firebase.apiKey")}\"")
        buildConfigField("String", "PUSH_URL", "\"${localProp("cemail.push.url")}\"")
        buildConfigField("String", "PUSH_APP_KEY", "\"${localProp("cemail.push.appKey")}\"")
        buildConfigField("String", "PUBSUB_TOPIC", "\"projects/${localProp("cemail.firebase.projectId")}/topics/gmail-push\"")
        manifestPlaceholders["appAuthRedirectScheme"] =
            "com.googleusercontent.apps." + googleClientId.removeSuffix(".apps.googleusercontent.com").ifEmpty { "unset" }
    }

    // Firma de release con la clave propia (la misma para todos los APK que se reparten).
    // Se activa solo si keystore.properties existe; ver docs/OAUTH_SETUP.md.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Las librerias de correo (android-mail y android-activation) traen el mismo archivo de licencia: se descartan.
    packaging {
        resources {
            excludes += setOf("META-INF/LICENSE.md", "META-INF/NOTICE.md", "META-INF/LICENSE", "META-INF/NOTICE", "META-INF/DEPENDENCIES")
        }
    }

    buildTypes {
        // Depuración firmada con la misma clave que release: así un único ID de cliente de Google
        // (atado a la SHA-1 de esa clave) sirve para probar y para el APK de los amigos.
        debug {
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Genera locales_config.xml a partir de las carpetas values-xx → idioma por app en Ajustes (Android 13+).
        generateLocaleConfig = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.haze)
    implementation(libs.rich.editor)
    implementation(libs.glance.appwidget)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.appauth)
    implementation(libs.jsoup)
    implementation(libs.android.mail)
    implementation(libs.android.activation)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
