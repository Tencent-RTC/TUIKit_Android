package com.tencent.uikit.app.login

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.tencent.imsdk.v2.V2TIMCallback
import com.tencent.imsdk.v2.V2TIMUserFullInfo
import com.tencent.uikit.app.R
import com.tencent.uikit.app.common.utils.GlideEngine
import com.tencent.uikit.app.common.widget.RoundCornerImageView
import com.tencent.uikit.app.main.BaseActivity
import com.tencent.uikit.app.main.MainActivity
import com.tencent.uikit.app.mine.UserManager
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import java.util.Random
import java.util.regex.Pattern

class RegisterActivity : BaseActivity() {
    companion object {
        private const val TAG = "RegisterActivity"

        private val USER_AVATAR_ARRAY = arrayOf(
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar1.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar2.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar3.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar4.png",
            "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar5.png"
        )

        private val CUSTOM_NAME_ARRAY = arrayOf(
            R.string.app_custom_name_1,
            R.string.app_custom_name_2,
            R.string.app_custom_name_3,
            R.string.app_custom_name_4,
            R.string.app_custom_name_5
        )
    }

    private lateinit var imageAvatar: RoundCornerImageView
    private lateinit var editUserName: EditText
    private lateinit var buttonRegister: Button
    private lateinit var tvInputTips: TextView
    private var avatarUrl: String = ""
    private val random = Random()

    private fun startMainActivity() {
        val intent = Intent(this@RegisterActivity, MainActivity::class.java)
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_activity_login_profile)
        initView()
    }

    private fun initView() {
        imageAvatar = findViewById(R.id.iv_user_avatar)
        editUserName = findViewById(R.id.et_user_name)
        buttonRegister = findViewById(R.id.btn_register)
        tvInputTips = findViewById(R.id.tv_tips_user_name)

        val index = random.nextInt(USER_AVATAR_ARRAY.size)
        avatarUrl = USER_AVATAR_ARRAY[index]
        val avatarUrl = LoginStore.shared.loginState.loginUserInfo.value?.avatarURL
        GlideEngine.loadUserIcon(imageAvatar, avatarUrl)

        buttonRegister.setOnClickListener {
            setProfile()
        }

        val customNameIndex = random.nextInt(CUSTOM_NAME_ARRAY.size)
        editUserName.setText(getString(CUSTOM_NAME_ARRAY[customNameIndex]))
        val text = editUserName.text.toString()
        if (text.isNotEmpty()) {
            editUserName.setSelection(text.length)
        }

        editUserName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                buttonRegister.isEnabled = !s.isNullOrEmpty()
                val editable = editUserName.text.toString()
                val pattern = Pattern.compile("^[a-z0-9A-Z\\u4e00-\\u9fa5\\_]{2,20}$")
                val matcher = pattern.matcher(editable)
                if (!matcher.matches()) {
                    tvInputTips.setTextColor(
                        ContextCompat.getColor(
                            this@RegisterActivity,
                            R.color.app_color_input_no_match
                        )
                    )
                } else {
                    tvInputTips.setTextColor(
                        ContextCompat.getColor(
                            this@RegisterActivity,
                            R.color.app_text_color_hint
                        )
                    )
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setProfile() {
        val userName = editUserName.text.toString().trim()
        if (userName.isEmpty()) {
            Toast.makeText(this, getString(R.string.app_hint_user_name), Toast.LENGTH_SHORT).show()
            return
        }
        val reg = "^[a-z0-9A-Z\\u4e00-\\u9fa5\\_]{2,20}$"
        if (!userName.matches(reg.toRegex())) {
            tvInputTips.setTextColor(
                ContextCompat.getColor(
                    this,
                    R.color.app_color_input_no_match
                )
            )
            return
        }
        tvInputTips.setTextColor(ContextCompat.getColor(this, R.color.app_text_color_hint))
        val v2TIMUserFullInfo = V2TIMUserFullInfo()
        v2TIMUserFullInfo.setFaceUrl(avatarUrl)
        v2TIMUserFullInfo.setNickname(userName)
        UserManager.getInstance().updateSelfUserInfo(v2TIMUserFullInfo, object : V2TIMCallback {
            override fun onError(code: Int, desc: String?) {
                Log.e(TAG, "set profile failed errorCode : $code errorMsg : $desc")
                Toast.makeText(this@RegisterActivity, getString(R.string.app_toast_failed_to_set, desc), Toast.LENGTH_SHORT).show()
                startMainActivity()
                finish()
            }

            override fun onSuccess() {
                Log.i(TAG, "set profile success.")
                Toast.makeText(this@RegisterActivity, getString(R.string.app_toast_register_success_and_logging_in), Toast.LENGTH_SHORT).show()
                startMainActivity()
                finish()
            }
        })
    }
}