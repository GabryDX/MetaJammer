package com.heronikostudios.metajammer.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * User-defined custom geographic location preset.
 */
@Serializable
data class CustomLocationPreset(
    override val id: String = UUID.randomUUID().toString(),
    val name: String,
    override val latitude: Double,
    override val longitude: Double
) : LocationPresetTarget {
    override val displayName: String get() = name
    override val isCustom: Boolean get() = true
}
