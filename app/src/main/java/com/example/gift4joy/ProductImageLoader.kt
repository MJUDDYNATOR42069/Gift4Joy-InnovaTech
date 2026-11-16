package com.example.gift4joy

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Loads Firestore-shared images, legacy HTTPS images, and local cached images. */
object ProductImageLoader {
    private val executor = Executors.newFixedThreadPool(3)

    fun load(source: String?, target: ImageView) {
        if (source.isNullOrBlank()) return
        target.tag = source

        if (source.startsWith("firestore:")) {
            val productId = source.removePrefix("firestore:")
            FirestoreProductImageStore.load(target.context.applicationContext, productId) { result ->
                result.onSuccess { file ->
                    executor.execute {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) target.post {
                            if (target.tag == source) target.setImageBitmap(bitmap)
                        }
                    }
                }
            }
            return
        }

        executor.execute {
            try {
                val bitmap = if (source.startsWith("file:")) {
                    target.context.contentResolver.openInputStream(Uri.parse(source)).use {
                        BitmapFactory.decodeStream(it)
                    }
                } else {
                    val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 15000
                        instanceFollowRedirects = true
                    }
                    try { connection.inputStream.use { BitmapFactory.decodeStream(it) } }
                    finally { connection.disconnect() }
                }
                if (bitmap != null) target.post {
                    if (target.tag == source) target.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {
                // Keep the layout's neutral image fallback.
            }
        }
    }
}
