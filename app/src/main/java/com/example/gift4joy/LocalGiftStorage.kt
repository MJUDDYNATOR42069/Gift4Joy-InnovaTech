package com.example.gift4joy

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.util.UUID

/** Copies selected gallery images into app-private storage so their URI survives app restarts. */
object LocalProductImageStore {
    fun copyIntoAppStorage(context: Context, source: Uri, productId: String): String {
        val directory = File(context.filesDir, "product_images").apply { mkdirs() }
        val extension = when (context.contentResolver.getType(source)) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val destination = File(directory, "${productId}_${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Unable to read selected image" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(destination).toString()
    }
}

data class SavedGift(
    val id: String,
    val title: String,
    val category: String,
    val price: String,
    val description: String = ""
)

object WishlistManager {
    private const val KEY = "saved_gifts"
    private fun preferences(context: Context) = context.getSharedPreferences(
        "gift4joy_wishlist_${FirebaseAuth.getInstance().currentUser?.uid ?: "guest"}",
        Context.MODE_PRIVATE
    )

    fun get(context: Context): List<SavedGift> {
        val raw = preferences(context).getString(KEY, null) ?: return emptyList()
        return try {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedGift(o.optString("id"), o.optString("title"), o.optString("category"), o.optString("price"), o.optString("description"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun contains(context: Context, id: String): Boolean = get(context).any { it.id == id }

    fun toggle(context: Context, gift: SavedGift): Boolean {
        val items = get(context).toMutableList()
        val removed = items.removeAll { it.id == gift.id }
        if (!removed) items.add(gift)
        val array = org.json.JSONArray()
        items.forEach { g ->
            array.put(org.json.JSONObject().put("id", g.id).put("title", g.title).put("category", g.category)
                .put("price", g.price).put("description", g.description))
        }
        preferences(context).edit().putString(KEY, array.toString()).apply()
        return !removed
    }

    fun remove(context: Context, id: String) {
        val items = get(context).filterNot { it.id == id }
        val array = org.json.JSONArray()
        items.forEach { g -> array.put(org.json.JSONObject().put("id", g.id).put("title", g.title)
            .put("category", g.category).put("price", g.price).put("description", g.description)) }
        preferences(context).edit().putString(KEY, array.toString()).apply()
    }
}
