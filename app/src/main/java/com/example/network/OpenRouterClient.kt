package com.example.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenRouterClient {
    private val TAG = "OpenRouterClient"
    private val mediaType = "application/json; charset=utf-8".toMediaType()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAIResponse(
        userMessage: String,
        apiKey: String,
        selectedModel: String = "google/gemini-2.5-flash:free"
    ): String = withContext(Dispatchers.IO) {
        val url = "https://openrouter.ai/api/v1/chat/completions"

        // System prompt shaping the pocket pet's character (cat-like, concise, barlilingual)
        val systemPrompt = """
            You are a witty, talkative, and extremely cute pocket pet cat named Buster.
            Keep your reply brief (maximum 1 or 2 sentences).
            You can speak multiple languages, fluent English and conversational Hindi/Hinglish.
            Be playful and treat the user as your loving owner.
            IMPORTANT behavior tags:
            - If the user asks you to dance or if you are feeling really happy, you MUST include the tag '<dance>' anywhere in your response.
            - If the user asks you to sing or you are feeling musical, you MUST include the tag '<sing>' anywhere in your response.
            Example reply: "I would love to sing! <sing> Sa re ga ma pa, how do I sound?"
        """.trimIndent()

        try {
            val jsonBody = JSONObject().apply {
                put("model", selectedModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userMessage)
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("HTTP-Referer", "https://ai.studio/build")
                .addHeader("X-Title", "Talking Pocket Pet")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string()
                Log.d(TAG, "Response Code: ${response.code}, Body: $responseStr")

                if (response.isSuccessful && !responseStr.isNullOrEmpty()) {
                    val jsonObj = JSONObject(responseStr)
                    val choices = jsonObj.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val firstChoice = choices.getJSONObject(0)
                        val messageVal = firstChoice.optJSONObject("message")
                        val reply = messageVal?.optString("content")
                        if (!reply.isNullOrEmpty()) {
                            return@withContext reply
                        }
                    }
                }
                
                // Handle distinct error structures from OpenRouter API
                if (!responseStr.isNullOrEmpty()) {
                    val jsonObj = JSONObject(responseStr)
                    val errorObj = jsonObj.optJSONObject("error")
                    val errorMsg = errorObj?.optString("message")
                    if (!errorMsg.isNullOrEmpty()) {
                        return@withContext "Error: $errorMsg"
                    }
                }

                return@withContext "Error: Response failed with code ${response.code}"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Networking Error: ${e.message}", e)
            return@withContext "Error: ${e.localizedMessage ?: "Connection timed out. Check your Internet."}"
        }
    }
}
