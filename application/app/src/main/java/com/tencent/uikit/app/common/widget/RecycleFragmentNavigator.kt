package com.tencent.uikit.app.common.widget

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.fragment.FragmentNavigator
import java.util.ArrayDeque

/**
 * Navigation 默认加载 fragment 的模式，每次会重新加载 view，导致每次导航切换的时候会重新加载view，
 * 通过重写 FragmentNavigator 的 navigate 方法，将其中的 replace 方法 替换为 show 和 hide 方法 来完成 Fragment 的切换，
 * 在应用切换导航的时候，会回收之前的view，避免每次重复加载fragment
 */
@Navigator.Name("RecycleFragmentNavigator")
class RecycleFragmentNavigator(
    private val mContext: Context,
    private val mManager: FragmentManager,
    private val mContainerId: Int
) : FragmentNavigator(
    mContext,
    mManager,
    mContainerId
) {
    override fun navigate(
        destination: FragmentNavigator.Destination,
        args: Bundle?,
        navOptions: NavOptions?,
        navigatorExtras: Navigator.Extras?
    ): NavDestination? {
        if (mManager.isStateSaved) {
            Log.i(TAG, "Ignoring navigate() call: FragmentManager has already" + " saved its state")
            return null
        }
        var className = destination.getClassName()
        if (className.get(0) == '.') {
            className = mContext.getPackageName() + className
        }
        val ft = mManager.beginTransaction()
        var enterAnim = navOptions?.enterAnim ?: -1
        var exitAnim = navOptions?.exitAnim ?: -1
        var popEnterAnim = navOptions?.popEnterAnim ?: -1
        var popExitAnim = navOptions?.popExitAnim ?: -1
        if (enterAnim != -1 || exitAnim != -1 || popEnterAnim != -1 || popExitAnim != -1) {
            enterAnim = if (enterAnim != -1) enterAnim else 0
            exitAnim = if (exitAnim != -1) exitAnim else 0
            popEnterAnim = if (popEnterAnim != -1) popEnterAnim else 0
            popExitAnim = if (popExitAnim != -1) popExitAnim else 0
            ft.setCustomAnimations(enterAnim, exitAnim, popEnterAnim, popExitAnim)
        }

        /**
         * 1、先查询当前显示的fragment 不为空则将其hide
         * 2、根据tag查询当前添加的fragment是否不为null，不为null则将其直接show
         * 3、为null则通过instantiateFragment方法创建fragment实例
         * 4、将创建的实例添加在事务中
         */
        val fragment = mManager.primaryNavigationFragment //当前显示的fragment
        if (fragment != null) {
            ft.hide(fragment)
        }

        val tag = destination.getId().toString()
        var frag = mManager.findFragmentByTag(tag)
        if (frag != null) {
            ft.show(frag)
        } else {
            frag = instantiateFragment(mContext, mManager, className, args)
            frag.setArguments(args)
            ft.add(mContainerId, frag, tag)
        }
        ft.setPrimaryNavigationFragment(frag)

        @IdRes val destId = destination.getId()

        var mBackStack: ArrayDeque<Int?>? = null
        var isAdded = false
        try {
            val navigationClass = Class.forName("androidx.navigation.fragment.FragmentNavigator")
            val field = navigationClass.getDeclaredField("mBackStack")
            field.setAccessible(true)
            mBackStack = field.get(this) as ArrayDeque<Int?>?
            val initialNavigation = mBackStack!!.isEmpty()
            val isSingleTopReplacement =
                (navOptions != null && !initialNavigation && navOptions.shouldLaunchSingleTop()
                        && mBackStack.peekLast() == destId)

            if (initialNavigation) {
                isAdded = true
            } else if (isSingleTopReplacement) {
                if (mBackStack.size > 1) {
                    mManager.popBackStack(
                        generateBackStackName(mBackStack.size, mBackStack.peekLast()!!),
                        FragmentManager.POP_BACK_STACK_INCLUSIVE
                    )
                    ft.addToBackStack(generateBackStackName(mBackStack.size, destId))
                }
            } else {
                ft.addToBackStack(generateBackStackName(mBackStack.size + 1, destId))
            }
            if (navigatorExtras is Extras) {
                val extras = navigatorExtras
                for (sharedElement in extras.getSharedElements().entries) {
                    ft.addSharedElement(sharedElement.key, sharedElement.value)
                }
            }
            ft.setReorderingAllowed(true)
            ft.commit()
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "addToBackStack error, message is: " + e.message)
            e.printStackTrace()
        } catch (e: NoSuchFieldException) {
            Log.e(TAG, "addToBackStack error, message is: " + e.message)
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            Log.e(TAG, "addToBackStack error, message is: " + e.message)
            e.printStackTrace()
        }

        if (mBackStack != null && isAdded) {
            mBackStack.add(destId)
            return destination
        } else {
            return null
        }
    }

    private fun generateBackStackName(backIndex: Int, destId: Int): String {
        return "$backIndex - $destId"
    }

    companion object {
        private const val TAG = "FixFragmentNavigator"
    }
}