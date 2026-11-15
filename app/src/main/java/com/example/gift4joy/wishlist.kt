package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class wishlist : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wishlist)

        val savedItemsContainer = findViewById<LinearLayout>(R.id.savedItemsContainer)
        val recommendedContainer = findViewById<LinearLayout>(R.id.recommendedContainer)

        val savedItems = listOf(
            SavedItem("Rose Gold Birthstone Necklace", "Anniversary · R459", true),
            SavedItem("Spa Day Pamper Box", "Self-Care · R549", false)
        )

        populateSavedItems(savedItemsContainer, savedItems)
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        BottomNav.setup(this, wishlist::class.java)
    }

    private fun populateSavedItems(container: LinearLayout, items: List<SavedItem>) {
        val inflater = LayoutInflater.from(this)
        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_wishlist_saved, container, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
            val tvDetails = itemView.findViewById<TextView>(R.id.tvDetails)
            val btnMoveToCart = itemView.findViewById<Button>(R.id.btnMoveToCart)
            val btnRemove = itemView.findViewById<ImageView>(R.id.btnRemove)

            tvTitle.text = item.title
            tvDetails.text = item.details

            btnMoveToCart.setOnClickListener {
                val parts = item.details.split("·")
                val category = parts.getOrElse(0) { "" }.trim()
                val price = parts.getOrElse(1) { "" }.trim()
                CartManager.add(this, item.title, category, price)
                container.removeView(itemView)
                Toast.makeText(this, "Moved ${item.title} to cart", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, CartActivity::class.java))
            }

            btnRemove.setOnClickListener {
                container.removeView(itemView)
            }
            container.addView(itemView)
        }
    }
}

data class SavedItem(val title: String, val details: String, val isDark: Boolean)