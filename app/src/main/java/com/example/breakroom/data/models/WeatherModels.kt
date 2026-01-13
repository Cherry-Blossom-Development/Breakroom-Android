package com.example.breakroom.data.models

// Open-Meteo API response models
data class OpenMeteoResponse(
    val current: CurrentWeather
)

data class CurrentWeather(
    val temperature_2m: Double,
    val relative_humidity_2m: Int,
    val weather_code: Int,
    val wind_speed_10m: Double,
    val wind_direction_10m: Int,
    val apparent_temperature: Double
)

// Processed weather data for UI
data class WeatherData(
    val temperature: Int,
    val feelsLike: Int,
    val humidity: Int,
    val windSpeed: Int,
    val windDirection: Int,
    val weatherCode: Int,
    val city: String
) {
    val description: String
        get() = WeatherCodes.getDescription(weatherCode)

    val icon: String
        get() = WeatherCodes.getIcon(weatherCode)

    val windCompass: String
        get() {
            val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val index = ((windDirection + 22.5) / 45).toInt() % 8
            return directions[index]
        }
}

// WMO Weather codes mapping (matching web version)
object WeatherCodes {
    private val codes = mapOf(
        0 to Pair("Clear sky", "☀️"),
        1 to Pair("Mainly clear", "🌤️"),
        2 to Pair("Partly cloudy", "⛅"),
        3 to Pair("Overcast", "☁️"),
        45 to Pair("Foggy", "🌫️"),
        48 to Pair("Depositing rime fog", "🌫️"),
        51 to Pair("Light drizzle", "🌦️"),
        53 to Pair("Moderate drizzle", "🌦️"),
        55 to Pair("Dense drizzle", "🌧️"),
        56 to Pair("Light freezing drizzle", "🌨️"),
        57 to Pair("Dense freezing drizzle", "🌨️"),
        61 to Pair("Slight rain", "🌧️"),
        63 to Pair("Moderate rain", "🌧️"),
        65 to Pair("Heavy rain", "🌧️"),
        66 to Pair("Light freezing rain", "🌨️"),
        67 to Pair("Heavy freezing rain", "🌨️"),
        71 to Pair("Slight snow", "🌨️"),
        73 to Pair("Moderate snow", "❄️"),
        75 to Pair("Heavy snow", "❄️"),
        77 to Pair("Snow grains", "🌨️"),
        80 to Pair("Slight rain showers", "🌦️"),
        81 to Pair("Moderate rain showers", "🌧️"),
        82 to Pair("Violent rain showers", "⛈️"),
        85 to Pair("Slight snow showers", "🌨️"),
        86 to Pair("Heavy snow showers", "❄️"),
        95 to Pair("Thunderstorm", "⛈️"),
        96 to Pair("Thunderstorm with hail", "⛈️"),
        99 to Pair("Thunderstorm with heavy hail", "⛈️")
    )

    fun getDescription(code: Int): String = codes[code]?.first ?: "Unknown"
    fun getIcon(code: Int): String = codes[code]?.second ?: "❓"
}

// User location data
data class UserLocation(
    val city: String,
    val latitude: Double,
    val longitude: Double
)

// Profile response - wraps user data from API
data class ProfileResponse(
    val user: UserProfileData
) {
    fun toUserProfile() = user.toUserProfile()

    // Convenience accessors for weather widget compatibility
    val city: String? get() = user.city
    val latitude: Double? get() = user.latitude
    val longitude: Double? get() = user.longitude
}

// User profile data as returned from API (camelCase fields)
data class UserProfileData(
    val id: Int,
    val handle: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val workBio: String? = null,
    val photoPath: String? = null,
    val timezone: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String? = null,
    val friendCount: Int = 0,
    val skills: List<Skill> = emptyList(),
    val jobs: List<UserJob> = emptyList()
) {
    fun toUserProfile() = UserProfile(
        id = id,
        handle = handle,
        firstName = firstName,
        lastName = lastName,
        email = email,
        bio = bio,
        workBio = workBio,
        photoPath = photoPath,
        timezone = timezone,
        city = city,
        latitude = latitude,
        longitude = longitude,
        createdAt = createdAt,
        friendCount = friendCount,
        skills = skills,
        jobs = jobs
    )
}

data class UserProfile(
    val id: Int = 0,
    val handle: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val workBio: String? = null,
    val photoPath: String? = null,
    val timezone: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String? = null,
    val friendCount: Int = 0,
    val skills: List<Skill> = emptyList(),
    val jobs: List<UserJob> = emptyList()
) {
    val displayName: String
        get() {
            val first = firstName ?: ""
            val last = lastName ?: ""
            val fullName = "$first $last".trim()
            return fullName.ifEmpty { handle }
        }

    val initials: String
        get() {
            val first = firstName ?: ""
            val last = lastName ?: ""
            return if (first.isNotEmpty() && last.isNotEmpty()) {
                "${first.first()}${last.first()}".uppercase()
            } else if (first.isNotEmpty()) {
                first.take(2).uppercase()
            } else if (handle.isNotEmpty()) {
                handle.take(2).uppercase()
            } else {
                "?"
            }
        }
}

data class Skill(
    val id: Int,
    val name: String
)

data class UserJob(
    val id: Int,
    val user_id: Int? = null,
    val title: String,
    val company: String,
    val location: String? = null,
    val start_date: String,
    val end_date: String? = null,
    val is_current: Boolean = false,
    val description: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

// Sealed class for weather results
sealed class WeatherResult<out T> {
    data class Success<T>(val data: T) : WeatherResult<T>()
    data class Error(val message: String) : WeatherResult<Nothing>()
    object Loading : WeatherResult<Nothing>()
}
