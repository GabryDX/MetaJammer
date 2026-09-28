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
| **Robolectric** | `org.robolectric:robolectric` | Apache 2.0 | FOSS |

## Map Implementation Audit

The "Map Picker" feature, which is the only feature with network capability, is implemented using FOSS components:

- **Leaflet**: Licensed under BSD-2-Clause (FOSS). Bundled locally in `app/src/main/assets/leaflet/` to eliminate external CDN dependencies and ensure zero network activity on map initialization.
- **OpenStreetMap Tiles**: Map data is licensed under the Open Data Commons Open Database License (ODbL) (FOSS). Tile loading is strictly opt-in and requires explicit user consent.
- **WebView**: Uses the system Android WebView, completely avoiding proprietary map SDKs like Google Maps.

## Project Assets and Licensing Audit

- **Icons**: Uses Material Icons (Apache 2.0).
- **Fonts**: No custom fonts are bundled; it uses system fonts.
- **Packaging Hygiene**: Unused third-party auxiliary files (BouncyCastle PQC tables and properties, OkHttp publicsuffix lists) are strictly stripped during packaging, maintaining minimal attack surface and clean FOSS binaries.
- **License**: The project itself is licensed under the **ISC License** ([LICENSE.txt](LICENSE.txt)).

## Conclusion

The MetaJammer project is **100% FOSS-compliant**. No proprietary SDKs (e.g., Google Play Services, Firebase, AdMob) or non-FOSS libraries are used in the project.

For an in-depth analysis of the application's threat model, data protection architecture, and parser hardening, refer to the [Security & Privacy Audit](security_audit.md).
