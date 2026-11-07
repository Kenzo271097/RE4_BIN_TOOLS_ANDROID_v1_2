package com.kenzo.re4bintools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BinSourceMetadata {

    static final int HEADER_SIZE = 0x50;
    static final int BONE_SIZE = 0x10;
    static final int MATERIAL_SIZE = 0x10;
    static final int BONE_PAIR_SIZE = 0x08;

    final int startOffset;
    final byte[] header;
    final byte[][] bones;
    final int[][] bonePairs;
    final byte[][] materials;
    final long[] nodeOffsets;

    final long bonesPoint;
    final int vertexScale;
    final int bonesCount;
    final int materialCount;
    final long materialOffset;
    final long bonePairPoint;
    final long versionFlags;
    final long binFlags;

    private BinSourceMetadata(
            int startOffset,
            byte[] header,
            byte[][] bones,
            int[][] bonePairs,
            byte[][] materials,
            long[] nodeOffsets) {

        this.startOffset = startOffset;
        this.header = header;
        this.bones = bones;
        this.bonePairs = bonePairs;
        this.materials = materials;
        this.nodeOffsets = nodeOffsets;

        bonesPoint = u32(header, 0x04);
        vertexScale = u8(header, 0x08);
        bonesCount = u8(header, 0x09);
        materialCount = u16(header, 0x0A);
        materialOffset = u32(header, 0x0C);
        versionFlags = u32(header, 0x18);
        bonePairPoint = u32(header, 0x1C);
        binFlags = u32(header, 0x24);
    }

    static BinSourceMetadata read(byte[] data) {
        return read(data, 0);
    }

    static BinSourceMetadata read(byte[] data, int startOffset) {

        if (data == null) {
            throw new IllegalArgumentException("Data BIN null");
        }

        if (startOffset < 0 || startOffset > data.length - HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "Start offset/header di luar batas file");
        }

        byte[] header = copyRange(
                data, startOffset, HEADER_SIZE, "BIN header");

        if (u8(header, 0) != 0x30) {
            throw new IllegalArgumentException(
                    "Magic BIN tidak dikenal: " +
                    Integer.toHexString(u8(header, 0)));
        }

        if (u32(header, 0x10) != 0xCDCDCDCDL) {
            throw new IllegalArgumentException(
                    "Padding header BIN tidak valid");
        }

        int bonesCount = u8(header, 0x09);
        int materialCount = u16(header, 0x0A);

        long bonesPoint = u32(header, 0x04);
        long materialOffset = u32(header, 0x0C);
        long bonePairPoint = u32(header, 0x1C);

        byte[][] bones = new byte[bonesCount][];

        long bonesLength = (long) bonesCount * BONE_SIZE;
        checkRange(data, startOffset, bonesPoint,
                bonesLength, "Bone records");

        for (int i = 0; i < bonesCount; i++) {
            long offset = bonesPoint + (long) i * BONE_SIZE;
            bones[i] = copyRange(
                    data,
                    absoluteOffset(startOffset, offset),
                    BONE_SIZE,
                    "Bone " + i);
        }

        byte[][] materials = new byte[materialCount][];
        long[] nodeOffsets = new long[materialCount];

        long materialsLength =
                (long) materialCount * MATERIAL_SIZE;

        checkRange(data, startOffset, materialOffset,
                materialsLength, "Material table");

        for (int i = 0; i < materialCount; i++) {

            long relativeOffset =
                    materialOffset + (long) i * MATERIAL_SIZE;

            byte[] material = copyRange(
                    data,
                    absoluteOffset(startOffset, relativeOffset),
                    MATERIAL_SIZE,
                    "Material " + i);

            materials[i] = material;
            nodeOffsets[i] = u32(material, 12);

            if (nodeOffsets[i] != 0) {
                checkRange(data, startOffset,
                        nodeOffsets[i], 4,
                        "Node pointer material " + i);
            }
        }

        int[][] bonePairs = new int[0][4];

        if (bonePairPoint != 0) {

            checkRange(data, startOffset,
                    bonePairPoint, 4, "Bone pair count");

            int count = (int) u32(
                    data, absoluteOffset(startOffset, bonePairPoint));

            if (count < 0) {
                throw new IllegalArgumentException(
                        "Jumlah bone pairs terlalu besar");
            }

            long pairLength = (long) count * BONE_PAIR_SIZE;

            checkRange(data, startOffset,
                    bonePairPoint + 4,
                    pairLength,
                    "Bone pair records");

            bonePairs = new int[count][4];

            for (int i = 0; i < count; i++) {

                long offset = bonePairPoint + 4L
                        + (long) i * BONE_PAIR_SIZE;

                int absolute = absoluteOffset(startOffset, offset);

                for (int j = 0; j < 4; j++) {
                    bonePairs[i][j] =
                            u16(data, absolute + j * 2);
                }
            }
        }

        return new BinSourceMetadata(
                startOffset,
                header,
                bones,
                bonePairs,
                materials,
                nodeOffsets);
    }

    byte[] getHeaderCopy() {
        return header.clone();
    }

    byte[] getBoneCopy(int index) {
        return bones[index].clone();
    }

    byte[] getMaterialCopy(int index) {
        return materials[index].clone();
    }

    long getNodeOffset(int materialIndex) {
        return nodeOffsets[materialIndex];
    }

    int getBonePairCount() {
        return bonePairs.length;
    }

    int getBonePairValue(int pairIndex, int valueIndex) {
        return bonePairs[pairIndex][valueIndex];
    }

    static int u8(byte[] data, int offset) {
        return data[offset] & 0xFF;
    }

    static int u16(byte[] data, int offset) {
        return u8(data, offset)
                | (u8(data, offset + 1) << 8);
    }

    static long u32(byte[] data, int offset) {
        return ((long) u8(data, offset))
                | ((long) u8(data, offset + 1) << 8)
                | ((long) u8(data, offset + 2) << 16)
                | ((long) u8(data, offset + 3) << 24);
    }

    private static byte[] copyRange(
            byte[] data,
            int offset,
            int length,
            String label) {

        if (offset < 0 || length < 0
                || offset > data.length - length) {
            throw new IllegalArgumentException(
                    label + " berada di luar batas file");
        }

        byte[] result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    private static int absoluteOffset(
            int startOffset, long relativeOffset) {

        long absolute = (long) startOffset + relativeOffset;

        if (absolute < 0 || absolute > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Offset BIN terlalu besar");
        }

        return (int) absolute;
    }

    private static void checkRange(
            byte[] data,
            int startOffset,
            long relativeOffset,
            long length,
            String label) {

        long absolute = (long) startOffset + relativeOffset;

        if (relativeOffset < 0 || length < 0
                || absolute < startOffset
                || absolute > data.length
                || length > data.length - absolute) {

            throw new IllegalArgumentException(
                    label + " berada di luar batas file");
        }
    }
}
