package com.tencent.qcloud.tuikit.tuicallkit.call

import android.content.Context
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitContext
import com.tencent.qcloud.tuikit.tuicallkit.common.CallKitLogger
import io.trtc.tuikit.atomicxcore.api.view.VolumeLevel
import java.io.File

/**
 * 引擎以绝对路径加载资源（setWaitingAnimation / setVolumeLevelIcons），
 * 这里把打包在 assets 的素材幂等拷贝到应用缓存目录后返回绝对路径。
 */
internal object CallKitAssets {

    private val logger = CallKitLogger.getLogger("CallKitAssets")
    private const val DIR_NAME = "callkit"
    private const val WAITING_DOTS_ASSET = "callkit_waiting_dots.gif"

    private val VOLUME_ICON_ASSETS = mapOf(
        VolumeLevel.Mute to "callkit_badge_micoff.png",
        VolumeLevel.Low to "callkit_badge_volume_low.png",
        VolumeLevel.Medium to "callkit_badge_volume_medium.png",
        VolumeLevel.High to "callkit_badge_volume_high.png",
        VolumeLevel.Peak to "callkit_badge_volume_peak.png",
    )

    fun waitingAnimationPath(): String {
        return materialize(WAITING_DOTS_ASSET).orEmpty()
    }

    fun volumeIconPaths(): Map<VolumeLevel, String> {
        val result = mutableMapOf<VolumeLevel, String>()
        for ((level, asset) in VOLUME_ICON_ASSETS) {
            val path = materialize(asset)
            if (!path.isNullOrEmpty()) {
                result[level] = path
            }
        }
        return result
    }

    private fun materialize(assetName: String): String? {
        val context = CallKitContext.requireContext()
        val target = File(File(context.cacheDir, DIR_NAME), assetName)
        try {
            context.assets.open(assetName).use { input ->
                val assetSize = input.available().toLong()
                if (target.exists() && target.length() == assetSize) {
                    return target.absolutePath
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return target.absolutePath
        } catch (e: Exception) {
            logger.warn("materialize $assetName failed: ${e.javaClass.simpleName}")
            return null
        }
    }
}
