package com.example.atomicxcore.scenes.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import coil.load
import coil.transform.CircleCropTransformation
import com.example.atomicxcore.R
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.databinding.ActivityProfileSetupBinding
import com.example.atomicxcore.scenes.featurelist.FeatureListActivity
import com.example.atomicxcore.utils.completionHandler
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.ShapeAppearanceModel
import io.trtc.tuikit.atomicxcore.api.login.LoginStore
import io.trtc.tuikit.atomicxcore.api.login.UserProfile

/**
 * Business scenario: Profile setup page
 *
 * After login, if the user's nickname is empty, this page is shown
 * to guide the user to set a nickname and avatar.
 *
 * Related APIs:
 * - LoginStore.shared.setSelfInfo(userProfile, completion) - Set personal info
 * - LoginStore.shared.loginState.loginUserInfo - Get current user info
 *
 * Interaction details:
 * - The nickname input field defaults to a randomly generated English name; users can modify freely
 * - An avatar is randomly selected from 5 preset URLs by default; users can tap to switch
 * - A "Skip" button at the top-right allows skipping profile setup and going directly to the feature list
 * - Tapping "Done" submits the nickname and avatar to the server
 *
 * Corresponds to ProfileSetupViewController on iOS
 */
class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding

    // MARK: - Constants

    /** Preset avatar URL list (aligned with iOS) */
    private val avatarURLs = listOf(
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar1.png",
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar2.png",
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar3.png",
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar4.png",
        "https://liteav.sdk.qcloud.com/app/res/picture/voiceroom/avatar/user_avatar5.png",
    )

    /** Preset random nickname list (aligned with iOS) */
    private val randomNicknames = listOf(
        "Alex", "Jordan", "Taylor", "Morgan", "Casey",
        "Riley", "Avery", "Quinn", "Harper", "Skyler",
    )

    // MARK: - Properties

    /** Currently selected avatar index */
    private var selectedAvatarIndex: Int = 0

    /** Avatar button list (used for updating selection state) */
    private val avatarButtons = mutableListOf<ShapeableImageView>()

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAvatarSelector()
        setupActions()
        randomizeDefaults()
    }

    // MARK: - Setup

    private fun setupAvatarSelector() {
        val container = binding.llAvatarSelector
        container.removeAllViews()

        for ((index, url) in avatarURLs.withIndex()) {
            val size = (44 * resources.displayMetrics.density).toInt()
            val margin = (6 * resources.displayMetrics.density).toInt()

            val avatarView = ShapeableImageView(this).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setAllCornerSizes(size / 2f)
                    .build()
                strokeWidth = 2 * resources.displayMetrics.density
                strokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                )
                setBackgroundColor(getColor(android.R.color.darker_gray))
                tag = index

                // Load avatar
                load(url) {
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }

                setOnClickListener {
                    selectedAvatarIndex = it.tag as Int
                    updateSelectedAvatar()
                }
            }

            avatarButtons.add(avatarView)
            container.addView(avatarView)
        }
    }

    private fun setupActions() {
        // Skip button
        binding.btnSkip.setOnClickListener {
            navigateToFeatureList()
        }

        // Random nickname button
        binding.btnRandomNickname.setOnClickListener {
            binding.etNickname.setText(randomNicknames.random())
        }

        // Done button
        binding.btnConfirm.setOnClickListener {
            dismissKeyboard()
            onConfirmTapped()
        }

        // Tap blank area to dismiss keyboard
        binding.root.setOnClickListener {
            dismissKeyboard()
        }
    }

    /**
     * Randomly initialize default nickname and avatar.
     * Corresponds to randomizeDefaults on iOS
     */
    private fun randomizeDefaults() {
        // Random nickname
        binding.etNickname.setText(randomNicknames.random())

        // Random avatar
        selectedAvatarIndex = (0 until avatarURLs.size).random()
        updateSelectedAvatar()
    }

    // MARK: - Avatar Selection

    /**
     * Update the highlight state and large avatar preview for the selected avatar.
     * Corresponds to updateSelectedAvatar on iOS
     */
    private fun updateSelectedAvatar() {
        // Update selection highlight
        for ((index, button) in avatarButtons.withIndex()) {
            if (index == selectedAvatarIndex) {
                button.strokeColor = android.content.res.ColorStateList.valueOf(
                    getColor(R.color.primary)
                )
                button.scaleX = 1.1f
                button.scaleY = 1.1f
            } else {
                button.strokeColor = android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.TRANSPARENT
                )
                button.scaleX = 1.0f
                button.scaleY = 1.0f
            }
        }

        // Update large avatar preview
        binding.ivSelectedAvatar.load(avatarURLs[selectedAvatarIndex]) {
            crossfade(true)
            transformations(CircleCropTransformation())
        }
    }

    // MARK: - Actions

    private fun onConfirmTapped() {
        val nickname = binding.etNickname.text?.toString()?.trim() ?: ""
        if (nickname.isEmpty()) {
            Toast.makeText(this, getString(R.string.profile_error_emptyNickname), Toast.LENGTH_SHORT).show()
            return
        }

        saveSelfInfo(nickname, avatarURLs[selectedAvatarIndex])
    }

    // MARK: - Save Info

    /**
     * Save personal info (nickname + avatar).
     * Corresponds to saveSelfInfo on iOS
     */
    private fun saveSelfInfo(nickname: String, avatarURL: String) {
        setLoading(true)

        val userID = LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
        val profile = UserProfile(userID, nickname, avatarURL)

        LoginStore.shared.setSelfInfo(
            profile,
            completionHandler { code, message ->
                runOnUiThread {
                    setLoading(false)
                    if (code == 0) {
                        Toast.makeText(
                            this,
                            getString(R.string.profile_status_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Brief delay before navigation after successful save
                        binding.root.postDelayed({
                            navigateToFeatureList()
                        }, 500)
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.profile_error_saveFailed, message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    // MARK: - Navigation

    private fun navigateToFeatureList() {
        val intent = Intent(this, FeatureListActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // MARK: - UI Helpers

    private fun setLoading(loading: Boolean) {
        binding.btnConfirm.isEnabled = !loading
        binding.btnConfirm.text = if (loading) "" else getString(R.string.profile_confirm)
        binding.btnSkip.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    private fun dismissKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }
}
