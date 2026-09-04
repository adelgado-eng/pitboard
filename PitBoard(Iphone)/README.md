# PitBoard (iOS)

Puerto a iOS de PitBoard(Android). Ver el plan completo y el mapa de equivalencias
Android → iOS en la conversación con Claude que generó este scaffold.

## Requisitos

- macOS + Xcode 15+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen): `brew install xcodegen`

## Generar el proyecto

```bash
cd "PitBoard(Iphone)"
xcodegen generate
open PitBoard.xcodeproj
```

`PitBoard.xcodeproj` **no se versiona** — se regenera siempre desde `project.yml`. Si
cambias targets, dependencias o Info.plist, edita `project.yml` y vuelve a correr
`xcodegen generate`.

## Estado

- **Fase 1 — Scaffold**: hecho. `project.yml` define 4 targets (PitBoard app,
  PitBoardWidgetExtension, PitBoardTests, PitBoardUITests) + el paquete local
  `PitBoardKit` (SPM) con SwiftSoup como dependencia externa.
- **Fase 2 — Capa de datos**: hecho, en `PitBoardKit/Sources/PitBoardKit/Data/`.
  SwiftData en vez de Room; `AppDatabase` comparte el store vía App Group
  (`group.com.pitboard.app`) para que el widget pueda leerlo directamente.
- **Fase 3 — Dominio schedule + standings**: hecho, en
  `PitBoardKit/Sources/PitBoardKit/Schedule/` y `.../Standings/`. Scraping HTML con
  SwiftSoup, JSON con `Codable`, red con `URLSession` — mismas ~30 fuentes que en
  Android, puerto 1:1 de la lógica de parsing.
- **Sincronización en segundo plano**: hecho, en `PitBoardKit/Sources/PitBoardKit/Sync/`.
  `BackgroundSyncManager` (`BGTaskScheduler`: 3 identificadores, ver `project.yml` →
  `BGTaskSchedulerPermittedIdentifiers`) + `ConnectivityMonitor` (`NWPathMonitor`).
- **Notificaciones locales**: hecho, en `PitBoardKit/Sources/PitBoardKit/Notifications/`.
  Sin equivalente de `EventReminderWorker` — cada aviso se programa directamente como
  `UNNotificationRequest` con su hora exacta, el sistema lo entrega solo.
- **UI**: hecho. Tema (`PitBoardKit/Sources/PitBoardKit/UI/Theme`), componentes
  compartidos (`.../UI/Components`), arranque + pestañas (`PitBoard/App/`), y las 4
  pantallas (`PitBoard/UI/Events`, `.../Standings`, `.../Settings`) — SwiftData `@Query`
  en vez de ViewModel+StateFlow.
- **Widget**: hecho, en `PitBoardWidget/`. `AppIntentConfiguration` en vez de una Activity
  de configuración a medida (Android necesitaba `RaceWidgetConfigActivity` porque Glance
  no tiene configuración nativa por widget; iOS 17 sí — mantener pulsado el widget →
  "Editar widget" ya genera esa UI solo a partir de los `@Parameter` del `AppIntent`).
- **Tests**: hecho, en `PitBoardKit/Tests/PitBoardKitTests/`. Cubren los parsers/utilidades
  puras (`TextNormalizer`, `SessionBadgeMatcher`, `SeasonWindow`, `EventWeekendGrouper`,
  `ColorContrast`, funciones del widget, `RosterNameFilter`) y la resiliencia de
  `RaceScheduleRepository`/`StandingsRepository` (aislamiento de fallos, deduplicación,
  reemplazo de caché) con fuentes falsas + `ModelContainer` en memoria — cero red real.
- **Cargador de imágenes propio**: hecho, en `PitBoardKit/Sources/PitBoardKit/UI/Components/`.
  `RemoteImage` (+ `RemoteImageCache`) sustituye a `AsyncImage` nativo en las 6 fotos/logos
  de Clasificaciones — usa el `URLSession` de `HTTPClient` (mismo User-Agent de navegador
  que ya llevaba el scraping), arreglando el mismo 403 de Wikimedia que forzó
  `PitBoardApplication.newImageLoader()` en Android. Limitación conocida sin resolver: no
  rasteriza SVG (logos de Fórmula E) — ver el comentario en `RemoteImage.swift` sobre por
  qué no se añadió una dependencia de terceros sin poder verificar su API en esta sesión.
- **Pendiente** (no bloquea nada de lo anterior): pulir el diseño visual en Xcode con la
  app corriendo de verdad, y tests de UI (`PitBoardUITests`, hoy vacío).

## Nota de seguridad

Android cifra su base de datos con SQLCipher usando una passphrase **hardcodeada en el
APK** — eso no protege nada (cualquiera con el APK puede extraer la clave). En iOS no se
replica ese patrón: SwiftData/Core Data ya quedan protegidos en reposo por el Data
Protection del sistema (ligado al passcode del dispositivo), que es una protección real.

## Sin Xcode a mano

Este scaffold se generó desde Windows (sin Xcode), así que nada de esto se ha compilado
todavía. Antes de dar por buena una fuente de scraping, ábrela en un Mac y corrige lo que
XcodeGen/Swift señalen — la lógica está portada 1:1 desde el Kotlin original pero no ha
pasado por el compilador de Swift.
