
package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        setupTrendingRecyclerView()
        setupBottomNavigation()
        loadUserGreeting()
    }

    private fun setupTrendingRecyclerView() {
        val rvTrending = findViewById<RecyclerView>(R.id.rvTrending)

        rvTrending.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        rvTrending.adapter = TrendingAdapter(emptyList()) { item ->
            startActivity(Intent(this, ProductActivity::class.java).apply {
                putExtra("PRODUCT_ID", item.id)
                putExtra("PRODUCT_TITLE", item.title)
                putExtra("PRODUCT_PRICE", item.price)
                putExtra("PRODUCT_CATEGORY", item.category)
                putExtra("PRODUCT_DESCRIPTION", item.description)
                putExtra("PRODUCT_IMAGE_URL", item.imageUrl)
            })
        }

        FirebaseFirestore.getInstance().collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    showLocalTrending(rvTrending)
                    return@addSnapshotListener
                }
                val products = snapshot.documents.mapNotNull { d ->
                    val available = d.getBoolean("available") ?: true
                    val stock = d.getLong("stockQuantity") ?: 1L
                    val cents = d.getLong("priceCents") ?: d.getString("price")?.let { CartManager.parsePrice(it) } ?: 0L
                    if (!available || stock <= 0 || cents <= 0) return@mapNotNull null
                    TrendingItem(d.id, d.getString("name") ?: return@mapNotNull null,
                        d.getString("category") ?: "General",
                        String.format(java.util.Locale.US, "R%.2f", cents / 100.0),
                        d.getString("description") ?: "", d.getString("imageUrl") ?: "")
                }
                val displayProducts = if (products.isNotEmpty()) products else localTrending()
                rvTrending.adapter = TrendingAdapter(displayProducts) { item ->
                    startActivity(Intent(this, ProductActivity::class.java).apply {
                        putExtra("PRODUCT_ID", item.id)
                        putExtra("PRODUCT_TITLE", item.title)
                        putExtra("PRODUCT_PRICE", item.price)
                        putExtra("PRODUCT_CATEGORY", item.category)
                        putExtra("PRODUCT_DESCRIPTION", item.description)
                        putExtra("PRODUCT_IMAGE_URL", item.imageUrl)
                    })
                }
            }
    }

    private fun localTrending(): List<TrendingItem> {
        return try {
            G4JLocalAppDatabase(this).getLocalProducts()
                .filter { it.available && it.stockQuantity > 0 && it.priceCents > 0 }
                .map { p ->
                    TrendingItem(p.productId, p.name, p.category,
                        String.format(java.util.Locale.US, "R%.2f", p.priceCents / 100.0), p.description, p.imageUrl)
                }
        } catch (_: Exception) { emptyList() }
    }

    private fun showLocalTrending(recyclerView: RecyclerView) {
        recyclerView.adapter = TrendingAdapter(localTrending()) { item ->
            startActivity(Intent(this, ProductActivity::class.java).apply {
                putExtra("PRODUCT_ID", item.id)
                putExtra("PRODUCT_TITLE", item.title)
                putExtra("PRODUCT_PRICE", item.price)
                putExtra("PRODUCT_CATEGORY", item.category)
                putExtra("PRODUCT_DESCRIPTION", item.description)
                putExtra("PRODUCT_IMAGE_URL", item.imageUrl)
            })
        }
    }

    private fun setupBottomNavigation() {
        val btnSeeAll = findViewById<TextView>(R.id.tvSeeAllTrending)

        btnSeeAll.setOnClickListener {
            startActivity(Intent(this, ShopActivity::class.java))
        }

        BottomNav.setup(this, HomeActivity::class.java)
    }

    private fun loadUserGreeting() {
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)

        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid == null) {
            tvWelcome.text = "Hi!"
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .get()
            .addOnSuccessListener { document ->

                if (!document.exists()) {
                    tvWelcome.text = "Hi!"
                    return@addOnSuccessListener
                }

                val name = document.getString("name")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }

                tvWelcome.text = if (name != null) {
                    "Hi, ${name.substringBefore(" ")}"
                } else {
                    "Hi!"
                }
            }
            .addOnFailureListener { error ->
                Log.e("HOME_PROFILE", "Could not load user name", error)
                tvWelcome.text = "Hi!"
            }
    }
}

data class TrendingItem(
    val id: String,
    val title: String,
    val category: String,
    val price: String,
    val description: String,
    val imageUrl: String = ""
)

class TrendingAdapter(
    private val items: List<TrendingItem>,
    private val onClick: (TrendingItem) -> Unit
) : RecyclerView.Adapter<TrendingAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvBadge: TextView = view.findViewById(R.id.tvBadge)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        val productImage: android.widget.ImageView = view.findViewById(R.id.ivProductImage)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trending_product, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.tvTitle.text = item.title
        holder.tvCategory.text = item.category
        holder.tvPrice.text = item.price
        ProductImageLoader.load(item.imageUrl, holder.productImage)

        holder.itemView.setOnClickListener {
            onClick(item)
        }

        holder.tvBadge.visibility = View.GONE
    }

    override fun getItemCount(): Int = items.size
}
