// Build.gradle.kts raíz — aquí solo se DECLARAN los plugins (apply false),
// se aplican de verdad en app/build.gradle.kts (paso 6).
// Nota: revisa en Android Studio (File > Project Structure > tras abrir el proyecto)
// si sugiere versiones más recientes de AGP/Kotlin — estas son estables a la fecha
// de este proyecto, pero el ecosistema Android se mueve rápido.

plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("androidx.room") version "2.6.1" apply false
}
