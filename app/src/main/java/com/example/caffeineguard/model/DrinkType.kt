package com.example.caffeineguard.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

// Definitia unui tip de bautura
data class DrinkType(
    val name: String,
    val caffeineMg: Int,
    val icon: ImageVector
)

// Meniul complet
val menuItems = listOf(
    DrinkType("Espresso", 60, Icons.Default.Coffee),
    DrinkType("Double Espresso", 120, Icons.Default.Coffee),
    DrinkType("Cappuccino", 80, Icons.Default.LocalCafe),
    DrinkType("Latte Macchiato", 70, Icons.Default.LocalCafe),
    DrinkType("Energy Drink", 120, Icons.Default.Bolt),
    DrinkType("Monster/RedBull Large", 160, Icons.Default.Bolt),
    DrinkType("Coca-Cola", 35, Icons.Default.LocalDrink),
    DrinkType("Black Tea", 45, Icons.Default.EmojiFoodBeverage),
    DrinkType("Pre-Workout", 200, Icons.Default.FitnessCenter)
)