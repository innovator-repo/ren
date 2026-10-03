package app.ren

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import app.ren.core.audio.AudioEngineManager
import app.ren.di.ApplicationScope
import app.ren.domain.repository.PreferencesRepository
import app.ren.work.NotificationHelper
import app.ren.work.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class RenApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var audio: AudioEngineManager
    @Inject lateinit var preferences: PreferencesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(audio)
        NotificationHelper.ensureChannel(this)
        scope.launch {
            val p = preferences.current()
            if (p.onboarded) ReminderScheduler.schedule(this@RenApplication, p.reminderEnabled, p.reminderMinuteOfDay)
        }
    }
}
