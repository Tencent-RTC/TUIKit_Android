package com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit.livecoverpicker

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.utils.widget.ImageFilterView
import androidx.recyclerview.widget.RecyclerView
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.common.imageloader.ImageLoader

class LiveCoverImagePickAdapter(
    private val context: Context,
    private val dataList: List<String>,
    private var selectedPosition: Int,
    private val itemClickListener: OnItemClickListener
) : RecyclerView.Adapter<LiveCoverImagePickAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.anchor_prepare_layout_pick_cover_image_item, parent, false
        )
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageURL = dataList[position]
        ImageLoader.load(context, holder.image, imageURL.replace(".png", "_thumb.png"),
            R.drawable.anchor_prepare_live_stream_default_cover)
        holder.imageSelectedContainer.visibility = if (position == selectedPosition) View.VISIBLE else View.INVISIBLE
        
        holder.image.setOnClickListener {
            notifyItemChanged(selectedPosition)
            selectedPosition = holder.bindingAdapterPosition
            notifyItemChanged(position)
            itemClickListener.onClick(imageURL)
        }
    }

    override fun getItemCount(): Int = dataList.size

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageFilterView = itemView.findViewById(R.id.image)
        val imageSelectedContainer: View = itemView.findViewById(R.id.image_selected_container)
    }

    class GridDividerItemDecoration(context: Context) : RecyclerView.ItemDecoration() {
        private val dividerValue: Int

        init {
            val metrics = context.resources.displayMetrics
            dividerValue = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14f, metrics).toInt()
        }

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.top = dividerValue
            outRect.left = dividerValue
        }
    }

    fun interface OnItemClickListener {
        fun onClick(coverURL: String)
    }
}