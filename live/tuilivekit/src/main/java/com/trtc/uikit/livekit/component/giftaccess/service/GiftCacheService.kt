package com.trtc.uikit.livekit.component.giftaccess.service

import android.text.TextUtils
import android.util.Log
import android.util.LruCache
import com.tencent.cloud.tuikit.engine.common.ContextProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GiftCacheService {
    private val cacheSize = 20
    private val tag = "GiftCacheService"
    private var executor: ExecutorService? = null
    private lateinit var cacheFile: File
    private var lruCache: LruCache<String, File>? = null

    init {
        ContextProvider.getApplicationContext()?.apply {
            setCacheDir(File(this.cacheDir.toString() + File.separator + "gift"))
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
        Log.i(tag, "release")
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
        Log.i(tag, "request: $urlString")
        
        if (executor?.isShutdown == true) {
            Log.i(tag, "mExecutor is isShutdown")
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
            Log.i(tag, "find cache: $url")
            callback?.onResult(0, cache.absolutePath)
            return
        }

        if (executor == null) {
            executor = Executors.newSingleThreadExecutor()
        }

        executor?.submit {
            var urlConnection: HttpURLConnection? = null
            try {
                val cacheFile = File(cacheFile, File(urlString).name)
                if (cacheFile.exists()) {
                    cacheFile.delete()
                }
                cacheFile.createNewFile()
                
                urlConnection = url.openConnection() as HttpURLConnection
                urlConnection.requestMethod = "GET"
                urlConnection.connectTimeout = 20 * 1000
                urlConnection.setRequestProperty("Connection", "close")
                urlConnection.connect()
                
                val inputStream: InputStream = urlConnection.inputStream
                val fos = FileOutputStream(cacheFile)
                val data = ByteArray(4096)
                var length: Int
                
                while (inputStream.read(data).also { length = it } != -1) {
                    fos.write(data, 0, length)
                }
                fos.close()
                
                lruCache?.put(key, cacheFile)
                callback?.onResult(0, cacheFile.absolutePath)
                
            } catch (e: IOException) {
                Log.i(tag, " ${e.localizedMessage}")
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
        return try {
            val messageDigest = MessageDigest.getInstance("MD5")
            val data = messageDigest.digest(url.toByteArray())
            bytesToHexString(data)
        } catch (e: NoSuchAlgorithmException) {
            ""
        }
    }

    private fun bytesToHexString(src: ByteArray): String {
        val sb = StringBuilder()
        for (b in src) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    interface Callback<T> {
        fun onResult(error: Int, result: T?)
    }
}