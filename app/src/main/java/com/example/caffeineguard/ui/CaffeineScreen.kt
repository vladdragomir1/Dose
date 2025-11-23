package com.example.caffeineguard.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.caffeineguard.data.AppDatabase
import com.example.caffeineguard.model.Drink
import com.example.caffeineguard.model.DrinkType
import com.example.caffeineguard.model.menuItems
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// --- CUSTOM PREMIUM COLORS ---
val DarkPurple = Color(0xFF120E1F)
val NeonPurple = Color(0xFFBB86FC)
val SurfaceGlass = Color(0xFF2D2D3A).copy(alpha = 0.7f)
val EnergyOrange = Color(0xFFFFAB40)
val EnergyGreen = Color(0xFF69F0AE)

// Group of identical drinks
data class DrinkGroup(
    val drink: Drink,
    val count: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaffeineScreen(
    userName: String, // <--- This is critical for filtering
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val db = remember { AppDatabase.getDatabase(context) }
    val scope = rememberCoroutineScope()

    // FIX 1: LOAD ONLY THIS USER'S DRINKS
    val drinks by db.drinkDao().getDrinksForUser(userName).collectAsState(initial = emptyList())

    // --- GROUP DRINKS LOGIC ---
    val groupedDrinks: List<DrinkGroup> = remember(drinks) {
        drinks
            .groupBy { Triple(it.name, it.caffeineMg, it.iconName) }
            .map { (_, list) ->
                val latest = list.maxByOrNull { it.timeAdded }!!
                DrinkGroup(drink = latest, count = list.size)
            }
            .sortedByDescending { it.drink.timeAdded }
    }

    // --- STATE FOR DIALOG ---
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedName by remember { mutableStateOf("") }
    var selectedMg by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("LocalCafe") }

    // --- CAMERA & ML LOGIC START ---

    // Function to process the image using ML Kit
    fun analyzeImage(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

        labeler.process(image)
            .addOnSuccessListener { labels ->
                // Simple logic to map detected objects to drinks
                var foundDrink = false

                for (label in labels) {
                    val text = label.text.lowercase()
                    val confidence = label.confidence

                    if (confidence > 0.7) { // 70% confidence threshold
                        when {
                            text.contains("espresso") || text.contains("demitasse") -> {
                                selectedName = "Espresso"
                                selectedMg = "60"
                                selectedIconName = "Espresso"
                                foundDrink = true
                            }
                            text.contains("coffee") || text.contains("mug") || text.contains("cup") -> {
                                selectedName = "Coffee"
                                selectedMg = "95"
                                selectedIconName = "LocalCafe"
                                foundDrink = true
                            }
                            text.contains("tea") || text.contains("teapot") -> {
                                selectedName = "Black Tea"
                                selectedMg = "45"
                                selectedIconName = "Tea"
                                foundDrink = true
                            }
                            text.contains("soda") || text.contains("can") || text.contains("cola") || text.contains("soft drink") -> {
                                selectedName = "Cola"
                                selectedMg = "35"
                                selectedIconName = "Cola"
                                foundDrink = true
                            }
                            text.contains("energy") -> {
                                selectedName = "Energy Drink"
                                selectedMg = "150"
                                selectedIconName = "Bolt"
                                foundDrink = true
                            }
                        }
                        if (foundDrink) break
                    }
                }

                if (foundDrink) {
                    Toast.makeText(context, "Detected: $selectedName", Toast.LENGTH_SHORT).show()
                    showAddDialog = true
                } else {
                    Toast.makeText(context, "Could not identify drink. Try again.", Toast.LENGTH_SHORT).show()
                    selectedName = "Unknown Scan"
                    selectedMg = ""
                    showAddDialog = true
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scan failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    // Launcher to take a picture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            analyzeImage(bitmap)
        }
    }

    // Launcher to request permission
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // FIX: Wrap the launch in a try-catch to prevent crashing
            try {
                cameraLauncher.launch()
            } catch (e: Exception) {
                Toast.makeText(context, "No camera app found on this device", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission required to scan drinks", Toast.LENGTH_SHORT).show()
        }
    }
    // --- CAMERA & ML LOGIC END ---

    // --- LOGIC FOR CHARTS & STATUS ---
    val currentLevel = drinks.sumOf { it.caffeineMg }
    val maxLimit = 400f
    val targetProgress = (currentLevel / maxLimit).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1200),
        label = "ProgressAnimation"
    )

    // Sleep Calculation
    val hoursToSleep = if (currentLevel > 50) (currentLevel - 50) / 20 else 0
    val now = Calendar.getInstance()
    val sleepCalendar = Calendar.getInstance()
    sleepCalendar.add(Calendar.HOUR_OF_DAY, hoursToSleep)
    val currentDay = now.get(Calendar.DAY_OF_YEAR)
    val sleepDay = sleepCalendar.get(Calendar.DAY_OF_YEAR)
    val currentYear = now.get(Calendar.YEAR)
    val sleepYear = sleepCalendar.get(Calendar.YEAR)
    val dayDifference = (sleepDay - currentDay) + ((sleepYear - currentYear) * 365)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val sleepTimeHour = timeFormat.format(sleepCalendar.time)
    val sleepDisplayText = when {
        currentLevel <= 50 -> "Sleep anytime"
        dayDifference <= 0 -> "Today at $sleepTimeHour"
        dayDifference == 1 -> "Tomorrow at $sleepTimeHour"
        else -> "In $dayDifference days at $sleepTimeHour"
    }

    // Crash Prediction Logic
    val lastDrink = drinks.maxByOrNull { it.timeAdded }
    val crashCalendar = lastDrink?.let {
        Calendar.getInstance().apply {
            timeInMillis = it.timeAdded
            add(Calendar.HOUR_OF_DAY, 4)
        }
    }
    val crashTimeText = crashCalendar?.let { timeFormat.format(it.time) }
    val nowCal = Calendar.getInstance()
    val isCrashInFuture = crashCalendar != null && crashCalendar.after(nowCal)
    val isCrashLikely = isCrashInFuture && currentLevel > 30
    val (statusText, statusColor) = when {
        currentLevel > 400 -> "⚠️ DANGER ZONE" to Color(0xFFFF5252)
        currentLevel > 250 -> "⚡ HIGH ENERGY" to Color(0xFFFFAB40)
        currentLevel > 100 -> "✅ PRODUCTIVE" to Color(0xFF69F0AE)
        else -> "💤 RELAXED" to Color(0xFFE0E0E0)
    }

    // --- UI LAYOUT ---
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Dose", fontWeight = FontWeight.Bold, color = Color.White) },
                actions = {
                    // FIX 2: DELETE ONLY THIS USER'S HISTORY
                    IconButton(onClick = { scope.launch { db.drinkDao().deleteAllForUser(userName) } }) {
                        Icon(Icons.Default.Delete, "Reset", tint = Color.White.copy(alpha = 0.7f))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkPurple, Color.Black)
                    )
                )
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = 16.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {

                        // Status Pill
                        Surface(
                            color = statusColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Chart
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { 1f },
                                modifier = Modifier.size(220.dp),
                                color = Color.White.copy(alpha = 0.1f),
                                strokeWidth = 12.dp,
                            )
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(220.dp),
                                strokeWidth = 12.dp,
                                color = statusColor,
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$currentLevel",
                                    fontSize = 50.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text("mg active", fontSize = 16.sp, color = Color.Gray)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        if (currentLevel > 250) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0D47A1).copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.WaterDrop, null, tint = Color(0xFF4FC3F7))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("High caffeine! Drink water.", color = Color.White, fontSize = 14.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // Sleep Forecast Card
                        Card(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                sendNotification(context, "Dose Forecast", "Sleep at $sleepDisplayText")
                            },
                            colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(NeonPurple.copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Bedtime, null, tint = NeonPurple)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Sleep Forecast (Tap for alert)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonPurple
                                    )
                                    if (currentLevel > 50) {
                                        Text("Safe to sleep:", color = Color.White, fontSize = 14.sp)
                                        Text(
                                            sleepDisplayText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            color = NeonPurple
                                        )
                                    } else {
                                        Text("Caffeine free", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Sleep anytime", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Crash Prediction Card
                        if (drinks.isNotEmpty()) {
                            Card(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val msg = if (isCrashLikely && crashTimeText != null) {
                                        "Energy crash expected around $crashTimeText"
                                    } else {
                                        "Energy levels are stable."
                                    }
                                    sendNotification(context, "Energy Forecast", msg)
                                },
                                colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val cardIcon =
                                        if (isCrashLikely) Icons.Default.BatteryAlert else Icons.Default.BatteryFull
                                    val iconColor =
                                        if (isCrashLikely) EnergyOrange else EnergyGreen

                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(iconColor.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(cardIcon, null, tint = iconColor)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            "Energy Forecast (Tap for alert)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Gray
                                        )
                                        if (isCrashLikely && crashTimeText != null) {
                                            Text(
                                                "Expect a crash around:",
                                                color = Color.White,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                crashTimeText,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp,
                                                color = EnergyOrange
                                            )
                                        } else {
                                            Text(
                                                "Energy levels stable",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "No crash predicted",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Quick Add Title
                        Text(
                            "Quick Add",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            // Scan Button
                            item {
                                ScanButton {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }

                            // Existing Menu Items
                            items(menuItems) { drinkType ->
                                QuickButton(drinkType) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                    selectedName = drinkType.name
                                    selectedMg = drinkType.caffeineMg.toString()
                                    selectedIconName = when (drinkType.name) {
                                        "Espresso" -> "Espresso"
                                        "Double Espresso" -> "Coffee"
                                        "Energy Drink" -> "Bolt"
                                        "Monster / RedBull" -> "Bolt"
                                        "Cola / Soda" -> "Cola"
                                        "Black Tea" -> "Tea"
                                        "Pre-Workout" -> "Workout"
                                        else -> "LocalCafe"
                                    }
                                    showAddDialog = true
                                }
                            }

                            // Custom Add Button
                            item {
                                CustomAddButton {
                                    selectedName = ""
                                    selectedMg = ""
                                    selectedIconName = "LocalCafe"
                                    showAddDialog = true
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // History Title
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                "Today's History",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                "${drinks.size} drinks",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }

                // HISTORY LIST
                if (groupedDrinks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.3f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No drinks yet",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = groupedDrinks,
                        key = { it.drink.id }
                    ) { group ->
                        DrinkRow(
                            drink = group.drink,
                            count = group.count,
                            onDelete = {
                                scope.launch {
                                    val toDelete = drinks
                                        .filter {
                                            it.name == group.drink.name &&
                                                    it.caffeineMg == group.drink.caffeineMg &&
                                                    it.iconName == group.drink.iconName
                                        }
                                        .maxByOrNull { it.timeAdded }

                                    toDelete?.let { db.drinkDao().deleteDrink(it) }
                                }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(50.dp)) }
            }
        }

        if (showAddDialog) {
            AddDrinkDialog(
                initialName = selectedName,
                initialMg = selectedMg,
                onDismiss = { showAddDialog = false },
                onConfirm = { name, mg, timeMillis ->
                    scope.launch {
                        // FIX 3: INSERT WITH USER ID
                        db.drinkDao().insertDrink(
                            Drink(
                                name = name,
                                caffeineMg = mg,
                                timeAdded = timeMillis,
                                iconName = selectedIconName,
                                userId = userName // <--- Pass the current user here!
                            )
                        )
                    }
                    showAddDialog = false
                }
            )
        }
    }
}

// --- UI COMPONENT FOR SCANNER ---
@Composable
fun ScanButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(85.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF00E5FF).copy(alpha = 0.2f)) // Cyan tint for AI/Scan
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = "Scan", modifier = Modifier.size(32.dp), tint = Color(0xFF00E5FF))
        }
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("Scan drink", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        Text("Estimate", fontSize = 10.sp, color = Color.White)
    }
}

// --- EXISTING COMPONENTS ---

@Composable
fun AddDrinkDialog(
    initialName: String,
    initialMg: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var mgText by remember { mutableStateOf(initialMg) }

    // Time Logic
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val calendar = remember { Calendar.getInstance() }
    var timeText by remember { mutableStateOf(timeFormatter.format(calendar.time)) }

    var showTimePicker by remember { mutableStateOf(false) }

    // Show the Time Picker Dialog if state is true
    if (showTimePicker) {
        DialUseStateExample(
            onConfirm = { hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                timeText = timeFormatter.format(calendar.time)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2D2D3A),
        title = { Text(if (initialName.isEmpty()) "Add Dose" else "Edit Dose", color = Color.White) },
        text = {
            Column {
                // 1. Name Input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Drink Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 2. Mg Input
                OutlinedTextField(
                    value = mgText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) mgText = it },
                    label = { Text("Caffeine (mg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonPurple,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                // 3. Time Input (Wrapped in Box to fix the error)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { }, // Read only
                        label = { Text("Time") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Default.AccessTime, null, tint = NeonPurple)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false, // Looks disabled but we catch click below
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = Color.White,
                            disabledBorderColor = Color.Gray,
                            disabledLabelColor = Color.Gray,
                            disabledTrailingIconColor = NeonPurple
                        )
                    )

                    // The invisible overlay that captures the click
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showTimePicker = true }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty() && mgText.isNotEmpty()) {
                        onConfirm(name, mgText.toIntOrNull() ?: 0, calendar.timeInMillis)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
            ) {
                Text("Add", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun QuickButton(drinkType: DrinkType, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(85.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(SurfaceGlass)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(drinkType.icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
        }
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = drinkType.name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.LightGray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "${drinkType.caffeineMg}mg",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun CustomAddButton(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(85.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(NeonPurple.copy(alpha = 0.2f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Custom", modifier = Modifier.size(32.dp), tint = NeonPurple)
        }
        Spacer(modifier = Modifier.height(6.dp))

        Box(modifier = Modifier.height(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("Custom", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        Text("Add New", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun DrinkRow(drink: Drink, count: Int = 1, onDelete: () -> Unit) {
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceGlass),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(drink.getIconVector(), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = if (count > 1) "${count}x ${drink.name}" else drink.name
                Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("${drink.caffeineMg} mg", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Text(fmt.format(Date(drink.timeAdded)), style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
            Spacer(modifier = Modifier.width(12.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFFFF5252))
            }
        }
    }
}

@android.annotation.SuppressLint("MissingPermission")
fun sendNotification(context: Context, title: String, message: String) {
    val channelId = "caffeine_alert"
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(channelId, "Dose Alerts", NotificationManager.IMPORTANCE_HIGH)
        notificationManager.createNotificationChannel(channel)
    }
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setAutoCancel(true)
    try {
        notificationManager.notify(1, builder.build())
    } catch (e: SecurityException) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialUseStateExample(
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}