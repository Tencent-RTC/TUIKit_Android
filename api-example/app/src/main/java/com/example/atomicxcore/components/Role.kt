package com.example.atomicxcore.components

import com.example.atomicxcore.R

/**
 * Shared role enum definition
 * Used to pass user identity between feature pages
 * Corresponds to iOS's Role enum
 */
enum class Role(val titleResId: Int) {
    ANCHOR(R.string.roleSelect_anchor),
    AUDIENCE(R.string.roleSelect_audience);

    companion object {
        fun fromName(name: String): Role {
            return when (name.uppercase()) {
                "ANCHOR" -> ANCHOR
                "AUDIENCE" -> AUDIENCE
                else -> ANCHOR
            }
        }
    }
}
