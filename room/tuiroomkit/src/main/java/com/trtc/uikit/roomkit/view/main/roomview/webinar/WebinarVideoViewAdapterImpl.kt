package com.trtc.uikit.roomkit.view.main.roomview.webinar

import android.content.Context
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.constraintlayout.utils.widget.ImageFilterView
import com.trtc.uikit.roomkit.R
import com.trtc.uikit.roomkit.base.log.RoomKitLogger
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader
import io.trtc.tuikit.atomicxcore.api.device.DeviceStatus
import io.trtc.tuikit.atomicxcore.api.room.RoomParticipant
import java.lang.ref.WeakReference

class WebinarVideoViewAdapterImpl(context: Context) : WebinarRoomView.VideoViewAdapter {
    private val logger = RoomKitLogger.getLogger("WebinarVideoViewAdapterImpl")

    private val weakContext = WeakReference(context)

    override fun createWidgetView(participant: RoomParticipant): View? {
        val context = weakContext.get()
        if (context == null) {
            return null
        }
        val widgetView = View.inflate(context, R.layout.roomkit_webinar_widget_view, null)
        logger.info("createWidgetView:userID:${participant.userID}, cameraStatus:${participant.cameraStatus}")
        if (participant.cameraStatus == DeviceStatus.OFF) {
            loadAvatar(widgetView, participant)
            widgetView.visibility = VISIBLE
        } else {
            widgetView.visibility = GONE
        }
        return widgetView
    }

    private fun loadAvatar(widgetView: View, participant: RoomParticipant) {
        val context = weakContext.get()
        if (context == null) {
            return
        }
        val avatarPlaceholder: ImageFilterView = widgetView.findViewById(R.id.iv_avatar_placeholder)
        if (participant.avatarURL.isEmpty()) {
            avatarPlaceholder.setImageResource(R.drawable.roomkit_ic_default_avatar)
        } else {
            ImageLoader.load(
                context,
                avatarPlaceholder,
                participant.avatarURL,
                R.drawable.roomkit_ic_default_avatar
            )
        }
    }
}