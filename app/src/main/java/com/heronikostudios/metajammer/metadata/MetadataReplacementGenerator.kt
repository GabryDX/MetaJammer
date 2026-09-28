package com.heronikostudios.metajammer.metadata

import com.heronikostudios.metajammer.domain.model.LocationPreset
import com.heronikostudios.metajammer.domain.model.MetadataReplacementPlan
import com.heronikostudios.metajammer.domain.model.PoisoningProfile
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

    private val lensModelsByMake = mapOf(
        "Canon" to listOf("EF 50mm f/1.8 STM", "RF 24-70mm F2.8 L IS USM", "EF 24-105mm f/4L IS II USM", "RF 35mm F1.8 Macro IS STM"),
        "Nikon" to listOf("NIKKOR Z 24-70mm f/4 S", "AF-S DX NIKKOR 35mm f/1.8G", "NIKKOR Z 50mm f/1.8 S", "AF-S NIKKOR 24-120mm f/4G ED VR"),
        "Sony" to listOf("FE 24-70mm F2.8 GM II", "FE 50mm F1.8", "E 16-50mm F3.5-5.6 OSS", "FE 35mm F1.8"),
        "Fujifilm" to listOf("XF18-55mmF2.8-4 R LM OIS", "XF35mmF2 R WR", "XF23mmF2 R WR", "XF16-80mmF4 R OIS WR"),
        "Panasonic" to listOf("LUMIX G VARIO 12-32mm / F3.5-5.6 ASPH.", "LEICA DG SUMMILUX 25mm / F1.4 II ASPH.", "LUMIX S 20-60mm F3.5-5.6"),
        "Olympus" to listOf("M.ZUIKO DIGITAL ED 12-40mm F2.8 PRO", "M.ZUIKO DIGITAL 25mm F1.8", "M.ZUIKO DIGITAL ED 14-42mm F3.5-5.6 EZ"),
        "Apple" to listOf("iPhone 15 Pro back triple camera 6.86mm f/1.78", "iPhone 14 back main camera 5.7mm f/1.5"),
        "Samsung" to listOf("Galaxy S23 Ultra back camera 6.3mm f/1.7", "Galaxy S22 back camera 5.4mm f/1.8"),
        "Google" to listOf("Pixel 8 Pro back camera 6.9mm f/1.68", "Pixel 7 back camera 6.81mm f/1.85"),
        "DJI" to listOf("DJI Mini 3 Pro Lens", "Mavic 3 Hasselblad L2D-20c"),
        "Leica" to listOf("Summilux 28mm f/1.7 ASPH", "Summicron-M 35mm f/2 ASPH"),
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

    fun randomLensMake(make: String): String = make

    fun randomLensModel(make: String): String {
        return lensModelsByMake[make]?.random() ?: "$make Lens ${Random.nextInt(10, 100)}mm"
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
        existingLon: Double? = null,
        profile: PoisoningProfile = PoisoningProfile.RANDOM,
        locationPreset: LocationPreset = LocationPreset.RANDOM
    ): MetadataReplacementPlan {
        val (latitude, longitude) = when {
            locationPreset != LocationPreset.RANDOM && locationPreset.latitude != null && locationPreset.longitude != null -> {
                val latJitter = Random.nextDouble(-0.0008, 0.0008)
                val lonJitter = Random.nextDouble(-0.0008, 0.0008)
                (locationPreset.latitude + latJitter) to (locationPreset.longitude + lonJitter)
            }
            existingLat != null && existingLon != null -> {
                val latOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
                val lonOffset = (Random.nextDouble(0.002, 0.01) * if (Random.nextBoolean()) 1 else -1)
                (existingLat + latOffset) to (existingLon + lonOffset)
            }
            else -> randomLatLong()
        }

        val basePlan = when (profile) {
            PoisoningProfile.PRO_MIRRORLESS -> generateProMirrorlessPlan(latitude, longitude)
            PoisoningProfile.MODERN_SMARTPHONE -> generateModernSmartphonePlan(latitude, longitude)
            PoisoningProfile.VINTAGE_DIGITAL -> generateVintageDigitalPlan(latitude, longitude)
            PoisoningProfile.ACTION_CAM -> generateActionCamPlan(latitude, longitude)
            PoisoningProfile.ANONYMOUS_MINIMAL -> generateAnonymousMinimalPlan(latitude, longitude)
            PoisoningProfile.RANDOM -> generateRandomPlan(latitude, longitude)
        }

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

    private fun generateRandomPlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        val make = randomMake()
        return MetadataReplacementPlan(
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
            lensMake = randomLensMake(make),
            lensModel = randomLensModel(make),
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }

    private fun generateProMirrorlessPlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        val make = listOf("Sony", "Canon", "Nikon", "Fujifilm").random()
        val (model, software, lensModel) = when (make) {
            "Sony" -> Triple(
                listOf("ILCE-7M4", "ILCE-7RM5", "ILCE-1").random(),
                listOf("ILCE-7M4 v2.00", "Creator's App 2.1").random(),
                listOf("FE 24-70mm F2.8 GM II", "FE 50mm F1.2 GM", "FE 70-200mm F2.8 GM OSS II").random()
            )
            "Canon" -> Triple(
                listOf("EOS R5", "EOS R6 Mark II", "EOS R3").random(),
                listOf("Firmware 1.8.1", "Digital Photo Professional 4.18").random(),
                listOf("RF 24-70mm F2.8 L IS USM", "RF 50mm F1.2 L USM", "RF 70-200mm F2.8 L IS USM").random()
            )
            "Nikon" -> Triple(
                listOf("Z8", "Z9", "Z6 III").random(),
                listOf("Ver.2.00", "NX Studio 1.6").random(),
                listOf("NIKKOR Z 24-70mm f/2.8 S", "NIKKOR Z 50mm f/1.2 S").random()
            )
            else -> Triple(
                listOf("X-T5", "GFX 100S II").random(),
                "FUJIFILM X RAW STUDIO",
                listOf("XF16-55mmF2.8 R LM WR", "XF56mmF1.2 R WR").random()
            )
        }

        return MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = make,
            model = model,
            software = software,
            imageDescription = listOf("Fine art landscape", "Portrait session", "Golden hour studio capture", "City architecture study").random(),
            userComment = "High-resolution RAW developed with natural tone curve",
            photographicSensitivity = listOf("100", "160", "200", "400", "800", "1600").random(),
            exposureTime = listOf("1/250", "1/500", "1/1000", "1/2000", "1/4000").random(),
            fNumber = listOf("1.4", "1.8", "2.0", "2.8", "4.0").random(),
            focalLength = listOf("24.0", "35.0", "50.0", "70.0", "85.0", "135.0").random(),
            whiteBalance = "0",
            flash = "16",
            lensMake = make,
            lensModel = lensModel,
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }

    private data class Five<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)

    private fun generateModernSmartphonePlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        val make = listOf("Apple", "Google", "Samsung").random()
        val (model, software, lensModel, focalLength, fNumber) = when (make) {
            "Apple" -> {
                val isMax = Random.nextBoolean()
                val m = if (isMax) "iPhone 15 Pro Max" else "iPhone 15 Pro"
                val sw = listOf("17.5.1", "18.0", "18.1").random()
                val lens = "$m back triple camera 6.86mm f/1.78"
                Five(m, sw, lens, "6.86", "1.78")
            }
            "Google" -> {
                val m = listOf("Pixel 8 Pro", "Pixel 9 Pro").random()
                val sw = listOf("Android 14", "Android 15", "AP1A.240405.002").random()
                val lens = "$m back camera 6.9mm f/1.68"
                Five(m, sw, lens, "6.9", "1.68")
            }
            else -> {
                val m = listOf("SM-S928B", "SM-S918B").random()
                val sw = listOf("One UI 6.0", "One UI 6.1").random()
                val lens = "Galaxy S24 Ultra back camera 6.3mm f/1.7"
                Five(m, sw, lens, "6.3", "1.7")
            }
        }

        return MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = make,
            model = model,
            software = software,
            imageDescription = randomImageDescription(),
            userComment = randomUserComment(),
            photographicSensitivity = listOf("50", "64", "100", "125", "250").random(),
            exposureTime = listOf("1/40", "1/60", "1/120", "1/240", "1/500").random(),
            fNumber = fNumber,
            focalLength = focalLength,
            whiteBalance = "0",
            flash = "16",
            lensMake = make,
            lensModel = lensModel,
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }

    private fun generateVintageDigitalPlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        val make = listOf("Olympus", "Canon", "Nikon", "Sony").random()
        val model = when (make) {
            "Olympus" -> listOf("C-2000Z", "C-3040Z", "CAMEDIA C-2500L").random()
            "Canon" -> listOf("PowerShot G1", "PowerShot G2", "PowerShot Pro90 IS").random()
            "Nikon" -> listOf("COOLPIX 990", "COOLPIX 995", "COOLPIX 5000").random()
            else -> listOf("Cyber-shot DSC-F707", "Cyber-shot DSC-S75").random()
        }

        return MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = make,
            model = model,
            software = "Ver 1.0",
            imageDescription = "OLYMPUS DIGITAL CAMERA",
            userComment = "",
            photographicSensitivity = listOf("64", "100", "200").random(),
            exposureTime = listOf("1/30", "1/60", "1/125", "1/250").random(),
            fNumber = listOf("2.0", "2.5", "2.8", "4.0").random(),
            focalLength = listOf("6.5", "7.0", "14.2", "19.5").random(),
            whiteBalance = "0",
            flash = listOf("0", "1").random(),
            lensMake = make,
            lensModel = "$make Optical Zoom Lens",
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }

    private fun generateActionCamPlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        val make = listOf("GoPro", "DJI").random()
        val (model, software, lensModel, focalLength, fNumber) = if (make == "GoPro") {
            Five("HERO12 Black", "HD12.01.10.00", "GoPro Ultra Wide Lens 2.47mm f/2.5", "2.47", "2.5")
        } else {
            Five("Osmo Action 4", "Firmware 01.02.0000", "DJI Action Wide Lens 2.6mm f/2.8", "2.6", "2.8")
        }

        return MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = make,
            model = model,
            software = software,
            imageDescription = listOf("Action POV capture", "Extreme wide angle", "Mountain run", "Outdoor session").random(),
            userComment = "Recorded with electronic image stabilization",
            photographicSensitivity = listOf("100", "200", "400").random(),
            exposureTime = listOf("1/120", "1/240", "1/480", "1/960").random(),
            fNumber = fNumber,
            focalLength = focalLength,
            whiteBalance = "0",
            flash = "0",
            lensMake = make,
            lensModel = lensModel,
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }

    private fun generateAnonymousMinimalPlan(latitude: Double, longitude: Double): MetadataReplacementPlan {
        return MetadataReplacementPlan(
            dateTime = randomRecentDateTime(),
            make = "Digital Camera",
            model = "Standard",
            software = "1.0",
            imageDescription = "",
            userComment = "",
            photographicSensitivity = "100",
            exposureTime = "1/125",
            fNumber = "2.8",
            focalLength = "35.0",
            whiteBalance = "0",
            flash = "0",
            lensMake = null,
            lensModel = null,
            latitude = latitude,
            longitude = longitude,
            latitudeRef = if (latitude >= 0) "N" else "S",
            longitudeRef = if (longitude >= 0) "E" else "W"
        )
    }
}