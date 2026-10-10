import dev.openrune.filesystem.Cache;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Offline exact-geometry comparison; never imports or changes cache models. */
public final class NativeModelAudit {
  static String signature(ModelDefinition m) throws Exception {
    int x0=Arrays.stream(m.vertexX).min().orElse(0);
    int y0=Arrays.stream(m.vertexY).min().orElse(0);
    int z0=Arrays.stream(m.vertexZ).min().orElse(0);
    String[] vertices=new String[m.vertexCount];
    for(int i=0;i<vertices.length;i++) vertices[i]=(m.vertexX[i]-x0)+","+(m.vertexY[i]-y0)+","+(m.vertexZ[i]-z0);
    List<String> faces=new ArrayList<>();
    for(int i=0;i<m.faceCount;i++) {
      String[] face={vertices[m.faceIndices1[i]],vertices[m.faceIndices2[i]],vertices[m.faceIndices3[i]]};
      Arrays.sort(face); faces.add(String.join("/",face));
    }
    Collections.sort(faces);
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join(";",faces).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }
  public static void main(String[] args) throws Exception {
    var input=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
    Set<Integer> ids=new TreeSet<>();
    for(String section:List.of("weapons","npcs")) for(var row:input.getAsJsonArray(section)) {
      var models=row.getAsJsonObject().get("models");
      if(models!=null&&!models.isJsonNull()) for(var id:models.getAsJsonArray()) ids.add(id.getAsInt());
    }
    var rows=new TreeMap<Integer,Object>(); var failures=new TreeMap<Integer,String>();
    Cache cache=Cache.Companion.load(Path.of(".data/cache/LIVE"));
    var loader=new ModelLoader();
    for(int id:ids) try {
      byte[] data=cache.data(7,id,0,null);
      if(data==null) continue;
      var m=loader.load(id,data);
      var row=new LinkedHashMap<String,Object>();
      row.put("signature",signature(m)); row.put("vertices",m.vertexCount); row.put("faces",m.faceCount);
      if(m.packedVertexGroups!=null) row.put("groups",Arrays.stream(m.packedVertexGroups).distinct().sorted().toArray());
      else if(m.getVertexGroups()!=null) {
        var groups=new ArrayList<Integer>();
        for(int g=0;g<m.getVertexGroups().length;g++) if(m.getVertexGroups()[g].length>0) groups.add(g);
        row.put("groups",groups);
      }
      rows.put(id,row);
    } catch(Exception ex) {failures.put(id,ex.toString());}
    cache.close();
    Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("models",rows,"failures",failures)));
    System.out.println("MODELS="+rows.size()+" FAILURES="+failures.size());
  }
}
