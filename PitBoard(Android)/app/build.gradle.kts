plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.pitboard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pitboard.app"
        minSdk = 26          // Android 8.0 — necesario para que Glance funcione de forma fiable
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // lo activaremos más adelante junto con las reglas de ProGuard
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Material Components (XML) — necesaria porque themes.xml hereda de
    // Theme.Material3.DayNight.NoActionBar; sin esto, la compilación falla con
    // "resource linking failed" al no encontrar ese estilo
    implementation("com.google.android.material:material:1.12.0")

    // Core / Compose
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Navegación entre pantallas de la app (paso 18)
    implementation("androidx.navigation:navigation-compose:2.8.4")
    // Iconos que no vienen en el set básico (CalendarMonth, Palette...) (paso 18)
    implementation("androidx.compose.material:material-icons-extended")
    // ViewModel dentro de composables (paso 19 en adelante)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // Círculo cromático real en la config del widget (paso "fondo del widget")
    implementation("com.github.skydoves:colorpicker-compose:1.1.2")

    // Glance (widgets de escritorio)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    // Room (base de datos local)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher para cifrado de la base de datos (Seguridad)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")

    // DataStore (preferencias por widget y ajustes globales de la app)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // WorkManager (refresco periódico + recordatorios de eventos)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Clasificaciones (Fase 0) y calendario automático (ver com.pitboard.app.schedule): red
    // para las fuentes JSON/HTML de cada serie.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.1")

    // Fotos de piloto/equipo en Clasificaciones (Fase 4)
    implementation("io.coil-kt:coil-compose:2.7.0")
    // 03/09/2026: los logos de equipo de Fórmula E son SVG (badges/{teamId}.svg, ver
    // FormulaEStandingsSource) — sin este decoder, Coil no sabe pintar SVG y esas filas se
    // quedan en el icono por defecto pese a que la URL sea correcta.
    implementation("io.coil-kt:coil-svg:2.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
}