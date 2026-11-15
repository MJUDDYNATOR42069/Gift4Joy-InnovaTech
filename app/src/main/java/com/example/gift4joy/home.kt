
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

        val trendingItems = listOf(
            TrendingItem(
                "BESTSELLER",
                "Personalised Photo Mug",
                "Corporate",
                "R189",
                "#C2185B"
            ),
            TrendingItem(
                "NEW IN",
                "Rose Gold Birthstone Necklace",
                "Anniversary",
                "R459",
                "#4A1525"
            ),
            TrendingItem(
                "",
                "Luxury Spa Hamper",
                "Self-Care",
                "R850",
                "#E91E63"
            )
        )

        rvTrending.adapter = TrendingAdapter(trendingItems) { item ->
            startActivity(
                Intent(this, ProductActivity::class.java).apply {
                    putExtra("PRODUCT_TITLE", item.title)
                    putExtra("PRODUCT_PRICE", item.price)
                    putExtra("PRODUCT_CATEGORY", item.category)
                }
            )
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
    val badge: String,
    val title: String,
    val category: String,
    val price: String,
    val bgColor: String
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

        holder.itemView.setOnClickListener {
            onClick(item)
        }

        if (item.badge.isNotEmpty()) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = item.badge
        } else {
            holder.tvBadge.visibility = View.GONE
        }
    }

    override fun getItemCount(): Int = items.size
}
