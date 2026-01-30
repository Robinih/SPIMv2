package com.cvsuagritech.spim

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.api.LoginRequest
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.ActivityLoginBinding
import com.cvsuagritech.spim.utils.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        setupClickListeners()
        setupSignUpText()
    }

    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            if (validateInputs()) {
                performLogin()
            }
        }
    }

    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnLogin.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.login(LoginRequest(username, password))
                
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    
                    // Save session with User ID from website
                    sessionManager.setLogin(true, username, loginResponse.userId)

                    Toast.makeText(this@LoginActivity, loginResponse.message, Toast.LENGTH_SHORT).show()

                    // Navigate to Main Navigation Activity
                    val intent = Intent(this@LoginActivity, MainNavActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // Handle error response (e.g., 401 Unauthorized)
                    val errorMsg = if (response.code() == 401) "Invalid username or password" else "Login failed"
                    Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Handle network errors
                Toast.makeText(this@LoginActivity, "Network error: Could not connect to server", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnLogin.isEnabled = true
            }
        }
    }

    private fun setupSignUpText() {
        val fullText = getString(R.string.login_no_account)
        val spannableString = SpannableString(fullText)
        
        // Find the clickable part: "Sign Up" in English, "Mag-Sign Up" in Tagalog
        val linkText = if (fullText.contains("Sign Up")) "Sign Up" else "Mag-Sign Up"
        
        val signUpClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@LoginActivity, SignUpActivity::class.java)
                startActivity(intent)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
                ds.color = ContextCompat.getColor(this@LoginActivity, R.color.primary_green)
            }
        }

        val start = fullText.indexOf(linkText)
        if (start != -1) {
            val end = start + linkText.length
            spannableString.setSpan(signUpClickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.tvSignUp.text = spannableString
        binding.tvSignUp.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (username.isEmpty()) {
            binding.tilUsername.error = "Username is required"
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        return isValid
    }
}
