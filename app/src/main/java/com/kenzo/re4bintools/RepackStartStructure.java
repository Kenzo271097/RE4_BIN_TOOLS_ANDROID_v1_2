package com.kenzo.re4bintools;

import java.util.ArrayList;
import java.util.List;

final class RepackStartStructure {

    static final class WeightMap {
        int links;
        int boneId1;
        float weight1;
        int boneId2;
        float weight2;
        int boneId3;
        float weight3;

        WeightMap(
                int links,
                int boneId1, float weight1,
                int boneId2, float weight2,
                int boneId3, float weight3) {
            this.links = links;
            this.boneId1 = boneId1;
            this.weight1 = weight1;
            this.boneId2 = boneId2;
            this.weight2 = weight2;
            this.boneId3 = boneId3;
            this.weight3 = weight3;
        }

        WeightMap copy() {
            return new WeightMap(
                    links,
                    boneId1, weight1,
                    boneId2, weight2,
                    boneId3, weight3
            );
        }

        boolean sameValue(WeightMap other) {
            return other != null
                    && links == other.links
                    && boneId1 == other.boneId1
                    && boneId2 == other.boneId2
                    && boneId3 == other.boneId3
                    && weight1 == other.weight1
                    && weight2 == other.weight2
                    && weight3 == other.weight3;
        }
    }

    static final class Vertex {
        float x, y, z;
        float u, v;
        float nx, ny, nz;

        int r = 255;
        int g = 255;
        int b = 255;
        int a = 255;

        int weightMapId;
        int indexMount;
        int indexComplement;
        int weightMapReference;
    }

    static final class Face {
        int[] vertices = new int[3];
        int weightMapId;
        int indexMount;
        int indexComplement;
        int weightMapReference;
    }

    static final class MaterialGroup {
        String name;
        List<Vertex> vertices = new ArrayList<>();
        List<Face> faces = new ArrayList<>();
        List<WeightMap> weightMaps = new ArrayList<>();

        MaterialGroup(String name) {
            this.name = name;
        }
    }

    static final class Model {
        List<MaterialGroup> materials = new ArrayList<>();
        int selectedBone;
        int totalVertices;
        int totalFaces;
    }

    static Model build(ObjParser.Model source, int selectedBone) {
        if (source == null) {
            throw new IllegalArgumentException("OBJ model kosong");
        }

        if (source.groups.isEmpty()) {
            throw new IllegalArgumentException(
                    "OBJ tidak memiliki material group");
        }

        if (selectedBone < 0 || selectedBone > 255) {
            throw new IllegalArgumentException(
                    "ID bone harus berada pada rentang 0-255");
        }

        Model result = new Model();
        result.selectedBone = selectedBone;

        for (ObjParser.MaterialGroup sourceGroup : source.groups) {
            MaterialGroup target =
                    new MaterialGroup(sourceGroup.name);

            target.weightMaps.add(new WeightMap(
                    1,
                    selectedBone, 1.0f,
                    0, 0.0f,
                    0, 0.0f
            ));

            for (ObjParser.Triangle triangle : sourceGroup.triangles) {
                Face face = new Face();
                face.weightMapId = 0;

                face.vertices[0] =
                        addVertex(source, target, triangle.a);
                face.vertices[1] =
                        addVertex(source, target, triangle.b);
                face.vertices[2] =
                        addVertex(source, target, triangle.c);

                target.faces.add(face);
            }

            if (!target.faces.isEmpty()) {
                result.totalVertices += target.vertices.size();
                result.totalFaces += target.faces.size();
                result.materials.add(target);
            }
        }

        if (result.materials.isEmpty()) {
            throw new IllegalArgumentException(
                    "Tidak ada face yang bisa diproses");
        }

        return result;
    }

    private static int addVertex(
            ObjParser.Model source,
            MaterialGroup target,
            ObjParser.Corner corner) {

        if (corner == null) {
            throw new IllegalArgumentException("Corner OBJ kosong");
        }

        if (corner.position < 0
                || corner.position >= source.positions.size()) {
            throw new IllegalArgumentException(
                    "Index posisi OBJ tidak valid");
        }

        ObjParser.Vec3 position =
                source.positions.get(corner.position);

        Vertex vertex = new Vertex();
        vertex.x = position.x * 100.0f;
        vertex.y = position.y * 100.0f;
        vertex.z = position.z * 100.0f;

        if (corner.uv >= 0 && corner.uv < source.uvs.size()) {
            ObjParser.Vec2 uv = source.uvs.get(corner.uv);
            vertex.u = uv.u;
            vertex.v = uv.v;
        }

        if (corner.normal >= 0
                && corner.normal < source.normals.size()) {
            ObjParser.Vec3 normal =
                    source.normals.get(corner.normal);

            vertex.nx = normal.x;
            vertex.ny = normal.y;
            vertex.nz = normal.z;
        }

        vertex.weightMapId = 0;

        int index = target.vertices.size();
        target.vertices.add(vertex);
        return index;
    }
}
