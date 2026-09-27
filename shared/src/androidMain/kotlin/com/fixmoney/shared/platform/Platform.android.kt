package com.fixmoney.shared.platform

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal actual fun httpClient(): HttpClient = HttpClient(OkHttp)

internal actual fun logWarn(tag: String, message: String) {
    Log.w(tag, message)
}
