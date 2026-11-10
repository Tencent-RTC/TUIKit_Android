package com.tencent.uikit.app.mine

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.GlideEngine
import com.tencent.uikit.app.common.utils.ScreenUtil
import com.tencent.uikit.app.common.widget.ImageSelectActivity
import com.tencent.uikit.app.common.widget.PopupInputCard
import com.tencent.uikit.app.common.widget.RoundCornerImageView
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.uikit.app.setting.LanguageSelectActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

private data class SettingItem(val type: SettingType, val title: String)

private enum class SettingType { AVATAR, NICKNAME, LANGUAGE, ACCOUNT }

private class SettingsAdapter(
    private val items: List<SettingItem>,
    private val onItemClick: (SettingItem) -> Unit,
    private val getNickName: () -> String?,
    private val getFaceUrl: () -> String?
) :
    RecyclerView.Adapter<SettingsVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_item_settings, parent, false)
        return SettingsVH(view, getNickName, getFaceUrl)
    }

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: SettingsVH, position: Int) {
        val item = items[position]
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick(item) }
    }
}

private class SettingsVH(
    itemView: View, private val getNickName: () -> String?,
    private val getFaceUrl: () -> String?
) : RecyclerView.ViewHolder(itemView) {
    private val title: TextView = itemView.findViewById(R.id.tv_title)
    private val rightIcon: ImageView = itemView.findViewById(R.id.iv_right)
    private val imageAvatarPreview: RoundCornerImageView? = itemView.findViewById(R.id.iv_avatar_preview)
    private val valueText: TextView? = itemView.findViewById(R.id.tv_value)
    fun bind(item: SettingItem) {
        title.text = item.title
        rightIcon.setImageResource(R.drawable.app_ic_details)
        when (item.type) {
            SettingType.AVATAR -> {
                imageAvatarPreview?.visibility = View.VISIBLE
                valueText?.visibility = View.GONE
                rightIcon.visibility = View.VISIBLE
                GlideEngine.loadUserIcon(imageAvatarPreview!!, getFaceUrl.invoke() ?: "")
            }

            SettingType.NICKNAME -> {
                imageAvatarPreview?.visibility = View.GONE
                valueText?.visibility = View.VISIBLE
                valueText?.text = getNickName.invoke() ?: ""
                rightIcon.visibility = View.VISIBLE
            }

            SettingType.ACCOUNT -> {
                imageAvatarPreview?.visibility = View.GONE
                valueText?.visibility = View.VISIBLE
                valueText?.text = LoginStore.shared.loginState.loginUserInfo.value?.userID
                rightIcon.visibility = View.GONE
            }

            SettingType.LANGUAGE -> {
                imageAvatarPreview?.visibility = View.GONE
                valueText?.visibility = View.GONE
                rightIcon.visibility = View.VISIBLE
            }
        }
    }
}

class SettingsActivity : BaseActivity() {

    private var nickName: String? = null
    private var faceUrl: String? = null

    private val CHOOSE_AVATAR_REQUEST_CODE = 1001

    private lateinit var settingsAdapter: SettingsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_mine_settings)
        nickName = LoginStore.shared.loginState.loginUserInfo.value?.nickname ?: ""
        faceUrl = LoginStore.shared.loginState.loginUserInfo.value?.avatarURL ?: ""

        initView()
    }

    private fun initView() {
        findViewById<ImageView>(R.id.img_back_with_line)?.setOnClickListener { finish() }

        val recycler = findViewById<RecyclerView>(R.id.recycler_settings)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))

        val items = listOf(
            SettingItem(SettingType.AVATAR, getString(R.string.app_user_avatar)),
            SettingItem(SettingType.NICKNAME, getString(R.string.app_user_nickname)),
//            SettingItem(SettingType.LANGUAGE, getString(R.string.app_language)),
        )
        settingsAdapter = SettingsAdapter(
            items, { item ->
                when (item.type) {
                    SettingType.AVATAR -> {
                        val faceList = ArrayList<ImageSelectActivity.ImageBean>()
                        for (i in 0..<AVATAR_FACE_COUNT) {
                            val imageBean = ImageSelectActivity.ImageBean()
                            imageBean.setThumbnailUri(
                                String.format(AVATAR_FACE_URL, (i + 1).toString() + "")
                            )
                            imageBean.setImageUri(String.format(AVATAR_FACE_URL, (i + 1).toString() + ""))
                            faceList.add(imageBean)
                        }
                        val intent = Intent(this@SettingsActivity, ImageSelectActivity::class.java)
                        intent.putExtra(ImageSelectActivity.TITLE, getString(R.string.app_choose_avatar))
                        intent.putExtra(ImageSelectActivity.SPAN_COUNT, 4)
                        val itemWidth = (ScreenUtil.getScreenWidth(this) * 0.2f).toInt()
                        intent.putExtra(ITEM_WIDTH, itemWidth)
                        intent.putExtra(ITEM_HEIGHT, itemWidth)
                        intent.putExtra(ImageSelectActivity.DATA, faceList)
                        intent.putExtra(
                            ImageSelectActivity.SELECTED,
                            ImageSelectActivity.ImageBean(faceUrl, faceUrl, false)
                        )
                        startActivityForResult(intent, CHOOSE_AVATAR_REQUEST_CODE)
                    }

                    SettingType.LANGUAGE -> LanguageSelectActivity.startSelectLanguage(this)
                    SettingType.NICKNAME -> {
                        val popupInputCard = PopupInputCard(this)
                        popupInputCard.setContent(nickName ?: "")
                        popupInputCard.setTitle(getString(R.string.app_self_detail_modify_nickname))
                        val description: String = getString(R.string.app_self_detail_modify_nickname_rule)
                        popupInputCard.setDescription(description)
                        popupInputCard.setOnPositive((PopupInputCard.OnClickListener { result: String? ->
                            nickName = result
                            settingsAdapter.notifyItemChanged(1)
                            updateProfile()
                        }))
                        val rootView = findViewById<View?>(android.R.id.content)
                        popupInputCard.show(rootView, Gravity.BOTTOM)
                    }

                    else -> {}
                }
            },
            { if (nickName.isNullOrEmpty()) LoginStore.shared.loginState.loginUserInfo.value?.nickname else nickName },
            { if (faceUrl.isNullOrEmpty()) LoginStore.shared.loginState.loginUserInfo.value?.avatarURL else faceUrl }
        )
        recycler.adapter = settingsAdapter
    }

    private fun updateProfile() {
        val v2TIMUserFullInfo = V2TIMUserFullInfo()
        if (!faceUrl.isNullOrEmpty()) {
            v2TIMUserFullInfo.faceUrl = faceUrl
        }

        v2TIMUserFullInfo.setNickname(nickName)
        UserManager.getInstance().updateSelfUserInfo(v2TIMUserFullInfo, object : V2TIMCallback {
            override fun onError(code: Int, desc: String?) {
                Log.e(
                    "SettingsActivity", "modifySelfProfile err code = " + code + ", desc = " + desc
                )
                Toast.makeText(this@SettingsActivity, "Error code = $code, desc = $desc", Toast.LENGTH_SHORT).show()
            }

            override fun onSuccess() {
                Log.i("SettingsActivity", "modifySelfProfile success")
                runOnUiThread {
                    settingsAdapter.notifyItemChanged(0)
                    settingsAdapter.notifyItemChanged(1)
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CHOOSE_AVATAR_REQUEST_CODE && resultCode == ImageSelectActivity.RESULT_CODE_SUCCESS) {
            if (data != null) {
                val imageBean =
                    data.getSerializableExtra(ImageSelectActivity.DATA) as? ImageSelectActivity.ImageBean
                if (imageBean != null) {
                    faceUrl = imageBean.imageUri
                    settingsAdapter.notifyItemChanged(0)
                    updateProfile()
                }
            }
        }
    }

    companion object {
        const val AVATAR_FACE_URL = "https://im.sdk.qcloud.com/download/tuikit-resource/avatar/avatar_%s.png"
        const val AVATAR_FACE_COUNT = 26
        const val ITEM_HEIGHT: String = "itemHeight"
        const val ITEM_WIDTH: String = "itemWidth"
    }
}