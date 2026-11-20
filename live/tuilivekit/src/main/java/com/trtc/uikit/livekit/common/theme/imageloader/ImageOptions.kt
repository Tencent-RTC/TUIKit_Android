package com.trtc.uikit.livekit.common.theme.imageloader

class ImageOptions private constructor(
    val placeImage: Int, val errorImage: Int, val isGif: Boolean, val skipMemoryCache: Boolean,
    val skipDiskCache: Boolean
) {

    class Builder {
        var placeImage: Int = 0
            private set
        var errorImage: Int = 0
            private set
        var isGif: Boolean = false
            private set
        val skipMemoryCache: Boolean = false
        val skipDiskCache: Boolean = false

        fun setPlaceImage(placeImage: Int): Builder {
            this.placeImage = placeImage
            return this
        }

        fun setErrorImage(errorImage: Int): Builder {
            this.errorImage = errorImage
            return this
        }

        fun asGif(isGif: Boolean): Builder {
            this.isGif = isGif
            return this
        }

        fun build(): ImageOptions {
            return ImageOptions(
                placeImage = placeImage, errorImage = errorImage, isGif = isGif, skipMemoryCache = skipMemoryCache,
                skipDiskCache = skipDiskCache
            )
        }
    }
}
