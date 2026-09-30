# Security & Privacy Audit Report

This document details the security and privacy architecture, threat model analysis, and vulnerability mitigation strategies implemented across the **MetaJammer** application.

---

## 1. Executive Summary

MetaJammer is a privacy-focused Android utility engineered to sanitize, scramble, or synthesize metadata in images, videos, audio containers, and PDF documents.

The primary security objective of MetaJammer is **guaranteed non-leakage**: ensuring that sensitive user data (including precise geolocation, device serial numbers, timestamps, author identities, and hidden document annotations) is completely neutralized before media leaves the user's custody.

### Core Security Posture

| Category | Status | Details |
| :--- | :--- | :--- |
| **Permissions** | **Least Privilege** | Zero broad storage permissions (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` not requested). Uses Storage Access Framework (SAF) and Scoped Storage. |
| **Telemetry / Tracking** | **Zero Exposure** | 100% Free and Open Source Software (FOSS). Zero analytics, zero ad SDKs, zero crash-reporting daemons, and zero "phone-home" beacons. |
| **Network Surface** | **Zero Network (Air-Gapped)** | Zero network permissions (`android.permission.INTERNET` removed). 100% offline Compose vector map with bundled Natural Earth dataset. |
| **Data Leak Prevention** | **Fail-Closed** | Processing failures abort immediately; unscrubbed original media is never inadvertently exported or shared. Android Auto-Backup is explicitly disabled. |
| **Inter-Process Security** | **Hardened** | `FileProvider` is unexported and scoped to strict subdirectories. Incoming URIs are validated through single-pass extraction. |

---

## 2. Threat Model & Security Objectives

### 2.1 Assets Protected
1. **Device & User Identifiers:** Camera make, model, firmware version, serial numbers, lens specifications, software versions.
2. **Location Data:** GPS latitude, longitude, altitude, speed, direction, geodetic datum, and location names.
3. **Temporal Data:** Creation date, modification date, digitized timestamps, sub-second offsets, and timezone indicators.
4. **Document Artifacts:** Authors, titles, producers, edit histories, hidden watermarks, stamps, and Optional Content Groups (layers).
5. **Original Files:** Unscrubbed input files must never be leaked, exposed via IPC, or sent to cloud backups.

### 2.2 Adversary Models
* **Data Brokers & Aggregators:** Platforms that harvest EXIF/XMP/IPTC data from uploaded media to profile users or track physical locations.
* **Malicious Media Inputs:** Crafted media files attempting path traversal, XML External Entity (XXE) attacks, XML entity expansion (Billion Laughs), buffer overflows, or arbitrary code execution.
* **Snooping Applications:** Local applications monitoring intent broadcasts or trying to exploit exported Android components.
* **Cloud Backup Interception:** Automatic OS cloud backup services silently capturing unscrubbed media during local processing.

---

## 3. Data Protection & Privacy Architecture

### 3.1 Principle of Least Privilege (Permissions)
* **Zero Storage Permissions:** MetaJammer does not declare or request `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` on any Android version (API 26–36).
* **Storage Access Framework (SAF) & MediaStore:** User file selections and exports occur through system-mediated pickers (`ActivityResultContracts.OpenMultipleDocuments`, `OpenDocumentTree`, and `MediaStore`), guaranteeing that the application only accesses files explicitly chosen by the user.
* **Scoped Notification Permission:** `POST_NOTIFICATIONS` is guarded by runtime API-level checks (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU`) and only used for background batch progress updates.

### 3.2 Network Isolation & Air-Gapped Architecture
* **Zero Network Permissions:** `android.permission.INTERNET` is completely absent from `AndroidManifest.xml`. MetaJammer is an air-gapped application that cannot make network connections.
* **100% Offline Vector Map:** The Location Picker uses a native Jetpack Compose Canvas rendering engine with a pre-compiled 20 KB binary land polygon dataset from Natural Earth (Public Domain). No WebViews, no JavaScript, and no external tile servers.
* **F-Droid Anti-Feature Elimination:** Fully compliant with F-Droid inclusion policies without requiring the `TetheredNet` anti-feature flag.
* **Offline Landmark & Custom Location Presets:** Pre-defined landmark coordinates (e.g., Tokyo, London, Paris, New York) and user-defined custom location presets function 100% offline. Custom presets are serialized via Kotlinx Serialization and persisted in application-private AndroidX DataStore preferences with zero network lookups, zero reverse-geocoding, and zero cloud synchronization.

### 3.3 Cloud Backup Elimination
Android Auto-Backup is strictly disabled in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml) to prevent sensitive or unscrubbed files from leaking to cloud storage:
```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:fullBackupContent="@xml/backup_rules"
```

### 3.4 Isolated Cache & Automatic Lifecycle Purging
* Processing occurs within isolated application sandbox directories (`context.cacheDir`).
* Temporary working files and outgoing share packages (`shared/`, `outgoing_shares/`) are cleaned up automatically upon session completion and purged during startup.
* Navigating away or returning to Home from the Output screen without saving or sharing automatically purges temporary processed files from disk, preventing stale or abandoned media from lingering in flash memory.

### 3.5 History Retention & Audit Log Privacy
* History records are maintained in a local Room database (`MetaJammerDatabase`).
* Configurable retention policies (`CLEAR_AFTER_24_HOURS`, `KEEP_100_ITEMS`, `CLEAR_ON_EXIT`, `DISABLED`) ensure processing records do not accumulate indefinitely.
* History entries store only sanitized display names and timestamp summaries—no image thumbnails, raw byte streams, or sensitive file paths are persisted.

---

## 4. Input Validation & Processor Hardening

### 4.1 Path Traversal & Filename Sanitization ([`SanitizationUtils.kt`](app/src/main/java/com/heronikostudios/metajammer/util/SanitizationUtils.kt))
To prevent directory traversal attacks (e.g., `../../system/`) or filesystem-level command injections:
* Filenames are stripped of path delimiters (`/`, `\`), control characters (`\u0000`–`\u001F`), and reserved filesystem characters (`:`, `*`, `?`, `"`, `<`, `>`, `|`).
* Leading dot prefixes (`.`) are sanitized to prevent unauthorized hidden file creation.
* Blank or all-period filenames fall back safely to timestamp-based unique identifiers (`file_<timestamp>`).
* Full international Unicode alphabets, accents, and UTF-8 characters are preserved without compromising path security.

### 4.2 Fail-Closed Error Handling
MetaJammer enforces a **fail-closed** architecture:
* If a file processor encounters an error, parsing exception, or malformed container, the operation aborts immediately.
* Any partially created temporary file is deleted from disk.
* The application **never** falls back to returning or sharing the unscrubbed original file.

### 4.3 XML / SVG Parser Hardening ([`SvgMetadataProcessor.kt`](app/src/main/java/com/heronikostudios/metajammer/metadata/SvgMetadataProcessor.kt))
To defend against XML External Entity (XXE), Billion Laughs entity expansion, and SSRF attacks:
* The DOM `DocumentBuilderFactory` is configured with strict security features:
  - `disallow-doctype-decl`: Prohibits inline DTD declarations.
  - `external-general-entities`: Disabled.
  - `external-parameter-entities`: Disabled.
  - `load-external-dtd`: Disabled.
  - `isXIncludeAware = false` and `isExpandEntityReferences = false`.
* **Sanitization:** Strips `<metadata>`, `<desc>`, `<title>`, `<rdf:RDF>`, editor metadata (`sodipodi`, `inkscape`), and HTML comment blocks (`<!-- ... -->`).
* **Embedded Raster Scrubbing:** Base64-encoded raster images embedded within `<image>` tags are decoded, scrubbed via the native image processor, and re-encoded.

### 4.4 Deep Image Stripping & In-Memory Chunk Parsing ([`ImageMetadataProcessor.kt`](app/src/main/java/com/heronikostudios/metajammer/metadata/ImageMetadataProcessor.kt))
* **ExifInterface Reflection:** Targets all standard `TAG_` constants dynamically via reflection plus vendor-specific tags (`ImageResources`, `OwnerName`, `PrintIM`, `SensitivityType`, etc.) to clear all metadata fields regardless of library version.
* **JPEG Marker Stripping:** Drops APPn metadata markers (APP1 EXIF/XMP, APP2 ICC/FlashPix, APP13 IPTC, COM) directly from the byte stream without recompressing raster scan data.
* **PNG Ancillary Chunk Stripping:** In-memory byte scanner strips `tEXt`, `zTXt`, `iTXt`, `pHYs`, and `eXIf` chunks, preserving only critical image rendering chunks (`IHDR`, `PLTE`, `IDAT`, `IEND`). Direct stream-to-file fast-path bypasses `ExifInterface` rewrites and eliminates intermediate disk copies entirely.
* **Thumbnail Elimination:** Strips embedded EXIF preview thumbnails to prevent visual data leakage of cropped or removed sections.

### 4.5 PDF Document Sanitization ([`PdfMetadataProcessor.kt`](app/src/main/java/com/heronikostudios/metajammer/metadata/PdfMetadataProcessor.kt))
* **In-Memory Scrubbing:** Configured with `MemoryUsageSetting.setupMixed(10MB)`—all standard PDF documents under 10MB are parsed and scrubbed strictly in RAM with zero disk scratch files created, preventing unscrubbed document fragments from touching physical flash storage.
* **Document Information Dictionary:** Replaces `PDDocumentInformation` with an empty structure (wipes Title, Author, Subject, Keywords, Creator, Producer, CreationDate, ModDate).
* **Catalog XMP Metadata:** Nullifies `documentCatalog.metadata`.
* **Watermark & Annotation Purging:** Iterates over all document pages to strip `Watermark` and `Stamp` annotations.
* **Layer Sanitization:** Clears Optional Content Groups (OCGs) to prevent hidden watermark layers from persisting.

### 4.6 Audio / Video Stream Remuxing ([`MediaMetadataProcessor.kt`](app/src/main/java/com/heronikostudios/metajammer/metadata/MediaMetadataProcessor.kt))
* **Container Remuxing:** Re-muxes MP4, MOV, and M4A containers at the sample level using `MediaExtractor` and `MediaMuxer`.
* **Atom Stripping:** Automatically drops location atoms (`loci`), user data atoms (`udta`), and custom encoder tags.
* **Orientation Preservation:** Retains video orientation hint (`setOrientationHint`) to maintain visual layout without re-encoding video streams.

---

## 5. Android Component & IPC Security

### 5.1 Component Exposure

| Component | Exported | Intent Filter | Security Controls |
| :--- | :--- | :--- | :--- |
| `MainActivity` | `true` | `MAIN`, `LAUNCHER` | Standard launcher entry point. |
| `QuickScrubActivity` | `true` | `SEND`, `SEND_MULTIPLE` | Standalone HUD activity. Validates incoming intent URIs; does not expose internal state. |
| `FileProvider` | `false` | None | Grants temporary read-only permissions via `FLAG_GRANT_READ_URI_PERMISSION`. |
| `InitializationProvider` | `false` | None | Internal startup provider. |

### 5.2 FileProvider Hardening ([`file_paths.xml`](app/src/main/res/xml/file_paths.xml))
`FileProvider` access is strictly confined to dedicated subdirectories:
```xml
<paths>
    <cache-path name="shared_cache" path="shared/" />
    <files-path name="shared_files" path="shared/" />
</paths>
```
* **No Root Exposure:** The root filesystem, external storage, and internal app directories are **not** accessible via `FileProvider`.
* **Scoped Sharing:** Shared files are stored exclusively within `context.cacheDir/shared/`, granting other apps temporary read access only when explicitly shared via `Intent.createChooser`.

### 5.3 Intent Validation ([`IntentUtils.kt`](app/src/main/java/com/heronikostudios/metajammer/util/IntentUtils.kt))
* Validates incoming intent action (`ACTION_SEND`, `ACTION_SEND_MULTIPLE`).
* Checks URI schemes (`content://`, `file://`), deduplicates targets, and handles security exceptions when reading external content URIs.

---

## 6. Packaging & Supply Chain Security

### 6.1 Dependency Auditing
* **100% FOSS:** Every dependency is audited for open-source compliance (Apache 2.0, MIT, BSD-2-Clause, EPL 2.0).
* **Attack Surface Reduction:** Stripped unused BouncyCastle post-quantum crypto tables (`org/bouncycastle/pqc/**`), residual properties (`org/bouncycastle/x509/*.properties`), and unused OkHttp public suffix assets (`okhttp3/internal/publicsuffix/**`) from release APK packaging, reducing binary size by ~44% (8.9MB to 5.0MB) and eliminating unused cryptographic and network parsing code.

### 6.2 Code Shrinking & Obfuscation
* Release builds employ **R8** minification to shrink dead code, optimize byte-code, and remove unused classes.

### 6.3 APK Authenticity & Signing Verification
Users can verify official release builds using [AppVerifier](https://github.com/soupslurpr/AppVerifier):
* **Package Name:** `com.heronikostudios.metajammer`
* **Release Signing SHA-256:**
  ```
  36:D6:9B:D7:8C:8A:44:90:C2:BC:3F:53:29:6A:BD:68:88:7E:2A:50:AD:9B:9D:A1:C3:6C:CC:D6:4E:96:AF:01
  ```

---

## 7. OWASP Mobile Application Security (MASVS) Checklist

| Category | Requirement | Compliance | Evidence / Implementation |
| :--- | :--- | :---: | :--- |
| **MASVS-STORAGE** | System credential store & sensitive data at rest | **PASS** | No sensitive credentials stored. Processed history uses Room with automated pruning; custom location presets are kept in application-private DataStore; raw media is never stored permanently. |
| **MASVS-STORAGE** | No sensitive data written to application logs | **PASS** | Timber logging suppresses verbose file contents; logs only contain metadata keys and debug status. |
| **MASVS-STORAGE** | Auto-backup disabled | **PASS** | `android:allowBackup="false"` and explicit `dataExtractionRules`. |
| **MASVS-CRYPTO** | Industry-standard cryptographic algorithms | **PASS** | Standard platform hashing and PRNGs used; no proprietary or weak home-grown cryptography. |
| **MASVS-NETWORK** | Network attack surface minimized | **PASS** | Complete network isolation: zero network permissions (`INTERNET` absent), air-gapped architecture. |
| **MASVS-PLATFORM** | Permissions minimized | **PASS** | Zero broad storage permissions (`READ/WRITE_EXTERNAL_STORAGE` absent). Zero network permissions (`INTERNET` absent). Only `POST_NOTIFICATIONS` (API 33+) for background batch status. |
| **MASVS-PLATFORM** | IPC components properly protected | **PASS** | `FileProvider` unexported with narrow path whitelist; incoming URIs sanitized. |
| **MASVS-CODE** | Input validation & parser hardening | **PASS** | Safe XML parsing (XXE protected), regex-based filename sanitization, fail-closed processor exception handling. |

---

## 8. Conclusion

MetaJammer adheres strictly to privacy-by-design and least-privilege principles. By eliminating tracking SDKs, disabling cloud auto-backup, isolating file sharing through narrowed `FileProvider` paths, enforcing fail-closed processing, and hardening parsers against malicious inputs, MetaJammer provides a robust and secure tool for personal metadata privacy.
