package com.cuentasclaras.app

import android.app.Application
import com.cuentasclaras.app.data.push.ExpenseNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CuentasClarasApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ExpenseNotifications.ensureChannel(this)
    }
}
