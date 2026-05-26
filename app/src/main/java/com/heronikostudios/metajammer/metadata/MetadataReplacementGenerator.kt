package com.heronikostudios.metajammer.metadata

import kotlin.random.Random

object MetadataReplacementGenerator {

    private val makes = listOf("GenericCam", "PixelForge", "NovaLens", "MobileUnit")
    private val models = listOf("X1", "A20", "M7", "Unit-7", "ProtoCam")
    private val software = listOf("MetaJammer", "ImageSuite", "MediaEditor", "PrivacyTool")

    fun randomDateTime(): String {
        val year = Random.nextInt(2018, 2025)
        val month = Random.nextInt(1, 13)
        val day = Random.nextInt(1, 28)
        val hour = Random.nextInt(0, 24)
        val minute = Random.nextInt(0, 60)
        val second = Random.nextInt(0, 60)
        return "%04d:%02d:%02d %02d:%02d:%02d".format(year, month, day, hour, minute, second)
    }

    fun randomLatLong(): Pair<Double, Double> {
        val lat = Random.nextDouble(-80.0, 80.0)
        val lon = Random.nextDouble(-170.0, 170.0)
        return lat to lon
    }

    fun randomMake(): String = makes.random()
    fun randomModel(): String = models.random()
    fun randomSoftware(): String = software.random()
}
