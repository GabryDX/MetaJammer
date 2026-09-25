package com.heronikostudios.metajammer.domain.model

enum class LocationPreset(
    val displayName: String,
    val latitude: Double?,
    val longitude: Double?
) {
    RANDOM("Random / Scramble", null, null),
    TOKYO("Tokyo, Japan", 35.6762, 139.6503),
    PARIS("Paris, France", 48.8566, 2.3522),
    NEW_YORK("New York, USA", 40.7128, -74.0060),
    LONDON("London, UK", 51.5074, -0.1278),
    ROME("Rome, Italy", 41.9028, 12.4964),
    SYDNEY("Sydney, Australia", -33.8688, 151.2093),
    REYKJAVIK("Reykjavik, Iceland", 64.1466, -21.9426),
    CAIRO("Cairo, Egypt", 30.0444, 31.2357);

    fun toReadableLabel(): String = displayName
}
