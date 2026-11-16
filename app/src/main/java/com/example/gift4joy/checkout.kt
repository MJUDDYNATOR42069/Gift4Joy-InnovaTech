package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

class checkout : AppCompatActivity() {

    private val paymentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            CartManager.clear(this)
            Toast.makeText(this, "Order confirmed successfully!", Toast.LENGTH_LONG).show()
            val tracking = Intent(this, OrderTracking::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(tracking)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnPlaceOrder = findViewById<Button>(R.id.btnPlaceOrder)

        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        btnPlaceOrder.setOnClickListener {
            if (CartManager.getItems(this).isEmpty()) {
                Toast.makeText(this, "Your cart is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            paymentLauncher.launch(Intent(this, Payement::class.java))
        }
    }
}