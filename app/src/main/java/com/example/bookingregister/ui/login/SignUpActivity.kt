package com.example.bookingregister.ui.login

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.bookingregister.R
import com.example.bookingregister.account.domain.BackendAccessManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val accessManager = BackendAccessManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        setContentView(R.layout.activity_signup)

        val email = findViewById<EditText>(R.id.etSignupEmail)
        val password = findViewById<EditText>(R.id.etSignupPassword)
        val confirmPassword = findViewById<EditText>(R.id.etSignupConfirmPassword)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val tvLogin = findViewById<TextView>(R.id.tvGoToLogin)

        btnSignup.setOnClickListener {
            val e = email.text.toString().trim()
            val p = password.text.toString().trim()
            val cp = confirmPassword.text.toString().trim()

            if (e.isEmpty() || p.isEmpty() || cp.isEmpty()) {
                Toast.makeText(this, "Enter all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(e)) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (p.length < 8) {
                Toast.makeText(this, "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (p != cp) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignup.isEnabled = false

            auth.createUserWithEmailAndPassword(e, p)
                .addOnSuccessListener { result ->
                    val user = result.user

                    if (user == null) {
                        btnSignup.isEnabled = true
                        Toast.makeText(
                            this,
                            "Account created. Please login again.",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                        return@addOnSuccessListener
                    }

                    lifecycleScope.launch {
                        runCatching {
                            accessManager.bootstrapOwner()
                            user.sendEmailVerification().await()
                        }
                            .onSuccess {
                                btnSignup.isEnabled = true
                                auth.signOut()

                                Toast.makeText(
                                    this@SignUpActivity,
                                    "Verification email sent. Please verify your email, then login.",
                                    Toast.LENGTH_LONG
                                ).show()

                                startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                                finish()
                            }
                            .onFailure {
                                btnSignup.isEnabled = true
                                auth.signOut()

                                Toast.makeText(
                                    this@SignUpActivity,
                                    it.message ?: "Account created, but setup could not finish. Please try login.",
                                    Toast.LENGTH_LONG
                                ).show()

                                startActivity(Intent(this@SignUpActivity, LoginActivity::class.java))
                                finish()
                            }
                        }
                }
                .addOnFailureListener {
                    btnSignup.isEnabled = true
                    Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                }
        }

        tvLogin.setOnClickListener {
            finish()
        }
    }
    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
