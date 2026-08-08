package com.saarthi.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * One shared client for the three cloud calls the agent makes — Claude
 * reasoning, Maya TTS, Sarvam STT. Shared purely for the connection pool;
 * each call site tunes its own per-request timeout via `newBuilder()`
 * rather than standing up a second client instance.
 *
 * Deliberately no logging interceptor here, even for debug builds — the
 * Claude request body carries the full serialized screen of whatever app
 * the user is in (order contents, phone numbers, addresses), and logging
 * that to logcat would be a PII leak in a takeover app.
 */
object SaarthiHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}
