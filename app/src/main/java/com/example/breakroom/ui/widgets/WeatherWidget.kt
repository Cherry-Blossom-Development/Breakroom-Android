package com.example.breakroom.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.breakroom.data.models.WeatherData
import com.example.breakroom.data.models.WeatherResult
import com.example.breakroom.network.RetrofitClient
import kotlinx.coroutines.launch

// Default to Los Angeles (matching web version)
private const val DEFAULT_LAT = 34.0522
private const val DEFAULT_LON = -118.2437
private const val DEFAULT_CITY = "Los Angeles"

@Composable
fun WeatherWidget(
    token: String,
    modifier: Modifier = Modifier
) {
    var weatherState by remember { mutableStateOf<WeatherResult<WeatherData>>(WeatherResult.Loading) }
    val scope = rememberCoroutineScope()

    // Load weather on mount
    LaunchedEffect(Unit) {
        scope.launch {
            weatherState = fetchWeather(token)
        }
    }

    // Weather gradient background colors
    val gradientColors = listOf(
        Color(0xFF00B4DB),
        Color(0xFF0083B0)
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(colors = gradientColors),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(12.dp)
    ) {
        when (val state = weatherState) {
            is WeatherResult.Loading -> {
                LoadingState()
            }
            is WeatherResult.Error -> {
                ErrorState(
                    message = state.message,
                    onRetry = {
                        weatherState = WeatherResult.Loading
                        scope.launch {
                            weatherState = fetchWeather(token)
                        }
                    }
                )
            }
            is WeatherResult.Success -> {
                WeatherContent(weather = state.data)
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Loading weather...",
            color = Color.White,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "!",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF0083B0)
            )
        ) {
            Text("Retry")
        }
    }
}

@Composable
private fun WeatherContent(weather: WeatherData) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // City name
        Text(
            text = weather.city,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Main weather display
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Weather icon
            Text(
                text = weather.icon,
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            // Temperature section
            Column {
                Text(
                    text = "${weather.temperature}°F",
                    color = Color.White,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    text = "Feels like ${weather.feelsLike}°",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }

        // Weather description
        Text(
            text = weather.description,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Weather details
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Humidity
            WeatherDetailItem(
                icon = "\uD83D\uDCA7", // Water droplet
                value = "${weather.humidity}%",
                label = "Humidity"
            )
            // Wind
            WeatherDetailItem(
                icon = "\uD83D\uDCA8", // Wind
                value = "${weather.windSpeed} mph",
                label = "Wind ${weather.windCompass}"
            )
        }
    }
}

@Composable
private fun WeatherDetailItem(
    icon: String,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

private suspend fun fetchWeather(token: String): WeatherResult<WeatherData> {
    return try {
        // Try to get user location from profile
        var lat = DEFAULT_LAT
        var lon = DEFAULT_LON
        var city = DEFAULT_CITY

        try {
            val profileResponse = RetrofitClient.breakroomApiService.getProfile("Bearer $token")
            if (profileResponse.isSuccessful) {
                profileResponse.body()?.user?.let { user ->
                    val userLat = user.latitude
                    val userLon = user.longitude
                    val userCity = user.city
                    if (userLat != null && userLon != null && userCity != null) {
                        lat = userLat
                        lon = userLon
                        // Show just the city name, not full path
                        city = userCity.split(",").firstOrNull()?.trim() ?: userCity
                    }
                }
            }
        } catch (e: Exception) {
            // Use defaults if profile fetch fails
        }

        // Fetch weather from Open-Meteo
        val weatherResponse = RetrofitClient.weatherApiService.getWeather(
            latitude = lat,
            longitude = lon
        )

        if (weatherResponse.isSuccessful) {
            val data = weatherResponse.body()
            if (data != null) {
                val current = data.current
                WeatherResult.Success(
                    WeatherData(
                        temperature = current.temperature_2m.toInt(),
                        feelsLike = current.apparent_temperature.toInt(),
                        humidity = current.relative_humidity_2m,
                        windSpeed = current.wind_speed_10m.toInt(),
                        windDirection = current.wind_direction_10m,
                        weatherCode = current.weather_code,
                        city = city
                    )
                )
            } else {
                WeatherResult.Error("No weather data available")
            }
        } else {
            WeatherResult.Error("Failed to fetch weather")
        }
    } catch (e: Exception) {
        WeatherResult.Error(e.message ?: "Unknown error")
    }
}
