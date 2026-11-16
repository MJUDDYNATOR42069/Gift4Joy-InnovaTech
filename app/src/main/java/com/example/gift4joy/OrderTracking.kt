package com.example.gift4joy

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class OrderTracking : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order_tracking)

        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnContactSupport = findViewById<Button>(R.id.btnContactSupport)


        btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }


        btnContactSupport.setOnClickListener {
            Toast.makeText(this, "Opening support chat...", Toast.LENGTH_SHORT).show()
            // TODO: Launch a chat intent, email intent, or a CustomerSupportActivity
        }
    }
}