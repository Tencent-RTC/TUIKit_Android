package com.tencent.qcloud.tuikit.tuicallkit.common

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import io.trtc.tuikit.atomicxcore.api.contact.ContactInfo
import io.trtc.tuikit.atomicxcore.api.contact.ContactStore
import io.trtc.tuikit.atomicxcore.api.contact.GetContactInfoCompletionHandler

internal object CallKitContactLoader {

    private val logger = CallKitLogger.getLogger("CallKitContactLoader")

    private val mainHandler = Handler(Looper.getMainLooper())

    fun load(userIDs: List<String>, onProfile: (String, String?, String?, Bitmap?) -> Unit) {
        if (userIDs.isEmpty()) {
            return
        }
        ContactStore.shared.getContactInfo(userIDs, object : GetContactInfoCompletionHandler {
            override fun onSuccess(contactInfoList: List<ContactInfo>) {
                for (info in contactInfoList) {
                    val nickname = info.nickname
                    val avatarURL = info.avatarURL
                    loadAvatar(avatarURL) { bitmap -> onProfile(info.userID, nickname, avatarURL, bitmap) }
                }
            }

            override fun onFailure(code: Int, desc: String) {
                logger.warn("getContactInfo failed: code=$code desc=$desc")
            }
        })
    }

    fun loadAvatarPaths(userIDs: List<String>, onDone: (Map<String, String>) -> Unit) {
        if (userIDs.isEmpty()) {
            mainHandler.post { onDone(emptyMap()) }
            return
        }
        ContactStore.shared.getContactInfo(userIDs, object : GetContactInfoCompletionHandler {
            override fun onSuccess(contactInfoList: List<ContactInfo>) {
                val results = mutableMapOf<String, String>()
                if (contactInfoList.isEmpty()) {
                    mainHandler.post { onDone(results) }
                    return
                }
                var pending = contactInfoList.size
                val lock = Any()
                for (info in contactInfoList) {
                    loadAvatarFile(info.avatarURL) { path ->
                        synchronized(lock) {
                            if (!path.isNullOrEmpty()) {
                                results[info.userID] = path
                            }
                            pending -= 1
                            if (pending == 0) {
                                mainHandler.post { onDone(results.toMap()) }
                            }
                        }
                    }
                }
            }

            override fun onFailure(code: Int, desc: String) {
                logger.warn("getContactInfo failed: code=$code desc=$desc")
                mainHandler.post { onDone(emptyMap()) }
            }
        })
    }

    fun loadAvatarPath(url: String, callback: (String?) -> Unit) {
        loadAvatarFile(url, callback)
    }

    fun loadAvatarBitmap(url: String, callback: (Bitmap?) -> Unit) {
        loadAvatar(url, callback)
    }

    private fun loadAvatarFile(url: String?, callback: (String?) -> Unit) {
        if (url.isNullOrEmpty()) {
            mainHandler.post { callback(null) }
            return
        }
        Thread {
            val file = try {
                Glide.with(CallKitContext.requireContext()).asFile().load(url).submit().get()
            } catch (e: Exception) {
                logger.warn("avatar file download failed: ${e.javaClass.simpleName}")
                null
            }
            mainHandler.post { callback(file?.absolutePath) }
        }.start()
    }

    private fun loadAvatar(url: String?, callback: (Bitmap?) -> Unit) {
        if (url.isNullOrEmpty()) {
            mainHandler.post { callback(null) }
            return
        }
        Glide.with(CallKitContext.requireContext())
            .asBitmap()
            .load(url)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    callback(resource)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    logger.warn("avatar load failed")
                    callback(null)
                }

                override fun onLoadCleared(placeholder: Drawable?) {}
            })
    }
}
