package com.cherryblossomdev.breakroom.network

import com.cherryblossomdev.breakroom.BuildConfig
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.util.concurrent.TimeUnit

object RetrofitClient {

    val BASE_URL = BuildConfig.BASE_URL
    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/"

    // Called by AppContainer to wire up token persistence
    var tokenUpdateCallback: ((String) -> Unit)? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    // Intercept responses and save the refreshed token if the server sent one
    private val tokenRefreshInterceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        response.header("X-New-Token")?.let { tokenUpdateCallback?.invoke(it) }
        response
    }

    // Interceptor that converts Authorization header to Cookie for chat endpoints
    private val cookieInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val authHeader = originalRequest.header("Authorization")

        // If we have an Authorization header and it's a chat API request, add cookie
        val newRequest = if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ")
            originalRequest.newBuilder()
                .addHeader("Cookie", "jwtToken=$token")
                .build()
        } else {
            originalRequest
        }

        chain.proceed(newRequest)
    }

    // Custom DNS that falls back to direct resolution when emulator DNS fails
    val fallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Emulator DNS is broken — try resolving via InetAddress directly
                val addresses = InetAddress.getAllByName(hostname)
                if (addresses.isNotEmpty()) {
                    addresses.toList()
                } else {
                    throw e
                }
            }
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .dns(fallbackDns)
        .addInterceptor(cookieInterceptor)
        .addNetworkInterceptor(tokenRefreshInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Simple client for external APIs (no auth interceptor)
    private val simpleOkHttpClient = OkHttpClient.Builder()
        .dns(fallbackDns)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val weatherRetrofit = Retrofit.Builder()
        .baseUrl(WEATHER_BASE_URL)
        .client(simpleOkHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    val chatApiService: ChatApiService = retrofit.create(ChatApiService::class.java)

    val breakroomApiService: BreakroomApiService = retrofit.create(BreakroomApiService::class.java)

    val weatherApiService: WeatherApiService = weatherRetrofit.create(WeatherApiService::class.java)
}
