RE4 BIN TOOLS — Android BIN Extractor integration
Version: 1.1-extractor
Package: com.kenzo.re4bintools

WHAT IS INCLUDED
- BinExtractor.java: Android/Java implementation of the JADERLINK RE4 PS2 BIN
  v1.5.0-compatible decoder and OBJ/MTL/SMD writer.
- Direct BIN weight diagnostics ported from KENZO RE4 BIN TOOLS v0.4.55:
  unique node-offset ordering, duplicate-bone ID handling, BIN weight-map row
  lookup (vertex unknownB / 2), local-bone resolution, positive finite weight
  checks, duplicate influence combination, normalization, and face-used vertex
  coverage checks.
- MainActivity.java: Android file picker, output-folder picker, background
  extraction, status display, and log viewer.
- Existing Android app identity, package, launcher resources, and original UI
  controls are retained. REPACK buttons remain placeholders.

HOW TO TEST IN ANDROIDIDE
1. Extract this ZIP to a normal writable project folder.
2. Open the extracted project folder in AndroidIDE.
3. Allow Gradle sync to complete.
4. Run the debug build/install task from AndroidIDE, or use its terminal:
     ./gradlew assembleDebug
5. In the app, choose a RE4 PS2 BIN, choose an output folder, then tap
   EXTRACT BIN. The app creates OBJ, MTL, and SMD files in that folder.

VALIDATION PERFORMED OUTSIDE ANDROID
The pure Java decoder/writer was compiled with javac and run against both BIN
fixtures. OBJ, MTL, and SMD output were compared byte-for-byte with the Python
extractor in RE4_BIN_TOOLS_v0_4_55_DIRECT_BIN_WEIGHT_BIND.zip. All six output
files matched exactly.

Direct weight diagnostics also matched the v0.4.55 test expectations:
- pl00_0000(1).BIN: 2,606 vertices; 2,604 face-used vertices; 2,606 valid
  weighted vertices; 0 face-used vertices missing weights; 64 segments.
- pl00_0002.BIN: 662 vertices; 660 face-used vertices; 662 valid weighted
  vertices; 0 face-used vertices missing weights; 17 segments.

LIMITATIONS / HONEST STATUS
- This is an integrated Android source project, not a prebuilt APK.
- An Android Gradle build and installation on a physical device have NOT been
  performed in this environment. AndroidIDE must perform that final build and
  the user must test it on-device.
- REPACK OBJ and REPACK SMD are still placeholders and are not implemented.
- Direct weight diagnostics are informational; extraction still preserves the
  original BIN-derived SMD weight rows. Scenario/unweighted vertices are not
  assigned invented weights.

LICENSES
- JADERLINK source attribution/license: LICENSES/MIT-JADERLINK.txt
- v0.4.55 direct-weight reference implementation license: LICENSES/GPL-2.0.txt
Retain these notices when redistributing the project.


VERSION 1.2 EXTENSION FIX
Output documents are now created with application/octet-stream MIME type so Android Storage Access Framework providers do not append an unwanted .txt suffix. Expected names: <model>.obj, <model>.mtl, <model>.smd.
