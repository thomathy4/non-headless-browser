package com.nonheadless.browser

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONTokener

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnGo: Button
    private lateinit var btnCapture: Button
    private lateinit var switchDomainLock: SwitchMaterial
    private lateinit var switchDesktopUa: SwitchMaterial
    private lateinit var switchJs: SwitchMaterial
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    private var lockedRootHost: String? = null
    private var lastHttpUrl = "https://example.com"
    private var activeSnapshotLabel: String? = null
    private var mobileUserAgent = ""
    private var suppressJsToggleReload = false
    private val desktopUserAgent =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnGo = findViewById(R.id.btnGo)
        btnCapture = findViewById(R.id.btnCapture)
        switchDomainLock = findViewById(R.id.switchDomainLock)
        switchDesktopUa = findViewById(R.id.switchDesktopUa)
        switchJs = findViewById(R.id.switchJs)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        configureWebView()
        bindControls()
        registerBackNavigation()
        etUrl.setText(lastHttpUrl)
        navigateTo(lastHttpUrl)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            mobileUserAgent = userAgentString
            javaScriptEnabled = false
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url ?: return true
                if (!request.isForMainFrame) return false
                val scheme = target.scheme?.lowercase()
                val host = extractNormalizedHost(target)
                if ((scheme != "http" && scheme != "https") || host == null) {
                    tvStatus.text = "Blocked unsupported navigation scheme or invalid host"
                    return true
                }
                val root = lockedRootHost
                if (switchDomainLock.isChecked && root != null && !isHostAllowed(host, root)) {
                    tvStatus.text = "Blocked off-domain navigation: " + host
                    Toast.makeText(this@MainActivity, "Domain lock blocked: " + host, Toast.LENGTH_SHORT).show()
                    return true
                }
                activeSnapshotLabel = null
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (activeSnapshotLabel == null && url != null &&
                    (url.startsWith("https://") || url.startsWith("http://"))) {
                    lastHttpUrl = url
                    etUrl.setText(url)
                    tvStatus.text = "Loading: " + url
                }
                updateNavButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val snapshot = activeSnapshotLabel
                if (snapshot != null) {
                    etUrl.setText(lastHttpUrl)
                    tvStatus.text = "Viewing " + snapshot + " | Source: " + lastHttpUrl
                } else if (url != null && url != "about:blank") {
                    lastHttpUrl = url
                    etUrl.setText(url)
                    val mode = if (switchDesktopUa.isChecked) "Desktop" else "Mobile"
                    val js = if (switchJs.isChecked) "JS:On" else "JS:Off"
                    tvStatus.text = "Active (" + mode + " | " + js + " | " +
                        (lockedRootHost ?: "unlocked") + "): " + url
                }
                updateNavButtons()
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    tvStatus.text = "Page load error: " + (error?.description ?: "unknown error")
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun bindControls() {
        btnGo.setOnClickListener { navigateTo(etUrl.text.toString()) }
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigateTo(etUrl.text.toString())
                true
            } else false
        }
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                activeSnapshotLabel = null
                webView.goBack()
            }
        }
        btnForward.setOnClickListener {
            if (webView.canGoForward()) {
                activeSnapshotLabel = null
                webView.goForward()
            }
        }
        switchDomainLock.setOnCheckedChangeListener { _, enabled ->
            lockedRootHost = if (enabled) extractNormalizedHost(Uri.parse(lastHttpUrl)) else null
            tvStatus.text = if (enabled) "Domain lock active: " + (lockedRootHost ?: "no valid host")
            else "Domain lock disabled (main-frame navigation)"
        }
        switchDesktopUa.setOnCheckedChangeListener { _, enabled ->
            webView.settings.userAgentString = if (enabled) desktopUserAgent else mobileUserAgent
            tvStatus.text = if (enabled) "Desktop User-Agent" else "Mobile User-Agent"
            reloadCurrentPage()
        }
        switchJs.setOnCheckedChangeListener { _, enabled ->
            webView.settings.javaScriptEnabled = enabled
            webView.settings.domStorageEnabled = enabled
            tvStatus.text = if (enabled) "JavaScript enabled" else "JavaScript disabled"
            if (!suppressJsToggleReload) reloadCurrentPage()
        }
        btnCapture.setOnClickListener { snapshotRenderedHtml() }
    }

    private fun reloadCurrentPage() {
        if (activeSnapshotLabel != null) navigateTo(lastHttpUrl)
        else if (webView.url != null) webView.reload()
    }

    private fun normalizeUrl(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        val localHost = isLocalDevelopmentAddress(input)
        val url = when {
            input.startsWith("https://", true) || input.startsWith("http://", true) -> input
            localHost -> "http://" + input
            else -> "https://" + input
        }
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if ((scheme != "http" && scheme != "https") || extractNormalizedHost(uri) == null) return null
        return url
    }

    private fun isLocalDevelopmentAddress(input: String): Boolean {
        val rawHost = Uri.parse("http://" + input).host?.lowercase() ?: return false
        val host = rawHost.removePrefix("[").removeSuffix("]")
        if (host == "::1" || host == "localhost" || host.endsWith(".localhost")) return true
        val octets = host.split(".")
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in 0..255 }) return false
        val first = values[0]
        val second = values[1]
        return first == 10 || first == 127 ||
            (first == 192 && second == 168) ||
            (first == 172 && second in 16..31)
    }

    private fun navigateTo(raw: String) {
        val url = normalizeUrl(raw)
        if (url == null) {
            Toast.makeText(this, "Enter a valid HTTP or HTTPS URL", Toast.LENGTH_SHORT).show()
            return
        }
        val host = extractNormalizedHost(Uri.parse(url)) ?: return
        if (switchDomainLock.isChecked) lockedRootHost = host
        activeSnapshotLabel = null
        lastHttpUrl = url
        etUrl.setText(url)
        webView.loadUrl(url)
    }

    private fun extractNormalizedHost(uri: Uri?): String? {
        val host = uri?.host?.lowercase() ?: return null
        return if (host.startsWith("www.")) host.removePrefix("www.") else host
    }

    private fun isHostAllowed(host: String, root: String): Boolean =
        host == root || host.endsWith("." + root)

    private fun updateNavButtons() {
        btnBack.isEnabled = webView.canGoBack()
        btnForward.isEnabled = webView.canGoForward()
    }

    private fun snapshotRenderedHtml() {
        tvStatus.text = "Extracting the rendered DOM snapshot..."
        val sourceUrl = lastHttpUrl
        webView.evaluateJavascript(
            "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()"
        ) { result ->
            val html = try {
                if (result == null) "" else JSONTokener(result).nextValue() as? String ?: ""
            } catch (_: Exception) { "" }
            if (html.isBlank()) {
                tvStatus.text = "Snapshot returned no document HTML"
                Toast.makeText(this, "No HTML was returned by WebView", Toast.LENGTH_LONG).show()
                return@evaluateJavascript
            }
            showSnapshotDialog(html, HtmlReencoder.reencode(html, sourceUrl), sourceUrl)
        }
    }

    private fun showSnapshotDialog(raw: String, neutralized: String, sourceUrl: String) {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val toggle = CheckBox(this).apply {
            text = "Re-encode scripts and inline event handlers"
            isChecked = true
        }
        val render = Button(this).apply { text = "Render in Browser" }
        val preview = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
        }
        val scroll = ScrollView(this).apply { addView(preview) }
        container.addView(toggle)
        container.addView(render)
        container.addView(scroll)
        var dialog: AlertDialog? = null
        fun chosen(): String = if (toggle.isChecked) neutralized else raw
        fun label(): String = if (toggle.isChecked) "Script-neutralized HTML" else "Raw HTML"
        fun refresh() {
            val html = chosen()
            dialog?.setTitle(label() + " (" + html.length + " chars)")
            preview.text = if (html.length > 6000) html.take(6000) + "\\n\\n... [preview truncated]" else html
            tvStatus.text = "Snapshot prepared: " + html.length + " characters (" + label() + ")"
        }
        toggle.setOnCheckedChangeListener { _, _ -> refresh() }
        render.setOnClickListener {
            val html = chosen()
            val mode = label()
            dialog?.dismiss()
            renderHtmlInWebView(html, mode, sourceUrl)
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Script-neutralized HTML (" + neutralized.length + " chars)")
            .setView(container)
            .setPositiveButton("Copy HTML") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Rendered DOM snapshot", chosen()))
                Toast.makeText(this, "Copied " + chosen().length + " characters", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Share HTML") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/html"
                    putExtra(Intent.EXTRA_SUBJECT, "Rendered DOM snapshot - " + sourceUrl)
                    putExtra(Intent.EXTRA_TEXT, chosen())
                }
                startActivity(Intent.createChooser(send, "Share HTML document"))
            }
            .setNegativeButton("Close", null)
            .create()
        refresh()
        dialog.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun renderHtmlInWebView(html: String, label: String, baseUrl: String) {
        suppressJsToggleReload = true
        switchJs.isChecked = false
        suppressJsToggleReload = false
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
        activeSnapshotLabel = label + " (" + html.length + " chars)"
        tvStatus.text = "Rendering " + label + "..."
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", baseUrl)
    }

    private fun registerBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    activeSnapshotLabel = null
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
