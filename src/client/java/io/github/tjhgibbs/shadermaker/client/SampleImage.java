package io.github.tjhgibbs.shadermaker.client;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;

/** Bounded, immutable CPU reference. Loading failure leaves the previous reference intact. */
final class SampleImage {
    static final int WIDTH = 64, HEIGHT = 36;
    static SampleImage current;
    final int[] pixels;
    final String name;
    private SampleImage(String name, int[] pixels) { this.name=name; this.pixels=pixels; }
    static void bundled() throws IOException {
        try (var stream=SampleImage.class.getResourceAsStream("/assets/shader_maker/samples/day.png")) {
            if(stream==null) throw new IOException("Bundled sample missing");
            current=read(stream,"Day capture");
        }
    }
    static void load(Path path) throws IOException {
        if(Files.size(path)>32*1024*1024) throw new IOException("Image exceeds 32 MB");
        try(var stream=Files.newInputStream(path)) { current=read(stream,path.getFileName().toString()); }
    }
    static SampleImage read(InputStream input,String name) throws IOException {
        try(ImageInputStream stream=ImageIO.createImageInputStream(input)) {
            if(stream==null) throw new IOException("Cannot read image");
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext()) throw new IOException("Use a PNG or JPEG image");
            var reader=readers.next();
            try {
                reader.setInput(stream,true,true);
                int w=reader.getWidth(0), h=reader.getHeight(0);
                if(w<1||h<1||w>8192||h>8192||(long)w*h>32_000_000) throw new IOException("Image dimensions exceed limit");
                var params=reader.getDefaultReadParam();
                params.setSourceSubsampling(Math.max(1,w/1024),Math.max(1,h/576),0,0);
                var image=reader.read(0,params);
                int[] pixels=new int[WIDTH*HEIGHT];
                for(int y=0;y<HEIGHT;y++) for(int x=0;x<WIDTH;x++)
                    pixels[y*WIDTH+x]=image.getRGB(x*image.getWidth()/WIDTH,y*image.getHeight()/HEIGHT);
                return new SampleImage(name,pixels);
            } finally { reader.dispose(); }
        }
    }
}
