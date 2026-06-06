package com.example.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.GroceryTranslations
import com.example.data.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GroceryAppContent(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val currentLangCode by viewModel.languageCode.collectAsStateWithLifecycle()
    val langConfig by viewModel.langConfig.collectAsStateWithLifecycle()
    
    // Core Navigation state: simple screen switcher
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    
    // Manage local layout direction (RTL or LTR) based on translations
    val layoutDirection = if (langConfig.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val screen = currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        langConfig = langConfig,
                        currentLangCode = currentLangCode,
                        onNavigateToCreator = { tripId ->
                            currentScreen = Screen.CreatorWorkspace(tripId)
                        },
                        onNavigateToShopper = { tripId ->
                            currentScreen = Screen.ShopperWorkspace(tripId)
                        }
                    )
                }
                is Screen.CreatorWorkspace -> {
                    CreatorScreen(
                        tripId = screen.tripId,
                        viewModel = viewModel,
                        langConfig = langConfig,
                        onBack = { currentScreen = Screen.Home },
                        onProceedToShopper = { currentScreen = Screen.ShopperWorkspace(screen.tripId) }
                    )
                }
                is Screen.ShopperWorkspace -> {
                    ShopperScreen(
                        tripId = screen.tripId,
                        viewModel = viewModel,
                        langConfig = langConfig,
                        onBack = { currentScreen = Screen.Home }
                    )
                }
            }
        }
    }
}

sealed class Screen {
    object Home : Screen()
    data class CreatorWorkspace(val tripId: Long) : Screen()
    data class ShopperWorkspace(val tripId: Long) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: GroceryViewModel,
    langConfig: GroceryTranslations.LangConfig,
    currentLangCode: String,
    onNavigateToCreator: (Long) -> Unit,
    onNavigateToShopper: (Long) -> Unit
) {
    val context = LocalContext.current
    var showAddShopperDialog by remember { mutableStateOf(false) }
    var selectedShopperName by remember { mutableStateOf("") }
    
    val shopperProfiles by viewModel.shopperProfiles.collectAsStateWithLifecycle()
    val groupedTrips by viewModel.groupedTrips.collectAsStateWithLifecycle()
    
    // If profiles list updates, default to first available
    LaunchedEffect(shopperProfiles) {
        if (shopperProfiles.isNotEmpty() && selectedShopperName.isEmpty()) {
            selectedShopperName = shopperProfiles[0].name
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Configuration: place a round profile avatar on the far left, the bold app title "Grocery List" centered, and the high-contrast Language/Globe dropdown button on the top right
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Round profile avatar on the far left
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👵",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bold app title centered
                Text(
                    text = stringResource(R.string.app_header),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF2E7D32),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // High-contrast Language/Globe dropdown button on top right
                var langMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { langMenuExpanded = !langMenuExpanded },
                        modifier = Modifier
                            .size(48.dp)
                            .border(2.dp, Color(0xFF2E7D32), CircleShape)
                            .testTag("language_dropdown_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Select Language",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = langMenuExpanded,
                        onDismissRequest = { langMenuExpanded = false }
                    ) {
                        GroceryTranslations.languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(lang.nativeName, fontWeight = FontWeight.Bold, color = Color.Black) },
                                onClick = {
                                    viewModel.selectLanguage(lang.key)
                                    langMenuExpanded = false
                                    (context as? android.app.Activity)?.recreate()
                                }
                            )
                        }
                    }
                }
            }
        }

        // 2. Notepad Profile Card: Enclose the title "امی کا کچن نوٹ پیڈ" and subtitle "Nomi: ڈیفالٹ خریدار" inside a large smooth green block with heavily rounded corners and stylized home graphic on the right
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(3.dp, Color(0xFF1B5E20))
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.label_notepad_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                            
                            val displayShopper = selectedShopperName.ifEmpty { "Nomi" }
                            Text(
                                text = stringResource(R.string.label_default_shopper, displayShopper),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            // Let's list interactive selectable shoppers right inside or next to this block
                            if (shopperProfiles.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    shopperProfiles.forEach { profile ->
                                        val isSelected = selectedShopperName == profile.name
                                        val chipBg = if (isSelected) Color.White else Color(0x33FFFFFF)
                                        val chipTextColor = if (isSelected) Color(0xFF2E7D32) else Color.White
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(chipBg)
                                                .clickable { selectedShopperName = profile.name }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Black,
                                                color = chipTextColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Kitchen Home Graphic",
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }
        }

        // 3. Action Navigation Links: Convert the four core actions into elegant white container cards bounded by subtle, clean borders with large icons
        val isActionEnabled = selectedShopperName.isNotEmpty()

        // Card Core Action 1: Create New List
        item {
            HomeActionCard(
                icon = Icons.Default.List,
                title = stringResource(R.string.btn_start_shopping),
                subtitle = stringResource(R.string.start_shopping_subtitle),
                enabled = isActionEnabled,
                onClick = {
                    viewModel.startNewTrip(selectedShopperName) { tripId ->
                        onNavigateToCreator(tripId)
                    }
                }
            )
        }

        // Card Core Action 2: Repeat Last Trip List
        item {
            HomeActionCard(
                icon = Icons.Default.Refresh,
                title = stringResource(R.string.btn_repeat_last),
                subtitle = stringResource(R.string.repeat_last_subtitle),
                enabled = isActionEnabled,
                onClick = {
                    viewModel.repeatLastTrip(
                        shopperName = selectedShopperName,
                        onTripCreated = { tripId ->
                            onNavigateToShopper(tripId)
                            Toast.makeText(context, "List replicated!", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            Toast.makeText(context, langConfig.strings["last_list_empty"] ?: "No previous list found!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // Card Core Action 3: Missed Last Time List
        item {
            HomeActionCard(
                icon = Icons.Default.Warning,
                title = stringResource(R.string.missed_last_title),
                subtitle = stringResource(R.string.missed_last_subtitle),
                enabled = isActionEnabled,
                onClick = {
                    viewModel.addMissedLastTripItems(
                        shopperName = selectedShopperName,
                        onTripCreated = { tripId ->
                            onNavigateToCreator(tripId)
                            Toast.makeText(context, "Auto-filled missed items!", Toast.LENGTH_SHORT).show()
                        },
                        onError = {
                            Toast.makeText(context, "No missed items found from the last trip!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // Card Core Action 4: Register/Add Shopper Profile
        item {
            HomeActionCard(
                icon = Icons.Default.Person,
                title = stringResource(R.string.btn_add_shopper),
                subtitle = stringResource(R.string.sub_add_shopper),
                enabled = true,
                onClick = { showAddShopperDialog = true }
            )
        }

        // Paste Shopper Completion Message Import Section
        item {
            var pastedText by remember { mutableStateOf("") }
            var importStatus by remember { mutableStateOf<String?>(null) }
            var isSuccessStatus by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, Color(0xFFE0E0E0))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_import_section),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = stringResource(R.string.desc_import_section),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("shopper_completion_paste_input"),
                        placeholder = { Text(stringResource(R.string.input_hint_import), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) },
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF2E7D32),
                            unfocusedBorderColor = Color.LightGray,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (pastedText.isBlank()) {
                                    importStatus = "Please paste some message text first."
                                    isSuccessStatus = false
                                    return@Button
                                }
                                viewModel.importShopperWhatsAppMessage(context, pastedText) { success, msg ->
                                    importStatus = msg
                                    isSuccessStatus = success
                                    if (success) {
                                        pastedText = ""
                                    }
                                }
                            },
                            modifier = Modifier
                                .height(56.dp)
                                .testTag("import_shoppers_log_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(2.dp, Color(0xFF1B5E20))
                        ) {
                            Text(stringResource(R.string.btn_import_proceed), fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                        }

                        if (pastedText.isNotEmpty()) {
                            TextButton(
                                onClick = { pastedText = "" }
                            ) {
                                Text("CLEAR", color = Color.Red, fontWeight = FontWeight.Black)
                            }
                        }
                    }

                    importStatus?.let { statusMsg ->
                        Text(
                            text = statusMsg,
                            color = if (isSuccessStatus) Color(0xFF2E7D32) else Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        // History Segment Title
        item {
            Text(
                text = langConfig.strings["history_title"] ?: "Shopping History Record",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        // Localized History Expansion cards
        if (groupedTrips.isEmpty()) {
            item {
                BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = langConfig.strings["no_history"] ?: "No previous historical trips found completely.",
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            // Loop Year Map
            groupedTrips.forEach { (year, monthMap) ->
                item {
                    Text(
                        text = "📅 $year",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                monthMap.forEach { (month, trips) ->
                    item {
                        Text(
                            text = "   👉 $month",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                        )
                    }

                    items(trips) { trip ->
                        val formattedDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(trip.dateMillis))
                        BrutalistCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Shopper: ${trip.shopperName}",
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = formattedDate,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text(
                                        text = "Rs. ${trip.totalBill.toInt()}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                
                                // Direct expansion list items for past trip history
                                TripDetailsHistoricalMini(tripId = trip.id, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }

    // Shopper profile dialog definition
    if (showAddShopperDialog) {
        Dialog(onDismissRequest = { showAddShopperDialog = false }) {
            var shopperNameInput by remember { mutableStateOf("") }
            var shopperRelationInput by remember { mutableStateOf("") }
            var shopperPhoneInput by remember { mutableStateOf("") }

            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_title_add_shopper),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedTextField(
                        value = shopperNameInput,
                        onValueChange = { shopperNameInput = it },
                        label = { Text(stringResource(R.string.dialog_label_name), fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    OutlinedTextField(
                        value = shopperRelationInput,
                        onValueChange = { shopperRelationInput = it },
                        label = { Text(stringResource(R.string.dialog_label_relation), fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    OutlinedTextField(
                        value = shopperPhoneInput,
                        onValueChange = { shopperPhoneInput = it },
                        label = { Text(stringResource(R.string.dialog_label_phone), fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddShopperDialog = false }) {
                            Text(stringResource(R.string.dialog_btn_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (shopperNameInput.isNotBlank()) {
                                    viewModel.addShopper(shopperNameInput, shopperRelationInput, shopperPhoneInput)
                                    selectedShopperName = shopperNameInput
                                    showAddShopperDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.dialog_btn_save), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TripDetailsHistoricalMini(
    tripId: Long,
    viewModel: GroceryViewModel
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var tripDetailsList by remember { mutableStateOf<List<TripDetail>>(emptyList()) }
    
    val database = remember { AppDatabase.getDatabase(context) }
    LaunchedEffect(isExpanded) {
        if (isExpanded) {
            tripDetailsList = database.groceryDao().getTripDetails(tripId)
        }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isExpanded) "Hide purchased items" else "View purchased items",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (isExpanded) {
            tripDetailsList.forEach { detail ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (detail.availability == "AVAILABLE") Icons.Default.Check else Icons.Default.Close,
                            contentDescription = detail.availability,
                            tint = if (detail.availability == "AVAILABLE") Color(0xFF2E7D32) else Color(0xFFDC2626),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${detail.itemName} (x${detail.qty})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (detail.purchasedBrand.isNotBlank()) {
                                Text(
                                    text = "Brand: ${detail.purchasedBrand}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    if (detail.availability == "AVAILABLE") {
                        Text(
                            text = "Rs. ${detail.price.toInt()}",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreatorScreen(
    tripId: Long,
    viewModel: GroceryViewModel,
    langConfig: GroceryTranslations.LangConfig,
    onBack: () -> Unit,
    onProceedToShopper: () -> Unit
) {
    var showCustomMedicineDialog by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var selectedCategoryTab by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    
    val activeTripDetails by viewModel.activeTripDetails.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val shopperProfiles by viewModel.shopperProfiles.collectAsStateWithLifecycle()
    
    // Auto populate tab based on available items
    LaunchedEffect(langConfig) {
        if (langConfig.defaultItems.isNotEmpty()) {
            selectedCategoryTab = langConfig.defaultItems.keys.first()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Navigation Header: crisp "بوم اسکرین" back-navigation adjacent to borderless Urdu-filled search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .height(56.dp)
                    .testTag("back_button_boom"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(2.dp, Color(0xFF1B5E20)),
                contentPadding = PaddingValues(horizontal = 14.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("بوم اسکرین", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("grocery_search_bar"),
                placeholder = {
                    Text(
                        "...سودا تلاش کریں (مثال: آٹا، چینی، صابن)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray
                    )
                },
                maxLines = 1,
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF2E7D32),
                    unfocusedBorderColor = Color.LightGray,
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                trailingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF2E7D32))
                }
            )
        }

        // 2. Split Screen Matrix Layout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left Panel Categories list
            LazyColumn(
                modifier = Modifier
                    .width(135.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(langConfig.defaultItems.keys.toList()) { category ->
                    val isSelected = selectedCategoryTab == category
                    
                    val cardBg = if (isSelected) Color(0xFF2E7D32) else Color.White
                    val textColor = if (isSelected) Color.White else Color.Black
                    val strokeColor = if (isSelected) Color(0xFF1B5E20) else Color(0xFFE0E0E0)
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategoryTab = category },
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(2.dp, strokeColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val parts = category.split(" ", limit = 2)
                            val emoji = parts.getOrNull(0) ?: "🛒"
                            val displayName = parts.getOrNull(1) ?: category
                            
                            Text(
                                text = emoji,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Black,
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Right Panel Grocery Cards
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val categoryItems = langConfig.defaultItems[selectedCategoryTab] ?: emptyList()
                val filteredItems = if (searchQuery.isBlank()) {
                    categoryItems
                } else {
                    val allSuggested = langConfig.defaultItems.values.flatten()
                    allSuggested.filter { it.contains(searchQuery, ignoreCase = true) }.distinct()
                }

                if (filteredItems.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(2.dp, Color.LightGray),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "نہ سودا ملا...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    items(filteredItems) { itemName ->
                        val matchedDetail = activeTripDetails.find { it.itemName.equals(itemName, ignoreCase = true) }
                        val isAdded = matchedDetail != null
                        val currentQty = matchedDetail?.qty ?: "0"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(2.dp, if (isAdded) Color(0xFF2E7D32) else Color(0xFFE0E0E0)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemName,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.Black
                                    )
                                    // brand name underneath
                                    val brandName = when {
                                        itemName.contains("آٹا", true) || itemName.contains("Atta", true) -> "دیسی فائن چکی / Premium"
                                        itemName.contains("چاول", true) || itemName.contains("Rice", true) -> "گارڈ باسمتی / Guard"
                                        itemName.contains("چائے", true) || itemName.contains("Tea", true) -> "ٹاپل دانہ دار / Tapal"
                                        itemName.contains("تیل", true) || itemName.contains("Oil", true) -> "ڈالڈا ککنگ آئل / Dalda"
                                        itemName.contains("دودھ", true) || itemName.contains("Milk", true) -> "ملک پیک خالص / MilkPak"
                                        itemName.contains("دوا", true) || itemName.contains("Med", true) -> "ڈاکٹر کی تجویز کردہ / Prescribed"
                                        else -> "لوکل برانڈ / Preferred Brand"
                                    }
                                    Text(
                                        text = brandName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.DarkGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    if (isAdded) {
                                        Text(
                                            text = "شامل: $currentQty",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                // Stepper on the right
                                if (isAdded && matchedDetail != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val q = currentQty.toIntOrNull() ?: 1
                                                if (q <= 1) {
                                                    viewModel.removeDetail(matchedDetail.id)
                                                } else {
                                                    viewModel.updateDetail(matchedDetail.copy(qty = (q - 1).toString()))
                                                }
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFFEEEEEE), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Minus",
                                                tint = Color.Black,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Text(
                                            text = currentQty,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Black,
                                            color = Color.Black,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )

                                        IconButton(
                                            onClick = {
                                                val q = currentQty.toIntOrNull() ?: 1
                                                viewModel.updateDetail(matchedDetail.copy(qty = (q + 1).toString()))
                                            },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(Color(0xFF2E7D32), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Plus",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            viewModel.addDefaultItemToTrip(selectedCategoryTab, itemName)
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFF2E7D32), RoundedCornerShape(8.dp))
                                            .border(1.5.dp, Color(0xFF1B5E20), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Add Item",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Crisp action buttons dialog triggers + proceed button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { showCustomMedicineDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("add_medicine_button_boom"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2E7D32)),
                border = BorderStroke(2.dp, Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Medicine", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("دوا شامل کریں", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { showCustomItemDialog = true },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .testTag("add_custom_item_button_boom"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2E7D32)),
                border = BorderStroke(2.dp, Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Custom Item", modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("نیا سودا سودیشی", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Final primary send to shopper & proceed Workspace buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val compressedData = WhatsAppShareHandler.generateCompressedPayload(activeTripDetails)
                    val generatedUrl = WhatsAppShareHandler.buildWebpageLink(compressedData)
                    val localizedMessage = WhatsAppShareHandler.getLocalizedWhatsAppMessage(langConfig.key, generatedUrl)

                    val shopperName = activeTrip?.shopperName ?: "Helper"
                    val shopperProfile = shopperProfiles.find { it.name == shopperName }
                    val phoneNumber = shopperProfile?.phoneNumber ?: ""

                    // TODO: INSERT ADMOB INTERSTITIAL AD SHOW() LOGIC HERE

                    WhatsAppShareHandler.shareToWhatsApp(context, localizedMessage, phoneNumber)
                },
                enabled = activeTripDetails.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("send_to_shopper_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(3.dp, Color(0xFF1B5E20))
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", modifier = Modifier.size(24.dp), tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "خریدار کو لسٹ بھیجیں",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = onProceedToShopper,
                enabled = activeTripDetails.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("proceed_button"),
                border = BorderStroke(2.dp, Color(0xFF2E7D32)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = langConfig.strings["proceed"] ?: "PROCEED TO THE SHOPPING WORKSPACE",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    // Medicine Input Dialog
    if (showCustomMedicineDialog) {
        Dialog(onDismissRequest = { showCustomMedicineDialog = false }) {
            var medName by remember { mutableStateOf("") }
            var medQty by remember { mutableStateOf("1") }
            var medUnit by remember { mutableStateOf("Tablets") }
            var medRecipient by remember { mutableStateOf("") }
            var medNotes by remember { mutableStateOf("") }

            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = langConfig.strings["med_dialog_title"] ?: "Add Custom Medicine",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )

                    OutlinedTextField(
                        value = medName,
                        onValueChange = { medName = it },
                        label = { Text(langConfig.strings["med_name"] ?: "Medicine Name", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = medQty,
                            onValueChange = { medQty = it },
                            label = { Text(langConfig.strings["med_qty"] ?: "Quantity", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                        )
                        OutlinedTextField(
                            value = medUnit,
                            onValueChange = { medUnit = it },
                            label = { Text("Unit (Tablets)", fontWeight = FontWeight.Bold) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    OutlinedTextField(
                        value = medRecipient,
                        onValueChange = { medRecipient = it },
                        label = { Text(langConfig.strings["med_recipient"] ?: "Intended Recipient Person", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    OutlinedTextField(
                        value = medNotes,
                        onValueChange = { medNotes = it },
                        label = { Text(langConfig.strings["med_notes"] ?: "Instructions", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomMedicineDialog = false }) {
                            Text(langConfig.strings["cancel"] ?: "Cancel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (medName.isNotBlank()) {
                                    val finalFullMedName = "$medName ($medUnit)"
                                    viewModel.addCustomItemToTrip(
                                        itemName = finalFullMedName,
                                        category = "💊 Custom Medicine",
                                        qty = medQty,
                                        reqBrand = "N/A",
                                        isMedicine = true,
                                        recipient = medRecipient,
                                        notes = medNotes
                                    )
                                    showCustomMedicineDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(langConfig.strings["save"] ?: "Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Generic Custom Item dialog form
    if (showCustomItemDialog) {
        Dialog(onDismissRequest = { showCustomItemDialog = false }) {
            var customItemName by remember { mutableStateOf("") }
            var customItemBrand by remember { mutableStateOf("") }
            var customItemQty by remember { mutableStateOf("1") }

            BrutalistCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = "Add Custom Item Form", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)

                    OutlinedTextField(
                        value = customItemName,
                        onValueChange = { customItemName = it },
                        label = { Text("Item Name", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    OutlinedTextField(
                        value = customItemBrand,
                        onValueChange = { customItemBrand = it },
                        label = { Text("Required Brand", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    OutlinedTextField(
                        value = customItemQty,
                        onValueChange = { customItemQty = it },
                        label = { Text(langConfig.strings["qty_label"] ?: "Qty", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomItemDialog = false }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (customItemName.isNotBlank()) {
                                    viewModel.addCustomItemToTrip(
                                        itemName = customItemName,
                                        category = "⭐ ${langConfig.strings["custom_med"] ?: "Custom Item"}",
                                        qty = customItemQty,
                                        reqBrand = customItemBrand
                                    )
                                    showCustomItemDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Save Item", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopperScreen(
    tripId: Long,
    viewModel: GroceryViewModel,
    langConfig: GroceryTranslations.LangConfig,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeTrip by viewModel.activeTrip.collectAsStateWithLifecycle()
    val activeDetails by viewModel.activeTripDetails.collectAsStateWithLifecycle()
    val receiptImages by viewModel.activeReceiptImages.collectAsStateWithLifecycle()

    // Calculate dynamic bill aggregate based on "Available" state items
    val currentTotal = activeDetails.filter { it.availability == "AVAILABLE" }.sumOf { it.price }

    // Launcher setup for taking receipt gallery images copy
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { uri ->
            val localPath = copyUriToInternalStorage(context, uri)
            if (localPath != null) {
                viewModel.addReceiptPhoto(localPath)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core workspace header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .size(48.dp)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = langConfig.strings["shopper_mode"] ?: "SHOPPER ACTIVE MODE",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                activeTrip?.let {
                    Text(
                        text = "Shopper: ${it.shopperName}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Active Shopper Items List
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(activeDetails) { detail ->
                    val isAvailable = detail.availability == "AVAILABLE"
                    val isNotAvailable = detail.availability == "NOT_AVAILABLE"

                    BrutalistCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = if (isAvailable) Color(0xFF2E7D32) else if (isNotAvailable) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Primary Info Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = detail.itemName,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = if (isNotAvailable) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Qty: ${detail.qty} | Brand request: ${detail.reqBrand.ifBlank { "N/A" }}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isNotAvailable) Color.Gray else MaterialTheme.colorScheme.primary
                                    )
                                    if (detail.isMedicine) {
                                        Text(
                                            text = "For: ${detail.recipient}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFDC2626),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Large Accessible Toggle switches for item Availability
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateDetail(detail.copy(availability = "AVAILABLE"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAvailable) Color(0xFF2E7D32) else Color.Transparent,
                                        contentColor = if (isAvailable) Color.White else MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(2.dp, if (isAvailable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(langConfig.strings["available"] ?: "Available", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                }

                                Button(
                                    onClick = {
                                        viewModel.updateDetail(detail.copy(availability = "NOT_AVAILABLE"))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isNotAvailable) Color(0xFFDC2626) else Color.Transparent,
                                        contentColor = if (isNotAvailable) Color.White else MaterialTheme.colorScheme.primary
                                    ),
                                    border = BorderStroke(2.dp, if (isNotAvailable) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(langConfig.strings["not_available"] ?: "Missed / No", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                }
                            }

                            // Dynamic subform inputs for purchased item details
                            if (isAvailable) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = detail.price.let { if (it == 0.0) "" else it.toInt().toString() },
                                        onValueChange = { inputPrice ->
                                            val numericPrice = inputPrice.toDoubleOrNull() ?: 0.0
                                            viewModel.updateDetail(detail.copy(price = numericPrice))
                                        },
                                        label = { Text("Price (Rs.)", fontWeight = FontWeight.Bold) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                                    )

                                    OutlinedTextField(
                                        value = detail.purchasedBrand,
                                        onValueChange = { inputBrand ->
                                            viewModel.updateDetail(detail.copy(purchasedBrand = inputBrand))
                                        },
                                        label = { Text("Actual Brand", fontWeight = FontWeight.Bold) },
                                        modifier = Modifier.weight(1.2f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }

                            // Note/Instruction field
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = detail.notes,
                                onValueChange = { inputNotes ->
                                    viewModel.updateDetail(detail.copy(notes = inputNotes))
                                },
                                label = { Text(langConfig.strings["notes"] ?: "Notes / Intakes Instructions", fontWeight = FontWeight.Bold) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = isNotAvailable,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.primary)
                              )
                        }
                    }
                }
            }
        }

        // Receipt photo list panel picker
        if (receiptImages.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(langConfig.strings["receipt_title"] ?: "Attached Receipt Photos:", fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        receiptImages.forEach { image ->
                            Box {
                                val bitmap = remember(image.filePath) {
                                    BitmapFactory.decodeFile(image.filePath)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Receipt photo",
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Gray)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color.White)
                                    }
                                }
                                IconButton(
                                    onClick = { viewModel.removeReceiptPhoto(image.id) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp)
                                        .background(Color.Red, CircleShape)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Aggregate Bottom Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = langConfig.strings["total_bill"] ?: "Total Bill:",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${langConfig.strings["currency"] ?: "Rs."} ${currentTotal.toInt()}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Share & Attach action triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Camera Attachment button
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                modifier = Modifier
                    .weight(0.8f)
                    .height(60.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "Attach receipts")
                Spacer(modifier = Modifier.width(4.dp))
                Text(langConfig.strings["btn_receipt"] ?: "ATTACH PHOTO", fontWeight = FontWeight.Black)
            }

            // WhatsApp send message dialog
            Button(
                onClick = {
                    viewModel.completeShoppingTrip(currentTotal) {
                        // Construct the localized formatted summary representation
                        val summaryText = buildWhatsAppSummary(
                            activeDetails = activeDetails,
                            shopperName = activeTrip?.shopperName ?: "Helper",
                            langConfig = langConfig,
                            grandTotal = currentTotal
                        )
                        
                        shareWhatsAppMsg(context, summaryText)
                        onBack()
                    }
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(60.dp)
                    .testTag("complete_button"),
                border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White)
            ) {
                Text(
                    text = langConfig.strings["btn_complete"] ?: "COMPLETE & SEND",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// WhatsApp summary copy formatter
fun buildWhatsAppSummary(
    activeDetails: List<TripDetail>,
    shopperName: String,
    langConfig: GroceryTranslations.LangConfig,
    grandTotal: Double
): String {
    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
    val builder = StringBuilder()
    
    builder.append("🛒 *Grocery List* - SHOPPER SUMMARY\n")
    builder.append("📅 Date: $dateStr\n")
    builder.append("👤 Shopper Name: $shopperName\n")
    builder.append("========================\n\n")
    
    // Available section items
    val availableList = activeDetails.filter { it.availability == "AVAILABLE" }
    if (availableList.isNotEmpty()) {
        builder.append("✅ *ISSUED/PURCHASED ITEMS*:\n")
        availableList.forEach { detail ->
            builder.append("• *${detail.itemName}* (Qty: ${detail.qty})\n")
            if (detail.purchasedBrand.isNotBlank()) {
                builder.append("   - Brand: ${detail.purchasedBrand}\n")
            }
            if (detail.price > 0.0) {
                builder.append("   - Price: Rs. ${detail.price.toInt()}\n")
            }
            if (detail.notes.isNotBlank()) {
                builder.append("   - Notes: ${detail.notes}\n")
            }
        }
        builder.append("\n")
    }

    // Missed section items
    val missedList = activeDetails.filter { it.availability == "NOT_AVAILABLE" }
    if (missedList.isNotEmpty()) {
        builder.append("❌ *UNAVAILABLE/MISSED ITEMS*:\n")
        missedList.forEach { detail ->
            builder.append("• *${detail.itemName}* (${detail.category})\n")
            if (detail.notes.isNotBlank()) {
                builder.append("   - Notes: ${detail.notes}\n")
            }
        }
        builder.append("\n")
    }
    
    builder.append("========================\n")
    builder.append("🧾 *TOTAL BILL:* Rs. ${grandTotal.toInt()}\n")
    
    return builder.toString()
}

// Share Intent trigger helper
fun shareWhatsAppMsg(context: Context, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            `package` = "com.whatsapp"
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback standard chooser if WhatsApp not native installed
        val genericIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(genericIntent, "Share complete list summary"))
    }
}

// Brutalist Style Card Component
@Composable
fun BrutalistCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.border(
            width = 3.dp,
            color = borderColor,
            shape = RoundedCornerShape(16.dp)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() }
    )
}

// Helper Uri Copy handler
fun copyUriToInternalStorage(context: Context, uri: Uri): String? {
    return try {
        val resolver = context.contentResolver
        val inputStream = resolver.openInputStream(uri) ?: return null
        val fileName = "receipt_${System.currentTimeMillis()}.jpg"
        val receiptsDir = File(context.filesDir, "receipts")
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs()
        }
        val destinationFile = File(receiptsDir, fileName)
        val outputStream = FileOutputStream(destinationFile)
        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        destinationFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun HomeActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, if (enabled) Color(0xFF2E7D32) else Color.LightGray),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large icon on left
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (enabled) Color(0xFF2E7D32) else Color.Gray,
                modifier = Modifier.size(36.dp)
            )
            
            // Bold title & subtitle in center
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) Color(0xFF2E7D32) else Color.Gray,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Right-facing arrow indicator (>)
            Text(
                text = "▶",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = if (enabled) Color(0xFF2E7D32) else Color.Gray
            )
        }
    }
}

