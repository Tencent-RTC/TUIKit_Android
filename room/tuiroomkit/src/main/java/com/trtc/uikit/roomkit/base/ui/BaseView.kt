package com.trtc.uikit.roomkit.base.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.trtc.tuikit.atomicxcore.api.room.RoomType

/**
 * Base class for all room UI components.
 * Provides standardized lifecycle management and observer pattern for state changes.
 *
 * Subclasses should:
 * - Initialize UI in init block
 * - Declare a Job for managing coroutines
 * - Override addObserver() to observe Store state changes
 * - Override removeObserver() to clean up resources
 * - Call init(roomID) to start observing
 *
 * Example:
 * ```kotlin
 * class CustomView(context: Context) : BaseView(context) {
 *     private var subscribeJob: Job? = null
 *     private var participantStore: RoomParticipantStore? = null
 *
 *     override fun initStore(roomID: String) {
 *         participantStore = RoomParticipantStore.create(roomID)
 *     }
 *
 *     override fun addObserver() {
 *         val store = participantStore ?: return
 *         subscribeJob = CoroutineScope(Dispatchers.Main).launch {
 *             launch { store.state.participantList.collect { updateList(it) } }
 *         }
 *     }
 *
 *     override fun removeObserver() {
 *         subscribeJob?.cancel()
 *     }
 * }
 * ```
 */
abstract class BaseView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    protected var roomID: String = ""
    private var isObserving = false

    protected open fun init(roomID: String) {
        if (roomID.isEmpty()) {
            return
        }
        this.roomID = roomID
        startObservingIfNeeded()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (roomID.isEmpty()) {
            return
        }
        startObservingIfNeeded()
    }

    override fun onDetachedFromWindow() {
        stopObserving()
        super.onDetachedFromWindow()
    }

    private fun startObservingIfNeeded() {
        if (isObserving) {
            return
        }
        if (roomID.isEmpty()) {
            return
        }
        initStore(roomID)
        addObserver()
        isObserving = true
    }

    private fun stopObserving() {
        if (!isObserving) {
            return
        }
        removeObserver()
        isObserving = false
    }

    /**
     * Initialize Store instance with the given roomID.
     * Called automatically after init(roomID) and before addObserver().
     */
    protected abstract fun initStore(roomID: String)

    /**
     * Add observer to state changes from Store.
     * Called automatically after initStore().
     * Use a parent coroutine with Job to manage all subscriptions.
     */
    protected abstract fun addObserver()

    /**
     * Remove observer and clean up resources.
     * Called automatically in onDetachedFromWindow().
     * Cancel your Job to stop all child coroutines.
     */
    protected abstract fun removeObserver()
}