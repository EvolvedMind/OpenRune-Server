import com.displee.cache.CacheLibrary;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Keeps native file-ID metadata, including the signed -1 varp sentinel, intact. */
public final class PreserveConfigReference {
  private record Layout(int protocol, int flags, int[] archives, Map<Integer,List<int[]>> fields) {}

  private static int smart(ByteBuffer in) {
    if (in.get(in.position()) < 0) return in.getInt() & 0x7fffffff;
    int value = Short.toUnsignedInt(in.getShort());
    return value == 32767 ? -1 : value;
  }

  private static int count(ByteBuffer in, int protocol) {
    return protocol >= 7 ? smart(in) : Short.toUnsignedInt(in.getShort());
  }

  private static Layout layout(byte[] bytes) {
    ByteBuffer in = ByteBuffer.wrap(bytes);
    int protocol = Byte.toUnsignedInt(in.get());
    if (protocol < 5 || protocol > 7) throw new IllegalStateException("Unsupported reference protocol");
    if (protocol >= 6) in.getInt();
    int flags = Byte.toUnsignedInt(in.get());
    if ((flags & ~15) != 0) throw new IllegalStateException("Unsupported reference flags");
    int[] archives = new int[count(in,protocol)];
    int id = 0;
    for (int i = 0; i < archives.length; i++) archives[i] = id += count(in,protocol);
    var fields = new LinkedHashMap<Integer,List<int[]>>();
    for (int archive : archives) fields.put(archive,new ArrayList<>());
    if ((flags & 1) != 0) in.position(in.position() + archives.length * 4);
    section(in,archives,fields,4); // CRCs
    if ((flags & 8) != 0) section(in,archives,fields,4);
    if ((flags & 2) != 0) section(in,archives,fields,64);
    if ((flags & 4) != 0) section(in,archives,fields,8);
    section(in,archives,fields,4); // Revisions
    int[] files = new int[archives.length];
    for (int i = 0; i < files.length; i++) files[i] = count(in,protocol);
    for (int size : files) for (int i = 0; i < size; i++) count(in,protocol);
    if ((flags & 1) != 0) for (int size : files) in.position(in.position() + size * 4);
    if (in.hasRemaining()) throw new IllegalStateException("Unexpected reference-table tail");
    return new Layout(protocol,flags,archives,fields);
  }

  private static void section(ByteBuffer in, int[] ids, Map<Integer,List<int[]>> fields, int size) {
    for (int id : ids) {
      fields.get(id).add(new int[]{in.position(),size});
      in.position(in.position()+size);
    }
  }

  static byte[] merge(byte[] original, byte[] candidate, int changedArchive) {
    Layout before = layout(original), after = layout(candidate);
    if (before.protocol != after.protocol || before.flags != after.flags
        || !Arrays.equals(before.archives,after.archives) || !before.fields.containsKey(changedArchive))
      throw new IllegalStateException("Reference structure changed");
    byte[] result = original.clone();
    if (before.protocol >= 6) System.arraycopy(candidate,1,result,1,4);
    var originalFields = before.fields.get(changedArchive);
    var candidateFields = after.fields.get(changedArchive);
    for (int i = 0; i < originalFields.size(); i++) {
      int[] to = originalFields.get(i), from = candidateFields.get(i);
      if (to[1] != from[1]) throw new IllegalStateException("Reference field size changed");
      System.arraycopy(candidate,from[0],result,to[0],to[1]);
    }
    return result;
  }

  private static byte[] reference(Path cache) throws IOException {
    try (RandomAccessFile index = new RandomAccessFile(cache.resolve("main_file_cache.idx255").toFile(),"r");
         RandomAccessFile data = new RandomAccessFile(cache.resolve("main_file_cache.dat2").toFile(),"r")) {
      index.seek(2 * 6);
      int length = medium(index), sector = medium(index);
      byte[] container = new byte[length];
      int offset = 0, chunk = 0;
      while (offset < length) {
        if (sector <= 0) throw new IOException("Invalid reference sector");
        data.seek(sector * 520L);
        int archive = data.readUnsignedShort(), ordinal = data.readUnsignedShort();
        sector = medium(data);
        int indexId = data.readUnsignedByte();
        if (archive != 2 || ordinal != chunk++ || indexId != 255) throw new IOException("Invalid reference header");
        int size = Math.min(512,length-offset);
        data.readFully(container,offset,size);
        offset += size;
      }
      ByteBuffer in = ByteBuffer.wrap(container);
      int compression = Byte.toUnsignedInt(in.get()), packed = in.getInt();
      if (compression == 0) return Arrays.copyOfRange(container,5,5+packed);
      if (compression != 2) throw new IOException("Unsupported reference compression");
      int unpacked = in.getInt();
      try (var gzip = new GZIPInputStream(new ByteArrayInputStream(container,9,packed))) {
        byte[] result = gzip.readAllBytes();
        if (result.length != unpacked) throw new IOException("Reference length mismatch");
        return result;
      }
    }
  }

  private static int medium(RandomAccessFile in) throws IOException {
    return (in.readUnsignedByte()<<16) | (in.readUnsignedByte()<<8) | in.readUnsignedByte();
  }

  static void preserve(Path original, Path candidate, int changedArchive) throws IOException {
    byte[] merged = merge(reference(original),reference(candidate),changedArchive);
    var packed = new ByteArrayOutputStream();
    try (var gzip = new GZIPOutputStream(packed)) { gzip.write(merged); }
    byte[] compressed = packed.toByteArray();
    byte[] container = ByteBuffer.allocate(9+compressed.length)
      .put((byte)2).putInt(compressed.length).putInt(merged.length).put(compressed).array();
    try (var writer = new CacheLibrary(candidate,false,null)) {
      if (!writer.getIndex255().writeArchiveSector(2,container)) throw new IOException("Reference write failed");
    }
  }
}
