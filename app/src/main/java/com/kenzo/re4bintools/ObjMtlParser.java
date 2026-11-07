package com.kenzo.re4bintools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class ObjMtlParser {

    static final class Material {
        String name;

        int diffuseId = -1;
        int bumpId = -1;
        int specularId = -1;

        String diffuseFolder = "null";
        String bumpFolder = "null";
        String specularFolder = "null";

        int specularR = 0;
        int specularG = 0;
        int specularB = 0;

        int specularScale = 0;

        boolean hasDiffuse;
        boolean hasBump;
        boolean hasSpecular;
    }

    static final class Result {
        final Map<String, Material> materials =
            new LinkedHashMap<>();
    }

    static Result parse(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("Input MTL kosong.");
        }

        Result result = new Result();
        Material current = null;

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, StandardCharsets.UTF_8)
        );

        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;

            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] p = line.split("\\s+");
            String type = p[0].toLowerCase(Locale.ROOT);

            try {
                switch (type) {
                    case "newmtl": {
                        String name = line.substring(p[0].length()).trim();

                        if (name.isEmpty()) {
                            throw new IOException("Nama material kosong.");
                        }

                        name = name.toUpperCase(Locale.ROOT);

                        current = new Material();
                        current.name = name;

                        if (result.materials.containsKey(name)) {
                            throw new IOException(
                                "Material duplikat: " + name
                            );
                        }

                        result.materials.put(name, current);
                        break;
                    }

                    case "ks": {
                        requireMaterial(current, type);

                        if (p.length < 4) {
                            throw new IOException("Ks harus memiliki RGB.");
                        }

                        current.specularR = toByte(
                            Float.parseFloat(p[1])
                        );
                        current.specularG = toByte(
                            Float.parseFloat(p[2])
                        );
                        current.specularB = toByte(
                            Float.parseFloat(p[3])
                        );
                        break;
                    }

                    case "map_kd": {
                        requireMaterial(current, type);

                        TextureRef ref = parseTexturePath(
                            getPath(line, p[0].length())
                        );

                        current.diffuseId = ref.id;
                        current.diffuseFolder = ref.folder;
                        current.hasDiffuse = true;
                        break;
                    }

                    case "map_bump":
                    case "bump": {
                        requireMaterial(current, type);

                        TextureRef ref = parseTexturePath(
                            getPath(line, p[0].length())
                        );

                        current.bumpId = ref.id;
                        current.bumpFolder = ref.folder;
                        current.hasBump = true;
                        break;
                    }

                    case "map_ns":
                    case "map_ks": {
                        requireMaterial(current, type);

                        String value = getPath(
                            line,
                            p[0].length()
                        );

                        float scaleX = 1.0f;
                        float scaleY = 1.0f;

                        if (value.startsWith("-s")) {
                            String[] args = value.split("\\s+");

                            if (args.length < 5) {
                                throw new IOException(
                                    "Format map_Ns -s tidak lengkap."
                                );
                            }

                            scaleX = Float.parseFloat(args[1]);
                            scaleY = Float.parseFloat(args[2]);

                            value = join(args, 4);
                        }

                        TextureRef ref = parseTexturePath(value);

                        current.specularId = ref.id;
                        current.specularFolder = ref.folder;
                        current.hasSpecular = true;

                        int x = scaleNibble(scaleX);
                        int y = scaleNibble(scaleY);

                        current.specularScale = (x << 4) | y;
                        break;
                    }

                    default:
                        // Ka, Kd, Ns, d, illum, map_d dan lainnya
                        break;
                }
            } catch (Exception e) {
                throw new IOException(
                    "MTL error pada baris " + lineNumber +
                    ": " + e.getMessage(),
                    e
                );
            }
        }

        if (result.materials.isEmpty()) {
            throw new IOException("MTL tidak memiliki newmtl.");
        }

        return result;
    }

    static BinRepackWriter.MaterialHeader toMaterialHeader(Material m) {
        if (m == null || !m.hasDiffuse || m.diffuseId < 0) {
            throw new IllegalArgumentException("Material membutuhkan map_Kd yang valid");
        }

        byte[] data = new byte[12];
        data[0] = 0;
        data[1] = (byte) m.diffuseId;
        data[2] = (byte) 255;
        data[3] = (byte) 255;
        data[4] = (byte) 255;
        data[5] = 0;
        data[6] = 0;
        data[7] = 0;
        data[10] = 0;
        data[11] = (byte) 255;

        if (m.hasBump) {
            data[0] |= 0x01;
            data[2] = (byte) m.bumpId;
            data[4] = 0;
            data[5] = (byte) 255;
            data[6] = (byte) 255;
            data[7] = (byte) 255;
            data[10] = 0;
        }

        if (m.hasSpecular) {
            data[0] |= 0x02;
            data[4] = (byte) m.specularId;
            data[5] = (byte) m.specularR;
            data[6] = (byte) m.specularG;
            data[7] = (byte) m.specularB;
            data[10] = (byte) m.specularScale;
        }

        return new BinRepackWriter.MaterialHeader(m.name, data);
    }

    private static void requireMaterial(
        Material material,
        String property
    ) throws IOException {
        if (material == null) {
            throw new IOException(
                property + " ditemukan sebelum newmtl."
            );
        }
    }

    private static String getPath(
        String line,
        int propertyLength
    ) throws IOException {
        String value = line.substring(propertyLength).trim();

        if (value.isEmpty()) {
            throw new IOException("Path tekstur kosong.");
        }

        return value;
    }

    private static String join(String[] values, int start) {
        StringBuilder result = new StringBuilder();

        for (int i = start; i < values.length; i++) {
            if (i > start) result.append(' ');
            result.append(values[i]);
        }

        return result.toString();
    }

    private static final class TextureRef {
        int id;
        String folder;
    }

    private static TextureRef parseTexturePath(
        String path
    ) throws IOException {

        path = path.trim().replace('\\', '/');

        if (path.startsWith("\"") && path.endsWith("\"")
                && path.length() >= 2) {
            path = path.substring(1, path.length() - 1);
        }

        String[] parts = path.split("/");
        String filename = parts[parts.length - 1];

        int dot = filename.lastIndexOf('.');

        if (dot <= 0 || dot == filename.length() - 1) {
            throw new IOException(
                "Nama file tekstur tidak memiliki ekstensi: " + path
            );
        }

        String extension = filename.substring(dot + 1);
        String stem = filename.substring(0, dot);

        int dash = stem.lastIndexOf('-');
        String idText = dash >= 0
            ? stem.substring(dash + 1)
            : stem;

        int id;

        try {
            id = Integer.parseInt(idText);
        } catch (NumberFormatException e) {
            throw new IOException(
                "ID tekstur tidak valid: " + path
            );
        }

        if (id < 0 || id > 255) {
            throw new IOException(
                "ID tekstur di luar rentang byte: " + id
            );
        }

        TextureRef ref = new TextureRef();
        ref.id = id;
        ref.folder = parts.length > 1
            ? parts[parts.length - 2].replace(' ', '_')
            : "null";

        return ref;
    }

    private static int toByte(float value) throws IOException {
        if (!Float.isFinite(value)) {
            throw new IOException("Nilai Ks tidak valid.");
        }

        int result = (int) (value * 255.0f);

        return Math.max(0, Math.min(255, result));
    }

    private static int scaleNibble(float value) throws IOException {
        if (!Float.isFinite(value)) {
            throw new IOException("Specular scale tidak valid.");
        }

        if (value < 1.0f) return 0;
        if (value > 16.0f) return 15;

        return (int) value - 1;
    }
}
