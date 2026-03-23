package com.example.atomicxcore.scenes.featurelist

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.atomicxcore.R
import com.example.atomicxcore.components.LocalizedManager
import com.example.atomicxcore.components.Role
import com.example.atomicxcore.databinding.ActivityFeatureListBinding
import com.example.atomicxcore.databinding.DialogLiveIdInputBinding
import com.example.atomicxcore.databinding.ItemFeatureCardBinding
import com.example.atomicxcore.scenes.basicstreaming.BasicStreamingActivity
import com.example.atomicxcore.scenes.interactive.InteractiveActivity
import com.example.atomicxcore.scenes.livepk.LivePKActivity
import com.example.atomicxcore.scenes.multiconnect.MultiConnectActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.atomicxcore.scenes.login.LoginActivity
import io.trtc.tuikit.atomicxcore.api.login.LoginStatus
import io.trtc.tuikit.atomicxcore.api.login.LoginStore

/**
 * Business scenario: Feature list page
 *
 * Displays four progressive stage entry cards:
 * 1. BasicStreaming - Basic streaming
 * 2. Interactive - Real-time interaction
 * 3. CoGuest - Audience co-hosting
 * 4. LivePK - Live PK battle
 */
class FeatureListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeatureListBinding

    /** Feature stage enum */
    enum class FeatureStage {
        BASIC_STREAMING,
        INTERACTIVE,
        CO_GUEST,
        LIVE_PK
    }

    /** Feature item data */
    data class FeatureItem(
        val titleResId: Int,
        val descResId: Int,
        val colorResId: Int,
        val stage: FeatureStage
    )

    private val features = listOf(
        FeatureItem(
            R.string.stage_basicStreaming,
            R.string.stage_basicStreaming_desc,
            R.color.stage_blue,
            FeatureStage.BASIC_STREAMING
        ),
        FeatureItem(
            R.string.stage_interactive,
            R.string.stage_interactive_desc,
            R.color.stage_green,
            FeatureStage.INTERACTIVE
        ),
        FeatureItem(
            R.string.stage_coGuest,
            R.string.stage_coGuest_desc,
            R.color.stage_orange,
            FeatureStage.CO_GUEST
        ),
        FeatureItem(
            R.string.stage_livePK,
            R.string.stage_livePK_desc,
            R.color.stage_red,
            FeatureStage.LIVE_PK
        )
    )

    // MARK: - Lifecycle

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalizedManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Crash recovery protection: SDK login state is lost after process kill; redirect to login flow
        if (LoginStore.shared.loginState.loginStatus.value != LoginStatus.LOGINED) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityFeatureListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
    }

    // MARK: - Setup

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.featureList_title)
        binding.toolbar.inflateMenu(R.menu.menu_feature_list)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_language) {
                LocalizedManager.showLanguageSwitchDialog(this)
                true
            } else false
        }
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = FeatureAdapter(features) { stage ->
            navigateToStage(stage)
        }
    }

    // MARK: - Navigation

    /** Role selection ActionSheet */
    private fun navigateToStage(stage: FeatureStage) {
        val items = arrayOf(
            getString(R.string.roleSelect_anchor),
            getString(R.string.roleSelect_audience)
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.roleSelect_title))
            .setItems(items) { _, which ->
                val role = if (which == 0) Role.ANCHOR else Role.AUDIENCE
                if (role == Role.ANCHOR) {
                    // Anchor: use userID directly as room ID, no input needed
                    val liveID = generateAnchorLiveID()
                    navigateToFunctionPage(role, stage, liveID)
                } else {
                    // Audience: show room ID input dialog
                    showLiveIDInput(role, stage)
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    /** Get anchor's room ID: use the currently logged-in userID directly */
    private fun generateAnchorLiveID(): String {
        return LoginStore.shared.loginState.loginUserInfo.value?.userID ?: ""
    }

    /** Show room ID input dialog (audience only) */
    private fun showLiveIDInput(role: Role, stage: FeatureStage) {
        val title = getString(R.string.liveIDInput_title_audience)
        val message = getString(R.string.liveIDInput_message_audience)

        val dialogBinding = DialogLiveIdInputBinding.inflate(layoutInflater)

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ ->
                val liveID = dialogBinding.etLiveID.text?.toString()?.trim() ?: ""
                if (liveID.isEmpty()) {
                    showEmptyLiveIDAlert(role, stage)
                } else {
                    navigateToFunctionPage(role, stage, liveID)
                }
            }
            .setNegativeButton(getString(R.string.common_cancel), null)
            .show()
    }

    /** Alert that room ID cannot be empty */
    private fun showEmptyLiveIDAlert(role: Role, stage: FeatureStage) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.common_warning))
            .setMessage(getString(R.string.liveIDInput_error_empty))
            .setPositiveButton(getString(R.string.common_confirm)) { _, _ ->
                showLiveIDInput(role, stage)
            }
            .show()
    }

    /** Navigate to the corresponding page based on stage */
    private fun navigateToFunctionPage(role: Role, stage: FeatureStage, liveID: String) {
        val targetClass = when (stage) {
            FeatureStage.BASIC_STREAMING -> BasicStreamingActivity::class.java
            FeatureStage.INTERACTIVE -> InteractiveActivity::class.java
            FeatureStage.CO_GUEST -> MultiConnectActivity::class.java
            FeatureStage.LIVE_PK -> LivePKActivity::class.java
        }
        val intent = Intent(this, targetClass).apply {
            putExtra("role", role.name)
            putExtra("liveID", liveID)
        }
        startActivity(intent)
    }

    // MARK: - RecyclerView Adapter

    inner class FeatureAdapter(
        private val items: List<FeatureItem>,
        private val onClick: (FeatureStage) -> Unit
    ) : RecyclerView.Adapter<FeatureAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemFeatureCardBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemFeatureCardBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val context = holder.itemView.context
            val color = ContextCompat.getColor(context, item.colorResId)

            holder.binding.tvIndex.text = "${position + 1}"
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            holder.binding.tvIndex.background = bg

            holder.binding.tvTitle.text = getString(item.titleResId)
            holder.binding.tvDescription.text = getString(item.descResId)

            holder.itemView.setOnClickListener { onClick(item.stage) }
        }

        override fun getItemCount() = items.size
    }
}
