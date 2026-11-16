package com.example.gift4joy

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/**
 * Shared product-image storage for Firebase Spark projects.
 * Images are JPEG-compressed and stored as Firestore Blob fields in a separate
 * productImages/{imageDocumentId} document. Each image is kept below 650 KiB so the
 * complete Firestore document remains safely below Firestore's 1 MiB limit.
 */
object FirestoreProductImageStore {
    private const val MAX_IMAGE_BYTES = 650 * 1024
    private const val IMAGE_PREFIX = "firestore:"
    private val db get() = FirebaseFirestore.getInstance()
    private val worker = Executors.newFixedThreadPool(2)

    fun imageReference(imageDocumentId: String): String = "$IMAGE_PREFIX$imageDocumentId"

    fun upload(
        context: Context,
        source: Uri,
        productId: String,
        ownerUid: String,
        callback: (Result<String>) -> Unit
    ) {
        worker.execute {
            try {
                val bytes = compressImage(context, source)
                // Use a new image document ID on every replacement so customer caches
                // cannot keep showing an older photo for the same product.
                val imageDocumentId = db.collection("productImages").document().id
                cacheBytes(context, imageDocumentId, bytes)
                val imageData = hashMapOf<String, Any>(
                    "imageData" to Blob.fromBytes(bytes),
                    "contentType" to "image/jpeg",
                    "productId" to productId,
                    "uploadedBy" to ownerUid,
                    "updatedAt" to System.currentTimeMillis()
                )
                db.collection("productImages").document(imageDocumentId).set(imageData)
                    .addOnSuccessListener { callback(Result.success(imageReference(imageDocumentId))) }
                    .addOnFailureListener { callback(Result.failure(it)) }
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun load(context: Context, imageDocumentId: String, callback: (Result<File>) -> Unit) {
        val cached = cachedFile(context, imageDocumentId)
        if (cached.exists() && cached.length() > 0L) {
            callback(Result.success(cached))
            return
        }
        db.collection("productImages").document(imageDocumentId).get()
            .addOnSuccessListener { snapshot ->
                val bytes = snapshot.getBlob("imageData")?.toBytes()
                if (bytes == null || bytes.isEmpty()) {
                    callback(Result.failure(IllegalStateException("Product image is not available")))
                    return@addOnSuccessListener
                }
                try {
                    cacheBytes(context, imageDocumentId, bytes)
                    callback(Result.success(cachedFile(context, imageDocumentId)))
                } catch (error: Exception) {
                    callback(Result.failure(error))
                }
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun deleteOldImage(reference: String?) {
        if (reference.isNullOrBlank() || !reference.startsWith(IMAGE_PREFIX)) return
        val imageDocumentId = reference.removePrefix(IMAGE_PREFIX)
        if (imageDocumentId.isBlank()) return
        db.collection("productImages").document(imageDocumentId).delete()
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected image" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("The selected file is not a supported image")
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1600) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val original = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the selected image" }
            BitmapFactory.decodeStream(input, null, decodeOptions)
        } ?: throw IllegalArgumentException("The selected file is not a supported image")

        var bitmap = original
        val maxDimension = 1200
        if (original.width > maxDimension || original.height > maxDimension) {
            val scale = minOf(maxDimension.toFloat() / original.width, maxDimension.toFloat() / original.height)
            bitmap = Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt().coerceAtLeast(1),
                (original.height * scale).toInt().coerceAtLeast(1),
                true
            )
            if (bitmap !== original) original.recycle()
        }

        try {
            var quality = 82
            var result: ByteArray
            do {
                val output = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                result = output.toByteArray()
                quality -= 7
            } while (result.size > MAX_IMAGE_BYTES && quality >= 40)

            if (result.size > MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("Image is too large even after compression. Please choose a smaller photo.")
            }
            return result
        } finally {
            bitmap.recycle()
        }
    }

    private fun cacheBytes(context: Context, productId: String, bytes: ByteArray) {
        val file = cachedFile(context, productId)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    private fun cachedFile(context: Context, productId: String): File {
        val safeId = productId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(File(context.filesDir, "product_image_cache"), "$safeId.jpg")
    }
}
