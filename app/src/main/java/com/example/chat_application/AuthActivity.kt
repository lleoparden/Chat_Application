package com.example.chat_application

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity : AppCompatActivity() {
    // Current view state tracking
    private var currentView = "AUTH" // AUTH, LOGIN, SIGNUP

    // User data storage for registration process
    private var userName: String = ""
    private var userPhone: String = ""
    private var userPassword: String = ""

    // Firestore instance
    private lateinit var db: FirebaseFirestore

    // Auth instance
    private lateinit var auth: FirebaseAuth

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firestore
        db = FirebaseFirestore.getInstance()

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Check if user is already logged in
        checkUserLoggedIn()
    }

    private fun checkUserLoggedIn() {
        // Check shared preferences for user ID
        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
        val userId = prefs.getString("userId", null)

        if (userId != null) {
            // User ID exists, verify it in Firestore
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        // User exists and is logged in, navigate to MainActivity
                        navigateToMainActivity()
                    } else {
                        // User ID in preferences but not in Firestore, clear and show auth
                        prefs.edit().remove("userId").apply()
                        showAuthView()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error checking user login status", e)
                    showAuthView()
                }
        } else {
            // No user ID stored, show authentication view
            showAuthView()
        }
    }

    private fun showAuthView() {
        // Set initial view
        setContentView(R.layout.authentication_page)
        setupAuthView()
    }

    private fun setupAuthView() {
        val toSignInPage = findViewById<Button>(R.id.goToLogin)
        val toSignUpPage = findViewById<Button>(R.id.goToSignup)

        toSignInPage.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = "LOGIN"
            setupLoginView()
        }

        toSignUpPage.setOnClickListener {
            setContentView(R.layout.signup_page)
            currentView = "SIGNUP"
            setupSignupView()
        }
    }

    private fun setupLoginView() {
        val number = findViewById<EditText>(R.id.signInPhone)
        val password = findViewById<EditText>(R.id.signInPassword)
        val logInButton = findViewById<Button>(R.id.logInButton)
        val switchLayout = findViewById<TextView>(R.id.switchSignInButton)

        logInButton.setOnClickListener {
            val phoneNumber = number.text.toString().trim()
            val userPassword = password.text.toString().trim()

            if (phoneNumber.isEmpty() || userPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Format phone number if needed
            val formattedPhone = formatPhoneNumber(phoneNumber)
            userPhone = formattedPhone
            this.userPassword = userPassword

            // Check if user exists and verify password
            validateLogin(formattedPhone, userPassword)
        }

        // Switch to signup
        switchLayout.setOnClickListener {
            setContentView(R.layout.signup_page)
            currentView = "SIGNUP"
            setupSignupView()
        }
    }

    private fun setupSignupView() {
        val name = findViewById<EditText>(R.id.signUpName)
        val number = findViewById<EditText>(R.id.signInPhone)
        val password = findViewById<EditText>(R.id.signInPassword)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val switchLayout = findViewById<TextView>(R.id.switchSignInButton)

        signUpButton.setOnClickListener {
            val nameText = name.text.toString().trim()
            val phoneNumber = number.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (nameText.isEmpty() || phoneNumber.isEmpty() || passwordText.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Store user data for registration
            userName = nameText
            userPhone = formatPhoneNumber(phoneNumber)
            userPassword = passwordText

            // Check if user already exists with this phone number
            checkUserExists(userPhone)
        }

        // Switch to login
        switchLayout.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = "LOGIN"
            setupLoginView()
        }
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        // This is just a simple example - adjust to your requirements
        var formattedNumber = phoneNumber

        // If doesn't start with +, assume it's a local number and add country code
        if (!formattedNumber.startsWith("+")) {
            formattedNumber = "+1$formattedNumber" // Assuming US (+1), change as needed
        }

        return formattedNumber
    }

    private fun checkUserExists(phoneNumber: String) {
        // Query Firestore to check if user with this phone number already exists
        db.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // User with this phone number already exists
                    Toast.makeText(this@AuthActivity,
                        "An account with this phone number already exists",
                        Toast.LENGTH_SHORT).show()
                } else {
                    // No user with this phone number, proceed with registration
                    registerUser()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking if user exists", e)
                Toast.makeText(this@AuthActivity,
                    "Database error: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun registerUser() {
        // Generate a unique ID for the user or use Firebase Auth
        val userId = db.collection("users").document().id

        // Create user profile
        val userProfile = hashMapOf(
            "uid" to userId,
            "displayName" to userName,
            "phoneNumber" to userPhone,
            "password" to userPassword
        )

        // Save user profile to Firestore
        db.collection("users").document(userId)
            .set(userProfile)
            .addOnSuccessListener {
                // Profile created successfully
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()

                // Store the user ID locally for session management
                val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
                prefs.edit().putString("userId", userId).apply()

                navigateToMainActivity()
            }
            .addOnFailureListener { e ->
                // Handle failure
                Toast.makeText(this, "Failed to create profile: ${e.message}",
                    Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error creating user profile", e)
            }
    }

    private fun validateLogin(phoneNumber: String, password: String) {
        // Query Firestore to find user with this phone number
        db.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // User found, check password
                    var isPasswordCorrect = false
                    var userId = ""

                    for (document in documents) {
                        val userPassword = document.getString("password")
                        if (userPassword == password) {
                            isPasswordCorrect = true
                            userId = document.getString("uid") ?: document.id
                            break
                        }
                    }

                    if (isPasswordCorrect) {
                        // Password correct, login successful
                        Toast.makeText(this@AuthActivity,
                            "Login successful",
                            Toast.LENGTH_SHORT).show()

                        // Store the user ID locally for session management
                        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
                        prefs.edit().putString("userId", userId).apply()

                        navigateToMainActivity()
                    } else {
                        // Password incorrect
                        Toast.makeText(this@AuthActivity,
                            "Incorrect password",
                            Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // No user with this phone number
                    Toast.makeText(this@AuthActivity,
                        "No account found with this phone number",
                        Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error validating login", e)
                Toast.makeText(this@AuthActivity,
                    "Database error: ${e.message}",
                    Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close AuthActivity so user can't go back
    }
}