package com.trtc.uikit.livekit.voiceroom.view.preview

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover

class StreamPresetImagePicker(
    context: Context,
    private val config: Config
) : AtomicPopover(context) {

    private var selectedImageURL: String? = null
    private var onConfirmListener: OnConfirmListener? = null
    private var imageGridAdapter: PresetImageGridAdapter? = null

    init {
        initView(context)
    }

    fun setOnConfirmListener(listener: OnConfirmListener) {
        onConfirmListener = listener
    }

    fun updateCurrentImageUrl(imageUrl: String?) {
        config.currentImageUrl = imageUrl
        selectedImageURL = imageUrl
        imageGridAdapter?.updateSelected(imageUrl)
    }

    private fun initView(context: Context) {
        val view = View.inflate(getContext(), R.layout.livekit_layout_select_preset_image, null)
        val title = view.findViewById<TextView>(R.id.title)
        title.text = config.title
        val confirmButton = view.findViewById<TextView>(R.id.btn_confirm)
        confirmButton.text = config.confirmButtonText
        initRecycleView(context, view)
        initBackButton(view)
        initSetCoverButton(view)
        setContent(view)
    }

    private fun initRecycleView(context: Context, view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_image)
        val spanCount = calculateViewColumnCount(context)
        recyclerView.layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.addItemDecoration(PresetImageGridAdapter.GridDividerItemDecoration(context))
        val selectedPosition = config.data.indexOf(config.currentImageUrl)
        selectedImageURL = config.currentImageUrl
        val adapter = PresetImageGridAdapter(
            context,
            config.data,
            selectedPosition,
            object : PresetImageGridAdapter.OnItemClickListener {
                override fun onClick(coverURL: String) {
                    selectedImageURL = coverURL
                }
            })
        imageGridAdapter = adapter
        recyclerView.adapter = adapter
    }

    private fun initBackButton(view: View) {
        view.findViewById<View>(R.id.iv_back).setOnClickListener { dismiss() }
    }

    private fun initSetCoverButton(view: View) {
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            onConfirmListener?.onConfirm(selectedImageURL ?: "")
            dismiss()
        }
    }

    private fun calculateViewColumnCount(context: Context): Int {
        val metrics: DisplayMetrics = context.resources.displayMetrics
        val imageViewDP = 114
        val screenWidth = metrics.widthPixels
        val itemWidth =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, imageViewDP.toFloat(), metrics)
                .toInt()
        return screenWidth / itemWidth
    }

    class Config {
        var title: String? = null
        var confirmButtonText: String? = null
        var data: List<String> = emptyList()
        var currentImageUrl: String? = null
    }

    interface OnConfirmListener {
        fun onConfirm(imageUrl: String)
    }
}