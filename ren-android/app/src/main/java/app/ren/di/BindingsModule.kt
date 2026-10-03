package app.ren.di

import android.content.Context
import androidx.datastore.core.DataStore
import app.ren.data.prefs.createPreferencesStore
import app.ren.data.repository.ContentRepositoryImpl
import app.ren.data.repository.MiningRepositoryImpl
import app.ren.data.repository.PreferencesRepositoryImpl
import app.ren.data.repository.StudyRepositoryImpl
import app.ren.domain.model.UserPreferences
import app.ren.domain.repository.ContentRepository
import app.ren.domain.repository.MiningRepository
import app.ren.domain.repository.PreferencesRepository
import app.ren.domain.repository.StudyRepository
import app.ren.domain.usecase.Clock
import app.ren.domain.usecase.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Process-wide scope for work that must outlive a screen, like warming the content cache. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun content(impl: ContentRepositoryImpl): ContentRepository
    @Binds abstract fun study(impl: StudyRepositoryImpl): StudyRepository
    @Binds abstract fun mining(impl: MiningRepositoryImpl): MiningRepository
    @Binds abstract fun preferences(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds abstract fun clock(impl: SystemClock): Clock
}

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun preferencesStore(@ApplicationContext context: Context): DataStore<UserPreferences> =
        createPreferencesStore(context)
}
