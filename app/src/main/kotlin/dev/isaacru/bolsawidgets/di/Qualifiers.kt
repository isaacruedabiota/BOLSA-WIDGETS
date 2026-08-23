package dev.isaacru.bolsawidgets.di

import javax.inject.Qualifier

/** Dispatcher for disk and network work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU-bound work such as bitmap rendering for the widgets. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
