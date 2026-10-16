package com.trtc.uikit.livekit.common.gift.player

import android.os.Handler
import android.os.Looper
import com.trtc.uikit.livekit.common.basic.ContextProvider
import com.trtc.uikit.livekit.common.basic.LiveKitLogger
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class LiveGiftEffectLoader {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null

    fun load(resourceURL: String, completion: (File?) -> Unit): Future<*>? {
        val remoteURL = runCatching { URL(resourceURL) }.getOrNull()
        val scheme = remoteURL?.protocol?.lowercase()
        if (remoteURL == null || (scheme != "https" && scheme != "http")) {
            mainHandler.post { completion(null) }
            return null
        }

        val cacheDirectory = cacheDirectory()
        if (cacheDirectory == null) {
            mainHandler.post { completion(null) }
            return null
        }

        val cachedFile = cacheFile(cacheDirectory, remoteURL)
        if (cachedFile.exists()) {
            if (isValidResourceFile(cachedFile, remoteURL)) {
                mainHandler.post { completion(cachedFile) }
                return null
            }
            LOGGER.warn("cached gift effect file invalid, delete and re-download: ${remoteURL}")
            cachedFile.delete()
        }

        val executor = this.executor?.takeIf { !it.isShutdown }
            ?: Executors.newSingleThreadExecutor().also { this.executor = it }
        return executor.submit {
            val downloaded = download(remoteURL, cacheDirectory, cachedFile)
            mainHandler.post { completion(downloaded) }
        }
    }

    fun release() {
        executor?.shutdownNow()
        executor = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun download(remoteURL: URL, cacheDirectory: File, destination: File): File? {
        var connection: HttpURLConnection? = null
        val temporaryFile = File(cacheDirectory, "${destination.name}.$TEMPORARY_SUFFIX")
        return try {
            if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) return null
            connection = (remoteURL.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = CONNECT_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Connection", "close")
                connect()
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { input ->
                FileOutputStream(temporaryFile).use { output -> input.copyTo(output) }
            }
            if (!isValidResourceFile(temporaryFile, remoteURL)) {
                LOGGER.warn("gift effect file validation failed: ${remoteURL}")
                temporaryFile.delete()
                return null
            }
            when {
                destination.exists() -> destination
                temporaryFile.renameTo(destination) -> destination
                destination.exists() -> destination
                else -> null
            }
        } catch (e: Exception) {
            LOGGER.warn("download gift effect failed: ${e.localizedMessage}")
            null
        } finally {
            connection?.disconnect()
            if (temporaryFile.exists()) {
                temporaryFile.delete()
            }
        }
    }

    private fun isValidResourceFile(file: File, remoteURL: URL): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        if (file.length() > MAX_RESOURCE_SIZE_BYTES) return false
        val extension = remoteURL.path.substringAfterLast('.', "").lowercase()
        return try {
            java.io.FileInputStream(file).use { input ->
                val header = ByteArray(HEADER_PROBE_SIZE)
                val read = input.read(header)
                if (read <= 0) return false
                when (extension) {
                    "svga" -> isSvgaHeader(header, read)
                    "mp4" -> isMp4Header(header, read)
                    else -> true
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("validate gift effect file failed: ${e.localizedMessage}")
            false
        }
    }

    private fun isSvgaHeader(header: ByteArray, read: Int): Boolean {
        if (read >= 4
            && header[0] == 0x50.toByte()
            && header[1] == 0x4B.toByte()
            && header[2] == 0x03.toByte()
            && header[3] == 0x04.toByte()
        ) {
            return true
        }
        return read >= 2 && header[0] == 0x78.toByte()
    }

    private fun isMp4Header(header: ByteArray, read: Int): Boolean {
        if (read < 8) return false
        return header[4] == 'f'.code.toByte()
                && header[5] == 't'.code.toByte()
                && header[6] == 'y'.code.toByte()
                && header[7] == 'p'.code.toByte()
    }

    private fun cacheDirectory(): File? {
        val context = ContextProvider.applicationContext ?: return null
        return File(context.cacheDir, CACHE_DIRECTORY_PATH)
    }

    private fun cacheFile(cacheDirectory: File, remoteURL: URL): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(remoteURL.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = remoteURL.path.substringAfterLast('.', "").lowercase()
        val name = if (extension.isEmpty()) digest else "$digest.$extension"
        return File(cacheDirectory, name)
    }

    private companion object {
        val LOGGER = LiveKitLogger.getComponentLogger("LiveGiftEffectLoader")
        const val CACHE_DIRECTORY_PATH = "TUILiveKit/PusherGiftEffects"
        const val CONNECT_TIMEOUT_MS = 60 * 1000
        const val TEMPORARY_SUFFIX = "downloading"
        const val MAX_RESOURCE_SIZE_BYTES = 50L * 1024 * 1024
        const val HEADER_PROBE_SIZE = 16
    }
}
