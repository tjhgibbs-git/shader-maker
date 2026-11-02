package io.github.tjhgibbs.shadermaker.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class SampleImageTest {
    public static void main(String[] args) throws Exception {
        var input=new BufferedImage(128,72,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<72;y++) for(int x=0;x<128;x++) input.setRGB(x,y,x<64?0x123456:0xABCDEF);
        var bytes=new ByteArrayOutputStream();ImageIO.write(input,"PNG",bytes);
        var sample=SampleImage.read(new ByteArrayInputStream(bytes.toByteArray()),"test");
        if(sample.pixels.length!=64*36||sample.pixels[0]!=0xFF123456||sample.pixels[63]!=0xFFABCDEF)
            throw new AssertionError("Reference color and sampling");
        SampleImage.current=sample;
        Path bad=Files.createTempFile("invalid-sample-",".png");
        try {
            Files.writeString(bad,"not an image");
            try {SampleImage.load(bad);throw new AssertionError("Accepted invalid image");}catch(IOException expected){}
            if(SampleImage.current!=sample)throw new AssertionError("Load failure replaced reference");
        } finally {Files.deleteIfExists(bad);}
        var oversized=new BufferedImage(8193,1,BufferedImage.TYPE_INT_RGB);
        bytes.reset();ImageIO.write(oversized,"PNG",bytes);
        try {SampleImage.read(new ByteArrayInputStream(bytes.toByteArray()),"large");throw new AssertionError("Accepted oversized dimensions");}catch(IOException expected){}
        if(!ShaderMakerCommands.describe(java.util.Optional.empty()).contains("unavailable"))throw new AssertionError("Missing world");
        System.out.println("SampleImageTest: colors, invalid input, dimension limit and reference preservation passed");
    }
}
