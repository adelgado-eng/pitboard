package com.pitboard.app.standings

import androidx.room.TypeConverter

/** Room guarda los enums como su nombre en texto (ej: "F1_ACADEMY"), no como el objeto. */
class StandingsConverters {

    @TypeConverter
    fun fromCategory(value: StandingsCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): StandingsCategory = StandingsCategory.valueOf(value)

    @TypeConverter
    fun fromClass(value: StandingsClass): String = value.name

    @TypeConverter
    fun toClass(value: String): StandingsClass = StandingsClass.valueOf(value)

    @TypeConverter
    fun fromType(value: StandingType): String = value.name

    @TypeConverter
    fun toType(value: String): StandingType = StandingType.valueOf(value)
}