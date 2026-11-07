package com.kenzo.re4bintools;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** SMD-to-existing-repack-structures adapter, following JADERLINK BINrepackSMD.cs. */
final class SmdRepackPipeline {
    static final float GLOBAL_POSITION_SCALE = 100.0f;

    static final class BoneLine {
        final int id, parentId;
        final float x,y,z;
        BoneLine(int id,int parentId,float x,float y,float z){this.id=id;this.parentId=parentId;this.x=x;this.y=y;this.z=z;}
    }
    static final class Bounds {
        float x,y,z,w=1f,width,height,depth;
    }
    static final class Output {
        SmdParser.Model parsed;
        RepackStartStructure.Model start;
        RepackStripCompressor.Result compressed;
        RepackSegmentBuilder.Result segments;
        List<BoneLine> bones;
        Bounds bounds;
        float farthestVertex, conversionFactor;
    }

    private SmdRepackPipeline() {}

    static Output process(InputStream input) throws IOException {
        SmdParser.Model smd=SmdParser.parse(input);
        RepackStartStructure.Model start=buildStart(smd);
        float farthest=0f;
        for(RepackStartStructure.MaterialGroup group:start.materials){
            for(RepackStartStructure.Vertex v:group.vertices){
                farthest=Math.max(farthest,Math.abs(v.x));
                farthest=Math.max(farthest,Math.abs(v.y));
                farthest=Math.max(farthest,Math.abs(v.z));
            }
        }
        float factor=farthest / 32767.0f;
        if(!(factor>0f) || !Float.isFinite(factor)) throw new IOException("ConversionFactorValue SMD tidak valid");
        // Existing compressor performs JADERLINK triangle-strip construction.
        RepackStripCompressor.Result compressed=RepackStripCompressor.compress(start);
        RepackSegmentBuilder.Result segments=RepackSegmentBuilder.build(start,compressed);
        Output out=new Output(); out.parsed=smd; out.start=start; out.compressed=compressed; out.segments=segments;
        out.farthestVertex=farthest; out.conversionFactor=factor; out.bones=buildBones(smd); out.bounds=buildBounds(start);
        return out;
    }

    private static RepackStartStructure.Model buildStart(SmdParser.Model smd) throws IOException {
        RepackStartStructure.Model result=new RepackStartStructure.Model();
        Map<String,RepackStartStructure.MaterialGroup> groups=new LinkedHashMap<>();
        for(SmdParser.Triangle triangle:smd.triangles){
            if(triangle.vertices.size()!=3) throw new IOException("Triangle SMD tidak memiliki tepat 3 vertex: "+triangle.id);
            String name=triangle.material.toUpperCase(Locale.ROOT).trim();
            RepackStartStructure.MaterialGroup group=groups.get(name);
            if(group==null){group=new RepackStartStructure.MaterialGroup(name);groups.put(name,group);}
            RepackStartStructure.Face face=new RepackStartStructure.Face();
            for(int i=0;i<3;i++){
                SmdParser.Vertex src=triangle.vertices.get(i);
                RepackStartStructure.Vertex v=new RepackStartStructure.Vertex();
                v.x=src.x*GLOBAL_POSITION_SCALE; v.y=src.z*GLOBAL_POSITION_SCALE; v.z=-src.y*GLOBAL_POSITION_SCALE;
                float nx=src.nx, ny=src.nz, nz=-src.ny;
                float length=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(length==0f)length=1f;
                v.nx=nx/length;v.ny=ny/length;v.nz=nz/length;
                v.u=src.u;v.v=src.v;v.r=255;v.g=255;v.b=255;v.a=255;
                RepackStartStructure.WeightMap map=makeWeightMap(src);
                int mapIndex=findMap(group.weightMaps,map);
                if(mapIndex<0){group.weightMaps.add(map);mapIndex=group.weightMaps.size()-1;}
                v.weightMapId=mapIndex;
                face.vertices[i]=group.vertices.size(); group.vertices.add(v);
            }
            face.weightMapId=group.vertices.get(face.vertices[0]).weightMapId;
            // Face.weightMapId is retained for legacy OBJ validation; the SMD
            // compressor now reads the weightMapId from each individual vertex.
            group.faces.add(face);
        }
        for(RepackStartStructure.MaterialGroup group:groups.values()){
            if(!group.faces.isEmpty()){
                result.totalVertices+=group.vertices.size();result.totalFaces+=group.faces.size();result.materials.add(group);
            }
        }
        if(result.materials.isEmpty())throw new IOException("SMD tidak memiliki triangle yang dapat direpack");
        return result;
    }

    private static RepackStartStructure.WeightMap makeWeightMap(SmdParser.Vertex vertex) throws IOException {
        List<SmdParser.Weight> links=new ArrayList<>(vertex.links);
        if(links.isEmpty()) return new RepackStartStructure.WeightMap(1,vertex.parentBone,1f,0,0f,0,0f);
        links.sort((a,b)->Float.compare(a.weight,b.weight)); // JADERLINK OrderBy(weight), ascending.
        int count=Math.min(3,links.size()); int[] bone={0,0,0};float[] weight={0f,0f,0f};
        for(int i=0;i<count;i++){bone[i]=links.get(i).boneId;weight[i]=links.get(i).weight;}
        float sum=weight[0]+weight[1]+weight[2];
        if(sum!=1f){float average=(sum-1f)/count;for(int i=0;i<count;i++)weight[i]-=average;float newDifference=(weight[0]+weight[1]+weight[2])-1f;if(newDifference!=1f)weight[0]-=newDifference;}
        for(int i=0;i<count;i++)if(!Float.isFinite(weight[i])||weight[i]<-0.0001f)throw new IOException("Weight map menghasilkan nilai tidak valid pada vertex "+vertex.id);
        return new RepackStartStructure.WeightMap(count,bone[0],weight[0],bone[1],weight[1],bone[2],weight[2]);
    }
    private static int findMap(List<RepackStartStructure.WeightMap> maps,RepackStartStructure.WeightMap value){for(int i=0;i<maps.size();i++)if(maps.get(i).sameValue(value))return i;return -1;}

    private static List<BoneLine> buildBones(SmdParser.Model smd)throws IOException{
        List<BoneLine> result=new ArrayList<>(); SmdParser.Time time0=null;
        for(SmdParser.Time t:smd.times)if(t.id==0){time0=t;break;}
        if(smd.nodes.size()>255)throw new IOException("Jumlah bone SMD melebihi batas 255");
        for(SmdParser.Node node:smd.nodes){
            float x=0f,y=0f,z=0f;
            if(time0!=null)for(SmdParser.Skeleton s:time0.skeletons)if(s.boneId==node.id){x=s.x*GLOBAL_POSITION_SCALE;y=s.z*GLOBAL_POSITION_SCALE;z=-s.y*GLOBAL_POSITION_SCALE;if(z==0f)z=0f;break;}
            if(node.id<0||node.id>255)throw new IOException("Bone ID di luar rentang byte: "+node.id);
            if(node.parentId>=0&&node.parentId>255)throw new IOException("Parent bone di luar rentang byte: "+node.parentId);
            result.add(new BoneLine(node.id,node.parentId<0?255:node.parentId,x,y,z));
        }
        return result;
    }
    private static Bounds buildBounds(RepackStartStructure.Model model){
        float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(RepackStartStructure.MaterialGroup g:model.materials)for(RepackStartStructure.Vertex v:g.vertices){float x=v.x,y=v.y,z=v.z;minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);}
        Bounds b=new Bounds();if(!Float.isFinite(minX))return b;b.x=(minX+maxX)/2f;b.y=(minY+maxY)/2f;b.z=(minZ+maxZ)/2f;b.width=Math.abs(maxX-minX)/2f;b.height=Math.abs(maxY-minY)/2f;b.depth=Math.abs(maxZ-minZ)/2f;return b;
    }
}
