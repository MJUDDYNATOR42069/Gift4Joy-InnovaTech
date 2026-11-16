
package com.example.gift4joy

import android.content.Intent
import android.net.Uri
import java.io.File
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class ShopActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var productGrid: GridLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)

        productGrid = findViewById(R.id.productGrid)

        setupBottomNavigation()
        loadProducts()
    }

    private fun setupBottomNavigation() {
        BottomNav.setup(this, ShopActivity::class.java)
    }

    private fun loadProducts() {
        db.collection("products")
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    populateGrid(localProducts())
                    Toast.makeText(this, "Showing saved products while the shop reconnects.", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val products = snapshot.documents.mapNotNull { document ->

                    val available = document.getBoolean("available") ?: true
                    val stock = document.getLong("stockQuantity") ?: 1L
                    val priceCents = document.getLong("priceCents")
                        ?: document.getString("price")?.let { CartManager.parsePrice(it) }
                        ?: 0L

                    if (!available || stock <= 0 || priceCents <= 0) return@mapNotNull null

                    Product(
                        id = document.id,
                        badge = "",
                        title = document.getString("name") ?: "Unnamed Product",
                        category = document.getString("category") ?: "General",
                        price = String.format(
                            Locale.US,
                            "%.2f",
                            priceCents / 100.0
                        ),
                        imageColorHex = "#F8E8ED",
                        badgeColorHex = "#D3154A",
                        description = document.getString("description") ?: "",
                        imageUrl = document.getString("imageUrl") ?: "",
                        stockQuantity = stock
                    )
                }

                if (products.isNotEmpty()) {
                    val localDb = G4JLocalAppDatabase(this)
                    products.forEach { p ->
                        try {
                            val cached = localDb.getLocalProducts().firstOrNull { it.productId == p.id }
                            val cachedPath = cached?.imageUrl?.takeIf { it.startsWith("file:") }?.let { Uri.parse(it).path }
                            val ownerLocalImage = if (cached != null && cachedPath != null && File(cachedPath).exists()) cached.imageUrl else p.imageUrl
                            localDb.saveProductLocally(LocalProduct(p.id, p.title, p.description, p.category,
                                CartManager.parsePrice(p.price), p.stockQuantity.toInt(), true, ownerLocalImage))
                        } catch (_: Exception) {}
                    }
                    populateGrid(products)
                } else {
                    populateGrid(localProducts())
                }
            }
    }

    private fun localProducts(): List<Product> {
        return try {
            G4JLocalAppDatabase(this).getLocalProducts()
                .filter { it.available && it.stockQuantity > 0 && it.priceCents > 0 }
                .map { p ->
                    Product(p.productId, "", p.name, p.category,
                        String.format(Locale.US, "%.2f", p.priceCents / 100.0),
                        "#F8E8ED", "#D3154A", p.description, p.imageUrl, p.stockQuantity.toLong())
                }
        } catch (_: Exception) { emptyList() }
    }

    private fun populateGrid(products: List<Product>) {
        productGrid.removeAllViews()

        if (products.isEmpty()) {
            val emptyMessage = TextView(this).apply {
                text = "No products are currently available."
                textSize = 16f
                setPadding(24, 32, 24, 32)
                setTextColor(Color.DKGRAY)
            }

            productGrid.addView(emptyMessage)
            return
        }

        val inflater = LayoutInflater.from(this)

        for (product in products) {
            val itemView = inflater.inflate(
                R.layout.item_product_grid,
                productGrid,
                false
            )

            val tvBadge = itemView.findViewById<TextView>(R.id.tvBadge)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvTitle)
            val tvCategory = itemView.findViewById<TextView>(R.id.tvCategory)
            val tvPrice = itemView.findViewById<TextView>(R.id.tvPrice)
            val imageContainer = itemView.findViewById<View>(R.id.imageContainer)
            val productImage = itemView.findViewById<android.widget.ImageView>(R.id.ivProductImage)
            ProductImageLoader.load(product.imageUrl, productImage)
            val addToCart = itemView.findViewById<View>(R.id.btnAddToCart)

            tvBadge.visibility = View.GONE

            tvTitle.text = product.title
            tvCategory.text = product.category
            tvPrice.text = "R${product.price}"

            try {
                imageContainer.setBackgroundColor(
                    Color.parseColor(product.imageColorHex)
                )
            } catch (_: Exception) {
                imageContainer.setBackgroundColor(Color.LTGRAY)
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(8, 8, 8, 8)
            }

            itemView.layoutParams = params

            itemView.setOnClickListener {
                val intent = Intent(this, ProductActivity::class.java).apply {
                    putExtra("PRODUCT_ID", product.id)
                    putExtra("PRODUCT_TITLE", product.title)
                    putExtra("PRODUCT_PRICE", product.price)
                    putExtra("PRODUCT_CATEGORY", product.category)
                    putExtra("PRODUCT_DESCRIPTION", product.description)
                    putExtra("PRODUCT_IMAGE_URL", product.imageUrl)
                }

                startActivity(intent)
            }

            addToCart.setOnClickListener {
                CartManager.add(
                    this,
                    product.title,
                    product.category,
                    product.price,
                    product.id
                )

                Toast.makeText(
                    this,
                    "${product.title} added to cart",
                    Toast.LENGTH_SHORT
                ).show()
            }

            productGrid.addView(itemView)
        }
    }
}

data class Product(
    val id: String,
    val badge: String,
    val title: String,
    val category: String,
    val price: String,
    val imageColorHex: String,
    val badgeColorHex: String,
    val description: String,
    val imageUrl: String,
    val stockQuantity: Long
)
