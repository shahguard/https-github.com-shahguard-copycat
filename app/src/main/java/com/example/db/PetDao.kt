package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pet_stats WHERE id = 1 LIMIT 1")
    fun getPetStatsFlow(): Flow<PetEntity?>

    @Query("SELECT * FROM pet_stats WHERE id = 1 LIMIT 1")
    suspend fun getPetStats(): PetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePetStats(pet: PetEntity)
}
