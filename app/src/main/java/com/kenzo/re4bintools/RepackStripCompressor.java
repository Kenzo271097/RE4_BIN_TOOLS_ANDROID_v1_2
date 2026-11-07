package com.kenzo.re4bintools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class RepackStripCompressor {

    static final int MAX_VERTICES = 44;
    static final int MAX_WEIGHT_MAPS = 15;

    static final class Strip {
        final List<RepackStartStructure.Vertex> vertices =
                new ArrayList<>();

        final List<RepackStartStructure.WeightMap> weightMaps =
                new ArrayList<>();
    }

    static final class MaterialStrips {
        String materialName;
        final List<Strip> strips = new ArrayList<>();
    }

    static final class Result {
        final List<MaterialStrips> materials = new ArrayList<>();
        int totalStrips;
        int totalVertices;
        int totalTriangles;
    }

    private static final int[][] ORDER_123 = {
            {1, 2, 3},
            {2, 3, 1},
            {3, 1, 2}
    };

    private static final int[][] ORDER_321 = {
            {1, 3, 2},
            {2, 1, 3},
            {3, 2, 1}
    };

    private static final class PositionKey {
        final float x;
        final float y;
        final float z;

        PositionKey(RepackStartStructure.Vertex v) {
            x = v.x;
            y = v.y;
            z = v.z;
        }

        @Override
        public int hashCode() {
            int h = 17;
            h = h * 23 + floatHash(x);
            h = h * 23 + floatHash(y);
            h = h * 23 + floatHash(z);
            return h;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof PositionKey)) return false;

            PositionKey other = (PositionKey) object;

            return x == other.x
                    && y == other.y
                    && z == other.z;
        }
    }

    private static int floatHash(float value) {
        // .NET Single.GetHashCode menganggap +0 dan -0 sama.
        if (value == 0.0f) return 0;
        return Float.floatToIntBits(value);
    }

    private static final class Triangle {
        final RepackStartStructure.Vertex[] v =
                new RepackStartStructure.Vertex[3];

        final int index;

        Triangle(
                RepackStartStructure.Vertex a,
                RepackStartStructure.Vertex b,
                RepackStartStructure.Vertex c,
                int index) {

            v[0] = a;
            v[1] = b;
            v[2] = c;
            this.index = index;
        }

        RepackStartStructure.Vertex get(int oneBasedIndex) {
            if (oneBasedIndex < 1 || oneBasedIndex > 3) {
                throw new IllegalArgumentException(
                        "Index triangle tidak valid");
            }
            return v[oneBasedIndex - 1];
        }
    }

    private static final class MatchFirst {
        final int[] lastOrder;
        final int[] nextOrder;

        MatchFirst(int[] lastOrder, int[] nextOrder) {
            this.lastOrder = lastOrder;
            this.nextOrder = nextOrder;
        }
    }

    private static final class MatchNext {
        final int[] nextOrder;

        MatchNext(int[] nextOrder) {
            this.nextOrder = nextOrder;
        }
    }

    private static final class MaterialContext {
        final RepackStartStructure.MaterialGroup material;

        final TreeMap<Integer, Triangle> active =
                new TreeMap<>();

        final Map<PositionKey, List<Integer>> triDic =
                new HashMap<>();

        int nextStartKey;

        MaterialContext(
                RepackStartStructure.MaterialGroup material) {
            this.material = material;
        }
    }

    static Result compress(RepackStartStructure.Model model) {
        if (model == null) {
            throw new IllegalArgumentException(
                    "Model repack kosong");
        }

        Result result = new Result();

        for (RepackStartStructure.MaterialGroup material
                : model.materials) {

            if (material == null) {
                throw new IllegalArgumentException(
                        "Material group kosong");
            }

            MaterialStrips materialResult = new MaterialStrips();
            materialResult.materialName =
                    material.name == null
                            ? ""
                            : material.name.trim()
                                    .toUpperCase(java.util.Locale.ROOT);

            MaterialContext context =
                    createMaterialContext(material);

            result.totalTriangles += context.active.size();

            compressMaterial(context, materialResult, result);

            // Jangan menghilangkan material kosong.
            // Urutan ini harus tetap sama dengan model.materials.
            result.materials.add(materialResult);
        }

        return result;
    }

    private static MaterialContext createMaterialContext(
            RepackStartStructure.MaterialGroup material) {

        MaterialContext context = new MaterialContext(material);

        for (int i = 0; i < material.faces.size(); i++) {
            RepackStartStructure.Face face = material.faces.get(i);

            if (face == null
                    || face.vertices == null
                    || face.vertices.length < 3) {
                continue;
            }

            int ia = face.vertices[0];
            int ib = face.vertices[1];
            int ic = face.vertices[2];

            if (!validVertex(material, ia)
                    || !validVertex(material, ib)
                    || !validVertex(material, ic)) {
                continue;
            }

            if (face.weightMapId < 0
                    || face.weightMapId >= material.weightMaps.size()) {
                throw new IllegalArgumentException(
                        "Weight map face tidak valid: " + i);
            }

            RepackStartStructure.Vertex a =
                    copyVertex(material.vertices.get(ia));

            RepackStartStructure.Vertex b =
                    copyVertex(material.vertices.get(ib));

            RepackStartStructure.Vertex c =
                    copyVertex(material.vertices.get(ic));

            Triangle triangle = new Triangle(a, b, c, i);

            context.active.put(i, triangle);

            addTriDic(context, a, i);
            addTriDic(context, b, i);
            addTriDic(context, c, i);
        }

        return context;
    }

    private static boolean validVertex(
            RepackStartStructure.MaterialGroup material,
            int index) {

        return index >= 0 && index < material.vertices.size();
    }

    private static RepackStartStructure.Vertex copyVertex(
            RepackStartStructure.Vertex source) {
        return copyVertex(source, source.weightMapId);
    }

    private static RepackStartStructure.Vertex copyVertex(
            RepackStartStructure.Vertex source,
            int weightMapId) {

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

        v.weightMapId = weightMapId;

        return v;
    }

    private static void addTriDic(
            MaterialContext context,
            RepackStartStructure.Vertex vertex,
            int triangleIndex) {

        PositionKey key = new PositionKey(vertex);

        List<Integer> indexes = context.triDic.get(key);

        if (indexes == null) {
            indexes = new ArrayList<>();
            context.triDic.put(key, indexes);
        }

        // Sama seperti sumber C#: setiap posisi A/B/C
        // menambahkan indeks triangle.
        indexes.add(triangleIndex);
    }

    private static void compressMaterial(
            MaterialContext context,
            MaterialStrips output,
            Result result) {

        if (context.active.isEmpty()) return;

        Strip currentStrip = new Strip();

        Triangle last = takeNextTriangle(context);

        if (last == null) return;

        int[] lastOrder = {1, 2, 3};

        boolean isFirst = true;
        int triangleAddedCount = 1;

        while (!context.active.isEmpty()) {

            boolean isAdded = false;
            int candidateCount = 0;

            TreeSet<Integer> candidateIndexes =
                    collectCandidates(context, last);

            List<Integer> orderedCandidates =
                    new ArrayList<>(candidateIndexes);

            for (int candidateIndex : orderedCandidates) {

                Triangle candidate =
                        context.active.get(candidateIndex);

                if (candidate == null) continue;

                if (!hasOnePositionEqualHashCode(last, candidate)
                        || !hasTwoVertexEqual(context, last, candidate)) {
                    continue;
                }

                candidateCount++;

                int[][] nextOrder = ORDER_321;

                if (triangleAddedCount % 2 == 0 && !isFirst) {
                    nextOrder = ORDER_123;
                }

                if (isFirst) {

                    MatchFirst match = checkOrderFirst(
                            context,
                            last,
                            candidate,
                            lastOrder,
                            nextOrder
                    );

                    if (match == null) continue;

                    addVertex(context, currentStrip,
                            last.get(match.lastOrder[0]));

                    addVertex(context, currentStrip,
                            last.get(match.lastOrder[1]));

                    addVertex(context, currentStrip,
                            last.get(match.lastOrder[2]));

                    addVertex(context, currentStrip,
                            candidate.get(match.nextOrder[2]));

                    last = candidate;
                    lastOrder = match.nextOrder.clone();

                    context.active.remove(candidateIndex);

                    isAdded = true;
                    isFirst = false;
                    triangleAddedCount++;

                    break;

                } else {

                    MatchNext match = checkOrderNext(
                            context,
                            last,
                            candidate,
                            lastOrder,
                            nextOrder
                    );

                    if (match == null) continue;

                    RepackStartStructure.Vertex newVertex =
                            candidate.get(match.nextOrder[2]);

                    if (!canAddWeightMap(
                            context, currentStrip, newVertex)) {
                        continue;
                    }

                    addVertex(context, currentStrip, newVertex);

                    last = candidate;
                    lastOrder = match.nextOrder.clone();

                    context.active.remove(candidateIndex);

                    isAdded = true;
                    isFirst = false;
                    triangleAddedCount++;

                    break;
                }
            }

            if (candidateCount == 0 || !isAdded) {

                if (isFirst) {
                    addVertex(context, currentStrip, last.v[0]);
                    addVertex(context, currentStrip, last.v[1]);
                    addVertex(context, currentStrip, last.v[2]);
                }

                finishStrip(currentStrip, output, result);

                currentStrip = new Strip();

                lastOrder = new int[]{1, 2, 3};

                last = takeNextTriangle(context);

                isFirst = true;
                triangleAddedCount = 1;

                if (last == null) break;

                continue;
            }

            if (currentStrip.vertices.size() >= MAX_VERTICES
                    || currentStrip.weightMaps.size()
                    >= MAX_WEIGHT_MAPS) {

                finishStrip(currentStrip, output, result);

                currentStrip = new Strip();

                lastOrder = new int[]{1, 2, 3};

                last = takeNextTriangle(context);

                isFirst = true;
                triangleAddedCount = 1;

                if (last == null) break;
            }
        }

        // Bagian akhir algoritma Jaderlink.
        if (last != null) {
            if (isFirst) {
                addVertex(context, currentStrip, last.v[0]);
                addVertex(context, currentStrip, last.v[1]);
                addVertex(context, currentStrip, last.v[2]);
            }

            finishStrip(currentStrip, output, result);
        }
    }

    private static TreeSet<Integer> collectCandidates(
            MaterialContext context,
            Triangle last) {

        TreeSet<Integer> indexes = new TreeSet<>();

        for (RepackStartStructure.Vertex vertex : last.v) {
            List<Integer> found =
                    context.triDic.get(new PositionKey(vertex));

            if (found != null) {
                indexes.addAll(found);
            }
        }

        indexes.removeIf(index -> !context.active.containsKey(index));

        return indexes;
    }

    private static Triangle takeNextTriangle(
            MaterialContext context) {

        while (context.nextStartKey <= Integer.MAX_VALUE) {

            Map.Entry<Integer, Triangle> entry =
                    context.active.ceilingEntry(context.nextStartKey);

            if (entry == null) return null;

            int index = entry.getKey();

            context.nextStartKey = index + 1;

            context.active.remove(index);

            return entry.getValue();
        }

        return null;
    }

    private static boolean hasOnePositionEqualHashCode(
            Triangle a,
            Triangle b) {

        for (RepackStartStructure.Vertex va : a.v) {
            int hashA = new PositionKey(va).hashCode();

            for (RepackStartStructure.Vertex vb : b.v) {
                int hashB = new PositionKey(vb).hashCode();

                if (hashA == hashB) return true;
            }
        }

        return false;
    }

    private static boolean hasTwoVertexEqual(
            MaterialContext context,
            Triangle a,
            Triangle b) {

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {

                if (i == j) continue;

                if (!vertexEquals(context, a.v[i], b.v[j])) {
                    continue;
                }

                for (int k = 0; k < 3; k++) {
                    if (k == i) continue;

                    for (int l = 0; l < 3; l++) {
                        if (l == j) continue;

                        if (vertexEquals(
                                context, a.v[k], b.v[l])) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private static boolean vertexEquals(
            MaterialContext context,
            RepackStartStructure.Vertex a,
            RepackStartStructure.Vertex b) {

        return a.x == b.x
                && a.y == b.y
                && a.z == b.z
                && a.nx == b.nx
                && a.ny == b.ny
                && a.nz == b.nz
                && a.u == b.u
                && a.v == b.v
                && a.r == b.r
                && a.g == b.g
                && a.b == b.b
                && a.a == b.a
                && weightMapEquals(context, a.weightMapId,
                        b.weightMapId);
    }

    private static boolean weightMapEquals(
            MaterialContext context,
            int a,
            int b) {

        List<RepackStartStructure.WeightMap> maps =
                context.material.weightMaps;

        if (a < 0 || b < 0
                || a >= maps.size()
                || b >= maps.size()) {
            return false;
        }

        return maps.get(a).sameValue(maps.get(b));
    }

    private static MatchFirst checkOrderFirst(
            MaterialContext context,
            Triangle last,
            Triangle next,
            int[] lastOrder,
            int[][] nextOrder) {

        for (int[] oldOrder : rotations(lastOrder)) {

            for (int[] newOrder : nextOrder) {

                if (vertexEquals(
                        context,
                        last.get(oldOrder[1]),
                        next.get(newOrder[0]))
                        && vertexEquals(
                        context,
                        last.get(oldOrder[2]),
                        next.get(newOrder[1]))) {

                    return new MatchFirst(
                            oldOrder.clone(),
                            newOrder.clone()
                    );
                }
            }
        }

        return null;
    }

    private static MatchNext checkOrderNext(
            MaterialContext context,
            Triangle last,
            Triangle next,
            int[] lastOrder,
            int[][] nextOrder) {

        for (int[] order : nextOrder) {

            if (vertexEquals(
                    context,
                    last.get(lastOrder[1]),
                    next.get(order[0]))
                    && vertexEquals(
                    context,
                    last.get(lastOrder[2]),
                    next.get(order[1]))) {

                return new MatchNext(order.clone());
            }
        }

        return null;
    }

    private static int[][] rotations(int[] order) {
        return new int[][]{
                {order[0], order[1], order[2]},
                {order[1], order[2], order[0]},
                {order[2], order[0], order[1]}
        };
    }

    private static boolean canAddWeightMap(
            MaterialContext context,
            Strip strip,
            RepackStartStructure.Vertex vertex) {

        RepackStartStructure.WeightMap map =
                getSourceWeightMap(context, vertex.weightMapId);

        for (RepackStartStructure.WeightMap existing
                : strip.weightMaps) {
            if (existing.sameValue(map)) return true;
        }

        return strip.weightMaps.size() < MAX_WEIGHT_MAPS;
    }

    private static RepackStartStructure.WeightMap getSourceWeightMap(
            MaterialContext context,
            int id) {

        if (id < 0 || id >= context.material.weightMaps.size()) {
            throw new IllegalArgumentException(
                    "Weight map vertex tidak valid: " + id);
        }

        return context.material.weightMaps.get(id);
    }

    private static void addVertex(
            MaterialContext context,
            Strip strip,
            RepackStartStructure.Vertex source) {

        RepackStartStructure.WeightMap sourceMap =
                getSourceWeightMap(context, source.weightMapId);

        boolean found = false;
        for (RepackStartStructure.WeightMap existing : strip.weightMaps) {
            if (existing.sameValue(sourceMap)) {
                found = true;
                break;
            }
        }
        if (!found) {
            strip.weightMaps.add(sourceMap.copy());
        }

        RepackStartStructure.Vertex copy =
                copyVertex(source);

        strip.vertices.add(copy);
    }

    private static void finishStrip(
            Strip strip,
            MaterialStrips output,
            Result result) {

        if (strip == null || strip.vertices.isEmpty()) return;

        output.strips.add(strip);

        result.totalStrips++;
        result.totalVertices += strip.vertices.size();
    }
}
