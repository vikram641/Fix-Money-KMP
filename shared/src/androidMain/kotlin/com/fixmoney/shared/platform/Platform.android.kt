package com.fixmoney.shared.platform

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpClient(): HttpClient = HttpClient(OkHttp)

actual fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
}
