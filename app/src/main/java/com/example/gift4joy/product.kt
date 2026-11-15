package com.example.gift4joy // Replace with your package name

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ProductActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product)

        // 1. Get Views
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val tvCategory = findViewById<TextView>(R.id.tvCategory)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvPrice = findViewById<TextView>(R.id.tvPrice)
        val tvDescription = findViewById<TextView>(R.id.tvDescription)
        val tvReviews = findViewById<TextView>(R.id.tvReviews)
        val btnAddToCart = findViewById<Button>(R.id.btnAddToCart)
        val btnWishlist = findViewById<Button>(R.id.btnWishlist)
        val tvReserve = findViewById<TextView>(R.id.tvReserve)

        // 2. Extract Data from Intent (if passed)
        val intent = intent
        val productTitle = intent.getStringExtra("PRODUCT_TITLE") ?: "Personalised Photo Mug"
        val productPrice = intent.getStringExtra("PRODUCT_PRICE") ?: "R189"
        val productCategory = intent.getStringExtra("PRODUCT_CATEGORY") ?: "CORPORATE"
        val productDesc = intent.getStringExtra("PRODUCT_DESC") ?: "A ceramic 350ml mug printed with a photo of your choosing. Dishwasher- and microwave-safe, arrives boxed and ready to gift."

        // 3. Bind Data to UI
        tvTitle.text = productTitle
        tvCategory.text = productCategory.uppercase()
        tvPrice.text = productPrice
        tvDescription.text = productDesc

        // Dynamically update the Add to Cart button text
        btnAddToCart.text = "Add to cart — $productPrice"

        // 4. Setup Click Listeners
        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnAddToCart.setOnClickListener {
            CartManager.add(this, productTitle, productCategory, productPrice)
            Toast.makeText(this, "$productTitle added to cart!", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, CartActivity::class.java))
        }

        btnWishlist.setOnClickListener {
            Toast.makeText(this, "Added to wishlist", Toast.LENGTH_SHORT).show()
            // TODO: Change icon to filled heart and save to DB
        }

        tvReviews.setOnClickListener {
            // Optional: Scroll to a reviews section or open a bottom sheet
            Toast.makeText(this, "Viewing 132 reviews", Toast.LENGTH_SHORT).show()
        }

        tvReserve.setOnClickListener {
            Toast.makeText(this, "Reservation flow initiated", Toast.LENGTH_SHORT).show()
        }
    }
}