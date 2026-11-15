package com.example.gift4joy

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

data class PaymentCartItem(
    val name: String,
    val priceCents: Long,
    val quantity: Int
)

class Payement : AppCompatActivity() {

    private lateinit var paymentRadioGroup: RadioGroup
    private lateinit var radioOnline: RadioButton
    private lateinit var radioArrival: RadioButton
    private lateinit var cardDetailsLayout: LinearLayout
    private lateinit var cardSaveRadioGroup: RadioGroup
    private lateinit var tvPaymentDescription: TextView
    private lateinit var tvOrderTotal: TextView
    private lateinit var cartItemsContainer: LinearLayout
    private lateinit var btnConfirmOrder: Button

    private val cartItems = mutableListOf<PaymentCartItem>()

    private var totalAmountCents: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_payement)

        paymentRadioGroup = findViewById(R.id.paymentRadioGroup)
        radioOnline = findViewById(R.id.radioOnline)
        radioArrival = findViewById(R.id.radioArrival)

        cardDetailsLayout = findViewById(R.id.cardDetailsLayout)
        cardSaveRadioGroup = findViewById(R.id.cardSaveRadioGroup)

        tvPaymentDescription = findViewById(R.id.tvPaymentDescription)
        tvOrderTotal = findViewById(R.id.tvOrderTotal)

        cartItemsContainer = findViewById(R.id.cartItemsContainer)
        btnConfirmOrder = findViewById(R.id.btnConfirmOrder)

        loadCartItems()

        calculateTotal()

        displayCartItems()

        paymentRadioGroup.setOnCheckedChangeListener {
                _, checkedId ->

            when (checkedId) {

                R.id.radioOnline -> {

                    cardDetailsLayout.visibility = View.VISIBLE

                    tvPaymentDescription.text =
                        "Enter your card details to continue."

                    btnConfirmOrder.text = "CONTINUE TO PAYMENT"
                }

                R.id.radioArrival -> {

                    cardDetailsLayout.visibility = View.GONE

                    tvPaymentDescription.text =
                        "You will pay when your order arrives."

                    btnConfirmOrder.text = "CONFIRM ORDER"
                }
            }
        }

        btnConfirmOrder.setOnClickListener {

            if (cartItems.isEmpty()) {

                Toast.makeText(
                    this,
                    "Your cart is empty",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            when (paymentRadioGroup.checkedRadioButtonId) {

                R.id.radioOnline -> {
                    payOnline()
                }

                R.id.radioArrival -> {
                    payUponArrival()
                }

                else -> {
                    Toast.makeText(
                        this,
                        "Please select a payment method",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadCartItems() {
        cartItems.clear()

        val managerItems = CartManager.getItems(this)
        if (managerItems.isNotEmpty()) {
            managerItems.forEach { item ->
                cartItems.add(
                    PaymentCartItem(
                        item.title,
                        item.unitPrice * 100L,
                        item.quantity
                    )
                )
            }
        } else {
            // Temporary example products if cart is empty
            cartItems.add(
                PaymentCartItem(
                    "Birthday Gift Box",
                    25000L,
                    1
                )
            )
            cartItems.add(
                PaymentCartItem(
                    "Chocolate Hamper",
                    18000L,
                    2
                )
            )
        }
    }

    private fun calculateTotal() {

        totalAmountCents = cartItems.sumOf {
            it.priceCents * it.quantity
        }

        tvOrderTotal.text =
            "Total: ${formatMoney(totalAmountCents)}"
    }

    private fun displayCartItems() {

        cartItemsContainer.removeAllViews()

        cartItems.forEach { item ->

            val itemTotal = item.priceCents * item.quantity

            val textView = TextView(this)

            textView.text =
                "${item.name}\n" +
                        "Quantity: ${item.quantity}\n" +
                        "Price: ${formatMoney(itemTotal)}"

            textView.textSize = 16f

            textView.setPadding(
                10,
                15,
                10,
                15
            )

            cartItemsContainer.addView(textView)
        }
    }

    private fun formatMoney(amountCents: Long): String {

        return String.format(
            Locale.US,
            "R %.2f",
            amountCents / 100.0
        )
    }

    private fun payOnline() {

        val saveCard =
            cardSaveRadioGroup.checkedRadioButtonId ==
                    R.id.radioSaveCard

        val message = if (saveCard) {

            "You selected Save Card for Future Payments.\n\n" +
                    "A secure payment provider must be connected " +
                    "before card details can be processed or saved."

        } else {

            "You selected Use Card Once.\n\n" +
                    "A secure payment provider must be connected " +
                    "before an online payment can be processed."
        }

        AlertDialog.Builder(this)
            .setTitle("Online Payment")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun payUponArrival() {

        AlertDialog.Builder(this)
            .setTitle("Confirm Order")
            .setMessage(
                "Order Total: ${formatMoney(totalAmountCents)}\n\n" +
                        "Payment Method: Pay Upon Arrival\n\n" +
                        "Would you like to continue?"
            )
            .setPositiveButton("Confirm") { _, _ ->

                val resultIntent = Intent().apply {

                    putExtra(
                        "PAYMENT_METHOD",
                        "PAY_UPON_ARRIVAL"
                    )

                    putExtra(
                        "PAYMENT_STATUS",
                        "PENDING"
                    )

                    putExtra(
                        "TOTAL_AMOUNT_CENTS",
                        totalAmountCents
                    )
                }

                setResult(
                    RESULT_OK,
                    resultIntent
                )

                Toast.makeText(
                    this,
                    "Payment method confirmed",
                    Toast.LENGTH_LONG
                ).show()

                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}