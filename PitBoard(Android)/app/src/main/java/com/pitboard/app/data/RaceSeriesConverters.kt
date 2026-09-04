package com.pitboard.app.data

import androidx.room.TypeConverter

/** Room guarda el enum como su nombre en texto (ej: "NASCAR_CUP"), igual que StandingsConverters. */
class RaceSeriesConverters {

    @TypeConverter
    fun fromSeries(value: RaceSeries): String = value.name

    @TypeConverter
    fun toSeries(value: String): RaceSeries = RaceSeries.valueOf(value)
}
