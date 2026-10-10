import java.nio.file.*;
import java.util.*;
import dev.openrune.filesystem.Cache;
import com.displee.cache.CacheLibrary;

public final class PatchShamans {
  public static void main(String[] args) throws Exception {
    Path compiled=Path.of(args[0]).toRealPath(), original=Path.of(args[1]).toRealPath(), stage=Path.of(args[2]).toRealPath();
    if (!stage.getFileName().toString().equals("shamans-runtime-stage") || stage.equals(original) || stage.equals(compiled)
        || !Files.isRegularFile(stage.resolve("ISOLATED-CANDIDATE"))) throw new IllegalStateException("Unsafe stage");
    Set<Integer> ids=Set.of(6766,6767,7744,7745,8565); int varp=65469;
    Path beforePath=original.resolve(".data/cache/SERVER"), sourcePath=compiled.resolve(".data/cache/SERVER"), targetPath=stage.resolve(".data/cache/SERVER");
    Cache before=Cache.Companion.load(beforePath), source=Cache.Companion.load(sourcePath);
    Set<Integer> beforeVars=new TreeSet<>(), sourceVars=new TreeSet<>();
    for (int id:before.files(2,67)) beforeVars.add(id);
    for (int id:source.files(2,67)) sourceVars.add(id);
    if (beforeVars.contains(varp)) throw new IllegalStateException("Killcount varp collides");
    var expected=new TreeSet<>(beforeVars); expected.add(varp);
    if (!expected.equals(sourceVars)) throw new IllegalStateException("Unexpected varp file-ID change");
    for (int id:beforeVars) if (!Arrays.equals(before.data(2,67,id,null),source.data(2,67,id,null)))
      throw new IllegalStateException("Changed unrelated varp "+id);
    try(var target=new CacheLibrary(targetPath,false,null)) {
      for(int id:ids) target.put(2,58,id,source.data(2,58,id,null),null);
      target.update();
      if(!target.index(2).writeArchiveSector(67,source.sector(2,67))) throw new IllegalStateException("Varp write failed");
    }
    byte[] reference=PreserveConfigReference.mergeGroups(PreserveConfigReference.reference(beforePath),
      PreserveConfigReference.reference(targetPath),Map.of(67,PreserveConfigReference.reference(sourcePath)));
    PreserveConfigReference.write(targetPath,reference);
    source.close(); before.close();
    for(String flavour:List.of("SERVER","LIVE")) {
      before=Cache.Companion.load(original.resolve(".data/cache/"+flavour));
      Cache after=Cache.Companion.load(stage.resolve(".data/cache/"+flavour)); int archives=0, files=0;
      if(!Arrays.equals(before.indices(),after.indices()))throw new IllegalStateException("Changed indices");
      for(int index:before.indices()) {
        if(!Arrays.equals(before.archives(index),after.archives(index)))throw new IllegalStateException("Changed archives");
        for(int archive:before.archives(index)) {
          boolean changed=flavour.equals("SERVER")&&index==2&&(archive==58||archive==67);
          if(!changed) {
            if(!Arrays.equals(before.sector(index,archive),after.sector(index,archive)))throw new IllegalStateException("Changed archive "+index+"/"+archive);
            if(!Arrays.equals(before.files(index,archive),after.files(index,archive)))throw new IllegalStateException("Changed file IDs");
            archives++;
          } else {
            var wanted=new TreeSet<Integer>(); for(int id:before.files(index,archive))wanted.add(id);
            if(archive==67)wanted.add(varp);
            var actual=new TreeSet<Integer>();for(int id:after.files(index,archive))actual.add(id);
            if(!wanted.equals(actual))throw new IllegalStateException("Unexpected selected file IDs");
            for(int id:before.files(index,archive)) {
              if(archive==58&&ids.contains(id))continue;
              if(!Arrays.equals(before.data(index,archive,id,null),after.data(index,archive,id,null)))throw new IllegalStateException("Changed unrelated file "+id);
              files++;
            }
          }
        }
      }
      System.out.println("PRESERVED "+flavour+" archives="+archives+"; config files="+files+"; NPC patches=5; new varps=1");
      before.close();after.close();
    }
  }
}
