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
import android.os.Build
import com.example.bookingregister.account.domain.DeviceInstallationId
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctionsException
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
                .addOnFailureListener { error ->
                    val cachedHotelId =
                        if (error is FirebaseNetworkException) {
                            cachedHotelIdForCurrentDevice(currentUser)
                        } else {
                            null
                        }

                    if (!cachedHotelId.isNullOrBlank()) {
                        goToMain(cachedHotelId)
                    } else {
                        auth.signOut()
                        showLoginForm("Could not verify account status. Please try again.")
                    }
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
            try {
                val access = accessManager.getMyAccess()
                val hotelId = access.hotelId

                if (!access.allowed || hotelId.isNullOrBlank()) {
                    clearCachedAccess()
                    auth.signOut()
                    showLoginForm(access.blockedMessage())
                    return@launch
                }

                val deviceId = DeviceInstallationId.get(this@LoginActivity)

                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                    .trim()
                    .ifBlank { "Android device" }

                val claim = accessManager.claimMyDevice(
                    deviceId = deviceId,
                    deviceName = deviceName
                )

                if (!claim.allowed) {
                    clearCachedAccess()
                    auth.signOut()

                    val message = when (claim.reason) {
                        "DEVICE_ALREADY_ACTIVE" ->
                            "This user is already active on another device."

                        "NO_ACTIVE_MEMBERSHIP" ->
                            "This login is not connected to an active hotel account."

                        else ->
                            "This device could not be activated. Please contact support."
                    }

                    showLoginForm(message)
                    return@launch
                }

                val claimedHotelId = claim.hotelId

                if (claimedHotelId.isNullOrBlank() || claimedHotelId != hotelId) {
                    showAccessError(
                        "Could not verify this device for the hotel account."
                    )
                    return@launch
                }

                val uid = auth.currentUser?.uid
                if (uid.isNullOrBlank()) {
                    showAccessError("Please login again.")
                    return@launch
                }

                cacheAccess(
                    hotelId = hotelId,
                    deviceId = deviceId,
                    uid = uid
                )
                auth.currentUser?.getIdToken(false)
                goToMain(hotelId)
            } catch (error: Exception) {
                val user = auth.currentUser

                val cachedHotelId =
                    if (user != null && isTemporaryNetworkFailure(error)) {
                        cachedHotelIdForCurrentDevice(user)
                    } else {
                        null
                    }

                if (!cachedHotelId.isNullOrBlank()) {
                    goToMain(cachedHotelId)
                } else {
                    showAccessError(error.message)
                }
            }
        }
    }

    private fun cachedHotelIdForCurrentDevice(user: FirebaseUser): String? {
        if (!user.isEmailVerified) {
            return null
        }

        val cachedHotelId = accessPrefs.getString(KEY_HOTEL_ID, null)
        val verifiedUid = accessPrefs.getString(KEY_VERIFIED_UID, null)
        val verifiedDeviceId = accessPrefs.getString(KEY_VERIFIED_DEVICE_ID, null)
        val installationId = DeviceInstallationId.get(this)

        return cachedHotelId?.takeIf {
            it.isNotBlank() &&
                    verifiedUid == user.uid &&
                    verifiedDeviceId == installationId
        }
    }

    private fun isTemporaryNetworkFailure(error: Exception): Boolean {
        if (error is FirebaseNetworkException) {
            return true
        }

        val functionsError = error as? FirebaseFunctionsException
            ?: return false

        return functionsError.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                functionsError.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED
    }
    private fun showAccessError(message: String?) {
        clearCachedAccess()
        auth.signOut()
        showLoginForm(message ?: "Could not check account access. Please try again.")
    }

    private fun cacheAccess(
        hotelId: String,
        deviceId: String,
        uid: String
    ) {
        accessPrefs.edit()
            .putString(KEY_HOTEL_ID, hotelId)
            .putString(KEY_VERIFIED_DEVICE_ID, deviceId)
            .putString(KEY_VERIFIED_UID, uid)
            .putLong(KEY_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearCachedAccess() {
        accessPrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_VERIFIED_DEVICE_ID = "verified_device_id"
        private const val KEY_VERIFIED_UID = "verified_uid"
        private const val KEY_HOTEL_ID = "hotel_id"
        private const val KEY_CACHED_AT = "cached_at"
    }
}
