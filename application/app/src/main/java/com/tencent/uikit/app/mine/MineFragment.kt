package com.tencent.uikit.app.mine

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.tencent.mmkv.MMKV
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.FileUtil
import com.tencent.uikit.app.common.utils.GlideEngine
import com.tencent.uikit.app.common.widget.ConfirmDialogFragment
import com.tencent.uikit.app.common.widget.RoundCornerImageView
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MineFragment : Fragment() {
    private var imageAvatar: RoundCornerImageView? = null
    private var textNickName: TextView? = null
    private var textUserId: TextView? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView: View = inflater.inflate(R.layout.app_fragment_my_info, container, false)
        imageAvatar = rootView.findViewById<View?>(R.id.iv_avatar) as? RoundCornerImageView?
        textNickName = rootView.findViewById<View?>(R.id.tv_show_name) as? TextView
        textUserId = rootView.findViewById<View?>(R.id.tv_userid) as? TextView
        val avatarUrl = LoginStore.shared.loginState.loginUserInfo.value?.avatarURL
        GlideEngine.loadUserIcon(imageAvatar!!, avatarUrl)
        textNickName?.text = LoginStore.shared.loginState.loginUserInfo.value?.nickname ?: ""
        textUserId?.text = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        rootView.findViewById<LinearLayout>(R.id.ll_settings).setOnClickListener {
            val intent = Intent(requireContext(), SettingsActivity::class.java)
            startActivity(intent)
        }
        rootView.findViewById<LinearLayout>(R.id.ll_log).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val logFile = FileUtil.getLogFile(requireActivity())
                if (logFile != null) {
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent(Intent.ACTION_SEND)
                        shareIntent.setType("application/zip")
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "com.trtc.uikit.livekit.example.app_uikit",
                            logFile
                        )
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.app_title_share_log)))
                    }
                }
            }
        }
        rootView.findViewById<TextView>(R.id.tv_logout).setOnClickListener {
            showLogoutDialog()
        }
        rootView.findViewById<ImageView>(R.id.img_back_with_line).setOnClickListener {
            val navController = Navigation.findNavController(it)
            val handled = navController.popBackStack()
            if (!handled) {
                navController.popBackStack(R.id.main_fragment, false)
            }
        }
        return rootView
    }

    override fun onResume() {
        super.onResume()
        val avatarUrl = LoginStore.shared.loginState.loginUserInfo.value?.avatarURL
        GlideEngine.loadUserIcon(imageAvatar!!, avatarUrl)
        textNickName?.text = LoginStore.shared.loginState.loginUserInfo.value?.nickname ?: ""
        textUserId?.text = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
    }

    private fun showLogoutDialog() {
        val logoutDialog = ConfirmDialogFragment()
        logoutDialog.setMessage(requireContext().getString(R.string.app_dialog_log_out))
        logoutDialog.setNegativeClickListener(object : ConfirmDialogFragment.NegativeClickListener {
            override fun onClick() {
                logoutDialog.dismiss()
            }
        })
        logoutDialog.setPositiveClickListener(object : ConfirmDialogFragment.PositiveClickListener {
            override fun onClick() {
                logoutDialog.dismiss()
                MMKV.defaultMMKV().putString("userId", "")
                LoginStore.shared.logout(null)
            }
        })
        parentFragmentManager.let {
            logoutDialog.show(it, "confirm_fragment")
        }
    }


    companion object {
        const val TAG = "MineFragment"
    }
}