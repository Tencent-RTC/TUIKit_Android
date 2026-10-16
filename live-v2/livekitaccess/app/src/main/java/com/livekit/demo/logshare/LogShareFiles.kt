package com.livekit.demo.logshare

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object LogShareFiles {
    data class Entry(val file: File, val modified: Long, val size: Long)
    data class Export(val directory: File, val file: File) {
        fun remove() { directory.deleteRecursively() }
    }

    fun scan(directory: File?): List<Entry> {
        if (directory == null) throw IOException("External app storage unavailable")
        if (!directory.exists()) return emptyList()
        val files = directory.listFiles() ?: throw IOException("Cannot list log directory")
        return files.filter {
            it.isFile && !it.isHidden && it.canonicalFile == File(directory.canonicalFile, it.name) &&
                !it.extension.equals("zip", ignoreCase = true)
        }.map { Entry(it, it.lastModified(), it.length()) }
            .sortedWith(compareByDescending<Entry> { it.modified }.thenByDescending { it.file.name })
    }

    fun prepare(cache: File, entries: List<Entry>, archive: Boolean): Export {
        val root = File(cache, "log_exports")
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create export directory")
        root.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 86_400_000L }
            ?.forEach { it.deleteRecursively() }
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdir()) throw IOException("Cannot create export snapshot")
        try {
            if (entries.isEmpty()) throw IOException("No files selected")
            val file = if (archive) {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                File(directory, "LiveKit-Logs-$stamp.zip").also { output ->
                    ZipOutputStream(output.outputStream().buffered()).use { zip ->
                        entries.forEach { entry ->
                            zip.putNextEntry(ZipEntry(entry.file.name).apply { time = entry.modified })
                            entry.file.inputStream().buffered().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            } else entries.single().file.copyTo(File(directory, entries.single().file.name))
            return Export(directory, file)
        } catch (error: Exception) {
            directory.deleteRecursively()
            throw error
        }
    }
}
