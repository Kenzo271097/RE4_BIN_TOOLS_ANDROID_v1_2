package com.kenzo.re4bintools;

import java.io.IOException;
import java.io.InputStream;

final class RepackPipeline {

    static final class Output {
        RepackStartStructure.Model start;
        RepackStripCompressor.Result compressed;
        RepackSegmentBuilder.Result segments;
        RepackValidator.Report report;
        float farthestVertex;
        float conversionFactor;
    }

    static Output process(InputStream input, int selectedBone)
            throws IOException {

        if (input == null) {
            throw new IllegalArgumentException("Input OBJ kosong");
        }

        ObjParser.Model parsed = ObjParser.parse(input);
        float farthest = 0.0f;
        for (ObjParser.Vec3 v : parsed.positions) {
            farthest = Math.max(farthest, Math.abs(v.x));
            farthest = Math.max(farthest, Math.abs(v.y));
            farthest = Math.max(farthest, Math.abs(v.z));
        }
        float factor = farthest * 100.0f / 32767.0f;
        if (!(factor > 0.0f) || !Float.isFinite(factor))
            throw new IllegalArgumentException("ConversionFactor tidak valid");

        RepackStartStructure.Model start =
                RepackStartStructure.build(parsed, selectedBone);

        RepackStripCompressor.Result compressed =
                RepackStripCompressor.compress(start);

        RepackSegmentBuilder.Result segments =
                RepackSegmentBuilder.build(start, compressed);

        RepackValidator.Report report =
                RepackValidator.validate(segments);

        if (report.errors != 0) {
            throw new IllegalStateException(
                    "Validasi gagal:\n" + report.summary());
        }

        Output output = new Output();
        output.start = start;
        output.compressed = compressed;
        output.segments = segments;
        output.report = report;
        output.farthestVertex = farthest;
        output.conversionFactor = factor;

        return output;
    }
}
