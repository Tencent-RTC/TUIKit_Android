package io.trtc.tuikit.chat.uikit.components.messagelist.ui.popups
import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.trtc.tuikit.chat.uikit.components.messagelist.model.MessageCustomAction
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.atomicxcore.api.message.MessageInfo
import kotlin.math.roundToInt

internal class LongPressActionMenuBuilder(
    private val context: Context,
    private val density: Float,
    private val colors: ColorTokens,
    private val message: MessageInfo,
    private val actions: List<LongPressPopupAction>,
    private val quickPickerRowWidth: Int,
    private val onDismiss: () -> Unit
) {

    fun build(forceFullColumns: Boolean = false): LongPressPopupContent {
        val pages = actions.chunked(LongPressDimens.PAGE_SIZE)
        if (pages.size <= 1) {
            val pageItems = pages.firstOrNull().orEmpty()
            val columnCount = LongPressActionMenuPolicy.singlePageColumnCount(
                itemCount = pageItems.size,
                forceFullColumns = forceFullColumns
            )
            val contentWidth = LongPressDimens.popupCardWidth(columnCount, density)
            val cardWidth = cardWidthForColumns(columnCount)
            val pageView = buildPage(pageItems, columnCount)
            pageView.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            return LongPressPopupContent(
                view = pageView,
                width = cardWidth,
                contentWidth = contentWidth,
                height = pageView.measuredHeight
            )
        }

        val contentWidth = LongPressDimens.popupCardWidth(LongPressDimens.COLUMNS, density)
        val cardWidth = cardWidthForColumns(LongPressDimens.COLUMNS)
        val pageHeight = LongPressDimens.popupPageHeight(LongPressDimens.MAX_ROWS, density)
        val pagerHeight = pageHeight + LongPressDimens.popupPagerVerticalPadding() * 2
        val pager = ViewPager2(context).apply {
            layoutParams = ViewGroup.LayoutParams(contentWidth, pagerHeight)
            offscreenPageLimit = 1
            adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
                ): RecyclerView.ViewHolder {
                    val pageView = FrameLayout(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    return object : RecyclerView.ViewHolder(pageView) {}
                }

                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val container = holder.itemView as FrameLayout
                    container.removeAllViews()
                    val page = buildPage(pages[position], LongPressDimens.COLUMNS)
                    container.addView(
                        page,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            Gravity.TOP
                        )
                    )
                }

                override fun getItemCount(): Int = pages.size
            }
        }
        val pageIndicator = LongPressPageIndicator(context, density, colors)
        val indicatorHeight = pageIndicator.areaHeight()
        val totalHeight = pagerHeight + indicatorHeight
        val pagerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(contentWidth, totalHeight)
            addView(
                pager,
                LinearLayout.LayoutParams(contentWidth, pagerHeight)
            )
            val indicator = pageIndicator.build(pages.size)
            pageIndicator.attach(pager, indicator)
            addView(
                indicator,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    indicatorHeight
                )
            )
        }
        pagerContainer.measure(
            View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY)
        )
        return LongPressPopupContent(
            view = pagerContainer,
            width = cardWidth,
            contentWidth = contentWidth,
            height = totalHeight
        )
    }

    private fun cardWidthForColumns(columnCount: Int): Int {
        return LongPressDimens.cardWidthForColumns(
            columnCount = columnCount,
            density = density,
            quickPickerRowWidth = quickPickerRowWidth
        )
    }

    private fun buildPage(items: List<LongPressPopupAction>, columnCount: Int): View {
        val itemCellWidth = LongPressDimens.popupItemCellWidth(density)
        val itemCellHeight = LongPressDimens.popupItemCellHeight(density)
        val rows = items.chunked(columnCount)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8.dp, 8.dp, 8.dp, 8.dp)
            rows.forEachIndexed { rowIndex, rowItems ->
                addView(buildRow(rowItems, itemCellWidth, itemCellHeight, columnCount))
                if (rowIndex < rows.lastIndex) {
                    addView(buildDivider())
                }
            }
        }
    }

    private fun buildRow(
        items: List<LongPressPopupAction>,
        cellWidth: Int,
        cellHeight: Int,
        columnCount: Int
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            isBaselineAligned = false
            minimumHeight = cellHeight
            for (col in 0 until columnCount) {
                val action = items.getOrNull(col)
                val child = if (action != null) {
                    buildActionItem(action, cellHeight)
                } else {
                    buildEmptyItem(cellHeight)
                }
                addView(child, LinearLayout.LayoutParams(cellWidth, cellHeight))
            }
        }
    }

    private fun buildActionItem(action: LongPressPopupAction, cellHeight: Int): View {
        val textColor = if (action.dangerousAction) {
            colors.textColorError
        } else {
            colors.textColorSecondary
        }
        val iconTint = if (action.dangerousAction) {
            colors.textColorError
        } else {
            colors.textColorPrimary
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = cellHeight
            setPadding(4.dp, 10.dp, 4.dp, 8.dp)
            isClickable = true
            isFocusable = true
            background = LongPressDrawables.createActionItemRipple(colors, density)
            setOnClickListener {
                onDismiss()
                action.onClick(message)
            }
            addView(
                ImageView(context).apply {
                    if (action.iconResId != 0) {
                        setImageResource(action.iconResId)
                        imageTintList = ColorStateList.valueOf(iconTint)
                    } else {
                        visibility = View.GONE
                    }
                },
                LinearLayout.LayoutParams(18.dp, 18.dp).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
            addView(
                TextView(context).apply {
                    text = action.title
                    gravity = Gravity.CENTER
                    setTextColor(textColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                    maxLines = 1
                    setPadding(0, 5.dp, 0, 0)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            )
        }
    }

    private fun buildEmptyItem(cellHeight: Int): View {
        return View(context).apply {
            minimumHeight = cellHeight
        }
    }

    private fun buildDivider(): View {
        return View(context).apply {
            setBackgroundColor(ColorUtils.setAlphaComponent(colors.strokeColorPrimary, 140))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                topMargin = 4.dp
                bottomMargin = 4.dp
            }
        }
    }

    private val Int.dp: Int
        get() = (this * density).roundToInt()
}

internal object LongPressActionMenuPolicy {

    fun singlePageColumnCount(itemCount: Int, forceFullColumns: Boolean): Int {
        return if (forceFullColumns) {
            LongPressDimens.COLUMNS
        } else {
            LongPressDimens.resolveSinglePageColumnCount(itemCount)
        }
    }

    fun usesPager(itemCount: Int): Boolean {
        return itemCount > LongPressDimens.PAGE_SIZE
    }
}

internal fun toLongPressPopupActions(
    actions: List<MessageCustomAction>
): List<LongPressPopupAction> {
    return actions.map { action ->
        LongPressPopupAction(
            title = action.title,
            dangerousAction = action.dangerous,
            iconResId = action.iconResID,
            onClick = action.action
        )
    }
}

internal data class LongPressPopupAction(
    val title: String,
    val dangerousAction: Boolean,
    @DrawableRes val iconResId: Int,
    val onClick: (MessageInfo) -> Unit
)

internal data class LongPressPopupContent(
    val view: View,
    val width: Int,
    val contentWidth: Int,
    val height: Int
)
