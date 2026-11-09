# Security Policy

The MetaJammer project takes the privacy and security of users seriously. MetaJammer is an open-source, air-gapped utility designed to sanitize, scramble, and strip metadata from media files to protect user privacy.

---

## Supported Versions

Only the latest release versions receive security patches and updates. We recommend always running the latest version available on GitHub Releases or F-Droid.

| Version | Supported |
| :--- | :--- |
| `0.5.x` | :white_check_mark: |
| `< 0.5.0` | :x: |

---

## Reporting a Vulnerability

If you discover a security vulnerability, data leakage issue, or fail-closed bypass in MetaJammer, please report it responsibly so that a fix can be developed and released before details are publicly disclosed.

### Preferred Reporting Method

- **GitHub Private Vulnerability Reporting:**  
  Please report vulnerabilities via GitHub's [Private Vulnerability Reporting](https://github.com/GabryDX/MetaJammer/security/advisories/new). This ensures the report is shared only with project maintainers in an encrypted, confidential workspace.

### Alternative Contact

If you cannot submit a report through GitHub Advisories, you may contact the maintainer directly via email:
- **Email:** `gabrydx@hotmail.com`
- **Subject:** `[SECURITY] MetaJammer Vulnerability Report`

### What to Include in Your Report

To help us investigate and reproduce the issue quickly, please include:
1. **Description:** A detailed explanation of the vulnerability and its potential privacy/security impact.
2. **Steps to Reproduce / PoC:** Clear, step-by-step instructions or a minimal reproducible media sample.
3. **Affected Versions:** The version of MetaJammer and Android OS version / API level tested.
4. **Expected vs. Actual Behavior:** What should have occurred versus what actually happened.

### Response & Disclosure Process

- **Acknowledgement:** We will acknowledge receipt of your report within 48 hours.
- **Triage & Remediation:** We will assess severity, verify reproduction, and work on a fix in a private branch.
- **Release & Disclosure:** Once a fix is verified, a patch will be tagged and released on GitHub and F-Droid. An advisory acknowledging your responsible disclosure will be published upon mutual agreement.

---

## Core Security & Privacy Principles

MetaJammer adheres to the **Principle of Least Privilege** and defensive engineering:

1. **Zero Storage Permissions:** MetaJammer declares zero storage permissions (`WRITE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`) across all supported Android versions (API 26–37). Storage operations use Scoped Storage, MediaStore, and the Storage Access Framework (SAF).
2. **100% Air-Gapped (Zero Network Surface):** The app declares NO `android.permission.INTERNET` permission and removes transitive network permissions like `ACCESS_NETWORK_STATE`. The app operates completely offline without network sockets or telemetry.
3. **Strict Fail-Closed Processing:** If processing fails or encounters unexpected file corruption, MetaJammer aborts immediately, purges temporary scratch files, and **never** falls back to exporting unscrubbed media.
4. **Lossless Pure Container Rewriting:** Media formats (JPEG, PNG, HEIC/HEIF, MP4, OGG) are processed directly at the container box and marker level without decoding or re-compressing raster/audio bitstreams.
5. **No Cloud Synchronization:** Android Auto-Backup is explicitly disabled in the manifest to ensure unscrubbed files are never backed up to cloud services.

---

## Detailed Audits & Documentation

For complete technical specifications, threat model analysis, parser hardening, and compliance documentation, please see:

- 🛡️ **[Comprehensive Security & Privacy Audit](docs/SECURITY_AUDIT.md)**: Deep dive into the threat model, parser hardening (XXE, chunk parsers, ISO-BMFF containers), memory safety, and OWASP MASVS compliance.
- ⚖️ **[FOSS Compliance Audit](docs/FOSS_AUDIT.md)**: Full verification of 100% Free and Open Source dependencies, offline datasets, and asset licensing.
