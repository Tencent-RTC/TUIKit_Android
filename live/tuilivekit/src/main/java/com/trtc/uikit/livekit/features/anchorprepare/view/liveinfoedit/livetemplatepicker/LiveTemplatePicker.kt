package com.trtc.uikit.livekit.features.anchorprepare.view.liveinfoedit.livetemplatepicker

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.trtc.tuikit.atomicx.widget.basicwidget.toast.AtomicToast
import com.trtc.uikit.livekit.R
import io.trtc.tuikit.atomicx.widget.basicwidget.popover.AtomicPopover
import com.trtc.uikit.livekit.features.anchorprepare.store.AnchorPrepareStore
import io.trtc.tuikit.atomicxcore.api.view.LiveCoreView

class LiveTemplatePicker(
    context: Context,
    private val store: AnchorPrepareStore,
    private val coreView: LiveCoreView
) : AtomicPopover(context) {

    init {
        initView()
    }

    private fun initView() {
        val view = View.inflate(context, R.layout.anchor_prepare_layout_select_template, null)
        view.findViewById<View>(R.id.iv_back).setOnClickListener { dismiss() }
        initCoGuestTemplateView(view)
        initCoHostTemplateView(view)
        setContent(view)
    }

    private fun initCoHostTemplateView(view: View) {
        val recyclerCoHost = view.findViewById<RecyclerView>(R.id.rv_template_co_host)
        recyclerCoHost.layoutManager = GridLayoutManager(context, 2)

        val adapter = CoHostTemplatePickAdapter(
            context,
            store,
            listOf(TemplateType.VERTICAL_DYNAMIC_GRID_CO_HOST)
        ) { type ->
            store.setCoHostTemplate(type.id)
            recyclerCoHost.adapter?.notifyDataSetChanged()
            val viewRatio = coreView.width / coreView.height.toDouble()
            val canvasRatio = 9 / 16.0
            if (type == TemplateType.VERTICAL_DYNAMIC_FLOAT_CO_HOST && viewRatio > canvasRatio) {
                AtomicToast.show(context, context.getString(R.string.common_template_601_ui_exception_toast), AtomicToast.Style.WARNING)
            }
        }
        recyclerCoHost.adapter = adapter
    }

    private fun initCoGuestTemplateView(view: View) {
        val recyclerCoGuest = view.findViewById<RecyclerView>(R.id.rv_template)
        recyclerCoGuest.layoutManager = GridLayoutManager(context, 2)

        val adapter = CoGuestTemplatePickAdapter(
            context,
            store,
            listOf(
                TemplateType.VERTICAL_DYNAMIC_GRID,
                TemplateType.VERTICAL_DYNAMIC_FLOAT,
                TemplateType.VERTICAL_STATIC_GRID,
                TemplateType.VERTICAL_STATIC_FLOAT
            )
        ) { type ->
            store.setCoGuestTemplate(type.id)
            recyclerCoGuest.adapter?.notifyDataSetChanged()
            val viewRatio = coreView.width / coreView.height.toDouble()
            val canvasRatio = 9 / 16.0
            if (type == TemplateType.VERTICAL_DYNAMIC_FLOAT && viewRatio > canvasRatio) {
                AtomicToast.show(context, context.getString(R.string.common_template_601_ui_exception_toast), AtomicToast.Style.WARNING)
            }
        }
        recyclerCoGuest.adapter = adapter
    }

    enum class TemplateType(val id: Int, val desc: Int, val icon: Int) {
        VERTICAL_DYNAMIC_GRID(600, R.string.common_template_dynamic_grid, R.drawable.anchor_prepare_dynamic_grid),
        VERTICAL_DYNAMIC_FLOAT(601, R.string.common_template_dynamic_float, R.drawable.anchor_prepare_dynamic_float),
        VERTICAL_STATIC_GRID(800, R.string.common_template_static_grid, R.drawable.anchor_prepare_static_grid),
        VERTICAL_STATIC_FLOAT(801, R.string.common_template_static_float, R.drawable.anchor_prepare_static_float),
        VERTICAL_DYNAMIC_GRID_CO_HOST(
            600,
            R.string.common_template_dynamic_grid,
            R.drawable.anchor_prepare_dynamic_grid_co_host
        ),
        VERTICAL_DYNAMIC_FLOAT_CO_HOST(
            601,
            R.string.common_template_dynamic_float,
            R.drawable.anchor_prepare_dynamic_float_co_host
        );

        companion object {
            fun getNameById(context: Context, id: Int?): String {
                if (id == null) {
                    return context.resources.getString(R.string.common_template_dynamic_grid)
                }
                for (type in TemplateType.values()) {
                    if (type.id == id) {
                        return context.resources.getString(type.desc)
                    }
                }
                return context.resources.getString(R.string.common_template_dynamic_grid)
            }
        }
    }
}