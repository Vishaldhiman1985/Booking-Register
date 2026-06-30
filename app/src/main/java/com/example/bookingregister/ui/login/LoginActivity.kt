package com.example.bookingregister.ui.login

import android.content.Intent
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Patterns
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.R
import com.example.bookingregister.account.domain.BackendAccessManager
import com.example.bookingregister.ui.booking.BookingChartActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val accessManager = BackendAccessManager()
    private val accessPrefs by lazy {
        getSharedPreferences("cached_hotel_access", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        val currentUser = auth.currentUser
        if (currentUser != null) {
            val cachedHotelId = accessPrefs.getString(KEY_HOTEL_ID, null)
            if (currentUser.isEmailVerified && !cachedHotelId.isNullOrBlank()) {
                goToMain(cachedHotelId)
                return
            }

            showLoadingScreen("Opening your hotel...")
            currentUser.reload()
                .addOnSuccessListener {
                    if (currentUser.isEmailVerified) {
                        checkAccessAndOpen()
                    } else {
                        auth.signOut()
                        showLoginForm("Please verify your email before login.")
                    }
                }
                .addOnFailureListener {
                    auth.signOut()
                    showLoginForm(null)
                }
            return
        }

        showLoginForm(null)
    }

    private fun showLoadingScreen(message: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.WHITE)
        }

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
        }

        val title = TextView(this).apply {
            text = message
            textSize = 17f
            setTextColor(Color.parseColor("#242424"))
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 0)
        }

        val subtitle = TextView(this).apply {
            text = "Please wait while we prepare your account."
            textSize = 13f
            setTextColor(Color.parseColor("#777777"))
            gravity = Gravity.CENTER
            setPadding(0, 6, 0, 0)
        }

        root.addView(progress)
        root.addView(title)
        root.addView(subtitle)
        setContentView(root)
    }
    private fun showLoginForm(message: String?) {
        setContentView(R.layout.activity_login)

        message?.let {
            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
        }

        val email = findViewById<EditText>(R.id.etEmail)
        val password = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val tvSignup = findViewById<TextView>(R.id.tvGoToSignup)

        tvSignup.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            val e = email.text.toString().trim()
            if (e.isEmpty()) {
                Toast.makeText(this, "Enter your registered email first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvForgotPassword.isEnabled = false
            auth.sendPasswordResetEmail(e)
                .addOnSuccessListener {
                    tvForgotPassword.isEnabled = true
                    Toast.makeText(
                        this,
                        "Password reset link sent. Please check your email.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnFailureListener {
                    tvForgotPassword.isEnabled = true
                    Toast.makeText(
                        this,
                        it.message ?: "Could not send password reset email.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }

        btnLogin.setOnClickListener {
            val e = email.text.toString().trim()
            val p = password.text.toString().trim()

            if (e.isEmpty() || p.isEmpty()) {
                Toast.makeText(this, "Enter email & password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(e)) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false

            auth.signInWithEmailAndPassword(e, p)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        btnLogin.isEnabled = true
                        Toast.makeText(this, "Login failed. Please try again.", Toast.LENGTH_LONG).show()
                        return@addOnSuccessListener
                    }

                    user.reload()
                        .addOnSuccessListener {
                            btnLogin.isEnabled = true
                            if (user.isEmailVerified) {
                                checkAccessAndOpen()
                            } else {
                                sendVerificationAndSignOut(user)
                            }
                        }
                        .addOnFailureListener {
                            btnLogin.isEnabled = true
                            Toast.makeText(this, "Could not verify account status. Please try again.", Toast.LENGTH_LONG).show()
                        }
                }
                .addOnFailureListener {
                    btnLogin.isEnabled = true
                    Toast.makeText(
                        this,
                        "Invalid login. Please contact hotel admin.",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun sendVerificationAndSignOut(user: FirebaseUser) {
        user.sendEmailVerification()
            .addOnCompleteListener {
                auth.signOut()
                Toast.makeText(
                    this,
                    "Email is not verified. We sent a fresh verification link.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun goToMain(hotelId: String) {
        startActivity(Intent(this, BookingChartActivity::class.java).apply {
            putExtra(BookingChartActivity.EXTRA_HOTEL_REMOTE_ID, hotelId)
        })
        finish()
    }

    private fun checkAccessAndOpen() {
        lifecycleScope.launch {
            runCatching { accessManager.getMyAccess() }
                .onSuccess { access ->
                    val hotelId = access.hotelId
                    if (access.allowed && !hotelId.isNullOrBlank()) {
                        cacheAccess(hotelId)
                        auth.currentUser?.getIdToken(false)
                        goToMain(hotelId)
                    } else {
                        clearCachedAccess()
                        auth.signOut()
                        showLoginForm(access.blockedMessage())
                    }
                }
                .onFailure {
                    showAccessError(it.message)
                }
        }
    }

    private fun showAccessError(message: String?) {
        clearCachedAccess()
        auth.signOut()
        showLoginForm(message ?: "Could not check account access. Please try again.")
    }

    private fun cacheAccess(hotelId: String) {
        accessPrefs.edit()
            .putString(KEY_HOTEL_ID, hotelId)
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearCachedAccess() {
        accessPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_HOTEL_ID = "hotel_id"
        private const val KEY_CACHED_AT = "cached_at"
    }
}
