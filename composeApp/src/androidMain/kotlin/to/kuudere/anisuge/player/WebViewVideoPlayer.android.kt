package to.kuudere.anisuge.player

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "WebViewPlayer"

private fun htmlAttrEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun iframeShellHtml(embedUrl: String): String {
    val escapedUrl = htmlAttrEscape(embedUrl)
    return "<iframe src=\"$escapedUrl\" width=\"100%\" height=\"100%\" frameborder=\"0\" scrolling=\"no\" allowfullscreen></iframe>"
}

/**
 * JS injected after every page load.
 * Finds the <video> element (retrying up to ~5s), then:
 *  - Forwards play/pause/timeupdate/durationchange/waiting/playing/ended/error events to Kotlin.
 *  - Exposes window.__anisurgePlayer for imperative control (play/pause/seek/speed/mute/volume).
 */
private val PLAYER_JS = """
(function() {
  var MAX_TRIES = 50;
  var tries = 0;
  function findAndBind() {
    var video = document.querySelector('video');
    if (!video) {
      if (++tries < MAX_TRIES) { setTimeout(findAndBind, 100); }
      else {
        // Cross-origin iframe players (Megaplay, etc.) hide their internal video
        // element from the host page. That is expected; mark the iframe ready and
        // let the provider's player controls handle playback.
        Android.onReady();
      }
      return;
    }

    // Control API
    window.__anisurgePlayer = {
      play:    function() { video.play().catch(function(){}); },
      pause:   function() { video.pause(); },
      seek:    function(t) { video.currentTime = t; },
      speed:   function(s) { video.playbackRate = s; },
      mute:    function(m) { video.muted = m; },
      volume:  function(v) { video.volume = Math.max(0, Math.min(1, v)); },
    };

    // Events → Kotlin bridge
    video.addEventListener('play',           function() { Android.onPlay(); });
    video.addEventListener('pause',          function() { Android.onPause(); });
    video.addEventListener('ended',          function() { Android.onEnded(); });
    video.addEventListener('waiting',        function() { Android.onBuffering(true); });
    video.addEventListener('playing',        function() { Android.onBuffering(false); });
    video.addEventListener('durationchange', function() {
      if (video.duration && isFinite(video.duration)) {
        Android.onDuration(video.duration);
      }
    });
    video.addEventListener('timeupdate', function() {
      Android.onTimeUpdate(video.currentTime);
    });
    video.addEventListener('error', function(e) {
      var msg = video.error ? ('code=' + video.error.code + ' ' + (video.error.message || '')) : 'unknown';
      Android.onError(msg);
    });

    // If video already has metadata (e.g. resumed page) push it immediately
    if (video.duration && isFinite(video.duration)) {
      Android.onDuration(video.duration);
    }
    if (video.currentTime > 0) {
      Android.onTimeUpdate(video.currentTime);
    }
    Android.onReady();
  }
  findAndBind();
})();
""".trimIndent()

/** Kotlin-side JS interface injected as "Android" into the WebView. */
private class PlayerBridge(
    private val state: VideoPlayerState,
    private val onFinishedCallback: (() -> Unit)?,
) {
    @JavascriptInterface fun onReady()                { state.hasLoadedMedia = true; state.error = null }
    @JavascriptInterface fun onPlay()                 { state.isPlaying = true; state.isPaused = false; state.isBuffering = false }
    @JavascriptInterface fun onPause()                { state.isPaused = true; state.isPlaying = false }
    @JavascriptInterface fun onEnded()                { state.isPlaying = false; onFinishedCallback?.invoke() }
    @JavascriptInterface fun onBuffering(buffering: Boolean) { state.isBuffering = buffering }
    @JavascriptInterface fun onDuration(dur: Double)  { state.duration = dur; if (dur > state.peakPlaybackDuration) state.peakPlaybackDuration = dur }
    @JavascriptInterface fun onTimeUpdate(pos: Double) {
        state.position = pos
        if (pos > state.peakPlaybackPosition) state.peakPlaybackPosition = pos
    }
    @JavascriptInterface fun onError(msg: String) {
        Log.e(TAG, "JS error: $msg")
        state.error = "Stream error: $msg"
        state.isBuffering = false
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebViewVideoPlayerSurface(
    embedUrl: String,
    state: VideoPlayerState,
    modifier: Modifier,
    headers: Map<String, String>?,
    onFinished: (() -> Unit)?,
) {
    val context = LocalContext.current
    val currentOnFinished by rememberUpdatedState(onFinished)

    // Build the WebView once per URL; a new URL means a new video session.
    val webView = remember(embedUrl) {
        @SuppressLint("SetJavaScriptEnabled")
        WebView(context).apply {
            WebView.setWebContentsDebuggingEnabled(true)
            setBackgroundColor(AndroidColor.BLACK)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                loadsImagesAutomatically = true
                // Desktop UA — many embed players block mobile UA or show a different player
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                builtInZoomControls = false
                displayZoomControls = false
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            addJavascriptInterface(
                PlayerBridge(state, { currentOnFinished?.invoke() }),
                "Android",
            )

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    state.isBuffering = true
                    state.hasLoadedMedia = false
                }

                override fun onPageFinished(view: WebView, url: String) {
                    state.isBuffering = false
                    // Inject our control/event bridge JS
                    view.evaluateJavascript(PLAYER_JS, null)
                    // If autoPlay is set, try pressing play after a short delay to let the
                    // embed's own JS initialise its player first.
                    if (state.config.autoPlay) {
                        view.postDelayed({
                            view.evaluateJavascript(
                                "if(window.__anisurgePlayer) window.__anisurgePlayer.play();",
                                null,
                            )
                        }, 800)
                    }
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        state.error = "Failed to load embed page"
                        state.isBuffering = false
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    // Stay inside the WebView for all navigation — don't launch external apps.
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(TAG, "[JS] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                    return true
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress < 100) state.isBuffering = true
                }
            }

            // Megaplay only works when embedded as an iframe. Keep the iframe markup
            // exactly like the provider expects and use our own neutral parent origin.
            val exactIframe = "<iframe src=\"${htmlAttrEscape(embedUrl)}\" width=\"100%\" height=\"100%\" frameborder=\"0\" scrolling=\"no\" allowfullscreen></iframe>"
            val html = iframeShellHtml(embedUrl)
            Log.d(TAG, "loading iframe shell exact=$exactIframe")
            loadDataWithBaseURL(
                "https://anisurge.local/",
                html,
                "text/html",
                "UTF-8",
                null,
            )
        }
    }

    // ── React to app-side control commands ──────────────────────────────────

    LaunchedEffect(state.pauseRequested) {
        val js = if (state.pauseRequested) "pause" else "play"
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.$js();",
                null,
            )
        }
        state.isPaused = state.pauseRequested
    }

    LaunchedEffect(state.seekTarget) {
        val target = state.seekTarget ?: return@LaunchedEffect
        state.seekTarget = null
        state.lastUserSeekAtMs = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.seek(${target});",
                null,
            )
        }
        state.position = target
    }

    LaunchedEffect(state.playbackSpeed) {
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.speed(${state.playbackSpeed});",
                null,
            )
        }
    }

    LaunchedEffect(state.isMuted) {
        val m = if (state.isMuted) "true" else "false"
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.mute($m);",
                null,
            )
        }
    }

    LaunchedEffect(state.volume) {
        val v = state.volume / 100.0
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.volume($v);",
                null,
            )
        }
    }

    // Screenshot is not supported for iframe servers
    LaunchedEffect(state.screenshotRequestCount) {
        if (state.screenshotRequestCount > 0) {
            state.screenshotResult = "Screenshot not supported for iframe servers"
        }
    }

    // Keep screen on while playing
    LaunchedEffect(state.isPlaying, state.isPaused, state.isBuffering) {
        webView.keepScreenOn = (state.isPlaying || state.isBuffering) && !state.isPaused
    }

    // Dispose: stop the WebView when leaving composition
    DisposableEffect(embedUrl) {
        onDispose {
            webView.evaluateJavascript(
                "if(window.__anisurgePlayer) window.__anisurgePlayer.pause();",
                null,
            )
            webView.stopLoading()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
