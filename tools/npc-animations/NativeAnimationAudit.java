import dev.openrune.ServerCacheManager;
import dev.openrune.gamevals.GameValProvider;
import dev.openrune.rscm.RSCM;
import dev.openrune.OsrsCacheProvider;
import dev.openrune.filesystem.Cache;
import dev.openrune.definition.type.SequenceType;
import dev.openrune.definition.type.ItemType;
import dev.openrune.definition.type.NpcType;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;

public final class NativeAnimationAudit {
  public static void main(String[] args) throws Exception {
    ServerCacheManager.INSTANCE.init(240).close();
    var provider = GameValProvider.Companion.loadIsolated("");
    var root = new LinkedHashMap<String,Object>();
    root.put("revision",240);
    root.put("sequences",provider.getMappings().get("seq"));
    root.put("npcSymbols",provider.getMappings().get("npc"));
    Cache client = Cache.Companion.load(Path.of(".data/cache/LIVE"));
    var nativeSequences = new HashMap<Integer,SequenceType>();
    new OsrsCacheProvider.SequenceDecoder(240).load(client,nativeSequences);
    var frames = new TreeMap<Integer,Map<String,Object>>();
    for (var seq : nativeSequences.values()) {
      var data = new LinkedHashMap<String,Object>();
      data.put("skeleton",seq.getSkeletalId());
      data.put("frames",seq.getFrameIDs());
      data.put("delay",seq.getFrameDelays());
      data.put("priority",seq.getPriority());
      var bases = new TreeSet<Integer>();
      if (seq.getSkeletalId() >= 0) {
        int frame = seq.getSkeletalId();
        byte[] bytes = client.data(dev.openrune.cache.ArchiveIndexKt.ANIMAYAS,frame >>> 16,frame & 65535,null);
        if (bytes != null && bytes.length >= 3) bases.add((bytes[1]&255)<<8 | bytes[2]&255);
      } else if (seq.getFrameIDs() != null) {
        for (int frame : seq.getFrameIDs()) {
          byte[] bytes = client.data(0,frame >>> 16,frame & 65535,null);
          if (bytes != null && bytes.length >= 2) bases.add((bytes[0]&255)<<8 | bytes[1]&255);
        }
      }
      data.put("framebases",bases);
      frames.put(seq.getId(),data);
    }
    root.put("sequenceDefinitions",frames);
    var serverSequences = new TreeMap<Integer,Map<String,Object>>();
    for (var seq : ServerCacheManager.INSTANCE.getAnims().values()) {
      serverSequences.put(seq.getId(),Map.of("duration",seq.getTickDuration(),"priority",seq.getPriority(),"maxLoops",seq.getMaxLoops(),"totalDelay",seq.getTotalDelay()));
    }
    root.put("serverSequences",serverSequences);
    var nativeNpcs = new HashMap<Integer,NpcType>();
    new OsrsCacheProvider.NPCDecoder(240).load(client,nativeNpcs);
    var nativeItems = new HashMap<Integer,ItemType>();
    new OsrsCacheProvider.ItemDecoder(240).load(client,nativeItems);
    var weapons = new ArrayList<Map<String,Object>>();
    for (var item : nativeItems.values()) {
      if (item.getEquipSlot() != 3) continue;
      var weapon = new LinkedHashMap<String,Object>();
      weapon.put("id",item.getId()); weapon.put("name",item.getName());
      var serverItem = ServerCacheManager.INSTANCE.getItems().get(item.getId());
      weapon.put("params",serverItem != null ? serverItem.getParamsRaw() : null);
      weapon.put("models",java.util.stream.IntStream.of(item.getMaleModel0(),item.getMaleModel1(),item.getMaleModel2(),item.getFemaleModel0(),item.getFemaleModel1(),item.getFemaleModel2()).filter(i -> i >= 0).distinct().boxed().toList());
      weapons.add(weapon);
    }
    root.put("weapons",weapons);
    client.close();
    var params = new LinkedHashMap<String,Integer>();
    for (String name : List.of("attack_anim","defend_anim","death_anim","npc_attack_type","attack_anim_stance1","attack_anim_stance2","attack_anim_stance3","attack_anim_stance4")) {
      params.put(name,RSCM.INSTANCE.asRSCM("param."+name));
    }
    root.put("params",params);
    var rows = new ArrayList<Map<String,Object>>();
    for (var npc : ServerCacheManager.INSTANCE.getNpcs().values()) {
      var row = new LinkedHashMap<String,Object>();
      row.put("id",npc.getId()); row.put("symbol",npc.getInternalName()); row.put("name",npc.getName());
      row.put("stand",npc.getStandAnim()); row.put("walk",npc.getWalkAnim());
      row.put("attackable",java.util.stream.IntStream.range(0,5).anyMatch(i -> "Attack".equalsIgnoreCase(npc.getActions().getOpOrNull(i))));
      row.put("transforms",npc.getTransforms()); row.put("contentGroup",npc.getContentGroup());
      row.put("combatLevel",npc.getCombatLevel()); row.put("params",npc.getParamsRaw());
      row.put("models",nativeNpcs.containsKey(npc.getId()) ? nativeNpcs.get(npc.getId()).getModels() : List.of());
      var definition = new TreeMap<String,Object>();
      for (var field : npc.getClass().getDeclaredFields()) {
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || List.of("paramMap","patrol").contains(field.getName())) continue;
        field.setAccessible(true);
        definition.put(field.getName(),field.get(npc));
      }
      row.put("definition",definition);
      rows.add(row);
    }
    root.put("npcs",rows);
    Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(root));
    System.out.println("NPCS="+rows.size()+" SEQUENCES="+provider.getMappings().get("seq").size());
  }
}
