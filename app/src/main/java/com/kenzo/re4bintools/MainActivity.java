package com.kenzo.re4bintools;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.view.Gravity;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView txtStatus;
    private List<Uri> binUris = new ArrayList<>();
    private Uri outputUri;

    private static final int PICK_BIN = 100;
    private static final int PICK_OUTPUT = 101;
    private static final int PICK_OBJ = 102;
    private static final int PICK_WALLPAPER = 103;
    private static final int PICK_SMD = 104;
    private volatile boolean repackRunning = false;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private volatile boolean extractionRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = findViewById(R.id.txtStatus);

        Button btnSelect = findViewById(R.id.btnSelect);
        Button btnOutput = findViewById(R.id.btnOutput);
        Button btnExtract = findViewById(R.id.btnExtract);
        Button btnRepackObj = findViewById(R.id.btnRepackObj);
        Button btnRepackSmd = findViewById(R.id.btnRepackSmd);
        Button btnTheme = findViewById(R.id.btnTheme);
        Button btnGuide = findViewById(R.id.btnGuide);
        applyTheme();
        btnTheme.setOnClickListener(v -> chooseTheme());
        btnGuide.setOnClickListener(v -> showGuide());

        btnSelect.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, PICK_BIN);
        });

        btnOutput.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(i, PICK_OUTPUT);
        });

        btnExtract.setOnClickListener(v -> startExtraction());

btnRepackObj.setOnClickListener(v -> {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("*/*");
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i, PICK_OBJ);
});

        btnRepackSmd.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(i, PICK_SMD);
        });


        writeLog("Aplikasi dibuka.");
    }

    private void chooseTheme() {
        String[] choices = {"DEFAULT", "CUSTOM PHOTO"};
        int savedMode = getSharedPreferences("ui_settings", MODE_PRIVATE)
            .getInt("theme_mode", 0);
        int current = (savedMode == 2) ? 1 : 0;
        new AlertDialog.Builder(this)
            .setTitle("THEME MANAGER")
            .setSingleChoiceItems(choices, current, (dialog, which) -> {
                dialog.dismiss();
                if (which == 1) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.setType("image/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, PICK_WALLPAPER);
                } else {
                    getSharedPreferences("ui_settings", MODE_PRIVATE)
                        .edit().putInt("theme_mode", 0).apply();
                    applyTheme();
                }
            })
            .setNegativeButton("BATAL", null)
            .show();
    }

    private void saveWallpaper(Uri uri) {
        File target = new File(getFilesDir(), "theme_wallpaper.img");
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Foto tidak dapat dibaca");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            getSharedPreferences("ui_settings", MODE_PRIVATE)
                .edit().putInt("theme_mode", 2).apply();
            applyTheme();
            Toast.makeText(this, "Wallpaper berhasil dipasang", Toast.LENGTH_SHORT).show();
            writeLog("Wallpaper custom dipasang.");
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memasang wallpaper: " + e.getMessage(), Toast.LENGTH_LONG).show();
            writeLog("Gagal memasang wallpaper: " + e);
        }
    }

    private void applyTheme() {
        android.view.View root = findViewById(R.id.mainScroll);
        if (root == null) return;

        int mode = getSharedPreferences("ui_settings", MODE_PRIVATE)
                .getInt("theme_mode", 0);

        android.view.Window window = getWindow();

        if (mode == 2) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));

            File file = new File(getFilesDir(), "theme_wallpaper.img");
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (bitmap != null) {
                BitmapDrawable drawable = new BitmapDrawable(getResources(), bitmap);
                drawable.setGravity(Gravity.FILL);
                root.setBackground(drawable);
            } else {
                root.setBackgroundColor(Color.BLACK);
            }
        } else {
            window.setBackgroundDrawableResource(android.R.color.background_light);

            android.util.TypedValue value = new android.util.TypedValue();
            getTheme().resolveAttribute(android.R.attr.colorBackground, value, true);
            root.setBackgroundColor(value.data);
        }
    }

    private void showGuide() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_guide, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .create();
        android.widget.Button close = view.findViewById(R.id.btnGuideClose);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.90),
                android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
        }
    }

    private void startExtraction() {

        if (extractionRunning) {
            showStatus("Batch Extract sedang berjalan.");
            return;
        }

        if (binUris.isEmpty()) {
            showStatus("Pilih file BIN terlebih dahulu.");
            return;
        }

        if (outputUri == null) {
            showStatus("Pilih folder output terlebih dahulu.");
            return;
        }

        final List<Uri> selectedBins = new ArrayList<>(binUris);
        final Uri selectedOutput = outputUri;

        extractionRunning = true;

        showStatus(
                "Memulai Batch Extract...\nJumlah BIN: " +
                selectedBins.size() + "\nJangan tutup aplikasi."
        );

        writeLog("Memulai Batch Extract: " + selectedBins.size() + " BIN.");

        executor.execute(() -> {

            int success = 0;
            int failed = 0;

            StringBuilder report = new StringBuilder();

            for (int i = 0; i < selectedBins.size(); i++) {

                Uri uri = selectedBins.get(i);
                String name = getDisplayName(uri);

                final String progress =
                        "Batch Extract: " + (i + 1) + "/" +
                        selectedBins.size() + "\n" + name +
                        "\nSedang diproses...";

                runOnUiThread(() -> showStatus(progress));

                writeLog("Memulai ekstraksi " + (i + 1) +
                        "/" + selectedBins.size() + ": " + uri);

                try {

                    String result = extractOne(uri, selectedOutput);
                    success++;

                    report.append("BERHASIL: ")
                            .append(name).append("\n");

                    writeLog(result);

                } catch (Exception e) {

                    failed++;

                    String error = e.getMessage() == null
                            ? e.toString() : e.getMessage();

                    report.append("GAGAL: ")
                            .append(name)
                            .append(" - ")
                            .append(error)
                            .append("\n");

                    writeLog("Ekstraksi gagal " + name + ": " + error);
                }
            }

            final int totalSuccess = success;
            final int totalFailed = failed;

            String finalMessage =
                    "BATCH EXTRACT SELESAI\n\n" +
                    "Total BIN: " + selectedBins.size() + "\n" +
                    "Berhasil: " + totalSuccess + "\n" +
                    "Gagal: " + totalFailed + "\n\n" +
                    report;

            writeLog("BATCH SELESAI. Berhasil: " +
                    totalSuccess + ", Gagal: " + totalFailed);

            extractionRunning = false;

            runOnUiThread(() -> {
                showStatus(finalMessage);

                Toast.makeText(
                        MainActivity.this,
                        "Batch selesai: " + totalSuccess +
                        " berhasil, " + totalFailed + " gagal.",
                        Toast.LENGTH_LONG
                ).show();
            });
        });
    }

    private String extractOne(Uri binUri, Uri targetOutput)
            throws Exception {

        byte[] data;

        try (
                InputStream in =
                        getContentResolver().openInputStream(binUri);
                ByteArrayOutputStream out =
                        new ByteArrayOutputStream()
        ) {

            if (in == null) {
                throw new IOException("Tidak dapat membuka file BIN.");
            }

            byte[] buffer = new byte[65536];
            int n;

            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }

            data = out.toByteArray();
        }

        BinExtractor.Model model = BinExtractor.decode(data);

        BinExtractor.DirectWeightReport weightReport =
                BinExtractor.inspectDirectWeights(model);

        String name = getDisplayName(binUri);

        if (name.toLowerCase().endsWith(".bin")) {
            name = name.substring(0, name.length() - 4);
        }

        if (name.trim().isEmpty()) {
            name = "RE4_MODEL";
        }

        Uri obj = createOutput(
                targetOutput, name + ".obj",
                "application/octet-stream"
        );

        Uri mtl = createOutput(
                targetOutput, name + ".mtl",
                "application/octet-stream"
        );

        Uri smd = createOutput(
                targetOutput, name + ".smd",
                "application/octet-stream"
        );

        Uri idx = createOutput(targetOutput, name + ".idxps2bin", "application/octet-stream");

        if (obj == null || mtl == null || smd == null || idx == null) {
            throw new IOException(
                    "Gagal membuat file output di folder yang dipilih."
            );
        }

        Map<String, String> stats;

        try (
                OutputStream a =
                        getContentResolver().openOutputStream(obj, "wt");
                OutputStream b =
                        getContentResolver().openOutputStream(mtl, "wt");
                OutputStream c =
                        getContentResolver().openOutputStream(smd, "wt")
        ) {

            if (a == null || b == null || c == null) {
                throw new IOException("Tidak dapat menulis file output.");
            }

            stats = BinExtractor.write(model, name, a, b, c);
        }

        try (OutputStream d = getContentResolver().openOutputStream(idx, "wt")) {
            if (d == null) throw new IOException("Tidak dapat menulis IDXPS2BIN.");
            IdxPs2BinExporter.write(data, model, d);
        }

        String weightLine =
                "Direct BIN weights: " +
                weightReport.weightedVertices + "/" +
                weightReport.vertices +
                " vertex valid; face-used missing: " +
                weightReport.missingUsedVertices;

        return "EXTRACT BERHASIL " + name +
                " Vertex: " + stats.get("vertices") +
                " Bone: " + stats.get("bones") +
                " Material: " + stats.get("materials") +
                " Segment: " + stats.get("segments") +
                " " + weightLine +
                " Output: OBJ + MTL + SMD + IDXPS2BIN";
    }

    private Uri createOutput(
            Uri targetOutput,
            String name,
            String mime
    ) throws IOException {

        String tree =
                DocumentsContract.getTreeDocumentId(targetOutput);

        Uri parent =
                DocumentsContract.buildDocumentUriUsingTree(
                        targetOutput, tree
                );

        return DocumentsContract.createDocument(
                getContentResolver(),
                parent,
                mime,
                name
        );
    }

    private String getDisplayName(Uri uri) {

        try (
                Cursor c = getContentResolver().query(
                        uri, null, null, null, null
                )
        ) {

            if (c != null && c.moveToFirst()) {

                int i = c.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                );

                if (i >= 0) {
                    return c.getString(i);
                }
            }

        } catch (Exception ignored) {
        }

        return "RE4_MODEL.BIN";
    }

    private void saveReadPermission(Uri uri, Intent data) {

        try {

            int flags = data.getFlags() &
                    Intent.FLAG_GRANT_READ_URI_PERMISSION;

            getContentResolver().takePersistableUriPermission(
                    uri, flags
            );

        } catch (SecurityException ignored) {
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) {
            return;
        }

        if (requestCode == PICK_WALLPAPER) {
            if (resultCode == RESULT_OK && data.getData() != null) {
                saveWallpaper(data.getData());
            }
            return;
        }

        if (requestCode == PICK_BIN) {

            List<Uri> selected = new ArrayList<>();

            if (data.getClipData() != null) {

                ClipData clip = data.getClipData();
                Set<String> unique = new HashSet<>();

                for (int i = 0; i < clip.getItemCount(); i++) {

                    Uri uri = clip.getItemAt(i).getUri();

                    if (uri != null && unique.add(uri.toString())) {
                        selected.add(uri);
                    }
                }

            } else if (data.getData() != null) {

                selected.add(data.getData());
            }

            if (selected.isEmpty()) {
                showStatus("Tidak ada file BIN yang dipilih.");
                return;
            }

            binUris.clear();
            binUris.addAll(selected);

            for (Uri uri : binUris) {
                saveReadPermission(uri, data);
                writeLog("File BIN dipilih: " + uri);
            }

            StringBuilder names = new StringBuilder();

            for (Uri uri : binUris) {
                names.append(getDisplayName(uri)).append("\n");
            }

            showStatus(
                    "Jumlah BIN dipilih: " + binUris.size() +
                    "\n\n" + names
            );

            writeLog("Total BIN dipilih: " + binUris.size());

} else if (requestCode == PICK_OBJ) {
    java.util.ArrayList<Uri> selected = new java.util.ArrayList<>();
    if (data.getClipData() != null) {
        android.content.ClipData clip = data.getClipData();
        for (int i = 0; i < clip.getItemCount(); i++) {
            Uri u = clip.getItemAt(i).getUri();
            if (u != null) selected.add(u);
        }
    } else if (data.getData() != null) {
        selected.add(data.getData());
    }

    Uri objUri = null;
    Uri mtlUri = null;
    for (Uri u : selected) {
        saveReadPermission(u, data);
        String name = getDisplayName(u).toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".obj")) objUri = u;
        if (name.endsWith(".mtl")) mtlUri = u;
    }

    if (objUri == null) {
        showStatus("File OBJ tidak ditemukan. Pilih OBJ dan MTL bersamaan.");
        return;
    }
    writeLog("OBJ dipilih: " + getDisplayName(objUri));
    if (mtlUri != null) writeLog("MTL dipilih: " + getDisplayName(mtlUri));
    startObjRepack(objUri, mtlUri);
        } else if (requestCode == PICK_SMD) {
            Uri smdUri = data.getData();
            if (smdUri == null) return;

            saveReadPermission(smdUri, data);
            String name = getDisplayName(smdUri);

            if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".smd")) {
                showStatus("Pilih file berekstensi .smd.");
                return;
            }

            writeLog("SMD dipilih: " + name);
            startSmdRepack(smdUri);

        } else if (requestCode == PICK_OUTPUT) {

            Uri uri = data.getData();

            if (uri == null) {
                return;
            }

            outputUri = uri;

            try {

                getContentResolver().takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION |
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );

            } catch (SecurityException e) {

                writeLog("Izin folder tidak dapat disimpan: " + e);
            }

            showStatus("Folder output dipilih.\nSiap ekstraksi.");

            writeLog("Folder output dipilih: " + uri);
        }
    }

    private void startObjRepack(Uri uri, Uri mtlUri) {
        if (repackRunning || extractionRunning) {
            showStatus("Tunggu proses lain selesai.");
            return;
        }
        if (binUris.isEmpty()) {
            showStatus("Pilih BIN sumber terlebih dahulu.");
            return;
        }
        if (outputUri == null) {
            showStatus("Pilih folder output terlebih dahulu.");
            return;
        }

        final Uri sourceUri = binUris.get(0);
        final Uri targetFolder = outputUri;
        repackRunning = true;
        showStatus("Memproses REPACK OBJ...");

        executor.execute(() -> {
            try {
                RepackPipeline.Output result;
                try (InputStream obj = getContentResolver().openInputStream(uri)) {
                    if (obj == null) throw new IOException("OBJ tidak dapat dibuka");
                    result = RepackPipeline.process(obj, 0);
                }

                ObjMtlParser.Result parsedMtl = null;
                if (mtlUri != null) {
                    try (InputStream mtl = getContentResolver().openInputStream(mtlUri)) {
                        if (mtl == null) throw new IOException("MTL tidak dapat dibuka");
                        parsedMtl = ObjMtlParser.parse(mtl);
                    }
                }

                byte[] sourceBytes;
                try (InputStream src = getContentResolver().openInputStream(sourceUri);
                     java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                    if (src == null) throw new IOException("BIN sumber tidak dapat dibuka");
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = src.read(chunk)) != -1) buffer.write(chunk, 0, count);
                    sourceBytes = buffer.toByteArray();
                }

                BinSourceMetadata source = BinSourceMetadata.read(sourceBytes);
                BinSourceAdapter.Data adapted =
                        BinSourceAdapter.convert(source, result.conversionFactor);

                java.util.Map<String, BinRepackWriter.MaterialHeader> sourceMaterials =
                        new java.util.HashMap<>();
                for (BinRepackWriter.MaterialHeader material : adapted.materials) {
                    sourceMaterials.put(
                            material.name.toUpperCase(java.util.Locale.ROOT), material);
                }

                java.util.Map<String, BinRepackWriter.MaterialHeader> outputMaterials =
                        new java.util.LinkedHashMap<>();

                for (RepackSegmentBuilder.MaterialNode node : result.segments.nodes) {
                    String key = node.materialName.toUpperCase(java.util.Locale.ROOT);
                    BinRepackWriter.MaterialHeader header = null;

                    if (parsedMtl != null) {
                        ObjMtlParser.Material material = parsedMtl.materials.get(key);
                        if (material != null) {
                            header = ObjMtlParser.toMaterialHeader(material);
                        }
                    }

                    if (header == null) header = sourceMaterials.get(key);

                    if (header != null) {
                        outputMaterials.put(key, header);
                    }
                }

                java.util.List<BinRepackWriter.MaterialHeader> finalMaterials =
                        new java.util.ArrayList<>(outputMaterials.values());

                byte[] bin = BinRepackWriter.write(
                        result.segments,
                        adapted.metadata,
                        finalMaterials,
                        adapted.options
                );

                BinSourceMetadata check = BinSourceMetadata.read(bin);
                if (check.bonesCount != adapted.metadata.bones.size())
                    throw new IOException("Validasi jumlah bone gagal");
                if (check.materialCount != result.segments.nodes.size())
                    throw new IOException("Validasi jumlah material gagal");

                for (long offset : check.nodeOffsets) {
                    if (offset < 0 || offset >= bin.length)
                        throw new IOException("Offset node di luar BIN");
                }

                Uri output = createOutput(
                        targetFolder, "REPACKED.BIN", "application/octet-stream");
                if (output == null) throw new IOException("Gagal membuat file BIN");

                try (OutputStream out = getContentResolver().openOutputStream(output, "w")) {
                    if (out == null) throw new IOException("Gagal membuka output BIN");
                    out.write(bin);
                    out.flush();
                }

                writeLog("REPACK OBJ selesai: " + bin.length + " bytes");
                runOnUiThread(() -> showStatus(
                        "REPACK OBJ BERHASIL\n\n" +
                        result.report.summary() +
                        "\n\nFile: REPACKED.BIN\nUkuran: " + bin.length + " bytes"));

            } catch (Exception e) {
                String error = "REPACK OBJ gagal: " + e.getMessage();
                writeLog(error);
                runOnUiThread(() -> showStatus(error));
            } finally {
                repackRunning = false;
            }
        });
    }


    private void startSmdRepack(Uri smdUri) {
        if (repackRunning || extractionRunning) {
            showStatus("Tunggu proses lain selesai.");
            return;
        }
        if (binUris.isEmpty()) {
            showStatus("Pilih BIN sumber terlebih dahulu.");
            return;
        }
        if (outputUri == null) {
            showStatus("Pilih folder output terlebih dahulu.");
            return;
        }

        final Uri sourceUri = binUris.get(0);
        final Uri targetFolder = outputUri;
        repackRunning = true;
        showStatus("Memproses REPACK SMD...");

        executor.execute(() -> {
            try {
                SmdRepackPipeline.Output result;
                try (InputStream smd = getContentResolver().openInputStream(smdUri)) {
                    if (smd == null) throw new IOException("SMD tidak dapat dibuka");
                    result = SmdRepackPipeline.process(smd);
                }

                byte[] sourceBytes;
                try (InputStream src = getContentResolver().openInputStream(sourceUri);
                     java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
                    if (src == null) throw new IOException("BIN sumber tidak dapat dibuka");
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = src.read(chunk)) != -1) buffer.write(chunk, 0, count);
                    sourceBytes = buffer.toByteArray();
                }

                BinSourceMetadata source = BinSourceMetadata.read(sourceBytes);
                BinSourceAdapter.Data adapted =
                        BinSourceAdapter.convert(source, result.conversionFactor);

                java.util.Map<String, BinRepackWriter.MaterialHeader> sourceMaterials =
                        new java.util.HashMap<>();

                for (BinRepackWriter.MaterialHeader material : adapted.materials) {
                    sourceMaterials.put(
                            material.name.toUpperCase(java.util.Locale.ROOT).trim(),
                            material);
                }

                java.util.List<BinRepackWriter.MaterialHeader> finalMaterials =
                        new java.util.ArrayList<>();

                for (RepackSegmentBuilder.MaterialNode node : result.segments.nodes) {
                    String key = node.materialName == null
                            ? ""
                            : node.materialName.toUpperCase(java.util.Locale.ROOT).trim();

                    BinRepackWriter.MaterialHeader header = sourceMaterials.get(key);

                    if (header == null) {
                        byte[] fallbackHeader = new byte[12];
                        fallbackHeader[2] = (byte) 0xFF;
                        fallbackHeader[3] = (byte) 0xFF;
                        fallbackHeader[4] = (byte) 0xFF;
                        fallbackHeader[11] = (byte) 0xFF;

                        header = new BinRepackWriter.MaterialHeader(
                                node.materialName, fallbackHeader);
                    }

                    finalMaterials.add(new BinRepackWriter.MaterialHeader(
                            node.materialName, header.data));
                }

                adapted.options.bboxX = result.bounds.x;
                adapted.options.bboxY = result.bounds.y;
                adapted.options.bboxZ = result.bounds.z;
                adapted.options.bboxW = result.bounds.w;
                adapted.options.bboxWidth = result.bounds.width;
                adapted.options.bboxHeight = result.bounds.height;
                adapted.options.bboxDepth = result.bounds.depth;
                adapted.options.conversionFactor = result.conversionFactor;
                adapted.options.vertexPositionScale = 1.0f;

                byte[] bin = BinRepackWriter.write(
                        result.segments,
                        adapted.metadata,
                        finalMaterials,
                        adapted.options,
                        result.bones
                );

                BinSourceMetadata check = BinSourceMetadata.read(bin);
                if (check.bonesCount != result.bones.size())
                    throw new IOException("Validasi jumlah bone SMD gagal");
                if (check.materialCount != result.segments.nodes.size())
                    throw new IOException("Validasi jumlah material SMD gagal");

                for (long offset : check.nodeOffsets) {
                    if (offset < 0 || offset >= bin.length)
                        throw new IOException("Offset node SMD di luar BIN");
                }

                Uri output = createOutput(
                        targetFolder, "REPACKED.BIN", "application/octet-stream");
                if (output == null) throw new IOException("Gagal membuat file BIN");

                try (OutputStream out = getContentResolver().openOutputStream(output, "w")) {
                    if (out == null) throw new IOException("Gagal membuka output BIN");
                    out.write(bin);
                    out.flush();
                }

                writeLog("REPACK SMD selesai: " + bin.length + " bytes");
                runOnUiThread(() -> showStatus(
                        "REPACK SMD BERHASIL\n\n" +
                        "Material: " + result.segments.nodes.size() +
                        "\nBone: " + result.bones.size() +
                        "\nFile: REPACKED.BIN\nUkuran: " + bin.length + " bytes"));

            } catch (Exception e) {
                String error = "REPACK SMD gagal: " + e.getMessage();
                writeLog(error);
                runOnUiThread(() -> showStatus(error));
            } finally {
                repackRunning = false;
            }
        });
    }

    private void showStatus(String message) {
        txtStatus.setText(message);
    }

    private void writeLog(String message) {

        try (
                FileOutputStream fos =
                        openFileOutput("re4bintools.txt", MODE_APPEND);
                BufferedWriter w = new BufferedWriter(
                        new OutputStreamWriter(fos, "UTF-8")
                )
        ) {

            w.write(System.currentTimeMillis() + " - " + message);
            w.newLine();

        } catch (IOException ignored) {
        }
    }

    private void showLogs() {

        try (
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(
                                openFileInput("re4bintools.txt"),
                                "UTF-8"
                        )
                )
        ) {

            StringBuilder s = new StringBuilder();
            String line;

            while ((line = r.readLine()) != null) {
                s.append(line).append('\n');
            }

            new AlertDialog.Builder(this)
                    .setTitle("RE4 BIN TOOLS LOG")
                    .setMessage(
                            s.length() == 0 ? "Log kosong." : s.toString()
                    )
                    .setPositiveButton("TUTUP", null)
                    .setNeutralButton("SALIN", (d, w) -> {

                        ClipboardManager cm =
                                (ClipboardManager) getSystemService(
                                        CLIPBOARD_SERVICE
                                );

                        cm.setPrimaryClip(
                                ClipData.newPlainText(
                                        "RE4 Logs", s.toString()
                                )
                        );

                        Toast.makeText(
                                this, "Log disalin",
                                Toast.LENGTH_SHORT
                        ).show();
                    })
                    .show();

        } catch (IOException e) {

            Toast.makeText(
                    this, "Belum ada log.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
