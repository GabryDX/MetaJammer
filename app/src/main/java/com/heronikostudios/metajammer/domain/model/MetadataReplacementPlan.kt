package com.heronikostudios.metajammer.domain.model

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
    val longitudeRef: String
)
