package cz.svitaninymburk.projects.reservations.android

import android.app.Application
import cz.svitaninymburk.projects.reservations.android.di.appModule
import cz.svitaninymburk.projects.reservations.android.feature.login.loginModule
import cz.svitaninymburk.projects.reservations.android.feature.profile.profileModule
import cz.svitaninymburk.projects.reservations.android.feature.reservations.reservationsModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ReservationsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ReservationsApp)
            modules(appModule, loginModule, reservationsModule, profileModule)
        }
    }
}
