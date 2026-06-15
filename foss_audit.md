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
| **AndroidX WorkManager** | `androidx.work:work-runtime-ktx` | Apache 2.0 | FOSS |
| **Coil** | `io.coil-kt:coil-*` | Apache 2.0 | FOSS |
| **Kotlinx Serialization** | `org.jetbrains.kotlinx:kotlinx-serialization-json` | Apache 2.0 | FOSS |
| **Timber** | `com.jakewharton.timber:timber` | Apache 2.0 | FOSS |
| **JUnit** | `junit:junit` | EPL 2.0 | FOSS |
| **Espresso** | `androidx.test.espresso:espresso-core` | Apache 2.0 | FOSS |

## Map Implementation Audit

The "Map Picker" feature, which is the only feature requiring internet access, is implemented using FOSS components:

- **Leaflet**: Licensed under BSD-2-Clause (FOSS).
- **OpenStreetMap Tiles**: Data is licensed under ODbL (FOSS).
- **WebView**: Uses the system WebView, avoiding proprietary map SDKs like Google Maps.

## Project Assets and Assets Audit

- **Icons**: Uses Material Icons (Apache 2.0).
- **Fonts**: No custom fonts are bundled; it uses system fonts.
- **License**: The project itself is licensed under the **ISC License** ([LICENSE.txt](file:///mnt/HDD1TB/Projects/Android/MetaJammer/LICENSE.txt)).

## Conclusion

The MetaJammer project is **100% FOSS-compliant**. No proprietary SDKs (e.g., Google Play Services, Firebase, AdMob) or non-FOSS libraries were found during the audit.
