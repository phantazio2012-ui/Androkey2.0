package com.cgsi.clavier

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Fait apparaître le téléphone/la tablette comme un clavier + souris Bluetooth (profil HID Device, Android 9+).
 * L'ordinateur interprète les codes de touches selon SA disposition (AZERTY/QWERTY/QWERTZ) :
 * choisissez dans l'appli la même disposition que celle réglée sur l'ordinateur.
 */
@SuppressLint("MissingPermission")
class HidKeyboard(
    private val context: Context,
    private val onState: (connected: Boolean, name: String) -> Unit,
    private val onMsg: (String) -> Unit
) {
    private var isRegistered = false
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var hid: BluetoothHidDevice? = null
    private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    /** Masque des boutons de souris actuellement enfoncés (1 = gauche, 2 = droit, 4 = milieu). */
    @Volatile var lastButtons = 0

    // Compteurs pour le diagnostic
    @Volatile var sentOk = 0
    @Volatile var sentFail = 0
    @Volatile var protocol = -1   // 0 = boot, 1 = report, -1 = inconnu

    private fun count(ok: Boolean) { if (ok) sentOk++ else sentFail++ }

    fun diag(): String = JSONObject()
        .put("registered", isRegistered)
        .put("proxy", hid != null)
        .put("connected", host != null)
        .put("host", runCatching { host?.name }.getOrNull() ?: host?.address ?: "")
        .put("protocol", protocol)
        .put("sentOk", sentOk).put("sentFail", sentFail)
        .put("sdk", android.os.Build.VERSION.SDK_INT)
        .put("model", android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL)
        .toString()

    /** Oublie l'appairage avec cet ordinateur (méthode non documentée d'Android, peut ne pas exister sur tous les téléphones). */
    fun unpair(address: String): Boolean = runCatching {
        val dev = adapter!!.getRemoteDevice(address)
        BluetoothDevice::class.java.getMethod("removeBond").invoke(dev) as Boolean
    }.getOrDefault(false)

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            isRegistered = registered
            if (!registered) {
                onMsg("Clavier non enregistré : ce téléphone refuse le mode clavier Bluetooth")
            } else if (pluggedDevice != null) {
                host = pluggedDevice
                onState(true, runCatching { pluggedDevice.name }.getOrNull() ?: pluggedDevice.address)
            } else {
                onMsg("Clavier prêt : choisissez l'ordinateur puis Connecter")
            }
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            this@HidKeyboard.protocol = protocol.toInt()
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            val size = if (id.toInt() == MOUSE_ID) 4 else 8
            hid?.replyReport(device, type, id, ByteArray(size))
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            // Voyants du clavier (Verr Maj...) envoyés par l'ordinateur : on accuse réception
            hid?.reportError(device, BluetoothHidDevice.ERROR_RSP_SUCCESS)
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTING -> onMsg("Connexion en cours…")
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    onState(true, runCatching { device.name }.getOrNull() ?: device.address)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (host?.address == device.address) host = null
                    onState(false, "")
                }
            }
        }
    }

    /** À appeler une fois la permission Bluetooth accordée. */
    fun start() {
        val ok = adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile != BluetoothProfile.HID_DEVICE) return
                val h = proxy as BluetoothHidDevice
                hid = h
                val sdp = BluetoothHidDeviceAppSdpSettings(
                    "Clavier CGSI", "Clavier et souris plein écran", "CGSI",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR
                )
                h.registerApp(sdp, null, null, executor, callback)
            }

            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hid = null
                    host = null
                }
            }
        }, BluetoothProfile.HID_DEVICE)
        if (ok != true) onMsg("Ce téléphone ne prend pas en charge le mode clavier Bluetooth")
    }

    fun pairedDevices(): String {
        val arr = JSONArray()
        runCatching {
            adapter?.bondedDevices?.forEach {
                arr.put(JSONObject().put("name", it.name ?: it.address).put("address", it.address))
            }
        }
        return arr.toString()
    }

    fun connect(address: String) {
        val h = hid
        if (h == null) { onMsg("Bluetooth indisponible : autorisez l'accès Bluetooth puis rouvrez l'appli"); return }
        if (!isRegistered) { onMsg("Clavier non enregistré : fermez puis rouvrez l'appli"); return }
        val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull() ?: return
        val ok = h.connect(dev)
        onMsg(if (ok) "Connexion à ${runCatching { dev.name }.getOrNull() ?: address}…"
              else "Échec : sur l'ordinateur, supprimez le téléphone des appareils Bluetooth, ré-appairez, puis réessayez")
    }

    fun disconnect() {
        host?.let { hid?.disconnect(it) }
    }

    /** Envoie une pression puis un relâchement de touche. mods : bits HID (Maj = 0x02, AltGr = 0x40…). */
    fun sendKey(code: Int, mods: Int) {
        val h = hid ?: return
        val d = host ?: return
        count(h.sendReport(d, KEYBOARD_ID, byteArrayOf(mods.toByte(), 0, code.toByte(), 0, 0, 0, 0, 0)))
        count(h.sendReport(d, KEYBOARD_ID, ByteArray(8)))
    }

    /** Rapport de souris : déplacement relatif, molette et boutons. */
    fun sendMouse(buttons: Int, dx: Int, dy: Int, wheel: Int): Int {
        lastButtons = buttons
        val h = hid ?: return 0
        val d = host ?: return 0
        var ok = true
        var rx = dx
        var ry = dy
        var w = wheel.coerceIn(-127, 127)
        do {
            val sx = rx.coerceIn(-127, 127)
            val sy = ry.coerceIn(-127, 127)
            val sent = h.sendReport(d, MOUSE_ID, byteArrayOf(buttons.toByte(), sx.toByte(), sy.toByte(), w.toByte()))
            count(sent); ok = sent && ok
            rx -= sx; ry -= sy; w = 0
        } while (rx != 0 || ry != 0)
        return if (ok) 1 else -1
    }

    fun stop() {
        runCatching {
            hid?.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hid = null
        host = null
    }

    companion object {
        private const val KEYBOARD_ID = 1
        private const val MOUSE_ID = 2

        // Descripteur HID composite : clavier standard (rapport 1, 8 octets + LED) et souris (rapport 2 : boutons, X, Y, molette)
        private val DESCRIPTOR: ByteArray = intArrayOf(
            // --- Clavier ---
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, KEYBOARD_ID,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,
            0x95, 0x05, 0x75, 0x01, 0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91, 0x02,
            0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00,
            0xC0,
            // --- Souris ---
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, MOUSE_ID,
            0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            0x95, 0x03, 0x75, 0x01, 0x81, 0x02,
            0x95, 0x01, 0x75, 0x05, 0x81, 0x03,
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0xC0, 0xC0
        ).map { it.toByte() }.toByteArray()
    }
}
