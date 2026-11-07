package com.kenzo.re4bintools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java port of JADERLINK SMD_READER_LIB/SmdReader.cs (SMD v1). */
final class SmdParser {
    private static final Pattern NODE = Pattern.compile("^(-?\\d+)\\s+\\\"([^\\\"]*)\\\"\\s+(-?\\d+)\\s*$");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    enum Section { COMMANDS, NODES, SKELETON, TRIANGLES, VERTEX_ANIMATION }

    static final class Model {
        final List<Node> nodes = new ArrayList<>();
        final List<Time> times = new ArrayList<>();
        final List<Triangle> triangles = new ArrayList<>();
        final List<VertexAnimationTime> vertexAnimation = new ArrayList<>();
    }
    static final class Node {
        final int id, parentId; final String name;
        Node(int id, String name, int parentId) { this.id=id; this.name=name; this.parentId=parentId; }
    }
    static final class Time {
        final int id; final List<Skeleton> skeletons = new ArrayList<>();
        Time(int id) { this.id=id; }
    }
    static final class Skeleton {
        int boneId; float x,y,z,rx,ry,rz;
    }
    static final class Triangle {
        int id; String material; final List<Vertex> vertices = new ArrayList<>();
    }
    static final class Vertex {
        int id, parentBone; float x,y,z,nx,ny,nz,u,v;
        final List<Weight> links = new ArrayList<>();
    }
    static final class Weight { int boneId; float weight; }
    static final class VertexAnimationTime {
        final int id; final List<VertexAnimation> vertices = new ArrayList<>();
        VertexAnimationTime(int id) { this.id=id; }
    }
    static final class VertexAnimation {
        int vertexId; float x,y,z,nx,ny,nz;
    }

    private SmdParser() {}

    static Model parse(InputStream input) throws IOException {
        if (input == null) throw new IllegalArgumentException("File SMD kosong");
        Model model = new Model();
        Section section = null;
        boolean versionSeen = false;
        Time currentTime = null;
        VertexAnimationTime currentAnimation = null;
        Triangle currentTriangle = null;
        int triangleState = 0, vertexId = 0, triangleId = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, Charset.forName("US-ASCII")))) {
            String raw;
            int lineNo = 0;
            while ((raw = reader.readLine()) != null) {
                lineNo++;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("//")) continue;
                if (!versionSeen) {
                    if (!line.toLowerCase(Locale.ROOT).startsWith("version"))
                        throw error(lineNo, "SMD harus diawali 'version 1'");
                    String[] v = tokens(line);
                    if (v.length < 2 || !"1".equals(v[1])) throw error(lineNo, "Versi SMD tidak didukung: " + line);
                    versionSeen = true; section = Section.COMMANDS; continue;
                }
                if (section == Section.COMMANDS) {
                    String command = line.toLowerCase(Locale.ROOT);
                    if (command.equals("nodes")) section = Section.NODES;
                    else if (command.equals("skeleton")) section = Section.SKELETON;
                    else if (command.equals("triangles")) section = Section.TRIANGLES;
                    else if (command.equals("vertexanimation")) section = Section.VERTEX_ANIMATION;
                    else throw error(lineNo, "Perintah SMD tidak dikenal: " + line);
                    continue;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.equals("end")) {
                    if (section == Section.SKELETON && currentTime != null) { model.times.add(currentTime); currentTime = null; }
                    if (section == Section.TRIANGLES && currentTriangle != null) { model.triangles.add(currentTriangle); currentTriangle = null; }
                    if (section == Section.VERTEX_ANIMATION && currentAnimation != null) { model.vertexAnimation.add(currentAnimation); currentAnimation = null; }
                    section = Section.COMMANDS; continue;
                }
                switch (section) {
                    case NODES: model.nodes.add(parseNode(line, lineNo)); break;
                    case SKELETON:
                        if (lower.startsWith("time")) {
                            if (currentTime != null) model.times.add(currentTime);
                            String[] t = tokens(line); currentTime = new Time(t.length > 1 ? parseInt(t[1], lineNo) : 0);
                        } else {
                            if (currentTime == null) throw error(lineNo, "Data skeleton sebelum deklarasi time");
                            currentTime.skeletons.add(parseSkeleton(line, lineNo));
                        }
                        break;
                    case TRIANGLES:
                        if (triangleState == 0) {
                            if (currentTriangle != null) model.triangles.add(currentTriangle);
                            currentTriangle = new Triangle(); currentTriangle.id = triangleId++;
                            currentTriangle.material = line.trim(); triangleState = 1;
                        } else {
                            if (currentTriangle == null) throw error(lineNo, "Triangle tanpa material");
                            currentTriangle.vertices.add(parseVertex(line, vertexId++, lineNo));
                            triangleState++;
                            if (triangleState == 4) triangleState = 0;
                        }
                        break;
                    case VERTEX_ANIMATION:
                        if (lower.startsWith("time")) {
                            if (currentAnimation != null) model.vertexAnimation.add(currentAnimation);
                            String[] t = tokens(line); currentAnimation = new VertexAnimationTime(t.length > 1 ? parseInt(t[1], lineNo) : 0);
                        } else {
                            if (currentAnimation == null) throw error(lineNo, "Vertex animation sebelum time");
                            currentAnimation.vertices.add(parseVertexAnimation(line, lineNo));
                        }
                        break;
                    default: throw error(lineNo, "State parser tidak valid");
                }
            }
        }
        if (!versionSeen) throw new IOException("Invalid SMD: tidak ditemukan version 1");
        if (section != Section.COMMANDS) throw new IOException("Invalid SMD: section tidak diakhiri 'end'");
        if (triangleState != 0) throw new IOException("Invalid SMD: triangle terakhir tidak memiliki 3 vertex");
        if (model.nodes.isEmpty()) throw new IOException("SMD tidak memiliki nodes");
        if (model.triangles.isEmpty()) throw new IOException("SMD tidak memiliki triangles");
        return model;
    }

    private static Node parseNode(String line, int n) throws IOException {
        Matcher m = NODE.matcher(line);
        if (!m.matches()) throw error(n, "Format node tidak valid");
        return new Node(parseInt(m.group(1),n), m.group(2), parseInt(m.group(3),n));
    }
    private static Skeleton parseSkeleton(String line, int n) throws IOException {
        String[] a=tokens(line); if(a.length<7) throw error(n,"Data skeleton harus memiliki 7 angka");
        Skeleton s=new Skeleton(); s.boneId=parseInt(a[0],n); s.x=f(a[1],n); s.y=f(a[2],n); s.z=f(a[3],n); s.rx=f(a[4],n); s.ry=f(a[5],n); s.rz=f(a[6],n); return s;
    }
    private static Vertex parseVertex(String line, int id, int n) throws IOException {
        String[] a=tokens(line); if(a.length<10) throw error(n,"Vertex SMD kurang dari 10 kolom");
        Vertex v=new Vertex(); v.id=id; v.parentBone=parseInt(a[0],n);
        v.x=f(a[1],n); v.y=f(a[2],n); v.z=f(a[3],n); v.nx=f(a[4],n); v.ny=f(a[5],n); v.nz=f(a[6],n); v.u=f(a[7],n); v.v=f(a[8],n);
        int links=parseInt(a[9],n); if(links<0 || links> a.length/2) throw error(n,"Jumlah weight link tidak valid");
        if(a.length < 10 + links*2) throw error(n,"Data weight link terpotong");
        for(int i=0;i<links;i++){ Weight w=new Weight(); w.boneId=parseInt(a[10+i*2],n); w.weight=f(a[11+i*2],n); v.links.add(w); }
        return v;
    }
    private static VertexAnimation parseVertexAnimation(String line,int n)throws IOException{
        String[] a=tokens(line); if(a.length<7) throw error(n,"Vertex animation tidak lengkap");
        VertexAnimation v=new VertexAnimation(); v.vertexId=parseInt(a[0],n); v.x=f(a[1],n);v.y=f(a[2],n);v.z=f(a[3],n);v.nx=f(a[4],n);v.ny=f(a[5],n);v.nz=f(a[6],n);return v;
    }
    private static String[] tokens(String s){ return SPACE.split(s.trim()); }
    private static int parseInt(String s,int n)throws IOException{try{return Integer.parseInt(s);}catch(Exception e){throw error(n,"Integer tidak valid: "+s);}}
    private static float f(String s,int n)throws IOException{try{float v=Float.parseFloat(s);if(!Float.isFinite(v))throw new NumberFormatException();return v;}catch(Exception e){throw error(n,"Float tidak valid: "+s);}}
    private static IOException error(int n,String m){return new IOException("SMD baris "+n+": "+m);}
}
