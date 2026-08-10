package io.trtc.tuikit.chat.uikit.components.messageinput.ui
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import io.trtc.tuikit.chat.uikit.components.emojipicker.EmojiSpanHelper
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.Emoji
import io.trtc.tuikit.chat.uikit.components.emojipicker.model.EmojiGroup
import io.trtc.tuikit.chat.uikit.components.emojipicker.ui.EmojiPickerView
import io.trtc.tuikit.chat.uikit.components.messageinput.data.MessageInputMenuAction
import io.trtc.tuikit.chat.uikit.components.messageinput.state.PanelState

internal class MessageInputPanelContentController private constructor(
    val emojiPickerView: EmojiPickerView,
    val moreActionsPanel: MoreActionsPanelView,
    private val panelContainer: FrameLayout,
    private val currentPanel: () -> PanelState?
) {
    fun refreshMorePanelActions(actions: List<MessageInputMenuAction>) {
        moreActionsPanel.setActions(actions)
    }

    fun cancelAnimations() {
        emojiPickerView.animate()?.cancel()
        moreActionsPanel.animate()?.cancel()
    }

    fun hidePanelContent() {
        Log.d(TAG, "hidePanelContent: GONE emojiPicker + moreActionsPanel")
        cancelAnimations()
        emojiPickerView.alpha = 1f
        moreActionsPanel.alpha = 1f
        emojiPickerView.visibility = View.GONE
        moreActionsPanel.visibility = View.GONE
    }

    fun showPanelContent(panel: PanelState, crossfade: Boolean = false) {
        Log.d(TAG, "showPanelContent: $panel | crossfade=$crossfade | containerH=${panelContainer.layoutParams?.height}")
        val incoming = if (panel == PanelState.EMOJI_PANEL) emojiPickerView else moreActionsPanel
        val outgoing = if (panel == PanelState.EMOJI_PANEL) moreActionsPanel else emojiPickerView

        incoming.animate()?.cancel()
        outgoing.animate()?.cancel()

        if (crossfade) {
            incoming.alpha = 0f
            incoming.visibility = View.VISIBLE
            incoming.animate().alpha(1f).setDuration(PANEL_ANIM_DURATION_MS).start()
            outgoing.animate().alpha(0f).setDuration(PANEL_ANIM_DURATION_MS).withEndAction {
                if (currentPanel() != panelOf(outgoing)) {
                    outgoing.visibility = View.GONE
                }
                outgoing.alpha = 1f
            }.start()
        } else {
            emojiPickerView.alpha = 1f
            moreActionsPanel.alpha = 1f
            emojiPickerView.visibility = if (panel == PanelState.EMOJI_PANEL) View.VISIBLE else View.GONE
            moreActionsPanel.visibility = if (panel == PanelState.MORE_PANEL) View.VISIBLE else View.GONE
        }
        panelContainer.visibility = View.VISIBLE
    }

    private fun panelOf(view: View): PanelState? {
        return when (view) {
            emojiPickerView -> PanelState.EMOJI_PANEL
            moreActionsPanel -> PanelState.MORE_PANEL
            else -> null
        }
    }

    companion object {
        private const val TAG = "MsgInput.PanelContent"
        private const val PANEL_ANIM_DURATION_MS = 250L

        fun attach(
            context: Context,
            panelContainer: FrameLayout,
            editText: AtomicEditText,
            currentPanel: () -> PanelState?,
            onSendClick: () -> Unit,
            onCustomEmojiClick: (EmojiGroup, Emoji) -> Unit = { _, _ -> }
        ): MessageInputPanelContentController {
            val picker = EmojiPickerView(context)
            picker.setup(
                onEmojiClick = { group, emoji ->
                    if (group.isLittleEmoji) {
                        editText.insertText(emoji.key)
                        EmojiSpanHelper.processEditTextEmoji(editText)
                    } else {
                        onCustomEmojiClick(group, emoji)
                    }
                },
                onSendClick = onSendClick,
                onDeleteClick = { editText.deleteAtCursor() }
            )
            panelContainer.addView(
                picker,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            picker.visibility = View.GONE

            val morePanel = MoreActionsPanelView(context)
            panelContainer.addView(
                morePanel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            morePanel.visibility = View.GONE

            return MessageInputPanelContentController(
                emojiPickerView = picker,
                moreActionsPanel = morePanel,
                panelContainer = panelContainer,
                currentPanel = currentPanel
            )
        }
    }
}
