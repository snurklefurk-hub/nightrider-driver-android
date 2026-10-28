package com.nightrider.driver

import android.content.Context
import android.util.TypedValue

/** Resolve the same semantic colors used by the board and Settings screens. */
internal fun Context.appearanceColor(attribute: Int): Int {
    val value = TypedValue()
    check(theme.resolveAttribute(attribute, value, true))
    return if (value.resourceId != 0) getColor(value.resourceId) else value.data
}
