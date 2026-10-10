import java.io.*;
import java.util.*;

/** Small reference-table regression fixtures; no live cache or server required. */
public final class PreserveConfigReferenceTest {
  private static void smart(DataOutputStream out,int value) throws IOException {
    if (value == -1) out.writeShort(32767);
    else if (value >= 32767) out.writeInt(value | 0x80000000);
    else out.writeShort(value);
  }

  private static byte[] fixture(int flags,boolean shifted,int version,int npc) throws IOException {
    return fixture(flags,shifted,version,npc,false);
  }

  private static byte[] fixture(int flags,boolean shifted,int version,int npc,boolean addedVarp) throws IOException {
    var bytes = new ByteArrayOutputStream();
    var out = new DataOutputStream(bytes);
    out.writeByte(7); out.writeInt(version); out.writeByte(flags);
    smart(out,2); smart(out,58); smart(out,9);
    if ((flags & 1) != 0) { out.writeInt(580); out.writeInt(670); }
    out.writeInt(npc); out.writeInt(671);
    if ((flags & 8) != 0) { out.writeInt(npc+1); out.writeInt(672); }
    if ((flags & 2) != 0) {
      byte[] a = new byte[64], b = new byte[64];
      Arrays.fill(a,(byte)npc); Arrays.fill(b,(byte)67);
      out.write(a); out.write(b);
    }
    if ((flags & 4) != 0) {
      out.writeInt(npc+2); out.writeInt(npc+3); out.writeInt(673); out.writeInt(674);
    }
    out.writeInt(npc+4); out.writeInt(675);
    smart(out,2); smart(out,addedVarp ? 4 : 3);
    smart(out,1); smart(out,4);
    smart(out,shifted ? 32767 : -1); smart(out,1);
    if(addedVarp) { smart(out,65469); smart(out,47); } else smart(out,65516);
    if ((flags & 1) != 0) for (int i=0;i<(addedVarp ? 6 : 5);i++) out.writeInt(700+i);
    return bytes.toByteArray();
  }

  public static void main(String[] args) throws Exception {
    int cases = 0;
    for (int flags : new int[]{0,1,2,4,8,15}) {
      byte[] original=fixture(flags,false,1,10), candidate=fixture(flags,true,2,20);
      byte[] result=PreserveConfigReference.merge(original,candidate,58);
      if (!Arrays.equals(result,fixture(flags,false,2,20)))
        throw new AssertionError("Unrelated metadata or signed sentinel changed: flags="+flags);
      if (!Arrays.equals(original,fixture(flags,false,1,10))) throw new AssertionError("Input mutated");
      cases++;
    }
    try {
      PreserveConfigReference.merge(fixture(0,false,1,10),fixture(4,true,2,20),58);
      throw new AssertionError("Mismatching flags accepted");
    } catch (IllegalStateException expected) { cases++; }
    try {
      PreserveConfigReference.merge(fixture(0,false,1,10),fixture(0,true,2,20),90);
      throw new AssertionError("Missing group accepted");
    } catch (IllegalStateException expected) { cases++; }
    for(int flags:new int[]{0,1,2,4,8,15}) {
      byte[] result=PreserveConfigReference.mergeGroups(fixture(flags,false,1,10),fixture(flags,true,2,20),
        Map.of(67,fixture(flags,false,3,30,true)));
      if(!Arrays.equals(result,fixture(flags,false,2,20,true))) throw new AssertionError("Native new varp metadata lost");
      cases++;
    }
    System.out.println("PASS: " + cases + " reference-table regression fixtures");
  }
}
