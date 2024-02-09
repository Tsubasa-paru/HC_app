package com.example.hc_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.livedata.observeAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hc_app.ui.theme.HC_appTheme
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel = MainViewModel(applicationContext) // Pass applicationContext to ViewModel
        setContent {
            HC_appTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StepCountScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun StepCountScreen(viewModel: MainViewModel) {
    val stepCounts = viewModel.stepCounts.observeAsState("Loading step counts...")
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = stepCounts.value, fontSize = 18.sp)
    }
}

class MainViewModel(private val appContext: android.content.Context) : ViewModel() { // Accept Context in ViewModel constructor
    private val _stepCounts = MutableLiveData<String>()
    val stepCounts: LiveData<String> = _stepCounts

    init {
        loadStepCounts()
    }

    private fun loadStepCounts() {
        val healthConnectClient = HealthConnectClient.getOrCreate(appContext) // Correctly use appContext
        val startTime = Instant.now().minus(Duration.ofDays(1))
        val endTime = Instant.now()

        viewModelScope.launch {
            readStepsByTimeRange(healthConnectClient, startTime, endTime).collect { steps ->
                _stepCounts.postValue(steps)
            }
        }
    }

    private fun readStepsByTimeRange(
        healthConnectClient: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ) = flow {
        try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            var totalSteps = 0
            response.records.forEach { record ->
                totalSteps += (record as StepsRecord).count.toInt()
            }
            emit("Total steps: $totalSteps")
        } catch (e: Exception) {
            emit("Error reading step records: ${e.message}")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    HC_appTheme {
        StepCountScreen(MainViewModel(android.app.Application())) // Mock context for preview
    }
}