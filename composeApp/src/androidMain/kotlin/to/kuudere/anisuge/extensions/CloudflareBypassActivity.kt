package to.kuudere.anisuge.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView

class CloudflareBypassActivity : Activity() {

    private var webView: WebView? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var errorView: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }

        errorView = TextView(this).apply {
            text = "Loading..."
            setTextColor(Color.parseColor("#FFFFFF"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            visibility = View.GONE
        }

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
        }

        try {
            webView = WebView(this).also { wv ->
                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        progressBar.visibility = View.GONE
                        errorView.visibility = View.GONE
                        if (url != null) syncCookiesFromView(url)
                    }

                    override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        val msg = "Error: ${error?.description ?: "Unknown"}"
                        Log.e(TAG, msg)
                        errorView.text = msg
                        errorView.visibility = View.VISIBLE
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        return false
                    }
                }
                wv.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progressBar.progress = newProgress
                        progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebView creation failed: ${e.message}", e)
            errorView.text = "WebView not available: ${e.message}"
            errorView.visibility = View.VISIBLE
            webView = null
        }

        webView?.let { wv ->
            root.addView(wv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }

        root.addView(progressBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 6
        ).apply { gravity = android.view.Gravity.TOP })

        root.addView(errorView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)

        val url = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_URL
        Log.i(TAG, "Loading URL: $url")
        webView?.loadUrl(url)
    }

    private fun syncCookiesFromView(url: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        if (!cookies.isNullOrEmpty()) {
            Log.i(TAG, "Synced cookies for $url: ${cookies.take(80)}${if (cookies.length > 80) "..." else ""}")
        }
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CloudflareBypass"
        const val EXTRA_URL = "extra_url"
        private const val DEFAULT_URL = "https://google.com"
    }
}
