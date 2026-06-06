package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.data.TripDetail
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object WhatsAppShareHandler {

    data class ParsedCompletionData(
        val tripId: Long,
        val totalBill: Double,
        val receiptImageBase64: String,
        val items: List<ParsedItem>
    )

    data class ParsedItem(
        val id: Long,
        val name: String,
        val purchasedBrand: String,
        val price: Double,
        val availability: String
    )

    /**
     * Converts a list of TripDetail items into a JSON string and compresses it into a Base64 text string.
     */
    fun generateCompressedPayload(details: List<TripDetail>, tripId: Long = 0L): String {
        val rootObject = JSONObject().apply {
            put("tripId", tripId)
            val jsonArray = JSONArray()
            for (item in details) {
                val jsonObject = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.itemName)
                    put("qty", item.qty)
                    put("brand", item.reqBrand)
                    put("med", item.isMedicine)
                    put("recipient", item.recipient)
                    put("notes", item.notes)
                }
                jsonArray.put(jsonObject)
            }
            put("items", jsonArray)
        }
        val jsonString = rootObject.toString()
        return compressString(jsonString)
    }

    private fun compressString(input: String): String {
        val bos = ByteArrayOutputStream()
        val gzos = GZIPOutputStream(bos)
        gzos.write(input.toByteArray(Charsets.UTF_8))
        gzos.close()
        val compressedBytes = bos.toByteArray()
        return Base64.encodeToString(compressedBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    /**
     * Extracts and decompresses a base64 string. Fallbacks to raw parsing if standard or corrupted.
     */
    fun decompressString(compressedBase64: String): String {
        val decodedBytes = try {
            Base64.decode(compressedBase64.trim(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e1: Exception) {
            try {
                Base64.decode(compressedBase64.trim(), Base64.DEFAULT)
            } catch (e2: Exception) {
                return ""
            }
        }

        return try {
            val bais = ByteArrayInputStream(decodedBytes)
            val gzis = GZIPInputStream(bais)
            val reader = InputStreamReader(gzis, Charsets.UTF_8)
            val inBuffer = BufferedReader(reader)
            val sb = StringBuilder()
            var line: String?
            while (inBuffer.readLine().also { line = it } != null) {
                sb.append(line)
            }
            inBuffer.close()
            gzis.close()
            bais.close()
            sb.toString()
        } catch (e: Exception) {
            // GZIP failed - possibly raw JSON or non-gzipped base64
            try {
                String(decodedBytes, Charsets.UTF_8)
            } catch (e2: Exception) {
                ""
            }
        }
    }

    /**
     * Scans arbitrary WhatsApp message text to extract the embedded Base64 payload.
     */
    fun extractBase64Payload(text: String): String {
        // 1. Try bracket tags like [SHOPPER_DATA:xxx]
        val tagPattern = Regex("\\[(?:SHOPPER_DATA|DATA|PAYLOAD):([A-Za-z0-9_\\-+=\\/]+)\\]")
        val tagMatch = tagPattern.find(text)
        if (tagMatch != null) {
            return tagMatch.groupValues[1]
        }

        // 2. Try query params like ?response=xxx or ?list=xxx
        val queryPattern = Regex("[?&](?:response|list|completed|data)=([A-Za-z0-9_\\-+=\\/]+)")
        val queryMatch = queryPattern.find(text)
        if (queryMatch != null) {
            return queryMatch.groupValues[1]
        }

        // 3. Try to find the longest alphanumeric continuous block of reasonable size (heuristic)
        val base64Pattern = Regex("[A-Za-z0-9_\\-+=\\/]{20,}")
        val matches = base64Pattern.findAll(text).toList()
        if (matches.isNotEmpty()) {
            return matches.maxByOrNull { it.value.length }?.value ?: ""
        }

        return text.trim()
    }

    /**
     * Decodes the string payload completely.
     */
    fun decodePayload(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed
        }
        val extracted = extractBase64Payload(trimmed)
        if (extracted.isEmpty()) return ""
        return decompressString(extracted)
    }

    /**
     * Parses the decoded JSON string into helper Kotlin data models.
     */
    fun parseShopperCompletion(payloadJson: String): ParsedCompletionData? {
        if (payloadJson.isBlank()) return null
        return try {
            val root = JSONObject(payloadJson)
            val id = root.optLong("tripId", 0L)
            val totalBill = root.optDouble("totalBill", 0.0)
            val receiptImg = root.optString("receiptImage", "")
            
            val itemsList = mutableListOf<ParsedItem>()
            val itemsArray = root.optJSONArray("items")
            if (itemsArray != null) {
                for (i in 0 until itemsArray.length()) {
                    val itemObj = itemsArray.getJSONObject(i)
                    val itemId = itemObj.optLong("id", 0L)
                    val name = itemObj.optString("name", "")
                    val purchasedBrand = itemObj.optString("purchasedBrand", "")
                    val price = itemObj.optDouble("price", 0.0)
                    val availability = itemObj.optString("availability", "PENDING")
                    
                    itemsList.add(
                        ParsedItem(
                            id = itemId,
                            name = name,
                            purchasedBrand = purchasedBrand,
                            price = price,
                            availability = availability
                        )
                    )
                }
            }
            ParsedCompletionData(
                tripId = id,
                totalBill = totalBill,
                receiptImageBase64 = receiptImg,
                items = itemsList
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decodes Base64 receipt image and saves it to internal app storage.
     * Returns the local file path, or null if failed.
     */
    fun saveReceiptImageToInternalStorage(context: Context, tripId: Long, base64Image: String): String? {
        if (base64Image.isBlank()) return null
        try {
            val cleanBase64 = if (base64Image.contains(",")) {
                base64Image.substringAfter(",")
            } else {
                base64Image
            }
            val decodedBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val directory = File(context.filesDir, "receipts")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = File(directory, "receipt_trip_${tripId}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                out.write(decodedBytes)
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Builds the webpage link: "https://yourname.github.io/index.html?list=[COMPRESSED_STRING]"
     */
    fun buildWebpageLink(compressedString: String, userName: String = "itx-engineer"): String {
        return "https://$userName.github.io/index.html?list=$compressedString"
    }

    /**
     * Prepares a clean WhatsApp text template string translated into the active localized language selection
     */
    fun getLocalizedWhatsAppMessage(langCode: String, urlLink: String): String {
        return when (langCode) {
            "ur" -> "السلام علیکم / ہیلو۔ براہ کرم یہ گراسری خریدیں۔ فہرست دیکھنے اور قیمتیں درج کرنے کے لیے لنک کھولیں: $urlLink شکریہ۔"
            "hi" -> "अस्सलाम-ओ-अलैकुम / नमस्ते। कृपया ये किराना सामान खरीदें। सूची देखने और कीमतें दर्ज करने के लिए लिंक खोलें: $urlLink धन्यवाद।"
            "bn" -> "আসসালামু আলাইকুম / হ্যালো। অনুগ্রহ করে এই মুদি জিনিসপত্র কিনুন। তালিকা দেখতে এবং দাম লিখতে লিঙ্কটি খুলুন: $urlLink ধন্যবাদ।"
            "ps" -> "السلام علیکم / هلو. هیله ده دا سودا واخلئ. د لیست لیدلو او د قیمتونو داخلولو لپاره لینک خلاص کړئ: $urlLink مننه."
            "sd" -> "السلام عليڪم / هيلو. مهرباني ڪري هي گراسري خريد ڪريو. لسٽ ڏسڻ ۽ قيمتون داخل ڪرڻ لاءِ لنڪ کوليو: $urlLink مهرباني."
            "pa" -> "ਅੱਸਲਾਮ-ਓ-ਅਲੈਕਮ / ਨਮਸਤੇ। ਕਿਰਪਾ ਕਰਕੇ ਇਹ ਕਰਿਆਨੇ ਦਾ ਸਾਮਾਨ ਖਰੀਦੋ। ਸੂਚੀ ਦੇਖਣ ਅਤੇ ਕੀਮਤਾਂ ਦਰਜ ਕਰਨ ਲਈ ਲਿੰਕ ਖੋਲ੍ਹੋ: $urlLink ਧੰਨਵਾਦ।"
            "skr" -> "السلام علیکم / ہیلو۔ مہربانی کر تے اے سودا سلف گھن گھنو۔ فہرست ڈیڑھݨ تے قیمتاں لکھݨ کیتے لنک کھولھو: $urlLink شکریہ۔"
            else -> "Assalam-o-Alaikum / Hello. Please buy these groceries. Open the link to see the list and enter prices: $urlLink Thank you."
        }
    }

    /**
     * Triggers sharing to WhatsApp with ad hook and fallback
     */
    fun shareToWhatsApp(context: Context, messageText: String, phoneNumber: String = "") {
        // TODO: INSERT ADMOB INTERSTITIAL AD SHOW() LOGIC HERE
        
        try {
            val intent = if (phoneNumber.isNotBlank()) {
                val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=" + Uri.encode(messageText))
                    `package` = "com.whatsapp"
                }
            } else {
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    `package` = "com.whatsapp"
                }
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback standard chooser if WhatsApp not native installed
            val genericIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, messageText)
            }
            context.startActivity(Intent.createChooser(genericIntent, "Share grocery list link"))
        }
    }
}
