package app.mawaqit.mosque

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

class MainActivity : Activity() {

    private lateinit var web: WebView
    @Volatile private var settingsOpen = false
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null

    // يُحقن بعد تحميل الصفحة: يتابع فتح/إغلاق الخيارات، ويخفي زر ملء الشاشة (غير لازم داخل التطبيق)
    private val hook = """
        (function(){
          if(window.__hk) return; window.__hk = 1;
          var st = document.createElement('style');
          st.textContent = '#fs,#fs2{display:none!important}';
          document.head.appendChild(st);
          var s = document.getElementById('st');
          if(s){
            var f = function(){ Android.settingsState(s.classList.contains('on')); };
            new MutationObserver(f).observe(s, {attributes:true, attributeFilter:['class']});
            f();
          }
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideBars()

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        setContentView(web)
        web.setBackgroundColor(0xFF0B2A24.toInt())

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            setGeolocationEnabled(true)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        web.isFocusable = true
        web.isFocusableInTouchMode = true
        web.addJavascriptInterface(Bridge(), "Android")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                loader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(hook, null)
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                if (Build.VERSION.SDK_INT < 23 ||
                    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                ) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeo = origin to callback
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 7
                    )
                }
            }
        }

        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        pendingGeo?.let { (origin, cb) ->
            cb.invoke(origin, grantResults.any { it == PackageManager.PERMISSION_GRANTED }, false)
        }
        pendingGeo = null
    }

    // أزرار الريموت: OK/Enter يفتح الخيارات (أو يُظهر الشاشة المخفية)، Menu يبدّل الخيارات، Back يغلقها
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.repeatCount == 0) {
                val js = if (settingsOpen) "document.getElementById('close').click()"
                else "document.getElementById('gear').click()"
                web.evaluateJavascript(js, null)
            }
            return true
        }
        if (ok && !settingsOpen) {
            if (event.repeatCount == 0) {
                web.evaluateJavascript(
                    "(function(){var o=document.getElementById('ov');" +
                            "if(o&&o.classList.contains('on')){o.click()}else{document.getElementById('gear').click()}})()",
                    null
                )
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && settingsOpen) {
            web.evaluateJavascript("document.getElementById('close').click()", null)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun hideBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
        hideBars()
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    /** الواجهة التي تستدعيها الصفحة: window.Android */
    inner class Bridge {
        @JavascriptInterface
        fun isApp(): Boolean = true

        @JavascriptInterface
        fun settingsState(open: Boolean) { settingsOpen = open }

        @JavascriptInterface
        fun keepScreenOn(on: Boolean) {
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        @JavascriptInterface
        fun setAutoStart(enabled: Boolean) {
            val prefs = getSharedPreferences("cfg", Context.MODE_PRIVATE)
            val was = prefs.getBoolean("auto", false)
            prefs.edit().putBoolean("auto", enabled).apply()
            // من أندرويد 10 يلزم إذن "العرض فوق التطبيقات الأخرى" ليبدأ التطبيق من الإقلاع
            if (enabled && !was && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this@MainActivity)) {
                runOnUiThread {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                        )
                    } catch (_: Exception) { }
                }
            }
        }
    }
}
