package com.example.gift4joy

import android.app.Activity
import android.content.Intent
import android.view.View

/** Shared bottom-navigation wiring so every screen navigates the same way. */
object BottomNav {

    fun setup(activity: Activity, current: Class<out Activity>) {
        bind(activity, R.id.navHome, HomeActivity::class.java, current)
        bind(activity, R.id.navShop, ShopActivity::class.java, current)
        bind(activity, R.id.navWishlist, wishlist::class.java, current)
        bind(activity, R.id.navCart, CartActivity::class.java, current)
        bind(activity, R.id.navProfile, profile::class.java, current)
    }

    private fun bind(
        activity: Activity,
        viewId: Int,
        target: Class<out Activity>,
        current: Class<out Activity>
    ) {
        val view = activity.findViewById<View>(viewId) ?: return
        view.setOnClickListener {
            if (target == current) return@setOnClickListener // already here
            val intent = Intent(activity, target).apply {
                // Reuse an existing instance instead of stacking duplicates
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            activity.startActivity(intent)
            activity.overridePendingTransition(0, 0)
        }
    }
}
