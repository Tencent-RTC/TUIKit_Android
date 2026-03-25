package com.trtc.uikit.livekit.component.gift.view.animation.manager

import com.trtc.uikit.livekit.common.LiveKitLogger
import com.trtc.uikit.livekit.common.LiveKitLogger.Companion.getComponentLogger
import com.trtc.uikit.livekit.component.gift.viewmodel.GiftModel

class GiftAnimationManager(private val maxChannels: Int = 3) {
    private val logger: LiveKitLogger = getComponentLogger("GiftAnimationManager")
    private val giftWaitQueue: MutableList<GiftModel> = ArrayList()
    private var animationPlayer: AnimationPlayer? = null
    private var currentPlayingCount = 0

    fun setPlayer(player: AnimationPlayer?) {
        animationPlayer = player
        animationPlayer?.setCallback { error: Int -> this.onFinished(error) }
    }

    fun add(gift: GiftModel) {
        if (currentPlayingCount < maxChannels) {
            playDirectly(gift)
            return
        }
        addToQueueWithAggregation(gift)
    }

    fun startPlay(model: GiftModel) {
        logger.info("startPlay:${model.gift?.resourceURL},currentPlayingCount:$currentPlayingCount")
        animationPlayer?.startPlay(model)
    }

    fun stopPlay() {
        logger.info("stopPlay:currentPlayingCount:$currentPlayingCount")
        currentPlayingCount = 0
        giftWaitQueue.clear()
        animationPlayer?.stopPlay()
    }

    fun finishPlay() {
        currentPlayingCount = maxOf(0, currentPlayingCount - 1)
        if (giftWaitQueue.isNotEmpty()) {
            val nextGift = giftWaitQueue.removeAt(0)
            playDirectly(nextGift)
        }
    }

    private fun playDirectly(gift: GiftModel) {
        currentPlayingCount++
        preparePlay(gift)
    }

    private fun addToQueueWithAggregation(gift: GiftModel) {
        val existingIndex = giftWaitQueue.indexOfFirst { it.comboKey == gift.comboKey }

        if (existingIndex != -1) {
            giftWaitQueue[existingIndex].addGiftCount(gift.giftCount)
        } else {
            if (gift.isFromSelf) {
                giftWaitQueue.add(0, gift)
            } else {
                giftWaitQueue.add(gift)
            }

            if (giftWaitQueue.size > MAX_QUEUE_LENGTH) {
                val removeIndex = giftWaitQueue.indexOfLast { !it.isFromSelf }
                if (removeIndex != -1) {
                    giftWaitQueue.removeAt(removeIndex)
                } else {
                    giftWaitQueue.removeAt(0)
                }
            }
        }
    }

    private fun preparePlay(model: GiftModel) {
        logger.info("preparePlay:${model.gift?.resourceURL},currentPlayingCount:$currentPlayingCount")
        animationPlayer?.preparePlay(model)
    }

    private fun onFinished(error: Int) {
        finishPlay()
    }

    companion object {
        private const val MAX_QUEUE_LENGTH = 20
    }
}
