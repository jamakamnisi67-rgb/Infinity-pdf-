package com.example.data.gemini

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiAiService {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun generateAiResponse(prompt: String, documentContext: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(IllegalStateException("GEMINI_API_KEY is not configured"))
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

            val truncatedContext = if (documentContext.length > 25000) documentContext.take(25000) else documentContext
            val combinedPrompt = """
                You are Infinity PDF Intelligence Assistant.
                DOCUMENT CONTEXT:
                $truncatedContext
                
                USER REQUEST:
                $prompt
            """.trimIndent()

            val rootJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            put(JSONObject().apply { put("text", combinedPrompt) })
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val requestBody = rootJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API Error ${response.code}: $responseString"))
            }

            val responseJson = JSONObject(responseString)
            val candidates = responseJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val text = parts?.optJSONObject(0)?.optString("text")

            if (!text.isNullOrBlank()) {
                Result.success(text)
            } else {
                Result.failure(Exception("Empty response from AI model"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Real local document intelligence engine when Gemini API key is not yet added in Secrets
     */
    fun analyzeLocally(taskType: String, documentText: String, userQuery: String = ""): String {
        if (documentText.isBlank()) {
            return "No text could be extracted from this document to analyze. Please ensure the PDF contains readable text or run PDF OCR first."
        }

        val paragraphs = documentText.split("\n\n").map { it.trim() }.filter { it.length > 20 }
        val words = documentText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val totalWords = words.size
        val estimatedReadTimeMinutes = (totalWords / 200).coerceAtLeast(1)

        return when (taskType) {
            "summarize" -> {
                val sentences = documentText.split(Regex("(?<=[.!?])\\s+")).filter { it.length > 30 }
                val topSentences = sentences.take(5)

                buildString {
                    append("📄 **Executive Summary** (Local Intelligence Engine)\n\n")
                    append("• **Document Overview**: Analyzed $totalWords words across ${paragraphs.size} paragraphs (~$estimatedReadTimeMinutes min reading time).\n\n")
                    append("**Key Takeaways & Core Statements:**\n")
                    for ((idx, s) in topSentences.withIndex()) {
                        append("${idx + 1}. ${s.trim()}\n\n")
                    }
                    append("💡 *Tip: For full conversational AI reasoning, configure your Gemini API Key in Settings.*")
                }
            }
            "chat" -> {
                val queryLower = userQuery.lowercase()
                val queryTerms = queryLower.split(Regex("\\s+")).filter { it.length > 2 }
                val matchingParas = paragraphs.filter { p ->
                    val pLower = p.lowercase()
                    queryTerms.any { term -> pLower.contains(term) }
                }

                if (matchingParas.isNotEmpty()) {
                    buildString {
                        append("Relevant excerpts matching \"$userQuery\":\n\n")
                        for ((idx, p) in matchingParas.take(3).withIndex()) {
                            append("**Match ${idx + 1}:**\n\"$p\"\n\n")
                        }
                    }
                } else {
                    "I searched through the document for \"$userQuery\", but found no direct paragraph matches. Try searching for related keywords or phrases."
                }
            }
            "translate" -> {
                val sampleText = documentText.take(600).trim()
                buildString {
                    append("🌐 **Document Text Extraction for Translation**\n\n")
                    append("Original Document Content ($totalWords total words):\n\n")
                    append("\"$sampleText...\"\n\n")
                    append("To translate the full PDF into 100+ languages with automated layout preservation, configure a Gemini API key in Settings.")
                }
            }
            "questions" -> {
                val sentences = documentText.split(Regex("(?<=[.!?])\\s+"))
                    .filter { it.length in 40..150 }
                    .take(5)

                buildString {
                    append("❓ **Study & Comprehension Questions**\n\n")
                    for ((idx, s) in sentences.withIndex()) {
                        append("**Q${idx + 1}:** What is the significance of the following statement from the document?\n")
                        append("   *\"${s.trim()}\"*\n\n")
                    }
                }
            }
            else -> "Document analysis complete. $totalWords words indexed."
        }
    }
}
