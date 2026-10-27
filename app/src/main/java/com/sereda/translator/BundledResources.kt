package com.sereda.translator

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.security.MessageDigest

data class ResourceReadiness(
    val translation: String? = "Preparing translation resources…",
    val recognition: String? = "Preparing speech input…",
    val synthesis: String? = "Preparing voices…"
) {
    fun error(mode: TranslationMode): String? = translation
        ?: if (mode.speechInput && recognition != null) recognition
        else if (mode.speechOutput) synthesis else null
}

class BundledResources(private val context: Context) {
    val root = File(context.cacheDir, "offline-resources")
    private val manifest by lazy {
        JSONArray(context.assets.open("resources.json").bufferedReader().use { it.readText() })
    }
    fun file(relative: String) = File(root, relative)

    fun needsPreparation(): Boolean {
        if (!root.isDirectory) return true
        for (index in 0 until manifest.length()) {
            val entry = manifest.getJSONObject(index)
            val extracted = file(entry.getString("path").removePrefix("models/"))
            if (!extracted.isFile || extracted.length() != entry.getLong("bytes")) return true
        }
        return false
    }

    fun initialize(progress: (String) -> Unit, cancelled: () -> Boolean): ResourceReadiness {
        // Earlier builds kept these regenerable copies in no_backup, preventing installer reclamation.
        val legacy = File(context.noBackupFilesDir, "offline-resources")
        check(legacy.canonicalFile.parentFile == context.noBackupFilesDir.canonicalFile)
        if (legacy.exists()) {
            if (!root.exists()) legacy.renameTo(root)
            if (legacy.exists() && !legacy.deleteRecursively()) {
                throw IOException("Cannot remove old model copies. Free storage and retry preparation.")
            }
        }
        root.mkdirs()
        // Drop resources from older app versions that are no longer shipped or supported.
        val expectedPaths = (0 until manifest.length()).map { index ->
            manifest.getJSONObject(index).getString("path").removePrefix("models/")
        }.toSet()
        root.walkBottomUp().filter { it != root }.forEach { extracted ->
            checkActive(cancelled)
            check(extracted.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Invalid cached resource path" }
            if (extracted.isFile && extracted.relativeTo(root).invariantSeparatorsPath !in expectedPaths) {
                if (!extracted.delete()) throw IOException("Cannot remove an obsolete model copy. Retry preparation.")
            } else if (extracted.isDirectory && extracted.list()?.isEmpty() == true) extracted.delete()
        }
        val failures = mutableMapOf<String, String>()
        for (index in 0 until manifest.length()) {
            checkActive(cancelled)
            val entry = manifest.getJSONObject(index)
            val path = entry.getString("path")
            val group = when {
                path.startsWith("models/translation/") -> "translation"
                path.contains("whisper") -> "recognition"
                else -> "synthesis"
            }
            if (group in failures) continue
            try {
                val destination = file(path.removePrefix("models/"))
                check(destination.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "Invalid resource path" }
                val expected = entry.getString("sha256")
                progress("Checking bundled resources ${index + 1}/${manifest.length()}")
                if (destination.isFile && destination.length() == entry.getLong("bytes") && sha256(destination) == expected) continue
                destination.parentFile?.mkdirs()
                val temporary = File(destination.path + ".partial")
                try {
                    context.assets.open(path).use { input ->
                        temporary.outputStream().use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                checkActive(cancelled)
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (sha256(temporary) != expected) throw IOException("Bundled resources are corrupt. Reinstall the app.")
                    java.nio.file.Files.move(temporary.toPath(), destination.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
                } finally { temporary.delete() }
            } catch (e: java.util.concurrent.CancellationException) { throw e }
            catch (e: Exception) {
                failures[group] = if (e.message?.contains("corrupt") == true) e.message!!
                    else "Cannot prepare $group resources. Check free storage and retry. If this persists, reinstall."
            }
        }
        return ResourceReadiness(failures["translation"], failures["recognition"], failures["synthesis"])
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
