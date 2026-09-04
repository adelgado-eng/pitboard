package com.pitboard.app.standings

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDriverDao {

    @Query("SELECT * FROM car_drivers WHERE category = :category AND standingsClass = :standingsClass AND carNumber = :carNumber")
    fun driversForCar(category: StandingsCategory, standingsClass: StandingsClass, carNumber: String): Flow<List<CarDriverEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CarDriverEntity>)

    @Query("DELETE FROM car_drivers WHERE category = :category")
    suspend fun deleteCategory(category: StandingsCategory)

    /** Igual que StandingDao.replaceCategory: se sustituye toda una categoría de golpe
     *  para que un piloto que ya no está en la parrilla no se quede "colgado" de una
     *  sync anterior — nunca borra las otras categorías (ELMS e IMSA sincronizan por
     *  separado, un fallo en una no debe vaciar la otra). */
    @Transaction
    suspend fun replaceCategory(category: StandingsCategory, rows: List<CarDriverEntity>) {
        deleteCategory(category)
        insertAll(rows)
    }
}
