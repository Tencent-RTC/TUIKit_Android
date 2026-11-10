package com.tencent.uikit.app.common.widget

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Conversation list window {@link ConversationLayout}、chat window {@link ChatLayout} have title bar，
 * The title bar is designed as a three-part title on the left, middle and right. The left can be
 * picture + text, the middle is text, and the right can also be picture + text. These areas return the
 * standard Android View，These Views can be interactively processed according to business needs。
 */
interface ITitleBarLayout {
    /**
     * Set the click event of the left header
     *
     * @param listener
     */
    fun setOnLeftClickListener(listener: View.OnClickListener?)

    /**
     * Set the click event of the right title
     *
     * @param listener
     */
    fun setOnRightClickListener(listener: View.OnClickListener?)

    /**
     * set Title
     */
    fun setTitle(title: String?, position: Position?)

    /**
     * Return to the left header area
     *
     * @return
     */
    fun getLeftGroup(): LinearLayout

    /**
     * Return to the right header area
     *
     * @return
     */
    fun getRightGroup(): LinearLayout

    /**
     * Returns the image for the left header
     *
     * @return
     */
    fun getLeftIcon(): ImageView

    /**
     * Set the image for the left header
     *
     * @param resId
     */
    fun setLeftIcon(resId: Int)

    /**
     * Returns the image with the right header
     *
     * @return
     */
    fun getRightIcon(): ImageView

    /**
     * Set the image for the title on the right
     *
     * @param resId
     */
    fun setRightIcon(resId: Int)

    /**
     * Returns the text of the left header
     *
     * @return
     */
    fun getLeftTitle(): TextView

    /**
     * Returns the text of the middle title
     *
     * @return
     */
    fun getMiddleTitle(): TextView

    /**
     * Returns the text of the title on the right
     *
     * @return
     */
    fun getRightTitle(): TextView

    /**
     * enumeration value of the header area
     */
    enum class Position {
        /**
         * left title
         */
        LEFT,

        /**
         * middle title
         */
        MIDDLE,

        /**
         * right title
         */
        RIGHT
    }
}