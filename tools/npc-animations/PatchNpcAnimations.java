import java.nio.file.*;
import java.util.*;
import dev.openrune.filesystem.Cache;
import com.displee.cache.CacheLibrary;
import com.google.gson.*;

/** Applies only verified NPC parameter files to a marked copy of the accepted cache. */
public final class PatchNpcAnimations {
  public static void main(String[] args) throws Exception {
    if (args.length == 3 && args[0].equals("--repair-reference")) {
      Path original=Path.of(args[1]).toRealPath(), stage=Path.of(args[2]).toRealPath();
      if (!stage.getFileName().toString().equals("npc-animations-login-fix-runtime-stage")
          || !Files.isRegularFile(stage.resolve("ISOLATED-CANDIDATE")) || stage.equals(original))
        throw new IllegalStateException("Unmarked or unsafe repair stage");
      PreserveConfigReference.preserve(original.resolve(".data/cache/SERVER"),stage.resolve(".data/cache/SERVER"),58);
      verify(original,stage,Collections.emptySet(),false);
      return;
    }
    Path compiled=Path.of(args[0]).toRealPath(), original=Path.of(args[1]).toRealPath(), stage=Path.of(args[2]).toRealPath();
    if(!Set.of("npc-animations-runtime-stage", "npc-animations-lifecycle-runtime-stage").contains(stage.getFileName().toString())
        || !Files.isRegularFile(stage.resolve("ISOLATED-CANDIDATE")) || stage.equals(original) || stage.equals(compiled))
      throw new IllegalStateException("Unmarked or unsafe candidate stage");
    Set<Integer> ids=new TreeSet<>();
    var report=JsonParser.parseString(Files.readString(Path.of(args[3]))).getAsJsonObject();
    if(report.get("revision").getAsInt()!=240) throw new IllegalStateException("Revision differs");
    for(var row:report.getAsJsonArray("patches")) ids.add(row.getAsJsonObject().get("id").getAsInt());
    Cache source=Cache.Companion.load(compiled.resolve(".data/cache/SERVER"));
    // CodecsOsrs.kt: revision-240 NpcDecoder uses SERVER config archive 58.
    // Native client NPCs are archive 9 and are deliberately left untouched.
    int npcArchive=58;
    var target=new CacheLibrary(stage.resolve(".data/cache/SERVER"),false,null);
    for(int id:ids) {
      byte[] data=source.data(2,npcArchive,id,null);
      if(data==null) throw new IllegalStateException("Missing compiled server NPC "+id);
      target.put(2,npcArchive,id,data,null);
    }
    target.update();target.close();source.close();
    // Displee interprets the native -1 varp file sentinel as 32767 when rewriting
    // the shared config reference table. Retain every original metadata entry
    // and update only the NPC group's checksums, lengths and revision.
    PreserveConfigReference.preserve(original.resolve(".data/cache/SERVER"),stage.resolve(".data/cache/SERVER"),npcArchive);
    verify(original,stage,ids,true);
  }

  private static void verify(Path original,Path stage,Set<Integer> ids,boolean checkNpcFiles) throws Exception {
    int npcArchive=58;
    for(String flavour:List.of("LIVE","SERVER")) {
      Cache before=Cache.Companion.load(original.resolve(".data/cache/"+flavour));
      Cache after=Cache.Companion.load(stage.resolve(".data/cache/"+flavour));
      if(!Arrays.equals(before.indices(),after.indices()))throw new IllegalStateException("Changed indices");
      int preserved=0;
      for(int index:before.indices()) {
        if(!Arrays.equals(before.archives(index),after.archives(index))) throw new IllegalStateException("Changed archives");
        for(int archive:before.archives(index)) {
          if(!Arrays.equals(before.files(index,archive),after.files(index,archive)))
            throw new IllegalStateException("Changed file IDs "+flavour+"/"+index+"/"+archive);
          if(flavour.equals("SERVER")&&index==2&&archive==npcArchive) {
            for(int file:before.files(index,archive)) if(checkNpcFiles&&!ids.contains(file)&&!Arrays.equals(before.data(index,archive,file,null),after.data(index,archive,file,null)))
              throw new IllegalStateException("Changed unrelated NPC "+file);
          } else {
            if(!Arrays.equals(before.sector(index,archive),after.sector(index,archive)))throw new IllegalStateException("Changed unrelated archive "+flavour+"/"+index+"/"+archive);
            preserved++;
          }
        }
      }
      System.out.println("PRESERVED "+flavour+" archives="+preserved+"; patched NPC definitions="+(flavour.equals("SERVER")?ids.size():0)+"; server NPC archive="+npcArchive);
      before.close();after.close();
    }
  }
}
