package com.example.promptstudio

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream

class MainActivity : Activity() {
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var webView: WebView? = null
    private var transformer: Transformer? = null
    private val imagePickerRequest = 1001
    private val videoPickerRequest = 1002

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        webView = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
        }
        web.addJavascriptInterface(ExportBridge(), "AndroidExport")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                return try {
                    startActivityForResult(Intent.createChooser(intent, "Pilih gambar"), imagePickerRequest)
                    true
                } catch (_: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return !(url.scheme == "file" && url.path?.startsWith("/android_asset/") == true)
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun callJs(script: String) {
        runOnUiThread { webView?.evaluateJavascript(script, null) }
    }

    @OptIn(UnstableApi::class)
    private inner class ExportBridge {
        @JavascriptInterface
        fun openVideoPicker() {
            runOnUiThread {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "video/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                try {
                    startActivityForResult(intent, videoPickerRequest)
                } catch (_: Exception) {
                    callJs("window.onNativeVideoError('Tidak dapat membuka pemilih video.');")
                }
            }
        }

        @JavascriptInterface
        fun exportVideos(urisJson: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                callJs("window.onNativeVideoError('Ekspor MP4 memerlukan Android 10 atau lebih baru.');")
                return false
            }
            return try {
                val uriArray = JSONArray(urisJson)
                if (uriArray.length() == 0 || uriArray.length() > 10) {
                    callJs("window.onNativeVideoError('Pilih antara 1 sampai 10 video.');")
                    return false
                }
                val items = ArrayList<EditedMediaItem>()
                for (i in 0 until uriArray.length()) {
                    val clip = uriArray.getJSONObject(i)
                    val uri = Uri.parse(clip.getString("uri"))
                    if (uri.scheme != "content") throw IllegalArgumentException("Sumber video tidak valid.")
                    val startMs = clip.optLong("startMs", 0L).coerceAtLeast(0L)
                    val endMs = clip.optLong("endMs", 0L).coerceAtLeast(0L)
                    val mediaBuilder = MediaItem.Builder().setUri(uri)
                    if (startMs > 0L || endMs > 0L) {
                        val clipping = MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                        if (endMs > startMs) clipping.setEndPositionMs(endMs)
                        mediaBuilder.setClippingConfiguration(clipping.build())
                    }
                    items.add(EditedMediaItem.Builder(mediaBuilder.build()).build())
                }
                val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(items)
                val composition = Composition.Builder(sequence).build()
                val output = File(cacheDir, "prompt-studio-" + System.currentTimeMillis() + ".mp4")
                callJs("window.onNativeExportStarted();")
                runOnUiThread {
                    try {
                        transformer = Transformer.Builder(this@MainActivity)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    val saved = saveMp4ToGallery(output)
                                    transformer = null
                                    if (saved) {
                                        callJs("window.onNativeExportFinished(true, 'MP4 berhasil disimpan ke Movies/Prompt Studio.');")
                                    } else {
                                        callJs("window.onNativeExportFinished(false, 'Render selesai, tetapi gagal menyimpan ke Galeri.');")
                                    }
                                    output.delete()
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException
                                ) {
                                    transformer = null
                                    output.delete()
                                    callJs("window.onNativeExportFinished(false, " + org.json.JSONObject.quote(exportException.message ?: "Kesalahan saat memproses video.") + ");")
                                }
                            })
                            .build()
                        transformer?.start(composition, output.absolutePath)
                    } catch (error: Exception) {
                        transformer = null
                        output.delete()
                        callJs("window.onNativeExportFinished(false, " + org.json.JSONObject.quote(error.message ?: "Tidak dapat memulai render.") + ");")
                    }
                }
                true
            } catch (error: Exception) {
                callJs("window.onNativeVideoError(" + org.json.JSONObject.quote(error.message ?: "Video tidak dapat diproses.") + ");")
                false
            }
        }

        @JavascriptInterface
        fun saveWebM(base64Data: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "prompt-studio-" + System.currentTimeMillis() + ".webm")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/webm")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Prompt Studio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Tidak dapat membuka file tujuan")
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    true
                } catch (error: Exception) {
                    contentResolver.delete(uri, null, null)
                    throw error
                }
            } catch (_: Exception) { false }
        }

        @JavascriptInterface
        fun downloadMp4(videoUrl: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return try {
                val uri = Uri.parse(videoUrl)
                if (uri.scheme != "https") return false
                val request = android.app.DownloadManager.Request(uri)
                    .setTitle("Prompt Studio AI Video")
                    .setDescription("Mengunduh video hasil AI")
                    .setMimeType("video/mp4")
                    .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_MOVIES,
                        "Prompt Studio/prompt-studio-ai-" + System.currentTimeMillis() + ".mp4"
                    )
                val manager = this@MainActivity.getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                manager.enqueue(request)
                true
            } catch (_: Exception) { false }
        }
    }

    private fun saveMp4ToGallery(file: File): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "prompt-studio-edit-" + System.currentTimeMillis() + ".mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Prompt Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            try {
                contentResolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(file).use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Tidak dapat membuka file tujuan")
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                true
            } catch (error: Exception) {
                contentResolver.delete(uri, null, null)
                throw error
            }
        } catch (_: Exception) { false }
    }

    @Deprecated("Deprecated Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == imagePickerRequest) {
            val result = if (resultCode == RESULT_OK) data?.data?.let { arrayOf(it) } else null
            uploadCallback?.onReceiveValue(result)
            uploadCallback = null
        } else if (requestCode == videoPickerRequest) {
            if (resultCode == RESULT_OK && data != null) {
                val selected = ArrayList<Uri>()
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) selected.add(clip.getItemAt(i).uri)
                } ?: data.data?.let { selected.add(it) }
                val values = JSONArray()
                selected.take(10).forEach { uri ->
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (_: Exception) { }
                    values.put(uri.toString())
                }
                callJs("window.onNativeVideosSelected($values);")
            } else {
                callJs("window.onNativeVideoError('Pemilihan video dibatalkan.');")
            }
        }
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        transformer?.cancel()
        transformer = null
        webView = null
        super.onDestroy()
    }
}
