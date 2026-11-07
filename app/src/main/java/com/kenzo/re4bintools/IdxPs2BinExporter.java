package com.kenzo.re4bintools;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

final class IdxPs2BinExporter {

    private static final float SCALE = 100.0f;

    private IdxPs2BinExporter() {}

    static void write(
            byte[] binData,
            BinExtractor.Model model,
            OutputStream output
    ) throws IOException {

        if (binData == null || binData.length < 0x50) {
            throw new IOException("Header BIN tidak lengkap.");
        }

        if (model == null || model.bones == null ||
                model.bones.isEmpty()) {
            throw new IOException("Data bone tidak tersedia.");
        }

        ByteBuffer b = ByteBuffer.wrap(binData)
                .order(ByteOrder.LITTLE_ENDIAN);

        long pairOffset = Integer.toUnsignedLong(b.getInt(0x1C));
        long flags = Integer.toUnsignedLong(b.getInt(0x24));

        float boxX = b.getFloat(0x30) / SCALE;
        float boxY = b.getFloat(0x34) / SCALE;
        float boxZ = b.getFloat(0x38) / SCALE;
        float boxW = b.getFloat(0x3C);
        float boxWidth = b.getFloat(0x40) / SCALE;
        float boxHeight = b.getFloat(0x44) / SCALE;
        float boxDepth = b.getFloat(0x48) / SCALE;

        int minBone = 255;
        for (BinExtractor.Bone bone : model.bones) {
            minBone = Math.min(minBone, bone.id);
        }

        BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(output, "US-ASCII")
        );

        w.write("# RE4 PS2 BIN IDX - Android Exporter");
        w.newLine();
        w.newLine();

        w.write("UseIdxMaterial:False");
        w.newLine();

        w.write("IsScenarioBin:" + model.scenario);
        w.newLine();

        w.write("EnableAdjacentBoneTag:" +
                ((flags & 0x00000200L) != 0));
        w.newLine();

        w.write("EnableBonepairTag:" +
                ((flags & 0x00000100L) != 0));
        w.newLine();

        w.write("EnableUnkFlag1:" +
                ((flags & 0x10000000L) != 0));
        w.newLine();

        w.write("EnableUnkFlag2:" +
                ((flags & 0x20000000L) != 0));
        w.newLine();

        w.write("EnableUnkFlag4:" +
                ((flags & 0x40000000L) != 0));
        w.newLine();

        w.write("ObjFileUseBone:" + minBone);
        w.newLine();
        w.newLine();

        w.write("AutoCalcBoundingBox:True");
        w.newLine();

        w.write("BoundingBoxPosX:" + number(boxX));
        w.newLine();

        w.write("BoundingBoxPosY:" + number(boxY));
        w.newLine();

        w.write("BoundingBoxPosZ:" + number(boxZ));
        w.newLine();

        w.write("BoundingBoxWidth:" + number(boxWidth));
        w.newLine();

        w.write("BoundingBoxHeight:" + number(boxHeight));
        w.newLine();

        w.write("BoundingBoxDepth:" + number(boxDepth));
        w.newLine();
        w.newLine();

        w.write("## BoneLine: ID Parent X -Z Y");
        w.newLine();

        for (BinExtractor.Bone bone : model.bones) {

            int parent = bone.parent == 255 ? -1 : bone.parent;

            float x = bone.x / SCALE;
            float y = -bone.z / SCALE;
            float z = bone.y / SCALE;

            w.write(String.format(
                    Locale.US,
                    "BoneLine:%4d %4d %s %s %s",
                    bone.id,
                    parent,
                    number(x),
                    number(y),
                    number(z)
            ));

            w.newLine();
        }

        if (pairOffset != 0) {

            if (pairOffset < 0 || pairOffset + 4 > binData.length) {
                throw new IOException("Pointer BonePair di luar BIN.");
            }

            int pairCount = b.getInt((int) pairOffset);

            if (pairCount < 0 ||
                    pairCount > (binData.length - pairOffset - 4) / 8) {
                throw new IOException("Jumlah BonePair tidak valid.");
            }

            if (pairCount > 0) {
                w.newLine();
                w.write("## BonePair: Bone1 Bone2 Bone3 Bone4");
                w.newLine();

                int p = (int) pairOffset + 4;

                for (int i = 0; i < pairCount; i++) {

                    int b1 = b.getShort(p) & 0xFFFF;
                    int b2 = b.getShort(p + 2) & 0xFFFF;
                    int b3 = b.getShort(p + 4) & 0xFFFF;
                    int b4 = b.getShort(p + 6) & 0xFFFF;

                    w.write(String.format(
                            Locale.US,
                            "BonePair:%4d %4d %4d %4d",
                            b1, b2, b3, b4
                    ));

                    w.newLine();
                    p += 8;
                }
            }
        }

        w.flush();
    }

    private static String number(float value) {
        if (!Float.isFinite(value)) {
            value = 0f;
        }

        return String.format(Locale.US, "%.9g", value);
    }
}
