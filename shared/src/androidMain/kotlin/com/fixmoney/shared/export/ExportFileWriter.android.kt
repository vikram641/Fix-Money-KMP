package com.fixmoney.shared.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val EXPORT_DIR = "FixMoney Exports"

/**
 * Writes into the shared **Downloads** collection, as the old app's
 * `ExpenseCsvExporter` did.
 *
 * API 29+ goes through MediaStore, which is scoped storage and needs **no
 * permission at all**. API 24-28 has no Downloads collection in MediaStore, so
 * it falls back to a plain file under the legacy public Downloads directory -
 * which does need `WRITE_EXTERNAL_STORAGE`, declared with `maxSdkVersion="28"`
 * so it is never requested on a modern device.
 */
actual class ExportFileWriter(private val context: Context) {

    actual suspend fun write(fileName: String, csv: String): ExportLocation =
        withContext(Dispatchers.IO) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeViaMediaStore(fileName, csv)
            } else {
                writeViaLegacyFile(fileName, csv)
            }
            ExportLocation(
                display = "Downloads/$EXPORT_DIR/$fileName",
                handle = uri.toString()
            )
        }

    /**
     * Opens the file through the system chooser.
     *
     * `ACTION_VIEW` wrapped in `createChooser`, not `ACTION_SEND`: the user
     * asked for the apps that can *open* a CSV (Sheets, Excel, a file viewer),
     * which is viewing, not sharing it somewhere.
     *
     * `FLAG_GRANT_READ_URI_PERMISSION` is what makes the receiving app able to
     * read our `content://` URI at all - without it the chooser appears and
     * every app it offers fails with a permission error.
     */
    actual fun open(location: ExportLocation): Boolean {
        val uri = runCatching { Uri.parse(location.handle) }.getOrNull() ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "text/csv")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(view, "Open export with")
            // The chooser is started from outside an Activity context.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(chooser) }.isSuccess
    }

    private fun writeViaMediaStore(fileName: String, csv: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/$EXPORT_DIR"
            )
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create the export file")
        resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray()) }
            ?: error("Could not open the export file for writing")
        return uri
    }

    private fun writeViaLegacyFile(fileName: String, csv: String): Uri {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            EXPORT_DIR
        )
        if (!dir.exists() && !dir.mkdirs()) error("Could not create ${dir.path}")
        val file = File(dir, fileName)
        FileOutputStream(file).use { it.write(csv.toByteArray()) }
        // A `file://` URI would throw FileUriExposedException the moment another
        // app touched it, so even a world-readable public file goes out through
        // the provider.
        return FileProvider.getUriForFile(context, "${context.packageName}.exports", file)
    }
}
