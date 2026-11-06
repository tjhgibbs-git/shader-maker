import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import java.util.regex.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import net.minecraft.client.renderer.PostChainConfig;
/** Offline fixture contract checks only; no graphics device or live uniform validation. */
public class VanillaProbeCheck {
 static String includes(String s, ZipFile client) throws Exception {
  Matcher m=Pattern.compile("#include <minecraft:([^>]+)>").matcher(s);StringBuffer b=new StringBuffer();
  while(m.find()) {
   var entry=client.getEntry("assets/minecraft/shaders/include/"+m.group(1));
   m.appendReplacement(b,Matcher.quoteReplacement(includes(new String(client.getInputStream(entry).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),client)));
  }
  m.appendTail(b);return b.toString();
 }
 public static void main(String[] args) throws Exception {
  if(args.length!=2) throw new IllegalArgumentException("Usage: VanillaProbeCheck <26.3-client.jar> <probe-directory>");
  var method=Class.forName("io.github.tjhgibbs.shadermaker.MinecraftContractTest").getDeclaredMethod("compile",String.class,ShaderType.class,boolean.class);method.setAccessible(true);
  int compiled=0,decoded=0;
  try(var client=new ZipFile(args[0]);var paths=Files.list(Path.of(args[1]))) {
   for(var p:paths.filter(p->p.toString().endsWith(".zip")).sorted().toList()) try(var z=new ZipFile(p.toFile())) {
    for(var e:Collections.list(z.entries())) {
     String name=e.getName();
     if(name.endsWith(".json")&&name.contains("post_effect/")) {
      PostChainConfig.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString(new String(z.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))).getOrThrow(); decoded++;
     }
     if(name.endsWith(".fsh")) {
      String shader=includes(new String(z.getInputStream(e).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8),client);
      var variants=name.endsWith("/core/terrain.fsh")?List.of("","#define MULTIDRAW_TERRAIN\n","#define OIT\n#define OIT_ALPHA_ONLY\n#define OIT_DEPTH_BOUNDS\n#define OIT_COEFF_COUNT 8\n#define OIT_WAVELET_RANK 2\n#define OIT_COEFF_ATTACHMENT_COUNT 2\n"):List.of("");
      for(String defines:variants) for(boolean depth:List.of(false,true)) {
       String source=shader.replace("#version 330", "#version 330\n"+defines);
       try(var module=(SPIRVModule)method.invoke(null,source,ShaderType.FRAGMENT,depth)) {
        module.reflect();compiled++;
       } catch(InvocationTargetException ex) {throw new RuntimeException(p+" "+defines,ex.getCause());}
      }
      System.out.println("Compiled/reflected "+p.getFileName());
     }
    }
   }
  }
  if(decoded==0||compiled==0) throw new AssertionError("No probe contracts were checked");
  System.out.println(decoded+" vanilla post-chain decodes; "+compiled+" ShaderC compiles/reflections. No graphics device used.");
 }
}
