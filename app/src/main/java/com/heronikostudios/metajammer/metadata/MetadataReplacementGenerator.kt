package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import java.time.LocalDateTime
import kotlin.random.Random

object MetadataReplacementGenerator {

    private val makes = listOf(
        "Canon", "Nikon", "Sony", "Fujifilm", "Panasonic", "Olympus",
        "Apple", "Samsung", "Google", "Xiaomi", "OnePlus", "Huawei",
        "Motorola", "DJI", "GoPro", "Leica"
    )

    private val modelsByMake = mapOf(
        "Canon" to listOf("EOS 80D", "EOS R6", "EOS 5D Mark IV", "PowerShot G7 X Mark III"),
        "Nikon" to listOf("D750", "D850", "Z6 II", "Z fc"),
        "Sony" to listOf("ILCE-7M3", "ILCE-7C", "DSC-RX100M7", "ILCE-6400"),
        "Fujifilm" to listOf("X-T4", "X100V", "X-S10", "GFX 50S"),
        "Panasonic" to listOf("DC-GH5", "DC-S5", "DMC-LX100"),
        "Olympus" to listOf("E-M10MarkIII", "E-M1MarkII", "TG-6"),
        "Apple" to listOf("iPhone 11", "iPhone 12", "iPhone 13 Pro", "iPhone 14", "iPhone 15 Pro"),
        "Samsung" to listOf("SM-G991B", "SM-S918B", "SM-A546B", "SM-G998U"),
        "Google" to listOf("Pixel 6", "Pixel 7", "Pixel 7 Pro", "Pixel 8"),
        "Xiaomi" to listOf("Mi 11", "Redmi Note 12", "13 Pro", "POCO F5"),
        "OnePlus" to listOf("ONEPLUS A6013", "CPH2449", "DN2103"),
        "Huawei" to listOf("VOG-L29", "ANA-NX9", "ELS-NX9"),
        "Motorola" to listOf("moto g82 5G", "moto g73", "edge 30"),
        "DJI" to listOf("FC3170", "FC3582", "DJI Mini 3 Pro"),
        "GoPro" to listOf("HERO9 Black", "HERO10 Black", "HERO11 Black"),
        "Leica" to listOf("Q2", "M10", "D-Lux 7")
    )

    private val softwareByMake = mapOf(
        "Canon" to listOf("Firmware 1.1.0", "Firmware 1.2.1", "Digital Photo Professional"),
        "Nikon" to listOf("Ver.1.10", "NX Studio", "Firmware 2.00"),
        "Sony" to listOf("ILCE-7M3 v4.01", "Imaging Edge", "PlayMemories"),
        "Fujifilm" to listOf("FUJIFILM X RAW STUDIO", "Firmware 3.00", "FUJIFILM Camera Remote"),
        "Panasonic" to listOf("LUMIX Sync", "Firmware 2.4"),
        "Olympus" to listOf("OLYMPUS Workspace", "Firmware 1.3"),
        "Apple" to listOf("16.6", "17.1", "17.4.1", "18.0"),
        "Samsung" to listOf("TP1A.220624.014", "One UI 5.1", "One UI 6.0"),
        "Google" to listOf("UQ1A.240205.002", "TQ3A.230901.001", "Android 14"),
        "Xiaomi" to listOf("MIUI 14", "HyperOS 1.0", "V14.0.6.0"),
        "OnePlus" to listOf("OxygenOS 13", "OxygenOS 14"),
        "Huawei" to listOf("EMUI 13", "HarmonyOS 4"),
        "Motorola" to listOf("My UX", "Android 13"),
        "DJI" to listOf("DJI Fly", "Firmware 01.00.0500"),
        "GoPro" to listOf("HD11.01.20.00", "Quik"),
        "Leica" to listOf("Firmware 5.0", "Leica FOTOS")
    )

    private val locations = listOf(
        "Downtown", "Old Town", "City Center", "Harbor", "Waterfront",
        "North Ridge", "South District", "Central Park", "Market Square",
        "Riverside", "University Campus", "Industrial Area", "Suburbs",
        "Historic Quarter", "Seaside", "Hilltop", "Station Area"
    )

    private val genericDescriptions = listOf(
        "Outdoor scene", "Street view", "Portrait shot", "Landscape", "Night shot",
        "Daylight photo", "Travel photo", "Family moment", "Weekend outing",
        "City skyline", "Close-up photo", "Casual snapshot", "Nature view",
        "Indoor shot", "Event photo", "Vacation memory"
    )

    private val realisticDescriptions = listOf(
        "Morning walk in the park", "Sunset over the water", "Weekend trip downtown",
        "Coffee shop window seat", "View from the hotel balcony", "Quiet street after rain",
        "Afternoon by the river", "Family lunch outdoors", "City lights at night",
        "Train station platform", "A quick stop on the way home", "View from the trail",
        "Beachside in the evening", "Lunch break outside", "First day of the trip",
        "Last evening before leaving", "View from the apartment", "Walk through the old town",
        "Late afternoon in the city", "Short break during the drive"
    )

    private val userComments = listOf(
        "Nice lighting in this one", "Taken during the trip", "Good shot from earlier",
        "Saved for later", "Remember this place", "Best one from today",
        "Kept this version", "Captured on the way back", "Worth keeping",
        "One of my favorites", "Shot from the other side", "Good detail in the background",
        "Better than the first attempt", "This angle worked well", "Taken just before sunset",
        "Looks better full size", "Original version kept", "Clearer than expected",
        "Taken near the station", "Good color in this one"
    )

    private val imageDirections = listOf(
        "Front camera", "Rear camera", "Wide lens", "Main camera", "Telephoto", "Ultra wide"
    )

    fun randomRecentDateTime(): String {
        val now = LocalDateTime.now()
        val year = Random.nextInt(now.year - 3, now.year + 1)
        val month = Random.nextInt(1, 13)
        val day = Random.nextInt(1, 29)
        val hour = Random.nextInt(6, 23)
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

    fun randomModel(make: String): String {
        return modelsByMake[make]?.random() ?: "Model-${Random.nextInt(100, 999)}"
    }

    fun randomSoftware(make: String): String {
        return softwareByMake[make]?.random()
            ?: listOf("Firmware 1.0", "Android 13", "Android 14", "System 2.1").random()
    }

    fun randomImageDescription(): String {
        return when (Random.nextInt(4)) {
            0 -> realisticDescriptions.random()
            1 -> "${genericDescriptions.random()} - ${locations.random()}"
            2 -> "${realisticDescriptions.random()}, ${locations.random()}"
            else -> genericDescriptions.random()
        }
    }

    fun randomUserComment(): String {
        return when (Random.nextInt(5)) {
            0 -> userComments.random()
            1 -> "${userComments.random()}."
            2 -> "${realisticDescriptions.random()}."
            3 -> "${imageDirections.random()}, ${userComments.random().lowercase()}"
            else -> "Taken ${listOf("earlier", "today", "in the evening", "during the trip", "on the way").random()}"
        }
    }

    fun randomPhotographicSensitivity(): String {
        return listOf("50", "64", "80", "100", "125", "160", "200", "250", "320", "400", "640", "800").random()
    }

    fun randomExposureTime(): String {
        return listOf(
            "1/30", "1/40", "1/50", "1/60", "1/80", "1/100", "1/125",
            "1/160", "1/200", "1/250", "1/320", "1/500", "1/1000"
        ).random()
    }

    fun randomFNumber(): String {
        return listOf("1.8", "2.0", "2.2", "2.4", "2.8", "3.5", "4.0", "5.6", "8.0").random()
    }

    fun randomFocalLength(): String {
        return listOf("1.8", "2.2", "4.2", "4.5", "5.4", "6.0", "24.0", "35.0", "50.0", "85.0").random()
    }

    fun randomWhiteBalance(): String {
        return listOf("0", "1").random()
    }

    fun randomFlash(): String {
        return listOf("0", "1").random()
    }

    fun generatePlan(
        existingLat: Double? = null,
        existingLon: Double? = null
    ): MetadataReplacementPlan {
        val make = randomMake()
        val model = randomModel(make)
        val software = randomSoftware(make)
        val dateTime = randomRecentDateTime()
        val imageDescription = randomImageDescription()
        val userComment = randomUserComment()
        val photographicSensitivity = randomPhotographicSensitivity()
        val exposureTime = randomExposureTime()
        val fNumber = randomFNumber()
        val focalLength = randomFocalLength()
        val whiteBalance = randomWhiteBalance()
        val flash = randomFlash()
        
        val (latitude, longitude) = if (existingLat != null && existingLon != null) {
            // Nearby scramble: shift by roughly 200m-1km
            // 0.001 degree is roughly 111m
            val latOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
            val lonOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
            (existingLat + latOffset) to (existingLon + lonOffset)
        } else {
            randomLatLong()
        }

        val latitudeRef = if (latitude >= 0) "N" else "S"
        val longitudeRef = if (longitude >= 0) "E" else "W"

        return MetadataReplacementPlan(
            dateTime = dateTime,
            make = make,
            model = model,
            software = software,
            imageDescription = imageDescription,
            userComment = userComment,
            photographicSensitivity = photographicSensitivity,
            exposureTime = exposureTime,
            fNumber = fNumber,
            focalLength = focalLength,
            whiteBalance = whiteBalance,
            flash = flash,
            latitude = latitude,
            longitude = longitude,
            latitudeRef = latitudeRef,
            longitudeRef = longitudeRef
        )
    }
}
