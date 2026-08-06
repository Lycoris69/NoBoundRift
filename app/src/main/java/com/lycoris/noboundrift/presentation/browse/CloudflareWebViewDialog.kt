package com.lycoris.noboundrift.presentation.browse

import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lycoris.noboundrift.data.remote.NetworkClient

/**
 * Full-screen dialog that opens a [WebView] pointed at [targetUrl] so that Cloudflare's
 * JS challenge can run with a real browser engine. Once the challenge is solved,
 * [CookieManager] will contain a valid `cf_clearance` cookie; the dialog extracts it,
 * injects it into OkHttp's cookie jar via [NetworkClient.injectWebViewCookies], and
 * calls [onSuccess] so the caller can retry the blocked request.
 *
 * The WebView uses the same [NetworkClient.USER_AGENT] string as OkHttp — Cloudflare
 * ties `cf_clearance` to the UA hash, so they must match exactly.
 */
@Composable
fun CloudflareWebViewDialog(
    targetUrl: String,
    host: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Enable cookies in the WebView's shared CookieManager (may already be on, but safe to repeat).
    remember { CookieManager.getInstance().setAcceptCookie(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Toolbar with dismiss button
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = "Solving Cloudflare challenge…",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = NetworkClient.USER_AGENT

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    val cookies = CookieManager.getInstance()
                                        .getCookie(targetUrl) ?: return
                                    if ("cf_clearance" in cookies) {
                                        // Sync cookies from WebView into OkHttp's jar, then signal
                                        // success so the caller can retry the blocked request.
                                        NetworkClient.injectWebViewCookies(host, cookies)
                                        onSuccess()
                                    }
                                }

                                // Keep navigation inside the WebView (don't open Chrome).
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ) = false
                            }

                            loadUrl(targetUrl)
                        }
                    },
                )
            }
        }
    }
}
