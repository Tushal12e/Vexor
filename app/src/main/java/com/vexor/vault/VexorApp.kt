package com.vexor.vault

import android.app.Application
import androidx.multidex.MultiDex
import android.content.Context

class VexorApp : Application() {
    
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}
