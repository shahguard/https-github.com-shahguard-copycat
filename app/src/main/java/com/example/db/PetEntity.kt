package com.example.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_stats")
data class PetEntity(
    @PrimaryKey val id: Int = 1,
    val petName: String = "Buster",
    val hunger: Float = 80f,      // 0 - 100 (100 is full, 0 is starving)
    val energy: Float = 70f,      // 0 - 100 (100 is rested, 0 is exhausted)
    val hygiene: Float = 90f,     // 0 - 100 (100 is clean, 0 is dirty)
    val funLevel: Float = 75f,    // 0 - 100 (100 is happy, 0 is bored)
    val lastUpdatedTs: Long = System.currentTimeMillis(),
    val coins: Int = 150,
    val gems: Int = 5,
    val level: Int = 1,
    val xp: Int = 0,
    val currentOutfitId: String = "default",
    val currentBackgroundId: String = "playroom", // default playroom wallpaper
    val unlockedOutfitsJson: String = "[\"default\"]",
    val unlockedBackgroundsJson: String = "[\"playroom\"]"
)
