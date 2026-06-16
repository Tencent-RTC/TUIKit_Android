package com.tencent.effect.beautykit.tuiextension

import android.content.Context
import android.text.TextUtils
import android.util.Log

import com.tencent.effect.beautykit.TEBeautyKit
import com.tencent.effect.beautykit.config.TEUIConfig
import com.tencent.effect.beautykit.model.TEPanelDataModel
import com.tencent.effect.beautykit.model.TEUIProperty
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback
import com.tencent.xmagic.telicense.TELicenseCheck

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class BeautyLevel {
    A1_00, A1_01, A1_02, A1_03, A1_04, A1_05, A1_06,
    S1_00, S1_01, S1_02, S1_03, S1_04, S1_05, S1_06, S1_07
}

class TUIBeautyKit private constructor() {

    companion object {
        private const val TAG = "TEBeautySettings"

        private const val BEAUTY_FILE_DIR_NAME = "xmagic"
        private const val KEY_TEBEAUTY_SETTINGS = "tebeauty_settings"
        private const val KEY_RESOURCE_COPIED = "resource_copied"
        internal var beautyLevel = BeautyLevel.S1_07

        private val initialized = AtomicBoolean(false)

        @JvmStatic
        val instance: TUIBeautyKit by lazy { TUIBeautyKit() }
    }

    private var mAppVersionName = ""

    fun init(
        context: Context?, licenseUrl: String, licenseKey: String,
        beautyLevel: BeautyLevel = BeautyLevel.S1_07
    ) {
        if (context == null) {
            Log.e(TAG, "context is null")
            return
        }

        if (!initialized.compareAndSet(false, true)) {
            Log.i(TAG, "init() already done, skip duplicated call")
            return
        }
        Companion.beautyLevel = beautyLevel
        TEUIConfig.getInstance().panelBackgroundColor = 0xFF1F2024.toInt()
        val appContext = context.applicationContext
        copyBeautyResources(appContext, object : TUIServiceCallback() {
            override fun onServiceCallback(errorCode: Int, errorMessage: String?, bundle: android.os.Bundle?) {
                Log.i(TAG, "copyRes callback: $errorCode, $errorMessage")
                setBeautyVersion()
                TEBeautyKit.setTELicense(appContext, licenseUrl, licenseKey) { error, msg ->
                    Log.i(TAG, "setTELicense callback: $error, $msg")
                    if (error == TELicenseCheck.ERROR_OK) {
                        Log.i(TAG, "License Verification Success")
                    } else {
                        Log.e(TAG, "License Verification Failed: $error, $msg")
                    }
                }
            }
        })
    }

    private fun copyBeautyResources(context: Context, callback: TUIServiceCallback?) {
        val appContext = context.applicationContext
        val resPath = File(appContext.filesDir, getBeautyFileDirName()).absolutePath
        TEBeautyKit.setResPath(resPath)
        if (isNeedCopyRes(appContext)) {
            Thread {
                TEBeautyKit.copyRes(appContext)
                saveCopyData(context)
                callback?.onServiceCallback(0, null, null)
            }.start()
        } else {
            callback?.onServiceCallback(0, null, null)
        }
    }

    private fun setBeautyVersion() {
        val panelDataModels = TEUIConfig.getInstance().panelDataList
        // panelDataList 是 TEUIConfig 全局单例下的非线程安全 ArrayList，
        // 这里对 clear + add 的整个过程加锁，避免任何并发路径下的越界/脏读。
        synchronized(panelDataModels) {
        panelDataModels.clear()

        when (beautyLevel) {
            BeautyLevel.A1_00 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
            }

            BeautyLevel.A1_01 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_base_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
            }

            BeautyLevel.A1_02 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_base_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
            }

            BeautyLevel.A1_03 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_general_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
            }

            BeautyLevel.A1_04 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_general_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
            }

            BeautyLevel.A1_05 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_general_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
            }

            BeautyLevel.A1_06 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_general_shape.json",
                        TEUIProperty.UICategory.BEAUTY
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
            }

            BeautyLevel.S1_00 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
            }

            BeautyLevel.S1_01 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
            }

            BeautyLevel.S1_02 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/motion_gesture.json",
                        TEUIProperty.UICategory.MOTION
                    )
                )
            }

            BeautyLevel.S1_03 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
            }

            BeautyLevel.S1_04 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/motion_gesture.json",
                        TEUIProperty.UICategory.MOTION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
            }

            BeautyLevel.S1_05 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_body.json",
                        TEUIProperty.UICategory.BODY_BEAUTY
                    )
                )
            }

            BeautyLevel.S1_06 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/motion_gesture.json",
                        TEUIProperty.UICategory.MOTION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_body.json",
                        TEUIProperty.UICategory.BODY_BEAUTY
                    )
                )
            }

            BeautyLevel.S1_07 -> {
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_template.json",
                        TEUIProperty.UICategory.BEAUTY_TEMPLATE
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_image.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/lut.json", TEUIProperty.UICategory.LUT))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_shape.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(TEPanelDataModel("beauty_panel/beauty_makeup.json", TEUIProperty.UICategory.BEAUTY))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/light_makeup.json",
                        TEUIProperty.UICategory.LIGHT_MAKEUP
                    )
                )
                panelDataModels.add(TEPanelDataModel("beauty_panel/makeup.json", TEUIProperty.UICategory.MAKEUP))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_2d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(TEPanelDataModel("beauty_panel/motion_3d.json", TEUIProperty.UICategory.MOTION))
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/motion_gesture.json",
                        TEUIProperty.UICategory.MOTION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/segmentation.json",
                        TEUIProperty.UICategory.SEGMENTATION
                    )
                )
                panelDataModels.add(
                    TEPanelDataModel(
                        "beauty_panel/beauty_body.json",
                        TEUIProperty.UICategory.BODY_BEAUTY
                    )
                )
            }
        }

        TEUIConfig.getInstance().revertEffect2Json = true
        }
    }

    private fun getBeautyFileDirName(): String {
        return BEAUTY_FILE_DIR_NAME
    }

    private fun getAppVersionName(context: Context): String {
        if (!TextUtils.isEmpty(mAppVersionName)) {
            return mAppVersionName
        }
        var versionName = ""
        try {
            val pm = context.packageManager
            val pi = pm.getPackageInfo(context.packageName, 0)
            versionName = pi.versionName ?: ""
            if (TextUtils.isEmpty(versionName)) {
                return ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mAppVersionName = versionName
        return versionName
    }

    private fun isNeedCopyRes(context: Context): Boolean {
        val appVersionName = getAppVersionName(context)
        val sp = context.getSharedPreferences(KEY_TEBEAUTY_SETTINGS, Context.MODE_PRIVATE)
        val savedVersionName = sp.getString(KEY_RESOURCE_COPIED, "")
        return !TextUtils.equals(savedVersionName, appVersionName)
    }

    private fun saveCopyData(context: Context) {
        val appVersionName = getAppVersionName(context)
        val sp = context.getSharedPreferences(KEY_TEBEAUTY_SETTINGS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_RESOURCE_COPIED, appVersionName).commit()
    }
}
