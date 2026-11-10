package com.tencent.uikit.app.common.widget

import android.app.Activity
import android.content.Context
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.ScreenUtil

class TitleBarLayout : LinearLayout, ITitleBarLayout {
    private lateinit var mLeftGroup: LinearLayout
    private lateinit var mRightGroup: LinearLayout
    private lateinit var mLeftTitle: TextView
    private lateinit var mCenterTitle: TextView
    private lateinit var mRightTitle: TextView
    private lateinit var mLeftIcon: ImageView
    private lateinit var mRightIcon: ImageView
    private lateinit var mTitleLayout: RelativeLayout
    private lateinit var unreadCountTextView: UnreadCountTextView

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        var middleTitle: String? = null
        var canReturn = false
        if (attrs != null) {
            val array: TypedArray = context.obtainStyledAttributes(attrs, R.styleable.TitleBarLayout)
            middleTitle = array.getString(R.styleable.TitleBarLayout_title_bar_middle_title)
            canReturn = array.getBoolean(R.styleable.TitleBarLayout_title_bar_can_return, false)
            array.recycle()
        }
        inflate(context, R.layout.app_layout_title_bar, this)
        mTitleLayout = findViewById(R.id.page_title_layout)
        mLeftGroup = findViewById(R.id.page_title_left_group)
        mRightGroup = findViewById(R.id.page_title_right_group)
        mLeftTitle = findViewById(R.id.page_title_left_text)
        mRightTitle = findViewById(R.id.page_title_right_text)
        mCenterTitle = findViewById(R.id.page_title)
        mLeftIcon = findViewById(R.id.page_title_left_icon)
        val leftIconDrawable: Drawable? = mLeftIcon.background
        leftIconDrawable?.isAutoMirrored = true
        mRightIcon = findViewById(R.id.page_title_right_icon)
        unreadCountTextView = findViewById(R.id.new_message_total_unread)

        val params = mTitleLayout.layoutParams as LayoutParams
        params.height = ScreenUtil.getPxByDp(50f)
        mTitleLayout.layoutParams = params
        setBackgroundResource(R.drawable.app_title_bar_bg_light)

        val iconSize = ScreenUtil.dip2px(20f)
        var iconParams = mLeftIcon.layoutParams
        iconParams.width = iconSize
        iconParams.height = iconSize
        mLeftIcon.layoutParams = iconParams
        iconParams = mRightIcon.layoutParams
        iconParams.width = iconSize
        iconParams.height = iconSize

        mRightIcon.layoutParams = iconParams

        if (canReturn) {
            setLeftReturnListener(context)
        }
        if (!TextUtils.isEmpty(middleTitle)) {
            mCenterTitle.text = middleTitle
        }
    }

    fun setLeftReturnListener(context: Context) {
        mLeftGroup.setOnClickListener {
            if (context is Activity) {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(this@TitleBarLayout.windowToken, 0)
                context.finish()
            }
        }
    }

    override fun setOnLeftClickListener(listener: OnClickListener?) {
        mLeftGroup.setOnClickListener(listener)
    }

    override fun setOnRightClickListener(listener: OnClickListener?) {
        mRightGroup.setOnClickListener(listener)
    }

    override fun setTitle(title: String?, position: ITitleBarLayout.Position?) {
        when (position) {
            ITitleBarLayout.Position.LEFT -> mLeftTitle.text = title
            ITitleBarLayout.Position.RIGHT -> mRightTitle.text = title
            ITitleBarLayout.Position.MIDDLE -> mCenterTitle.text = title
            else -> {}
        }
    }

    override fun getLeftGroup(): LinearLayout {
        return mLeftGroup
    }

    override fun getRightGroup(): LinearLayout {
        return mRightGroup
    }

    override fun getLeftIcon(): ImageView {
        return mLeftIcon
    }

    override fun setLeftIcon(resId: Int) {
        mLeftIcon.setBackgroundResource(resId)
    }

    override fun getRightIcon(): ImageView {
        return mRightIcon
    }

    override fun setRightIcon(resId: Int) {
        mRightIcon.setBackgroundResource(resId)
    }

    override fun getLeftTitle(): TextView {
        return mLeftTitle
    }

    override fun getMiddleTitle(): TextView {
        return mCenterTitle
    }

    override fun getRightTitle(): TextView {
        return mRightTitle
    }

    fun getUnreadCountTextView(): UnreadCountTextView {
        return unreadCountTextView
    }
}