package com.tencent.uikit.app.call

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tencent.uikit.app.R
import com.tencent.uikit.app.call.common.WindowInsetsHelper
import com.tencent.uikit.app.call.settings.CallSettingsView
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import kotlinx.coroutines.launch

class CallSettingsFragment : Fragment(R.layout.callaccess_settings_fragment) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        WindowInsetsHelper.applyStatusBarInset(view.findViewById(R.id.header))
        view.findViewById<ImageView>(R.id.back_icon).setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        val settingsView = view.findViewById<CallSettingsView>(R.id.settings_view)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (settingsView.dismissSheet()) return@addCallback
            isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            isEnabled = true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                LoginStore.shared.loginState.loginUserInfo.collect { profile ->
                    settingsView.updateProfile(profile)
                }
            }
        }
    }
}
