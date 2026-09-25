package com.heronikostudios.metajammer.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MetadataReplacementPlan(
    val dateTime: String = "2025:01:01 12:00:00",
    val make: String = "GenericMake",
    val model: String = "GenericModel",
    val software: String = "GenericSoftware",
    val imageDescription: String = "GenericDescription",
    val userComment: String = "GenericComment",
    val photographicSensitivity: String = "100",
    val exposureTime: String = "1/100",
    val fNumber: String = "2.8",
    val focalLength: String = "50/1",
    val whiteBalance: String = "0",
    val flash: String = "0",
    val lensMake: String? = null,
    val lensModel: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val latitudeRef: String = "N",
    val longitudeRef: String = "E",
    
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
