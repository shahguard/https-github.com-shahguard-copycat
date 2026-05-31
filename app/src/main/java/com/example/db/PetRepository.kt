package com.example.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PetRepository(private val petDao: PetDao) {

    val petStatsFlow: Flow<PetEntity> = petDao.getPetStatsFlow().map { entity ->
        entity ?: PetEntity() // Fallback to default if database is empty initially
    }

    suspend fun getPetStats(): PetEntity {
        return petDao.getPetStats() ?: PetEntity()
    }

    suspend fun savePetStats(pet: PetEntity) {
        petDao.savePetStats(pet)
    }
}
