import Foundation
import SwiftData

/// Esquema v1 — equivalente del `@Database(entities = [...], version = 9, ...)` de
/// Android. No se replica el historial de 9 migraciones de Room: son datos que ya no
/// aplican (Android e iOS no comparten base de datos), así que iOS arranca limpio en su
/// propio v1. Ver `PitBoardMigrationPlan` para cómo se añadirá una v2 el día que haga
/// falta un cambio de esquema real.
public enum SchemaV1: VersionedSchema {
    public static var versionIdentifier: Schema.Version = .init(1, 0, 0)

    public static var models: [any PersistentModel.Type] {
        [EventModel.self, SeriesConfigModel.self, StandingModel.self, CarDriverModel.self]
    }
}

/// Plan de migración de SwiftData — equivalente de `DatabaseMigrations.kt`. Hoy solo
/// tiene una etapa (v1, sin migraciones previas que aplicar). Cuando haga falta cambiar
/// el esquema, se añade un `SchemaV2: VersionedSchema` y una `MigrationStage.custom`/`
/// .lightweight` aquí — mismo patrón que las `Migration(n, n+1)` de Room, pero declarado
/// como plan en vez de una lista de objetos `Migration` sueltos.
public enum PitBoardMigrationPlan: SchemaMigrationPlan {
    public static var schemas: [any VersionedSchema.Type] { [SchemaV1.self] }
    public static var stages: [MigrationStage] { [] }
}

/// Punto único de acceso a la base de datos local — equivalente de `AppDatabase.kt`
/// (Room). El store SQLite vive en el contenedor compartido del App Group para que tanto
/// la app como la extensión de widget (proceso separado en iOS, a diferencia de Android
/// donde Glance corre en el mismo proceso que la app) lean y escriban la MISMA base de
/// datos sin necesidad de un servidor/IPC intermedio.
///
/// Nota de seguridad: Android cifra su base con SQLCipher usando una passphrase
/// hardcodeada en el propio APK — eso no protege nada de verdad (cualquiera con el APK
/// puede extraer la clave). Aquí NO se replica ese patrón: SwiftData ya hereda la
/// protección de datos en reposo de iOS (ligada al passcode del dispositivo), que sí es
/// una protección real y no requiere gestionar ninguna clave a mano.
public enum AppDatabase {

    /// Debe coincidir exactamente con el App Group declarado en `project.yml`
    /// (`com.apple.security.application-groups`) y en las entitlements de ambos targets
    /// (PitBoard y PitBoardWidgetExtension) — si no coinciden, `containerURL` devuelve
    /// nil y la app no arranca (ver el `fatalError` de abajo, deliberado: sin base de
    /// datos compartida no hay app que valga la pena arrancar).
    public static let appGroupId = "group.com.pitboard.app"

    private static let storeFileName = "pitboard_v1.store"

    public static let container: ModelContainer = {
        guard let groupURL = FileManager.default
            .containerURL(forSecurityApplicationGroupIdentifier: appGroupId)
        else {
            fatalError(
                "No se pudo resolver el contenedor del App Group '\(appGroupId)'. " +
                "Revisa Signing & Capabilities > App Groups en los targets PitBoard y " +
                "PitBoardWidgetExtension (y que el Team ID esté configurado en project.yml)."
            )
        }

        let storeURL = groupURL.appendingPathComponent(storeFileName)
        let configuration = ModelConfiguration(schema: Schema(SchemaV1.models), url: storeURL)

        do {
            return try ModelContainer(
                for: Schema(SchemaV1.models),
                migrationPlan: PitBoardMigrationPlan.self,
                configurations: [configuration]
            )
        } catch {
            fatalError("No se pudo crear el ModelContainer de SwiftData: \(error)")
        }
    }()

    /// Contexto nuevo para trabajo fuera de la UI (repositorios de sincronización) —
    /// equivalente de hacer las escrituras de Room en `Dispatchers.IO`. A diferencia de
    /// `container.mainContext` (ligado al main actor), este NO está atado a ningún actor
    /// — se puede usar desde una `Task` de fondo. Cada llamada crea un `ModelContext`
    /// nuevo porque no son seguros de compartir entre tareas concurrentes (mismo motivo
    /// por el que Android fuerza `JournalMode.TRUNCATE`: una única escritura a la vez).
    public static func newContext() -> ModelContext {
        ModelContext(container)
    }
}
