package org.koitharu.kotatsu.comments

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class GunClient(private val context: Context) {

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _events = Channel<GunEvent>(Channel.UNLIMITED)
    val events: Flow<GunEvent> = _events.receiveAsFlow()

    @SuppressLint("SetJavaScriptEnabled")
    fun start() {
        if (webView != null) return

        webView = WebView(context).apply {
            visibility = WebView.GONE // Keep it hidden
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_NO_CACHE // Ensure fresh gun.html
            addJavascriptInterface(this@GunClient, "Android")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.endsWith("gun.html") == true) {
                        // Set initial relays from settings
                        setRelay(CommentsSettings.relayUrls.value)
                        _events.trySend(GunEvent.Ready)
                    }
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    _events.trySend(GunEvent.Error(failingUrl ?: "unknown", description ?: "unknown"))
                }
            }
            loadUrl("file:///android_asset/gun.html")
        }
    }

    fun stop() {
        webView?.destroy()
        webView = null
        _events.close()
    }

    @JavascriptInterface
    fun postMessage(message: String) {
        try {
            val json = JSONObject(message)
            when (json.optString("event")) {
                "newComment" -> {
                    val commentJson = json.getJSONObject("comment")
                    _events.trySend(GunEvent.NewComment(Comment.fromJson(commentJson)))
                }
                "commentDeleted" -> {
                    _events.trySend(GunEvent.CommentDeleted(json.getString("commentId")))
                }
                "commentDeletedAck" -> {
                    _events.trySend(GunEvent.CommentDeletedAck(json.getString("commentId")))
                }
                "commentSentAck" -> {
                    val commentJson = json.getJSONObject("comment")
                    _events.trySend(GunEvent.CommentSentAck(Comment.fromJson(commentJson)))
                }
                "commentReportedAck" -> {
                    _events.trySend(GunEvent.CommentReportedAck(json.getString("commentId"), json.optInt("newReportCount")))
                }
                "error" -> {
                    _events.trySend(GunEvent.Error(json.optString("mangaId"), json.getString("message")))
                }
                "log" -> {
                    // Log messages from JS for debugging
                    println("GunClient JS Log: ${json.getString("message")}")
                }
            }
        } catch (e: JSONException) {
            println("GunClient JSON Error: $e, Message: $message")
            _events.trySend(GunEvent.Error("JSON_PARSE_ERROR", e.message ?: "Unknown JSON error"))
        }
    }

    private suspend fun evaluateJavascript(script: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine {\n            continuation ->
                handler.post {
                    webView?.evaluateJavascript(script) { it ->
                        if (continuation.isActive) {
                            continuation.resume(it)
                        }
                    }
                    ?: continuation.resumeWithException(IllegalStateException("WebView not initialized"))
                }
        }
    }

    suspend fun subscribe(mangaId: String) {
        evaluateJavascript("window.CommentAPI.subscribe('$mangaId');")
    }

    suspend fun unsubscribe() {
        evaluateJavascript("window.CommentAPI.unsubscribe();")
    }

    suspend fun sendComment(mangaId: String, comment: Comment) {
        val commentJson = comment.toJson().toString()
        evaluateJavascript("window.CommentAPI.sendComment('$mangaId', $commentJson);")
    }

    suspend fun deleteComment(mangaId: String, commentId: String) {
        evaluateJavascript("window.CommentAPI.deleteComment('$mangaId', '$commentId');")
    }

    suspend fun reportComment(mangaId: String, commentId: String) {
        evaluateJavascript("window.CommentAPI.reportComment('$mangaId', '$commentId');")
    }

    suspend fun setRelay(relayUrls: List<String>) {
        val jsonArray = JSONArray(relayUrls)
        evaluateJavascript("window.CommentAPI.setRelay(${jsonArray.toString()});")
    }

    sealed class GunEvent {
        object Ready : GunEvent()
        data class NewComment(val comment: Comment) : GunEvent()
        data class CommentDeleted(val commentId: String) : GunEvent()
        data class CommentSentAck(val comment: Comment) : GunEvent()
        data class CommentDeletedAck(val commentId: String) : GunEvent()
        data class CommentReportedAck(val commentId: String, val newReportCount: Int) : GunEvent()
        data class Error(val mangaId: String, val message: String) : GunEvent()
    }
}
