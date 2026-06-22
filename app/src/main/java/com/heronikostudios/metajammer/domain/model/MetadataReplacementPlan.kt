package com.heronikostudios.metajammer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MetadataReplacementPlan(
    val dateTime: String,
    val make: String,
    val model: String,
    val software: String,
    val imageDescription: String,
    val userComment: String,
    val photographicSensitivity: String,
    val exposureTime: String,
    val fNumber: String,
    val focalLength: String,
    val whiteBalance: String,
    val flash: String,
    val latitude: Double,
    val longitude: Double,
    val latitudeRef: String,
    val longitudeRef: String,
    
    // Media-specific metadata (Audio/Video)
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
    val year: String? = null,
    val mediaDate: String? = null, // Full date for media (ISO 8601)
    
    // PDF-specific metadata
    val author: String? = null,
    val creator: String? = null,
    val producer: String? = null,
    val pdfTitle: String? = null,
    val subject: String? = null,
    val keywords: String? = null
)
