package com.tencent.uikit.app.common.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FileUtil {
    fun getLogFile(mContext: Context): File? {
        val path = mContext.getExternalFilesDir(null)!!.getAbsolutePath() + "/log/liteav"
        val logs: MutableList<String> = ArrayList<String>()
        val directory = File(path)
        if (directory.exists() && directory.isDirectory()) {
            val files = directory.listFiles()
            if (files != null && files.size > 0) {
                for (file in files) {
                    if (file.getName().endsWith("xlog") || file.getName().endsWith("clog")) {
                        logs.add(file.absolutePath)
                    }
                }
            }
        }
        val zipPath = "$path/liteavLog.zip"
        return zip(logs, zipPath)
    }

    fun zip(files: MutableList<String>, zipFileName: String): File? {
        val zipFile = File(zipFileName)
        zipFile.deleteOnExit()
        var `is`: InputStream? = null
        var zos: ZipOutputStream? = null
        try {
            zos = ZipOutputStream(FileOutputStream(zipFile))
            zos.setComment("LiteAV log")
            for (path in files) {
                val file = File(path)
                try {
                    if (file.length() == 0L || file.length() > 8 * 1024 * 1024) {
                        continue
                    }
                    `is` = FileInputStream(file)
                    zos.putNextEntry(ZipEntry(file.getName()))
                    val buffer = ByteArray(8 * 1024)
                    var length = 0
                    while ((`is`.read(buffer).also { length = it }) != -1) {
                        zos.write(buffer, 0, length)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        `is`?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (_: FileNotFoundException) {
            return null
        } finally {
            try {
                zos?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return zipFile
    }
}