package com.cherryblossomdev.breakroom.data

import android.content.Context
import android.net.Uri
import com.cherryblossomdev.breakroom.data.models.*
import com.cherryblossomdev.breakroom.network.BreakroomApiService
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class CollectionsRepository(
    private val apiService: BreakroomApiService,
    private val tokenManager: TokenManager,
    private val context: Context
) {
    private fun auth(): String? = tokenManager.getBearerToken()

    // ── Collections ──────────────────────────────────────────────────────────

    suspend fun getCollections(): BreakroomResult<List<StoreCollection>> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getCollections(token)
            when {
                response.isSuccessful -> BreakroomResult.Success(response.body() ?: emptyList())
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load collections")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createCollection(name: String, backgroundColor: String?): BreakroomResult<StoreCollection> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val settings = if (backgroundColor != null) CollectionSettings(backgroundColor) else null
            val response = apiService.createCollection(token, CreateCollectionRequest(name, settings))
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to create collection")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateCollection(
        id: Int,
        name: String,
        backgroundType: String,
        backgroundColor: String,
        backgroundImagePath: String?,
        imageUri: Uri?
    ): BreakroomResult<StoreCollection> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val plain = "text/plain".toMediaTypeOrNull()
            val namePart = name.toRequestBody(plain)
            val bgTypePart = backgroundType.toRequestBody(plain)
            val bgColorPart = backgroundColor.toRequestBody(plain)
            val bgImagePathPart = backgroundImagePath?.toRequestBody(plain)
            val imagePart = imageUri?.let { buildImagePart(it, "image") }

            val response = apiService.updateCollection(token, id, namePart, bgTypePart, bgColorPart, bgImagePathPart, imagePart)
            when {
                response.isSuccessful -> {
                    val settings = CollectionSettings(
                        background_color = backgroundColor,
                        background_type = backgroundType,
                        background_image = backgroundImagePath
                    )
                    BreakroomResult.Success(StoreCollection(id = id, name = name, settings = settings))
                }
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to update collection")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun reorderCollections(ids: List<Int>): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.reorderCollections(token, ReorderCollectionsRequest(ids))
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to reorder collections")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteCollection(id: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteCollection(token, id)
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to delete collection")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    suspend fun getItems(collectionId: Int): BreakroomResult<List<CollectionItem>> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getCollectionItems(token, collectionId)
            when {
                response.isSuccessful -> BreakroomResult.Success(response.body() ?: emptyList())
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load items")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun createItem(
        collectionId: Int,
        name: String,
        description: String?,
        priceCents: Int?,
        isAvailable: Boolean,
        inGallery: Boolean,
        shippingCostCents: Int?,
        weightOz: Double?,
        lengthIn: Double?,
        widthIn: Double?,
        heightIn: Double?,
        imageUri: Uri?
    ): BreakroomResult<CollectionItem> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val plain = "text/plain".toMediaTypeOrNull()
            val namePart = name.toRequestBody(plain)
            val descPart = description?.toRequestBody(plain)
            val pricePart = priceCents?.toString()?.toRequestBody(plain)
            val availPart = (if (isAvailable) "1" else "0").toRequestBody(plain)
            val galleryPart = (if (inGallery) "1" else "0").toRequestBody(plain)
            val shipPart = shippingCostCents?.toString()?.toRequestBody(plain)
            val weightPart = weightOz?.toString()?.toRequestBody(plain)
            val lenPart = lengthIn?.toString()?.toRequestBody(plain)
            val widPart = widthIn?.toString()?.toRequestBody(plain)
            val heiPart = heightIn?.toString()?.toRequestBody(plain)
            val imagePart = imageUri?.let { buildImagePart(it, "image") }

            val response = apiService.createCollectionItem(
                token, collectionId,
                namePart, descPart, pricePart, availPart, galleryPart, shipPart,
                weightPart, lenPart, widPart, heiPart, imagePart
            )
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to create item")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun updateItem(
        collectionId: Int,
        itemId: Int,
        name: String,
        description: String?,
        priceCents: Int?,
        isAvailable: Boolean,
        inGallery: Boolean,
        shippingCostCents: Int?,
        weightOz: Double?,
        lengthIn: Double?,
        widthIn: Double?,
        heightIn: Double?,
        imageUri: Uri?,
        newCollectionId: Int? = null
    ): BreakroomResult<CollectionItem> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val plain = "text/plain".toMediaTypeOrNull()
            val namePart = name.toRequestBody(plain)
            val descPart = description?.toRequestBody(plain)
            val pricePart = priceCents?.toString()?.toRequestBody(plain)
            val availPart = (if (isAvailable) "1" else "0").toRequestBody(plain)
            val galleryPart = (if (inGallery) "1" else "0").toRequestBody(plain)
            val shipPart = shippingCostCents?.toString()?.toRequestBody(plain)
            val weightPart = weightOz?.toString()?.toRequestBody(plain)
            val lenPart = lengthIn?.toString()?.toRequestBody(plain)
            val widPart = widthIn?.toString()?.toRequestBody(plain)
            val heiPart = heightIn?.toString()?.toRequestBody(plain)
            val newColPart = if (newCollectionId != null && newCollectionId != collectionId)
                newCollectionId.toString().toRequestBody(plain) else null
            val imagePart = imageUri?.let { buildImagePart(it, "image") }

            val response = apiService.updateCollectionItem(
                token, collectionId, itemId,
                namePart, descPart, pricePart, availPart, galleryPart, shipPart,
                weightPart, lenPart, widPart, heiPart, newColPart, imagePart
            )
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to update item")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteItem(collectionId: Int, itemId: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.deleteCollectionItem(token, collectionId, itemId)
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to delete item")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun exportToGallery(collectionId: Int, itemId: Int): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.exportItemToGallery(token, collectionId, itemId)
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to copy to Gallery")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Shipping ──────────────────────────────────────────────────────────────

    suspend fun getShippingSettings(): BreakroomResult<CollectionShippingSettings?> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getShippingSettings(token)
            when {
                response.isSuccessful -> BreakroomResult.Success(response.body()?.settings)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load shipping settings")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun saveShippingSettings(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        state: String?,
        zip: String?,
        country: String?,
        destinations: String?,
        processingTime: String?
    ): BreakroomResult<CollectionShippingSettings> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val request = UpdateCollectionShippingRequest(
                addressLine1, addressLine2, city, state, zip, country, destinations, processingTime
            )
            val response = apiService.saveShippingSettings(token, request)
            when {
                response.isSuccessful -> response.body()?.settings?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to save shipping settings")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    suspend fun getOrders(): BreakroomResult<List<CollectionOrder>> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getCollectionOrders(token)
            when {
                response.isSuccessful -> BreakroomResult.Success(response.body() ?: emptyList())
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load orders")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun markOrderShipped(
        orderId: Int,
        carrier: String?,
        trackingNumber: String?
    ): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.markOrderShipped(
                token, orderId, MarkOrderShippedRequest(carrier, trackingNumber)
            )
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to update order")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Storefront ────────────────────────────────────────────────────────────

    suspend fun getStorefront(): BreakroomResult<StorefrontData?> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getStorefront(token)
            when {
                response.isSuccessful -> BreakroomResult.Success(response.body())
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load storefront")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun saveStorefront(request: StorefrontSaveRequest): BreakroomResult<Unit> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.saveStorefront(token, request)
            when {
                response.isSuccessful -> BreakroomResult.Success(Unit)
                response.code() == 401 -> BreakroomResult.AuthenticationError
                response.code() == 409 -> BreakroomResult.Error("That store URL is already taken.")
                else -> BreakroomResult.Error("Failed to save storefront")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun checkStoreUrl(slug: String): BreakroomResult<UrlCheckResponse> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.checkStoreUrl(token, slug)
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to check URL")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Billing / Stripe Connect ──────────────────────────────────────────────

    suspend fun getBillingPlan(): BreakroomResult<BillingPlanResponse> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBillingPlan(token)
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load plan")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getConnectStatus(): BreakroomResult<ConnectStatusResponse> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBillingConnectStatus(token)
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to load connect status")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun startConnect(): BreakroomResult<ConnectStartResponse> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.startBillingConnect(token)
            when {
                response.isSuccessful -> response.body()?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No data returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to start connect")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun getBillingPortalUrl(): BreakroomResult<String> {
        val token = auth() ?: return BreakroomResult.Error("Not logged in")
        return try {
            val response = apiService.getBillingPortal(token)
            when {
                response.isSuccessful -> response.body()?.url?.let { BreakroomResult.Success(it) }
                    ?: BreakroomResult.Error("No portal URL returned")
                response.code() == 401 -> BreakroomResult.AuthenticationError
                else -> BreakroomResult.Error("Failed to open portal")
            }
        } catch (e: Exception) {
            BreakroomResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildImagePart(uri: Uri, fieldName: String): MultipartBody.Part? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val ext = when (mimeType) {
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val tempFile = File.createTempFile("item_", ".$ext", context.cacheDir)
        FileOutputStream(tempFile).use { out -> inputStream.copyTo(out) }
        inputStream.close()
        val body = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(fieldName, "item.$ext", body)
    }
}
