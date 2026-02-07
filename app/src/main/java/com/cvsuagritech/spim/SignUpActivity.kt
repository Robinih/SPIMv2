package com.cvsuagritech.spim

import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cvsuagritech.spim.api.RegisterRequest
import com.cvsuagritech.spim.api.RetrofitClient
import com.cvsuagritech.spim.databinding.ActivitySignUpBinding
import com.cvsuagritech.spim.utils.ThemeManager
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    private val naicBarangays = arrayOf(
        "Bagong Karsada", "Balsahan", "Bancaan", "Bucana Malaki", "Bucana Sasahan",
        "Calubcob", "Capt. C. Nazareno (Poblacion)", "Gomez-Zamora (Poblacion)",
        "Halang", "Humbac", "Ibayo Estacion", "Ibayo Silangan", "Kanluran",
        "Labac", "Latoria", "Mabulo", "Makina", "Malainen Bago", "Malainen Luma",
        "Molino", "Munting Mapino", "Muzon", "Palangue 1 (Central)", "Palangue 2 & 3",
        "Sabang", "San Roque", "Santulan", "Sapa", "Timalan Balsahan", "Timalan Concepcion"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.initializeTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBarangayDropdown()
        setupClickListeners()
        setupLoginText()
        setupAutoScroll()
    }

    private fun setupAutoScroll() {
        // We only keep auto-scroll for fields likely to be hidden by the keyboard (bottom fields)
        binding.etUsername.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollTask(binding.tilUsername.top)
        }
        binding.etPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollTask(binding.tilPassword.top)
        }
        binding.etConfirmPassword.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollTask(binding.tilConfirmPassword.top)
        }
    }

    private fun scrollTask(y: Int) {
        binding.scrollView.postDelayed({
            binding.scrollView.smoothScrollTo(0, y - 50)
        }, 300)
    }

    private fun setupBarangayDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, naicBarangays)
        binding.actBarangay.setAdapter(adapter)
    }

    private fun setupClickListeners() {
        binding.btnSignUp.setOnClickListener {
            if (validateInputs()) {
                performRegistration()
            }
        }
    }

    private fun performRegistration() {
        val fullName = binding.etFullName.text.toString().trim()
        val municipality = "Naic"
        val barangay = binding.actBarangay.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.btnSignUp.isEnabled = false

        val request = RegisterRequest(
            username = username,
            fullName = fullName,
            password = password,
            municipality = municipality,
            streetBarangay = barangay
        )

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.instance.register(request)
                
                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(this@SignUpActivity, response.body()!!.message, Toast.LENGTH_LONG).show()
                    
                    // Navigate to Login after successful registration
                    val intent = Intent(this@SignUpActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@SignUpActivity, "Registration failed: ${response.message()}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SignUpActivity, "Network error: Could not connect to server", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.visibility = View.GONE
                binding.btnSignUp.isEnabled = true
            }
        }
    }

    private fun setupLoginText() {
        val fullText = getString(R.string.signup_already_account)
        val spannableString = SpannableString(fullText)
        
        val linkText = if (fullText.contains("Login")) "Login" else "Mag-Login"
        
        val loginClickable = object : ClickableSpan() {
            override fun onClick(widget: View) {
                finish()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
                ds.color = ContextCompat.getColor(this@SignUpActivity, R.color.primary_green)
            }
        }

        val start = fullText.indexOf(linkText)
        if (start != -1) {
            val end = start + linkText.length
            spannableString.setSpan(loginClickable, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.tvLogin.text = spannableString
        binding.tvLogin.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun validateInputs(): Boolean {
        var isValid = true
        
        val fullName = binding.etFullName.text.toString().trim()
        val barangay = binding.actBarangay.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.tilFullName.error = getString(R.string.error_name_required)
            isValid = false
        } else {
            binding.tilFullName.error = null
        }

        if (barangay.isEmpty()) {
            binding.tilStreetBarangay.error = "Barangay is required"
            isValid = false
        } else {
            binding.tilStreetBarangay.error = null
        }

        if (username.isEmpty()) {
            binding.tilUsername.error = getString(R.string.error_username_required)
            isValid = false
        } else {
            binding.tilUsername.error = null
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            isValid = false
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            isValid = false
        } else {
            binding.tilPassword.error = null
        }

        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = getString(R.string.error_confirm_password_required)
            isValid = false
        } else if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_mismatch)
            isValid = false
        } else {
            binding.tilConfirmPassword.error = null
        }

        return isValid
    }
}
