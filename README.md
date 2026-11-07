# RE4 BIN TOOLS — Android

An independent Android application for extracting and repacking Resident Evil 4 Original (PS2) BIN model files.

## Overview

RE4 BIN TOOLS brings BIN extraction and model repacking workflows to Android. It supports OBJ and SMD model formats and is developed in Java.

## Features

### BIN Extractor
- Extracts model geometry from compatible RE4 PS2 BIN files.
- Exports OBJ, MTL, and SMD files.
- Handles vertex and bone-weight information.

### OBJ Repack
- Parses OBJ geometry and MTL materials.
- Builds repack segments and BIN output structures.
- Validates repack data.

### SMD Repack
- Parses SMD nodes, skeletons, triangles, vertices, and weights.
- Converts model coordinates and vertex positions.
- Generates bounding-box data for BIN repacking.

### Android Interface
- File selection and extraction/repacking workflows.
- Operation logs and application guide.
- OPEN SOURCE LICENSES button for viewing included license texts.

## What's New in v1.2.1

### Open Source Licenses
- Added an in-app screen for viewing included third-party license texts.

### Check Update
- Added manual update checking through the GitHub Releases API.
- Displays release information and download progress.
- Verifies downloaded APK using GitHub's SHA-256 digest.
- Opens Android Package Installer for user confirmation.

Updates are not installed silently.

## Technical Architecture

| Source file | Responsibility |
|---|---|
| `MainActivity.java` | Android UI and workflows |
| `BinExtractor.java` | BIN parsing and extraction |
| `BinRepackWriter.java` | BIN output structure generation |
| `BinSourceAdapter.java` | Source metadata adaptation |
| `ObjParser.java` | OBJ parsing |
| `ObjMtlParser.java` | MTL parsing |
| `SmdParser.java` | SMD parsing |
| `SmdRepackPipeline.java` | SMD-to-BIN conversion |
| `RepackPipeline.java` | Repacking coordination |
| `RepackSegmentBuilder.java` | Segment construction |
| `RepackStripCompressor.java` | Strip compression |
| `RepackValidator.java` | Repack validation |
| `IdxPs2BinExporter.java` | IDXPS2BIN export support |

| `UpdateManager.java` | GitHub update checking and APK download |

## Validation

The Java extractor was compared with the historical Python reference implementation using two test files.

| Test BIN | OBJ | MTL | SMD |
|---|---|---|---|
|  | Identical | Identical | Identical |
|  | Identical | Identical | Identical |

Weight diagnostics:

| Test BIN | Vertices | Used | Weighted | Missing used weights | Segments |
|---|---:|---:|---:|---:|---:|
| Sample 1 | 2606 | 2604 | 2606 | 0 | 64 |
| Sample 2 | 662 | 660 | 662 | 0 | 17 |

These results apply only to the listed test files and do not guarantee compatibility with every BIN variant. The Android application has also been built and installed on a device; repacking tests remain sample-based.

## Android Build Configuration

| Setting | Value |
|---|---|
| Package | `com.kenzo.re4bintools` |
| Minimum Android | 6.0 (API 23) |
| Compile SDK | 35 |
| Target SDK | 35 |
| Version | 1.2.1 (version code 4) |
| Java compatibility | Java 8 |
| Gradle wrapper | 8.13 |
| Android Gradle Plugin | 8.12.0 |

### Build with Termux

From the project root, run:



APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Credits and References

Special thanks to **JADERLINK** for the original RE4 PS2 BIN research, tools, and technical reference material.

- JADERLINK GitHub: https://github.com/JADERLINK
- RE4 PS2 BIN TOOL: https://github.com/JADERLINK/RE4-PS2-BIN-TOOL
- Original releases: https://github.com/JADERLINK/RE4-PS2-BIN-TOOL/releases

This is an independent Android implementation and is not affiliated with or endorsed by JADERLINK.

### Third-party licenses

The project includes license texts for JADERLINK (MIT License) and CjClutter ObjLoader (MIT License). They are available inside the app through the OPEN SOURCE LICENSES button.

## Additional Documentation

- `README_ANDROID_EXTRACTOR.txt` — earlier Android extractor documentation.
- `VALIDATION_REPORT.txt` — extractor validation report.
- `JADERLINK_SOURCE/` — local reference source material, where included.

## Project Status

This is an ongoing independent development project. Current work focuses on improving BIN extraction and OBJ/SMD repacking compatibility, including geometry, skeletons, weights, materials, and binary structure.

**RE4 BIN TOOLS Android — Kenzo**
