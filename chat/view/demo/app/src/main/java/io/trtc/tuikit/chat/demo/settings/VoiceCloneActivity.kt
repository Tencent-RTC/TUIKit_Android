package io.trtc.tuikit.chat.demo.settings

import io.trtc.tuikit.chat.demo.common.BaseActivity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.trtc.tuikit.atomicx.common.permission.PermissionCallback
import io.trtc.tuikit.atomicx.theme.ThemeStore
import io.trtc.tuikit.atomicx.theme.tokens.ColorTokens
import io.trtc.tuikit.chat.app.R
import io.trtc.tuikit.chat.uikit.components.ai.AiMediaProcessManager
import io.trtc.tuikit.chat.uikit.components.ai.tts.VoiceMessageConfig
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorder
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderListener
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResult
import io.trtc.tuikit.chat.uikit.components.audiorecorder.AudioRecorderResultCode
import io.trtc.tuikit.chat.uikit.components.common.ChatPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceCloneActivity : BaseActivity() {

    private val themeStore by lazy { ThemeStore.shared(this) }
    private var activityScope: CoroutineScope? = null

    private lateinit var rootContainer: LinearLayout
    private lateinit var headerContainer: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var headerDivider: View
    private lateinit var badgeContainer: View
    private lateinit var leftContainer: LinearLayout
    private lateinit var scrollView: ScrollView

    private lateinit var tvTip: TextView
    private lateinit var tvReadingTitle: TextView
    private lateinit var tvSample: TextView
    private lateinit var waveContainer: FrameLayout
    private lateinit var waveformView: VoiceWaveformView
    private lateinit var tvTimer: TextView
    private lateinit var recordBtn: FrameLayout
    private lateinit var recordIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvAuthTip: TextView
    private lateinit var etName: EditText
    private lateinit var btnSubmit: FrameLayout
    private lateinit var tvSubmit: TextView
    private lateinit var submitProgress: ProgressBar

    private var recording = false
    private var stopping = false
    private var submitting = false
    private var recordedPath: String? = null
    private var successDialog: AlertDialog? = null
    private var keyboardBottomInset = 0
    private var systemBottomInset = 0

    private lateinit var colors: ColorTokens

    companion object {
        private const val MIN_CLONE_MS = 3000
        private const val MAX_CLONE_MS = 30000
        private const val SUCCESS_DIALOG_AUTO_DISMISS_MS = 3000L

        fun start(context: Context) {
            context.startActivity(Intent(context, VoiceCloneActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isFinishing) {
            return
        }
        configureSoftInputMode()
        setContentView(R.layout.demo_activity_voice_clone)

        colors = themeStore.themeState.value.currentTheme.tokens.color

        rootContainer = findViewById(R.id.demo_voiceCloneRoot)
        headerContainer = findViewById(R.id.demo_chatHeaderContainer)
        tvTitle = findViewById(R.id.demo_tvChatTitle)
        btnBack = findViewById(R.id.demo_btnBack)
        btnMore = findViewById(R.id.demo_btnMore)
        headerDivider = findViewById(R.id.demo_headerDivider)
        badgeContainer = findViewById(R.id.demo_badgeContainer)
        leftContainer = findViewById(R.id.demo_leftContainer)
        scrollView = findViewById(R.id.demo_voiceCloneScroll)

        tvTip = findViewById(R.id.demo_tvVoiceCloneTip)
        tvReadingTitle = findViewById(R.id.demo_tvVoiceCloneReadingTitle)
        tvSample = findViewById(R.id.demo_tvVoiceCloneSample)
        waveContainer = findViewById(R.id.demo_voiceCloneWaveContainer)
        tvTimer = findViewById(R.id.demo_tvVoiceCloneTimer)
        recordBtn = findViewById(R.id.demo_voiceCloneRecordBtn)
        recordIcon = findViewById(R.id.demo_ivVoiceCloneRecordIcon)
        tvStatus = findViewById(R.id.demo_tvVoiceCloneStatus)
        tvAuthTip = findViewById(R.id.demo_tvVoiceCloneAuthTip)
        etName = findViewById(R.id.demo_etVoiceCloneName)
        btnSubmit = findViewById(R.id.demo_btnVoiceCloneSubmit)
        tvSubmit = findViewById(R.id.demo_tvVoiceCloneSubmit)
        submitProgress = findViewById(R.id.demo_progressVoiceCloneSubmit)

        waveformView = VoiceWaveformView(this)
        waveContainer.addView(
            waveformView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            keyboardBottomInset = ime.bottom
            systemBottomInset = systemBars.bottom
            view.updatePadding(top = systemBars.top)
            scrollView.updatePadding(bottom = maxOf(systemBars.bottom, ime.bottom))
            if (ime.bottom > systemBars.bottom) {
                scrollInputAboveKeyboard()
            }
            insets
        }

        leftContainer.contentDescription = btnBack.contentDescription
        leftContainer.setOnClickListener { finish() }
        btnMore.visibility = View.GONE
        badgeContainer.visibility = View.GONE
        tvTitle.text = getString(R.string.demo_voice_clone)

        tvTip.text = getString(R.string.demo_voice_clone_tip)
        tvReadingTitle.text = getString(R.string.demo_voice_clone_reading_title)
        tvSample.text = "\u201C${getString(R.string.demo_voice_clone_sample)}\u201D"
        tvAuthTip.text = getString(R.string.demo_voice_clone_auth_tip)
        etName.hint = getString(R.string.demo_voice_clone_name_hint)
        tvSubmit.text = getString(R.string.demo_voice_clone_submit)
        updateTimer(0)
        tvStatus.text = getString(R.string.demo_voice_clone_start)

        etName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) scrollInputAboveKeyboard()
        }
        recordBtn.setOnClickListener { toggleRecord() }
        btnSubmit.setOnClickListener { submit() }

        applyColors(colors)
        updateSubmitEnabled()
    }

    @Suppress("DEPRECATION")
    private fun configureSoftInputMode() {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun scrollInputAboveKeyboard() {
        if (keyboardBottomInset <= systemBottomInset) return
        scrollView.post {
            val visibleHeight = scrollView.height - scrollView.paddingTop - scrollView.paddingBottom
            if (visibleHeight <= 0) return@post
            val margin = (16f * resources.displayMetrics.density).toInt()
            val targetBottom = maxOf(etName.bottom, btnSubmit.bottom)
            val scrollY = (targetBottom - visibleHeight + margin).coerceAtLeast(0)
            scrollView.smoothScrollTo(0, scrollY)
        }
    }

    override fun onStart() {
        super.onStart()
        activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        activityScope?.launch {
            themeStore.themeState.collectLatest { state ->
                colors = state.currentTheme.tokens.color
                applyColors(colors)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        activityScope?.cancel()
        activityScope = null
        if (AudioRecorder.isRecording()) {
            AudioRecorder.cancelRecord()
        }
        waveformView.setActive(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        successDialog?.setOnDismissListener(null)
        successDialog?.dismiss()
        successDialog = null
        if (AudioRecorder.isRecording()) {
            AudioRecorder.cancelRecord()
        }
        waveformView.setActive(false)
    }

    private fun toggleRecord() {
        if (submitting || stopping) return
        if (recording) {
            stopping = true
            updateRecordButton()
            AudioRecorder.stopRecord()
            return
        }
        if (hasRecordAudioPermission()) {
            startRecording()
        } else {
            requestRecordAudioPermission()
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestRecordAudioPermission() {
        ChatPermissionHelper.requestPermission(
            ChatPermissionHelper.PERMISSION_MICROPHONE,
            object : PermissionCallback() {
                override fun onGranted() {
                    if (isFinishing || isDestroyed) return
                    tvStatus.text = getString(R.string.demo_voice_clone_start)
                }

                override fun onDenied() {
                    if (isFinishing || isDestroyed) return
                    Toast.makeText(
                        this@VoiceCloneActivity,
                        R.string.demo_voice_clone_permission_denied,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        )
    }

    private fun startRecording() {
        if (isFinishing || isDestroyed) return
        if (submitting || stopping || recording) return
        recordedPath = null
        updateSubmitEnabled()
        // Microphone permission (with the compliance rationale) is requested in toggleRecord via
        // ChatPermissionHelper before reaching here, so AudioRecorder.startRecord's own permission
        // check resolves immediately. The recording UI is driven by the listener callbacks to stay
        // consistent with the real recorder state.
        AudioRecorder.startRecord(
            minDurationMs = MIN_CLONE_MS,
            maxDurationMs = MAX_CLONE_MS,
            listener = object : AudioRecorderListener {
                override fun onStart() {
                    onRecordStarted()
                }

                override fun onProgress(durationMs: Int, powerLevel: Int) {
                    if (recording) {
                        updateTimer(durationMs)
                        waveformView.setPowerLevel(powerLevel)
                    }
                }

                override fun onCompleted(result: AudioRecorderResult) {
                    onRecordCompleted(result)
                }
            }
        )
    }

    private fun onRecordStarted() {
        if (isFinishing || isDestroyed) return
        recording = true
        stopping = false
        recordedPath = null
        updateTimer(0)
        waveformView.setActive(true)
        tvStatus.text = getString(R.string.demo_voice_clone_stop)
        updateRecordButton()
        updateSubmitEnabled()
    }

    private fun onRecordCompleted(result: AudioRecorderResult) {
        if (isFinishing || isDestroyed) return
        recording = false
        stopping = false
        waveformView.setActive(false)
        updateRecordButton()
        when (result.resultCode) {
            AudioRecorderResultCode.ERROR_RECORD_PERMISSION_DENIED -> {
                recordedPath = null
                updateTimer(0)
                tvStatus.text = getString(R.string.demo_voice_clone_start)
                Toast.makeText(this, R.string.demo_voice_clone_permission_denied, Toast.LENGTH_SHORT).show()
            }

            AudioRecorderResultCode.ERROR_CANCEL -> {
                recordedPath = null
                updateTimer(0)
                tvStatus.text = getString(R.string.demo_voice_clone_start)
            }

            AudioRecorderResultCode.ERROR_RECORDING,
            AudioRecorderResultCode.ERROR_RECORD_INNER_FAIL,
            AudioRecorderResultCode.ERROR_STORAGE_UNAVAILABLE -> {
                recordedPath = null
                updateTimer(0)
                tvStatus.text = getString(R.string.demo_voice_clone_start)
                Toast.makeText(this, R.string.demo_voice_clone_record_failed, Toast.LENGTH_SHORT).show()
            }

            else -> {
                if (result.isSuccess && result.durationMs >= MIN_CLONE_MS &&
                    !result.filePath.isNullOrEmpty()
                ) {
                    recordedPath = result.filePath
                    updateTimer(result.durationMs)
                    tvStatus.text = getString(R.string.demo_voice_clone_done)
                } else {
                    recordedPath = null
                    updateTimer(0)
                    tvStatus.text = getString(R.string.demo_voice_clone_start)
                    showTooShortDialog()
                }
            }
        }
        updateSubmitEnabled()
    }

    private fun submit() {
        val path = recordedPath
        if (path.isNullOrEmpty()) {
            Toast.makeText(this, R.string.demo_voice_clone_empty_record, Toast.LENGTH_SHORT).show()
            return
        }
        if (submitting) return
        val inputName = etName.text?.toString()?.trim().orEmpty()
        val voiceName = inputName.ifEmpty { getString(R.string.demo_voice_clone_default_name) }
        submitting = true
        updateSubmitEnabled()
        AiMediaProcessManager.voiceClone(
            filePath = path,
            voiceName = voiceName,
            onSuccess = { voiceId ->
                if (isFinishing || isDestroyed) return@voiceClone
                submitting = false
                updateSubmitEnabled()
                VoiceMessageConfig.setSelectedVoice(this, voiceId, voiceName)
                showSuccessDialog()
            },
            onFailure = { _, _ ->
                if (isFinishing || isDestroyed) return@voiceClone
                submitting = false
                updateSubmitEnabled()
                Toast.makeText(this, R.string.demo_voice_clone_failed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showTooShortDialog() {
        AlertDialog.Builder(this, dialogThemeRes())
            .setTitle(getString(R.string.demo_voice_clone_too_short_title))
            .setMessage(getString(R.string.demo_voice_clone_too_short_message))
            .setPositiveButton(getString(R.string.demo_about_confirm)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showSuccessDialog() {
        successDialog?.dismiss()
        successDialog = AlertDialog.Builder(this, dialogThemeRes())
            .setTitle(getString(R.string.demo_voice_clone_success_title))
            .setMessage(getString(R.string.demo_voice_clone_success_message))
            .setCancelable(true)
            .setPositiveButton(getString(R.string.demo_about_confirm)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .apply {
                setCanceledOnTouchOutside(true)
                setOnDismissListener {
                    successDialog = null
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                }
                show()
                window?.decorView?.postDelayed({
                    if (isShowing) {
                        dismiss()
                    }
                }, SUCCESS_DIALOG_AUTO_DISMISS_MS)
            }
    }

    private fun dialogThemeRes(): Int {
        return if (isColorLight(colors.bgColorOperate)) {
            androidx.appcompat.R.style.Theme_AppCompat_Light_Dialog_Alert
        } else {
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        }
    }

    private fun updateTimer(durationMs: Int) {
        val totalSec = durationMs / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        tvTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun updateSubmitEnabled() {
        val canSubmit = recordedPath != null && !submitting && !recording
        btnSubmit.isEnabled = canSubmit
        val fillColor = if (canSubmit) colors.buttonColorPrimaryDefault else colors.buttonColorPrimaryDisabled
        val bg = GradientDrawable().apply {
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(fillColor)
        }
        btnSubmit.background = bg
        tvSubmit.setTextColor(colors.textColorButton)
        tvSubmit.visibility = if (submitting) View.GONE else View.VISIBLE
        submitProgress.visibility = if (submitting) View.VISIBLE else View.GONE
        submitProgress.indeterminateTintList = ColorStateList.valueOf(colors.textColorButton)
    }

    private fun updateRecordButton() {
        val fillColor = if (recording) colors.textColorError else colors.buttonColorPrimaryDefault
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fillColor)
        }
        recordBtn.background = bg
        recordIcon.setImageResource(
            if (recording) R.drawable.demo_ic_voice_stop else R.drawable.demo_ic_voice_mic
        )
        recordIcon.imageTintList = ColorStateList.valueOf(colors.textColorButton)
    }

    private fun applyColors(colors: ColorTokens) {
        rootContainer.setBackgroundColor(colors.bgColorOperate)
        scrollView.setBackgroundColor(colors.bgColorOperate)
        headerContainer.setBackgroundColor(colors.bgColorOperate)
        tvTitle.setTextColor(colors.textColorPrimary)
        btnBack.imageTintList = ColorStateList.valueOf(colors.textColorSecondary)
        headerDivider.setBackgroundColor(colors.strokeColorPrimary)

        tvTip.setTextColor(colors.textColorSecondary)
        tvReadingTitle.setTextColor(colors.textColorPrimary)
        tvSample.setTextColor(colors.textColorPrimary)
        tvTimer.setTextColor(colors.textColorPrimary)
        tvStatus.setTextColor(colors.textColorSecondary)
        tvAuthTip.setTextColor(colors.textColorTertiary)

        waveformView.setBarColor(colors.buttonColorPrimaryDefault)
        waveContainer.background = GradientDrawable().apply {
            cornerRadius = 12f * resources.displayMetrics.density
            setColor(colors.bgColorInput)
        }

        etName.setTextColor(colors.textColorPrimary)
        etName.setHintTextColor(colors.textColorTertiary)
        val inputBg = GradientDrawable().apply {
            cornerRadius = 8f * resources.displayMetrics.density
            setColor(colors.bgColorInput)
        }
        etName.background = inputBg

        updateRecordButton()
        updateSubmitEnabled()
    }
}
