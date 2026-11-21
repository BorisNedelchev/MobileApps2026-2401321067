package com.bagofholding

import android.app.Activity
import android.app.Application
import com.bagofholding.di.AppContainer

class BagOfHoldingApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.themePreferences.applySavedTheme()
    }
}

/** Shortcut used by activities to reach the dependency container. */
val Activity.appContainer: AppContainer
    get() = (application as BagOfHoldingApp).container
