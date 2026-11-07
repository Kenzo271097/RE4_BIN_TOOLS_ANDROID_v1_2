package com.kenzo.re4bintools;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BinSourceAdapter {
    static final class Data {
        IdxPs2BinMetadata metadata;
        List<BinRepackWriter.MaterialHeader> materials = new ArrayList<>();
        BinRepackWriter.Options options;
    }

    static Data convert(BinSourceMetadata source, float factor) {
        if (source == null) throw new IllegalArgumentException("BIN sumber kosong");

        Data out = new Data();
        out.metadata = new IdxPs2BinMetadata();
        ByteBuffer b;

        for (byte[] raw : source.bones) {
            b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
            IdxPs2BinMetadata.Bone bone = new IdxPs2BinMetadata.Bone();
            bone.id = raw[0] & 255;
            bone.parent = raw[1];
            bone.x = b.getFloat(4);
            bone.y = b.getFloat(8);
            bone.z = b.getFloat(12);
            out.metadata.bones.add(bone);
        }

        for (int[] raw : source.bonePairs) {
            IdxPs2BinMetadata.BonePair pair = new IdxPs2BinMetadata.BonePair();
            pair.b1 = raw[0]; pair.b2 = raw[1];
            pair.b3 = raw[2]; pair.b4 = raw[3];
            out.metadata.bonePairs.add(pair);
        }

        for (int i = 0; i < source.materials.length; i++) {
            out.materials.add(new BinRepackWriter.MaterialHeader(
                String.format(Locale.ROOT, "MATERIAL_%03d", i),
                source.materials[i]));
        }

        out.options = new BinRepackWriter.Options();
        out.options.conversionFactor = factor;
        out.options.enableAdjacentBoneTag = (source.binFlags & 0x200) != 0;
        out.options.enableBonepairTag = (source.binFlags & 0x100) != 0;
        out.options.enableUnkFlag1 = (source.binFlags & 1) != 0;
        out.options.enableUnkFlag2 = (source.binFlags & 2) != 0;
        out.options.enableUnkFlag4 = (source.binFlags & 4) != 0;

        b = ByteBuffer.wrap(source.header).order(ByteOrder.LITTLE_ENDIAN);
        out.options.bboxX = b.getFloat(0x30);
        out.options.bboxY = b.getFloat(0x34);
        out.options.bboxZ = b.getFloat(0x38);
        out.options.bboxW = b.getFloat(0x3C);
        out.options.bboxWidth = b.getFloat(0x40);
        out.options.bboxHeight = b.getFloat(0x44);
        out.options.bboxDepth = b.getFloat(0x48);

        return out;
    }
}
