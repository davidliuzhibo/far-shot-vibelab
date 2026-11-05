package com.vrgames.sniper.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.sniper.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
