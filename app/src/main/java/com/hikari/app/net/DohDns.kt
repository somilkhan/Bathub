package com.hikari.app.net

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * DNS resolver used by the Nuvio QuickJS bridge.
 *
 * Uses the normal Android resolver first, then DNS-over-HTTPS when the device
 * resolver cannot resolve a host. The DoH endpoints are reached by IP literal,
 * so the fallback does not depend on the broken DNS path it is replacing.
 */
object DohDns : Dns {
    private const val TTL_MS = 5 * 60 * 1000L
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    private val doh = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val endpoints = listOf(
        "https://1.1.1.1/dns-query",
        "https://8.8.8.8/resolve",
    )

    override fun lookup(hostname: String): List<InetAddress> {
        val host = hostname.trim().lowercase()
        if (host.isEmpty() || host.all { it.isDigit() || it == '.' || it == ':' } || !host.contains('.')) {
            return Dns.SYSTEM.lookup(hostname)
        }

        cache[host]?.takeIf { it.first > System.currentTimeMillis() }?.let { return it.second }

        runCatching { Dns.SYSTEM.lookup(hostname) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                cache[host] = System.currentTimeMillis() + TTL_MS to it
                return it
            }

        val resolved = resolveViaDoh(host)
        if (resolved.isNotEmpty()) {
            cache[host] = System.currentTimeMillis() + TTL_MS to resolved
            return resolved
        }

        throw UnknownHostException("Unable to resolve host \"$hostname\"")
    }

    private fun resolveViaDoh(host: String): List<InetAddress> {
        val encoded = URLEncoder.encode(host, "UTF-8")
        for (endpoint in endpoints) {
            val found = LinkedHashMap<String, InetAddress>()
            for (type in listOf("A", "AAAA")) {
                val response = runCatching {
                    doh.newCall(
                        Request.Builder()
                            .url("$endpoint?name=$encoded&type=$type")
                            .header("accept", "application/dns-json")
                            .build()
                    ).execute()
                }.getOrNull() ?: continue

                response.use { r ->
                    if (!r.isSuccessful) return@use
                    val body = r.body?.string() ?: return@use
                    val answers = runCatching { JSONObject(body).optJSONArray("Answer") }.getOrNull()
                        ?: return@use
                    for (i in 0 until answers.length()) {
                        val answer = answers.optJSONObject(i) ?: continue
                        val data = answer.optString("data").trim()
                        if (answer.optInt("type") != 1 && answer.optInt("type") != 28) continue
                        if (data.isBlank()) continue
                        runCatching { InetAddress.getByName(data) }.getOrNull()?.let {
                            found[it.hostAddress ?: data] = it
                        }
                    }
                }
            }
            if (found.isNotEmpty()) return found.values.toList()
        }
        return emptyList()
    }
}
