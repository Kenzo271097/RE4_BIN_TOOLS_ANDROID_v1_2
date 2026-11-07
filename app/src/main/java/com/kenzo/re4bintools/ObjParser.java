package com.kenzo.re4bintools;

import java.io.*;
import java.util.*;

final class ObjParser {

    static final class Vec2 {
        float u, v;
        Vec2(float u, float v) {
            this.u = u;
            this.v = v;
        }
    }

    static final class Vec3 {
        float x, y, z;
        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    static final class Corner {
        int position;
        int uv;
        int normal;
    }

    static final class Triangle {
        Corner a, b, c;
    }

    static final class MaterialGroup {
        String name;
        List<Triangle> triangles = new ArrayList<>();

        MaterialGroup(String name) {
            this.name = name;
        }
    }

    static final class Model {
        List<Vec3> positions = new ArrayList<>();
        List<Vec2> uvs = new ArrayList<>();
        List<Vec3> normals = new ArrayList<>();
        List<MaterialGroup> groups = new ArrayList<>();
    }

    static Model parse(InputStream input) throws IOException {
        Model model = new Model();
        Map<String, MaterialGroup> groups = new LinkedHashMap<>();

        MaterialGroup current = getGroup(groups, "DEFAULT");

        BufferedReader reader = new BufferedReader(
            new InputStreamReader(input, "UTF-8")
        );

        String line;
        int lineNumber = 0;

        while ((line = reader.readLine()) != null) {
            lineNumber++;

            line = line.trim();

            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            String[] p = line.split("\\s+");
            String type = p[0];

            try {
                switch (type) {
                    case "v":
                        if (p.length < 4) {
                            throw new IOException("Vertex tidak lengkap");
                        }
                        model.positions.add(new Vec3(
                            Float.parseFloat(p[1]),
                            Float.parseFloat(p[2]),
                            Float.parseFloat(p[3])
                        ));
                        break;

                    case "vt":
                        if (p.length < 3) {
                            throw new IOException("UV tidak lengkap");
                        }
                        model.uvs.add(new Vec2(
                            Float.parseFloat(p[1]),
                            Float.parseFloat(p[2])
                        ));
                        break;

                    case "vn":
                        if (p.length < 4) {
                            throw new IOException("Normal tidak lengkap");
                        }
                        model.normals.add(new Vec3(
                            Float.parseFloat(p[1]),
                            Float.parseFloat(p[2]),
                            Float.parseFloat(p[3])
                        ));
                        break;

                    case "usemtl":
                        String material = line.substring(6).trim();
                        if (material.isEmpty()) material = "DEFAULT";
                        current = getGroup(groups, material.toUpperCase(Locale.ROOT));
                        break;

                    case "f":
                        if (p.length < 4) {
                            throw new IOException("Face kurang dari 3 titik");
                        }

                        List<Corner> corners = new ArrayList<>();

                        for (int i = 1; i < p.length; i++) {
                            corners.add(parseCorner(
                                p[i],
                                model.positions.size(),
                                model.uvs.size(),
                                model.normals.size()
                            ));
                        }

                        // Triangulasi fan: (0, i-1, i)
                        for (int i = 2; i < corners.size(); i++) {
                            Triangle t = new Triangle();
                            t.a = corners.get(0);
                            t.b = corners.get(i - 1);
                            t.c = corners.get(i);
                            current.triangles.add(t);
                        }
                        break;

                    default:
                        // Abaikan o, g, s, mtllib dan directive lain.
                        break;
                }
            } catch (Exception e) {
                throw new IOException(
                    "OBJ error pada baris " + lineNumber +
                    ": " + e.getMessage(), e
                );
            }
        }

        model.groups.addAll(groups.values());

        if (model.positions.isEmpty()) {
            throw new IOException("OBJ tidak memiliki vertex.");
        }

        boolean hasFaces = false;
        for (MaterialGroup g : model.groups) {
            if (!g.triangles.isEmpty()) {
                hasFaces = true;
                break;
            }
        }

        if (!hasFaces) {
            throw new IOException("OBJ tidak memiliki face.");
        }

        return model;
    }

    private static MaterialGroup getGroup(
        Map<String, MaterialGroup> groups,
        String name
    ) {
        MaterialGroup g = groups.get(name);
        if (g == null) {
            g = new MaterialGroup(name);
            groups.put(name, g);
        }
        return g;
    }

    private static Corner parseCorner(
        String token,
        int positionCount,
        int uvCount,
        int normalCount
    ) throws IOException {

        String[] parts = token.split("/", -1);

        Corner c = new Corner();

        c.position = resolveIndex(parts[0], positionCount);

        c.uv = -1;
        c.normal = -1;

        if (parts.length > 1 && !parts[1].isEmpty()) {
            c.uv = resolveIndex(parts[1], uvCount);
        }

        if (parts.length > 2 && !parts[2].isEmpty()) {
            c.normal = resolveIndex(parts[2], normalCount);
        }

        return c;
    }

    private static int resolveIndex(
        String value,
        int count
    ) throws IOException {

        int index = Integer.parseInt(value);

        if (index == 0) {
            throw new IOException("Indeks OBJ tidak boleh nol");
        }

        int resolved = index > 0 ? index - 1 : count + index;

        if (resolved < 0 || resolved >= count) {
            throw new IOException("Indeks OBJ di luar batas: " + value);
        }

        return resolved;
    }
}
