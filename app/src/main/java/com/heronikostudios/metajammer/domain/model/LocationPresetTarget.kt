package com.heronikostudios.metajammer.domain.model

/**
 * Common interface representing any geographic location preset target,
 * whether built-in (e.g. Tokyo, Paris) or custom user-defined coordinates.
 */
interface LocationPresetTarget {
    val id: String
    val displayName: String
    val latitude: Double?
    val longitude: Double?
    val isCustom: Boolean get() = false
}
