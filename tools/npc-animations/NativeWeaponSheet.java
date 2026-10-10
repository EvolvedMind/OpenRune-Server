import dev.openrune.filesystem.Cache;
import net.runelite.cache.definitions.loaders.ModelLoader;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class NativeWeaponSheet {
  public static void main(String[] args) throws Exception {
    var ids=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonArray();
    Cache cache=Cache.Companion.load(Path.of(".data/cache/LIVE")); var loader=new ModelLoader();
    int columns=8,cell=140,rows=(ids.size()+columns-1)/columns;
    var img=new BufferedImage(columns*cell,rows*cell,BufferedImage.TYPE_INT_RGB);var g=img.createGraphics();
    g.setColor(Color.WHITE);g.fillRect(0,0,img.getWidth(),img.getHeight());g.setFont(new Font("SansSerif",Font.PLAIN,16));
    for(int i=0;i<ids.size();i++) {
      int id=ids.get(i).getAsInt();var m=loader.load(id,cache.data(7,id,0,null));
      double[] xx=new double[m.vertexCount],yy=new double[m.vertexCount],zz=new double[m.vertexCount];
      for(int v=0;v<xx.length;v++) {xx[v]=m.vertexX[v]*0.86+m.vertexZ[v]*0.5;yy[v]=m.vertexY[v]+m.vertexZ[v]*0.25-m.vertexX[v]*0.15;zz[v]=m.vertexZ[v]*0.86-m.vertexX[v]*0.5;}
      double xmin=Arrays.stream(xx).min().orElse(0),xmax=Arrays.stream(xx).max().orElse(1),ymin=Arrays.stream(yy).min().orElse(0),ymax=Arrays.stream(yy).max().orElse(1);
      double scale=100/Math.max(1,Math.max(xmax-xmin,ymax-ymin));
      int x=i%columns*cell,y=i/columns*cell;
      var faces=new Integer[m.faceCount];for(int f=0;f<faces.length;f++)faces[f]=f;
      Arrays.sort(faces,Comparator.comparingDouble(f->zz[m.faceIndices1[f]]+zz[m.faceIndices2[f]]+zz[m.faceIndices3[f]]));
      for(int f:faces) {
        int[] vertices={m.faceIndices1[f],m.faceIndices2[f],m.faceIndices3[f]},px=new int[3],py=new int[3];
        for(int j=0;j<3;j++){px[j]=x+cell/2+(int)((xx[vertices[j]]-(xmin+xmax)/2)*scale);py[j]=y+65+(int)((yy[vertices[j]]-(ymin+ymax)/2)*scale);}
        g.setColor(new Color(175+f%3*15,180+f%3*15,185+f%3*15));g.fillPolygon(px,py,3);g.setColor(Color.DARK_GRAY);g.drawPolygon(px,py,3);
      }
      g.setColor(Color.BLACK);g.drawString(""+id,x+10,y+133);
    }
    g.dispose();cache.close();ImageIO.write(img,"png",Path.of(args[1]).toFile());
  }
}
