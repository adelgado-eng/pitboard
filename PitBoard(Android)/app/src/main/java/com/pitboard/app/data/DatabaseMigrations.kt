package com.pitboard.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE standings ADD COLUMN photoUrl TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS standings (
                category TEXT NOT NULL,
                standingsClass TEXT NOT NULL,
                type TEXT NOT NULL,
                entrantKey TEXT NOT NULL,
                position INTEGER NOT NULL,
                name TEXT NOT NULL,
                team TEXT NOT NULL,
                points REAL NOT NULL,
                updatedAtUtc INTEGER NOT NULL,
                PRIMARY KEY(category, standingsClass, type, entrantKey)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_standings_category_standingsClass_type ON standings (category, standingsClass, type)"
        )
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS categories_new (
                    calendarSourceId INTEGER NOT NULL,
                    seriesName TEXT NOT NULL,
                    tag TEXT NOT NULL,
                    colorHex TEXT NOT NULL,
                    enabledByDefault INTEGER NOT NULL DEFAULT 1,
                    PRIMARY KEY(calendarSourceId, seriesName),
                    FOREIGN KEY(calendarSourceId) REFERENCES calendar_sources(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO categories_new (calendarSourceId, seriesName, tag, colorHex, enabledByDefault)
                SELECT DISTINCT e.calendarSourceId, c.seriesName, c.tag, c.colorHex, c.enabledByDefault
                FROM categories c
                INNER JOIN events e ON e.seriesName = c.seriesName
                """.trimIndent()
            )
            db.execSQL("DROP TABLE categories")
            db.execSQL("ALTER TABLE categories_new RENAME TO categories")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_seriesName ON categories (seriesName)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS events_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    calendarSourceId INTEGER NOT NULL,
                    uid TEXT NOT NULL,
                    seriesName TEXT NOT NULL,
                    fullTitle TEXT NOT NULL,
                    startTimeUtc INTEGER NOT NULL,
                    inferredBadge TEXT NOT NULL,
                    FOREIGN KEY(calendarSourceId) REFERENCES calendar_sources(id) ON DELETE CASCADE,
                    FOREIGN KEY(calendarSourceId, seriesName) REFERENCES categories(calendarSourceId, seriesName) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("INSERT INTO events_new SELECT * FROM events")
            db.execSQL("DROP TABLE events")
            db.execSQL("ALTER TABLE events_new RENAME TO events")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_events_startTimeUtc ON events (startTimeUtc)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_events_seriesName ON events (seriesName)")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_events_calendarSourceId_uid ON events (calendarSourceId, uid)"
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }
}