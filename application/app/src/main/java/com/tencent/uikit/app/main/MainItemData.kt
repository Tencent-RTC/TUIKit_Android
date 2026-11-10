package com.tencent.uikit.app.main

data class MainItemData(
    val itemType: MainTypeEnum?,
    val itemResId: Int,
    val itemTitle: Int,
    val itemSubTitle: Int,
    val itemTargetClass: Class<*>?,
    val category: Category?
) {
    enum class Category {
        UNDEFINED,
        KIT,
        HOT
    }
}