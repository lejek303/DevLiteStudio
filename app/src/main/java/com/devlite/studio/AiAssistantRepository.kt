package com.devlite.studio.data

import com.devlite.studio.model.AiAction
import com.devlite.studio.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Talks to the Anthropic Messages API, the OpenAI Chat Completions API,
 * or a local Ollama server, streaming tokens back as server-sent events
 * so they can be shown live in the AI side panel.
 */
class AiAssistantRepository(private val securePreferences: SecurePreferences) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    fun runAction(action: AiAction, selectedCode: String, languageName: String): Flow<String> =
        streamCompletion(buildPrompt(action, selectedCode, languageName))

    fun streamCompletion(prompt: String): Flow<String> = flow {
        val provider = securePreferences.selectedProvider
        val request = when (provider) {
            AiProvider.ANTHROPIC -> anthropicRequest(prompt)
            AiProvider.OPENAI -> openAiRequest(prompt)
            AiProvider.OLLAMA_LOCAL -> ollamaRequest(prompt)
        }

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                emit("[error: HTTP ${response.code} — ${response.body?.string().orEmpty()}]")
                return@flow
            }
            val source = response.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val chunk = extractText(provider, payload)
                if (chunk.isNotEmpty()) emit(chunk)
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun anthropicRequest(prompt: String): Request {
        val body = JSONObject().apply {
            put("model", securePreferences.selectedModel)
            put("max_tokens", 2048)
            put("stream", true)
            put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
        }
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", securePreferences.anthropicApiKey.orEmpty())
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun openAiRequest(prompt: String): Request {
        val body = JSONObject().apply {
            put("model", securePreferences.selectedModel.ifBlank { "gpt-4o-mini" })
            put("stream", true)
            put(
                "messages",
                JSONArray().put(JSONObject().put("role", "user").put("content", prompt))
            )
        }
        return Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer ${securePreferences.openAiApiKey.orEmpty()}")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun ollamaRequest(prompt: String): Request {
        val base = securePreferences.ollamaBaseUrl?.trimEnd('/') ?: "http://127.0.0.1:11434"
        val body = JSONObject().apply {
            put("model", securePreferences.selectedModel.ifBlank { "llama3" })
            put("prompt", prompt)
            put("stream", true)
        }
        return Request.Builder()
            .url("$base/api/generate")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun extractText(provider: AiProvider, payload: String): String = try {
        val json = JSONObject(payload)
        when (provider) {
            AiProvider.ANTHROPIC -> json.optJSONObject("delta")?.optString("text").orEmpty()
            AiProvider.OPENAI -> json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("delta")
                ?.optString("content")
                .orEmpty()
            AiProvider.OLLAMA_LOCAL -> json.optString("response")
        }
    } catch (_: Throwable) {
        ""
    }

    private fun buildPrompt(action: AiAction, code: String, languageName: String): String {
        val instruction = when (action) {
            AiAction.EXPLAIN -> "Explain what this $languageName code does, concisely."
            AiAction.REFACTOR -> "Refactor this $languageName code for clarity and best practices. Return only the revised code."
            AiAction.FIX_BUGS -> "Find and fix bugs in this $languageName code. Return the corrected code, then a short list of what was wrong."
            AiAction.GENERATE_TESTS -> "Write unit tests for this $languageName code using an idiomatic testing framework for the language."
            AiAction.COMPLETE -> "Continue this $languageName code naturally from where it leaves off. Return only the code to append."
        }
        return "$instruction\n\n```$languageName\n$code\n```"
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
