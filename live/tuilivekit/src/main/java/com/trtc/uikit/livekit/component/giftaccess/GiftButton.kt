package com.trtc.uikit.livekit.component.giftaccess

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicxcore.api.live.LiveEndedReason
import io.trtc.tuikit.atomicxcore.api.live.LiveListListener
import io.trtc.tuikit.atomicxcore.api.live.LiveListStore

@SuppressLint("ViewConstructor")
class GiftButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    var roomId: String? = null
    var ownerId: String? = null
    var ownerName: String? = null
    var ownerAvatarUrl: String? = null
    private lateinit var imageButton: ImageView
    private var giftSendDialog: GiftSendDialog? = null

    private val liveListListener = object : LiveListListener() {
        override fun onLiveEnded(liveID: String, reason: LiveEndedReason, message: String) {
            giftSendDialog?.dismiss()
        }
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.gift_layout_extension_view, this)
    }

    fun init(roomId: String, ownerId: String, ownerName: String, ownerAvatarUrl: String) {
        this.roomId = roomId
        this.ownerId = ownerId
        this.ownerName = ownerName
        this.ownerAvatarUrl = ownerAvatarUrl
        initView()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LiveListStore.shared().addLiveListListener(liveListListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        LiveListStore.shared().removeLiveListListener(liveListListener)
    }

    private fun initView() {
        bindViewId()
        initImageButton()
    }

    private fun bindViewId() {
        imageButton = findViewById(R.id.iv_gift)
    }

    private fun initImageButton() {
        imageButton.setOnClickListener {
            showGiftPanel()
        }
    }

    fun showGiftPanel() {
        if (roomId == null || ownerId == null || ownerName == null || ownerAvatarUrl == null) return
        giftSendDialog = GiftSendDialog(context, roomId!!, ownerId!!, ownerName!!, ownerAvatarUrl!!)
        giftSendDialog?.show()
    }
}