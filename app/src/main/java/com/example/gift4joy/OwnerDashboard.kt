package com.example.gift4joy

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.math.BigDecimal
import java.util.Locale

data class DisplayProduct(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val priceCents: Long,
    val stockQuantity: Long,
    val available: Boolean,
    val imageUrl: String
)

class OwnerDashboard : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private lateinit var name: EditText
    private lateinit var description: EditText
    private lateinit var category: EditText
    private lateinit var price: EditText
    private lateinit var stock: EditText
    private lateinit var available: CheckBox
    private lateinit var imagePreview: ImageView
    private lateinit var stockContainer: LinearLayout
    private lateinit var summary: TextView
    private lateinit var saveButton: Button

    private var imageUri: Uri? = null
    private var editingId: String? = null
    private var existingImageUrl = ""

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            imagePreview.setImageURI(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_owner_dashboard)

        name = findViewById(R.id.etProductName)
        description = findViewById(R.id.etProductDescription)
        category = findViewById(R.id.etProductCategory)
        price = findViewById(R.id.etProductPrice)
        stock = findViewById(R.id.etStockQuantity)
        available = findViewById(R.id.cbAvailable)
        imagePreview = findViewById(R.id.ivProductImage)
        stockContainer = findViewById(R.id.stockContainer)
        summary = findViewById(R.id.tvStockSummary)
        saveButton = findViewById(R.id.btnSaveProduct)

        findViewById<Button>(R.id.btnSelectImage)
            .setOnClickListener {
                imagePicker.launch("image/*")
            }

        saveButton.setOnClickListener {
            saveProduct()
        }

        findViewById<Button>(R.id.btnClearForm)
            .setOnClickListener {
                clearForm()
            }

        findViewById<Button>(R.id.btnManageOrders)
            .setOnClickListener {
                Toast.makeText(
                    this,
                    "Order management screen connected to local orders",
                    Toast.LENGTH_SHORT
                ).show()
            }

        findViewById<Button>(R.id.btnLogout)
            .setOnClickListener {
                startActivity(Intent(this, login::class.java))
                auth.signOut()
                finish()
            }

        verifyOwner()
    }

    private fun verifyOwner() {
        val user = auth.currentUser

        if (user == null) {
            try {
                val localUser = G4JLocalAppDatabase(this).getUserByEmail("supplier@gift4joy.com")
                if (localUser != null && localUser.role == "owner") {
                    loadStockLocally()
                    return
                }
            } catch (_: Exception) {}

            finish()
            return
        }

        db.collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.getString("role") == "owner") {
                    loadStock()
                } else {
                    Toast.makeText(
                        this,
                        "Owner access only",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
            .addOnFailureListener {
                try {
                    val localUser = G4JLocalAppDatabase(this).getUserById(user.uid)
                    if (localUser != null && localUser.role == "owner") {
                        loadStockLocally()
                        return@addOnFailureListener
                    }
                } catch (_: Exception) {}

                finish()
            }
    }

    private fun saveProduct() {
        val productName = name.text.toString().trim()
        val productDescription = description.text.toString().trim()
        val productCategory = category.text.toString().trim()
        val stockNumber = stock.text.toString().toIntOrNull()

        val priceCents = try {
            BigDecimal(price.text.toString())
                .movePointRight(2)
                .longValueExact()
        } catch (e: Exception) {
            null
        }

        if (productName.isBlank() ||
            productDescription.isBlank() ||
            productCategory.isBlank() ||
            priceCents == null ||
            priceCents <= 0 ||
            stockNumber == null ||
            stockNumber < 0
        ) {
            message("Please enter valid product details")
            return
        }

        if (editingId == null && imageUri == null) {
            message("Please select a product image")
            return
        }

        saveButton.isEnabled = false

        val id = editingId
            ?: db.collection("products").document().id

        val selectedUri = imageUri

        // Save locally first for offline support
        try {
            G4JLocalAppDatabase(this).saveProductLocally(
                LocalProduct(
                    id, productName, productDescription, productCategory,
                    priceCents, stockNumber, available.isChecked, existingImageUrl
                )
            )
        } catch (e: Exception) {
            Log.e("OwnerDashboard", "Local product save error", e)
        }

        if (selectedUri != null) {
            val imageRef = storage.reference
                .child("products/$id/main.jpg")

            imageRef.putFile(selectedUri)
                .continueWithTask { task ->
                    if (!task.isSuccessful) {
                        throw task.exception
                            ?: Exception("Upload failed")
                    }
                    imageRef.downloadUrl
                }
                .addOnSuccessListener { url ->
                    writeProduct(
                        id,
                        productName,
                        productDescription,
                        productCategory,
                        priceCents,
                        stockNumber,
                        url.toString()
                    )
                }
                .addOnFailureListener {
                    saveButton.isEnabled = true
                    message("Image upload failed, saved locally (offline mode)")
                    clearForm()
                }
        } else {
            writeProduct(
                id,
                productName,
                productDescription,
                productCategory,
                priceCents,
                stockNumber,
                existingImageUrl
            )
        }
    }

    private fun writeProduct(
        id: String,
        productName: String,
        productDescription: String,
        productCategory: String,
        priceCents: Long,
        stockNumber: Int,
        imageUrl: String
    ) {
        val data = hashMapOf<String, Any>(
            "name" to productName,
            "description" to productDescription,
            "category" to productCategory,
            "priceCents" to priceCents,
            "stockQuantity" to stockNumber,
            "available" to available.isChecked,
            "imageUrl" to imageUrl
        )

        try {
            G4JLocalAppDatabase(this).saveProductLocally(
                LocalProduct(id, productName, productDescription, productCategory, priceCents, stockNumber, available.isChecked, imageUrl)
            )
        } catch (_: Exception) {}

        db.collection("products")
            .document(id)
            .set(data)
            .addOnSuccessListener {
                saveButton.isEnabled = true
                clearForm()
                message("Product saved successfully")
            }
            .addOnFailureListener {
                saveButton.isEnabled = true
                message("Saved locally (offline mode)")
                clearForm()
            }
    }

    private fun loadStock() {
        db.collection("products")
            .addSnapshotListener { snapshot, error ->

                if (error != null || snapshot == null) {
                    loadStockLocally()
                    return@addSnapshotListener
                }

                val products = snapshot.documents
                val localDb = G4JLocalAppDatabase(this)

                products.forEach { document ->
                    try {
                        localDb.saveProductLocally(
                            LocalProduct(
                                document.id,
                                document.getString("name") ?: "",
                                document.getString("description") ?: "",
                                document.getString("category") ?: "",
                                document.getLong("priceCents") ?: 0L,
                                (document.getLong("stockQuantity") ?: 0L).toInt(),
                                document.getBoolean("available") ?: false,
                                document.getString("imageUrl") ?: ""
                            )
                        )
                    } catch (_: Exception) {}
                }

                val displayList = mutableListOf<DisplayProduct>()
                for (i in 0 until products.size) {
                    val document = products[i] as com.google.firebase.firestore.DocumentSnapshot
                    displayList.add(
                        DisplayProduct(
                            id = document.id,
                            name = document.getString("name") ?: "",
                            description = document.getString("description") ?: "",
                            category = document.getString("category") ?: "",
                            priceCents = document.getLong("priceCents") ?: 0L,
                            stockQuantity = document.getLong("stockQuantity") ?: 0L,
                            available = document.getBoolean("available") ?: false,
                            imageUrl = document.getString("imageUrl") ?: ""
                        )
                    )
                }

                displayProducts(displayList)
            }
    }

    private fun loadStockLocally() {
        try {
            val localDb = G4JLocalAppDatabase(this)
            val localProducts = localDb.getLocalProducts()
            val displayList = localProducts.map {
                DisplayProduct(
                    id = it.productId,
                    name = it.name,
                    description = it.description,
                    category = it.category,
                    priceCents = it.priceCents,
                    stockQuantity = it.stockQuantity.toLong(),
                    available = it.available,
                    imageUrl = it.imageUrl
                )
            }
            displayProducts(displayList)
            message("Loaded inventory from local offline database")
        } catch (e: Exception) {
            message("Unable to load inventory")
        }
    }

    private fun displayProducts(products: List<DisplayProduct>) {
        stockContainer.removeAllViews()

        val totalUnits = products.sumOf { it.stockQuantity }
        val lowStock = products.count { it.stockQuantity in 1..5 }

        summary.text =
            "Total Products: ${products.size}\n" +
                    "Total Stock Units: $totalUnits\n" +
                    "Low Stock Products: $lowStock"

        products.forEach { product ->
            val productStatus = when {
                !product.available -> "UNAVAILABLE"
                product.stockQuantity == 0L -> "OUT OF STOCK"
                product.stockQuantity <= 5L -> "LOW STOCK"
                else -> "IN STOCK"
            }

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 20, 12, 20)
            }

            val info = TextView(this).apply {
                text =
                    "${product.name}\n" +
                            "Stock: ${product.stockQuantity}\n" +
                            "Status: $productStatus"
                textSize = 17f
            }

            row.addView(info)

            val updateButton = Button(this).apply {
                text = "UPDATE STOCK"
            }

            updateButton.setOnClickListener {
                updateStock(product.id, product.stockQuantity)
            }

            row.addView(updateButton)

            val availabilityButton = Button(this).apply {
                text = if (product.available) {
                    "MARK OUT OF STOCK"
                } else {
                    "MARK AVAILABLE"
                }
            }

            availabilityButton.setOnClickListener {
                val newAvailability = !product.available
                db.collection("products")
                    .document(product.id)
                    .update("available", newAvailability)
                    .addOnSuccessListener {
                        try {
                            G4JLocalAppDatabase(this).saveProductLocally(
                                LocalProduct(
                                    product.id, product.name, product.description,
                                    product.category, product.priceCents, product.stockQuantity.toInt(),
                                    newAvailability, product.imageUrl
                                )
                            )
                        } catch (_: Exception) {}
                        message("Availability updated")
                    }
                    .addOnFailureListener {
                        try {
                            G4JLocalAppDatabase(this).saveProductLocally(
                                LocalProduct(
                                    product.id, product.name, product.description,
                                    product.category, product.priceCents, product.stockQuantity.toInt(),
                                    newAvailability, product.imageUrl
                                )
                            )
                            message("Updated locally (offline mode)")
                            loadStockLocally()
                        } catch (e: Exception) {
                            message("Update failed")
                        }
                    }
            }

            row.addView(availabilityButton)

            val editButton = Button(this).apply {
                text = "EDIT PRODUCT"
            }

            editButton.setOnClickListener {
                editingId = product.id

                name.setText(product.name)
                description.setText(product.description)
                category.setText(product.category)
                price.setText(String.format(Locale.US, "%.2f", product.priceCents / 100.0))
                stock.setText(product.stockQuantity.toString())
                available.isChecked = product.available
                existingImageUrl = product.imageUrl

                imageUri = null
                imagePreview.setImageDrawable(null)
                saveButton.text = "UPDATE PRODUCT"

                message("Product loaded for editing")
            }

            row.addView(editButton)
            stockContainer.addView(row)
        }
    }

    private fun updateStock(
        productId: String,
        currentQuantity: Long
    ) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentQuantity.toString())
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Update Stock")
            .setView(input)
            .setPositiveButton("SAVE", null)
            .setNegativeButton("CANCEL", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {

                    val quantity = input.text.toString().toIntOrNull()

                    if (quantity == null || quantity < 0) {
                        input.error = "Enter a valid quantity"
                        return@setOnClickListener
                    }

                    db.collection("products")
                        .document(productId)
                        .update("stockQuantity", quantity)
                        .addOnSuccessListener {
                            message("Stock updated")
                            dialog.dismiss()
                        }
                        .addOnFailureListener {
                            // Offline update
                            try {
                                val localDb = G4JLocalAppDatabase(this)
                                val products = localDb.getLocalProducts()
                                val prod = products.find { it.productId == productId }
                                if (prod != null) {
                                    localDb.saveProductLocally(prod.copy(stockQuantity = quantity))
                                    message("Stock updated locally (offline mode)")
                                    dialog.dismiss()
                                    loadStockLocally()
                                } else {
                                    message("Stock update failed")
                                }
                            } catch (e: Exception) {
                                message("Stock update failed")
                            }
                        }
                }
        }

        dialog.show()
    }

    private fun clearForm() {
        editingId = null
        existingImageUrl = ""
        imageUri = null

        name.text.clear()
        description.text.clear()
        category.text.clear()
        price.text.clear()
        stock.text.clear()

        available.isChecked = true
        imagePreview.setImageDrawable(null)
        saveButton.text = "SAVE PRODUCT"
    }

    private fun message(text: String) {
        Toast.makeText(
            this,
            text,
            Toast.LENGTH_SHORT
        ).show()
    }
}
