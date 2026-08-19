package io.github.openwarpkit.warpscout.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openwarpkit.warpscout.core.CoreBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ConfigDocument(
    val historyId: Long,
    val format: String,
    val fileName: String,
    val content: String
)

@Singleton
class ExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bridge: CoreBridge,
    private val accountStore: AccountStore
) {
    suspend fun shareReport(item: HistoryEntity) {
        val export = reportFile(item)
        share(export.fileName, export.uri, "text/plain")
    }

    suspend fun shareReportImage(document: ReportImageDocument) {
        val fileName = "warpscout-report-${document.historyId}.png"
        val uri = writeReportImage(fileName, document)
        share(fileName, uri, "image/png")
    }

    suspend fun saveReportImage(document: ReportImageDocument, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            ReportImageRenderer.write(document, it)
        } ?: error("Unable to open the selected file")
    }

    suspend fun openReport(item: HistoryEntity) {
        val export = reportFile(item)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(export.uri, "text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(export.fileName, export.uri) }
        context.startActivity(
            Intent.createChooser(view, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    suspend fun renderConfig(item: HistoryEntity, format: String): ConfigDocument {
        val report = item.resultJson?.let(::JSONObject) ?: error("Scan result is unavailable")
        val endpoint = bestEndpoint(report, item.bestEndpoint) ?: error("Working endpoint is unavailable")
        val account = accountStore.read() ?: error("WARP account is unavailable")
        val payload = JSONObject()
            .put("endpoint", endpoint)
            .put("options", JSONObject(item.optionsJson))
            .put("format", format)
        val content = render("render-config", account, payload)
        val extension = if (format == "mihomo") "yaml" else "conf"
        return ConfigDocument(
            historyId = item.id,
            format = format,
            fileName = "warpscout-${format}-${item.id}.$extension",
            content = content
        )
    }

    suspend fun shareConfig(document: ConfigDocument) {
        share(document.fileName, write(document.fileName, document.content), "text/plain")
    }

    suspend fun saveConfig(document: ConfigDocument, uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(document.content)
        } ?: error("Unable to open the selected file")
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports")
        check(!directory.exists() || directory.deleteRecursively())
    }

    private suspend fun render(operation: String, account: String, payload: JSONObject): String =
        withContext(Dispatchers.IO) {
            var content: String? = null
            var failure: String? = null
            val request = JSONObject()
                .put("schemaVersion", 1)
                .put("operation", operation)
                .put("accountJson", account)
                .put("payload", payload)
                .toString()
            bridge.start(request) { eventJson ->
                val event = JSONObject(eventJson)
                when (event.optString("type")) {
                    "completed" -> content = event.optString("payload")
                    "error" -> failure = event.optJSONObject("error")?.optString("message")
                }
            }
            failure?.let { error(it) }
            content ?: error("Core returned no export data")
        }

    private suspend fun reportFile(item: HistoryEntity): ExportedFile {
        val report = item.resultJson?.let(::JSONObject) ?: error("Scan result is unavailable")
        val payload = JSONObject().put("report", report)
        val content = render("render-report", "", payload)
        val fileName = "warpscout-report-${item.id}.txt"
        return ExportedFile(fileName, write(fileName, content))
    }

    private suspend fun write(fileName: String, content: String): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName)
        file.writeText(content)
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private suspend fun writeReportImage(
        fileName: String,
        document: ReportImageDocument
    ): Uri = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName)
        file.outputStream().use { ReportImageRenderer.write(document, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun share(fileName: String, uri: Uri, mimeType: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(fileName, uri) }
        context.startActivity(
            Intent.createChooser(send, null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        )
    }

    private data class ExportedFile(val fileName: String, val uri: Uri)

    private fun bestEndpoint(report: JSONObject, preferred: String?): JSONObject? {
        val results = report.optJSONArray("results") ?: return null
        var fallback: JSONObject? = null
        for (index in 0 until results.length()) {
            val result = results.optJSONObject(index) ?: continue
            if (!result.optBoolean("working") || !result.optBoolean("durable")) continue
            if (result.optString("endpoint") == preferred) return result
            if (fallback == null) fallback = result
        }
        return fallback
    }
}
