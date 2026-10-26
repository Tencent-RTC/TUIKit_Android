package com.trtc.uikit.roomkit.base.ui

import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewTreeObserver

/**
 * Drives expand/collapse animation for a two-layer bar layout (mainBar + extensionBar inside rootView).
 */
class ExpandableBarAnimator(
    private val rootView: View,
    private val mainBar: View,
    private val extensionBar: View,
) {

    var isExpanded: Boolean = false
        private set

    var onToggleStart: ((expand: Boolean) -> Unit)? = null
    var onToggleEnd: ((expand: Boolean) -> Unit)? = null

    private var animatorSet: AnimatorSet? = null

    private var pendingExpand: Boolean? = null
    private var layoutListenerAttached = false

    fun toggle() {
        setExpanded(!isExpanded)
    }

    fun setExpanded(expand: Boolean) {
        if (animatorSet?.isRunning == true && expand == isExpanded) return
        isExpanded = expand
        onToggleStart?.invoke(expand)
        playAnimation(expand)
    }

    fun cancel() {
        animatorSet?.cancel()
        animatorSet = null
    }

    private fun playAnimation(expand: Boolean) {
        val rootWidth = rootView.width
        val rootHeight = rootView.height
        if (rootWidth <= 0 || rootHeight <= 0) {
            pendingExpand = expand
            ensureLayoutListener()
            return
        }
        pendingExpand = null

        val extensionHeight = extensionBar.height.toFloat().takeIf { it > 0f }
            ?: (rootHeight / 2).toFloat()
        val bgDrawable = rootView.background ?: return

        val translationFrom = if (expand) 0f else -extensionHeight
        val translationTo = if (expand) -extensionHeight else 0f
        val mainBarAnimator = ObjectAnimator.ofFloat(mainBar, "translationY", translationFrom, translationTo)
            .apply { duration = DURATION_SLIDE }

        val alphaFrom = if (expand) 0f else 1f
        val alphaTo = if (expand) 1f else 0f
        val extensionAlphaAnimator = ObjectAnimator.ofFloat(extensionBar, "alpha", alphaFrom, alphaTo)
            .apply { duration = DURATION_FADE }

        val boundsFrom = if (expand) rootHeight / 2 else 0
        val boundsTo = if (expand) 0 else rootHeight / 2
        val boundsAnimator = ValueAnimator.ofInt(boundsFrom, boundsTo).apply {
            duration = DURATION_SLIDE
            addUpdateListener { bgDrawable.setBounds(0, animatedValue as Int, rootWidth, rootHeight) }
        }

        val bgAlphaFrom = if (expand) 0 else 255
        val bgAlphaTo = if (expand) 255 else 0
        val bgAlphaAnimator = ObjectAnimator.ofInt(bgDrawable, "alpha", bgAlphaFrom, bgAlphaTo)
            .apply { duration = DURATION_SLIDE }

        bgAlphaAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) {
                extensionBar.visibility = View.VISIBLE
                if (expand) mainBar.visibility = View.VISIBLE
                bgDrawable.setBounds(0, rootHeight / 2, rootWidth, rootHeight)
            }
        })

        animatorSet?.cancel()
        animatorSet = AnimatorSet().apply {
            playTogether(mainBarAnimator, extensionAlphaAnimator, boundsAnimator)
            if (expand) {
                play(mainBarAnimator).after(bgAlphaAnimator)
            } else {
                play(bgAlphaAnimator).after(mainBarAnimator)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!expand) extensionBar.visibility = View.INVISIBLE
                    onToggleEnd?.invoke(expand)
                }
            })
            start()
        }
    }

    private fun ensureLayoutListener() {
        if (layoutListenerAttached) return
        layoutListenerAttached = true
        rootView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val observer = rootView.viewTreeObserver
                if (!observer.isAlive) return
                observer.removeOnGlobalLayoutListener(this)
                layoutListenerAttached = false
                val pending = pendingExpand ?: return
                playAnimation(pending)
            }
        })
    }

    companion object {
        private const val DURATION_SLIDE = 250L
        private const val DURATION_FADE = 300L
    }
}