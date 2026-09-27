package com.fixmoney.shared.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import platform.Foundation.NSLog

actual fun httpClient(): HttpClient = HttpClient(Darwin)

actual fun logWarn(tag: String, message: String) {
    NSLog("%s: %s", tag, message)
}
