package com.cvsuagritech.spim.api

import com.google.gson.GsonBuilder
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.ArrayList

object RetrofitClient {
    private const val BASE_URL = "https://Eyow189.pythonanywhere.com/"

    // Simple in-memory CookieJar to maintain session between login and other requests
    private val cookieJar = object : CookieJar {
        private val cookieStore = HashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Log cookies for debugging
            if (cookies.isNotEmpty()) {
                println("RetrofitClient: Saving cookies for ${url.host}: ${cookies.size} cookies")
            }
            cookieStore[url.host] = cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookies = cookieStore[url.host] ?: ArrayList()
            if (cookies.isNotEmpty()) {
                println("RetrofitClient: Loading cookies for ${url.host}: ${cookies.size} cookies")
            }
            return cookies
        }
    }

    private val logging = HttpLoggingInterceptor { message ->
        println("Retrofit: $message")
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(logging)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Lenient GSON to handle potential HTML responses or malformed JSON from the server
    private val lenientGson = GsonBuilder()
        .setLenient()
        .create()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(lenientGson))
            .client(httpClient)
            .build()

        retrofit.create(ApiService::class.java)
    }
}
