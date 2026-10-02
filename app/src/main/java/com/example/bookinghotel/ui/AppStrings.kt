package com.example.bookinghotel.ui

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface AppStrings {
    fun get(@StringRes id: Int, vararg args: Any): String
}

@Singleton
class AndroidAppStrings @Inject constructor(@ApplicationContext private val context: Context) : AppStrings {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)
}
