package com.trtc.uikit.livekit.component.giftaccess.service

import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.LiveKitLogger.Companion.getComponentLogger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GiftCacheService {
    private val cacheSize = 20
    private var executor: ExecutorService? = null
    private lateinit var cacheFile: File
    private var lruCache: LruCache<String, File>? = null
    private val logger: LiveKitLogger = getComponentLogger("GiftCacheService")

    init {
        ContextProvider.getApplicationContext()?.apply {
            Thread {
                ContextProvider.getApplicationContext()?.apply {
                    setCacheDir(File(this.cacheDir.toString() + File.separator + "gift"))
                }
            }.start()
        }
    }

    fun setCacheDir(file: File?) {
        file?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
            cacheFile = it
        }
    }

    fun release() {
        logger.info("release")
        clearCache()
        executor?.shutdown()
    }

    private fun clearCache() {
        val files = cacheFile.listFiles() ?: return
        
        for (file in files) {
            if (file.isFile) {
                file.delete()
            }
        }
        lruCache?.evictAll()
    }

    fun request(urlString: String, callback: Callback<String>?) {
        logger.info("request: $urlString")
        
        if (executor?.isShutdown == true) {
            logger.info("mExecutor is isShutdown")
            callback?.onResult(-1, null)
            return
        }

        val url = try {
            URL(urlString)
        } catch (e: MalformedURLException) {
            callback?.onResult(-1, null)
            return
        }

        if (lruCache == null) {
            lruCache = LruCache(cacheSize)
        }

        val key = keyForUrl(url.path)
        val cache = lruCache?.get(key)
        
        if (cache != null && cache.exists()) {
            logger.info("find memory cache: $url")
            callback?.onResult(0, cache.absolutePath)
            return
        }

        val targetFile = File(cacheFile, key)
        if (targetFile.exists()) {
            logger.info("find storage cache: $url")
            lruCache?.put(key, targetFile)
            callback?.onResult(0, targetFile.absolutePath)
            return
        }

        if (executor == null) {
            executor = Executors.newSingleThreadExecutor()
        }

        executor?.submit {
            var urlConnection: HttpURLConnection? = null
            try {
                targetFile.createNewFile()
                
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connectTimeout = 20 * 1000
                urlConnection.setRequestProperty("Connection", "close")
                urlConnection.connect()
                
                val inputStream: InputStream = urlConnection.inputStream
                val fos = FileOutputStream(targetFile)
                val data = ByteArray(4096)
                var length: Int
                
                while (inputStream.read(data).also { length = it } != -1) {
                    fos.write(data, 0, length)
                }
                fos.close()
                
                lruCache?.put(key, targetFile)
                callback?.onResult(0, targetFile.absolutePath)
            } catch (e: IOException) {
                logger.info(" ${e.localizedMessage}")
                try {
                    if (targetFile.exists()) {
                        targetFile.delete()
                    }
                } catch (e: Exception) {
                    logger.info("delete cache file failed: ${e.localizedMessage}")
                }
                callback?.onResult(-1, null)
            } finally {
                urlConnection?.disconnect()
            }
        }
    }

    private fun keyForUrl(url: String): String {
        if (TextUtils.isEmpty(url)) {
            return ""
        }
        val trimmedUrl = url.trimEnd('/')
        if (trimmedUrl.isEmpty()) {
            return ""
        }
        val lastSlashIndex = trimmedUrl.lastIndexOf('/')
        val key = if (lastSlashIndex >= 0 && lastSlashIndex < trimmedUrl.length - 1) {
            trimmedUrl.substring(lastSlashIndex + 1)
        } else {
            trimmedUrl
        }
        return key.substringBefore('?').substringBefore('#').ifEmpty { trimmedUrl }
    }

    interface Callback<T> {
        fun onResult(error: Int, result: T?)
    }
}