package com.example.gift4joy

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors

/** Small authenticated client for the Gift4Joy HTTPS backend. */
object Gift4JoyApi {
    private val executor = Executors.newSingleThreadExecutor()
    private val baseUrl: String get() = BuildConfig.API_BASE_URL.trimEnd('/')

    data class Checkout(val orderId: String, val checkoutHtml: String)

    fun createCheckout(context: Context, items: List<CartItem>, callback: (Result<Checkout>) -> Unit) {
        val payload = JSONObject().put("items", JSONArray().apply {
            items.forEach { item ->
                put(JSONObject().put("productId", item.productId).put("quantity", item.quantity))
            }
        })
        authenticatedRequest("POST", "/api/payments/create", payload.toString()) { result ->
            callback(result.mapCatching { response ->
                Checkout(response.getString("orderId"), response.getString("checkoutHtml"))
            })
        }
    }

    fun getPaymentStatus(orderId: String, callback: (Result<String>) -> Unit) {
        authenticatedRequest("GET", "/api/payments/${Uri.encode(orderId)}", null) { result ->
            callback(result.mapCatching { it.optString("paymentStatus", "PENDING") })
        }
    }

    fun uploadProductImage(context: Context, productId: String, imageFile: File, callback: (Result<String>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { callback(Result.failure(IllegalStateException("Please sign in again"))); return }
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            executor.execute {
                var connection: HttpURLConnection? = null
                try {
                    val boundary = "Gift4Joy-${UUID.randomUUID()}"
                    val conn = (URL("$baseUrl/api/products/${Uri.encode(productId)}/image").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 20000
                        readTimeout = 30000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer ${tokenResult.token}")
                        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    }
                    connection = conn
                    conn.outputStream.use { out ->
                        val mimeType = when (imageFile.extension.lowercase()) {
                            "png" -> "image/png"
                            "webp" -> "image/webp"
                            else -> "image/jpeg"
                        }
                        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"image\"; filename=\"${imageFile.name}\"\r\nContent-Type: $mimeType\r\n\r\n".toByteArray())
                        imageFile.inputStream().use { it.copyTo(out) }
                        out.write("\r\n--$boundary--\r\n".toByteArray())
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream.bufferedReader().use { it.readText() }
                    if (code !in 200..299) throw IllegalStateException(JSONObject(body).optString("error", "Image upload failed"))
                    callback(Result.success(JSONObject(body).getString("imageUrl")))
                } catch (e: Exception) { callback(Result.failure(e)) }
                finally { connection?.disconnect() }
            }
        }.addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun authenticatedRequest(method: String, path: String, body: String?, callback: (Result<JSONObject>) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { callback(Result.failure(IllegalStateException("Please sign in again"))); return }
        user.getIdToken(false).addOnSuccessListener { tokenResult ->
            executor.execute {
                var connection: HttpURLConnection? = null
                try {
                    val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                        requestMethod = method
                        connectTimeout = 15000
                        readTimeout = 20000
                        setRequestProperty("Authorization", "Bearer ${tokenResult.token}")
                        setRequestProperty("Accept", "application/json")
                        if (body != null) {
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        }
                    }
                    connection = conn
                    if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val response = stream.bufferedReader().use { it.readText() }
                    val json = if (response.isBlank()) JSONObject() else JSONObject(response)
                    if (code !in 200..299) throw IllegalStateException(json.optString("error", "Request failed ($code)"))
                    callback(Result.success(json))
                } catch (e: Exception) { callback(Result.failure(e)) }
                finally { connection?.disconnect() }
            }
        }.addOnFailureListener { callback(Result.failure(it)) }
    }

}
