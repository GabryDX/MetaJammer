# Privacy Policy for MetaJammer

**Last Updated:** November 10, 2026  
**Developer:** HeroNikoStudios / Gabriele N.  
**Contact:** heronikostudios@gmail.com  
**Source Code:** [https://github.com/GabryDX/MetaJammer](https://github.com/GabryDX/MetaJammer)

---

## 1. Overview & Core Commitment

**MetaJammer** is an open-source, air-gapped privacy utility designed to strip, scramble, or sanitize metadata from media files (photos, videos, audio containers, and PDF documents).

Our core privacy commitment is absolute: **MetaJammer does not collect, transmit, track, profile, store remotely, or share any personal data, device information, or media content.**

MetaJammer is built on the **Principle of Least Privilege**:
* **Zero Network Access:** MetaJammer requests **no internet permission** (`android.permission.INTERNET` is completely absent from the application manifest). The application is fundamentally incapable of opening network connections, sending telemetry, or transmitting user media off the device.
* **100% On-Device Processing:** All metadata inspection, stripping, poisoning, and container remuxing occur strictly in local device memory (`RAM`) within the application's isolated sandbox.
* **Zero Third-Party SDKs or Trackers:** MetaJammer contains zero advertising SDKs, zero analytics trackers, zero crash-reporting daemons, and zero proprietary Google Play Services dependencies.

---

## 2. Information Handled by the Application

### 2.1 Media Files & Metadata
* **User-Selected Files:** When you select photos, videos, audio tracks, or PDF documents to inspect or sanitize, MetaJammer reads the media stream solely to detect, remove, or modify metadata tags (such as GPS coordinates, device serial numbers, camera settings, author identities, or hidden annotations).
* **Temporary Processing Cache:** During processing, temporary files may be created within the application's private, sandboxed cache directory (`context.cacheDir`). These working files are inaccessible to other applications and are automatically purged upon session completion, application restart, or when returning to the Home screen.
* **No Cloud Storage or Remote Backups:** Android Auto-Backup is explicitly disabled (`android:allowBackup="false"`). Your raw and unstripped files are never uploaded to Google Drive, cloud backups, or any remote servers.

### 2.2 Processing History (Optional)
* MetaJammer includes an optional local Processing History log that records timestamps and file names of recently sanitized media to help you verify what was cleaned.
* This history log is stored strictly on your device inside an application-private SQLite database (Room).
* History entries contain **no image thumbnails, no raw file payloads, and no sensitive file paths**.
* You can configure retention policies (`Keep 100 items`, `Clear after 24 hours`, `Clear on exit`, or `Disabled`) and purge the log at any time with one tap from Settings.

### 2.3 Custom Location Presets
* If you use the Location Picker to select fake coordinates for metadata poisoning, you can save custom location presets (e.g., custom coordinates).
* Presets are stored locally on your device in AndroidX DataStore preferences. No network reverse-geocoding or external map APIs are ever used.

---

## 3. Device Permissions

MetaJammer requests only the absolute minimum permissions necessary to perform its functions on Android:

| Permission | Purpose | Why It Is Safe |
| :--- | :--- | :--- |
| `POST_NOTIFICATIONS` | Displays background progress and completion notifications during large multi-file batch operations. | Only requested on Android 13+ (API 33+). Can be denied without breaking any metadata stripping features. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_DATA_SYNC` | Keeps background batch processing alive when sanitizing 50+ high-resolution files while you switch apps. | Android 14+ requirement for WorkManager background batch tasks; displays an ongoing system notification during processing. |
| **Storage Permissions (`READ/WRITE_EXTERNAL_STORAGE`)** | **NOT REQUESTED** | MetaJammer declares **zero storage permissions**. File access uses modern Scoped Storage, MediaStore, and the Storage Access Framework (SAF). |
| **Internet (`INTERNET`)** | **NOT REQUESTED** | Completely absent from the manifest. Network access is physically blocked at the operating system level. |

---

## 4. Third-Party Sharing and Disclosures

* **We do not sell, rent, or trade your data.**
* **We do not share any data with third parties.**
* When you choose to export or re-share a sanitized file using the Android system share sheet, MetaJammer grants temporary, read-only access to that single file via a secured, unexported Android `FileProvider` (`context.cacheDir/shared/`) solely to the destination app you explicitly choose.

---

## 5. Security Measures

MetaJammer employs robust security engineering:
* **Fail-Closed Processing:** If an error or malformed container is encountered during processing, MetaJammer immediately aborts and wipes temporary files—unscrubbed media is **never** inadvertently exported or returned.
* **Parser Hardening:** XML and SVG parsers explicitly prohibit external entity resolution (XXE protection) and entity expansion.
* **Memory Protection:** Concurrency is bounded to prevent memory exhaustion, and sensitive metadata extents are overwritten with zeros (`0x00`).

---

## 6. Children's Privacy

MetaJammer is a general utility tool and does not knowingly collect, request, or solicit any personal information from children under the age of 13 (or under 16 in certain jurisdictions).

---

## 7. Open Source Verification

MetaJammer is 100% Free and Open Source Software (FOSS) licensed under the **ISC License**. Any security researcher or user can independently inspect, audit, and compile the entire source code from our public repository:
[https://github.com/GabryDX/MetaJammer](https://github.com/GabryDX/MetaJammer)

---

## 8. Changes to This Privacy Policy

If we update this Privacy Policy, the revised version will be published in this repository with an updated "Last Updated" date. Because MetaJammer does not collect contact details or maintain network connections, we encourage users to review the policy on GitHub periodically.

---

## 9. Contact Us

If you have any questions, feedback, or concerns regarding this Privacy Policy or MetaJammer's privacy practices, please contact:

* **Maintainer:** HeroNikoStudios / Gabriele N.
* **Email:** heronikostudios@gmail.com
* **Issue Tracker:** [https://github.com/GabryDX/MetaJammer/issues](https://github.com/GabryDX/MetaJammer/issues)
