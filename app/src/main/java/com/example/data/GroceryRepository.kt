package com.example.data

import com.example.GroceryTranslations
import kotlinx.coroutines.flow.Flow

class GroceryRepository(private val groceryDao: GroceryDao) {

    // Default Items
    fun getDefaultItemsFlow(langCode: String): Flow<List<DefaultItem>> =
        groceryDao.getDefaultItemsFlow(langCode)

    fun getCategoriesFlow(langCode: String): Flow<List<String>> =
        groceryDao.getCategoriesFlow(langCode)

    suspend fun prepopulateDefaultsIfEmpty(langCode: String) {
        val count = groceryDao.getDefaultItemsCountForLanguage(langCode)
        if (count == 0) {
            val config = GroceryTranslations.getByKey(langCode)
            val list = mutableListOf<DefaultItem>()
            config.defaultItems.forEach { (category, items) ->
                items.forEach { itemName ->
                    list.add(
                        DefaultItem(
                            category = category,
                            itemName = itemName,
                            languageCode = langCode
                        )
                    )
                }
            }
            if (list.isNotEmpty()) {
                groceryDao.insertDefaultItems(list)
            }
        }
    }

    // Custom Items
    val customItemsFlow: Flow<List<CustomItem>> = groceryDao.getCustomItemsFlow()

    suspend fun addCustomItem(name: String, category: String, isMedicine: Boolean = false, recipient: String? = null, notes: String? = null): Long {
        val item = CustomItem(
            itemName = name,
            category = category,
            isMedicine = isMedicine,
            recipient = recipient,
            notes = notes
        )
        return groceryDao.insertCustomItem(item)
    }

    // Shopper Profiles
    val shopperProfilesFlow: Flow<List<ShopperProfile>> = groceryDao.getShopperProfilesFlow()

    suspend fun addShopperProfile(name: String, relationship: String, phoneNumber: String) {
        groceryDao.insertShopperProfile(
            ShopperProfile(name = name, relationship = relationship, phoneNumber = phoneNumber)
        )
    }

    suspend fun getShopperProfiles(): List<ShopperProfile> = groceryDao.getShopperProfiles()

    // Shopping Trips
    val shoppingTripsFlow: Flow<List<ShoppingTrip>> = groceryDao.getShoppingTripsFlow()

    fun getShoppingTripFlow(tripId: Long): Flow<ShoppingTrip?> =
        groceryDao.getShoppingTripFlow(tripId)

    suspend fun getShoppingTrip(tripId: Long): ShoppingTrip? =
        groceryDao.getShoppingTrip(tripId)

    suspend fun createShoppingTrip(shopperName: String): Long {
        val trip = ShoppingTrip(
            dateMillis = System.currentTimeMillis(),
            shopperName = shopperName,
            status = "CREATING",
            totalBill = 0.0
        )
        return groceryDao.insertShoppingTrip(trip)
    }

    suspend fun updateShoppingTrip(trip: ShoppingTrip) {
        groceryDao.updateShoppingTrip(trip)
    }

    suspend fun deleteShoppingTrip(trip: ShoppingTrip) {
        groceryDao.deleteShoppingTrip(trip)
    }

    // Trip Details
    fun getTripDetailsFlow(tripId: Long): Flow<List<TripDetail>> =
        groceryDao.getTripDetailsFlow(tripId)

    suspend fun getTripDetails(tripId: Long): List<TripDetail> =
        groceryDao.getTripDetails(tripId)

    suspend fun addTripDetail(detail: TripDetail): Long =
        groceryDao.insertTripDetail(detail)

    suspend fun updateTripDetail(detail: TripDetail) =
        groceryDao.updateTripDetail(detail)

    suspend fun removeTripDetail(id: Long) =
        groceryDao.deleteTripDetailById(id)

    // Receipt Images
    fun getReceiptImagesFlow(tripId: Long): Flow<List<ReceiptImage>> =
        groceryDao.getReceiptImagesFlow(tripId)

    suspend fun addReceiptImage(tripId: Long, filePath: String): Long {
        return groceryDao.insertReceiptImage(ReceiptImage(tripId = tripId, filePath = filePath))
    }

    suspend fun removeReceiptImage(id: Long) {
        groceryDao.deleteReceiptImageById(id)
    }

    /**
     * Finds the last shopping trip, duplicates all of its items into the newly created trip [newTripId],
     * and resets status details (availability -> PENDING, purchased brand -> empty, price -> 0.0, etc.)
     */
    suspend fun repeatLastList(newTripId: Long): Boolean {
        val lastTrip = groceryDao.getLastTrip() ?: return false
        val lastDetails = groceryDao.getTripDetails(lastTrip.id)
        if (lastDetails.isEmpty()) return false

        val duplicatedDetails = lastDetails.map { detail ->
            TripDetail(
                tripId = newTripId,
                itemName = detail.itemName,
                category = detail.category,
                qty = detail.qty,
                reqBrand = detail.reqBrand,
                purchasedBrand = "",
                price = 0.0,
                notes = detail.notes,
                availability = "PENDING",
                isCustom = detail.isCustom,
                isMedicine = detail.isMedicine,
                recipient = detail.recipient
            )
        }
        groceryDao.insertTripDetails(duplicatedDetails)
        return true
    }

    /**
     * Finds items marked 'NOT_AVAILABLE' in the last trip and adds them automatically to a newly created list [newTripId].
     */
    suspend fun selectAndAddMissedLastTime(newTripId: Long): Int {
        val lastTrip = groceryDao.getLastTrip() ?: return 0
        val lastDetails = groceryDao.getTripDetails(lastTrip.id)
        val missedDetails = lastDetails.filter { it.availability == "NOT_AVAILABLE" }
        if (missedDetails.isEmpty()) return 0

        val autoAddedDetails = missedDetails.map { detail ->
            TripDetail(
                tripId = newTripId,
                itemName = detail.itemName,
                category = detail.category,
                qty = detail.qty,
                reqBrand = detail.reqBrand,
                purchasedBrand = "",
                price = 0.0,
                notes = "Missed last time!",
                availability = "PENDING",
                isCustom = detail.isCustom,
                isMedicine = detail.isMedicine,
                recipient = detail.recipient
            )
        }
        groceryDao.insertTripDetails(autoAddedDetails)
        return autoAddedDetails.size
    }
}
