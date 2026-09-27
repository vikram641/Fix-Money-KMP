package com.fixmoney.shared.platform

import io.ktor.client.HttpClient

/**
 * The two things every shared-module network call needs that have no single
 * cross-platform implementation: an HTTP engine and a log sink. Both are
 * `expect` here and `actual`-ed per platform (OkHttp/android.util.Log on
 * Android, Darwin/NSLog on iOS) - the same pattern the rest of the iOS port
 * will use for camera, OCR, ads and background sync.
 */
internal expect fun httpClient(): HttpClient

internal expect fun logWarn(tag: String, message: String)
