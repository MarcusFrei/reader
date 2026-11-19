package com.example.bookreader.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.util.Calendar

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun currentMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

fun isInRange(now: Int, start: Int, end: Int): Boolean =
    if (start <= end) now in start until end else now >= start || now < end

fun formatRemaining(minutes: Int): String =
    if (minutes < 60) "~$minutes мин" else "~${minutes / 60} ч ${minutes % 60} мин"

class VMFactory<T : ViewModel>(private val make: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <V : ViewModel> create(modelClass: Class<V>): V = make() as V
}
