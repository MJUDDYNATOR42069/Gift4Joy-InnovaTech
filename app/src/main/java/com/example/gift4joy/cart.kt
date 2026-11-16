
package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CartActivity : AppCompatActivity() {

    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var cartScroll: ScrollView
    private lateinit var emptyStateLayout: View

    private lateinit var btnCheckout: Button
    private lateinit var btnBrowseGifts: Button

    private lateinit var tvItemCount: TextView
    private lateinit var tvSubtotal: TextView
    private lateinit var tvDelivery: TextView
    private lateinit var tvTotal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        initializeViews()
        setupListeners()
        BottomNav.setup(this, CartActivity::class.java)

        renderCart()
    }

    private fun initializeViews() {
        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        cartScroll = findViewById(R.id.cartScroll)
        emptyStateLayout = findViewById(R.id.emptyStateLayout)

        btnCheckout = findViewById(R.id.btnCheckout)
        btnBrowseGifts = findViewById(R.id.btnBrowseGifts)

        tvItemCount = findViewById(R.id.tvItemCount)
        tvSubtotal = findViewById(R.id.tvSubtotal)
        tvDelivery = findViewById(R.id.tvDelivery)
        tvTotal = findViewById(R.id.tvTotal)
    }

    private fun setupListeners() {
        btnBrowseGifts.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }

        btnCheckout.setOnClickListener {
            val items = CartManager.getItems(this)

            if (items.isEmpty()) {
                btnCheckout.isEnabled = false
                return@setOnClickListener
            }

            startActivity(Intent(this, checkout::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderCart()
    }

    private fun renderCart() {
        cartItemsContainer.removeAllViews()

        val items = CartManager.getItems(this)
        val count = CartManager.totalCount(this)
        val subtotal = CartManager.subtotal(this)

        tvItemCount.text = "$count items"
        tvSubtotal.text = CartManager.formatPrice(subtotal)
        tvDelivery.text = "At checkout"
        tvTotal.text = CartManager.formatPrice(subtotal)

        val isEmpty = items.isEmpty()

        cartScroll.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE

        btnCheckout.isEnabled = !isEmpty

        btnCheckout.setBackgroundResource(
            if (isEmpty) R.drawable.bg_btn_disabled
            else R.drawable.bg_btn_primary
        )

        if (isEmpty) return

        items.forEach { item ->
            addCartItemView(item)
        }
    }

    private fun addCartItemView(item: CartItem) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_cart, cartItemsContainer, false)

        val tvCartTitle = row.findViewById<TextView>(R.id.tvCartTitle)
        val tvCartPrice = row.findViewById<TextView>(R.id.tvCartPrice)
        val tvQty = row.findViewById<TextView>(R.id.tvQty)

        val btnPlus = row.findViewById<Button>(R.id.btnPlus)
        val btnMinus = row.findViewById<Button>(R.id.btnMinus)
        val btnRemoveItem = row.findViewById<Button>(R.id.btnRemoveItem)

        tvCartTitle.text = item.title

        val itemTotal = item.unitPrice * item.quantity
        tvCartPrice.text =
            "${item.category} · ${CartManager.formatPrice(itemTotal)}"

        tvQty.text = item.quantity.toString()

        btnPlus.setOnClickListener {
            CartManager.increase(this, item.title)
            renderCart()
        }

        btnMinus.setOnClickListener {
            CartManager.decrease(this, item.title)
            renderCart()
        }

        btnRemoveItem.setOnClickListener {
            CartManager.remove(this, item.title)
            renderCart()
        }

        cartItemsContainer.addView(row)
    }
}
