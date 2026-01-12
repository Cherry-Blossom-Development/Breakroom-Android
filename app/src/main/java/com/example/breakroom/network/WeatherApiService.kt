package com.example.breakroom.network

import com.example.breakroom.data.models.OpenMeteoResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,apparent_temperature",
        @Query("temperature_unit") temperatureUnit: String = "fahrenheit",
        @Query("wind_speed_unit") windSpeedUnit: String = "mph",
        @Query("timezone") timezone: String = "auto"
    ): Response<OpenMeteoResponse>
}
