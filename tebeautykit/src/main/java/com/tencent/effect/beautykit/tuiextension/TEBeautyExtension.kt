package com.tencent.effect.beautykit.tuiextension

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tencent.effect.beautykit.TEBeautyKit
import com.tencent.effect.beautykit.config.TEUIConfig
import com.tencent.effect.beautykit.model.TEUIProperty
import com.tencent.effect.beautykit.tuiextension.utils.AppConfig
import com.tencent.effect.beautykit.tuiextension.utils.BitmapUtil
import com.tencent.effect.beautykit.tuiextension.utils.UriUtils
import com.tencent.effect.beautykit.utils.LogUtils
import com.tencent.effect.beautykit.view.panelview.TEPanelView
import com.tencent.effect.beautykit.view.panelview.TEPanelViewCallback
import com.tencent.qcloud.tuicore.TUICore
import com.tencent.qcloud.tuicore.interfaces.ITUIExtension
import com.tencent.qcloud.tuicore.interfaces.ITUIService
import com.tencent.qcloud.tuicore.interfaces.TUIExtensionInfo
import com.tencent.qcloud.tuicore.interfaces.TUIServiceCallback
import com.tencent.xmagic.XmagicConstant.FeatureName.SEGMENTATION_FACE_BLOCK
import com.tencent.xmagic.XmagicConstant.FeatureName.SEGMENTATION_SKIN
import com.tencent.xmagic.XmagicConstant.FeatureName.SMART_BEAUTY
import com.tencent.xmagic.XmagicConstant.FeatureName.WHITEN_ONLY_SKIN_AREA

import java.io.File
import java.util.Locale

class TEBeautyExtension : ITUIExtension, ITUIService {

    companion object {
        private const val TAG = "TUIBeautyService"

        @JvmStatic
        private var mBeautyKit: TEBeautyKit? = null
    }

    private var mCustomProperty: TEUIProperty? = null
    private var mPanelView: TEPanelView? = null

    private val mTEPanelViewCallback: TEPanelViewCallback = object : TEPanelViewCallback {
        override fun onClickCustomSeg(uiProperty: TEUIProperty) {
            mCustomProperty = uiProperty
            val param = HashMap<String, Any>()
            param["requestCode"] = AppConfig.TE_CHOOSE_PHOTO_SEG_CUSTOM
            TUICore.notifyEvent(Constants.KEY_EXTENSION_NAME, Constants.NOTIFY_START_ACTIVITY, param)
        }

        override fun onCameraClick() {}

        override fun onUpdateEffected(sdkParams: List<TEUIProperty.TESDKParam>) {}

        override fun onEffectStateChange(effectState: TEBeautyKit.EffectState) {}

        override fun onTitleClick(uiProperty: TEUIProperty) {}
    }

    override fun onGetExtension(extensionID: String, param: Map<String, Any>?): List<TUIExtensionInfo>? {
        val hashMap = HashMap<String, Any>()
        if (param != null && Constants.KEY_EXTENSION_NAME == extensionID) {
            val context = param[Constants.PARAM_CONTEXT] as? Context
            val lastParamList = param[Constants.PARAM_LAST_PARAM_LIST] as? String
            hashMap[Constants.PARAM_BEAUTY_PANEL] = getTEPanelView(context, lastParamList) ?: return null
            val extensionInfo = TUIExtensionInfo()
            extensionInfo.data = hashMap
            return listOf(extensionInfo)
        }
        return null
    }

    override fun onCall(method: String, param: Map<String, Any>?, callback: TUIServiceCallback?): Any? {
        if (param != null && TextUtils.equals(Constants.METHOD_ENABLE_HIGH_PERFORMANCE, method)) {
            Log.i(TAG, "onCall: method = $method, param = $param")
            if (param[Constants.PARAM_ENABLE_HIGH_PERFORMANCE] != null) {
                AppConfig.getInstance().isEnableHighPerformance =
                    param[Constants.PARAM_ENABLE_HIGH_PERFORMANCE] as Boolean
            }
            if (param[Constants.PARAM_DISABLE_FEATURE] != null) {
                AppConfig.getInstance().featureDisable =
                    param[Constants.PARAM_DISABLE_FEATURE] as Boolean
            }
        }
        if (param != null && TextUtils.equals(Constants.METHOD_INIT_BEAUTY_KIT, method)) {
            if (mBeautyKit == null) {
                val context = param[Constants.PARAM_CONTEXT] as? Context
                val lastParamList = param[Constants.PARAM_LAST_PARAM_LIST] as? String
                context?.let {
                    TEBeautyKit.create(context, AppConfig.getInstance().isEnableHighPerformance) { beautyKit ->
                        mBeautyKit = beautyKit
                        if (AppConfig.getInstance().featureDisable) {
                            mBeautyKit?.setFeatureEnableDisable(SEGMENTATION_SKIN, false)
                            mBeautyKit?.setFeatureEnableDisable(SEGMENTATION_FACE_BLOCK, false)
                            mBeautyKit?.setFeatureEnableDisable(WHITEN_ONLY_SKIN_AREA, false)
                            mBeautyKit?.setFeatureEnableDisable(SMART_BEAUTY, false)
                        }
                        mBeautyKit?.setLastParamList(lastParamList)
                        callback?.onServiceCallback(if (mBeautyKit == null) -1 else 0, "", null)
                    }
                }
            } else {
                callback?.onServiceCallback(0, "", null)
            }
        }
        if (param != null && TextUtils.equals(Constants.METHOD_CHECK_RESOURCE, method)) {
            callback?.onServiceCallback(0, null, null)
        }
        return null
    }

    override fun onCall(method: String, param: Map<String, Any>?): Any? {
        if (param != null && TextUtils.equals(Constants.METHOD_PROCESS_VIDEO_FRAME, method)) {
            val srcTextureId = param[Constants.PARAM_NAME_SRC_TEXTURE_ID] as Int
            val width = param[Constants.PARAM_NAME_FRAME_WIDTH] as Int
            val height = param[Constants.PARAM_NAME_FRAME_HEIGHT] as Int
            return processVideoFrame(srcTextureId, width, height)
        } else if (TextUtils.equals(Constants.METHOD_DESTROY_BEAUTY_KIT, method)) {
            destroyBeautyKit()
        } else if (TextUtils.equals(Constants.METHOD_ACTIVITY_RESULT, method)) {
            val requestCode = param!!["requestCode"] as Int
            val resultCode = param["resultCode"] as Int
            val data = param["data"] as? Intent
            onActivityResult(requestCode, resultCode, data)
        } else if (TextUtils.equals(Constants.METHOD_EXPORT_PARAM, method)) {
            return exportParam()
        }
        return null
    }

    private fun processVideoFrame(srcTextureId: Int, textureWidth: Int, textureHeight: Int): Int {
        val beautyKit = mBeautyKit ?: return srcTextureId
        return beautyKit.process(srcTextureId, textureWidth, textureHeight)
    }

    private fun getTEPanelView(context: Context?, lastParamList: String?): TEPanelView? {
        if (context == null) {
            return null
        }
        TEUIConfig.getInstance().setSystemLocal(Locale.getDefault())
        if (mPanelView != null) {
            return mPanelView
        }
        mPanelView = TEPanelView(context)
        mPanelView?.setTEPanelViewCallback(mTEPanelViewCallback)
        Log.i(TAG, "TEPanelView create, mBeautyKit = $mBeautyKit  $lastParamList")
        mPanelView?.setLastParamList(lastParamList)
        if (mBeautyKit != null) {
            mPanelView?.setupWithTEBeautyKit(mBeautyKit)
        }
        mPanelView?.showView(mTEPanelViewCallback)
        return mPanelView
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            val filePath: String?
            if (data != null) {
                filePath = UriUtils.getFilePathByUri(mPanelView!!.context, data.data!!)
            } else {
                LogUtils.e(TAG, "the data and filePath is null ")
                return
            }
            if (requestCode == AppConfig.TE_CHOOSE_PHOTO_SEG_CUSTOM) { // custom segmentation
                setCustomSegParam(filePath)
            }
        } else {
            mCustomProperty = null
        }
    }

    private fun setCustomSegParam(filePath: String?) {
        if (mCustomProperty != null && mCustomProperty!!.sdkParam != null
            && mCustomProperty!!.sdkParam.extraInfo != null && !TextUtils.isEmpty(filePath)
            && File(filePath!!).exists()
        ) {
            if (filePath.endsWith("jpg") || filePath.endsWith("JPG") || filePath.endsWith("PNG")
                || filePath.endsWith("png")
                || filePath.endsWith("jpeg") || filePath.endsWith("JPEG")
            ) {
                BitmapUtil.compressImage(mPanelView!!.context.applicationContext, filePath) { imgPath ->
                    mCustomProperty!!.sdkParam.extraInfo[TEUIProperty.TESDKParam.EXTRA_INFO_KEY_BG_TYPE] =
                        TEUIProperty.TESDKParam.EXTRA_INFO_BG_TYPE_IMG
                    mCustomProperty!!.sdkParam.extraInfo[TEUIProperty.TESDKParam.EXTRA_INFO_KEY_BG_PATH] = imgPath
                    mBeautyKit?.setEffect(mCustomProperty!!.sdkParam)
                    Handler(Looper.getMainLooper()).post {
                        mPanelView?.checkPanelViewItem(mCustomProperty)
                        mCustomProperty = null
                    }
                }
            } else {
                mCustomProperty!!.sdkParam.extraInfo[TEUIProperty.TESDKParam.EXTRA_INFO_KEY_BG_TYPE] =
                    TEUIProperty.TESDKParam.EXTRA_INFO_BG_TYPE_VIDEO
                mCustomProperty!!.sdkParam.extraInfo[TEUIProperty.TESDKParam.EXTRA_INFO_KEY_BG_PATH] = filePath
                mBeautyKit?.setEffect(mCustomProperty!!.sdkParam)
                Handler(Looper.getMainLooper()).post {
                    mPanelView?.checkPanelViewItem(mCustomProperty)
                    mCustomProperty = null
                }
            }
        } else {
            mCustomProperty = null
        }
    }

    private fun destroyBeautyKit() {
        Log.i(TAG, "destroyBeautyKit mBeautyKit:$mBeautyKit")
        if (mBeautyKit != null) {
            mBeautyKit?.onDestroy()
            mBeautyKit = null
        }
        mPanelView = null
    }

    private fun exportParam(): String {
        return mBeautyKit?.exportInUseSDKParam() ?: ""
    }

    private fun convertLastParamList(lastParamList: String?): List<TEUIProperty.TESDKParam>? {
        if (!TextUtils.isEmpty(lastParamList)) {
            val type = object : TypeToken<List<TEUIProperty.TESDKParam>>() {}.type
            try {
                return Gson().fromJson(lastParamList, type)
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        return null
    }
}
