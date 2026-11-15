
package com.example.gift4joy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class login : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnGoogle: Button
    private lateinit var btnFacebook: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvFooter: TextView

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val googleSignInLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                showMessage("Google login was cancelled.")
                return@registerForActivityResult
            }

            try {
                val account = GoogleSignIn
                    .getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)

                val idToken = account.idToken

                if (idToken.isNullOrBlank()) {
                    showMessage(
                        "Google did not return an ID token. Check your Firebase and OAuth configuration."
                    )
                    return@registerForActivityResult
                }

                val credential =
                    GoogleAuthProvider.getCredential(idToken, null)

                signInWithSocialCredential(credential, "Google")

            } catch (e: ApiException) {

                Log.e(
                    "GoogleLogin",
                    "Google sign-in failed: ${e.statusCode}",
                    e
                )

                showMessage("Google login failed. Please try again.")

            } catch (e: Exception) {

                Log.e(
                    "GoogleLogin",
                    "Unexpected Google sign-in error",
                    e
                )

                showMessage("Google login failed. Please try again.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnGoogle = findViewById(R.id.btnGoogle)
        btnFacebook = findViewById(R.id.btnFacebook)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvFooter = findViewById(R.id.tvFooter)

        setupGoogleSignIn()
        setupFacebookSignIn()
        setupClickListeners()
        setupTextWatchers()
    }

    private fun setupGoogleSignIn() {

        try {
            val options = GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken(
                    getString(R.string.default_web_client_id)
                )
                .requestEmail()
                .build()

            googleSignInClient =
                GoogleSignIn.getClient(this, options)

        } catch (e: Exception) {

            Log.e(
                "GoogleLogin",
                "Unable to initialize Google sign-in",
                e
            )

            showMessage("Google login is not configured correctly.")
        }
    }

    private fun setupFacebookSignIn() {

        callbackManager = CallbackManager.Factory.create()

        try {
            LoginManager.getInstance().registerCallback(
                callbackManager,

                object : FacebookCallback<LoginResult> {

                    override fun onSuccess(result: LoginResult) {

                        val credential =
                            FacebookAuthProvider.getCredential(
                                result.accessToken.token
                            )

                        signInWithSocialCredential(
                            credential,
                            "Facebook"
                        )
                    }

                    override fun onCancel() {
                        showMessage("Facebook login was cancelled.")
                    }

                    override fun onError(error: FacebookException) {

                        Log.e(
                            "FacebookLogin",
                            "Facebook sign-in failed",
                            error
                        )

                        showMessage(
                            "Facebook login failed. Check your configuration."
                        )
                    }
                }
            )

        } catch (e: Exception) {

            Log.e(
                "FacebookLogin",
                "Unable to initialize Facebook login",
                e
            )

            showMessage("Facebook login is not configured correctly.")
        }
    }

    private fun setupClickListeners() {

        btnLogin.setOnClickListener {
            authenticateUser()
        }

        btnGoogle.setOnClickListener {

            if (::googleSignInClient.isInitialized) {

                googleSignInLauncher.launch(
                    googleSignInClient.signInIntent
                )

            } else {
                showMessage("Google login is unavailable.")
            }
        }

        btnFacebook.setOnClickListener {

            try {
                LoginManager.getInstance().logInWithReadPermissions(
                    this,
                    listOf("email", "public_profile")
                )

            } catch (e: Exception) {

                Log.e(
                    "FacebookLogin",
                    "Unable to start Facebook login",
                    e
                )

                showMessage("Facebook login is unavailable.")
            }
        }

        tvForgotPassword.setOnClickListener {
            resetPassword()
        }

        tvFooter.setOnClickListener {

            startActivity(
                Intent(this, register::class.java)
            )
        }
    }

    @Deprecated("Facebook SDK callback compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(requestCode, resultCode, data)

        if (::callbackManager.isInitialized) {

            callbackManager.onActivityResult(
                requestCode,
                resultCode,
                data
            )
        }
    }

    private fun authenticateUser() {

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (!validateInput(email, password)) return

        btnLogin.isEnabled = false

        val originalText = btnLogin.text.toString()
        btnLogin.text = "Signing in..."

        auth.signInWithEmailAndPassword(email, password)

            .addOnSuccessListener {

                val user = auth.currentUser

                if (user == null) {

                    rejectLogin(
                        originalText,
                        "Unable to retrieve your account."
                    )

                    return@addOnSuccessListener
                }

                verifyUserRole(user.uid, originalText)
            }

            .addOnFailureListener { error ->

                Log.e(
                    "LOGIN_ERROR",
                    "Firebase Authentication login failed: ${error.message}",
                    error
                )

                rejectLogin(
                    originalText,
                    friendlyAuthError(error)
                )
            }
    }

    private fun verifyUserRole(
        uid: String,
        originalText: String
    ) {

        db.collection("users")
            .document(uid)
            .get()

            .addOnSuccessListener { document ->

                if (isFinishing || isDestroyed) {
                    return@addOnSuccessListener
                }

                if (!document.exists()) {

                    rejectLogin(
                        originalText,
                        "Your account profile was not found. Please contact support."
                    )

                    return@addOnSuccessListener
                }

                val storedUid = document.getString("uid")

                val role = document.getString("role")
                    ?.trim()
                    ?.lowercase()

                if (storedUid != uid) {

                    rejectLogin(
                        originalText,
                        "Your account profile could not be verified."
                    )

                    return@addOnSuccessListener
                }

                when (role) {

                    "owner" -> {
                        openOwnerDashboard()
                    }

                    "user" -> {
                        openCustomerHome()
                    }

                    else -> {

                        rejectLogin(
                            originalText,
                            "Your account has an invalid role. Please contact support."
                        )
                    }
                }
            }

            .addOnFailureListener { error ->

                Log.e(
                    "FIRESTORE_ROLE_ERROR",
                    "Firestore role verification failed for UID: $uid",
                    error
                )

                val message = when (error) {

                    is FirebaseFirestoreException -> {

                        when (error.code) {

                            FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                                "Firestore permission denied. Check your security rules."

                            FirebaseFirestoreException.Code.UNAVAILABLE ->
                                "Firestore is unavailable. Check your internet connection."

                            else ->
                                "Unable to verify your account: ${error.localizedMessage}"
                        }
                    }

                    else ->
                        "Unable to verify your account: ${error.localizedMessage}"
                }

                rejectLogin(
                    originalText,
                    message
                )
            }
    }

    private fun signInWithSocialCredential(
        credential: AuthCredential,
        provider: String
    ) {

        auth.signInWithCredential(credential)

            .addOnSuccessListener {

                val user = auth.currentUser

                if (user == null) {

                    auth.signOut()
                    showMessage("Unable to retrieve your account.")

                    return@addOnSuccessListener
                }

                val userRef =
                    db.collection("users").document(user.uid)

                userRef.get()

                    .addOnSuccessListener { document ->

                        if (isFinishing || isDestroyed) {
                            return@addOnSuccessListener
                        }

                        if (document.exists()) {

                            val storedUid =
                                document.getString("uid")

                            val role = document.getString("role")
                                ?.trim()
                                ?.lowercase()

                            if (storedUid != user.uid) {

                                auth.signOut()

                                showMessage(
                                    "Your account profile could not be verified."
                                )

                                return@addOnSuccessListener
                            }

                            when (role) {

                                "owner" -> openOwnerDashboard()

                                "user" -> openCustomerHome()

                                else -> {

                                    auth.signOut()

                                    showMessage(
                                        "Your account has an invalid role."
                                    )
                                }
                            }

                        } else {

                            // New social accounts are always regular users.
                            val userData = hashMapOf(
                                "uid" to user.uid,
                                "email" to (user.email ?: ""),
                                "name" to (user.displayName ?: ""),
                                "role" to "user",
                                "provider" to provider
                            )

                            userRef.set(userData)

                                .addOnSuccessListener {

                                    if (!isFinishing && !isDestroyed) {
                                        openCustomerHome()
                                    }
                                }

                                .addOnFailureListener { error ->

                                    Log.e(
                                        "SocialLogin",
                                        "Could not create user profile",
                                        error
                                    )

                                    auth.signOut()

                                    showMessage(
                                        "Signed in, but your profile could not be created. Check Firestore permissions."
                                    )
                                }
                        }
                    }

                    .addOnFailureListener { error ->

                        Log.e(
                            "SocialLogin",
                            "Could not retrieve user profile",
                            error
                        )

                        auth.signOut()

                        showMessage(
                            "Unable to verify your account. Please try again."
                        )
                    }
            }

            .addOnFailureListener { error ->

                Log.e(
                    "SocialLogin",
                    "$provider authentication failed",
                    error
                )

                showMessage(
                    "$provider sign-in failed: ${friendlyAuthError(error)}"
                )
            }
    }

    private fun friendlyAuthError(error: Exception): String {

        val code =
            (error as? FirebaseAuthException)?.errorCode

        return when (code) {

            "ERROR_INVALID_EMAIL" ->
                "The email address is invalid."

            "ERROR_WRONG_PASSWORD",
            "ERROR_INVALID_CREDENTIAL" ->
                "Email or password is incorrect."

            "ERROR_USER_NOT_FOUND" ->
                "No account exists for that email."

            "ERROR_USER_DISABLED" ->
                "This account has been disabled."

            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait and try again."

            "ERROR_OPERATION_NOT_ALLOWED" ->
                "This sign-in method is not enabled in Firebase."

            else ->
                error.localizedMessage
                    ?: "Sign-in failed. Please try again."
        }
    }

    private fun openOwnerDashboard() {
        openAndFinish(OwnerDashboard::class.java)
    }

    private fun openCustomerHome() {
        openAndFinish(HomeActivity::class.java)
    }

    private fun openAndFinish(destination: Class<*>) {

        val intent = Intent(this, destination).apply {

            flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }

    private fun rejectLogin(
        originalText: String,
        message: String
    ) {

        auth.signOut()

        btnLogin.isEnabled = true
        btnLogin.text = originalText

        showMessage(message)
    }

    private fun resetPassword() {

        val email = etEmail.text.toString().trim()

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            etEmail.error = "Enter a valid email address first."
            etEmail.requestFocus()

            return
        }

        tvForgotPassword.isEnabled = false

        auth.sendPasswordResetEmail(email)

            .addOnCompleteListener {
                tvForgotPassword.isEnabled = true
            }

            .addOnSuccessListener {

                showMessage(
                    "Password reset instructions have been sent."
                )
            }

            .addOnFailureListener { error ->

                Log.e(
                    "PasswordReset",
                    "Could not send reset email",
                    error
                )

                showMessage(
                    "Unable to send the password reset email."
                )
            }
    }

    private fun setupTextWatchers() {

        etEmail.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                etEmail.error = null
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        etPassword.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                etPassword.error = null
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun validateInput(
        email: String,
        password: String
    ): Boolean {

        var valid = true

        if (email.isEmpty()) {

            etEmail.error = "Email address is required."
            valid = false

        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

            etEmail.error = "Enter a valid email address."
            valid = false
        }

        if (password.isEmpty()) {

            etPassword.error = "Password is required."
            valid = false
        }

        return valid
    }

    private fun showMessage(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_LONG
        ).show()
    }
}
