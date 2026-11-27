package com.tencent.uikit.app.mine

import android.content.Intent
import android.os.Bundle
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
    private val items: List<SettingItem>, private val onItemClick: (SettingItem) -> Unit,
    private val getNickName: () -> String?, private val getFaceUrl: () -> String?
) : RecyclerView.Adapter<SettingsVH>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_item_settings, parent, false)
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
    itemView: View, private val getNickName: () -> String?, private val getFaceUrl: () -> String?
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, SettingsFragment()).commit()
    }
}

class SettingsFragment : androidx.fragment.app.Fragment(R.layout.app_mine_settings) {
    private var nickname = ""
    private var avatarURL = ""
    private var saving = false
    private lateinit var adapter: SettingsAdapter
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val profile = LoginStore.shared.loginState.loginUserInfo.value
        nickname = profile?.nickname.orEmpty()
        avatarURL = profile?.avatarURL.orEmpty()
        view.findViewById<View>(R.id.rl_title).let { toolbar ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
                val lp = v.layoutParams as ViewGroup.MarginLayoutParams
                lp.topMargin = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                v.layoutParams = lp
                insets
            }
        }
        view.findViewById<View>(R.id.img_back_with_line).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        adapter = SettingsAdapter(
            listOf(
            SettingItem(SettingType.AVATAR, getString(R.string.app_user_avatar)),
            SettingItem(SettingType.NICKNAME, getString(R.string.app_user_nickname))
        ), { item ->
            if (!saving) when (item.type) {
                SettingType.AVATAR -> {
                    val faces = ArrayList((1..26).map { index ->
                        val url = "https://im.sdk.qcloud.com/download/tuikit-resource/avatar/avatar_$index.png"
                        ImageSelectActivity.ImageBean(url, url, false)
                    })
                    startActivityForResult(Intent(requireContext(), ImageSelectActivity::class.java).apply {
                        putExtra(ImageSelectActivity.TITLE, getString(R.string.app_choose_avatar))
                        putExtra(ImageSelectActivity.SPAN_COUNT, 4)
                        putExtra("itemWidth", (resources.displayMetrics.widthPixels * .2f).toInt())
                        putExtra("itemHeight", (resources.displayMetrics.widthPixels * .2f).toInt())
                        putExtra(ImageSelectActivity.DATA, faces)
                        putExtra(
                            ImageSelectActivity.SELECTED, ImageSelectActivity.ImageBean(avatarURL, avatarURL, false)
                        )
                    }, 1001)
                }

                SettingType.NICKNAME -> {
                    val card = PopupInputCard(requireActivity())
                    card.setContent(nickname)
                    card.setTitle(getString(R.string.app_self_detail_modify_nickname))
                    card.setDescription(getString(R.string.app_self_detail_modify_nickname_rule))
                    card.setOnPositive(PopupInputCard.OnClickListener { value ->
                        nickname = value.orEmpty()
                        save()
                    })
                    card.show(requireView(), Gravity.BOTTOM)
                }

                else -> Unit
            }
        }, { nickname }, { avatarURL })
        view.findViewById<RecyclerView>(R.id.recycler_settings).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@SettingsFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
    }

    private fun save() {
        if (saving) return
        saving = true
        val info = V2TIMUserFullInfo().apply { setNickname(nickname); faceUrl = avatarURL }
        UserManager.updateSelfUserInfo(info, object : V2TIMCallback {
            override fun onSuccess() {
                activity?.runOnUiThread {
                    saving = false
                    if (view != null) adapter.notifyDataSetChanged()
                }
            }

            override fun onError(code: Int, desc: String?) {
                activity?.runOnUiThread {
                    saving = false
                    if (view != null) Toast.makeText(
                        context, getString(R.string.app_toast_failed_to_set, desc.orEmpty()), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == ImageSelectActivity.RESULT_CODE_SUCCESS) {
            val bean = data?.getSerializableExtra(ImageSelectActivity.DATA) as? ImageSelectActivity.ImageBean ?: return
            avatarURL = bean.imageUri.orEmpty()
            save()
        }
    }
}
