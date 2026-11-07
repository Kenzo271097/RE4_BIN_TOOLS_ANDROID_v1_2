package com.kenzo.re4bintools;

import java.io.*;
import java.nio.*;
import java.util.*;

/** Android/Java port of the JADERLINK RE4 PS2 BIN v1.5.0 extraction path. */
final class BinExtractor {
    static final class ExtractException extends Exception { ExtractException(String m){super(m);} }
    static final class Bone { int id,parent; float x,y,z; }
    static final class WeightRow { int[] ids=new int[3]; int amount; float[] weights=new float[3]; }
    static final class Vertex { int x,y,z,u,v,nx,ny,nz,comp,mount,unknownA,unknownB; }
    static final class Segment { boolean scenario; float conversion; List<Vertex> vertices=new ArrayList<>(); List<WeightRow> rows; }
    static final class Node { int offset; int[] boneIds; List<Segment> segments=new ArrayList<>(); }
    static final class Material { int nodeOffset,flag,diffuse,bump,genericSpecular,ks1,ks2,ks3,specScale; }
    static final class Model { List<Bone>bones=new ArrayList<>(); List<Node>nodes=new ArrayList<>(); List<Material>materials=new ArrayList<>(); boolean scenario; }
    private final ByteBuffer b;
    private final int length;
    private BinExtractor(byte[] data){ b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); length=data.length; }
    private void need(long off,long size,String what)throws ExtractException { if(off<0||size<0||off+size>length) throw new ExtractException(what+" di luar batas file (offset 0x"+Long.toHexString(off)+")"); }
    private int u8(int o)throws ExtractException{need(o,1,"byte");return b.get(o)&255;}
    private int u16(int o)throws ExtractException{need(o,2,"ushort");return b.getShort(o)&65535;}
    private int i16(int o)throws ExtractException{need(o,2,"short");return b.getShort(o);}
    private long u32(int o)throws ExtractException{need(o,4,"uint");return b.getInt(o)&0xffffffffL;}
    private float f32(int o)throws ExtractException{need(o,4,"float");return b.getFloat(o);}
    static Model decode(byte[] data)throws ExtractException{return new BinExtractor(data).decode();}
    private Model decode()throws ExtractException {
        if(length<0x50)throw new ExtractException("BIN lebih kecil dari header 0x50.");
        if(u16(0)!=0x30||u32(16)!=0xCDCDCDCDL)throw new ExtractException("Header BIN tidak cocok dengan format JADERLINK.");
        int bonesPoint=(int)u32(4), bonesCount=u8(9), matCount=u16(10), matOff=(int)u32(12);
        if(bonesCount==0||matCount==0)throw new ExtractException("BIN tidak memiliki bone atau material/node.");
        need(bonesPoint,(long)bonesCount*16,"bone table"); need(matOff,(long)matCount*16,"material table");
        Model out=new Model();
        for(int i=0;i<bonesCount;i++){int p=bonesPoint+i*16;Bone x=new Bone();x.id=u8(p);x.parent=u8(p+1);x.x=f32(p+4);x.y=f32(p+8);x.z=f32(p+12);out.bones.add(x);}
        for(int mi=0;mi<matCount;mi++){
            int mp=matOff+mi*16; Material m=new Material();m.flag=u8(mp);m.diffuse=u8(mp+1);m.bump=u8(mp+2);m.genericSpecular=u8(mp+4);m.ks1=u8(mp+5);m.ks2=u8(mp+6);m.ks3=u8(mp+7);m.specScale=u8(mp+10);m.nodeOffset=(int)u32(mp+12);out.materials.add(m);
            int no=m.nodeOffset;need(no,4,"node header");int segCount=u8(no+2)+1,boneAmount=u8(no+3);int boneBytes=(((4+boneAmount+15)/16)*16)-4;need(no+4,boneBytes,"node bone list");
            Node node=new Node();node.offset=no;node.boneIds=new int[boneAmount];for(int j=0;j<boneAmount;j++)node.boneIds[j]=u8(no+4+j);int cur=no+4+boneBytes;
            for(int si=0;si<segCount;si++){
                need(cur,16,"segment header");int h1=cur;cur+=16;boolean weighted=u8(h1+12)==0&&u8(h1+14)>1;Segment s=new Segment();
                if(weighted){int weightBytes=u8(h1)*16;need(cur,weightBytes,"weight map table");s.rows=new ArrayList<>();for(int q=0;q<weightBytes/32;q++){int rp=cur+q*32;WeightRow wr=new WeightRow();for(int k=0;k<3;k++)wr.ids[k]=(int)u32(rp+k*4);wr.amount=b.getInt(rp+12);for(int k=0;k<3;k++)wr.weights[k]=f32(rp+16+k*4);s.rows.add(wr);}cur+=weightBytes;need(cur,16,"second segment header");cur+=16;s.scenario=false;
                }else{s.scenario=true;out.scenario=true;}
                need(cur,32,"VIF headers");int h2=cur,h3=cur+16;int lineCount=u8(h2),chunkBytes=u8(h3)*16;s.conversion=f32(h2+12);cur+=32;need(cur,chunkBytes,"vertex chunk");if(lineCount*24>chunkBytes)throw new ExtractException("Vertex count melebihi ukuran chunk pada node "+mi+" segment "+si);
                for(int vi=0;vi<lineCount;vi++){int p=cur+vi*24;Vertex v=new Vertex();v.x=i16(p);v.y=i16(p+2);v.z=i16(p+4);
                    if(s.scenario){v.mount=u16(p+6);v.u=i16(p+8);v.v=i16(p+10);v.unknownA=u16(p+12);v.comp=u16(p+14);v.nx=i16(p+16);v.ny=i16(p+18);v.nz=i16(p+20);v.unknownB=u16(p+22);}
                    else{v.unknownB=u16(p+6);v.nx=i16(p+8);v.ny=i16(p+10);v.nz=i16(p+12);v.comp=u16(p+14);v.u=i16(p+16);v.v=i16(p+18);v.unknownA=u16(p+20);v.mount=u16(p+22);}s.vertices.add(v);}
                cur+=chunkBytes;if(si>0){need(cur,16,"segment end tag");cur+=16;}node.segments.add(s);
            }out.nodes.add(node);
        }return out;
    }
    private static float f(float x){return Float.isFinite(x)?x:0f;}
    private static String n(float x){
        x=f(x);
        if(x==0f)return Float.floatToRawIntBits(x)<0?"-0.0":"0.0";
        String s=null;
        for(int precision=1;precision<=9;precision++){
            String candidate=String.format(Locale.US,"%."+precision+"g",x);
            try{if(Float.parseFloat(candidate)==x){s=candidate;break;}}catch(NumberFormatException ignored){}
        }
        if(s==null)s=String.format(Locale.US,"%.9g",x);
        if(s.indexOf('e')>=0||s.indexOf('E')>=0){
            s=String.format(Locale.US,"%.9f",(double)x).replaceAll("0+$","").replaceAll("\\.$","");
            if(s.indexOf('.')<0)s+=".0";
        }else if(s.indexOf('.')<0)s+=".0";
        return s;
    }
    private static float pos(int c,float conv){return ((float)c*conv)/100f;}
    private static float negpos(int c,float conv){return ((float)c*-1f*conv)/100f;}
    private static float[] norm(Vertex v){float x=v.nx,y=v.ny,z=v.nz;float len=(float)Math.sqrt(x*x+y*y+z*z);if(len==0)len=1;return new float[]{x/len,y/len,z/len};}

    /** Diagnostics ported from v0.4.55 direct BIN weight binding. */
    static final class DirectWeightReport {
        int vertices, weightedVertices, usedVertices, missingUsedVertices, segments;
    }
    static DirectWeightReport inspectDirectWeights(Model model) {
        DirectWeightReport report=new DirectWeightReport();
        Map<Integer,Node> nodesByOffset=new HashMap<>();
        for(Node node:model.nodes)if(!nodesByOffset.containsKey(node.offset))nodesByOffset.put(node.offset,node);
        Set<Integer> validBoneIds=new HashSet<>();
        for(Bone bone:model.bones){
            validBoneIds.add(bone.id);
        }
        List<Map<Integer,Float>> weightsByVertex=new ArrayList<>();
        Set<Integer> seenOffsets=new HashSet<>();Set<Integer> usedIndices=new HashSet<>();
        for(Material material:model.materials){
            if(!seenOffsets.add(material.nodeOffset))continue;
            Node node=nodesByOffset.get(material.nodeOffset);if(node==null)continue;
            for(Segment segment:node.segments){
                report.segments++;
                for(int vi=0;vi<segment.vertices.size();vi++){
                    Vertex vertex=segment.vertices.get(vi);Map<Integer,Float> normalized=null;
                    if(segment.rows!=null){
                        int rowIndex=vertex.unknownB/2;
                        if(rowIndex>=0&&rowIndex<segment.rows.size()){
                            WeightRow row=segment.rows.get(rowIndex);int amount=row.amount;
                            if(amount>=1&&amount<=3){
                                Map<Integer,Double> combined=new TreeMap<>();boolean valid=true;
                                for(int j=0;j<amount;j++){
                                    int local=row.ids[j]/4;
                                    if(local<0||local>=node.boneIds.length){valid=false;break;}
                                    int boneId=node.boneIds[local];float weight=row.weights[j];
                                    if(!validBoneIds.contains(boneId)||!Float.isFinite(weight)||weight<=0f){valid=false;break;}
                                    combined.put(boneId,combined.containsKey(boneId)?combined.get(boneId)+(double)weight:(double)weight);
                                }
                                double total=0.0;for(double value:combined.values())total+=value;
                                if(valid&&total>1.0e-8){normalized=new LinkedHashMap<>();for(Map.Entry<Integer,Double> e:combined.entrySet())normalized.put(e.getKey(),(float)(e.getValue()/total));}
                            }
                        }
                    }
                    weightsByVertex.add(normalized);
                    if(vi>=2&&vertex.comp==0){int base=weightsByVertex.size()-1;usedIndices.add(base-2);usedIndices.add(base-1);usedIndices.add(base);}
                }
            }
        }
        report.vertices=weightsByVertex.size();report.usedVertices=usedIndices.size();
        for(Map<Integer,Float> weight:weightsByVertex)if(weight!=null&&!weight.isEmpty())report.weightedVertices++;
        for(Integer index:usedIndices)if(index<0||index>=weightsByVertex.size()||weightsByVertex.get(index)==null||weightsByVertex.get(index).isEmpty())report.missingUsedVertices++;
        return report;
    }

    static Map<String,String> write(Model m,String base,OutputStream objOut,OutputStream mtlOut,OutputStream smdOut)throws IOException {
        String folder=new File(base).getName().replace(' ','_');String baseName=new File(base).getName();
        BufferedWriter obj=new BufferedWriter(new OutputStreamWriter(objOut,"UTF-8"));obj.write("# Extracted with KENZO RE4 BIN TOOLS; JADERLINK v1.5.0-compatible output\nmtllib "+baseName+".mtl\n");int gi=1;
        for(int ni=0;ni<m.nodes.size();ni++){Node node=m.nodes.get(ni);String mat=String.format(Locale.US,"MATERIAL_%03d",ni);obj.write("g "+mat+"\nusemtl "+mat+"\n");for(Segment s:node.segments){for(Vertex v:s.vertices){obj.write("v "+n(pos(v.x,s.conversion))+" "+n(pos(v.y,s.conversion))+" "+n(pos(v.z,s.conversion)));if(m.scenario)obj.write(" "+n(v.nx/128f)+" "+n(v.ny/128f)+" "+n(v.nz/128f)+" "+n(v.unknownB/128f));obj.write("\nvt "+n(v.u/255f)+" "+n(v.v/255f)+"\n");if(!m.scenario){float[] q=norm(v);obj.write("vn "+n(q[0])+" "+n(q[1])+" "+n(q[2])+"\n");}}
                boolean inv=false;for(int i=0;i<s.vertices.size();i++){Vertex v=s.vertices.get(i);if(i>=2&&v.comp==0){int a=gi-2,bb=gi-1,c=gi;int[] t=inv?new int[]{c,bb,a}:new int[]{a,bb,c};obj.write("f");for(int x:t)obj.write(m.scenario?" "+x+"/"+x:" "+x+"/"+x+"/"+x);obj.write("\n");inv=!inv;}else inv=false;gi++;}}
        }obj.flush();
        BufferedWriter mtl=new BufferedWriter(new OutputStreamWriter(mtlOut,"UTF-8"));mtl.write("# Material properties extracted from RE4 PS2 BIN\n\n");for(int i=0;i<m.materials.size();i++){Material x=m.materials.get(i);String name=String.format(Locale.US,"MATERIAL_%03d",i),tex=folder+"/"+String.format(Locale.US,"%04d.tga",x.diffuse);mtl.write("newmtl "+name+"\nKa 1.0 1.0 1.0\nKd 1.0 1.0 1.0\nKs "+n(x.ks1/255f)+" "+n(x.ks2/255f)+" "+n(x.ks3/255f)+"\nNs 0\nd 1\nmap_Kd "+tex+"\nmap_d "+tex+"\n");if((x.flag&1)!=0){String bump=folder+"/"+String.format(Locale.US,"%04d.tga",x.bump);mtl.write("map_Bump "+bump+"\nBump "+bump+"\n");}if((x.flag&2)!=0){int sx=((x.specScale&240)>>4)+1,sy=(x.specScale&15)+1;mtl.write("map_Ns -s "+sx+" "+sy+" 1 generic_specular/"+String.format(Locale.US,"%04d.tga",x.genericSpecular)+"\n");}mtl.write("\n");}mtl.flush();
        List<Bone> fixed=new ArrayList<>(m.bones);Map<Integer,Integer> seen=new HashMap<>();for(int i=fixed.size()-1;i>=0;i--){Bone old=fixed.get(i);int count=seen.containsKey(old.id)?seen.get(old.id):0;seen.put(old.id,count+1);Bone b=new Bone();b.id=old.id+0x100*count;b.parent=old.parent==255?-1:old.parent;b.x=old.x/100f;b.y=-old.z/100f;b.z=old.y/100f;fixed.set(i,b);}
        BufferedWriter smd=new BufferedWriter(new OutputStreamWriter(smdOut,"UTF-8"));smd.write("version 1\nnodes\n");for(Bone b:fixed)smd.write(b.id+" \"BONE_"+String.format(Locale.US,"%03d",b.id)+"\" "+b.parent+"\n");smd.write("end\nskeleton\ntime 0\n");for(Bone b:fixed)smd.write(b.id+" "+n(b.x)+" "+n(b.y)+" "+n(b.z)+" 0.0 0.0 0.0\n");smd.write("end\ntriangles\n");
        for(int ni=0;ni<m.nodes.size();ni++){Node node=m.nodes.get(ni);for(Segment s:node.segments){String[] weights=s.rows==null?new String[0]:new String[s.rows.size()];for(int i=0;i<weights.length;i++){WeightRow r=s.rows.get(i);StringBuilder z=new StringBuilder().append(r.amount);for(int j=0;j<Math.max(0,Math.min(3,r.amount));j++){int li=r.ids[j]/4;int bone=li<node.boneIds.length?node.boneIds[li]:0;z.append(' ').append(bone).append(' ').append(n(r.weights[j]));}weights[i]=z.toString();}
                boolean inv=false;for(int i=0;i<s.vertices.size();i++){Vertex v=s.vertices.get(i);if(i<2||v.comp!=0){inv=false;continue;}Vertex[] c={s.vertices.get(i-2),s.vertices.get(i-1),v};String[][] lines=new String[3][4];for(int k=0;k<3;k++){Vertex q=c[k];float[] no=m.scenario?new float[]{0,0,0}:norm(q);lines[k][0]=n(pos(q.x,s.conversion))+" "+n(negpos(q.z,s.conversion))+" "+n(pos(q.y,s.conversion));lines[k][1]=n(no[0])+" "+n(m.scenario?0:-no[2])+" "+n(m.scenario?0:no[1]);lines[k][2]=n(q.u/255f)+" "+n(q.v/255f);int wi=q.unknownB/2;lines[k][3]=wi<weights.length?weights[wi]:"0";}smd.write(String.format(Locale.US,"MATERIAL_%03d\n",ni));int[] order=inv?new int[]{0,1,2}:new int[]{2,1,0};for(int k:order)smd.write("0 "+lines[k][0]+" "+lines[k][1]+" "+lines[k][2]+" "+lines[k][3]+"\n");inv=!inv;}}
        }smd.write("end\n");smd.flush();Map<String,String> stats=new LinkedHashMap<>();int verts=0,segs=0;for(Node node:m.nodes)for(Segment s:node.segments){verts+=s.vertices.size();segs++;}stats.put("vertices",String.valueOf(verts));stats.put("bones",String.valueOf(m.bones.size()));stats.put("materials",String.valueOf(m.materials.size()));stats.put("segments",String.valueOf(segs));return stats;
    }
}
