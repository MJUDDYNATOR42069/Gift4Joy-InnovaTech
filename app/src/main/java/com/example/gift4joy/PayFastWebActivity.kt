package com.example.gift4joy

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class PayFastWebActivity : AppCompatActivity() {
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val html = intent.getStringExtra(EXTRA_HTML)
        val orderId = intent.getStringExtra(EXTRA_ORDER_ID)
        if (html.isNullOrBlank() || orderId.isNullOrBlank()) { setResult(Activity.RESULT_CANCELED); finish(); return }
        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = false
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url ?: return false
                if (url.scheme == "gift4joy" && url.host in listOf("payment-return", "payment-cancel")) {
                    setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_ORDER_ID, orderId).putExtra(EXTRA_CANCELLED, url.host == "payment-cancel"))
                    finish()
                    return true
                }
                return false
            }
            @Deprecated("Deprecated in Android API")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val uri = android.net.Uri.parse(url ?: return false)
                if (uri.scheme == "gift4joy" && uri.host in listOf("payment-return", "payment-cancel")) {
                    setResult(Activity.RESULT_OK, intent.putExtra(EXTRA_ORDER_ID, orderId).putExtra(EXTRA_CANCELLED, uri.host == "payment-cancel"))
                    finish()
                    return true
                }
                return false
            }
        }
        webView.loadDataWithBaseURL(BuildConfig.API_BASE_URL, html, "text/html", "UTF-8", null)
    }
    companion object {
        const val EXTRA_HTML = "payfast_checkout_html"
        const val EXTRA_ORDER_ID = "payfast_order_id"
        const val EXTRA_CANCELLED = "payfast_cancelled"
    }
}
