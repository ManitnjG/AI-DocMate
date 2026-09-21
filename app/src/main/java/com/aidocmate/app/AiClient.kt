package com.aidocmate.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiClient {
    private val client = OkHttpClient.Builder().callTimeout(110, TimeUnit.SECONDS).readTimeout(105, TimeUnit.SECONDS).build()
    suspend fun ask(endpoint: String, token: String, pages: List<DocPage>, question: String, language: String, summary: Boolean): String = withContext(Dispatchers.IO) {
        val uri = java.net.URI(endpoint.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Enter an HTTPS backend URL in Settings" }
        require(token.isNotBlank()) { "Enter your server access token in Settings" }
        val payload = JSONObject().put("pages", pagesJson(pages)).put("question", question).put("language", language).put("mode", if (summary) "summary" else "question")
        val request = Request.Builder().url(endpoint.trim().trimEnd('/') + "/ask").header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string() ?: error("Server returned no response")
            val data = runCatching { JSONObject(raw) }.getOrElse { error("Server returned an invalid response (${response.code})") }
            check(response.isSuccessful) { data.optString("detail", "Request failed (${response.code})") }
            val sources = data.optJSONArray("sources")
            buildString {
                append(if (data.optString("provider") == "openrouter") "AI answer\n\n" else "Source search (not AI)\n\n")
                append(data.getString("answer"))
                if (sources != null && sources.length() > 0) {
                    append("\n\nSupporting excerpts\n")
                    for (i in 0 until sources.length()) { val s = sources.getJSONObject(i); append("\nPage ${s.getInt("page")}: ${s.getString("quote")}\n") }
                }
            }
        }
    }
}
