package com.livekit.demo.logshare

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.livekit.demo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class LogShareFragment : BottomSheetDialogFragment() {
    companion object { const val TAG = "log_share" }

    override fun getTheme() = R.style.Theme_LiveKitAccess_LogShare

    override fun onStart() {
        super.onStart()
        val sheet = (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val windowHeight = requireActivity().window.decorView.height
        sheet.layoutParams = sheet.layoutParams.apply { height = (windowHeight * 0.7f).toInt() }
        com.google.android.material.bottomsheet.BottomSheetBehavior.from(sheet).state =
            com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    }

    private var busy = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.livekit_access_log_share, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.livekit_log_cancel).setOnClickListener { dismiss() }
        val list = view.findViewById<RecyclerView>(R.id.livekit_log_list)
        list.layoutManager = LinearLayoutManager(requireContext())
        val directory = requireContext().getExternalFilesDir(null)?.resolve("log/liteav")
        setBusy(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { LogShareFiles.scan(directory) } }
            setBusy(false)
            result.fold(onSuccess = { files ->
                list.adapter = LogAdapter(files) { selected, archive -> export(selected, archive) }
                showMessage(if (files.isEmpty()) R.string.livekit_logs_empty else null)
            }, onFailure = { showMessage(R.string.livekit_logs_read_failed) })
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        view?.findViewById<ProgressBar>(R.id.livekit_log_progress)?.isVisible = value
        view?.findViewById<RecyclerView>(R.id.livekit_log_list)?.alpha = if (value) 0.5f else 1f
    }

    private fun showMessage(message: Int?) {
        view?.findViewById<TextView>(R.id.livekit_log_message)?.apply {
            isVisible = message != null
            if (message != null) setText(message)
        }
    }

    private fun export(files: List<LogShareFiles.Entry>, archive: Boolean) {
        if (busy) return
        setBusy(true)
        showMessage(null)
        val cache = requireContext().cacheDir
        viewLifecycleOwner.lifecycleScope.launch {
            var prepared: LogShareFiles.Export? = null
            var handedOff = false
            try {
                // Keep ownership of a completed export even if the sheet closes during IO.
                withContext(Dispatchers.IO + NonCancellable) {
                    prepared = LogShareFiles.prepare(cache, files, archive)
                }
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@launch
                val file = requireNotNull(prepared).file
                val context = requireContext()
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.logshare", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = if (archive) "application/zip" else "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(send, getString(R.string.livekit_logs_title)))
                handedOff = true
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (isAdded && view != null) {
                    Toast.makeText(requireContext(), R.string.livekit_logs_failed, Toast.LENGTH_LONG).show()
                }
            } finally {
                if (!handedOff) withContext(Dispatchers.IO + NonCancellable) { prepared?.remove() }
                setBusy(false)
            }
        }
    }

    private class LogAdapter(
        private val files: List<LogShareFiles.Entry>,
        private val share: (List<LogShareFiles.Entry>, Boolean) -> Unit
    ) : RecyclerView.Adapter<LogAdapter.Holder>() {
        private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        override fun getItemCount() = if (files.isEmpty()) 0 else files.size + 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(
            LayoutInflater.from(parent.context).inflate(R.layout.livekit_access_log_item, parent, false)
        )
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val context = holder.itemView.context
            if (position == 0) {
                holder.title.setText(R.string.livekit_logs_archive)
                holder.detail.text = context.getString(R.string.livekit_logs_count, files.size)
                holder.itemView.setOnClickListener { share(files, true) }
            } else {
                val file = files[position - 1]
                holder.title.text = file.file.name
                holder.detail.text = context.getString(R.string.livekit_logs_metadata,
                    dateFormat.format(Date(file.modified)), Formatter.formatFileSize(context, file.size))
                holder.itemView.setOnClickListener { share(listOf(file), false) }
            }
        }
        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.livekit_log_filename)
            val detail: TextView = view.findViewById(R.id.livekit_log_detail)
        }
    }
}
