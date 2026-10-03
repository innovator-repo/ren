package app.ren.di

import android.content.Context
import androidx.room.Room
import app.ren.data.local.ContentDao
import app.ren.data.local.MiningDao
import app.ren.data.local.RenDatabase
import app.ren.data.local.StudyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RenDatabase =
        Room.databaseBuilder(context, RenDatabase::class.java, "ren_core.db")
            .createFromAsset("database/ren_core.db")
            // Learner progress lives here, so a schema bump must ship a Migration (schemas/ holds every version).
            // Only a downgrade, which Play never performs, may rebuild from the asset.
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideStudyDao(database: RenDatabase): StudyDao = database.studyDao()

    @Provides
    fun provideContentDao(database: RenDatabase): ContentDao = database.contentDao()

    @Provides
    fun provideMiningDao(database: RenDatabase): MiningDao = database.miningDao()
}
