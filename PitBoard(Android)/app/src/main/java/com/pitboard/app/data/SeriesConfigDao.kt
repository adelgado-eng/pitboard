package com.pitboard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeriesConfigDao {

    @Query("SELECT * FROM series_config")
    fun observeAll(): Flow<List<SeriesConfigEntity>>

    @Query("SELECT * FROM series_config")
    suspend fun getAll(): List<SeriesConfigEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(config: SeriesConfigEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfNew(configs: List<SeriesConfigEntity>)

    @Update
    suspend fun update(config: SeriesConfigEntity)

    @Query("SELECT COUNT(*) FROM series_config")
    suspend fun count(): Int
}
