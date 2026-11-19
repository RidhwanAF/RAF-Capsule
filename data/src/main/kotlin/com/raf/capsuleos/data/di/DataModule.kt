package com.raf.capsuleos.data.di

import com.raf.capsuleos.data.hardware.BluetoothRepositoryImpl
import com.raf.capsuleos.data.hardware.BrightnessRepositoryImpl
import com.raf.capsuleos.domain.repository.BluetoothRepository
import com.raf.capsuleos.data.hardware.TorchRepositoryImpl
import com.raf.capsuleos.data.hardware.VolumeRepositoryImpl
import com.raf.capsuleos.data.media.MediaSessionRepositoryImpl
import com.raf.capsuleos.data.notification.NotificationRepositoryImpl
import com.raf.capsuleos.data.settings.CapsuleSettingsRepositoryImpl
import com.raf.capsuleos.data.statusbar.StatusBarIconControllerImpl
import com.raf.capsuleos.data.system.SystemEventsRepositoryImpl
import com.raf.capsuleos.domain.repository.BrightnessRepository
import com.raf.capsuleos.domain.repository.CapsuleSettingsRepository
import com.raf.capsuleos.domain.repository.MediaSessionRepository
import com.raf.capsuleos.domain.repository.NotificationRepository
import com.raf.capsuleos.domain.repository.StatusBarIconController
import com.raf.capsuleos.domain.repository.SystemEventsRepository
import com.raf.capsuleos.domain.repository.TorchRepository
import com.raf.capsuleos.domain.repository.VolumeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds pure domain repository contracts to Android platform implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMediaSessionRepository(impl: MediaSessionRepositoryImpl): MediaSessionRepository

    @Binds
    @Singleton
    abstract fun bindTorchRepository(impl: TorchRepositoryImpl): TorchRepository

    @Binds
    @Singleton
    abstract fun bindBrightnessRepository(impl: BrightnessRepositoryImpl): BrightnessRepository

    @Binds
    @Singleton
    abstract fun bindVolumeRepository(impl: VolumeRepositoryImpl): VolumeRepository

    @Binds
    @Singleton
    abstract fun bindSystemEventsRepository(impl: SystemEventsRepositoryImpl): SystemEventsRepository

    @Binds
    @Singleton
    abstract fun bindStatusBarIconController(impl: StatusBarIconControllerImpl): StatusBarIconController

    @Binds
    @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository

    @Binds
    @Singleton
    abstract fun bindCapsuleSettingsRepository(impl: CapsuleSettingsRepositoryImpl): CapsuleSettingsRepository

    @Binds
    @Singleton
    abstract fun bindBluetoothRepository(impl: BluetoothRepositoryImpl): BluetoothRepository
}
