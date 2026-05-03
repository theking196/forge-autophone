package com.forge.autophone

import android.app.Application
import com.forge.autophone.di.AppContainer

class AutoPhoneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
