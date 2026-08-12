package com.pgoorts.tripplanner.di

import android.content.Context
import com.pgoorts.tripplanner.auth.GoogleAuthClient
import com.pgoorts.tripplanner.auth.UserSessionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideUserSessionManager(
        @ApplicationContext context: Context
    ): UserSessionManager = UserSessionManager(context)

    @Provides
    @Singleton
    fun provideGoogleAuthClient(
        @ApplicationContext context: Context,
        userSessionManager: UserSessionManager
    ): GoogleAuthClient = GoogleAuthClient(context, userSessionManager)
}
