package com.example.caffeineguard.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.UUID

@Entity(tableName = "drinks_table")
data class Drink(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val caffeineMg: Int,
    val timeAdded: Long,
    val iconName: String,
    val userId: String
) {
    fun getIconVector(): ImageVector {
        return when (iconName) {
            "Espresso", "Coffee" -> Icons.Default.Coffee
            "Bolt", "Energy" -> Icons.Default.Bolt
            "LocalCafe", "Latte" -> Icons.Default.LocalCafe
            "LocalDrink", "Cola" -> Icons.Default.LocalDrink
            "EmojiFoodBeverage", "Tea" -> Icons.Default.EmojiFoodBeverage
            "FitnessCenter", "Workout" -> Icons.Default.FitnessCenter
            else -> Icons.Default.LocalCafe
        }
    }
}