package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.toColorInt

class profile : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        val btnEditProfile = findViewById<Button>(R.id.btnEditProfile)
        val menuItemsContainer = findViewById<LinearLayout>(R.id.menuItemsContainer)
        loadSignedInProfile()

        btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Edit Profile clicked", Toast.LENGTH_SHORT).show()
        }

        // FIXED: Changed ic_menu_credit_card to ic_menu_view
        val menuItems = listOf(
            MenuItem("My orders", android.R.drawable.ic_menu_agenda, false),
            MenuItem("Wishlist", android.R.drawable.ic_menu_myplaces, false),
            MenuItem("Saved addresses", android.R.drawable.ic_menu_mylocation, false),
            MenuItem("Payment methods", android.R.drawable.ic_menu_view, false),
            MenuItem("Notifications", android.R.drawable.ic_popup_reminder, false),
            MenuItem("Help & support", android.R.drawable.ic_menu_help, false),
            MenuItem("Log out", android.R.drawable.ic_lock_power_off, true)
        )

        populateMenu(menuItemsContainer, menuItems)
        BottomNav.setup(this, profile::class.java)
    }

    private fun loadSignedInProfile() {
        val nameView = findViewById<TextView>(R.id.tvProfileName)
        val emailView = findViewById<TextView>(R.id.tvProfileEmail)
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            nameView.text = "Guest"
            emailView.text = "Not signed in"
            return
        }
        emailView.text = user.email.orEmpty()
        nameView.text = user.displayName?.takeIf { it.isNotBlank() } ?: "Loading profile..."
        FirebaseFirestore.getInstance().collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val fullName = doc.getString("name")?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(doc.getString("firstName"), doc.getString("lastName"))
                        .joinToString(" ").trim().takeIf { it.isNotBlank() }
                    ?: user.displayName ?: "Gift4Joy Customer"
                nameView.text = fullName
                emailView.text = doc.getString("email") ?: user.email.orEmpty()
            }
            .addOnFailureListener {
                nameView.text = user.displayName?.takeIf { it.isNotBlank() } ?: "Gift4Joy Customer"
            }
    }

    private fun populateMenu(container: LinearLayout, items: List<MenuItem>) {
        val inflater = LayoutInflater.from(this)
        for (item in items) {
            val itemView = inflater.inflate(R.layout.item_profile_menu, container, false)
            val ivIcon = itemView.findViewById<ImageView>(R.id.ivMenuIcon)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvMenuTitle)

            tvTitle.text = item.title
            ivIcon.setImageResource(item.iconRes)

            if (item.isDestructive) {
                // FIXED: Updated to use toColorInt() instead of deprecated parseColor()
                val destructiveColor = "#C2185B".toColorInt()
                tvTitle.setTextColor(destructiveColor)
                ivIcon.setColorFilter(destructiveColor)
            }

            itemView.setOnClickListener {
                when(item.title) {
                    "Wishlist" -> startActivity(Intent(this, wishlist::class.java))
                    "My orders" -> startActivity(Intent(this, OrderTracking::class.java))
                    "Log out" -> {
                        FirebaseAuth.getInstance().signOut()
                        Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, login::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    else -> Toast.makeText(this, "Clicked: ${item.title}", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(itemView)
        }
    }
}

data class MenuItem(val title: String, val iconRes: Int, val isDestructive: Boolean = false)