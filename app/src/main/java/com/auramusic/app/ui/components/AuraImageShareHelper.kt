package com.auramusic.app.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.auramusic.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Shared PNG export, Gallery save (MediaStore), and Android 10+ / HyperOS ClipData-enabled
 * image sharing utilities for Aura Lyric Cards, Receiptify Receipts, and Wrapped Stats Cards.
 */
object AuraImageShareHelper {

    suspend fun loadArtworkBitmap(
        context: Context,
        url: String?,
        sizePx: Int = 360
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val loader = Coil.imageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(sizePx)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@runCatching null
                (result.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }

    fun decodeAuraRoundLogo(context: Context): Bitmap? = runCatching {
        BitmapFactory.decodeResource(context.resources, R.drawable.aura_app_icon_round)
    }.getOrNull()

    suspend fun shareBitmapAsPng(
        context: Context,
        bitmap: Bitmap,
        cacheSubDir: String,
        fileName: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        val outFile = withContext(Dispatchers.IO) {
            val preferredDir = File(context.cacheDir, cacheSubDir).apply { mkdirs() }
            val preferredFile = File(preferredDir, fileName)
            FileOutputStream(preferredFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.flush()
            }
            val uriCheck = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", preferredFile)
            }.getOrNull()
            if (uriCheck != null) {
                preferredFile
            } else {
                val fallbackDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val fallbackFile = File(fallbackDir, fileName)
                FileOutputStream(fallbackFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.flush()
                }
                fallbackFile
            }
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            // Attach ClipData so Android 10–15 & HyperOS share sheets display a crisp image
            // thumbnail preview and grant URI read permissions reliably across target apps.
            clipData = ClipData.newUri(context.contentResolver, chooserTitle, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
            clipData = ClipData.newUri(context.contentResolver, chooserTitle, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        filePrefix: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = "${filePrefix}_${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Aura Music")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create MediaStore entry")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            error("PNG compression failed")
                        }
                        out.flush()
                    } ?: error("Could not open MediaStore output stream")
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
                "Pictures/Aura Music"
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val targetDir = File(picturesDir, "Aura Music").apply { mkdirs() }
                val outFile = File(targetDir, displayName)
                FileOutputStream(outFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf("image/png"),
                    null
                )
                "Pictures/Aura Music"
            }
        }
    }
}
