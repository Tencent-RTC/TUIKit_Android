package com.tencent.uikit.app.home

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.tencent.uikit.app.R
import com.tencent.uikit.app.main.MainActivity
import com.tencent.uikit.app.mine.MineFragment

class HomeBranchFragment : Fragment(R.layout.app_home_branch) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.addOnBackStackChangedListener { updateBars() }
        if (savedInstanceState == null) {
            val tab = requireArguments().getInt("tab")
            val root = when (tab) {
                0 -> com.tencent.uikit.app.room.RoomHomeFragment()
                1 -> com.tencent.uikit.app.live.home.LiveHomeFragment()
                2 -> com.tencent.uikit.app.call.CallEntryFragment()
                3 -> com.tencent.uikit.app.chat.HomeMessagesFragment()
                else -> MineFragment()
            }
            childFragmentManager.beginTransaction().replace(R.id.home_branch_content, root).commitNow()
        }
    }

    fun push(page: Fragment, fullScreen: Boolean) {
        page.arguments = (page.arguments ?: Bundle()).apply { putBoolean("fullScreen", fullScreen) }
        val previous = childFragmentManager.findFragmentById(R.id.home_branch_content)
        childFragmentManager.beginTransaction().apply {
            if (page is com.tencent.uikit.app.live.home.LiveHomePlayerFragment && previous is com.tencent.uikit.app.live.home.LiveHomeFragment) {
                // The exit fade must reveal the mounted list, not the activity's empty container.
                setMaxLifecycle(previous, androidx.lifecycle.Lifecycle.State.STARTED)
                add(R.id.home_branch_content, page)
            } else replace(R.id.home_branch_content, page)
            addToBackStack(null)
        }.commit()
    }

    fun reset() {
        childFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    fun pop(): Boolean {
        if (childFragmentManager.backStackEntryCount == 0) return false
        childFragmentManager.popBackStackImmediate()
        return true
    }

    override fun onResume() {
        super.onResume()
        updateBars()
    }

    private fun updateBars() {
        if (!isResumed) return
        val page = childFragmentManager.findFragmentById(R.id.home_branch_content)
        childFragmentManager.fragments.forEach { child ->
            child.view?.importantForAccessibility = if (child === page) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        (activity as? MainActivity)?.setHomeImmersive(page?.arguments?.getBoolean("fullScreen") == true)
    }
}
