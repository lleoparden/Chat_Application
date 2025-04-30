package com.example.chat_application

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.animation.Animation
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.chat_application.dataclasses.UserData
import com.example.chat_application.dataclasses.UserSettings
import com.example.chat_application.services.FirebaseService
import com.example.chat_application.services.LocalStorageService
import com.hbb20.CountryCodePicker

class AuthActivity : AppCompatActivity() {
    // Current view state tracking
    private var currentView = "AUTH" // AUTH, LOGIN, SIGNUP
    private val TAG = "AUTHActivity"

    // User data storage for registration process
    private var userName: String = ""
    private var userPhone: String = ""
    private var userPassword: String = ""

    //Booleans
    public var animationFlag: Boolean = false

    //animation variables
    private lateinit var slideDownAnim : Animation
    private lateinit var slideUpAnim : Animation
    private lateinit var slideUpFastAnim : Animation
    private lateinit var slideRightAnim : Animation
    private lateinit var slideLeftAnim : Animation
    private lateinit var slideUpLateAnim : Animation
    private lateinit var slideDownLateAnim : Animation
    private lateinit var fadeInAnim : Animation
    private lateinit var slideLeftOutAnim : Animation
    private lateinit var slideRightOutAnim : Animation
    private lateinit var fadeInFastAnim : Animation

    //background
    private lateinit var topleftbg: ImageView
    private lateinit var bottomleftbg: ImageView
    private lateinit var bottomrightbg: ImageView
    private lateinit var toprightbg: ImageView
    private lateinit var ballbottomleft: ImageView
    private lateinit var ballbottomright: ImageView
    private lateinit var balltopright: ImageView
    private lateinit var balltopleft: ImageView

    var users = mutableListOf<UserData>()


    private val firebaseEnabled by lazy { resources.getBoolean(R.bool.firebaseOn) }

    private lateinit var contactManager: ContactManager


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.getThemeResource())
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize services
        LocalStorageService.initialize(applicationContext,TAG)

        if (firebaseEnabled) {
            FirebaseService.initialize(this,TAG,firebaseEnabled)
        }

        contactManager = ContactManager(this)
        contactManager.checkAndRequestContactsPermission(this)
        loadAndProcessUsers()

        //Initialize Animations
        initializeAnimations()

        // Check if user is already logged in
        checkUserLoggedIn()
    }

    private fun initializeAnimations() {
        slideDownAnim = AnimationUtils.loadAnimation(this, R.anim.slidedown)
        slideUpAnim = AnimationUtils.loadAnimation(this, R.anim.slideup)
        slideUpFastAnim = AnimationUtils.loadAnimation(this, R.anim.slideupfast)
        slideRightAnim = AnimationUtils.loadAnimation(this, R.anim.slideright)
        slideLeftAnim = AnimationUtils.loadAnimation(this, R.anim.slideleft)
        slideUpLateAnim = AnimationUtils.loadAnimation(this, R.anim.slideuplate)
        slideDownLateAnim = AnimationUtils.loadAnimation(this, R.anim.slidedownlate)
        fadeInAnim = AnimationUtils.loadAnimation(this, R.anim.fadein)
        fadeInFastAnim = AnimationUtils.loadAnimation(this, R.anim.fadeinfast)
        slideRightOutAnim = AnimationUtils.loadAnimation(this, R.anim.slideoutright)
        slideLeftOutAnim = AnimationUtils.loadAnimation(this, R.anim.slideoutleft)
    }

    private fun initializeBackGround() {
        topleftbg = findViewById(R.id.topleftbg)
        bottomleftbg = findViewById(R.id.bottomleftbg)
        bottomrightbg = findViewById(R.id.bottomrightbg)
        toprightbg = findViewById(R.id.toprightbg)
        ballbottomleft = findViewById(R.id.ballbottomleft)
        ballbottomright = findViewById(R.id.ballbottomright)
        balltopright = findViewById(R.id.balltopright)
        balltopleft = findViewById(R.id.balltopleft)
    }

    private fun checkUserLoggedIn() {
        // Check for user ID in session
        val userId = LocalStorageService.getUserSession()

        if (userId != null) {
            // Check if the user exists locally
            if (LocalStorageService.checkUserExistsLocally(userId)) {
                Log.d(TAG, "User found locally, logging in")
                navigateToMainActivity(userId)
                return
            }

            // If not found locally, verify in Firestore
            if (firebaseEnabled) {
                FirebaseService.verifyUserInFirestore(userId,
                    onSuccess = { userData ->
                        // Save user data locally for future logins
                        LocalStorageService.saveUserToLocalStorage(userData)
                        navigateToMainActivity(userId)
                    },
                    onFailure = { e ->
                        // User ID in preferences but not in Firestore, clear and show auth
                        LocalStorageService.clearUserSession()
                        showAuthView()
                    }
                )
            } else {
                // Firebase is off but user not found locally
                LocalStorageService.clearUserSession()
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
        //Initialize BG Items
        initializeBackGround()

        animationFlag = false
        bottomrightbg.isVisible = true
        bottomleftbg.isVisible = true
        ballbottomleft.isVisible = true
        ballbottomright.isVisible = true

        val toSignInPage = findViewById<Button>(R.id.goToLogin)
        val toSignUpPage = findViewById<Button>(R.id.goToSignup)
        //background
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val loginQuestion = findViewById<TextView>(R.id.loginQuestion)


        balltopleft.startAnimation(slideDownLateAnim)
        balltopright.startAnimation(slideDownAnim)

        ballbottomleft.startAnimation(slideUpAnim)
        ballbottomright.startAnimation(slideUpLateAnim)

        bottomleftbg.startAnimation(slideRightAnim)
        topleftbg.startAnimation(slideRightAnim)

        bottomrightbg.startAnimation(slideLeftAnim)
        toprightbg.startAnimation(slideLeftAnim)

        welcomeText.startAnimation(fadeInAnim)
        loginQuestion.startAnimation(fadeInAnim)


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
        val loginLayout = findViewById<LinearLayout>(R.id.logInLayout)
        val welcomeBack = findViewById<TextView>(R.id.welcomeBack)

        //Initialize BG Items
        initializeBackGround()

        if (!animationFlag) {
            bottomrightbg.startAnimation(slideRightOutAnim)
            bottomleftbg.startAnimation(slideLeftOutAnim)
            ballbottomleft.startAnimation(slideLeftOutAnim)
            ballbottomright.startAnimation(slideRightOutAnim)
            loginLayout.startAnimation(slideUpFastAnim)
            animationFlag = true
        }
        else {
            number.startAnimation(fadeInFastAnim)
            password.startAnimation(fadeInFastAnim)
            logInButton.startAnimation(fadeInFastAnim)
            switchLayout.startAnimation(fadeInFastAnim)
            welcomeBack.startAnimation(fadeInFastAnim)
            bottomrightbg.isVisible = false
            bottomleftbg.isVisible = false
            ballbottomleft.isVisible = false
            ballbottomright.isVisible = false
        }

        logInButton.setOnClickListener {
            val phoneNumber = number.text.toString().trim()
            val userPassword = password.text.toString().trim()

            if (phoneNumber.isEmpty() || userPassword.isEmpty()) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }

            // Format phone number if needed
            val formattedPhone = formatPhoneNumber(phoneNumber)
            userPhone = formattedPhone
            this.userPassword = userPassword

            // Attempt local login first
            val user = LocalStorageService.validateLoginLocally(formattedPhone, userPassword)
            if (user != null) {
                showToast("Login successful (local)")
                LocalStorageService.saveUserSession(user.uid)
                navigateToMainActivity(user.uid)
                return@setOnClickListener
            }

            // If local login fails, check database
            if (firebaseEnabled) {
                FirebaseService.validateLoginRemote(
                    formattedPhone,
                    userPassword,
                    onSuccess = { userId, displayName ->
                        handleSuccessfulRemoteLogin(userId, displayName, formattedPhone, userPassword)
                    },
                    onIncorrectPassword = {
                        showToast("Incorrect password")
                    },
                    onUserNotFound = {
                        showToast("No account found with this phone number")
                    },
                    onError = { e ->
                        showToast("Database error: ${e.message}")
                    }
                )
            } else {
                showToast("No account found with this phone number")
            }
        }

        // Switch to signup
        switchLayout.setOnClickListener {
            setContentView(R.layout.signup_page)
            currentView = "SIGNUP"
            setupSignupView()
        }
    }

    private fun isValidPassword(password: String): Boolean {
        if (password.length < 8) return false

        val hasUppercase = password.any { it.isUpperCase() }
        val hasSpecialChar = password.any { !it.isLetterOrDigit() }

        return hasUppercase && hasSpecialChar
    }

    private fun validatePassword(password: String, passwordField: EditText) {
        if (password.isEmpty()) {
            passwordField.error = null
            return
        }

        when {
            password.length < 8 -> {
                passwordField.error = "Password must be at least 8 characters"
            }
            !password.any { it.isUpperCase() } -> {
                passwordField.error = "Password must include uppercase letters"
            }
            !password.any { !it.isLetterOrDigit() } -> {
                passwordField.error = "Password must include special characters"
            }
            else -> {
                passwordField.error = null
            }
        }
    }

    private fun setupSignupView() {
        val name = findViewById<EditText>(R.id.signUpName)
        val number = findViewById<EditText>(R.id.signInPhone)
        val password = findViewById<EditText>(R.id.signInPassword)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val switchLayout = findViewById<TextView>(R.id.switchSignInButton)
        val signupLayout = findViewById<LinearLayout>(R.id.signUpLayout)
        val helloThere = findViewById<TextView>(R.id.helloThere)

        password.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePassword(s.toString(), password)
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })


        // Initialize CountryCodePicker
        val ccp = findViewById<CountryCodePicker>(R.id.ccp)
        ccp.registerCarrierNumberEditText(number)
        ccp.setAutoDetectedCountry(true)
        ccp.setNumberAutoFormattingEnabled(true)

        // Add phone number validation listener
        ccp.setPhoneNumberValidityChangeListener { isValidNumber ->
            if (number.text.isNotEmpty() && !isValidNumber) {
                number.error = "Invalid phone number"
            } else {
                number.error = null
            }
        }


        //Initialize BG Items
        initializeBackGround()

        if (!animationFlag) {
            bottomrightbg.startAnimation(slideRightOutAnim)
            bottomleftbg.startAnimation(slideLeftOutAnim)
            ballbottomleft.startAnimation(slideLeftOutAnim)
            ballbottomright.startAnimation(slideRightOutAnim)
            signupLayout.startAnimation(slideUpFastAnim)
            animationFlag = true
        }
        else {
            name.startAnimation(fadeInFastAnim)
            number.startAnimation(fadeInFastAnim)
            password.startAnimation(fadeInFastAnim)
            signUpButton.startAnimation(fadeInFastAnim)
            switchLayout.startAnimation(fadeInFastAnim)
            helloThere.startAnimation(fadeInFastAnim)
            ccp.startAnimation(fadeInFastAnim)
            bottomrightbg.isVisible = false
            bottomleftbg.isVisible = false
            ballbottomleft.isVisible = false
            ballbottomright.isVisible = false
        }

        signUpButton.setOnClickListener {
            val nameText = name.text.toString().trim()
            val phoneNumber = number.text.toString().trim()
            val passwordText = password.text.toString().trim()

            if (nameText.isEmpty() || phoneNumber.isEmpty() || passwordText.isEmpty()) {
                showToast("Please fill all fields")
                return@setOnClickListener
            }
// Check if phone number is valid
            if (!ccp.isValidFullNumber) {
                showToast("Please enter a valid phone number")
                return@setOnClickListener
            }
//check if the password is vaild
            if (!isValidPassword(passwordText)) {
                showToast("Password must be at least 8 characters with uppercase letters and special symbols")
                return@setOnClickListener
            }



            // Store user data for registration
            userName = nameText
            userPhone = formatPhoneNumber(phoneNumber)
            userPassword = passwordText

            // Check if user already exists locally with this phone number
            if (LocalStorageService.checkPhoneExistsLocally(userPhone)) {
                showToast("An account with this phone number already exists locally")
                return@setOnClickListener
            }

            // Check if user exists in the database
            if (firebaseEnabled) {
                FirebaseService.checkUserExistsRemote(
                    userPhone,
                    onExists = {
                        showToast("An account with this phone number already exists")
                    },
                    onNotExists = {
                        registerUser()
                    },
                    onError = { e ->
                        showToast("Database error: ${e.message}")
                    }
                )
            } else {
                // If Firebase is off, just register locally
                registerUser()
            }
        }

        // Switch to login
        switchLayout.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = "LOGIN"
            setupLoginView()
        }
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        // Use the CCP's selected country code if available
        val ccp = findViewById<CountryCodePicker?>(R.id.ccp)
        var formattedNumber = phoneNumber

        // If doesn't start with +, assume it's a local number and add appropriate country code
        if (!formattedNumber.startsWith("+")) {
            if (ccp != null) {
                // Get selected country code from CCP
                val countryCode = ccp.selectedCountryCode
                formattedNumber = "+$countryCode$formattedNumber"
            } else {
                // Fallback to Egypt code if CCP not available
                formattedNumber = "+2$formattedNumber"
            }
        }

        return formattedNumber
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun registerUser() {
        // Generate a unique ID for the user
        val userId = if (firebaseEnabled) {
            FirebaseService.generateUserId()
        } else {
            // Generate a local ID if Firebase is off
            System.currentTimeMillis().toString()
        }

        // Create a UserData object for local storage
        val localUserData = UserData(
            uid = userId,
            displayName = userName,
            phoneNumber = userPhone,
            password = userPassword,
            userDescription = "",
            userStatus = "",
            online = true,
            lastSeen = "",
            profilePictureUrl = ""
        )

        // Save to local storage first
        LocalStorageService.saveUserToLocalStorage(localUserData)

        // Save user session
        LocalStorageService.saveUserSession(userId)

        // Then attempt to save to Firebase
        if (firebaseEnabled) {
            val userData = FirebaseService.createUserData(userId,userName, userPhone, userPassword)
            FirebaseService.saveUserToFirebase(
                userData,
                userId,
                onSuccess = {
                    showToast("Account created successfully")
                    navigateToMainActivity(userId)
                },
                onFailure = { e ->
                    showToast("Failed to create profile on server: ${e.message}")
                    Log.e(TAG, "Error creating user profile", e)
                    // Continue with local account
                    navigateToMainActivity(userId)
                }
            )
        } else {
            showToast("Account created successfully (saved locally)")
            navigateToMainActivity(userId)
        }
    }

    private fun handleSuccessfulRemoteLogin(userId: String, displayName: String, phoneNumber: String, password: String) {
        showToast("Login successful")

        // Save user data locally for future logins
        LocalStorageService.saveUserToLocalStorage(
            UserData(
                uid = userId,
                displayName = displayName,
                phoneNumber = phoneNumber,
                password = password,
                userDescription = "",
                userStatus = "",
                online = true,
                lastSeen = "",
                profilePictureUrl = ""
            )
        )

        // Store the user ID for session management
        LocalStorageService.saveUserSession(userId)

        navigateToMainActivity(userId)
    }

    private fun navigateToMainActivity(userId: String) {
        UserSettings.userId = userId
        UserSettings.loadSettings(this)

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish() // Close AuthActivity so user can't go back
    }

    // Load all users asynchronously
    private fun loadAndProcessUsers() {
        Log.d(TAG, "Starting loadAndProcessUsers operation")
        // Clear any existing data
        users.clear()
        Log.d(TAG, "Users list cleared, now fetching user IDs from Firebase")

        HelperFunctions.getAllUserIds { userIds ->
            if (userIds != null && userIds.isNotEmpty()) {
                Log.d(TAG, "Received ${userIds.size} user IDs from Firebase")

                // Keep track of how many users we've processed
                val totalUsers = userIds.size
                var loadedUsers = 0

                Log.d(TAG, "Beginning to fetch individual user data for $totalUsers users")

                // For each user ID, fetch the user data
                for (userId in userIds) {
                    Log.d(TAG, "Requesting user data for ID: $userId")

                    HelperFunctions.getUserData(userId) { userData ->
                        // Check if we got valid user data
                        if (userData != null) {
                            Log.d(TAG, "Received valid user data for ${userData.displayName} (ID: ${userData.uid})")
                            // Add this user to our list
                            users.add(userData)
                        } else {
                            Log.w(TAG, "Received null user data for ID: $userId")
                        }

                        // Increment our counter
                        loadedUsers++
                        Log.d(TAG, "Processed $loadedUsers/$totalUsers users")

                        // If we've processed all users, now we can process them
                        if (loadedUsers == totalUsers) {
                            Log.d(TAG, "All $totalUsers users loaded, found ${users.size} valid users")

                            // All users are loaded, now process them
                            Log.d(TAG, "Starting contact processing with removeNonContacts=true")
                            val processedUsers = contactManager.processUsersToContact(users, true)
                            Log.d(TAG, "Contact processing complete. Started with ${users.size} users, ended with ${processedUsers.size} matching contacts")

                            Log.d(TAG, "Saving processed users to local storage")
                            LocalStorageService.saveContactsAsUserToLocalStorage(processedUsers)
                            Log.d(TAG, "Local storage save operation completed")
                        }
                    }
                }
            } else {
                // Handle the case where there are no users or an error occurred
                if (userIds == null) {
                    Log.e(TAG, "Error loading user IDs - received null list from Firebase")
                } else {
                    Log.w(TAG, "No users found in Firebase (empty list returned)")
                }
            }
        }
    }
}