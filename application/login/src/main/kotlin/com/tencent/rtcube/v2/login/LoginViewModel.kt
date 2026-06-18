package com.tencent.rtcube.v2.login

import androidx.lifecycle.ViewModel
import com.tencent.rtcube.v2.login.debugauth.store.DebugAuthStore
import com.tencent.rtcube.v2.login.emailverify.store.EmailInviteCodeStore
import com.tencent.rtcube.v2.login.emailverify.store.EmailVerifyStore
import com.tencent.rtcube.v2.login.hiddenconfig.store.HiddenConfigStore
import com.tencent.rtcube.v2.login.invitecode.store.InviteCodeStore
import com.tencent.rtcube.v2.login.phoneverify.store.PhoneVerifyStore
import com.tencent.rtcube.v2.login.profile.store.ProfileStore

internal class LoginViewModel : ViewModel() {

    var phoneVerifyStore: PhoneVerifyStore? = null
    var emailVerifyStore: EmailVerifyStore? = null
    var emailInviteCodeStore: EmailInviteCodeStore? = null
    var inviteCodeStore: InviteCodeStore? = null
    var debugAuthStore: DebugAuthStore? = null
    var profileStore: ProfileStore? = null
    var hiddenConfigStore: HiddenConfigStore? = null

    override fun onCleared() {
        super.onCleared()
        phoneVerifyStore?.destroy()
        phoneVerifyStore = null
        emailVerifyStore?.destroy()
        emailVerifyStore = null
        emailInviteCodeStore?.destroy()
        emailInviteCodeStore = null
        inviteCodeStore?.destroy()
        inviteCodeStore = null
        debugAuthStore?.destroy()
        debugAuthStore = null
        profileStore?.destroy()
        profileStore = null
        hiddenConfigStore?.destroy()
        hiddenConfigStore = null
    }
}
