package io.github.openwarpkit.warpscout.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.toolResultDataStore by preferencesDataStore("tool_results")

data class ToolSearchResult(
    val operation: String,
    val requestJson: String,
    val resultJson: String,
    val status: String,
    val finishedAt: Long,
    val errorCode: String?,
    val errorMessage: String?
)

data class ToolSearchResults(
    val junk: ToolSearchResult? = null,
    val sni: ToolSearchResult? = null
)

data class ToolScanProfile(
    val sourceFinishedAt: Long,
    val protocol: String,
    val junkCount: Int = 0,
    val junkMin: Int = 0,
    val junkMax: Int = 0,
    val i1: String = "",
    val sni: String = "",
    val attempts: Int = 0
)

@Singleton
class ToolResultStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val results: Flow<ToolSearchResults> = context.toolResultDataStore.data.map { preferences ->
        ToolSearchResults(
            junk = preferences[JUNK_RESULT]?.let(::decodeToolSearchResult),
            sni = preferences[SNI_RESULT]?.let(::decodeToolSearchResult)
        )
    }

    suspend fun save(result: ToolSearchResult) {
        val key = when (result.operation) {
            "find-junk" -> JUNK_RESULT
            "find-sni" -> SNI_RESULT
            else -> return
        }
        context.toolResultDataStore.edit { it[key] = encodeToolSearchResult(result) }
    }

    suspend fun clear() {
        context.toolResultDataStore.edit { it.clear() }
    }

    private companion object {
        val JUNK_RESULT = stringPreferencesKey("last_find_junk")
        val SNI_RESULT = stringPreferencesKey("last_find_sni")
    }
}

internal fun encodeToolSearchResult(result: ToolSearchResult): String = JSONObject()
    .put("operation", result.operation)
    .put("requestJson", result.requestJson)
    .put("resultJson", result.resultJson)
    .put("status", result.status)
    .put("finishedAt", result.finishedAt)
    .put("errorCode", result.errorCode)
    .put("errorMessage", result.errorMessage)
    .toString()

internal fun decodeToolSearchResult(value: String): ToolSearchResult? = runCatching {
    val json = JSONObject(value)
    ToolSearchResult(
        operation = json.getString("operation"),
        requestJson = json.getString("requestJson"),
        resultJson = json.getString("resultJson"),
        status = json.getString("status"),
        finishedAt = json.getLong("finishedAt"),
        errorCode = json.optString("errorCode").takeUnless { it.isBlank() || it == "null" },
        errorMessage = json.optString("errorMessage").takeUnless { it.isBlank() || it == "null" }
    )
}.getOrNull()

fun ToolSearchResult.toScanProfile(): ToolScanProfile? = runCatching {
    val result = JSONObject(resultJson)
    when (operation) {
        "find-junk" -> {
            val junkCount = result.optInt("junkCount")
            val junkMin = result.optInt("junkMin")
            val junkMax = result.optInt("junkMax")
            if (junkCount <= 0 || junkMax <= 0) return null
            ToolScanProfile(
                sourceFinishedAt = finishedAt,
                protocol = "awg",
                junkCount = junkCount,
                junkMin = junkMin,
                junkMax = junkMax,
                i1 = result.optString("i1")
            )
        }
        "find-sni" -> {
            val sni = result.optString("sni")
            if (sni.isBlank()) return null
            ToolScanProfile(
                sourceFinishedAt = finishedAt,
                protocol = result.optString("protocol").ifBlank { "masque" },
                sni = sni,
                attempts = result.optInt("attempts", 3)
            )
        }
        else -> null
    }
}.getOrNull()
