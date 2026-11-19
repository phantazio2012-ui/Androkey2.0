package com.cgsi.clavier

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var hid: HidKeyboard
    private lateinit var gyro: GyroMouse
    private lateinit var updater: Updater

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hid = HidKeyboard(
            this,
            onState = { connected, name ->
                runOnUiThread {
                    web.evaluateJavascript(
                        "window.onBtState($connected, ${JSONObject.quote(name)})", null
                    )
                }
            },
            onMsg = { msg ->
                runOnUiThread {
                    web.evaluateJavascript("window.onBtMsg(${JSONObject.quote(msg)})", null)
                }
            }
        )
        updater = Updater(this)
        gyro = GyroMouse(this) { dx, dy -> hid.sendMouse(hid.lastButtons, dx, dy, 0); Unit }

        web = WebView(this).apply {
            setBackgroundColor(0xFF0B3D91.toInt())
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("file:///android_asset/keyboard.html")
        }
        setContentView(web)
        hideSystemBars()

        if (hasBtPermission()) hid.start() else requestBtPermission()
    }

    // ---- Pont JavaScript -> Android (objet "Android" côté page) ----
    inner class Bridge {
        @JavascriptInterface fun sendKey(code: Int, mods: Int) = hid.sendKey(code, mods)
        @JavascriptInterface fun mouse(dx: Int, dy: Int, wheel: Int, buttons: Int): Int = hid.sendMouse(buttons, dx, dy, wheel)
        @JavascriptInterface fun getDevices(): String = hid.pairedDevices()
        @JavascriptInterface fun disconnect() = hid.disconnect()
        @JavascriptInterface fun connect(address: String) {
            if (!hasBtPermission()) runOnUiThread { requestBtPermission() } else hid.connect(address)
        }
        @JavascriptInterface fun discoverable() {
            runOnUiThread {
                startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                        .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
                )
            }
        }

        /** Active/désactive la souris par mouvement (gyroscope). Renvoie false si le capteur est absent. */
        @JavascriptInterface fun gyro(on: Boolean, sens: Double, invX: Boolean, invY: Boolean): Boolean {
            gyro.sensitivity = sens; gyro.invertX = invX; gyro.invertY = invY
            if (!on) { gyro.stop(); return true }
            gyro.stop()
            return gyro.start()
        }

        // ---- Diagnostic Bluetooth ----
        @JavascriptInterface fun diag(): String = hid.diag()
        @JavascriptInterface fun unpair(address: String): Boolean = hid.unpair(address)
        @JavascriptInterface fun openBtSettings() {
            runOnUiThread { runCatching { startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) } }
        }

        // ---- Mise à jour automatique ----
        @JavascriptInterface fun appInfo(): String = updater.currentInfo()
        @JavascriptInterface fun checkUpdate() {
            updater.check { json ->
                runOnUiThread { web.evaluateJavascript("window.onUpdateInfo && window.onUpdateInfo($json)", null) }
            }
        }
        @JavascriptInterface fun downloadUpdate() { updater.download(
            progress = { p -> runOnUiThread { web.evaluateJavascript("window.onUpdateProgress && window.onUpdateProgress($p)", null) } },
            done = { err ->
                runOnUiThread {
                    if (err != null) web.evaluateJavascript("window.onUpdateError && window.onUpdateError(${JSONObject.quote(err)})", null)
                    else {
                        val msg = updater.install()
                        web.evaluateJavascript("window.onUpdateReady && window.onUpdateReady(${JSONObject.quote(msg ?: "")})", null)
                    }
                }
            }
        ) }
        @JavascriptInterface fun installUpdate() {
            runOnUiThread {
                val msg = updater.install()
                web.evaluateJavascript("window.onUpdateReady && window.onUpdateReady(${JSONObject.quote(msg ?: "")})", null)
            }
        }

        @JavascriptInterface fun openUrl(url: String) {
            if (!url.startsWith("https://")) return
            runOnUiThread {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }
    }

    // ---- Permissions Bluetooth (Android 12+) ----
    private fun hasBtPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun requestBtPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE), 1
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasBtPermission()) {
            hid.start()
            web.evaluateJavascript("refreshDevices()", null)
        }
    }

    // ---- Plein écran immersif ----
    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onPause() {
        // Le gyroscope est coupé quand l'appli passe en arrière-plan
        gyro.stop()
        web.evaluateJavascript("window.onGyroOff && window.onGyroOff()", null)
        super.onPause()
    }

    override fun onDestroy() {
        gyro.stop()
        hid.stop()
        super.onDestroy()
    }
}
