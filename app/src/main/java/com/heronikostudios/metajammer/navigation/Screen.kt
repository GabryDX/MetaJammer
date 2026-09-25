package com.heronikostudios.metajammer.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes for MetaJammer.
 */
sealed interface Screen {

    @Serializable
    data object Onboarding : Screen

    @Serializable
    data object Home : Screen

    @Serializable
    data object Preview : Screen

    @Serializable
    data object Process : Screen

    @Serializable
    data class LocationPicker(val uriString: String) : Screen

    @Serializable
    data object Output : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Help : Screen

    @Serializable
    data object History : Screen

    @Serializable
    data object QuickScrub : Screen
}
