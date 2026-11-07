package com.kenzo.re4bintools;

import java.util.*;

final class RepackSegmentBuilder {

    static final int MAX_VERTICES = 44;
    static final int MAX_WEIGHT_MAPS = 15;
    static final int MAX_SEGMENTS = 255;

    static final class Segment {
        String materialName;

        List<RepackStartStructure.Vertex> vertices = new ArrayList<>();
        List<RepackStartStructure.WeightMap> weightMaps = new ArrayList<>();
        List<Integer> sourceVertexIndices = new ArrayList<>();
        List<Integer> stripLengths = new ArrayList<>();
    }

    static final class MaterialNode {
        String materialName;
        List<Integer> bonesIDs = new ArrayList<>();
        List<Segment> segments = new ArrayList<>();
    }

    static final class Result {
        List<MaterialNode> nodes = new ArrayList<>();
        int totalSegments;
        int totalVertices;
        int totalFaces;
        int totalStrips;
    }

    static Result build(
            RepackStartStructure.Model model,
            RepackStripCompressor.Result compressed) {

        if (model == null || compressed == null) {
            throw new IllegalArgumentException("Input repack kosong");
        }

        if (model.materials.size() != compressed.materials.size()) {
            throw new IllegalArgumentException(
                    "Jumlah material compressor tidak cocok");
        }

        Result result = new Result();

        for (int m = 0; m < model.materials.size(); m++) {
            RepackStartStructure.MaterialGroup material =
                    model.materials.get(m);

            RepackStripCompressor.MaterialStrips materialStrips =
                    compressed.materials.get(m);

            if (!material.name.equals(materialStrips.materialName)) {
                throw new IllegalArgumentException(
                        "Urutan material tidak cocok: " + material.name);
            }

            List<Integer> usedBones = new ArrayList<>();
            List<Segment> allSegments = new ArrayList<>();

            // LINIER: stable sort, mengikuti OrderByDescending Jaderlink.
            List<RepackStripCompressor.Strip> faces =
                    new ArrayList<>(materialStrips.strips);

            faces.sort(
                    Comparator.comparingInt(
                            (RepackStripCompressor.Strip s) ->
                                    s.vertices.size()
                    ).reversed()
            );

            Segment current = null;
            int vertexCount = 0;

            // Daftar WeightMap sumber untuk kapasitas segment.
            List<RepackStartStructure.WeightMap> sourceMaps =
                    new ArrayList<>();

            while (!faces.isEmpty()) {
                // Jaderlink menutup segment pada iterasi setelah
                // kapasitas sebelumnya dinyatakan penuh.
                if (vertexCount > MAX_VERTICES) {
                    current = null;
                    sourceMaps.clear();
                    vertexCount = 0;
                    continue;
                }

                if (current == null) {
                    current = newSegment(material.name);
                    sourceMaps.clear();
                    allSegments.add(current);
                }

                

                int selectedIndex = -1;

                // Coba face pertama terlebih dahulu.
                if (fitsFace(
                        faces.get(0), vertexCount,
                        sourceMaps, material)) {
                    selectedIndex = 0;
                } else {
                    // Look-ahead: cari face berikutnya yang masih muat.
                    for (int i = 1; i < faces.size(); i++) {
                        if (fitsFace(
                                faces.get(i), vertexCount,
                                sourceMaps, material)) {
                            selectedIndex = i;
                            break;
                        }
                    }
                }

                // Tidak ada face yang muat: tutup segment.
                if (selectedIndex < 0) {
                    if (current.vertices.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Face tidak dapat dimasukkan ke segment: "
                                        + material.name);
                    }

                    current = null;
                    sourceMaps.clear();
                    vertexCount = MAX_VERTICES + 1;
                    continue;
                }

                RepackStripCompressor.Strip strip =
                        faces.remove(selectedIndex);

                // Gabungkan WeightMap sumber seperti
                // CreateNewIntermediaryWeightMapList milik Jaderlink.
                for (RepackStartStructure.Vertex vertex : strip.vertices) {
                    validateVertex(vertex, material);

                    RepackStartStructure.WeightMap map =
                            material.weightMaps.get(vertex.weightMapId);

                    if (!containsMap(sourceMaps, map)) {
                        sourceMaps.add(map);
                    }
                }

                vertexCount += strip.vertices.size();
                current.stripLengths.add(strip.vertices.size());

                int mountStatus = 0;
                int stripVertexIndex = 0;

                for (RepackStartStructure.Vertex source : strip.vertices) {
                    RepackStartStructure.Vertex vertex =
                            copyVertex(source);

                    RepackStartStructure.WeightMap sourceMap =
                            material.weightMaps.get(source.weightMapId);

                    RepackStartStructure.WeightMap finalMap =
                            convertWeightMap(sourceMap, usedBones);

                    int localMap = findMap(current.weightMaps, finalMap);

                    if (localMap < 0) {
                        current.weightMaps.add(finalMap);
                        localMap = current.weightMaps.size() - 1;
                    }

                    vertex.weightMapReference = localMap * 2;

                    if (stripVertexIndex >= 2) {
                        if (mountStatus == 0 || mountStatus == 0xFFFF) {
                            mountStatus = 1;
                            vertex.indexMount = 1;
                        } else if (mountStatus == 1) {
                            mountStatus = 0xFFFF;
                            vertex.indexMount = 0xFFFF;
                        }
                    }

                    current.vertices.add(vertex);
                    current.sourceVertexIndices.add(-1);
                    stripVertexIndex++;
                }

                result.totalStrips++;
            }

            // IndexComplement dihitung dari indeks global segment.
            for (Segment segment : allSegments) {
                for (int i = 2; i < segment.vertices.size(); i++) {
                    RepackStartStructure.Vertex vertex =
                            segment.vertices.get(i);

                    if (vertex.indexMount == 0) {
                        vertex.indexComplement = 1;
                    }
                }
            }

            // Satu node maksimal 255 segment.
            for (int startIndex = 0;
                    startIndex < allSegments.size();
                    startIndex += MAX_SEGMENTS) {

                MaterialNode node = new MaterialNode();
                node.materialName = material.name;

                int endIndex = Math.min(
                        startIndex + MAX_SEGMENTS,
                        allSegments.size());

                node.segments.addAll(
                        allSegments.subList(startIndex, endIndex));

                node.bonesIDs.addAll(usedBones);
                result.nodes.add(node);
            }

            result.totalSegments += allSegments.size();

            for (Segment segment : allSegments) {
                result.totalVertices += segment.vertices.size();
                result.totalFaces += segment.stripLengths.size();
            }
        }

        return result;
    }

    private static boolean fitsFace(
            RepackStripCompressor.Strip strip,
            int currentVertexCount,
            List<RepackStartStructure.WeightMap> currentMaps,
            RepackStartStructure.MaterialGroup material) {

        if (strip == null || strip.vertices.isEmpty()) {
            return false;
        }

        if (currentVertexCount + strip.vertices.size() > MAX_VERTICES) {
            return false;
        }

        List<RepackStartStructure.WeightMap> combined =
                new ArrayList<>(currentMaps);

        for (RepackStartStructure.Vertex vertex : strip.vertices) {
            validateVertex(vertex, material);

            RepackStartStructure.WeightMap map =
                    material.weightMaps.get(vertex.weightMapId);

            if (!containsMap(combined, map)) {
                combined.add(map);
            }
        }

        return combined.size() <= MAX_WEIGHT_MAPS;
    }

    private static int countCurrentStripVertices(List<Integer> lengths) {
        int total = 0;

        for (int length : lengths) {
            total += length;
        }

        return total;
    }

    private static Segment newSegment(String materialName) {
        Segment segment = new Segment();
        segment.materialName = materialName;
        return segment;
    }

    private static RepackStartStructure.Vertex copyVertex(
            RepackStartStructure.Vertex source) {

        RepackStartStructure.Vertex v =
                new RepackStartStructure.Vertex();

        v.x = source.x;
        v.y = source.y;
        v.z = source.z;
        v.u = source.u;
        v.v = source.v;
        v.nx = source.nx;
        v.ny = source.ny;
        v.nz = source.nz;
        v.r = source.r;
        v.g = source.g;
        v.b = source.b;
        v.a = source.a;
        v.weightMapId = source.weightMapId;

        return v;
    }

    private static void validateVertex(
            RepackStartStructure.Vertex vertex,
            RepackStartStructure.MaterialGroup material) {

        if (vertex == null || vertex.weightMapId < 0
                || vertex.weightMapId >= material.weightMaps.size()) {
            throw new IllegalArgumentException("Vertex/WeightMap tidak valid");
        }
    }

    private static RepackStartStructure.WeightMap convertWeightMap(
            RepackStartStructure.WeightMap source,
            List<Integer> usedBones) {

        int b1 = 0;
        int b2 = 0;
        int b3 = 0;

        if (source.links >= 1) {
            b1 = getBoneIndex(usedBones, source.boneId1) * 4;
        }

        if (source.links >= 2) {
            b2 = getBoneIndex(usedBones, source.boneId2) * 4;
        }

        if (source.links >= 3) {
            b3 = getBoneIndex(usedBones, source.boneId3) * 4;
        }

        return new RepackStartStructure.WeightMap(
                source.links,
                b1, source.weight1,
                b2, source.weight2,
                b3, source.weight3);
    }

    private static int getBoneIndex(
            List<Integer> usedBones, int boneId) {

        if (boneId < 0 || boneId > 255) {
            throw new IllegalArgumentException("Bone ID di luar rentang byte");
        }

        int index = usedBones.indexOf(boneId);

        if (index < 0) {
            usedBones.add(boneId);
            index = usedBones.size() - 1;
        }

        return index;
    }

    private static boolean containsMap(
            List<RepackStartStructure.WeightMap> maps,
            RepackStartStructure.WeightMap candidate) {

        return findMap(maps, candidate) >= 0;
    }

    private static int findMap(
            List<RepackStartStructure.WeightMap> maps,
            RepackStartStructure.WeightMap candidate) {

        for (int i = 0; i < maps.size(); i++) {
            if (maps.get(i).sameValue(candidate)) {
                return i;
            }
        }

        return -1;
    }
}
