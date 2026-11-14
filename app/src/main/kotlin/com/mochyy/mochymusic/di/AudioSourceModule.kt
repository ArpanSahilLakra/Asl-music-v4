package com.mochyy.mochymusic.di

import com.mochyy.mochymusic.domain.audio.AudioSourceProviderContract
import com.mochyy.mochymusic.domain.audio.AudioSourceResolverContract
import com.mochyy.mochymusic.domain.audio.LicensedAudioProviderContract
import com.mochyy.mochymusic.domain.audio.LocalAudioProviderContract
import com.mochyy.mochymusic.playback.audio.AudioSourceProvider
import com.mochyy.mochymusic.playback.audio.AudioSourceResolver
import com.mochyy.mochymusic.playback.audio.LicensedAudioProvider
import com.mochyy.mochymusic.playback.audio.LocalAudioProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalAudioSource

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LicensedAudioSource

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioSourceModule {

    @Binds
    @Singleton
    abstract fun bindAudioSourceResolverContract(
        impl: AudioSourceResolver,
    ): AudioSourceResolverContract

    @Binds
    @Singleton
    abstract fun bindLocalAudioProviderContract(
        impl: LocalAudioProvider,
    ): LocalAudioProviderContract

    @Binds
    @Singleton
    abstract fun bindLicensedAudioProviderContract(
        impl: LicensedAudioProvider,
    ): LicensedAudioProviderContract

    @Binds
    @Singleton
    @LocalAudioSource
    abstract fun bindLocalAudioSourceProvider(
        impl: LocalAudioProvider,
    ): AudioSourceProvider

    @Binds
    @Singleton
    @LicensedAudioSource
    abstract fun bindLicensedAudioSourceProvider(
        impl: LicensedAudioProvider,
    ): AudioSourceProvider

    @Binds
    @IntoSet
    abstract fun bindLocalIntoProviderSet(
        impl: LocalAudioProvider,
    ): AudioSourceProvider

    @Binds
    @IntoSet
    abstract fun bindLicensedIntoProviderSet(
        impl: LicensedAudioProvider,
    ): AudioSourceProvider

    @Binds
    @IntoSet
    abstract fun bindLocalIntoDomainProviderSet(
        impl: LocalAudioProvider,
    ): AudioSourceProviderContract

    @Binds
    @IntoSet
    abstract fun bindLicensedIntoDomainProviderSet(
        impl: LicensedAudioProvider,
    ): AudioSourceProviderContract
}
