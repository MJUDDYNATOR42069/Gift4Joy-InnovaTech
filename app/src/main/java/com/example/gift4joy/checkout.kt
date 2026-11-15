package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class checkout : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnPlaceOrder = findViewById<Button>(R.id.btnPlaceOrder)

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnPlaceOrder.setOnClickListener {
            CartManager.clear(this)
            Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_LONG).show()

            // NAVIGATE TO ORDER TRACKING
            val intent = Intent(this, OrderTracking::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }
}