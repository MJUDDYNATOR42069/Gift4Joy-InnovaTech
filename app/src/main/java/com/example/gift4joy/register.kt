
package com.example.gift4joy

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore

class register : AppCompatActivity() {

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirm: EditText
    private lateinit var cbTerms: CheckBox
    private lateinit var btnCreateAccount: Button
    private lateinit var tvTerms: TextView
    private lateinit var tvFooter: TextView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initViews()
        setupClickableText()
        setupClickListeners()
    }

    private fun initViews() {
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etPassword = findViewById(R.id.etPassword)
        etConfirm = findViewById(R.id.etConfirm)
        cbTerms = findViewById(R.id.cbTerms)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvTerms = findViewById(R.id.tvTerms)
        tvFooter = findViewById(R.id.tvFooter)
    }

    private fun setupClickableText() {

        val termsText =
            "I agree to the Terms of Service and Privacy Policy"

        val termsSpannable = SpannableString(termsText)

        val termsStart = termsText.indexOf("Terms of Service")
        val termsEnd = termsStart + "Terms of Service".length

        val privacyStart = termsText.indexOf("Privacy Policy")
        val privacyEnd = privacyStart + "Privacy Policy".length

        termsSpannable.setSpan(
            createClickableSpan("Terms of Service"),
            termsStart,
            termsEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        termsSpannable.setSpan(
            createClickableSpan("Privacy Policy"),
            privacyStart,
            privacyEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvTerms.text = termsSpannable
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = Color.TRANSPARENT

        val footerText = "Already have an account? Log in"
        val footerSpannable = SpannableString(footerText)

        val loginStart = footerText.indexOf("Log in")
        val loginEnd = loginStart + "Log in".length

        footerSpannable.setSpan(
            createClickableSpan("Log in"),
            loginStart,
            loginEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        tvFooter.text = footerSpannable
        tvFooter.movementMethod = LinkMovementMethod.getInstance()
        tvFooter.highlightColor = Color.TRANSPARENT
    }

    private fun createClickableSpan(linkName: String): ClickableSpan {

        return object : ClickableSpan() {

            override fun onClick(widget: View) {

                if (linkName == "Log in") {

                    navigateToLogin()

                } else {

                    Toast.makeText(
                        this@register,
                        "Clicked: $linkName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)

                ds.color = Color.parseColor("#D81B60")
                ds.isUnderlineText = false
            }
        }
    }

    private fun setupClickListeners() {

        btnCreateAccount.setOnClickListener {

            if (validateForm()) {
                performSignUp()
            }
        }
    }

    private fun validateForm(): Boolean {

        var isValid = true

        if (etFullName.text.toString().trim().isEmpty()) {
            etFullName.error = "Full name is required"
            isValid = false
        }

        val email = etEmail.text.toString().trim()

        if (
            email.isEmpty() ||
            !Patterns.EMAIL_ADDRESS.matcher(email).matches()
        ) {
            etEmail.error = "Valid email address is required"
            isValid = false
        }

        if (etPhone.text.toString().trim().isEmpty()) {
            etPhone.error = "Phone number is required"
            isValid = false
        }

        val password = etPassword.text.toString()

        if (password.length < 8) {
            etPassword.error = "Password must be at least 8 characters"
            isValid = false
        }

        if (etConfirm.text.toString() != password) {
            etConfirm.error = "Passwords do not match"
            isValid = false
        }

        if (!cbTerms.isChecked) {

            Toast.makeText(
                this,
                "You must agree to the Terms of Service",
                Toast.LENGTH_LONG
            ).show()

            isValid = false
        }

        return isValid
    }

    private fun performSignUp() {

        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val password = etPassword.text.toString()

        btnCreateAccount.isEnabled = false
        btnCreateAccount.text = "Creating account..."

        auth.createUserWithEmailAndPassword(email, password)

            .addOnSuccessListener { authResult ->

                val firebaseUser = authResult.user

                if (firebaseUser == null) {

                    restoreButton()
                    showMessage("Unable to retrieve your account.")
                    auth.signOut()

                    return@addOnSuccessListener
                }

                val uid = firebaseUser.uid

                // All public registrations are CUSTOMER/USER accounts.
                val userData = hashMapOf(
                    "uid" to uid,
                    "email" to email,
                    "name" to fullName,
                    "phone" to phone,
                    "role" to "user"
                )

                // Firestore document ID must match Firebase UID.
                db.collection("users")
                    .document(uid)
                    .set(userData)

                    .addOnSuccessListener {

                        Log.d(
                            "REGISTER",
                            "User profile successfully created: $uid"
                        )

                        Toast.makeText(
                            this,
                            "Account created successfully! Please log in.",
                            Toast.LENGTH_LONG
                        ).show()

                        // Sign out before returning to login.
                        auth.signOut()

                        navigateToLogin()
                    }

                    .addOnFailureListener { error ->

                        Log.e(
                            "REGISTER_ERROR",
                            "Firestore profile creation failed",
                            error
                        )

                        auth.signOut()
                        restoreButton()

                        showMessage(
                            "Your Firebase account was created, but your profile could not be saved. " +
                                    "Error: ${error.localizedMessage}"
                        )
                    }
            }

            .addOnFailureListener { error ->

                restoreButton()

                val errorCode =
                    (error as? FirebaseAuthException)?.errorCode

                val message = when (errorCode) {

                    "ERROR_EMAIL_ALREADY_IN_USE" ->
                        "An account already exists with this email."

                    "ERROR_WEAK_PASSWORD" ->
                        "Your password is too weak."

                    "ERROR_INVALID_EMAIL" ->
                        "Please enter a valid email address."

                    "ERROR_OPERATION_NOT_ALLOWED" ->
                        "Email/Password authentication is disabled."

                    "ERROR_NETWORK_REQUEST_FAILED" ->
                        "Please check your internet connection."

                    else ->
                        error.localizedMessage
                            ?: "Registration failed."
                }

                Log.e(
                    "REGISTER_ERROR",
                    "Firebase Authentication registration failed",
                    error
                )

                showMessage(message)
            }
    }

    private fun navigateToLogin() {

        val intent = Intent(this, login::class.java).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun restoreButton() {

        btnCreateAccount.isEnabled = true
        btnCreateAccount.text = "Create account"
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
