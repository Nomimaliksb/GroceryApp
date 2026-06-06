package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "default_items")
data class DefaultItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val itemName: String,
    val languageCode: String
)

@Entity(tableName = "custom_items")
data class CustomItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemName: String,
    val category: String,
    val isMedicine: Boolean = false,
    val recipient: String? = null,
    val notes: String? = null
)

@Entity(tableName = "shopper_profiles")
data class ShopperProfile(
    @PrimaryKey val name: String,
    val relationship: String,
    val phoneNumber: String
)

@Entity(tableName = "shopping_trips")
data class ShoppingTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long = System.currentTimeMillis(),
    val shopperName: String,
    val totalBill: Double = 0.0,
    val status: String = "CREATING" // "CREATING", "ACTIVE", "COMPLETED"
)

@Entity(
    tableName = "trip_details",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingTrip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class TripDetail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val itemName: String,
    val category: String,
    val qty: String = "1",
    val reqBrand: String = "",
    val purchasedBrand: String = "",
    val price: Double = 0.0,
    val notes: String = "",
    val availability: String = "PENDING", // "PENDING", "AVAILABLE", "NOT_AVAILABLE"
    val isCustom: Boolean = false,
    val isMedicine: Boolean = false,
    val recipient: String = ""
)

@Entity(
    tableName = "receipt_images",
    foreignKeys = [
        ForeignKey(
            entity = ShoppingTrip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId")]
)
data class ReceiptImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val filePath: String
)
