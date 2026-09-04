package com.pitboard.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query(
        """
        SELECT * FROM events
        WHERE series IN (:activeSeries)
          AND startTimeUtc >= :nowUtc
          AND startTimeUtc <= :endOfYearUtc
        ORDER BY startTimeUtc ASC
        """
    )
    fun observeUpcomingBySeries(
        activeSeries: List<RaceSeries>,
        nowUtc: Long,
        endOfYearUtc: Long
    ): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE startTimeUtc >= :nowUtc
          AND startTimeUtc <= :endOfYearUtc
        ORDER BY startTimeUtc ASC
        """
    )
    fun observeAllUpcoming(nowUtc: Long, endOfYearUtc: Long): Flow<List<EventEntity>>

    @Query(
        """
        SELECT * FROM events
        WHERE startTimeUtc >= :nowUtc
          AND startTimeUtc <= :endOfYearUtc
        ORDER BY startTimeUtc ASC
        """
    )
    suspend fun getAllUpcoming(nowUtc: Long, endOfYearUtc: Long): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE startTimeUtc >= :nowUtc
          AND startTimeUtc <= :endOfYearUtc
          AND series IN (:activeSeries)
        ORDER BY startTimeUtc ASC
        LIMIT :limit
        """
    )
    suspend fun getFilteredUpcoming(
        nowUtc: Long,
        endOfYearUtc: Long,
        activeSeries: List<RaceSeries>,
        limit: Int
    ): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("DELETE FROM events WHERE series = :series")
    suspend fun deleteBySeries(series: RaceSeries)

    @Query("SELECT COUNT(*) FROM events WHERE series = :series")
    suspend fun countForSeries(series: RaceSeries): Int

    /** Sustituye de golpe todas las sesiones de una serie por las de la sincronización nueva,
     *  igual que StandingDao.replaceCategory — así una sesión reprogramada o cancelada no se
     *  queda "colgada" de una sync anterior. */
    @Transaction
    suspend fun replaceSeries(series: RaceSeries, events: List<EventEntity>) {
        deleteBySeries(series)
        insertAll(events)
    }

    companion object {
        const val NO_LIMIT: Int = 9_999
    }
}
