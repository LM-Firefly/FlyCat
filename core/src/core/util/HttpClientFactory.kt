/*
 * This file is part of FlyCat.
 *
 * FlyCat is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c)  YumeYucca 2025 - Present
 * Based on YumeBox by YumeYucca
 *
 */

package com.github.lmfirefly.flycat.core.util

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Timeout profile for [createHttpClient]. */
enum class HttpClientProfile {
    /** Short requests, 5 s all timeouts (IP lookup, etc.). */
    FAST,
    /** REST API, connect 5 s / socket 10 s (mihomo control plane). */
    API,
    /** Large file download, connect 15 s / socket 60 s. */
    DOWNLOAD,
}

/**
 * Shared [HttpClient] factory that eliminates duplicated OkHttp + Ktor plugin
 * configuration across the codebase.
 *
 * @param profile timeout preset
 * @param json JSON instance for ContentNegotiation (ignored when [installContentNegotiation] is false)
 * @param installContentNegotiation whether to install the Ktor ContentNegotiation plugin
 * @param userAgent optional User-Agent header applied to every request
 * @param followRedirects whether to follow HTTP redirects (default true)
 */
fun createHttpClient(
    profile: HttpClientProfile,
    json: Json = Json { ignoreUnknownKeys = true },
    installContentNegotiation: Boolean = true,
    userAgent: String? = null,
    followRedirects: Boolean = true,
): HttpClient = HttpClient(OkHttp) {
    install(HttpTimeout) {
        when (profile) {
            HttpClientProfile.FAST -> {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 5_000
            }
            HttpClientProfile.API -> {
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            HttpClientProfile.DOWNLOAD -> {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
        }
    }
    if (installContentNegotiation) {
        install(ContentNegotiation) { json(json) }
    }
    this@HttpClient.followRedirects = followRedirects
    if (userAgent != null) {
        defaultRequest { headers.append(HttpHeaders.UserAgent, userAgent) }
    }
}
