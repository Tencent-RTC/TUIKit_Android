package com.tencent.rtcube.modules.call.online

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.ConfigurationCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tencent.imsdk.v2.V2TIMManager
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.imsdk.v2.V2TIMValueCallback
import com.tencent.qcloud.tuicore.TUILogin
import com.tencent.qcloud.tuikit.tuicallkit.TUICallKit
import com.tencent.rtcube.modules.R
import com.tencent.rtcube.modules.call.online.guide.GuideHomeModel
import com.tencent.rtcube.modules.call.online.guide.GuideHomeScreen
import com.tencent.rtcube.modules.call.online.model.CallBotType
import com.tencent.rtcube.modules.call.online.model.CallingRobotModel
import com.tencent.rtcube.modules.call.online.ui.CallingBotHesitationScreen
import com.tencent.rtcube.modules.call.online.ui.CallingContactScreen
import com.tencent.rtcube.modules.call.online.ui.CallingEntranceMenuScreen
import com.tencent.rtcube.modules.call.online.ui.CallingUserModel
import io.trtc.tuikit.atomicxcore.api.CompletionHandler
import io.trtc.tuikit.atomicxcore.api.call.CallMediaType
import io.trtc.tuikit.atomicxcore.api.call.CallParams
import org.json.JSONObject

private object CallRoute {
    const val ENTRANCE = "entrance"
    const val BOT_WAIT = "bot_wait/{callType}"
    const val CONTACT = "contact"
    const val GUIDE = "guide"

    fun botWait(callType: CallBotType) = "bot_wait/${callType.name}"
}

private fun showToast(context: Context, msg: String) {
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}

private fun startVideoCall(context: Context, userId: String, onFailure: (String) -> Unit) {
    val lang = ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language ?: "zh"
    val params = CallParams().apply {
        userData = JSONObject().put("lang", lang).toString()
    }
    TUICallKit.createInstance(context).calls(
        listOf(userId), CallMediaType.Video, params,
        object : CompletionHandler {
            override fun onSuccess() {}
            override fun onFailure(code: Int, desc: String) = onFailure(desc)
        },
    )
}

@Composable
fun CallEntrance(onFinish: () -> Unit) {
    val navController = rememberNavController()
    val currentUserId = remember { TUILogin.getUserId() ?: "" }
    var isRobotExpanded by remember { mutableStateOf(false) }
    var selectedRobotType by remember { mutableStateOf(CallBotType.INIT_CALL) }

    NavHost(
        navController = navController,
        startDestination = CallRoute.ENTRANCE,
    ) {
        composable(CallRoute.ENTRANCE) {
            CallingEntranceMenuScreen(
                isRobotExpanded = isRobotExpanded,
                onRobotExpandedChange = { isRobotExpanded = it },
                onNavigateToBotHesitation = { robot ->
                    selectedRobotType = robot.callType
                    navController.navigate(CallRoute.botWait(robot.callType))
                },
                onNavigateToContact = { navController.navigate(CallRoute.CONTACT) },
                onBack = onFinish,
            )
        }

        composable(
            route = CallRoute.BOT_WAIT,
            arguments = listOf(navArgument("callType") { type = NavType.StringType }),
        ) { backStackEntry ->
            val context = navController.context
            val callType = runCatching {
                CallBotType.valueOf(backStackEntry.arguments?.getString("callType") ?: "")
            }.getOrDefault(selectedRobotType)

            CallingBotHesitationScreen(
                robot = CallingRobotModel(
                    title = if (callType == CallBotType.INIT_CALL) "Robot A" else "Robot B",
                    callType = callType,
                ),
                onRequestBot = { botId ->
                    startVideoCall(context, botId) { desc ->
                        showToast(context, context.getString(R.string.assembly_call_error_call_failed, desc))
                    }
                },
                onBotBusy = { showToast(context, context.getString(R.string.assembly_call_bot_busy)) },
                onFailed = { msg -> showToast(context, context.getString(R.string.assembly_call_error_call_failed, msg)) },
                onWaitingFailed = { msg ->
                    showToast(context, context.getString(R.string.assembly_call_error_wait_failed, msg))
                },
                onShowToast = { msg -> showToast(context, msg) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(CallRoute.CONTACT) {
            val context = navController.context
            CallingContactScreen(
                currentUserId = currentUserId,
                onSearchUser = { userId, onResult ->
                    V2TIMManager.getInstance().getUsersInfo(
                        listOf(userId),
                        object : V2TIMValueCallback<List<V2TIMUserFullInfo>> {
                            override fun onSuccess(infoList: List<V2TIMUserFullInfo>?) {
                                val info = infoList?.firstOrNull()
                                if (info != null) {
                                    val model = CallingUserModel(
                                        userId = info.userID ?: userId,
                                        name = info.nickName ?: "",
                                        avatar = info.faceUrl ?: "",
                                    )
                                    onResult(listOf(model))
                                } else {
                                    onResult(emptyList())
                                }
                            }

                            override fun onError(code: Int, msg: String?) {
                                onResult(emptyList())
                                val toast = if (code == 206) {
                                    context.getString(R.string.assembly_call_user_not_exist)
                                } else {
                                    context.getString(R.string.assembly_call_error_search_failed, msg ?: "")
                                }
                                showToast(context, toast)
                            }
                        },
                    )
                },
                onStartCall = { userId ->
                    startVideoCall(context, userId) { desc ->
                        showToast(context, context.getString(R.string.assembly_call_error_call_failed, desc))
                    }
                },
                onShowToast = { msg -> showToast(context, msg) },
                onBack = { navController.popBackStack() },
                onNavigateToGuide = { navController.navigate(CallRoute.GUIDE) },
            )
        }

        composable(CallRoute.GUIDE) {
            GuideHomeScreen(
                homeModel = GuideHomeModel(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
