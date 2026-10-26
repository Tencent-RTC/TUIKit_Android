package com.trtc.uikit.roomkit.view.schedule.wheelpicker

interface IWheelPicker {

    var visibleItemCount: Int

    var selectedItemPosition: Int

    val currentItemPosition: Int

    var onItemSelectedListener: WheelPicker.OnItemSelectedListener?

    var onWheelChangeListener: WheelPicker.OnWheelChangeListener?

    fun setData(data: List<String>)
}
