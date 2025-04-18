package com.example.chat_application

import android.annotation.SuppressLint
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
import java.util.concurrent.TimeUnit

class AuthActivity : AppCompatActivity() {
    // Firebase Auth
    private lateinit var auth: FirebaseAuth
    private var storedVerificationId: String? = ""
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken
    private lateinit var callbacks: PhoneAuthProvider.OnVerificationStateChangedCallbacks

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

        // Initialize phone auth callbacks
        initializePhoneAuthCallbacks()
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

            // Start phone verification
            startPhoneNumberVerification(formattedPhone)

            // Switch to OTP view
            setContentView(R.layout.otp_confirmation)
            currentView = "OTP"
            setupOtpView()
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

            // Start phone verification
            startPhoneNumberVerification(userPhone)

            // Switch to OTP view
            setContentView(R.layout.otp_confirmation)
            currentView = "OTP"
            setupOtpView()
        }

        // Switch to login
        switchLayout.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = "LOGIN"
            setupLoginView()
        }
    }

    private fun setupOtpView() {
        val otp = findViewById<EditText>(R.id.otp)
        val otpConfirmation = findViewById<Button>(R.id.confirm_otp)
        val resendOtpButton = findViewById<TextView>(R.id.resend_otp)

        otpConfirmation.setOnClickListener {
            val code = otp.text.toString().trim()

            if (code.isEmpty()) {
                Toast.makeText(this, "Please enter the verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (storedVerificationId != null && storedVerificationId!!.isNotEmpty()) {
                verifyPhoneNumberWithCode(storedVerificationId, code)
            } else {
                Toast.makeText(this, "Verification ID not received. Try again.", Toast.LENGTH_SHORT).show()
            }
        }

        // Optional: Add resend OTP functionality
        resendOtpButton?.setOnClickListener {
            if (::resendToken.isInitialized) {
                resendVerificationCode(userPhone, resendToken)
                Toast.makeText(this, "Verification code resent", Toast.LENGTH_SHORT).show()
            } else {
                startPhoneNumberVerification(userPhone)
            }
        }
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        // Format phone number to E.164 format if needed
        // This is just a simple example - adjust to your requirements
        var formattedNumber = phoneNumber

        // If doesn't start with +, assume it's a local number and add country code
        if (!formattedNumber.startsWith("+")) {
            formattedNumber = "+1$formattedNumber" // Assuming US (+1), change as needed
        }

        return formattedNumber
    }

    private fun initializePhoneAuthCallbacks() {
        callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted:$credential")

                // This can happen in two situations:
                // 1. Instant verification without SMS code
                // 2. Auto-retrieval of SMS code
                Toast.makeText(applicationContext, "Verification completed automatically", Toast.LENGTH_SHORT).show()
                signInWithPhoneAuthCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "onVerificationFailed", e)

                val errorMessage = when (e) {
                    is FirebaseAuthInvalidCredentialsException -> "Invalid phone number format"
                    is FirebaseTooManyRequestsException -> "Too many verification attempts. Try again later"
                    is FirebaseAuthMissingActivityForRecaptchaException -> "reCAPTCHA verification failed"
                    else -> "Verification failed: ${e.message}"
                }

                Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_LONG).show()

                // Return to previous screen
                when (currentView) {
                    "SIGNUP" -> {
                        setContentView(R.layout.signup_page)
                        setupSignupView()
                    }
                    "LOGIN" -> {
                        setContentView(R.layout.login_page)
                        setupLoginView()
                    }
                    else -> {
                        setContentView(R.layout.authentication_page)
                        setupAuthView()
                    }
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent:$verificationId")

                // Save verification ID and resending token
                storedVerificationId = verificationId
                resendToken = token

                Toast.makeText(applicationContext, "Verification code sent", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        Toast.makeText(this, "Sending verification code to $phoneNumber", Toast.LENGTH_SHORT).show()

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyPhoneNumberWithCode(verificationId: String?, code: String) {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
            signInWithPhoneAuthCredential(credential)
        } catch (e: Exception) {
            Toast.makeText(this, "Verification failed: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error verifying code", e)
        }
    }

    private fun resendVerificationCode(
        phoneNumber: String,
        token: PhoneAuthProvider.ForceResendingToken
    ) {
        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setForceResendingToken(token)

        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
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
        // In a real app, you would store the user name and other data in Firebase Database or Firestore
        // This is just a simplified example

        // Create user profile in your database
        // ...

        // For example, you might use Firebase Realtime Database:
        // val database = Firebase.database.reference
        // database.child("users").child(user.uid).setValue(UserProfile(userName, userPhone))

        // After creating profile, navigate to main activity
        Toast.makeText(this, "Account created successfully", Toast.LENGTH_SHORT).show()
        navigateToMainActivity()
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close AuthActivity so user can't go back
    }

    override fun onBackPressed() {
        when (currentView) {
            "LOGIN", "SIGNUP" -> {
                // Go back to auth selection
                setContentView(R.layout.authentication_page)
                currentView = "AUTH"
                setupAuthView()
            }
            "OTP" -> {
                // Go back to previous screen
                if (userName.isNotEmpty()) {
                    // Was signing up
                    setContentView(R.layout.signup_page)
                    currentView = "SIGNUP"
                    setupSignupView()
                } else {
                    // Was logging in
                    setContentView(R.layout.login_page)
                    currentView = "LOGIN"
                    setupLoginView()
                }
            }
            else -> super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "AuthActivity"
    }
}