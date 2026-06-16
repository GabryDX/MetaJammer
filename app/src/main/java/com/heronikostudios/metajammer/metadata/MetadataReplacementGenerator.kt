package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import java.time.LocalDateTime
import kotlin.random.Random

object MetadataReplacementGenerator {

    private val makes = listOf(
        "Canon", "Nikon", "Sony", "Fujifilm", "Panasonic", "Olympus",
        "Apple", "Samsung", "Google", "Xiaomi", "OnePlus", "Huawei",
        "Motorola", "DJI", "GoPro", "Leica", "Ricoh", "Pentax", "Hasselblad",
        "Oppo", "Vivo", "Asus"
    )

    private val modelsByMake = mapOf(
        "Canon" to listOf("EOS 80D", "EOS 90D", "EOS R5", "EOS R6", "EOS R8", "EOS 5D Mark IV", "PowerShot G7 X Mark III"),
        "Nikon" to listOf("D750", "D850", "Z6 II", "Z8", "Z9", "Z fc"),
        "Sony" to listOf("ILCE-7M3", "ILCE-7M4", "ILCE-7RM5", "ILCE-1", "ILCE-7C", "DSC-RX100M7", "ILCE-6400", "ILCE-6700"),
        "Fujifilm" to listOf("X-T4", "X-T5", "X100V", "X100VI", "X-S10", "X-S20", "GFX 50S", "GFX 100S"),
        "Panasonic" to listOf("DC-GH5", "DC-GH6", "DC-S5", "DC-S5M2", "DMC-LX100"),
        "Olympus" to listOf("E-M10MarkIII", "E-M1MarkII", "OM-1", "TG-6", "TG-7"),
        "Apple" to listOf("iPhone 12", "iPhone 13 Pro", "iPhone 14", "iPhone 15 Pro", "iPhone 15 Pro Max", "iPhone 16", "iPhone 16 Pro", "iPhone 17 Pro"),
        "Samsung" to listOf("SM-G991B", "SM-S918B", "SM-S928B", "SM-A546B", "SM-A556B", "SM-G998U", "Galaxy Z Fold 5", "Galaxy Z Flip 5"),
        "Google" to listOf("Pixel 6", "Pixel 7", "Pixel 7 Pro", "Pixel 8", "Pixel 8 Pro", "Pixel 9 Pro", "Pixel Fold"),
        "Xiaomi" to listOf("Mi 11", "12 Pro", "13 Pro", "14 Ultra", "Redmi Note 12", "Redmi Note 13 Pro", "POCO F5"),
        "OnePlus" to listOf("ONEPLUS A6013", "CPH2449", "CPH2581", "DN2103", "OnePlus 12"),
        "Huawei" to listOf("VOG-L29", "ANA-NX9", "ELS-NX9", "P60 Pro", "Mate 60 Pro"),
        "Motorola" to listOf("moto g82 5G", "moto g73", "moto g84", "edge 30", "edge 40 pro", "Razr 40 Ultra"),
        "DJI" to listOf("FC3170", "FC3582", "DJI Mini 3 Pro", "DJI Mini 4 Pro", "Mavic 3 Classic", "Osmo Action 4"),
        "GoPro" to listOf("HERO9 Black", "HERO10 Black", "HERO11 Black", "HERO12 Black"),
        "Leica" to listOf("Q2", "Q3", "M10", "M11", "D-Lux 7", "SL2"),
        "Ricoh" to listOf("GR III", "GR IIIx", "WG-80"),
        "Pentax" to listOf("K-3 Mark III", "K-1 Mark II", "KF"),
        "Hasselblad" to listOf("X2D 100C", "907X", "X1D II 50C"),
        "Oppo" to listOf("Find X5 Pro", "Find X6 Pro", "Find X7 Ultra", "Reno 10 Pro"),
        "Vivo" to listOf("X90 Pro", "X100 Pro", "V27", "V29"),
        "Asus" to listOf("Zenfone 9", "Zenfone 10", "Zenfone 11 Ultra", "ROG Phone 7", "ROG Phone 8")
    )

    private val softwareByMake = mapOf(
        "Canon" to listOf("Firmware 1.1.0", "Firmware 1.2.1", "Firmware 1.4.0", "Digital Photo Professional"),
        "Nikon" to listOf("Ver.1.10", "Ver.1.30", "NX Studio", "Firmware 2.00", "Firmware 3.10"),
        "Sony" to listOf("ILCE-7M3 v4.01", "ILCE-7M4 v2.00", "Imaging Edge", "PlayMemories", "Creator's App"),
        "Fujifilm" to listOf("FUJIFILM X RAW STUDIO", "Firmware 3.00", "Firmware 2.10", "FUJIFILM Camera Remote", "XApp"),
        "Panasonic" to listOf("LUMIX Sync", "Firmware 2.4", "Firmware 3.0"),
        "Olympus" to listOf("OLYMPUS Workspace", "Firmware 1.3", "OM Image Share"),
        "Apple" to listOf("16.6", "17.1", "17.4.1", "18.0", "18.2", "19.0"),
        "Samsung" to listOf("TP1A.220624.014", "UP1A.231005.007", "One UI 5.1", "One UI 6.0", "One UI 6.1"),
        "Google" to listOf("UQ1A.240205.002", "TQ3A.230901.001", "Android 14", "Android 15", "AP1A.240405.002"),
        "Xiaomi" to listOf("MIUI 14", "HyperOS 1.0", "HyperOS 2.0", "V14.0.6.0", "V816.0.1.0"),
        "OnePlus" to listOf("OxygenOS 13", "OxygenOS 14", "OxygenOS 15"),
        "Huawei" to listOf("EMUI 13", "HarmonyOS 4", "HarmonyOS NEXT"),
        "Motorola" to listOf("My UX", "Hello UI", "Android 13", "Android 14"),
        "DJI" to listOf("DJI Fly", "Firmware 01.00.0500", "Firmware 01.02.0000", "DJI Mimo"),
        "GoPro" to listOf("HD11.01.20.00", "HD12.01.10.00", "Quik"),
        "Leica" to listOf("Firmware 5.0", "Firmware 2.0.1", "Leica FOTOS"),
        "Ricoh" to listOf("Firmware 1.31", "Image Sync"),
        "Pentax" to listOf("Firmware 2.10", "Image Transmitter 2"),
        "Hasselblad" to listOf("Phocus", "Firmware 1.0.6", "Firmware 3.1.0"),
        "Oppo" to listOf("ColorOS 13", "ColorOS 14"),
        "Vivo" to listOf("Funtouch OS 13", "Funtouch OS 14", "OriginOS 4"),
        "Asus" to listOf("ZenUI 10", "ROG UI", "Android 14")
    )

    private val locations = listOf(
        "Downtown", "Old Town", "City Center", "Harbor", "Waterfront",
        "North Ridge", "South District", "Central Park", "Market Square",
        "Riverside", "University Campus", "Industrial Area", "Suburbs",
        "Historic Quarter", "Seaside", "Hilltop", "Station Area",
        "Mountain Peak", "Forest Trail", "Desert Highway", "Local Cafe",
        "Airport Terminal", "Train Interior", "Hidden Beach", "Botanical Garden",
        "Amusement Park", "Stadium", "National Park", "Quiet Alley", "Rooftop"
    )

    private val genericDescriptions = listOf(
        "Outdoor scene", "Street view", "Portrait shot", "Landscape", "Night shot",
        "Daylight photo", "Travel photo", "Family moment", "Weekend outing",
        "City skyline", "Close-up photo", "Casual snapshot", "Nature view",
        "Indoor shot", "Event photo", "Vacation memory", "Macro photography",
        "Astrophotography", "Architecture", "Candid shot", "Wildlife",
        "Food photography", "Abstract", "Long exposure", "Golden hour"
    )

    private val realisticDescriptions = listOf(
        "Morning walk in the park", "Sunset over the water", "Weekend trip downtown",
        "Coffee shop window seat", "View from the hotel balcony", "Quiet street after rain",
        "Afternoon by the river", "Family lunch outdoors", "City lights at night",
        "Train station platform", "A quick stop on the way home", "View from the trail",
        "Beachside in the evening", "Lunch break outside", "First day of the trip",
        "Last evening before leaving", "View from the apartment", "Walk through the old town",
        "Late afternoon in the city", "Short break during the drive", "Hiking up the mountain trail",
        "Quick snap before dinner", "Waiting at the departure gate", "Concert from the back row",
        "Sunrise through the window", "Kids playing in the yard", "Snowy morning commute",
        "Exploring the ruins", "Road trip pit stop"
    )

    private val userComments = listOf(
        "Nice lighting in this one", "Taken during the trip", "Good shot from earlier",
        "Saved for later", "Remember this place", "Best one from today",
        "Kept this version", "Captured on the way back", "Worth keeping",
        "One of my favorites", "Shot from the other side", "Good detail in the background",
        "Better than the first attempt", "This angle worked well", "Taken just before sunset",
        "Looks better full size", "Original version kept", "Clearer than expected",
        "Taken near the station", "Good color in this one", "No filter needed",
        "Straight out of camera", "Edited in LR", "Cropped version",
        "Too dark but keeping it", "Great memories", "Test shot",
        "Needs editing later", "Focus slightly off but okay", "Perfect timing"
    )

    private val imageDirections = listOf(
        "Front camera", "Rear camera", "Wide lens", "Main camera", "Telephoto",
        "Ultra wide", "Macro lens", "Periscope zoom", "Portrait mode"
    )

    // Audio-specific lists
    private val audioTitles = listOf(
        "Recording", "Meeting Notes", "Voice Memo", "Voice_001", "Lecture",
        "New Track", "Final Mix", "Rough Demo", "Interview", "Field Recording",
        "Podcast Interview", "Band Practice", "Guitar Riff Idea", "Voice_Note_045",
        "Brainstorming Session", "Client Call", "Audio Journal", "Jam Session"
    )
    private val audioArtists = listOf(
        "Voice Recorder", "MetaJammer", "System", "Unknown Artist", "Internal Mic",
        "Dictaphone", "Studio A", "Default User", "Mobile Capture", "Field Mic"
    )
    private val audioAlbums = listOf(
        "Voice Memos", "Recordings", "Drafts", "Archive", "Captures",
        "Audio Notes", "Project Files", "Unsorted", "Session 1"
    )
    private val audioGenres = listOf(
        "Voice", "Speech", "Notes", "Podcast", "Ambient", "Other",
        "Acoustic", "Interview", "Lecture", "Soundscape", "Demo"
    )

    // Video-specific lists
    private val videoTitles = listOf(
        "Project Alpha", "Final Cut", "Scene 01", "Vlog Update", "Travel Diary",
        "Short Film", "Draft 2", "Birthday Video", "Holiday Recap", "Nature Clip",
        "Drone Footage", "Timelapse", "B-Roll", "Game Highlight", "Dashcam Capture",
        "Concert Clip", "Unboxing", "Tutorial Draft", "Sequence 04", "Raw Footage"
    )
    private val videoDirectors = listOf(
        "Internal Studio", "MetaJammer Video", "Content Creator", "System Camera", "Mobile User",
        "Drone Operator", "Main Cam", "Action Cam", "User Profile 1"
    )
    private val videoGenres = listOf(
        "Action", "Comedy", "Documentary", "Drama", "Vlog", "Family", "Travel",
        "Cinematic", "Sports", "Education", "Lifestyle", "Gaming"
    )

    // PDF-specific lists
    private val authors = listOf(
        "System User", "MetaJammer PDF", "Office Worker", "Document Editor", "Administrator",
        "Legal Dept", "HR", "Finance Team", "Automated System", "Guest User", "Consultant"
    )
    private val pdfTitles = listOf(
        "Document", "Report", "Export", "Scanned File", "Summary", "Project Draft", "Meeting Notes",
        "Invoice", "Q3 Report", "User Manual", "Confidential Draft", "Whitepaper", "Research Paper",
        "Itinerary", "Contract Revision", "Syllabus", "Presentation Slides"
    )
    private val creators = listOf(
        "Microsoft Word", "Google Docs", "Adobe PDF Library", "MetaJammer", "LibreOffice", "System Print",
        "Apple Pages", "Foxit PhantomPDF", "LaTeX with hyperref", "Canva", "PDF24 Creator"
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

    fun randomIsoDateTime(): String {
        val now = LocalDateTime.now()
        val year = Random.nextInt(now.year - 3, now.year + 1)
        val month = Random.nextInt(1, 13)
        val day = Random.nextInt(1, 29)
        val hour = Random.nextInt(0, 24)
        val minute = Random.nextInt(0, 60)
        val second = Random.nextInt(0, 60)
        return "%04d%02d%02dT%02d%02d%02d.000Z".format(year, month, day, hour, minute, second)
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
            ?: listOf("Firmware 1.0", "Android 14", "Android 15", "System 2.1", "Version 3.0").random()
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
        return listOf("50", "64", "80", "100", "125", "160", "200", "250", "320", "400", "640", "800", "1600", "3200").random()
    }

    fun randomExposureTime(): String {
        return listOf(
            "1/15", "1/30", "1/40", "1/50", "1/60", "1/80", "1/100", "1/125",
            "1/160", "1/200", "1/250", "1/320", "1/500", "1/800", "1/1000", "1/2000"
        ).random()
    }

    fun randomFNumber(): String {
        return listOf("1.4", "1.8", "2.0", "2.2", "2.4", "2.8", "3.5", "4.0", "5.6", "7.1", "8.0", "11").random()
    }

    fun randomFocalLength(): String {
        return listOf("1.8", "2.2", "4.2", "4.5", "5.4", "6.0", "14.0", "24.0", "35.0", "50.0", "70.0", "85.0", "105.0", "200.0").random()
    }

    fun randomWhiteBalance(): String {
        return listOf("0", "1").random()
    }

    fun randomFlash(): String {
        return listOf("0", "1", "16", "24").random() // added common EXIF flash fired codes
    }

    fun generatePlan(
        mimeType: String? = null,
        existingLat: Double? = null,
        existingLon: Double? = null
    ): MetadataReplacementPlan {
        val make = randomMake()
        val (latitude, longitude) = if (existingLat != null && existingLon != null) {
            val latOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
            val lonOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
            (existingLat + latOffset) to (existingLon + lonOffset)
        } else {
            randomLatLong()
        }

        val basePlan = MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = make,
            model = randomModel(make),
            software = randomSoftware(make),
            imageDescription = randomImageDescription(),
            userComment = randomUserComment(),
            photographicSensitivity = randomPhotographicSensitivity(),
            exposureTime = randomExposureTime(),
            fNumber = randomFNumber(),
            focalLength = randomFocalLength(),
            whiteBalance = randomWhiteBalance(),
            flash = randomFlash(),
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )

        return when {
            mimeType?.startsWith("audio/") == true -> {
                basePlan.copy(
                    title = audioTitles.random(),
                    artist = audioArtists.random(),
                    album = audioAlbums.random(),
                    genre = audioGenres.random(),
                    year = (LocalDateTime.now().year - Random.nextInt(0, 5)).toString(),
                    trackNumber = Random.nextInt(1, 15).toString(),
                    mediaDate = randomIsoDateTime()
                )
            }
            mimeType?.startsWith("video/") == true -> {
                basePlan.copy(
                    title = videoTitles.random(),
                    artist = videoDirectors.random(),
                    genre = videoGenres.random(),
                    year = (LocalDateTime.now().year - Random.nextInt(0, 10)).toString(),
                    mediaDate = randomIsoDateTime()
                )
            }
            mimeType == "application/pdf" -> {
                basePlan.copy(
                    author = authors.random(),
                    pdfTitle = pdfTitles.random(),
                    creator = creators.random(),
                    producer = creators.random(),
                    subject = "General Document",
                    keywords = listOf("clean", "document", "report", "draft", "final", "archive").shuffled().take(3).joinToString(", ")
                )
            }
            else -> basePlan
        }
    }
}