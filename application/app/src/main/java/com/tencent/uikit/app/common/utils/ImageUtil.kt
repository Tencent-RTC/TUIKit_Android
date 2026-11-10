package com.tencent.uikit.app.common.utils

import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtil {
    const val SP_IMAGE = "_conversation_group_face"

    /**
     * @param outFile
     * @param bitmap
     * @return
     */
    fun storeBitmap(outFile: File, bitmap: Bitmap): File {
        if (!outFile.exists() || outFile.isDirectory) {
            outFile.parentFile?.mkdirs()
        }
        var fOut: FileOutputStream? = null
        try {
            outFile.deleteOnExit()
            outFile.createNewFile()
            fOut = FileOutputStream(outFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut)
            fOut.flush()
        } catch (e1: IOException) {
            outFile.deleteOnExit()
        } finally {
            if (fOut != null) {
                try {
                    fOut.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                    outFile.deleteOnExit()
                }
            }
        }
        return outFile
    }

    fun setGroupConversationAvatar(conversationId: String, url: String) {
        //TODO(lanqi)
//        val spUtils = SPUtils.getInstance("${TUILogin.getSdkAppId()} $SP_IMAGE")
//        spUtils.put(conversationId, url)
    }
}