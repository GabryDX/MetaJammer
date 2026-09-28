package com.heronikostudios.metajammer.domain.model

enum class PoisoningProfile(
    val displayName: String,
    val description: String
) {
    RANDOM(
        displayName = "Randomized Noise",
        description = "Random selection from all supported camera manufacturers and smartphone models."
    ),
    PRO_MIRRORLESS(
        displayName = "Pro Mirrorless",
        description = "Sony α7 IV / Canon EOS R5 full-frame with high-end prime or G Master / L zoom lenses."
    ),
    MODERN_SMARTPHONE(
        displayName = "Modern Smartphone",
        description = "Flagship smartphone (iPhone 15 Pro, Pixel 8 Pro, Galaxy S24 Ultra) mobile camera metadata."
    ),
    VINTAGE_DIGITAL(
        displayName = "Vintage Point-and-Shoot",
        description = "Late 90s and early 2000s compact CCD camera (Olympus Camedia, Canon PowerShot G-series)."
    ),
    ACTION_CAM(
        displayName = "Action Cam",
        description = "Ultra-wide, fixed-focus action camera (GoPro HERO12, DJI Osmo Action 4)."
    ),
    ANONYMOUS_MINIMAL(
        displayName = "Anonymous Minimal",
        description = "Generic camera body with zero identifying lens metadata, serials, or descriptions."
    );

    fun toReadableLabel(): String = displayName
}
