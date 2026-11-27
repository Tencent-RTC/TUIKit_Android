package io.trtc.tuikit.chat.uikit.components.emojipicker

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache



fun createImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, 0.1) 
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("emoji_cache"))
                .maxSizeBytes(50 * 1024 * 1024) 
                .build()
        }
        .build()
}


object EmojiImageLoader {
    private var instance: ImageLoader? = null

    fun getInstance(context: Context): ImageLoader {
        if (instance == null) {
            instance = createImageLoader(context)
        }
        return instance!!
    }
}
