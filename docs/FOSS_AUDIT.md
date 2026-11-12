# FOSS Compliance Audit

This document summarizes the audit performed to ensure that MetaJammer only uses Free and Open Source Software (FOSS) dependencies.

## Dependency Audit

| Library | Group/Name | License | Type |
| :--- | :--- | :--- | :--- |
| **AndroidX AppCompat** | `androidx.appcompat:appcompat` | Apache 2.0 | FOSS |
| **AndroidX Core KTX** | `androidx.core:core-ktx` | Apache 2.0 | FOSS |
| **AndroidX DataStore** | `androidx.datastore:datastore-preferences` | Apache 2.0 | FOSS |
| **AndroidX DocumentFile** | `androidx.documentfile:documentfile` | Apache 2.0 | FOSS |
| **AndroidX ExifInterface** | `androidx.exifinterface:exifinterface` | Apache 2.0 | FOSS |
| **AndroidX Lifecycle** | `androidx.lifecycle:*` | Apache 2.0 | FOSS |
| **AndroidX Activity Compose** | `androidx.activity:activity-compose` | Apache 2.0 | FOSS |
| **Jetpack Compose BOM** | `androidx.compose:compose-bom` | Apache 2.0 | FOSS |
| **Jetpack Compose UI** | `androidx.compose.ui:*` | Apache 2.0 | FOSS |
| **Jetpack Compose Material3** | `androidx.compose.material3:material3` | Apache 2.0 | FOSS |
| **Jetpack Compose Icons** | `androidx.compose.material:material-icons-core` | Apache 2.0 | FOSS |
| **AndroidX Navigation Compose** | `androidx.navigation:navigation-compose` | Apache 2.0 | FOSS |
| **AndroidX Room** | `androidx.room:room-*` | Apache 2.0 | FOSS |
| **AndroidX WorkManager** | `androidx.work:work-runtime-ktx` | Apache 2.0 | FOSS |
| **Koin** | `io.insert-koin:koin-*` | Apache 2.0 | FOSS |
| **Coil** | `io.coil-kt:coil-*` | Apache 2.0 | FOSS |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | Apache 2.0 | FOSS |
| **Timber** | `com.jakewharton.timber:timber` | Apache 2.0 | FOSS |
| **PDFBox-Android** | `com.tom-roush:pdfbox-android` | Apache 2.0 | FOSS |
| **Kotlinx Coroutines Test** | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache 2.0 | FOSS |
| **JUnit** | `junit:junit` | EPL 2.0 | FOSS |
| **AndroidX Test Ext** | `androidx.test.ext:junit` | Apache 2.0 | FOSS |
| **Espresso** | `androidx.test.espresso:espresso-core` | Apache 2.0 | FOSS |
| **Robolectric** | `org.robolectric:robolectric` | MIT | FOSS |

## Map Implementation Audit

The Location Picker and Custom Location Preset features are 100% offline and air-gapped, requiring **zero network permissions**:

- **Native Compose Vector Map**: Renders an interactive equirectangular world map directly via hardware-accelerated Jetpack Compose Canvas, completely eliminating WebViews and JavaScript engines.
- **Natural Earth Data**: Landmass boundaries are sourced from Natural Earth 110m physical land polygons (Public Domain / CC0), stored as a clean, human-readable JSON dataset (`app/src/main/assets/map/world_land.json`) parsed via FOSS `kotlinx.serialization` with zero opaque binary blobs.
- **Offline & Custom Location Presets**: Users can select built-in landmarks or define custom location presets visually on the vector map or via decimal coordinates. Custom presets are serialized using FOSS `kotlinx-serialization-json` and stored locally in AndroidX DataStore preferences without network lookups or external geocoding.
- **Zero Network Activity**: No tiles, APIs, or network requests are ever dispatched. MetaJammer requires no `INTERNET` permission and carries zero F-Droid Anti-Features (`TetheredNet` completely eliminated).

## Container Engine & Parser Audit

All core media parsers and container scrubbers are implemented entirely in pure Kotlin using standard library APIs, eliminating external binary libraries, native code, and closed codecs:
- **HEIC / HEIF**: ISO-BMFF box parser (`HeicMetadataProcessor.kt`) with zero recompression.
- **MP4 / MOV / M4A**: Direct ISO-BMFF atom parser and box scrubber (`Mp4MetadataReader.kt`).
- **MP3**: Stream-level ID3v2/v1 and C2PA manifest parser and stripper (`Mp3MetadataReader.kt`).
- **OGG Vorbis & Opus**: Pure Kotlin page parser and Vorbis comment packet reassembler (`OggMetadataReader.kt`).
- **PNG & SVG**: Streaming chunk parser (`PngMetadataReader.kt`) and secure XML parser (`SvgMetadataProcessor.kt`).

## Project Assets and Licensing Audit

- **Icons**: Uses Material Icons (Apache 2.0).
- **Fonts**: No custom fonts are bundled; it uses system fonts.
- **Packaging Hygiene**: Unused third-party auxiliary files (BouncyCastle PQC tables and properties, OkHttp publicsuffix lists) are strictly stripped during packaging, maintaining minimal attack surface and clean FOSS binaries.
- **License**: The project itself is licensed under the **ISC License** ([LICENSE.txt](../LICENSE.txt)).

## Conclusion

The MetaJammer project is **100% FOSS-compliant**. No proprietary SDKs (e.g., Google Play Services, Firebase, AdMob) or non-FOSS libraries are used in the project.

For an in-depth analysis of the application's threat model, data protection architecture, and parser hardening, refer to the [Security & Privacy Audit](SECURITY_AUDIT.md).
