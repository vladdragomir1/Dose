package com.example.caffeineguard.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.caffeineguard.model.Drink

// CHANGE: Version is now 2
@Database(entities = [Drink::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drinkDao(): DrinkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "caffeine_guard_db"
                )
                    .fallbackToDestructiveMigration() // <--- IMPORTANT: Wipes old DB structure to avoid crash
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}