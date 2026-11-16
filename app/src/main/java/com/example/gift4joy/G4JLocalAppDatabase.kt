package com.example.gift4joy

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class LocalUser(
    val userId: String = "",
    val email: String = "",
    val password: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "customer",
    val accountStatus: Boolean = true
)

data class LocalOrder(
    val orderId: String,
    val userId: String,
    val amount: Double,
    val category: String,
    val status: String
)

data class LocalSupplierMetrics(
    val supplierId: String,
    val revenue: Double,
    val unitsSold: Int,
    val topProductName: String,
    val topProductStats: String,
    val topProductRevenue: Double
)

data class AdminRecord(
    val documentId: String = "",
    val title: String = "",
    val subtitle: String = "",
    val status: String = "",
    val actionOne: String = "",
    val actionTwo: String = "",
    val actionThree: String = ""
)

data class LocalProduct(
    val productId: String,
    val name: String,
    val description: String,
    val category: String,
    val priceCents: Long,
    val stockQuantity: Int,
    val available: Boolean,
    val imageUrl: String
)

class G4JLocalAppDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "gift4joy_local.db"
        private const val DATABASE_VERSION = 3

        // Local Users table
        private const val TABLE_USERS = "local_users"
        private const val COL_USER_ID = "user_id"
        private const val COL_EMAIL = "email"
        private const val COL_PASSWORD = "password"
        private const val COL_FIRST_NAME = "first_name"
        private const val COL_LAST_NAME = "last_name"
        private const val COL_ROLE = "role"
        private const val COL_ACCOUNT_STATUS = "account_status"

        // User Dashboard Orders table
        private const val TABLE_ORDERS = "user_orders"
        private const val COL_ORDER_ID = "id"
        private const val COL_USER_ID_ORDER = "user_id"
        private const val COL_AMOUNT = "amount"
        private const val COL_CATEGORY = "category"
        private const val COL_STATUS = "status"

        // Supplier Dashboard Metrics table
        private const val TABLE_SUPPLIER_METRICS = "supplier_metrics"
        private const val COL_SUPPLIER_ID = "supplier_id"
        private const val COL_REVENUE = "revenue"
        private const val COL_UNITS_SOLD = "units_sold"
        private const val COL_TOP_PRODUCT_NAME = "top_product_name"
        private const val COL_TOP_PRODUCT_STATS = "top_product_stats"
        private const val COL_TOP_PRODUCT_REVENUE = "top_product_revenue"

        // Admin Records table
        private const val TABLE_ADMIN_RECORDS = "admin_records"
        private const val COL_RECORD_ID = "record_id"
        private const val COL_TYPE = "type"
        private const val COL_TITLE = "title"
        private const val COL_SUBTITLE = "subtitle"
        private const val COL_RECORD_STATUS = "record_status"
        private const val COL_ACTION_ONE = "action_one"
        private const val COL_ACTION_TWO = "action_two"
        private const val COL_ACTION_THREE = "action_three"

        // Products table for offline capabilities
        private const val TABLE_PRODUCTS = "local_products"
        private const val COL_PRODUCT_ID = "product_id"
        private const val COL_PRODUCT_NAME = "name"
        private const val COL_PRODUCT_DESC = "description"
        private const val COL_PRODUCT_CATEGORY = "category"
        private const val COL_PRICE_CENTS = "price_cents"
        private const val COL_STOCK_QTY = "stock_quantity"
        private const val COL_AVAILABLE = "available"
        private const val COL_IMAGE_URL = "image_url"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createUsersTable = """
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID TEXT PRIMARY KEY,
                $COL_EMAIL TEXT UNIQUE,
                $COL_PASSWORD TEXT,
                $COL_FIRST_NAME TEXT,
                $COL_LAST_NAME TEXT,
                $COL_ROLE TEXT,
                $COL_ACCOUNT_STATUS INTEGER
            )
        """.trimIndent()

        val createOrdersTable = """
            CREATE TABLE $TABLE_ORDERS (
                $COL_ORDER_ID TEXT PRIMARY KEY,
                $COL_USER_ID_ORDER TEXT,
                $COL_AMOUNT REAL,
                $COL_CATEGORY TEXT,
                $COL_STATUS TEXT
            )
        """.trimIndent()

        val createSupplierTable = """
            CREATE TABLE $TABLE_SUPPLIER_METRICS (
                $COL_SUPPLIER_ID TEXT PRIMARY KEY,
                $COL_REVENUE REAL,
                $COL_UNITS_SOLD INTEGER,
                $COL_TOP_PRODUCT_NAME TEXT,
                $COL_TOP_PRODUCT_STATS TEXT,
                $COL_TOP_PRODUCT_REVENUE REAL
            )
        """.trimIndent()

        val createAdminTable = """
            CREATE TABLE $TABLE_ADMIN_RECORDS (
                $COL_RECORD_ID TEXT PRIMARY KEY,
                $COL_TYPE TEXT,
                $COL_TITLE TEXT,
                $COL_SUBTITLE TEXT,
                $COL_RECORD_STATUS TEXT,
                $COL_ACTION_ONE TEXT,
                $COL_ACTION_TWO TEXT,
                $COL_ACTION_THREE TEXT
            )
        """.trimIndent()

        val createProductsTable = """
            CREATE TABLE $TABLE_PRODUCTS (
                $COL_PRODUCT_ID TEXT PRIMARY KEY,
                $COL_PRODUCT_NAME TEXT,
                $COL_PRODUCT_DESC TEXT,
                $COL_PRODUCT_CATEGORY TEXT,
                $COL_PRICE_CENTS INTEGER,
                $COL_STOCK_QTY INTEGER,
                $COL_AVAILABLE INTEGER,
                $COL_IMAGE_URL TEXT
            )
        """.trimIndent()

        db.execSQL(createUsersTable)
        db.execSQL(createOrdersTable)
        db.execSQL(createSupplierTable)
        db.execSQL(createAdminTable)
        db.execSQL(createProductsTable)

        seedDefaultUsers(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ORDERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SUPPLIER_METRICS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ADMIN_RECORDS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PRODUCTS")
        onCreate(db)
    }

    private fun seedDefaultUsers(db: SQLiteDatabase) {
        val users = listOf(
            LocalUser("admin_default_id", "admin@gift4joy.com", "Admin123!", "Admin", "User", "admin", true),
            LocalUser("supplier_default_id", "supplier@gift4joy.com", "Supplier123!", "Supplier", "User", "owner", true),
            LocalUser("customer_default_id", "customer@gift4joy.com", "Customer123!", "Customer", "User", "customer", true)
        )

        for (u in users) {
            val cv = ContentValues().apply {
                put(COL_USER_ID, u.userId)
                put(COL_EMAIL, u.email.lowercase())
                put(COL_PASSWORD, u.password)
                put(COL_FIRST_NAME, u.firstName)
                put(COL_LAST_NAME, u.lastName)
                put(COL_ROLE, u.role.lowercase())
                put(COL_ACCOUNT_STATUS, if (u.accountStatus) 1 else 0)
            }
            db.insertWithOnConflict(TABLE_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun saveUser(user: LocalUser) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_USER_ID, user.userId)
            put(COL_EMAIL, user.email.lowercase())
            put(COL_PASSWORD, user.password)
            put(COL_FIRST_NAME, user.firstName)
            put(COL_LAST_NAME, user.lastName)
            put(COL_ROLE, user.role.lowercase())
            put(COL_ACCOUNT_STATUS, if (user.accountStatus) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_USERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUserByEmail(email: String): LocalUser? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COL_EMAIL = ?",
            arrayOf(email.lowercase().trim()),
            null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val userId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID))
                val mail = c.getString(c.getColumnIndexOrThrow(COL_EMAIL))
                val pass = c.getString(c.getColumnIndexOrThrow(COL_PASSWORD))
                val fName = c.getString(c.getColumnIndexOrThrow(COL_FIRST_NAME))
                val lName = c.getString(c.getColumnIndexOrThrow(COL_LAST_NAME))
                val role = c.getString(c.getColumnIndexOrThrow(COL_ROLE))
                val status = c.getInt(c.getColumnIndexOrThrow(COL_ACCOUNT_STATUS)) == 1
                return LocalUser(userId, mail, pass, fName, lName, role, status)
            }
        }
        return null
    }

    fun getUserById(userId: String): LocalUser? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_USERS,
            null,
            "$COL_USER_ID = ?",
            arrayOf(userId),
            null, null, null
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val uId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID))
                val mail = c.getString(c.getColumnIndexOrThrow(COL_EMAIL))
                val pass = c.getString(c.getColumnIndexOrThrow(COL_PASSWORD))
                val fName = c.getString(c.getColumnIndexOrThrow(COL_FIRST_NAME))
                val lName = c.getString(c.getColumnIndexOrThrow(COL_LAST_NAME))
                val role = c.getString(c.getColumnIndexOrThrow(COL_ROLE))
                val status = c.getInt(c.getColumnIndexOrThrow(COL_ACCOUNT_STATUS)) == 1
                return LocalUser(uId, mail, pass, fName, lName, role, status)
            }
        }
        return null
    }

    fun authenticateUserLocally(email: String, passwordText: String): LocalUser? {
        val user = getUserByEmail(email) ?: return null
        if (user.password.isNotEmpty() && passwordText.isNotEmpty()) {
            return if (user.password == passwordText) user else null
        }
        return user
    }

    fun getAllUsers(): List<LocalUser> {
        val list = mutableListOf<LocalUser>()
        val db = readableDatabase
        val cursor = db.query(TABLE_USERS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val uId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID))
                val mail = c.getString(c.getColumnIndexOrThrow(COL_EMAIL))
                val pass = c.getString(c.getColumnIndexOrThrow(COL_PASSWORD))
                val fName = c.getString(c.getColumnIndexOrThrow(COL_FIRST_NAME))
                val lName = c.getString(c.getColumnIndexOrThrow(COL_LAST_NAME))
                val role = c.getString(c.getColumnIndexOrThrow(COL_ROLE))
                val status = c.getInt(c.getColumnIndexOrThrow(COL_ACCOUNT_STATUS)) == 1
                list.add(LocalUser(uId, mail, pass, fName, lName, role, status))
            }
        }
        return list
    }

    fun saveProductLocally(product: LocalProduct) {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_PRODUCT_ID, product.productId)
            put(COL_PRODUCT_NAME, product.name)
            put(COL_PRODUCT_DESC, product.description)
            put(COL_PRODUCT_CATEGORY, product.category)
            put(COL_PRICE_CENTS, product.priceCents)
            put(COL_STOCK_QTY, product.stockQuantity)
            put(COL_AVAILABLE, if (product.available) 1 else 0)
            put(COL_IMAGE_URL, product.imageUrl)
        }
        db.insertWithOnConflict(TABLE_PRODUCTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getLocalProducts(): List<LocalProduct> {
        val list = mutableListOf<LocalProduct>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PRODUCTS, null, null, null, null, null, null)
        cursor.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(c.getColumnIndexOrThrow(COL_PRODUCT_ID))
                val name = c.getString(c.getColumnIndexOrThrow(COL_PRODUCT_NAME))
                val desc = c.getString(c.getColumnIndexOrThrow(COL_PRODUCT_DESC))
                val cat = c.getString(c.getColumnIndexOrThrow(COL_PRODUCT_CATEGORY))
                val price = c.getLong(c.getColumnIndexOrThrow(COL_PRICE_CENTS))
                val stock = c.getInt(c.getColumnIndexOrThrow(COL_STOCK_QTY))
                val avail = c.getInt(c.getColumnIndexOrThrow(COL_AVAILABLE)) == 1
                val img = c.getString(c.getColumnIndexOrThrow(COL_IMAGE_URL))
                list.add(LocalProduct(id, name, desc, cat, price, stock, avail, img))
            }
        }
        return list
    }

    fun saveUserOrder(orderId: String, userId: String, amount: Double, category: String, status: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ORDER_ID, orderId)
            put(COL_USER_ID_ORDER, userId)
            put(COL_AMOUNT, amount)
            put(COL_CATEGORY, category)
            put(COL_STATUS, status)
        }
        db.insertWithOnConflict(TABLE_ORDERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getUserOrders(userId: String): List<LocalOrder> {
        val list = mutableListOf<LocalOrder>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ORDERS,
            null,
            "$COL_USER_ID_ORDER = ?",
            arrayOf(userId),
            null, null, null
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val orderId = c.getString(c.getColumnIndexOrThrow(COL_ORDER_ID))
                val uId = c.getString(c.getColumnIndexOrThrow(COL_USER_ID_ORDER))
                val amount = c.getDouble(c.getColumnIndexOrThrow(COL_AMOUNT))
                val category = c.getString(c.getColumnIndexOrThrow(COL_CATEGORY))
                val status = c.getString(c.getColumnIndexOrThrow(COL_STATUS))
                list.add(LocalOrder(orderId, uId, amount, category, status))
            }
        }
        return list
    }
}

typealias LocalAppDatabase = G4JLocalAppDatabase
