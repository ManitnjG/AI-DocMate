package com.aidocmate.app

import android.content.Context
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
    val configured: Boolean get() = BuildConfig.DOCMATE_API_URL.isNotBlank()
    private fun session(context: Context, endpoint: String, refresh: Boolean = false): String {
        val prefs = context.getSharedPreferences("managed-session", Context.MODE_PRIVATE)
        if (!refresh && prefs.getLong("expires", 0) > System.currentTimeMillis() + 60000) {
            val saved = SecretStore.read(context)
            if (saved.isNotBlank()) return saved
        }
        val request = Request.Builder().url(endpoint.trimEnd('/') + "/session")
            .post("{}".toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { if (response.code == 429) "AI usage limit reached. Try again later." else "AI service is temporarily unavailable. Use offline tools for now." }
            val data = JSONObject(response.body?.string() ?: error("AI service returned no response"))
            val value = data.getString("access_token")
            SecretStore.write(context, value)
            prefs.edit().putLong("expires", System.currentTimeMillis() + data.getLong("expires_in") * 1000).apply()
            value
        }
    }
    suspend fun ask(context: Context, pages: List<DocPage>, question: String, language: String, summary: Boolean): String = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.DOCMATE_API_URL
        check(configured) { "Cloud AI is not activated in this build. Offline tools are available." }
        var token = session(context, endpoint)
        val uri = java.net.URI(endpoint.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "AI service address is invalid" }
        require(token.isNotBlank()) { "Could not start an AI session" }
        val payload = JSONObject().put("pages", pagesJson(pages)).put("question", question).put("language", language).put("mode", if (summary) "summary" else "question")
        val request = Request.Builder().url(endpoint.trim().trimEnd('/') + "/ask").header("Authorization", "Bearer $token")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        var first = client.newCall(request).execute()
        if (first.code == 401) {
            first.close()
            token = session(context, endpoint, refresh = true)
            first = client.newCall(request.newBuilder().header("Authorization", "Bearer $token").build()).execute()
        }
        first.use { response ->
            val raw = response.body?.string() ?: error("Server returned no response")
            val data = runCatching { JSONObject(raw) }.getOrElse { error("Server returned an invalid response (${response.code})") }
            check(response.isSuccessful) { data.optString("detail", "Request failed (${response.code})") }
            val sources = data.optJSONArray("sources")
            buildString {
                append(if (data.optString("provider") in setOf("openrouter", "groq")) "AI answer\n\n" else "Source search (not AI)\n\n")
                append(data.getString("answer"))
                if (sources != null && sources.length() > 0) {
                    append("\n\nSupporting excerpts\n")
                    for (i in 0 until sources.length()) { val s = sources.getJSONObject(i); append("\nPage ${s.getInt("page")}: ${s.getString("quote")}\n") }
                }
            }
        }
    }
}
