package com.example.promptstudio

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private val imagePickerRequest = 1001

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val web = WebView(this)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true // WebView needs the granted URI to read the image selected by the user.
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
                    startActivityForResult(
                        Intent.createChooser(intent, "Pilih gambar"),
                        imagePickerRequest
                    )
                    true
                } catch (_: Exception) {
                    uploadCallback?.onReceiveValue(null)
                    uploadCallback = null
                    false
                }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                return !(url.scheme == "file" &&
                    url.path?.startsWith("/android_asset/") == true)
            }
        }
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    private inner class ExportBridge {
        @JavascriptInterface
        fun saveWebM(base64Data: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "prompt-studio-${System.currentTimeMillis()}.webm")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/webm")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Prompt Studio")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return false
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
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun downloadMp4(videoUrl: String): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
            return try {
                val uri = Uri.parse(videoUrl)
                if (uri.scheme != "https") return false
                val request = DownloadManager.Request(uri)
                    .setTitle("Prompt Studio AI Video")
                    .setDescription("Mengunduh video hasil AI")
                    .setMimeType("video/mp4")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_MOVIES,
                        "Prompt Studio/prompt-studio-ai-${System.currentTimeMillis()}.mp4"
                    )
                val manager = this@MainActivity.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    @Deprecated("Deprecated in Android API; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == imagePickerRequest) {
            val result = if (resultCode == RESULT_OK) data?.data?.let { arrayOf(it) } else null
            uploadCallback?.onReceiveValue(result)
            uploadCallback = null
        }
    }

    override fun onDestroy() {
        uploadCallback?.onReceiveValue(null)
        uploadCallback = null
        super.onDestroy()
    }
}
