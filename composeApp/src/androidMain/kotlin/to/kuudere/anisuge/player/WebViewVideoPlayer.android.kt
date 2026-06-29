package to.kuudere.anisuge.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val TAG = "WebViewPlayer"

private val MEGA_ALLOWED_HOSTS = setOf(
    "meg.anisurge.lol",
    "megaplay.buzz",
    "megacloud.blog",
    "megacloud.tv",
    "megacloud.store",
    "megacloud.club",
    "vidtube.site",
    "anikototv.to",
)

private val MEGA_BLOCKED_HOSTS = listOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adnxs.com",
    "propellerads.com",
    "popads.net",
    "popcash.net",
    "go2cloud.org",
    "onclickads.net",
    "exoclick.com",
    "trafficjunky.net",
    "taboola.com",
    "outbrain.com",
)

private val MEGA_BLOCKED_KEYWORDS = listOf(
    "/ads",
    "/ad/",
    "advert",
    "banner",
    "popup",
    "popunder",
    "tracking",
    "analytics",
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun hostMatches(host: String, allowedHost: String): Boolean =
    host == allowedHost || host.endsWith(".$allowedHost")

private fun emptyWebResponse(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    "utf-8",
    ByteArrayInputStream(ByteArray(0)),
)

private fun htmlAttrEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

private fun iframeShellHtml(embedUrl: String): String {
    val escapedUrl = htmlAttrEscape(embedUrl)
    return """
        <!doctype html>
        <html>
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
          <style>
            html, body { margin:0; padding:0; width:100%; height:100%; background:#000; overflow:hidden; }
            iframe { display:block; width:100%; height:100%; border:0; background:#000; }
          </style>
        </head>
        <body>
          <iframe id="megaplay-player" src="$escapedUrl" width="100%" height="100%" frameborder="0" scrolling="no" allow="autoplay; fullscreen" allowfullscreen></iframe>
          <script>
            (function() {
              var iframe = document.getElementById('megaplay-player');
              function parseMessage(raw) {
                try { return typeof raw === 'string' ? JSON.parse(raw) : raw; } catch (e) { return null; }
              }
              function seekTo(seconds) {
                seconds = Number(seconds || 0);
                if (!iframe || !iframe.contentWindow || !isFinite(seconds) || seconds <= 0) return;
                iframe.contentWindow.postMessage(JSON.stringify({ cmd: 'SEEK', value: seconds }), '*');
              }
              window.__anisurgeMegaplaySeek = seekTo;
              iframe.onload = function() {
                var start = Number(Android.getStartPositionSeconds ? Android.getStartPositionSeconds() : 0);
                if (isFinite(start) && start > 0) {
                  setTimeout(function() { seekTo(start); }, 1000);
                  setTimeout(function() { seekTo(start); }, 2500);
                }
                Android.onReady();
              };
              window.addEventListener('message', function(event) {
                if (event.origin && event.origin !== 'https://megaplay.buzz' && event.origin !== 'https://meg.anisurge.lol') return;
                var data = parseMessage(event.data);
                if (!data) return;
                var position = NaN;
                var duration = NaN;
                if (data.type === 'progress') {
                  position = Number(data.currentTime || 0);
                  duration = Number(data.duration || 0);
                } else if (data.type === 'complete') {
                  Android.onEnded();
                  return;
                } else if (data.channel === 'megacloud' && data.event === 'time') {
                  position = Number(data.time || 0);
                  duration = Number(data.duration || 0);
                } else if (data.event === 'PLAY_TIMING' && data.data) {
                  position = Number(data.data.position || 0);
                  duration = Number(data.data.duration || 0);
                } else {
                  return;
                }
                if (isFinite(position) && position >= 0) Android.onTimeUpdate(position);
                if (isFinite(duration) && duration > 0) Android.onDuration(duration);
                Android.onBuffering(false);
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
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
    private var lastLoggedPosition = -1
    @JavascriptInterface fun getStartPositionSeconds(): Double = state.config.startPosition.coerceAtLeast(0.0)
    @JavascriptInterface fun onReady()                { state.hasLoadedMedia = true; state.error = null }
    @JavascriptInterface fun onPlay()                 { state.isPlaying = true; state.isPaused = false; state.isBuffering = false }
    @JavascriptInterface fun onPause()                { state.isPaused = true; state.isPlaying = false }
    @JavascriptInterface fun onEnded()                { state.isPlaying = false; onFinishedCallback?.invoke() }
    @JavascriptInterface fun onBuffering(buffering: Boolean) { state.isBuffering = buffering }
    @JavascriptInterface fun onDuration(dur: Double)  { state.duration = dur; if (dur > state.peakPlaybackDuration) state.peakPlaybackDuration = dur }
    @JavascriptInterface fun onTimeUpdate(pos: Double) {
        state.position = pos
        if (pos > state.peakPlaybackPosition) state.peakPlaybackPosition = pos
        val whole = pos.toInt()
        if (whole >= 0 && whole / 5 != lastLoggedPosition / 5) {
            lastLoggedPosition = whole
            Log.d(TAG, "megaplay progress position=$pos duration=${state.duration}")
        }
    }
    @JavascriptInterface fun onError(msg: String) {
        Log.e(TAG, "JS error: $msg")
        state.error = "Stream error: $msg"
        state.isBuffering = false
    }

    @JavascriptInterface fun postMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("type")) {
                "progress" -> {
                    val position = json.optDouble("currentTime", 0.0)
                    val duration = json.optDouble("duration", 0.0)
                    if (position >= 0.0) onTimeUpdate(position)
                    if (duration > 0.0) onDuration(duration)
                    onBuffering(false)
                }
                "complete" -> onEnded()
            }
        } catch (_: Exception) {
        }
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
    val directWebViewHost = remember(embedUrl) {
        embedUrl.substringAfter("://", embedUrl).substringBefore("/")
            .removePrefix("www.")
            .lowercase()
    }
    val shouldLoadDirectly = directWebViewHost == "meg.anisurge.lol" || directWebViewHost == "vidtube.site"
    val shouldUseMegaGuards = directWebViewHost == "meg.anisurge.lol" || directWebViewHost == "vidtube.site"

    // Build the WebView once per URL; a new URL means a new video session.
    val webView = remember(embedUrl) {
        val activity = context.findActivity()
        var fullscreenView: View? = null
        var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
        var previousSystemUiVisibility: Int? = null
        var previousRequestedOrientation: Int? = null

        fun hideFullscreenView() {
            val view = fullscreenView ?: return
            val decor = activity?.window?.decorView as? ViewGroup
            decor?.removeView(view)
            previousSystemUiVisibility?.let { activity?.window?.decorView?.systemUiVisibility = it }
            previousRequestedOrientation?.let { activity?.requestedOrientation = it }
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = null
            fullscreenCallback = null
            previousSystemUiVisibility = null
            previousRequestedOrientation = null
        }

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
                javaScriptCanOpenWindowsAutomatically = false
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
            addJavascriptInterface(
                PlayerBridge(state, { currentOnFinished?.invoke() }),
                "ReactNativeWebView",
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
                    if (!shouldUseMegaGuards) return false
                    val host = request.url.host?.removePrefix("www.") ?: return true
                    val allowed = MEGA_ALLOWED_HOSTS.any { hostMatches(host, it) }
                    return !allowed
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    if (!shouldUseMegaGuards) return null
                    val host = request.url.host?.removePrefix("www.") ?: return null
                    val url = request.url.toString()
                    val shouldBlock = MEGA_BLOCKED_HOSTS.any { blocked -> hostMatches(host, blocked) } ||
                        MEGA_BLOCKED_KEYWORDS.any { keyword -> url.contains(keyword, ignoreCase = true) }
                    if (shouldBlock) {
                        Log.d(TAG, "blocked mega ad request: $url")
                        return emptyWebResponse()
                    }
                    return null
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?,
                ): Boolean {
                    return false
                }

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    val decor = activity?.window?.decorView as? ViewGroup
                    if (decor == null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    if (fullscreenView != null) hideFullscreenView()
                    fullscreenView = view
                    fullscreenCallback = callback
                    previousSystemUiVisibility = activity.window.decorView.systemUiVisibility
                    previousRequestedOrientation = activity.requestedOrientation
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    view.setBackgroundColor(AndroidColor.BLACK)
                    decor.addView(
                        view,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    activity.window.decorView.systemUiVisibility =
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                }

                override fun onHideCustomView() {
                    hideFullscreenView()
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(TAG, "[JS] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                    return true
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress < 100) state.isBuffering = true
                }
            }

            if (shouldLoadDirectly) {
                Log.d(TAG, "loading webview url=$embedUrl")
                loadUrl(embedUrl)
            } else {
                // Megaplay only works when embedded as an iframe. Keep the iframe markup
                // exactly like the provider expects and use our own neutral parent origin.
                val exactIframe = "<iframe src=\"${htmlAttrEscape(embedUrl)}\" width=\"100%\" height=\"100%\" frameborder=\"0\" scrolling=\"no\" allow=\"autoplay; fullscreen\" allowfullscreen></iframe>"
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
                "if(window.__anisurgeMegaplaySeek) window.__anisurgeMegaplaySeek(${target}); else if(window.__anisurgePlayer) window.__anisurgePlayer.seek(${target});",
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
