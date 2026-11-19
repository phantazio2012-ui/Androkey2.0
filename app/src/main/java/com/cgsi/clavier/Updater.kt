package com.cgsi.clavier

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Mise à jour automatique via les "Releases" GitHub du dépôt (BuildConfig.GITHUB_REPO).
 * Chaque publication crée une release « v<numéro> » avec le fichier clavier.apk ;
 * l'appli compare ce numéro à son propre versionCode.
 */
class Updater(private val activity: Activity) {

    private var apkUrl: String = ""
    private var apkFile: File? = null

    fun currentInfo(): String = JSONObject()
        .put("versionCode", BuildConfig.VERSION_CODE)
        .put("versionName", BuildConfig.VERSION_NAME)
        .put("repo", BuildConfig.GITHUB_REPO)
        .toString()

    /** Interroge GitHub en arrière-plan ; le résultat (JSON) est renvoyé à [done]. */
    fun check(done: (String) -> Unit) = thread {
        val out = JSONObject().put("current", BuildConfig.VERSION_CODE)
        try {
            if (BuildConfig.GITHUB_REPO.startsWith("OWNER")) {
                done(out.put("error", "Dépôt GitHub non configuré (APK compilé hors GitHub Actions)").toString()); return@thread
            }
            val c = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10000; c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "ClavierCGSI")
            when (c.responseCode) {
                200 -> {
                    val j = JSONObject(c.inputStream.bufferedReader().readText())
                    val latest = j.getString("tag_name").trim().removePrefix("v").toIntOrNull() ?: 0
                    val assets = j.getJSONArray("assets")
                    var url = ""
                    for (i in 0 until assets.length()) {
                        val a = assets.getJSONObject(i)
                        if (a.getString("name").endsWith(".apk")) { url = a.getString("browser_download_url"); break }
                    }
                    apkUrl = url
                    out.put("latest", latest).put("name", j.optString("name")).put("notes", j.optString("body"))
                        .put("available", latest > BuildConfig.VERSION_CODE && url.isNotEmpty())
                }
                404 -> out.put("available", false).put("error", "Aucune version publiée sur GitHub pour l'instant")
                else -> out.put("error", "GitHub a répondu ${c.responseCode}")
            }
        } catch (e: Exception) {
            out.put("error", "Pas de connexion Internet (${e.javaClass.simpleName})")
        }
        done(out.toString())
    }

    /** Télécharge l'APK ; [progress] reçoit 0..100, [done] reçoit null si OK ou un message d'erreur. */
    fun download(progress: (Int) -> Unit, done: (String?) -> Unit) = thread {
        if (apkUrl.isEmpty()) { done("Aucune mise à jour à télécharger : relancez la recherche"); return@thread }
        try {
            val dir = File(activity.cacheDir, "updates").apply { mkdirs() }
            val f = File(dir, "clavier.apk")
            val c = URL(apkUrl).openConnection() as HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "ClavierCGSI")
            val total = c.contentLengthLong
            var read = 0L; var last = -1
            c.inputStream.use { ins ->
                f.outputStream().use { outs ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        val n = ins.read(buf); if (n < 0) break
                        outs.write(buf, 0, n); read += n
                        if (total > 0) { val p = (read * 100 / total).toInt(); if (p != last) { last = p; progress(p) } }
                    }
                }
            }
            apkFile = f
            done(null)
        } catch (e: Exception) {
            done("Téléchargement impossible (${e.javaClass.simpleName})")
        }
    }

    /** Ouvre l'installateur Android. Renvoie un message si l'autorisation « sources inconnues » manque. */
    fun install(): String? {
        val f = apkFile ?: return "Téléchargez d'abord la mise à jour"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return "Autorisez « Installer des applis inconnues » pour Clavier CGSI, puis revenez et appuyez de nouveau sur Installer"
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", f)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return null
    }
}
