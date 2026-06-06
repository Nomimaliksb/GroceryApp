package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GroceryDao {

    // Default Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDefaultItems(items: List<DefaultItem>)

    @Query("SELECT COUNT(*) FROM default_items WHERE languageCode = :langCode")
    suspend fun getDefaultItemsCountForLanguage(langCode: String): Int

    @Query("SELECT * FROM default_items WHERE languageCode = :langCode ORDER BY id ASC")
    fun getDefaultItemsFlow(langCode: String): Flow<List<DefaultItem>>

    @Query("SELECT DISTINCT category FROM default_items WHERE languageCode = :langCode")
    fun getCategoriesFlow(langCode: String): Flow<List<String>>

    @Query("SELECT * FROM default_items WHERE languageCode = :langCode AND category = :category ORDER BY id ASC")
    suspend fun getDefaultItemsByCategory(langCode: String, category: String): List<DefaultItem>


    // Custom Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomItem(item: CustomItem): Long

    @Query("SELECT * FROM custom_items ORDER BY id DESC")
    fun getCustomItemsFlow(): Flow<List<CustomItem>>

    @Query("SELECT * FROM custom_items WHERE category = :category ORDER BY id DESC")
    suspend fun getCustomItemsByCategory(category: String): List<CustomItem>


    // Shopper Profiles
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShopperProfile(profile: ShopperProfile)

    @Query("SELECT * FROM shopper_profiles ORDER BY name ASC")
    fun getShopperProfilesFlow(): Flow<List<ShopperProfile>>

    @Query("SELECT * FROM shopper_profiles")
    suspend fun getShopperProfiles(): List<ShopperProfile>


    // Shopping Trips
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoppingTrip(trip: ShoppingTrip): Long

    @Update
    suspend fun updateShoppingTrip(trip: ShoppingTrip)

    @Delete
    suspend fun deleteShoppingTrip(trip: ShoppingTrip)

    @Query("SELECT * FROM shopping_trips ORDER BY dateMillis DESC")
    fun getShoppingTripsFlow(): Flow<List<ShoppingTrip>>

    @Query("SELECT * FROM shopping_trips WHERE id = :tripId")
    fun getShoppingTripFlow(tripId: Long): Flow<ShoppingTrip?>

    @Query("SELECT * FROM shopping_trips WHERE id = :tripId")
    suspend fun getShoppingTrip(tripId: Long): ShoppingTrip?

    @Query("SELECT * FROM shopping_trips WHERE status = 'COMPLETED' ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLastCompletedTrip(): ShoppingTrip?

    @Query("SELECT * FROM shopping_trips ORDER BY dateMillis DESC LIMIT 1")
    suspend fun getLastTrip(): ShoppingTrip?


    // Trip Details
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripDetails(details: List<TripDetail>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTripDetail(detail: TripDetail): Long

    @Update
    suspend fun updateTripDetail(detail: TripDetail)

    @Query("DELETE FROM trip_details WHERE id = :id")
    suspend fun deleteTripDetailById(id: Long)

    @Query("SELECT * FROM trip_details WHERE tripId = :tripId ORDER BY id ASC")
    fun getTripDetailsFlow(tripId: Long): Flow<List<TripDetail>>

    @Query("SELECT * FROM trip_details WHERE tripId = :tripId ORDER BY id ASC")
    suspend fun getTripDetails(tripId: Long): List<TripDetail>


    // Receipt Images
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptImage(image: ReceiptImage): Long

    @Query("SELECT * FROM receipt_images WHERE tripId = :tripId ORDER BY id ASC")
    fun getReceiptImagesFlow(tripId: Long): Flow<List<ReceiptImage>>

    @Query("SELECT * FROM receipt_images WHERE tripId = :tripId")
    suspend fun getReceiptImages(tripId: Long): List<ReceiptImage>

    @Query("DELETE FROM receipt_images WHERE id = :id")
    suspend fun deleteReceiptImageById(id: Long)
}
