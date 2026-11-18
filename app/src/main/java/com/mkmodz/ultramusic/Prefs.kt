package com.mkmodz.ultramusic

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    lateinit var sp: SharedPreferences
    fun init(c: Context) {
        if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("mkmodz", Context.MODE_PRIVATE)
    }
}
