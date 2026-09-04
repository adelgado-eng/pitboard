# 04/09/2026: reglas para isMinifyEnabled = true (Fase 0 del diagnóstico de graphify).
# La app usa Retrofit/OkHttp/Room/Glance/Coil, que ya traen sus propias reglas consumer-proguard
# dentro de sus AAR — R8 las aplica solo con añadir la dependencia, no hace falta repetirlas aquí.
#
# El riesgo real es Moshi SIN codegen: StandingsMoshi (StandingsHttpClient.kt) usa
# KotlinJsonAdapterFactory, que construye los data class de schedule/sources/ y
# standings/sources/ por reflexión sobre el constructor primario en tiempo de ejecución. Si R8
# renombra o elimina esos campos, el parsing de JSON falla en producción sin ningún error de
# compilación que lo avise — por eso se mantienen intactos explícitamente en vez de confiar en
# las reglas por defecto.
-keep class kotlin.Metadata { *; }
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.Json <fields>;
}
-keep,allowobfuscation @interface com.squareup.moshi.*

# Modelos de red parseados por Moshi (uno por fuente de calendario/clasificación).
-keep class com.pitboard.app.schedule.sources.** { *; }
-keep class com.pitboard.app.standings.sources.** { *; }
-keep class com.pitboard.app.standings.CarBasedStandingsClasses* { *; }

# SQLCipher carga sus propias clases nativas por nombre — no deben renombrarse.
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**
