package mx.sntss1puebla.credenciales

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var browserFuture: ListenableFuture<MediaBrowser>
    private var browser: MediaBrowser? = null
    private var playWhenConnected = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val selected = WebChromeClient.FileChooserParams.parseResult(
            result.resultCode,
            result.data,
        )
        fileCallback?.onReceiveValue(selected)
        fileCallback = null
    }

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        pendingWebPermission?.let { request ->
            if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            else request.deny()
        }
        pendingWebPermission = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.app_web_view)
        progress = findViewById(R.id.loading_indicator)
        connectMediaBrowser()
        configureWebView()
        findViewById<View>(R.id.play_native_radio).setOnClickListener { playRadio() }

        if (savedInstanceState == null) webView.loadUrl(APP_URL)
        else webView.restoreState(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 20)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                routeUrl(request.url)
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return runCatching {
                    filePicker.launch(fileChooserParams.createIntent())
                    true
                }.getOrElse {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.origin.scheme != "https" || request.origin.host !in APP_HOSTS ||
                    !request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                ) {
                    request.deny()
                    return
                }
                runOnUiThread {
                    if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                    ) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    else {
                        pendingWebPermission?.deny()
                        pendingWebPermission = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                }
            }
        }
        webView.setDownloadListener { url, _, _, _, _ -> openExternal(Uri.parse(url)) }
    }

    private fun routeUrl(uri: Uri): Boolean {
        if (uri.scheme == "https" && uri.host in APP_HOSTS) return false
        openExternal(uri)
        return true
    }

    private fun connectMediaBrowser() {
        val token = SessionToken(this, ComponentName(this, RadioPlaybackService::class.java))
        browserFuture = MediaBrowser.Builder(this, token).buildAsync()
        browserFuture.addListener({
            browser = runCatching { browserFuture.get() }.getOrNull()
            if (playWhenConnected) {
                playWhenConnected = false
                playRadio()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playRadio() {
        val activeBrowser = browser
        if (activeBrowser == null) {
            playWhenConnected = true
            Toast.makeText(this, R.string.radio_connecting, Toast.LENGTH_SHORT).show()
            return
        }
        webView.evaluateJavascript("document.querySelectorAll('audio').forEach(audio => audio.pause())", null)
        CookieManager.getInstance().flush()
        val songsFuture = activeBrowser.getChildren(RadioCatalog.SONGS_ID, 0, 500, null)
        songsFuture.addListener({
            val songs = runCatching { songsFuture.get().value }.getOrNull()
            if (songs.isNullOrEmpty()) {
                Toast.makeText(this, R.string.radio_sign_in, Toast.LENGTH_LONG).show()
                return@addListener
            }
            activeBrowser.setMediaItems(songs)
            activeBrowser.prepare()
            activeBrowser.play()
        }, ContextCompat.getMainExecutor(this))
        Toast.makeText(this, R.string.radio_connecting, Toast.LENGTH_SHORT).show()
    }

    private fun openExternal(uri: Uri) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        MediaBrowser.releaseFuture(browserFuture)
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val APP_URL = "https://sntss1puebla.com/"
        private val APP_HOSTS = setOf("sntss1puebla.com", "www.sntss1puebla.com")
    }
}
