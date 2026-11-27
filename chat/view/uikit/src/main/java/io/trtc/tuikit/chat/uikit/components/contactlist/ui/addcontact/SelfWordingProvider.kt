package io.trtc.tuikit.chat.uikit.components.contactlist.ui.addcontact
import android.content.Context
import io.trtc.tuikit.chat.uikit.R
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

internal object SelfWordingProvider {

    fun defaultWording(context: Context): String {
        val loginUserInfo = LoginStore.shared.loginState.loginUserInfo.value
        return defaultWording(
            nickname = loginUserInfo?.nickname,
            userID = loginUserInfo?.userID,
            formatter = { displayName ->
                context.getString(R.string.contact_list_add_wording_i_am, displayName)
            }
        )
    }

    fun defaultWording(
        nickname: String?,
        userID: String?,
        formatter: (String) -> String
    ): String {
        return formatter(displayName(nickname, userID))
    }

    private fun displayName(nickname: String?, userID: String?): String {
        return nickname?.takeIf { it.isNotEmpty() } ?: userID.orEmpty()
    }
}
