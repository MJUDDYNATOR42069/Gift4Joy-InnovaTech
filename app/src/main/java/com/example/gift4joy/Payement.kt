package com.example.gift4joy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class Payement : AppCompatActivity() {
    private lateinit var paymentRadioGroup: RadioGroup
    private lateinit var radioOnline: RadioButton
    private lateinit var radioArrival: RadioButton
    private lateinit var tvPaymentDescription: TextView
    private lateinit var tvOrderTotal: TextView
    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var btnConfirmOrder: Button
    private val cartItems = mutableListOf<CartItem>()
    private var totalAmountCents: Long = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pollCount = 0
    private var activeOrderId: String? = null

    private val payFastLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            tvPaymentDescription.text = "Payment window closed. Your cart has not been cleared."
            btnConfirmOrder.isEnabled = true
            return@registerForActivityResult
        }
        val orderId = result.data?.getStringExtra(PayFastWebActivity.EXTRA_ORDER_ID)
        val cancelled = result.data?.getBooleanExtra(PayFastWebActivity.EXTRA_CANCELLED, false) ?: false
        if (cancelled || orderId.isNullOrBlank()) {
            tvPaymentDescription.text = "Payment cancelled. You can try again when ready."
            btnConfirmOrder.isEnabled = true
            return@registerForActivityResult
        }
        activeOrderId = orderId
        pollCount = 0
        tvPaymentDescription.text = "Payment returned. Verifying securely with PayFast…"
        pollPaymentStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_payement)
        paymentRadioGroup = findViewById(R.id.paymentRadioGroup)
        radioOnline = findViewById(R.id.radioOnline)
        radioArrival = findViewById(R.id.radioArrival)
        tvPaymentDescription = findViewById(R.id.tvPaymentDescription)
        tvOrderTotal = findViewById(R.id.tvOrderTotal)
        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        btnConfirmOrder = findViewById(R.id.btnConfirmOrder)

        cartItems.clear()
        cartItems.addAll(CartManager.getItems(this))
        totalAmountCents = cartItems.sumOf { it.unitPrice * it.quantity }
        tvOrderTotal.text = "Total: ${formatMoney(totalAmountCents)}"
        displayCartItems()

        // Firebase Spark has no Cloud Functions. A secure dynamic PayFast integration
        // needs a trusted server to sign checkout requests and validate PayFast ITNs.
        // Keep the option visible but unavailable rather than embedding merchant secrets in the APK.
        radioOnline.isEnabled = false
        radioOnline.text = "PayFast (requires secure payment service)"
        radioArrival.isChecked = true
        tvPaymentDescription.text = "PayFast is not enabled in the Spark-only setup yet. You can still place an order using Pay Upon Arrival."

        paymentRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOnline -> {
                    tvPaymentDescription.text = "Continue securely with PayFast. Card details are entered only on PayFast."
                    btnConfirmOrder.text = "CONTINUE TO PAYMENT"
                }
                R.id.radioArrival -> {
                    tvPaymentDescription.text = "You will pay when your order arrives."
                    btnConfirmOrder.text = "CONFIRM ORDER"
                }
            }
        }
        btnConfirmOrder.setOnClickListener {
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Your cart is empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            when (paymentRadioGroup.checkedRadioButtonId) {
                R.id.radioOnline -> startPayFastCheckout()
                R.id.radioArrival -> payUponArrival()
                else -> Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayCartItems() {
        cartItemsContainer.removeAllViews()
        cartItems.forEach { item ->
            val textView = TextView(this).apply {
                text = "${item.title}\nQuantity: ${item.quantity}\nPrice: ${formatMoney(item.unitPrice * item.quantity)}"
                textSize = 16f
                setPadding(10, 15, 10, 15)
            }
            cartItemsContainer.addView(textView)
        }
    }

    private fun startPayFastCheckout() {
        val missingId = cartItems.any { it.productId.isBlank() }
        if (missingId) {
            AlertDialog.Builder(this).setTitle("Refresh your cart")
                .setMessage("One or more cart items were added using an older app version. Remove those items and add them again before paying online.")
                .setPositiveButton("OK", null).show()
            return
        }
        btnConfirmOrder.isEnabled = false
        tvPaymentDescription.text = "Creating a secure PayFast checkout…"
        Gift4JoyApi.createCheckout(this, cartItems) { result ->
            runOnUiThread {
                result.onSuccess { checkout ->
                    val intent = Intent(this, PayFastWebActivity::class.java).apply {
                        putExtra(PayFastWebActivity.EXTRA_HTML, checkout.checkoutHtml)
                        putExtra(PayFastWebActivity.EXTRA_ORDER_ID, checkout.orderId)
                    }
                    payFastLauncher.launch(intent)
                }.onFailure { error ->
                    btnConfirmOrder.isEnabled = true
                    tvPaymentDescription.text = "Unable to start PayFast. Please try again."
                    AlertDialog.Builder(this).setTitle("Payment setup")
                        .setMessage(error.localizedMessage ?: "The secure payment service is unavailable.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }
    }

    private fun pollPaymentStatus() {
        val orderId = activeOrderId ?: return
        Gift4JoyApi.getPaymentStatus(orderId) { result ->
            runOnUiThread {
                val status = result.getOrNull()?.uppercase(Locale.US)
                when (status) {
                    "PAID" -> {
                        handler.removeCallbacksAndMessages(null)
                        setResult(Activity.RESULT_OK, Intent().apply {
                            putExtra("PAYMENT_METHOD", "PAYFAST")
                            putExtra("PAYMENT_STATUS", "PAID")
                            putExtra("ORDER_ID", orderId)
                            putExtra("TOTAL_AMOUNT_CENTS", totalAmountCents)
                        })
                        Toast.makeText(this, "PayFast payment confirmed", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    "FAILED", "CANCELLED" -> {
                        btnConfirmOrder.isEnabled = true
                        tvPaymentDescription.text = "PayFast did not confirm this payment. Your cart is still available."
                    }
                    else -> {
                        pollCount++
                        if (pollCount >= 30) {
                            btnConfirmOrder.isEnabled = true
                            tvPaymentDescription.text = "Payment is still awaiting confirmation. Your cart has not been cleared. Check your order status before trying again."
                        } else {
                            tvPaymentDescription.text = "Waiting for PayFast confirmation… (${pollCount}/30)"
                            handler.postDelayed({ pollPaymentStatus() }, 2000)
                        }
                    }
                }
            }
        }
    }

    private fun payUponArrival() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Order")
            .setMessage("Order Total: ${formatMoney(totalAmountCents)}\n\nPayment Method: Pay Upon Arrival\n\nWould you like to continue?")
            .setPositiveButton("Confirm") { _, _ ->
                setResult(Activity.RESULT_OK, Intent().apply {
                    putExtra("PAYMENT_METHOD", "PAY_UPON_ARRIVAL")
                    putExtra("PAYMENT_STATUS", "PENDING")
                    putExtra("TOTAL_AMOUNT_CENTS", totalAmountCents)
                })
                Toast.makeText(this, "Payment method confirmed", Toast.LENGTH_LONG).show()
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatMoney(amountCents: Long): String = String.format(Locale.US, "R %.2f", amountCents / 100.0)

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
