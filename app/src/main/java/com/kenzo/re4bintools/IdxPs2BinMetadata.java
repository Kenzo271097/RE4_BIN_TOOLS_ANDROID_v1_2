package com.kenzo.re4bintools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class IdxPs2BinMetadata {

    static final float GLOBAL_POSITION_SCALE = 100.0f;

    static final class Bone {
        int id;
        int parent;
        float x;
        float y;
        float z;
    }

    static final class BonePair {
        int b1;
        int b2;
        int b3;
        int b4;
    }

    final List<Bone> bones = new ArrayList<>();
    final List<BonePair> bonePairs = new ArrayList<>();

    int objFileUseBone;

    boolean enableBonepairTag;
    boolean enableAdjacentBoneTag;
    boolean enableUnkFlag1;
    boolean enableUnkFlag2;
    boolean enableUnkFlag4;
    boolean useIdxMaterial;
    boolean isScenarioBin;
    boolean autoCalcBoundingBox;

    float boundingBoxPosX;
    float boundingBoxPosY;
    float boundingBoxPosZ;
    float boundingBoxPosW = 1.0f;
    float boundingBoxWidth;
    float boundingBoxHeight;
    float boundingBoxDepth;

    static IdxPs2BinMetadata read(InputStream input)
            throws IOException {

        if (input == null) {
            throw new IllegalArgumentException(
                    "File IDXPS2BIN tidak tersedia");
        }

        IdxPs2BinMetadata result =
                new IdxPs2BinMetadata();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        input, StandardCharsets.US_ASCII));

        String raw;

        while ((raw = reader.readLine()) != null) {
            String line = raw.trim().toUpperCase(Locale.ROOT);

            if (line.isEmpty()
                    || line.startsWith("\\")
                    || line.startsWith("/")
                    || line.startsWith("#")
                    || line.startsWith(":")
                    || line.startsWith("!")
                    || line.startsWith("@")
                    || line.startsWith("=")) {
                continue;
            }

            if (line.startsWith("BONELINE")) {
                String[] p = values(line);

                if (p.length >= 2) {
                    Bone bone = new Bone();
                    bone.id = parseInt(p[0], 0);
                    bone.parent = parseInt(p[1], -1);
                    bone.x = p.length > 2 ? parseFloat(p[2], 0) : 0;
                    bone.y = p.length > 3 ? parseFloat(p[3], 0) : 0;
                    bone.z = p.length > 4 ? parseFloat(p[4], 0) : 0;
                    result.bones.add(bone);
                }
                continue;
            }

            if (line.startsWith("BONEPAIR")) {
                String[] p = values(line);

                if (p.length >= 4) {
                    BonePair pair = new BonePair();
                    pair.b1 = parseUnsignedShort(p[0]);
                    pair.b2 = parseUnsignedShort(p[1]);
                    pair.b3 = parseUnsignedShort(p[2]);
                    pair.b4 = parseUnsignedShort(p[3]);
                    result.bonePairs.add(pair);
                }
                continue;
            }

            String[] entry = line.split(":", 2);
            if (entry.length != 2) {
                continue;
            }

            String key = entry[0].trim();
            String value = entry[1].trim();

            switch (key) {
                case "OBJFILEUSEBONE":
                    result.objFileUseBone = parseInt(value, 0);
                    break;
                case "ENABLEADJACENTBONETAG":
                    result.enableAdjacentBoneTag = parseBoolean(value);
                    break;
                case "ENABLEBONEPAIRTAG":
                    result.enableBonepairTag = parseBoolean(value);
                    break;
                case "ENABLEUNKFLAG1":
                    result.enableUnkFlag1 = parseBoolean(value);
                    break;
                case "ENABLEUNKFLAG2":
                    result.enableUnkFlag2 = parseBoolean(value);
                    break;
                case "ENABLEUNKFLAG4":
                    result.enableUnkFlag4 = parseBoolean(value);
                    break;
                case "USEIDXMATERIAL":
                    result.useIdxMaterial = parseBoolean(value);
                    break;
                case "ISSCENARIOBIN":
                    result.isScenarioBin = parseBoolean(value);
                    break;
                case "AUTOCALCBOUNDINGBOX":
                    result.autoCalcBoundingBox = parseBoolean(value);
                    break;
                case "BOUNDINGBOXPOSX":
                    result.boundingBoxPosX = parseFloat(value, 0);
                    break;
                case "BOUNDINGBOXPOSY":
                    result.boundingBoxPosY = parseFloat(value, 0);
                    break;
                case "BOUNDINGBOXPOSZ":
                    result.boundingBoxPosZ = parseFloat(value, 0);
                    break;
                case "BOUNDINGBOXPOSW":
                    result.boundingBoxPosW = parseFloat(value, 1);
                    break;
                case "BOUNDINGBOXWIDTH":
                    result.boundingBoxWidth = parseFloat(value, 0);
                    break;
                case "BOUNDINGBOXHEIGHT":
                    result.boundingBoxHeight = parseFloat(value, 0);
                    break;
                case "BOUNDINGBOXDEPTH":
                    result.boundingBoxDepth = parseFloat(value, 0);
                    break;
                default:
                    break;
            }
        }

        validate(result);
        return result;
    }

    private static String[] values(String line) {
        int colon = line.indexOf(':');
        if (colon < 0 || colon == line.length() - 1) {
            return new String[0];
        }

        String value = line.substring(colon + 1).trim();
        if (value.isEmpty()) {
            return new String[0];
        }

        return value.split("\\s+");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseUnsignedShort(String value) {
        int result = Integer.parseInt(value.trim());
        if (result < 0 || result > 65535) {
            throw new IllegalArgumentException(
                    "BonePair di luar rentang ushort: " + value);
        }
        return result;
    }

    private static float parseFloat(String value, float fallback) {
        try {
            float result = Float.parseFloat(value.trim());
            return Float.isFinite(result) ? result : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String value) {
        return value.trim().equalsIgnoreCase("TRUE");
    }

    private static void validate(IdxPs2BinMetadata data) {
        if (data.objFileUseBone < 0 || data.objFileUseBone > 255) {
            throw new IllegalArgumentException(
                    "OBJFILEUSEBONE harus 0 sampai 255");
        }

        if (data.bones.size() > 255) {
            throw new IllegalArgumentException(
                    "Jumlah bone melebihi batas BIN");
        }

        for (Bone bone : data.bones) {
            if (bone.id < 0 || bone.id > 255
                    || bone.parent < -128 || bone.parent > 127) {
                throw new IllegalArgumentException(
                        "Data BONELINE di luar rentang BIN");
            }
        }

        if (data.bonePairs.size() > 65535) {
            throw new IllegalArgumentException(
                    "Jumlah BonePair terlalu besar");
        }
    }
}
