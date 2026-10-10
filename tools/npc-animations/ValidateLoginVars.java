import com.google.gson.GsonBuilder;
import dev.openrune.ServerCacheManager;
import dev.openrune.rscm.RSCM;
import dev.openrune.rscm.RSCMType;
import org.rsmod.game.entity.Player;
import java.nio.file.*;
import java.util.*;

/** Exercises the real login account flag against an isolated revision-240 cache. */
public final class ValidateLoginVars {
  public static void main(String[] args) throws Exception {
    ServerCacheManager.INSTANCE.init(240).close();
    var manager = ServerCacheManager.INSTANCE;
    int id = RSCM.INSTANCE.asRSCM("varbit.new_player_account",RSCMType.VARBIT);
    var bit = manager.getVarbit(id);
    var missing = new TreeMap<Integer,Integer>();
    manager.getVarbits().forEach((key,value) -> {
      if (manager.getVarp(value.getVarp()) == null) missing.put(key,value.getVarp());
    });
    var report = new LinkedHashMap<String,Object>();
    report.put("varbit",id); report.put("baseVar",bit == null ? null : bit.getVarp());
    report.put("basePresent",bit != null && manager.getVarp(bit.getVarp()) != null);
    report.put("varps",manager.getVarps().size()); report.put("varbits",manager.getVarbits().size());
    report.put("missingBases",missing);
    var hook = Class.forName("org.rsmod.api.net.rsprot.player.AccountLoadResponseHook");
    var field = hook.getDeclaredField("Companion"); field.setAccessible(true);
    Object companion = field.get(null);
    var setter = companion.getClass().getDeclaredMethod("setNewAccount",Player.class,boolean.class);
    var getter = companion.getClass().getDeclaredMethod("getNewAccount",Player.class);
    setter.setAccessible(true); getter.setAccessible(true);
    var outcomes = new ArrayList<Map<String,Object>>();
    boolean passed = missing.isEmpty() && Boolean.TRUE.equals(report.get("basePresent"));
    for (boolean isNew : new boolean[]{true,false}) {
      var player = new Player();
      try {
        setter.invoke(companion,player,isNew);
        if ((Boolean)getter.invoke(companion,player) != isNew) throw new AssertionError("Flag did not round-trip");
        setter.invoke(companion,player,!isNew);
        if ((Boolean)getter.invoke(companion,player) == isNew) throw new AssertionError("Flag did not toggle");
        outcomes.add(Map.of("newAccount",isNew,"passed",true));
      } catch (java.lang.reflect.InvocationTargetException e) {
        passed = false;
        outcomes.add(Map.of("newAccount",isNew,"passed",false,"error",e.getCause().toString()));
      }
    }
    report.put("accountInitialisation",outcomes); report.put("passed",passed);
    Files.writeString(Path.of(args[0]),new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(report));
    System.out.println("Login flag: " + outcomes + "; unresolved varbit bases=" + missing.size());
    if (!passed && !(args.length == 2 && args[1].equals("--expect-failure"))) System.exit(1);
    if (passed && args.length == 2) throw new AssertionError("Failure was expected");
  }
}
