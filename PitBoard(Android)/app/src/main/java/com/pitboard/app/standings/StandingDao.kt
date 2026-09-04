package com.pitboard.app.standings

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StandingDao {

    @Query(
        """
        SELECT * FROM standings
        WHERE category = :category AND standingsClass = :standingsClass AND type = :type
        ORDER BY position ASC
        """
    )
    fun observe(
        category: StandingsCategory,
        standingsClass: StandingsClass,
        type: StandingType
    ): Flow<List<StandingEntity>>

    @Query("SELECT MAX(updatedAtUtc) FROM standings WHERE category = :category")
    fun observeLastUpdated(category: StandingsCategory): Flow<Long?>

    @Query("SELECT MAX(updatedAtUtc) FROM standings")
    suspend fun getLastUpdatedOverall(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<StandingEntity>)

    @Query("DELETE FROM standings WHERE category = :category")
    suspend fun deleteCategory(category: StandingsCategory)

    @Query("DELETE FROM standings")
    suspend fun deleteAll()

    /** Sustituye de golpe toda la clasificación de una categoría por los datos nuevos,
     *  para que un piloto que ya no puntúa no se quede "colgado" de una sync anterior. */
    @Transaction
    suspend fun replaceCategory(category: StandingsCategory, rows: List<StandingEntity>) {
        deleteCategory(category)
        insertAll(rows)
    }
}