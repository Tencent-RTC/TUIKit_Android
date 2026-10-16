package com.trtc.uikit.livekit.common.basic

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

class ContextProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        mApplicationContext = this.context!!.applicationContext
        return false
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return ""
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int {
        return 0
    }

    companion object {
        private const val TAG = "ContextProvider"
        private var mApplicationContext: Context? = null
        val applicationContext: Context?
            @SuppressLint("PrivateApi")
            get() {
                if (mApplicationContext == null) {
                    try {
                        val method = Class.forName("android.app.ActivityThread").getMethod("currentActivityThread")
                        method.setAccessible(true)
                        val activityThread = method.invoke(null)
                        val app =
                            activityThread!!.javaClass.getMethod("getApplication").invoke(activityThread)
                        mApplicationContext =
                            app as Context?
                    } catch (e: Exception) {
                        Log.e(
                            TAG,
                            "get context fail, e=" + e.message
                        )
                    }
                }
                return mApplicationContext
            }

        fun setContext(context: Context?) {
            if (context != null && mApplicationContext == null) {
                mApplicationContext = context.applicationContext
            }
        }
    }
}
