package com.kenzo.re4bintools;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class BinRepackWriter {

    private static final int HEADER_SIZE = 0x50;
    private static final int ALIGNMENT = 0x10;

    static final class MaterialHeader {
        final String name;
        final byte[] data;

        MaterialHeader(String name, byte[] data) {
            if (data == null || data.length < 12) {
                throw new IllegalArgumentException("Material header tidak valid");
            }
            this.name = name == null ? "" : name.toUpperCase(Locale.ROOT);
            this.data = new byte[16];
            System.arraycopy(data, 0, this.data, 0, 12);
        }
    }

    static final class Options {
        float conversionFactor;
        float vertexPositionScale = 1.0f;
        boolean scenario;
        boolean enableAdjacentBoneTag;
        boolean enableBonepairTag;
        boolean enableUnkFlag1;
        boolean enableUnkFlag2;
        boolean enableUnkFlag4;

        float bboxX;
        float bboxY;
        float bboxZ;
        float bboxW = 1.0f;
        float bboxWidth;
        float bboxHeight;
        float bboxDepth;
    }

    private static final class NodeData {
        String name;
        byte[] data;
        byte[] materialHeader;
    }

    private BinRepackWriter() {}

    /** SMD overload: retain source BIN flags/material pairs, but replace its bone table
     * with the skeleton-derived bone lines produced by SmdRepackPipeline. */
    static byte[] write(
            RepackSegmentBuilder.Result result,
            IdxPs2BinMetadata sourceMetadata,
            List<MaterialHeader> materials,
            Options options,
            List<SmdRepackPipeline.BoneLine> smdBones) throws IOException {

        if (sourceMetadata == null || smdBones == null) {
            throw new IllegalArgumentException("Metadata/bones SMD kosong");
        }
        IdxPs2BinMetadata metadata = copyMetadata(sourceMetadata);
        metadata.bones.clear();
        for (SmdRepackPipeline.BoneLine source : smdBones) {
            IdxPs2BinMetadata.Bone bone = new IdxPs2BinMetadata.Bone();
            bone.id = source.id;
            bone.parent = source.parentId;
            bone.x = source.x;
            bone.y = source.y;
            bone.z = source.z;
            metadata.bones.add(bone);
        }
        return write(result, metadata, materials, options);
    }

    private static IdxPs2BinMetadata copyMetadata(IdxPs2BinMetadata source) {
        IdxPs2BinMetadata target = new IdxPs2BinMetadata();
        target.bonePairs.addAll(source.bonePairs);
        target.objFileUseBone = source.objFileUseBone;
        target.enableBonepairTag = source.enableBonepairTag;
        target.enableAdjacentBoneTag = source.enableAdjacentBoneTag;
        target.enableUnkFlag1 = source.enableUnkFlag1;
        target.enableUnkFlag2 = source.enableUnkFlag2;
        target.enableUnkFlag4 = source.enableUnkFlag4;
        target.useIdxMaterial = source.useIdxMaterial;
        target.isScenarioBin = source.isScenarioBin;
        target.autoCalcBoundingBox = source.autoCalcBoundingBox;
        target.boundingBoxPosX = source.boundingBoxPosX;
        target.boundingBoxPosY = source.boundingBoxPosY;
        target.boundingBoxPosZ = source.boundingBoxPosZ;
        target.boundingBoxPosW = source.boundingBoxPosW;
        target.boundingBoxWidth = source.boundingBoxWidth;
        target.boundingBoxHeight = source.boundingBoxHeight;
        target.boundingBoxDepth = source.boundingBoxDepth;
        return target;
    }

    static byte[] write(
            RepackSegmentBuilder.Result result,
            IdxPs2BinMetadata metadata,
            List<MaterialHeader> materials,
            Options options) throws IOException {

        if (result == null || metadata == null || options == null) {
            throw new IllegalArgumentException("Input BIN writer kosong");
        }

        if (!(options.conversionFactor > 0.0f)
                || !Float.isFinite(options.conversionFactor)) {
            throw new IllegalArgumentException("ConversionFactorValue tidak valid");
        }

        if (metadata.bones.size() > 255) {
            throw new IllegalArgumentException("Jumlah bone melebihi 255");
        }

        List<RepackSegmentBuilder.MaterialNode> ordered =
                new ArrayList<>(result.nodes);

        ordered.sort(Comparator.comparing(
                n -> n.materialName == null ? "" : n.materialName
        ));

        List<NodeData> nodes = new ArrayList<>();

        for (RepackSegmentBuilder.MaterialNode node : ordered) {
            NodeData output = new NodeData();
            output.name = node.materialName;
            output.data = writeNode(node, options);
            output.materialHeader = findMaterialHeader(
                    node.materialName, materials);
            nodes.add(output);
        }

        byte[] bonePairs = writeBonePairs(metadata);
        byte[] bones = writeBones(metadata);

        int bonesPoint = HEADER_SIZE;
        int bonePairPoint = bonePairs.length == 0
                ? 0 : bonesPoint + bones.length;

        int materialsPoint = bonesPoint + bones.length;
        if (bonePairs.length != 0) {
            materialsPoint = bonePairPoint + bonePairs.length;
        }

        int firstNodePoint = materialsPoint + nodes.size() * 16;
        int nodeOffset = firstNodePoint;

        for (NodeData node : nodes) {
            putInt(node.materialHeader, 12, nodeOffset);
            nodeOffset += node.data.length;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(nodeOffset);

        byte[] header = new byte[HEADER_SIZE];

        header[0] = 0x30;
        putInt(header, 4, bonesPoint);
        header[8] = 0x04;
        header[9] = (byte) metadata.bones.size();
        putShort(header, 10, nodes.size());
        putInt(header, 12, materialsPoint);

        for (int i = 0x10; i < 0x18; i++) {
            header[i] = (byte) 0xCD;
        }

        putInt(header, 0x1C, bonePairPoint);

        int binFlags = 0xA0000000;
        int versionFlags = 0x20010801;

        if (options.enableAdjacentBoneTag || options.enableBonepairTag) {
            binFlags |= 0x00000200;
            versionFlags = 0x20030818;
        }

        if (options.enableBonepairTag) {
            binFlags |= 0x00000100;
        }

        if (options.enableUnkFlag1) {
            binFlags |= 0x00000001;
        }
        if (options.enableUnkFlag2) {
            binFlags |= 0x00000002;
        }
        if (options.enableUnkFlag4) {
            binFlags |= 0x00000004;
        }

        putInt(header, 0x18, versionFlags);
        putInt(header, 0x24, binFlags);
        header[0x28] = 0x30;

        putFloat(header, 0x30, options.bboxX);
        putFloat(header, 0x34, options.bboxY);
        putFloat(header, 0x38, options.bboxZ);
        putFloat(header, 0x3C, options.bboxW);
        putFloat(header, 0x40, options.bboxWidth);
        putFloat(header, 0x44, options.bboxHeight);
        putFloat(header, 0x48, options.bboxDepth);

        for (int i = 0x4C; i < 0x50; i++) {
            header[i] = (byte) 0xCD;
        }

        out.write(header);
        out.write(bones);
        out.write(bonePairs);

        for (NodeData node : nodes) {
            out.write(node.materialHeader);
        }

        for (NodeData node : nodes) {
            out.write(node.data);
        }

        return out.toByteArray();
    }

    private static byte[] writeBones(IdxPs2BinMetadata metadata) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (IdxPs2BinMetadata.Bone bone : metadata.bones) {
            byte[] line = new byte[16];
            line[0] = (byte) bone.id;
            line[1] = (byte) bone.parent;

            putFloat(line, 4, bone.x);
            putFloat(line, 8, bone.y);
            putFloat(line, 12, bone.z);

            out.write(line, 0, line.length);
        }

        return out.toByteArray();
    }

    private static byte[] writeBonePairs(IdxPs2BinMetadata metadata) {
        if (metadata.bonePairs.isEmpty()) {
            return new byte[0];
        }

        int rawLength = 4 + metadata.bonePairs.size() * 8;
        byte[] result = new byte[align(rawLength)];

        putInt(result, 0, metadata.bonePairs.size());

        int offset = 4;
        for (IdxPs2BinMetadata.BonePair pair : metadata.bonePairs) {
            putShort(result, offset, pair.b1);
            putShort(result, offset + 2, pair.b2);
            putShort(result, offset + 4, pair.b3);
            putShort(result, offset + 6, pair.b4);
            offset += 8;
        }

        return result;
    }

    private static byte[] writeNode(
            RepackSegmentBuilder.MaterialNode node,
            Options options) throws IOException {

        if (node.segments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Node tidak memiliki segment: " + node.materialName);
        }

        if (node.segments.size() > 255) {
            throw new IllegalArgumentException("Segment node melebihi 255");
        }

        int boneCount = options.scenario ? 0 : node.bonesIDs.size();
        int headerLength = align(Math.max(16, 4 + boneCount));

        RepackSegmentBuilder.Segment first = node.segments.get(0);
        int firstVertexBlock = align(first.vertices.size() * 24);

        int firstSubmeshOffset = headerLength + 0x30 + firstVertexBlock;

        if (!options.scenario) {
            firstSubmeshOffset += 0x10 + first.weightMaps.size() * 0x20;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        byte[] nodeHeader = new byte[headerLength];
        putShort(nodeHeader, 0, firstSubmeshOffset);
        nodeHeader[2] = (byte) (node.segments.size() - 1);
        nodeHeader[3] = (byte) boneCount;

        if (!options.scenario) {
            for (int i = 0; i < boneCount; i++) {
                nodeHeader[4 + i] = (byte) (int) node.bonesIDs.get(i);
            }
        }

        out.write(nodeHeader);

        for (int i = 0; i < node.segments.size(); i++) {
            RepackSegmentBuilder.Segment segment = node.segments.get(i);

            if (segment.vertices.isEmpty() || segment.vertices.size() > 44) {
                throw new IllegalArgumentException("Jumlah vertex segment tidak valid");
            }

            if (!options.scenario) {
                out.write(writeWeightMaps(segment));
            }

            out.write(writeVifHeader(
                    segment.vertices.size(),
                    options.conversionFactor,
                    i == 0
            ));

            out.write(writeVertices(segment, options.scenario, options.conversionFactor, options.vertexPositionScale));

            if (i != 0) {
                out.write(writeEndTag(i == node.segments.size() - 1));
            }
        }

        return out.toByteArray();
    }

    private static byte[] writeWeightMaps(
            RepackSegmentBuilder.Segment segment) {

        if (segment.weightMaps.isEmpty()
                || segment.weightMaps.size() > 15) {
            throw new IllegalArgumentException("Jumlah weight map tidak valid");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        int length = segment.weightMaps.size() * 2;

        out.write(length);
        out.write(0);
        out.write(0);
        out.write(0x10);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(4);
        out.write(4);
        out.write(0);
        out.write(1);
        out.write(0);
        out.write(0x80);
        out.write(length);
        out.write(0x6C);

        for (RepackStartStructure.WeightMap map : segment.weightMaps) {
            byte[] line = new byte[0x20];

            putInt(line, 0, map.boneId1);
            putInt(line, 4, map.boneId2);
            putInt(line, 8, map.boneId3);
            putInt(line, 12, map.links);

            putFloat(line, 16, map.weight1);
            putFloat(line, 20, map.weight2);
            putFloat(line, 24, map.weight3);
            putInt(line, 28, segment.weightMaps.size());

            out.write(line, 0, line.length);
        }

        return out.toByteArray();
    }

    private static byte[] writeVifHeader(
            int vertexCount,
            float conversionFactor,
            boolean first) {

        int blockParts = align(vertexCount * 24) / 16;
        int blockParts8 = blockParts * 2;

        byte[] result = new byte[] {
                0x01, 0x00, 0x00, 0x10,
                0x00, 0x00, 0x00, 0x00,
                0x04, 0x04, 0x00, 0x01,
                0x20, (byte) 0x80, 0x01, 0x6C,

                (byte) vertexCount, (byte) 0x80, 0x00, 0x00,
                0x00, 0x40, 0x2E, 0x30,
                0x12, 0x05, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,

                (byte) blockParts, 0x00, 0x00,
                (byte) (first ? 0x60 : 0x10),
                0x00, 0x00, 0x00, 0x00,
                0x04, 0x04, 0x00, 0x01,
                0x21, (byte) 0x80,
                (byte) blockParts8, 0x6D
        };

        putFloat(result, 28, conversionFactor);
        return result;
    }

    private static byte[] writeVertices(
            RepackSegmentBuilder.Segment segment,
            boolean scenario, float conversionFactor,
            float vertexPositionScale) {

        byte[] result = new byte[align(segment.vertices.size() * 24)];

        int offset = 0;

        for (RepackStartStructure.Vertex vertex : segment.vertices) {
            byte[] line = new byte[24];

            putShort(line, 0, toShort((vertex.x * vertexPositionScale) / conversionFactor));
            putShort(line, 2, toShort((vertex.y * vertexPositionScale) / conversionFactor));
            putShort(line, 4, toShort((vertex.z * vertexPositionScale) / conversionFactor));
            putShort(line, 14, vertex.indexComplement);

            putShort(line, 8, toShort(vertex.nx * 127.0f));
            putShort(line, 10, toShort(vertex.ny * 127.0f));
            putShort(line, 12, toShort(vertex.nz * 127.0f));

            if (scenario) {
                putShort(line, 6, vertex.indexMount);
                putShort(line, 8, toShort(vertex.u * 255.0f));
                putShort(line, 10, toShort(vertex.v * 255.0f));

                putShort(line, 12, 1);
                putShort(line, 16, toShort(vertex.r * 128.0f));
                putShort(line, 18, toShort(vertex.g * 128.0f));
                putShort(line, 20, toShort(vertex.b * 128.0f));
                putShort(line, 22, toShort(vertex.a * 128.0f));
            } else {
                putShort(line, 6, vertex.weightMapReference);
                putShort(line, 16, toShort(vertex.u * 255.0f));
                putShort(line, 18, toShort(vertex.v * 255.0f));
                putShort(line, 20, 1);
                putShort(line, 22, vertex.indexMount);
            }

            System.arraycopy(line, 0, result, offset, line.length);
            offset += line.length;
        }

        return result;
    }

    private static byte[] writeEndTag(boolean last) {
        return new byte[] {
                0x00, 0x00, 0x00,
                (byte) (last ? 0x60 : 0x10),
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x17
        };
    }

    private static byte[] findMaterialHeader(
            String name,
            List<MaterialHeader> materials) {

        String key = name == null ? "" : name.toUpperCase(Locale.ROOT);

        if (materials != null) {
            for (MaterialHeader material : materials) {
                if (material.name.equals(key)) {
                    return material.data.clone();
                }
            }
        }

        byte[] fallback = new byte[16];
        fallback[2] = (byte) 0xFF;
        fallback[3] = (byte) 0xFF;
        fallback[4] = (byte) 0xFF;
        fallback[11] = (byte) 0xFF;
        return fallback;
    }

    private static int toShort(float value) {
 if (!Float.isFinite(value)) throw new IllegalArgumentException("Nilai tidak valid");
 long rounded = (long)Math.copySign(Math.floor(Math.abs((double)value)+0.5), value);
 return (int)Math.max(-32768, Math.min(32767, rounded));
}

    private static int align(int value) {
        return (value + ALIGNMENT - 1) & ~(ALIGNMENT - 1);
    }

    private static void putShort(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
    }

    private static void putInt(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static void putFloat(byte[] data, int offset, float value) {
        putInt(data, offset, Float.floatToIntBits(value));
    }
}
