
package com.example.gift4joy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

data class CartItem(
    val title: String,
    val category: String,
    val unitPrice: Long,
    var quantity: Int = 1
)

object CartManager {

    private const val PREFS = "gift4joy_cart"
    private const val KEY = "items"

    private val items = mutableListOf<CartItem>()
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return

        loaded = true

        val json = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return

        try {
            val array = JSONArray(json)

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                items.add(
                    CartItem(
                        title = item.getString("title"),
                        category = item.getString("category"),
                        unitPrice = item.getLong("price"),
                        quantity = item.getInt("qty")
                    )
                )
            }
        } catch (_: Exception) {
            items.clear()
        }
    }

    private fun save(context: Context) {
        val array = JSONArray()

        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("title", item.title)
                    .put("category", item.category)
                    .put("price", item.unitPrice)
                    .put("qty", item.quantity)
            )
        }

        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, array.toString())
            .apply()
    }

    fun parsePrice(price: String): Long {
        return try {
            BigDecimal(
                price.replace("R", "", ignoreCase = true)
                    .replace(",", "")
                    .trim()
            )
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        } catch (_: Exception) {
            0L
        }
    }

    fun formatPrice(priceCents: Long): String {
        return String.format(
            Locale.US,
            "R%.2f",
            priceCents / 100.0
        )
    }

    fun add(
        context: Context,
        title: String,
        category: String,
        price: String
    ) {
        ensureLoaded(context)

        val priceCents = parsePrice(price)

        if (priceCents <= 0L) return

        val existing = items.find {
            it.title == title
        }

        if (existing != null) {
            existing.quantity++
        } else {
            items.add(
                CartItem(
                    title = title,
                    category = category,
                    unitPrice = priceCents
                )
            )
        }

        save(context)
    }

    fun increase(context: Context, title: String) {
        ensureLoaded(context)

        items.find { it.title == title }?.let {
            it.quantity++
        }

        save(context)
    }

    fun decrease(context: Context, title: String) {
        ensureLoaded(context)

        val item = items.find {
            it.title == title
        } ?: return

        item.quantity--

        if (item.quantity <= 0) {
            items.remove(item)
        }

        save(context)
    }

    fun remove(context: Context, title: String) {
        ensureLoaded(context)

        items.removeAll {
            it.title == title
        }

        save(context)
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        items.clear()
        save(context)
    }

    fun getItems(context: Context): List<CartItem> {
        ensureLoaded(context)
        return items.toList()
    }

    fun totalCount(context: Context): Int {
        ensureLoaded(context)

        return items.sumOf {
            it.quantity
        }
    }

    fun subtotal(context: Context): Long {
        ensureLoaded(context)

        return items.sumOf {
            it.unitPrice * it.quantity
        }
    }
}
