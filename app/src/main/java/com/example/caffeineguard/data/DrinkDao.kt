package com.example.caffeineguard.data

import androidx.room.*
import com.example.caffeineguard.model.Drink
import kotlinx.coroutines.flow.Flow

@Dao
interface DrinkDao {
    // CHANGE: Filter by userId
    @Query("SELECT * FROM drinks_table WHERE userId = :userId ORDER BY timeAdded DESC")
    fun getDrinksForUser(userId: String): Flow<List<Drink>>

    @Insert
    suspend fun insertDrink(drink: Drink)

    @Delete
    suspend fun deleteDrink(drink: Drink)

    // CHANGE: Only delete THIS user's drinks
    @Query("DELETE FROM drinks_table WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}