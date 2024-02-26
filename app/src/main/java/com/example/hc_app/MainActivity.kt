package com.example.hc_app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.security.MessageDigest
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.TextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.time.LocalDate
import java.time.ZoneId
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import android.util.Log
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey




class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = MainViewModel(applicationContext)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StepCountScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}

@Composable
fun StepCountScreen(viewModel: MainViewModel) {
    val stepCounts = viewModel.stepCounts.observeAsState(listOf())
    var userId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Observe isRegistered as a LiveData from the ViewModel
    val isRegistered by viewModel.isRegistered.observeAsState(false)

    //for display registered user data
    val registeredUserId by viewModel.registeredUserId.observeAsState()
    val registeredPassword by viewModel.registeredPassword.observeAsState()
    LaunchedEffect(key1 = true) {
        viewModel.loadUserData()
    }


    Column(modifier = Modifier.padding(16.dp)) {
        if (true) {
            TextField(
                value = userId,
                onValueChange = { userId = it },
                label = { Text("User ID") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.registerUser(userId, password) }) {
                Text("Register / Login")
            }
            Spacer(modifier = Modifier.height(16.dp))

            registeredUserId?.let {
                Text("Registered User ID: $it")
            }
            // Displaying the password for demonstration purposes only
            registeredPassword?.let {
                Text("Password (for demonstration only): $it")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        stepCounts.value.forEach { dailySteps ->
            //Text(text = dailySteps, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveStepCountsToCSV() }) {
            Text("Save to CSV")
        }
    }
}


class MainViewModel(private val appContext: Context) : ViewModel() {
    private val credentialsManager = UserCredentialsManager(appContext)
    private val _isRegistered = MutableLiveData<Boolean>()
    val isRegistered: LiveData<Boolean> = _isRegistered
    private val _stepCounts = MutableLiveData<List<String>>()
    val stepCounts: LiveData<List<String>> = _stepCounts

    //for display registered id and password
    private val _registeredUserId = MutableLiveData<String?>()
    val registeredUserId: LiveData<String?> = _registeredUserId
    private val _registeredPassword = MutableLiveData<String?>()
    val registeredPassword: LiveData<String?> = _registeredPassword
    fun loadUserData() {
        _registeredUserId.value = credentialsManager.getRegisteredUserId()
        _registeredPassword.value = credentialsManager.getRegisteredPassword()
    }



    init {
        loadStepCounts()
    }

    private fun loadStepCounts() = viewModelScope.launch {
        val healthConnectClient = HealthConnectClient.getOrCreate(appContext)
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(29) // Last 30 days
        val stepCountsList = mutableListOf<String>()

        (0L..29L).forEach { day ->
            val date = startDate.plusDays(day)
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val steps = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            ).records.fold(0L) { sum, record ->
                sum + (record as StepsRecord).count
            }

            stepCountsList.add("${date}, $steps")
        }

        _stepCounts.postValue(stepCountsList)
        Log.d("StepCount", "Loaded steps for 30 days")
    }


    fun registerUser(userId: String, password: String) {
        credentialsManager.registerUser(userId, password)
        _isRegistered.postValue(true)
    }

    fun saveStepCountsToCSV() = viewModelScope.launch {
        val fileName = "StepCounts.csv"
        val file = File(appContext.filesDir, fileName)
        try {
            OutputStreamWriter(FileOutputStream(file)).use { writer ->
                writer.write("Date, Steps\n")
                _stepCounts.value?.forEach { line ->
                    writer.write("$line\n")
                }
            }
        } catch (e: Exception) {
            // Handle error
        }
    }
}

class UserCredentialsManager(private val context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "UserCredentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun registerUser(userId: String, password: String) {
        val editor = preferences.edit()
        editor.putString("userId", userId)
        editor.putString("passwordHash", hashPassword(password))
        editor.apply()
    }

    fun isUserRegistered(): Boolean {
        return preferences.contains("userId") && preferences.contains("passwordHash")
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return BigInteger(1, md.digest(password.toByteArray())).toString(16).padStart(32, '0')
    }

    //delete if not needed
    fun getRegisteredUserId(): String? {
        return preferences.getString("userId", null)
    }
    fun getRegisteredPassword(): String? {
        return preferences.getString("password", null) // Assuming you store it directly, which you shouldn't.
    }


}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppTheme {
        // Your Preview Content
    }
}
