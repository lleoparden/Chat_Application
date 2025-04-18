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
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthMissingActivityForRecaptchaException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.database.FirebaseDatabase
import java.util.concurrent.TimeUnit

class AuthActivity : AppCompatActivity() {
    // Firebase Auth
    private lateinit var auth: FirebaseAuth

    // Current view state tracking
    private var currentView = "AUTH" // AUTH, LOGIN, SIGNUP, OTP

    // User data storage for registration process
    private var userName: String = ""
    private var userPhone: String = ""
    private var userPassword: String = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase Auth
        auth = Firebase.auth

        // Check if user is already signed in
        if (auth.currentUser != null) {
            // User is already signed in, go to main activity
            navigateToMainActivity()
            return
        }

        // Set initial view
        setContentView(R.layout.authentication_page)
        setupAuthView()

    }

    private fun setupAuthView() {
        val toSigninPage = findViewById<Button>(R.id.goToLogin)
        val toSignUpPage = findViewById<Button>(R.id.goToSignup)

        toSigninPage.setOnClickListener {
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

            // Store user data for registration after verification
            userName = nameText
            userPhone = formatPhoneNumber(phoneNumber)
            userPassword = passwordText

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


    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")

                    // Update user profile info if this is a signup
                    val user = task.result?.user
                    if (currentView == "SIGNUP" && user != null) {
                        updateUserProfile(user)
                    } else {
                        navigateToMainActivity()
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)

                    val errorMessage = if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        "Invalid verification code"
                    } else {
                        "Authentication failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun updateUserProfile(user: FirebaseUser) {
        // Get reference to Firebase Realtime Database
        val database = FirebaseDatabase.getInstance().reference

        // Get user information from FirebaseUser object
        val uid = user.uid
        val email = user.email ?: ""
        val displayName = user.displayName ?: ""

        // Create a UserProfile object
        val userProfile = UserProfile(
            uid = uid,
            email = email,
            displayName = displayName,
        )

        // Save user profile to Firebase Realtime Database
        database.child("users").child(uid).setValue(userProfile)
            .addOnSuccessListener {
                // Profile updated successfully
                Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
                navigateToMainActivity()
            }
            .addOnFailureListener { e ->
                // Handle failure
                Toast.makeText(this, "Failed to create profile: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error creating user profile", e)
            }
    }

    // User profile data class
    data class UserProfile(
        val uid: String = "",
        val email: String = "",
        val displayName: String = "",
        val phoneNumber: String = "",
        val isProfileComplete: Boolean = false
    )

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close AuthActivity so user can't go back
    }




}