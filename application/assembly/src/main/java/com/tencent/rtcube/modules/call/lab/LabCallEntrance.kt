package com.tencent.rtcube.modules.call.lab

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tencent.rtcube.modules.call.lab.ui.LabCallScreen
import com.tencent.rtcube.modules.call.lab.ui.LabSettingDetailScreen
import com.tencent.rtcube.modules.call.lab.ui.LabSettingsScreen

private object LabCallRoute {
    const val CALL_MAIN = "call"
    const val SETTINGS = "settings"
    const val SETTING_DETAIL = "setting_detail/{itemType}"

    fun settingDetail(itemType: String) = "setting_detail/$itemType"
}

@Composable
fun LabCallEntrance(onBack: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = LabCallRoute.CALL_MAIN,
    ) {
        composable(LabCallRoute.CALL_MAIN) {
            LabCallScreen(
                onNavigateToSettings = { navController.navigate(LabCallRoute.SETTINGS) },
                onBack = onBack,
            )
        }

        composable(LabCallRoute.SETTINGS) {
            LabSettingsScreen(
                onNavigateToDetail = { itemType ->
                    navController.navigate(LabCallRoute.settingDetail(itemType))
                },
                onBack = { navController.popBackStack() },
                onShowToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            )
        }

        composable(
            route = LabCallRoute.SETTING_DETAIL,
            arguments = listOf(navArgument("itemType") { type = NavType.StringType }),
        ) { backStackEntry ->
            val itemType = backStackEntry.arguments?.getString("itemType") ?: LabSettingDetailScreen.ITEM_USER_DATA
            LabSettingDetailScreen(
                itemType = itemType,
                onBack = { navController.popBackStack() },
                onShowToast = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() },
            )
        }
    }
}
