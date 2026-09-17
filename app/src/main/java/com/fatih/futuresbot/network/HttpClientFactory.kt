package com.fatih.futuresbot.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object HttpClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Emirlerin sessizce iki kez gönderilmesini önlemek için otomatik tekrar KAPALI
        .retryOnConnectionFailure(false)
        .build()
}
