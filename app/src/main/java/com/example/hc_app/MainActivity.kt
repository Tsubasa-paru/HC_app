package com.example.hc_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.LocalDate
import java.time.ZoneId
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import android.util.Log
import android.content.Context
import java.math.BigInteger
import java.security.MessageDigest


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = MainViewModel(applicationContext)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
    Column(modifier = Modifier.padding(16.dp)) {
        stepCounts.value.forEach { dailySteps ->
            //Text(text = dailySteps, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveStepCountsToCSV() }) {
            Text("Save to CSV")
        }
    }
}

class MainViewModel(private val appContext: android.content.Context) : ViewModel() {
    private val _stepCounts = MutableLiveData<List<String>>()
    val stepCounts: LiveData<List<String>> = _stepCounts

    //id and password
    private val credentialsManager = UserCredentialsManager(appContext)
    fun registerUser(userId: String, password: String) {
        credentialsManager.registerUser(userId, password)
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
            Log.d("CSV", "CSV file saved successfully at ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("CSV", "Error saving CSV file", e)
        }
    }
}

//For id and password
class UserCredentialsManager(private val context: Context) {

    private val preferences = context.getSharedPreferences("UserCredentials", Context.MODE_PRIVATE)

    fun registerUser(userId: String, password: String) {
        val editor = preferences.edit()
        editor.putString("userId", userId)
        editor.putString("passwordHash", hashPassword(password))
        editor.apply()
    }

    fun verifyUser(userId: String, password: String): Boolean {
        val storedUserId = preferences.getString("userId", null)
        val storedPasswordHash = preferences.getString("passwordHash", null)
        return userId == storedUserId && storedPasswordHash == hashPassword(password)
    }

    private fun hashPassword(password: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return BigInteger(1, md.digest(password.toByteArray())).toString(16).padStart(32, '0')
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    AppTheme {
        // Replace with your UI preview code
    }
}
