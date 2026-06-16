package com.tencent.effect.beautykit.tuiextension

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

import com.tencent.qcloud.tuicore.TUICore

class TEBeautyProvider : ContentProvider() {

    companion object {
        private const val TAG = "TEBeautyProvider"
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "TEBeautyProvider onCreate")
        val extension = TEBeautyExtension()
        TUICore.registerExtension(Constants.KEY_EXTENSION_NAME, extension)
        TUICore.registerService(Constants.KEY_EXTENSION_NAME, extension)
        return false
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        return 0
    }
}
