package com.smj1740.smartqr

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var offlineHtml: String = ""
    private var pendingSave: Pair<ByteArray, String>? = null

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingSave
        pendingSave = null
        if (granted && pending != null) {
            writeImage(pending.first, pending.second)
        } else if (!granted) {
            Toast.makeText(this, R.string.storage_permission, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterFullscreen()
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progress = findViewById(R.id.progress)
        offlineHtml = assets.open("offline.html").bufferedReader().use { it.readText() }

        ViewCompat.setOnApplyWindowInsetsListener(webView) { view, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, 0, 0, ime.bottom)
            insets
        }

        webView.setBackgroundColor(getColor(R.color.window_background))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            allowFileAccess = false
            allowContentAccess = false
        }
        webView.addJavascriptInterface(JsBridge(), "SmartQr")
        webView.webViewClient = AppWebViewClient()
        webView.webChromeClient = AppChromeClient()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(START_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    private fun enterFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showOfflinePage() {
        webView.loadDataWithBaseURL(
            "https://appassets.smartqr.local/",
            offlineHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun saveDataUrl(dataUrl: String, fileName: String) {
        val bytes = try {
            decodePngDataUrl(dataUrl)
        } catch (_: IllegalArgumentException) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val name = safeFileName(fileName)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingSave = bytes to name
            storagePermission.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        writeImage(bytes, name)
    }

    private fun writeImage(bytes: ByteArray, name: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/SmartQR"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("insert")
            contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return true
            return scheme != "http" && scheme != "https"
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (url != null && url.startsWith(START_URL)) {
                view?.evaluateJavascript(DOWNLOAD_HOOK, null)
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (request.isForMainFrame) showOfflinePage()
        }
    }

    private inner class AppChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            progress.progress = newProgress
            progress.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
        }

        override fun onJsAlert(
            view: WebView?,
            url: String?,
            message: String?,
            result: JsResult
        ): Boolean {
            if (isFinishing) {
                result.cancel()
                return true
            }
            AlertDialog.Builder(this@MainActivity)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }
                .show()
            return true
        }
    }

    private inner class JsBridge {
        @android.webkit.JavascriptInterface
        fun saveDataUrl(dataUrl: String, fileName: String) {
            runOnUiThread { this@MainActivity.saveDataUrl(dataUrl, fileName) }
        }

        @android.webkit.JavascriptInterface
        fun retry() {
            runOnUiThread { webView.loadUrl(START_URL) }
        }
    }

    companion object {
        private const val START_URL = "https://smj1740.github.io/dddff/"

        private val DOWNLOAD_HOOK = """
            (function () {
              if (window.__smartQrHook) return;
              window.__smartQrHook = true;
              if (typeof downloadImage === "function") {
                window.downloadImage = function (dataUrl, name) {
                  SmartQr.saveDataUrl(String(dataUrl || ""), String(name || "qr.png"));
                };
              }
              var proto = HTMLAnchorElement.prototype;
              var original = proto.click;
              proto.click = function () {
                var href = this.href || "";
                var name = this.getAttribute("download");
                if (name && href.indexOf("data:image/") === 0) {
                  SmartQr.saveDataUrl(href, name);
                  return;
                }
                return original.apply(this, arguments);
              };
            })();
        """.trimIndent()

        private fun decodePngDataUrl(dataUrl: String): ByteArray {
            val comma = dataUrl.indexOf(',')
            require(comma > 0) { "data url" }
            val meta = dataUrl.substring(0, comma).lowercase()
            require(meta.startsWith("data:image/png") || meta.startsWith("data:image/jpeg"))
            require(meta.contains(";base64"))
            val bytes = Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
            require(bytes.size in 32..5_000_000)
            val bitmap = BitmapFactory.decodeStream(ByteArrayInputStream(bytes))
            require(bitmap != null)
            bitmap.recycle()
            return bytes
        }

        private fun safeFileName(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "-")
                .trim('.', ' ')
                .take(80)
            val base = cleaned.ifBlank { "qr" }
            return if (base.endsWith(".png", ignoreCase = true)) base else "$base.png"
        }
    }
}
