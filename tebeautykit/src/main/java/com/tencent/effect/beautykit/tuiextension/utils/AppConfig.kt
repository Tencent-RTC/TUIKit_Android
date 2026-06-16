package com.tencent.effect.beautykit.tuiextension.utils

class AppConfig private constructor() {

    @JvmField
    var isEnableHighPerformance = false

    @JvmField
    var featureDisable = false

    companion object {
        const val TE_CHOOSE_PHOTO_SEG_CUSTOM = 2002

        @JvmStatic
        fun getInstance(): AppConfig = ClassHolder.APP_CONFIG
    }

    private object ClassHolder {
        val APP_CONFIG = AppConfig()
    }
}
