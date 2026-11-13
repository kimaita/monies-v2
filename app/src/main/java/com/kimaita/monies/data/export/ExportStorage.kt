package com.kimaita.monies.data.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject

class ExportStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun write(
        fileName: String,
        mime: String,
        body: suspend (OutputStream) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore returned no URI for $fileName")
            try {
                resolver.openOutputStream(uri)?.use { out -> body(out) }
                    ?: throw IOException("Could not open output stream for $uri")
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)        // drop the partial/pending file
                throw t
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri                                          // branch result
        } else {
            // API 26-28: public Downloads needs WRITE_EXTERNAL_STORAGE, so write to the app's own
            // external folder and hand out a content:// URI (a file:// URI crashes the share intent).
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("External storage is not available")
            val file = File(dir, fileName)
            try {
                FileOutputStream(file).use { out -> body(out) }
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)                         // branch result
        }
    }


}