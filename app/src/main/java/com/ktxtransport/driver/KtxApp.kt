package com.ktxtransport.driver

import android.app.Application

class KtxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServerConfig.init(this)
    }
}
