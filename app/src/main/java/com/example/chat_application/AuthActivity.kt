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
import com.google.firebase.firestore.FirebaseFirestore
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.example.chat_application.UserSettings.userId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class AuthActivity : AppCompatActivity() {
    // Using enum for view state tracking
    private enum class ViewState {
        AUTH, LOGIN, SIGNUP
    }

    private var currentView = ViewState.AUTH

    // User data storage for registration process
    private var userName: String = ""
    private var userPhone: String = ""
    private var userPassword: String = ""

    // Animation flag
    private var animationFlag: Boolean = false

    // Animation variables
    private lateinit var slideDownAnim: Animation
    private lateinit var slideUpAnim: Animation
    private lateinit var slideUpFastAnim: Animation
    private lateinit var slideRightAnim: Animation
    private lateinit var slideLeftAnim: Animation
    private lateinit var slideUpLateAnim: Animation
    private lateinit var slideDownLateAnim: Animation
    private lateinit var fadeInAnim: Animation
    private lateinit var slideLeftOutAnim: Animation
    private lateinit var slideRightOutAnim: Animation
    private lateinit var fadeInFastAnim: Animation

    // Background UI elements
    private lateinit var topleftbg: ImageView
    private lateinit var bottomleftbg: ImageView
    private lateinit var bottomrightbg: ImageView
    private lateinit var toprightbg: ImageView
    private lateinit var ballbottomleft: ImageView
    private lateinit var ballbottomright: ImageView
    private lateinit var balltopright: ImageView
    private lateinit var balltopleft: ImageView

    // Firestore instance
    private lateinit var db: FirebaseFirestore

    // JSON file for local storage
    private lateinit var localUsersFile: File

    // User data class
    data class UserData(
        val uid: String,
        val displayName: String,
        val phoneNumber: String,
        val password: String,
        val userDescription: String = "",
        val userStatus: String = "",
        val online: Boolean = false
    )

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(UserSettings.theme)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()



        // Initialize local storage first
        initializeLocalStorage()

        // Initialize Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            initializeFirebase()
        }

        // Initialize animations
        initializeAnimations()

        // Check if user is already logged in
        // This will set the appropriate content view
        checkUserLoggedIn()

    }

    override fun onDestroy() {
        if (resources.getBoolean(R.bool.firebaseOn)) {
            UserSettings.setUserOffline(UserSettings.userId)
        }
        super.onDestroy()
    }

    // Initialize Firebase instance
    private fun initializeFirebase() {
        db = FirebaseFirestore.getInstance()
        Log.d(TAG, "Firebase initialized")
    }

    // Initialize and load all animations
    private fun initializeAnimations() {
        // Load all animations from resources
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
        Log.d(TAG, "Animations initialized")
    }

    // Initialize background UI elements - this should only be called AFTER content view is set
    private fun initializeBackGround() {
        try {
            topleftbg = findViewById(R.id.topleftbg)
            bottomleftbg = findViewById(R.id.bottomleftbg)
            bottomrightbg = findViewById(R.id.bottomrightbg)
            toprightbg = findViewById(R.id.toprightbg)
            ballbottomleft = findViewById(R.id.ballbottomleft)
            ballbottomright = findViewById(R.id.ballbottomright)
            balltopright = findViewById(R.id.balltopright)
            balltopleft = findViewById(R.id.balltopleft)
            Log.d(TAG, "Background elements initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing background elements", e)
        }
    }

    // Initialize local storage for users
    private fun initializeLocalStorage() {
        localUsersFile = File(filesDir, "local_users.json")
        if (!localUsersFile.exists()) {
            try {
                localUsersFile.createNewFile()
                saveUsersToJson(emptyList())
                Log.d(TAG, "Created new local users file at: ${localUsersFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating local users file", e)
                showToast("Error initializing storage")
            }
        }
    }

    // Check if user is logged in already
    private fun checkUserLoggedIn() {
        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
        val userId = prefs.getString("userId", null)

        if (userId != null) {
            // Check if user exists locally first
            if (checkUserExistsLocally(userId)) {
                Log.d(TAG, "User found locally, logging in automatically")
                navigateToMainActivity(userId)
                return
            }

            // If not found locally but Firebase is enabled, check there
            if (resources.getBoolean(R.bool.firebaseOn)) {
                Log.d(TAG, "Checking Firebase for user: $userId")
                verifyUserInFirestore(userId)
            } else {
                // If Firebase is disabled and user not found locally, show auth
                showAuthView()
            }
        } else {
            // No stored user ID, show authentication view
            showAuthView()
        }
    }

    // Verify user in Firebase
    private fun verifyUserInFirestore(userId: String) {
        db.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    Log.d(TAG, "User found in Firestore, saving locally")

                    // Extract user data from document
                    val userData = document.data
                    if (userData != null) {
                        val userToSave = getUserDataFromFirestore(userId, userData)
                        saveUserToLocalStorage(userToSave)
                        navigateToMainActivity(userId)
                    } else {
                        Log.e(TAG, "User document exists but has no data")
                        clearUserSession()
                        showAuthView()
                    }
                } else {
                    // User ID in preferences but not in Firestore
                    Log.d(TAG, "User ID stored but not found in Firestore")
                    clearUserSession()
                    showAuthView()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking user login status", e)
                showToast("Connection error: ${e.message}")
                showAuthView()
            }
    }

    // Helper to extract UserData from Firestore document
    private fun getUserDataFromFirestore(userId: String, userData: Map<String, Any>): UserData {
        return UserData(
            uid = userId,
            displayName = userData["displayName"] as? String ?: "",
            phoneNumber = userData["phoneNumber"] as? String ?: "",
            password = userData["password"] as? String ?: "",
            userDescription = userData["userDescription"] as? String ?: "",
            userStatus = userData["userStatus"] as? String ?: "",
            online = userData["online"] as? Boolean ?: false
        )
    }

    // Clear stored user session data
    private fun clearUserSession() {
        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
        prefs.edit().remove("userId").apply()
        Log.d(TAG, "User session cleared")
    }

    // Show initial authentication view
    private fun showAuthView() {
        setContentView(R.layout.authentication_page)
        currentView = ViewState.AUTH

        // Initialize background elements after content view is set
        initializeBackGround()
        setupAuthView()
    }

    // Set up the main authentication view
    private fun setupAuthView() {
        // Reset animation flag
        animationFlag = false

        // Set visibility of background elements
        bottomrightbg.isVisible = true
        bottomleftbg.isVisible = true
        ballbottomleft.isVisible = true
        ballbottomright.isVisible = true

        // Get references to UI elements
        val toSignInPage = findViewById<Button>(R.id.goToLogin)
        val toSignUpPage = findViewById<Button>(R.id.goToSignup)
        val welcomeText = findViewById<TextView>(R.id.welcomeText)
        val loginQuestion = findViewById<TextView>(R.id.loginQuestion)

        // Start animations
        applyAuthViewAnimations(welcomeText, loginQuestion)

        // Set up button click listeners
        toSignInPage.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = ViewState.LOGIN
            initializeBackGround() // Reinitialize background after changing content view
            setupLoginView()
        }

        toSignUpPage.setOnClickListener {
            setContentView(R.layout.signup_page)
            currentView = ViewState.SIGNUP
            initializeBackGround() // Reinitialize background after changing content view
            setupSignupView()
        }
    }

    // Apply animations to auth view elements
    private fun applyAuthViewAnimations(welcomeText: TextView, loginQuestion: TextView) {
        try {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error applying animations", e)
        }
    }

    // Set up the login view
    private fun setupLoginView() {
        val phoneInput = findViewById<EditText>(R.id.signInPhone)
        val passwordInput = findViewById<EditText>(R.id.signInPassword)
        val logInButton = findViewById<Button>(R.id.logInButton)
        val switchLayout = findViewById<TextView>(R.id.switchSignInButton)
        val loginLayout = findViewById<LinearLayout>(R.id.logInLayout)

        // Apply appropriate animations based on flag
        if (!animationFlag) {
            applyInitialLoginAnimations(loginLayout)
            animationFlag = true
        } else {
            applySubsequentLoginAnimations(phoneInput, passwordInput, logInButton, switchLayout)
        }

        // Login button click handler
        logInButton.setOnClickListener {
            handleLoginAttempt(phoneInput, passwordInput)
        }

        // Switch to signup view
        switchLayout.setOnClickListener {
            setContentView(R.layout.signup_page)
            currentView = ViewState.SIGNUP
            initializeBackGround() // Reinitialize background after changing content view
            setupSignupView()
        }
    }

    // Handle login attempt
    private fun handleLoginAttempt(phoneInput: EditText, passwordInput: EditText) {
        val phoneNumber = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validate inputs
        if (phoneNumber.isEmpty() || password.isEmpty()) {
            showToast("Please fill all fields")
            return
        }

        // Format and store phone number
        val formattedPhone = formatPhoneNumber(phoneNumber)
        userPhone = formattedPhone
        userPassword = password

        // Try local login first
        if (validateLoginLocally(formattedPhone, password)) {
            return  // Successful local login handled in validateLoginLocally
        }

        // If local login fails and Firebase is enabled, try remote login
        if (resources.getBoolean(R.bool.firebaseOn)) {
            validateLoginRemote(formattedPhone, password)
        } else {
            showToast("Login failed - account not found")
        }
    }

    // Apply initial animations for login view
    private fun applyInitialLoginAnimations(loginLayout: LinearLayout) {
        try {
            bottomrightbg.startAnimation(slideRightOutAnim)
            bottomleftbg.startAnimation(slideLeftOutAnim)
            ballbottomleft.startAnimation(slideLeftOutAnim)
            ballbottomright.startAnimation(slideRightOutAnim)
            loginLayout.startAnimation(slideUpFastAnim)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying initial login animations", e)
        }
    }

    // Apply subsequent animations for login view
    private fun applySubsequentLoginAnimations(
        phoneInput: EditText,
        passwordInput: EditText,
        logInButton: Button,
        switchLayout: TextView
    ) {
        try {
            phoneInput.startAnimation(fadeInFastAnim)
            passwordInput.startAnimation(fadeInFastAnim)
            logInButton.startAnimation(fadeInFastAnim)
            switchLayout.startAnimation(fadeInFastAnim)

            // Hide background elements
            bottomrightbg.isVisible = false
            bottomleftbg.isVisible = false
            ballbottomleft.isVisible = false
            ballbottomright.isVisible = false
        } catch (e: Exception) {
            Log.e(TAG, "Error applying subsequent login animations", e)
        }
    }

    // Set up the signup view
    private fun setupSignupView() {
        val nameInput = findViewById<EditText>(R.id.signUpName)
        val phoneInput = findViewById<EditText>(R.id.signInPhone)
        val passwordInput = findViewById<EditText>(R.id.signInPassword)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val switchLayout = findViewById<TextView>(R.id.switchSignInButton)
        val signupLayout = findViewById<LinearLayout>(R.id.signUpLayout)

        // Apply appropriate animations based on flag
        if (!animationFlag) {
            applyInitialSignupAnimations(signupLayout)
            animationFlag = true
        } else {
            applySubsequentSignupAnimations(nameInput, phoneInput, passwordInput, signUpButton, switchLayout)
        }

        // Signup button click handler
        signUpButton.setOnClickListener {
            handleSignupAttempt(nameInput, phoneInput, passwordInput)
        }

        // Switch to login view
        switchLayout.setOnClickListener {
            setContentView(R.layout.login_page)
            currentView = ViewState.LOGIN
            initializeBackGround() // Reinitialize background after changing content view
            setupLoginView()
        }
    }

    // Handle signup attempt
    private fun handleSignupAttempt(nameInput: EditText, phoneInput: EditText, passwordInput: EditText) {
        val name = nameInput.text.toString().trim()
        val phoneNumber = phoneInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        // Validate inputs
        if (name.isEmpty() || phoneNumber.isEmpty() || password.isEmpty()) {
            showToast("Please fill all fields")
            return
        }

        // Store user data for registration
        userName = name
        userPhone = formatPhoneNumber(phoneNumber)
        userPassword = password

        // Check if user already exists locally
        if (checkPhoneExistsLocally(userPhone)) {
            showToast("An account with this phone number already exists locally")
            return
        }

        // Check if user exists in Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            checkUserExistsRemote(userPhone)
        } else {
            // If Firebase is disabled, proceed with local registration
            registerUser()
        }
    }

    // Apply initial animations for signup view
    private fun applyInitialSignupAnimations(signupLayout: LinearLayout) {
        try {
            bottomrightbg.startAnimation(slideRightOutAnim)
            bottomleftbg.startAnimation(slideLeftOutAnim)
            ballbottomleft.startAnimation(slideLeftOutAnim)
            ballbottomright.startAnimation(slideRightOutAnim)
            signupLayout.startAnimation(slideUpFastAnim)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying initial signup animations", e)
        }
    }

    // Apply subsequent animations for signup view
    private fun applySubsequentSignupAnimations(
        nameInput: EditText,
        phoneInput: EditText,
        passwordInput: EditText,
        signUpButton: Button,
        switchLayout: TextView
    ) {
        try {
            nameInput.startAnimation(fadeInFastAnim)
            phoneInput.startAnimation(fadeInFastAnim)
            passwordInput.startAnimation(fadeInFastAnim)
            signUpButton.startAnimation(fadeInFastAnim)
            switchLayout.startAnimation(fadeInFastAnim)

            // Hide background elements
            bottomrightbg.isVisible = false
            bottomleftbg.isVisible = false
            ballbottomleft.isVisible = false
            ballbottomright.isVisible = false
        } catch (e: Exception) {
            Log.e(TAG, "Error applying subsequent signup animations", e)
        }
    }

    // Format phone number to include country code
    private fun formatPhoneNumber(phoneNumber: String): String {
        var formattedNumber = phoneNumber

        if (!formattedNumber.startsWith("+20")) {
            formattedNumber = "+2$formattedNumber"
        }

        return formattedNumber
    }

    // Show toast message
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    //-----------------------------
    // Local Storage Functions
    //-----------------------------

    // Get all users from local storage
    private fun getLocalUsers(): List<UserData> {
        if (!localUsersFile.exists() || localUsersFile.length() == 0L) {
            return emptyList()
        }

        return try {
            val fileContent = FileReader(localUsersFile).use { it.readText() }

            // If file is empty, return empty list
            if (fileContent.isBlank()) {
                return emptyList()
            }

            val usersList = mutableListOf<UserData>()

            // Simple JSON parsing - just handle as array for compatibility
            if (fileContent.trim().startsWith("[")) {
                val jsonArray = JSONArray(fileContent)

                for (i in 0 until jsonArray.length()) {
                    val jsonUser = jsonArray.getJSONObject(i)
                    val user = UserData(
                        uid = jsonUser.getString("uid"),
                        displayName = jsonUser.getString("displayName"),
                        phoneNumber = jsonUser.getString("phoneNumber"),
                        password = jsonUser.getString("password"),
                        userDescription = jsonUser.optString("userDescription", ""),
                        userStatus = jsonUser.optString("userStatus", ""),
                        online = jsonUser.optBoolean("online", false)
                    )
                    usersList.add(user)
                }
            } else if (fileContent.trim().startsWith("{")) {
                // Handle new format with users array
                val rootObj = JSONObject(fileContent)
                if (rootObj.has("users")) {
                    val jsonArray = rootObj.getJSONArray("users")

                    for (i in 0 until jsonArray.length()) {
                        val jsonUser = jsonArray.getJSONObject(i)
                        val user = UserData(
                            uid = jsonUser.getString("uid"),
                            displayName = jsonUser.getString("displayName"),
                            phoneNumber = jsonUser.getString("phoneNumber"),
                            password = jsonUser.getString("password"),
                            userDescription = jsonUser.optString("userDescription", ""),
                            userStatus = jsonUser.optString("userStatus", ""),
                            online = jsonUser.optBoolean("online", false)
                        )
                        usersList.add(user)
                    }
                }
            }

            usersList
        } catch (e: Exception) {
            Log.e(TAG, "Error reading local users file", e)
            emptyList()
        }
    }

    // Save users list to JSON file
    private fun saveUsersToJson(users: List<UserData>) {
        try {
            // Create simple array format for maximum compatibility
            val jsonArray = JSONArray()

            users.forEach { userData ->
                val jsonUser = JSONObject().apply {
                    put("uid", userData.uid)
                    put("displayName", userData.displayName)
                    put("phoneNumber", userData.phoneNumber)
                    put("password", userData.password)
                    put("userDescription", userData.userDescription)
                    put("userStatus", userData.userStatus)
                    put("online", userData.online)
                }
                jsonArray.put(jsonUser)
            }

            FileWriter(localUsersFile).use { writer ->
                writer.write(jsonArray.toString())
            }
            Log.d(TAG, "Saved ${users.size} users to local storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving users to local storage", e)
        }
    }

    // Save a single user to local storage
    private fun saveUserToLocalStorage(userData: UserData) {
        val users = getLocalUsers().toMutableList()

        // Check if user already exists
        val existingUserIndex = users.indexOfFirst { it.uid == userData.uid }
        if (existingUserIndex >= 0) {
            users[existingUserIndex] = userData
            Log.d(TAG, "Updated existing user in local storage: ${userData.uid}")
        } else {
            users.add(userData)
            Log.d(TAG, "Added new user to local storage: ${userData.uid}")
        }

        saveUsersToJson(users)
    }

    // Check if user exists locally by ID
    private fun checkUserExistsLocally(userId: String): Boolean {
        return getLocalUsers().any { it.uid == userId }
    }

    // Check if phone number exists locally
    private fun checkPhoneExistsLocally(phoneNumber: String): Boolean {
        return getLocalUsers().any { it.phoneNumber == phoneNumber }
    }

    // Validate login against local storage
    private fun validateLoginLocally(phoneNumber: String, password: String): Boolean {
        val users = getLocalUsers()
        val matchingUser = users.find { it.phoneNumber == phoneNumber && it.password == password }

        if (matchingUser != null) {
            // Local login successful
            Log.d(TAG, "Local login successful for user: ${matchingUser.displayName}")
            showToast("Login successful")

            // Store the user ID for session management
            saveUserSession(matchingUser.uid)

            navigateToMainActivity(matchingUser.uid)
            return true
        }

        return false
    }

    // Save user ID to shared preferences
    private fun saveUserSession(userId: String) {
        val prefs = getSharedPreferences("ChatAppPrefs", MODE_PRIVATE)
        prefs.edit().putString("userId", userId).apply()
        Log.d(TAG, "User session saved: $userId")
    }

    //-----------------------------
    // Firebase Remote Functions
    //-----------------------------

    // Check if user exists in Firebase by phone number
    private fun checkUserExistsRemote(phoneNumber: String) {
        db.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Log.d(TAG, "User with phone number already exists in Firestore")
                    showToast("An account with this phone number already exists")
                } else {
                    Log.d(TAG, "Phone number is available, proceeding with registration")
                    registerUser()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking if user exists", e)
                showToast("Connection error: ${e.message}")

                // Fallback to local registration if Firebase check fails
                Log.d(TAG, "Proceeding with local registration after Firebase error")
                registerUser()
            }
    }

    // Register new user (local and Firebase if enabled)
    private fun registerUser() {
        // Generate a unique ID for the user
        val userId = if (resources.getBoolean(R.bool.firebaseOn)) {
            generateUserId()
        } else {
            "local_${System.currentTimeMillis()}"
        }

        Log.d(TAG, "Registering new user with ID: $userId")

        // Create a UserData object for local storage
        val localUserData = UserData(
            uid = userId,
            displayName = userName,
            phoneNumber = userPhone,
            password = userPassword,
            userDescription = "",
            userStatus = "",
            online = true
        )

        // Save to local storage first
        saveUserToLocalStorage(localUserData)

        // Save user session
        saveUserSession(userId)

        // Then attempt to save to Firebase if enabled
        if (resources.getBoolean(R.bool.firebaseOn)) {
            val userData = createUserData(userId)
            saveUserToFirebase(userData, userId)
        } else {
            // If Firebase is disabled, just proceed with local registration
            handleSuccessfulRegistration(userId)
        }
    }

    // Generate a unique ID for Firebase documents
    private fun generateUserId(): String {
        return db.collection("users").document().id
    }

    // Create user data map for Firebase
    private fun createUserData(userId: String): HashMap<String, Any> {
        return hashMapOf(
            "uid" to userId,
            "displayName" to userName,
            "phoneNumber" to userPhone,
            "password" to userPassword,
            "userDescription" to "",
            "userStatus" to "",
            "online" to true
        )
    }

    // Save user to Firebase
    private fun saveUserToFirebase(userData: HashMap<String, Any>, userId: String) {
        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "User successfully saved to Firebase")
                handleSuccessfulRegistration(userId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error saving user to Firebase", e)
                showToast("Account created locally, but could not be synced online")

                // Even if Firebase save fails, proceed with local data
                navigateToMainActivity(userId)
            }
    }

    // Handle successful registration
    private fun handleSuccessfulRegistration(userId: String) {
        showToast("Account created successfully")
        navigateToMainActivity(userId)
    }

    // Validate login against Firebase
    private fun validateLoginRemote(phoneNumber: String, password: String) {
        db.collection("users")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // User found, check password
                    var isPasswordCorrect = false
                    var userId = ""
                    var displayName = ""

                    for (document in documents) {
                        val userPassword = document.getString("password")
                        if (userPassword == password) {
                            isPasswordCorrect = true
                            userId = document.getString("uid") ?: document.id
                            displayName = document.getString("displayName") ?: ""
                            break
                        }
                    }

                    if (isPasswordCorrect) {
                        Log.d(TAG, "Remote login successful for user: $displayName")
                        handleSuccessfulRemoteLogin(userId, displayName, phoneNumber, password)
                    } else {
                        Log.d(TAG, "Incorrect password for phone: $phoneNumber")
                        showToast("Incorrect password")
                    }
                } else {
                    Log.d(TAG, "No account found with phone: $phoneNumber")
                    showToast("No account found with this phone number")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error validating login", e)
                showToast("Connection error: ${e.message}")
            }
    }

    // Handle successful remote login
    private fun handleSuccessfulRemoteLogin(userId: String, displayName: String, phoneNumber: String, password: String) {
        showToast("Login successful")

        // Save user data locally for future logins
        saveUserToLocalStorage(
            UserData(
                uid = userId,
                displayName = displayName,
                phoneNumber = phoneNumber,
                password = password,
                userDescription = "",
                userStatus = "",
                online = true
            )
        )

        // Store the user ID for session management
        saveUserSession(userId)

        navigateToMainActivity(userId)
    }

    // Navigate to main activity after successful login/registration
    private fun navigateToMainActivity(userId: String) {
        UserSettings.userId = userId

        // Set user online right after successful login
        if (resources.getBoolean(R.bool.firebaseOn)) {
            db.collection("users").document(userId)
                .update("online", true)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error updating online status", e)
                }
        }

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)


        finish() // Close AuthActivity so user can't go back
        Log.d(TAG, "Navigated to MainActivity with userId: $userId")
    }


}