package com.example.data.repository

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class AiSuggestionsRepository {

    private val smartReplySchema = Schema.obj(
        properties = mapOf(
            "suggestions" to Schema.array(
                items = Schema.string(description = "A concise, natural reply suggestion under 14 words"),
                description = "Exactly 3 contextually relevant reply suggestions"
            )
        )
    )

    private val smartReplyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = smartReplySchema
            }
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun generateSmartReplies(
        chatTitle: String,
        recentMessages: List<String>,
        preferredTone: String
    ): Result<List<String>> {
        val contextBlock = recentMessages.takeLast(8).joinToString("\n")
            .ifBlank { "Conversation started in $chatTitle" }
        val prompt = """
            You are an intelligent messaging assistant inside PulseTalk.
            Chat: "$chatTitle"
            Preferred reply tone: $preferredTone
            Recent messages:
            $contextBlock

            Generate 3 distinct, helpful, natural quick-reply suggestions the user can tap to send next.
            Keep each suggestion concise (under 14 words).
        """.trimIndent()

        return try {
            val response = withTimeout(TIMEOUT_MS) {
                smartReplyModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned an empty response.")
            val json = JSONObject(rawJson)
            val arr = json.getJSONArray("suggestions")
            val list = (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }.take(3)
            if (list.isEmpty()) {
                Result.failure(IllegalStateException("No suggestions generated."))
            } else {
                Result.success(list)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Smart reply generation timed out", e)
            Result.failure(Exception("AI suggestions timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Smart reply generation failed", e)
            Result.failure(e)
        }
    }

    fun rewriteDraftStream(draft: String, targetStyle: String): Flow<String> = flow {
        val prompt = """
            Rewrite the following chat message in a "$targetStyle" tone.
            Return only the rewritten message text with no quotes or preamble.
            Message: $draft
        """.trimIndent()
        val acc = StringBuilder()
        withTimeout(TIMEOUT_MS) {
            generalModel.generateContentStream(prompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    acc.append(part)
                    emit(acc.toString())
                }
            }
        }
    }

    suspend fun summarizeThread(chatTitle: String, messages: List<String>): Result<String> {
        val transcript = messages.takeLast(25).joinToString("\n")
        if (transcript.isBlank()) {
            return Result.failure(IllegalStateException("No messages available to summarize."))
        }
        val prompt = """
            Summarize the following chat thread ("$chatTitle") concisely into:
            1) Key Summary (2 sentences max)
            2) Action Items / Decisions (bullet points)
            Messages:
            $transcript
        """.trimIndent()
        return try {
            val response = withTimeout(TIMEOUT_MS) {
                generalModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI summary returned empty text."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Thread summary timed out", e)
            Result.failure(Exception("Thread summary timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Thread summary failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateMessage(text: String, targetLanguage: String): Result<String> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Empty text cannot be translated."))
        val prompt = """
            Translate the following chat message accurately into $targetLanguage.
            Return only the translated text without quotes or commentary.
            Message: $text
        """.trimIndent()
        return try {
            val response = withTimeout(TIMEOUT_MS) { generalModel.generateContent(prompt) }
            val translated = response.text?.trim()
            if (translated.isNullOrBlank()) Result.failure(IllegalStateException("Empty translation."))
            else Result.success(translated)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun composeDraftFromTopic(topic: String, tone: String): Result<String> {
        val prompt = """
            Write a natural, ready-to-send PulseTalk chat message about: "$topic".
            Tone: $tone. Keep it concise (1-3 sentences). Return only the message text.
        """.trimIndent()
        return try {
            val response = withTimeout(TIMEOUT_MS) { generalModel.generateContent(prompt) }
            val draft = response.text?.trim()
            if (draft.isNullOrBlank()) Result.failure(IllegalStateException("AI composer returned empty text."))
            else Result.success(draft)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Draft compose failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiSuggestionsRepo"
        const val TIMEOUT_MS = 60_000L
    }
}
