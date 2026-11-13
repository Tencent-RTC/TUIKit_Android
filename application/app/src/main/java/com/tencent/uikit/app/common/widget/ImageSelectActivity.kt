package com.tencent.uikit.app.common.widget

import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.LayoutUtil
import com.tencent.uikit.app.common.utils.ScreenUtil
import com.tencent.uikit.app.common.widget.gatherimage.SynthesizedImageView
import java.io.File
import java.io.Serializable

class ImageSelectActivity : BaseLightActivity() {
    companion object {
        private val TAG = ImageSelectActivity::class.java.simpleName

        const val CHAT_CONVERSATION_BACKGROUND_DEFAULT_URL =
            "chat/conversation/background/default/url"
        const val RESULT_CODE_ERROR = -1
        const val RESULT_CODE_SUCCESS = 0
        const val TITLE = "title"
        const val SPAN_COUNT = "spanCount"
        const val DATA = "data"
        const val ITEM_HEIGHT = "itemHeight"
        const val ITEM_WIDTH = "itemWidth"
        const val SELECTED = "selected"
        const val PLACEHOLDER = "placeholder"
        const val NEED_DOWNLOAD_LOCAL = "needDownload"
    }

    private var defaultSpacing: Int = 0
    private var data: List<ImageBean>? = null
    private var selected: ImageBean? = null
    private var placeHolder: Int = 0
    private var columnNum: Int = 0
    private lateinit var imageGrid: RecyclerView
    private lateinit var gridLayoutManager: GridLayoutManager
    private lateinit var gridAdapter: ImageGridAdapter
    private lateinit var titleBarLayout: TitleBarLayout
    private var itemHeight: Int = 0
    private var itemWidth: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        defaultSpacing = ScreenUtil.dip2px(12f)
        setContentView(R.layout.app_activity_image_select_layout)

        val intent = intent
        val title = intent.getStringExtra(TITLE)
        titleBarLayout = findViewById(R.id.image_select_title)
        titleBarLayout.setTitle(title, ITitleBarLayout.Position.MIDDLE)
        titleBarLayout.setTitle(
            getString(R.string.app_btn_confirm),
            ITitleBarLayout.Position.RIGHT
        )
        titleBarLayout.getRightIcon().visibility = View.GONE
        titleBarLayout.getRightTitle().setTextColor(0xFF006EFF.toInt())
        titleBarLayout.setOnLeftClickListener {
            setResult(RESULT_CODE_ERROR)
            finish()
        }

        val needDownload = intent.getBooleanExtra(NEED_DOWNLOAD_LOCAL, false)
        titleBarLayout.setOnRightClickListener {
            if (selected == null) {
                return@setOnRightClickListener
            }
            if (needDownload) {
                downloadUrl()
            } else {
                val resultIntent = Intent()
                resultIntent.putExtra(DATA, selected as Serializable)
                setResult(RESULT_CODE_SUCCESS, resultIntent)
                finish()
            }
        }

        @Suppress("UNCHECKED_CAST")
        data = intent.getSerializableExtra(DATA) as? List<ImageBean>
        selected = intent.getSerializableExtra(SELECTED) as? ImageBean
        placeHolder = intent.getIntExtra(PLACEHOLDER, 0)
        itemHeight = intent.getIntExtra(ITEM_HEIGHT, 0)
        itemWidth = intent.getIntExtra(ITEM_WIDTH, 0)
        columnNum = intent.getIntExtra(SPAN_COUNT, 2)
        gridLayoutManager = GridLayoutManager(this, columnNum)
        imageGrid = findViewById(R.id.image_select_grid)
        imageGrid.addItemDecoration(GridDecoration(columnNum, defaultSpacing, defaultSpacing))
        imageGrid.layoutManager = gridLayoutManager
        imageGrid.itemAnimator = null
        gridAdapter = ImageGridAdapter()
        gridAdapter.setPlaceHolder(placeHolder)
        gridAdapter.setSelected(selected)
        gridAdapter.setOnItemClickListener { obj ->
            selected = obj
            setSelectedStatus()
        }
        gridAdapter.setItemWidth(itemWidth)
        gridAdapter.setItemHeight(itemHeight)
        imageGrid.adapter = gridAdapter
        gridAdapter.setData(data)
        setSelectedStatus()
        gridAdapter.notifyDataSetChanged()
    }

    private fun downloadUrl() {
        if (selected == null) {
            return
        }

        if (selected!!.isDefault()) {
            selected!!.setLocalPath(CHAT_CONVERSATION_BACKGROUND_DEFAULT_URL)
            setResult(selected!!)
            Toast.makeText(this, resources.getString(R.string.app_set_success), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val url = selected!!.getImageUri()
        if (TextUtils.isEmpty(url)) {
            Log.d(TAG, "DownloadUrl is null")
            return
        }

        val dialog = ProgressDialog(this)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            finish()
        }
        dialog.setMessage(resources.getString(R.string.app_setting))
        dialog.show()

        val finalBean = selected!!
        Glide.with(this)
            .downloadOnly()
            .load(url)
            .listener(object : RequestListener<File> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<File>,
                    isFirstResource: Boolean
                ): Boolean {
                    dialog.cancel()
                    Log.e(TAG, "DownloadUrl onLoadFailed e = $e")
                    Toast.makeText(this@ImageSelectActivity, resources.getString(R.string.app_set_fail), Toast.LENGTH_SHORT).show()
                    return false
                }

                override fun onResourceReady(
                    resource: File,
                    model: Any,
                    target: Target<File>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    dialog.cancel()
                    val path = resource.absolutePath
                    Log.e(TAG, "DownloadUrl resource path = $path")
                    finalBean.setLocalPath(path)
                    setResult(finalBean)
                    Toast.makeText(this@ImageSelectActivity, resources.getString(R.string.app_set_success), Toast.LENGTH_SHORT).show()
                    return false
                }
            })
            .preload()
    }

    private fun setResult(bean: ImageBean) {
        val resultIntent = Intent()
        resultIntent.putExtra(DATA, bean as Serializable)
        setResult(RESULT_CODE_SUCCESS, resultIntent)
        finish()
    }

    private fun setSelectedStatus() {
        if (selected != null && data != null && data!!.contains(selected)) {
            titleBarLayout.getRightTitle().isEnabled = true
//            titleBarLayout.getRightTitle().setTextColor(
//                resources.getColor(
//                    TUIThemeManager.getAttrResId(
//                        this,
//                        com.tencent.qcloud.tuicore.R.attr.core_primary_color
//                    )
//                )
//            )
        } else {
            titleBarLayout.getRightTitle().isEnabled = false
            titleBarLayout.getRightTitle().setTextColor(0xFF666666.toInt())
        }
        gridAdapter.setSelected(selected)
    }

    class ImageGridAdapter : RecyclerView.Adapter<ImageGridAdapter.ImageViewHolder>() {
        private var itemWidth: Int = 0
        private var itemHeight: Int = 0
        private var data: List<ImageBean>? = null
        private var selected: ImageBean? = null
        private var placeHolder: Int = 0
        private var onItemClickListener: OnItemClickListener? = null

        fun setData(data: List<ImageBean>?) {
            this.data = data
        }

        fun setSelected(selected: ImageBean?) {
            if (data == null || data!!.isEmpty()) {
                this.selected = selected
            } else {
                this.selected = selected
                notifyDataSetChanged()
            }
        }

        fun setPlaceHolder(placeHolder: Int) {
            this.placeHolder = placeHolder
        }

        fun setItemHeight(itemHeight: Int) {
            this.itemHeight = itemHeight
        }

        fun setItemWidth(itemWidth: Int) {
            this.itemWidth = itemWidth
        }

        fun setOnItemClickListener(onItemClickListener: OnItemClickListener) {
            this.onItemClickListener = onItemClickListener
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view =
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.app_select_image_item_layout, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imageView = holder.imageView
            setItemLayoutParams(holder)
            val imageBean = data!![position]
            if (selected != null && imageBean != null && TextUtils.equals(
                    selected!!.getThumbnailUri(),
                    imageBean.getThumbnailUri()
                )
            ) {
                holder.selectBorderLayout.visibility = View.VISIBLE
            } else {
                holder.selectBorderLayout.visibility = View.GONE
            }

            if (imageBean.getGroupGridAvatar() != null) {
                holder.defaultLayout.visibility = View.GONE
                if (imageView is SynthesizedImageView) {
                    val synthesizedImageView = imageView
                    val imageId = imageBean.getImageId()
                    synthesizedImageView.setImageId(imageId ?: "")
                    synthesizedImageView.displayImage(imageBean.getGroupGridAvatar())
                        .load(imageId ?: "")
                }
            } else if (imageBean.isDefault()) {
                holder.defaultLayout.visibility = View.VISIBLE
                imageView.setImageResource(android.R.color.transparent)
            } else {
                holder.defaultLayout.visibility = View.GONE
                Glide.with(holder.itemView.context)
                    .asBitmap()
                    .load(imageBean.getThumbnailUri())
                    .placeholder(placeHolder)
                    .apply(RequestOptions().error(placeHolder))
                    .into(imageView)
            }

            holder.itemView.setOnClickListener {
                onItemClickListener?.onClick(imageBean)
            }
        }

        private fun setItemLayoutParams(holder: ImageViewHolder) {
            if (itemHeight > 0 && itemWidth > 0) {
                val itemViewLayoutParams = holder.itemView.layoutParams
                itemViewLayoutParams.width = itemWidth
                itemViewLayoutParams.height = itemHeight
                holder.itemView.layoutParams = itemViewLayoutParams

                val params = holder.imageView.layoutParams
                params.width = itemWidth
                params.height = itemHeight
                holder.imageView.layoutParams = params

                val borderLayoutParams = holder.selectBorderLayout.layoutParams
                borderLayoutParams.width = itemWidth
                borderLayoutParams.height = itemHeight
                holder.selectBorderLayout.layoutParams = borderLayoutParams

                val borderParams = holder.selectedBorder.layoutParams
                borderParams.width = itemWidth
                borderParams.height = itemHeight
                holder.selectedBorder.layoutParams = borderParams
            }
        }

        override fun getItemCount(): Int {
            return if (data == null || data!!.isEmpty()) {
                0
            } else data!!.size
        }

        class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.content_image)
            val selectedBorder: ImageView = itemView.findViewById(R.id.select_border)
            val selectBorderLayout: RelativeLayout =
                itemView.findViewById(R.id.selected_border_area)
            val defaultLayout: Button = itemView.findViewById(R.id.default_image_layout)
        }
    }

    /**
     * add spacing
     */
    class GridDecoration(
        private val columnNum: Int, // span count
        private val leftRightSpace: Int, // vertical spacing
        private val topBottomSpace: Int // horizontal spacing
    ) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            val column = position % columnNum

            val left = column * leftRightSpace / columnNum
            val right = leftRightSpace * (columnNum - 1 - column) / columnNum
            if (LayoutUtil.isRTL()) {
                outRect.left = right
                outRect.right = left
            } else {
                outRect.left = left
                outRect.right = right
            }
            // add top spacing
            if (position >= columnNum) {
                outRect.top = topBottomSpace
            }
        }
    }

    fun interface OnItemClickListener {
        fun onClick(obj: ImageBean)
    }

    class ImageBean : Serializable {
        private var thumbnailUri: String? = null // for display
        internal var imageUri: String? = null // for download
        private var localPath: String? = null // for local path
        private var isDefault = false // for default display
        private var groupGridAvatar: List<Any>? = null // for group grid avatar
        private var imageId: String? = null

        constructor()

        constructor(thumbnailUri: String?, imageUri: String?, isDefault: Boolean) {
            this.thumbnailUri = thumbnailUri
            this.imageUri = imageUri
            this.isDefault = isDefault
        }

        fun getImageUri(): String? {
            return imageUri
        }

        fun getThumbnailUri(): String? {
            return thumbnailUri
        }

        fun setImageUri(imageUri: String?) {
            this.imageUri = imageUri
        }

        fun setThumbnailUri(thumbnailUri: String?) {
            this.thumbnailUri = thumbnailUri
        }

        fun getLocalPath(): String? {
            return localPath
        }

        fun setLocalPath(localPath: String?) {
            this.localPath = localPath
        }

        fun isDefault(): Boolean {
            return isDefault
        }

        fun setDefault(aDefault: Boolean) {
            isDefault = aDefault
        }

        fun getGroupGridAvatar(): List<Any>? {
            return groupGridAvatar
        }

        fun setGroupGridAvatar(groupGridAvatar: List<Any>?) {
            this.groupGridAvatar = groupGridAvatar
        }

        fun getImageId(): String? {
            return imageId
        }

        fun setImageId(imageId: String?) {
            this.imageId = imageId
        }
    }
}