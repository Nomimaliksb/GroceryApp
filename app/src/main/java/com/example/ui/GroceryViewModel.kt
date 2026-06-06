package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.GroceryTranslations
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class GroceryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GroceryRepository
    
    // Language state
    val languageCode = MutableStateFlow("en")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GroceryRepository(database.groceryDao())
        
        val sharedPrefs = application.getSharedPreferences("grocery_prefs", android.content.Context.MODE_PRIVATE)
        val savedLang = sharedPrefs.getString("selected_lang", "en") ?: "en"
        languageCode.value = savedLang
        
        viewModelScope.launch {
            repository.prepopulateDefaultsIfEmpty(savedLang)
        }
    }

    val langConfig: StateFlow<GroceryTranslations.LangConfig> = languageCode
        .map { GroceryTranslations.getByKey(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GroceryTranslations.getByKey("en")
        )

    // Observe Profiles and Trips from DB
    val shopperProfiles: StateFlow<List<ShopperProfile>> = repository.shopperProfilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTrips: StateFlow<List<ShoppingTrip>> = repository.shoppingTripsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customItems: StateFlow<List<CustomItem>> = repository.customItemsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Trip tracking
    val activeTripId = MutableStateFlow<Long?>(null)

    val activeTrip: StateFlow<ShoppingTrip?> = activeTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getShoppingTripFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeTripDetails: StateFlow<List<TripDetail>> = activeTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getTripDetailsFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeReceiptImages: StateFlow<List<ReceiptImage>> = activeTripId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getReceiptImagesFlow(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouped History for Year -> Month -> Trips
    val groupedTrips: StateFlow<Map<String, Map<String, List<ShoppingTrip>>>> = allTrips
        .map { trips ->
            val sdfYear = SimpleDateFormat("yyyy", Locale.getDefault())
            val sdfMonth = SimpleDateFormat("MMMM", Locale.getDefault())
            
            trips.filter { it.status == "COMPLETED" }
                .groupBy { trip ->
                    sdfYear.format(Date(trip.dateMillis))
                }.mapValues { entry ->
                    entry.value.groupBy { trip ->
                        sdfMonth.format(Date(trip.dateMillis))
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun selectLanguage(lang: String) {
        viewModelScope.launch {
            languageCode.value = lang
            val sharedPrefs = getApplication<Application>().getSharedPreferences("grocery_prefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("selected_lang", lang).apply()
            repository.prepopulateDefaultsIfEmpty(lang)
        }
    }

    fun addShopper(name: String, relationship: String, phoneNumber: String) {
        viewModelScope.launch {
            repository.addShopperProfile(name, relationship, phoneNumber)
        }
    }

    fun startNewTrip(shopperName: String, onTripCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val tripId = repository.createShoppingTrip(shopperName)
            activeTripId.value = tripId
            onTripCreated(tripId)
        }
    }

    fun repeatLastTrip(shopperName: String, onTripCreated: (Long) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val tripId = repository.createShoppingTrip(shopperName)
            val success = repository.repeatLastList(tripId)
            if (success) {
                activeTripId.value = tripId
                onTripCreated(tripId)
            } else {
                repository.deleteShoppingTrip(ShoppingTrip(id = tripId, shopperName = shopperName))
                onError()
            }
        }
    }

    fun addMissedLastTripItems(shopperName: String, onTripCreated: (Long) -> Unit, onError: () -> Unit) {
        viewModelScope.launch {
            val tripId = repository.createShoppingTrip(shopperName)
            val addedCount = repository.selectAndAddMissedLastTime(tripId)
            if (addedCount > 0) {
                activeTripId.value = tripId
                onTripCreated(tripId)
            } else {
                repository.deleteShoppingTrip(ShoppingTrip(id = tripId, shopperName = shopperName))
                onError()
            }
        }
    }

    fun addCustomItemToTrip(itemName: String, category: String, qty: String, reqBrand: String, isMedicine: Boolean = false, recipient: String = "", notes: String = "") {
        val tripId = activeTripId.value ?: return
        viewModelScope.launch {
            val detail = TripDetail(
                tripId = tripId,
                itemName = itemName,
                category = category,
                qty = qty,
                reqBrand = reqBrand,
                isCustom = true,
                isMedicine = isMedicine,
                recipient = recipient,
                notes = notes
            )
            repository.addTripDetail(detail)
            
            // Also permanently save it in CustomItem for suggestions
            repository.addCustomItem(
                name = itemName,
                category = category,
                isMedicine = isMedicine,
                recipient = recipient,
                notes = notes
            )
        }
    }

    fun addDefaultItemToTrip(category: String, itemName: String) {
        val tripId = activeTripId.value ?: return
        viewModelScope.launch {
            val detail = TripDetail(
                tripId = tripId,
                itemName = itemName,
                category = category,
                qty = "1",
                reqBrand = ""
            )
            repository.addTripDetail(detail)
        }
    }

    fun updateDetail(detail: TripDetail) {
        viewModelScope.launch {
            repository.updateTripDetail(detail)
            recalculateTotal()
        }
    }

    fun removeDetail(detailId: Long) {
        viewModelScope.launch {
            repository.removeTripDetail(detailId)
            recalculateTotal()
        }
    }

    fun addReceiptPhoto(filePath: String) {
        val tripId = activeTripId.value ?: return
        viewModelScope.launch {
            repository.addReceiptImage(tripId, filePath)
        }
    }

    fun removeReceiptPhoto(id: Long) {
        viewModelScope.launch {
            repository.removeReceiptImage(id)
        }
    }

    fun completeShoppingTrip(totalBill: Double, onComplete: () -> Unit) {
        val tripId = activeTripId.value ?: return
        viewModelScope.launch {
            val trip = repository.getShoppingTrip(tripId)
            if (trip != null) {
                val updatedTrip = trip.copy(
                    status = "COMPLETED",
                    totalBill = totalBill
                )
                repository.updateShoppingTrip(updatedTrip)
            }
            onComplete()
        }
    }

    fun importShopperWhatsAppMessage(context: android.content.Context, rawText: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val decodedJson = WhatsAppShareHandler.decodePayload(rawText)
                if (decodedJson.isBlank()) {
                    onResult(false, "Could not find or extract encoded shopper payload in the pasted content.")
                    return@launch
                }
                val parsed = WhatsAppShareHandler.parseShopperCompletion(decodedJson)
                if (parsed == null) {
                    onResult(false, "Failed to parse completed shopping data structure.")
                    return@launch
                }

                // Locate structural trip
                var targetTrip = if (parsed.tripId > 0L) {
                    repository.getShoppingTrip(parsed.tripId)
                } else {
                    null
                }

                if (targetTrip == null) {
                    // Fallback to active trip or latest general trip
                    val fallbackId = activeTripId.value
                    if (fallbackId != null) {
                        targetTrip = repository.getShoppingTrip(fallbackId)
                    }
                    if (targetTrip == null) {
                        val allTripsList = allTrips.value
                        if (allTripsList.isNotEmpty()) {
                            targetTrip = allTripsList.find { it.status != "COMPLETED" } 
                                ?: allTripsList.first()
                        }
                    }
                }

                if (targetTrip == null) {
                    onResult(false, "No matching shopping trip found in local database.")
                    return@launch
                }

                val tripId = targetTrip.id
                val localDetails = repository.getTripDetails(tripId)

                // Update details
                for (parsedItem in parsed.items) {
                    val matchedLocal = localDetails.find { it.id == parsedItem.id }
                        ?: localDetails.find { it.itemName.equals(parsedItem.name, ignoreCase = true) }

                    if (matchedLocal != null) {
                        val updatedDetail = matchedLocal.copy(
                            purchasedBrand = parsedItem.purchasedBrand,
                            price = parsedItem.price,
                            availability = parsedItem.availability
                        )
                        repository.updateTripDetail(updatedDetail)
                    }
                }

                // Decode and save receipt image
                if (parsed.receiptImageBase64.isNotBlank()) {
                    val savedPath = WhatsAppShareHandler.saveReceiptImageToInternalStorage(context, tripId, parsed.receiptImageBase64)
                    if (savedPath != null) {
                        repository.addReceiptImage(tripId, savedPath)
                    }
                }

                // Recalculate bill
                val detailsNow = repository.getTripDetails(tripId)
                val calculatedTotal = detailsNow.filter { it.availability == "AVAILABLE" }.sumOf { it.price }
                val finalBill = if (parsed.totalBill > 0.0) parsed.totalBill else calculatedTotal

                val updatedTrip = targetTrip.copy(
                    status = "COMPLETED",
                    totalBill = finalBill
                )
                repository.updateShoppingTrip(updatedTrip)

                onResult(true, "Successfully imported list and completed trip #${tripId}!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "Error: ${e.localizedMessage ?: "Unknown parsing error"}")
            }
        }
    }

    private suspend fun recalculateTotal() {
        val tripId = activeTripId.value ?: return
        val currentTrip = repository.getShoppingTrip(tripId) ?: return
        val details = repository.getTripDetails(tripId)
        val calculatedTotal = details.filter { it.availability == "AVAILABLE" }.sumOf { it.price }
        repository.updateShoppingTrip(currentTrip.copy(totalBill = calculatedTotal))
    }
}
