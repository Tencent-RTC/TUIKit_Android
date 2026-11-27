package io.trtc.tuikit.chat.uikit.components.imageviewer.ui
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.chat.uikit.components.imageviewer.ImageElement

class ImageViewerPagerAdapter(
    private val onImageTap: () -> Unit,
    private val onCloseRequested: () -> Unit,
    private val onDownloadRequested: (ImageElement) -> Unit,
    private val resolveVideoData: (ImageElement) -> Any?,
    private val isDownloading: (ImageElement) -> Boolean
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<ImageElement>()

    @Suppress("NotifyDataSetChanged")
    fun submitItems(newItems: List<ImageElement>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItemOrNull(position: Int): ImageElement? {
        return items.getOrNull(position)
    }

    override fun getItemCount(): Int {
        return items.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].type == 0) VIEW_TYPE_IMAGE else VIEW_TYPE_VIDEO
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_IMAGE) {
            ImagePageViewHolder(ZoomableImagePageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            })
        } else {
            VideoPageViewHolder(VideoMediaPageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
            })
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is ImagePageViewHolder -> holder.bind(item, onImageTap)
            is VideoPageViewHolder -> holder.bind(
                item = item,
                effectiveVideoData = resolveVideoData(item),
                isDownloading = isDownloading(item),
                onImageTap = onImageTap,
                onCloseRequested = onCloseRequested,
                onDownloadRequested = onDownloadRequested
            )
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is VideoPageViewHolder) {
            holder.pageView.onRelease()
        }
        super.onViewRecycled(holder)
    }

    class ImagePageViewHolder(
        val pageView: ZoomableImagePageView
    ) : RecyclerView.ViewHolder(pageView) {
        fun bind(item: ImageElement, onImageTap: () -> Unit) {
            pageView.bind(item, onImageTap)
        }
    }

    class VideoPageViewHolder(
        val pageView: VideoMediaPageView
    ) : RecyclerView.ViewHolder(pageView) {
        fun bind(
            item: ImageElement,
            effectiveVideoData: Any?,
            isDownloading: Boolean,
            onImageTap: () -> Unit,
            onCloseRequested: () -> Unit,
            onDownloadRequested: (ImageElement) -> Unit
        ) {
            pageView.bind(
                item = item,
                effectiveVideoData = effectiveVideoData,
                isDownloading = isDownloading,
                onImageTap = onImageTap,
                onCloseRequested = onCloseRequested,
                onDownloadRequested = onDownloadRequested
            )
        }
    }

    companion object {
        private const val VIEW_TYPE_IMAGE = 0
        private const val VIEW_TYPE_VIDEO = 1
    }
}
