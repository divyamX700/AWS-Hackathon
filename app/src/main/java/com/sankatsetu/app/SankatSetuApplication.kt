package com.sankatsetu.app

import android.app.Application
import com.sankatsetu.app.di.AppContainer

class SankatSetuApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
