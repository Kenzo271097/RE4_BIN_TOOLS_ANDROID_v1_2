package com.kenzo.re4bintools;

final class RepackValidator {

    static final class Report {
        int nodes;
        int segments;
        int vertices;
        int faces;
        int weightMaps;
        int errors;

        String summary() {
            return "Nodes: " + nodes
                    + "\nSegments: " + segments
                    + "\nVertices: " + vertices
                    + "\nStrips: " + faces
                    + "\nWeight maps: " + weightMaps
                    + "\nErrors: " + errors;
        }
    }

    static Report validate(RepackSegmentBuilder.Result result) {
        if (result == null) {
            throw new IllegalArgumentException("Hasil segment kosong");
        }

        Report report = new Report();

        for (RepackSegmentBuilder.MaterialNode node : result.nodes) {
            if (node == null || node.segments == null
                    || node.bonesIDs == null) {
                report.errors++;
                continue;
            }

            report.nodes++;

            if (node.segments.isEmpty()
                    || node.segments.size() > RepackSegmentBuilder.MAX_SEGMENTS) {
                report.errors++;
            }

            for (int bone : node.bonesIDs) {
                if (bone < 0 || bone > 255) report.errors++;
            }

            for (RepackSegmentBuilder.Segment segment : node.segments) {
                if (segment == null || segment.vertices == null
                        || segment.weightMaps == null
                        || segment.stripLengths == null
                        || segment.sourceVertexIndices == null) {
                    report.errors++;
                    continue;
                }

                report.segments++;

                int vertexCount = segment.vertices.size();
                int weightCount = segment.weightMaps.size();

                report.vertices += vertexCount;
                report.weightMaps += weightCount;
                report.faces += segment.stripLengths.size();

                if (vertexCount == 0
                        || vertexCount > RepackSegmentBuilder.MAX_VERTICES) {
                    report.errors++;
                }

                if (weightCount == 0
                        || weightCount > RepackSegmentBuilder.MAX_WEIGHT_MAPS) {
                    report.errors++;
                }

                if (segment.sourceVertexIndices.size() != vertexCount) {
                    report.errors++;
                }

                int stripTotal = 0;
                for (int length : segment.stripLengths) {
                    if (length < 3 || length > RepackSegmentBuilder.MAX_VERTICES) {
                        report.errors++;
                    }
                    stripTotal += length;
                }

                if (stripTotal != vertexCount || segment.stripLengths.isEmpty()) {
                    report.errors++;
                    continue;
                }

                for (RepackStartStructure.WeightMap map : segment.weightMaps) {
                    if (map == null || map.links < 1 || map.links > 3) {
                        report.errors++;
                        continue;
                    }

                    float sum = 0f;
                    float[] weights = {map.weight1, map.weight2, map.weight3};
                    int[] bones = {map.boneId1, map.boneId2, map.boneId3};

                    for (int i = 0; i < 3; i++) {
                        float w = weights[i];

                        if (i < map.links) {
                            if (!Float.isFinite(w) || w < 0f || w > 1f) {
                                report.errors++;
                            }
                            sum += w;

                            int localBone = bones[i];
                            if (localBone < 0 || (localBone & 3) != 0
                                    || localBone / 4 >= node.bonesIDs.size()) {
                                report.errors++;
                            }
                        } else if (bones[i] != 0 || w != 0f) {
                            report.errors++;
                        }
                    }

                    if (!Float.isFinite(sum) || Math.abs(sum - 1f) > 0.001f) {
                        report.errors++;
                    }
                }

                int cursor = 0;

                for (int length : segment.stripLengths) {
                    int mountStatus = 0;

                    for (int j = 0; j < length; j++) {
                        RepackStartStructure.Vertex vertex =
                                segment.vertices.get(cursor);

                        if (vertex == null) {
                            report.errors++;
                            cursor++;
                            continue;
                        }

                        int expectedMount = 0;

                        if (j >= 2) {
                            if (mountStatus == 0 || mountStatus == 0xFFFF) {
                                mountStatus = 1;
                                expectedMount = 1;
                            } else {
                                mountStatus = 0xFFFF;
                                expectedMount = 0xFFFF;
                            }
                        }

                        if (vertex.indexMount != expectedMount) {
                            report.errors++;
                        }

                        int ref = vertex.weightMapReference;

                        if (ref < 0 || (ref & 1) != 0
                                || ref / 2 >= weightCount) {
                            report.errors++;
                        } else {
                            RepackStartStructure.WeightMap referenced =
                                    segment.weightMaps.get(ref / 2);

                            if (!validLocalMap(referenced, node)) {
                                report.errors++;
                            }
                        }

                        int expectedComplement =
                                cursor >= 2 && expectedMount == 0 ? 1 : 0;

                        if (vertex.indexComplement != expectedComplement) {
                            report.errors++;
                        }

                        cursor++;
                    }
                }

                if (cursor != vertexCount) report.errors++;
            }
        }

        if (report.segments != result.totalSegments
                || report.vertices != result.totalVertices
                || report.faces != result.totalFaces
                || report.faces != result.totalStrips) {
            report.errors++;
        }

        return report;
    }

    private static boolean validLocalMap(
            RepackStartStructure.WeightMap map,
            RepackSegmentBuilder.MaterialNode node) {

        if (map == null || map.links < 1 || map.links > 3) {
            return false;
        }

        for (int i = 0; i < map.links; i++) {
            int id;

            if (i == 0) id = map.boneId1;
            else if (i == 1) id = map.boneId2;
            else id = map.boneId3;

            if (id < 0 || (id & 3) != 0
                    || id / 4 >= node.bonesIDs.size()) {
                return false;
            }
        }

        return true;
    }
}
