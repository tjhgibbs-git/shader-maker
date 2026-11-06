package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/** Immutable versioned project, shared by the client, CPU preview and pack compiler. */
public record Project(Map<String, Double> values, Curves.Channels curves, List<Layer> layers, List<Fog> fog) {
    public Project(Map<String,Double> values, Curves.Channels curves, List<Layer> layers) { this(values,curves,layers,List.of()); }
    /** The base grade remains unconditional. Layers run in list order over it. */
    public record Layer(String name, boolean enabled, double strength, Map<String, Double> values, Curves.Channels curves,
                        Set<String> biomes, Atmosphere.Range altitude, Atmosphere.CircularRange time,
                        Atmosphere.Weather weather, Atmosphere.Range lightning, String visualBiomeGroup, WorldSignals.Controls contextControls,
                        Options options) {
        public Layer {
            Objects.requireNonNull(options);
            if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid layer name");
            name = name.strip();
            Objects.requireNonNull(contextControls);
            if (!Double.isFinite(strength) || strength < 0 || strength > 1) throw new IllegalArgumentException("Invalid layer strength");
            if (lightning!=null) Atmosphere.unitRange(lightning);
            values = Map.copyOf(values);
            validateGrade(values, curves);
            if(visualBiomeGroup!=null) {VisualBiomes.get(visualBiomeGroup);if(!biomes.isEmpty())throw new IllegalArgumentException("Choose exact IDs or a visual group, not both");}
            biomes = Set.copyOf(biomes);
            for (String biome : biomes) if (!biome.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                throw new IllegalArgumentException("Invalid biome identifier: " + biome);
            if (time != null && (time.period() != 24000 || time.start() < 0 || time.start() >= 24000
                    || time.end() < 0 || time.end() >= 24000)) throw new IllegalArgumentException("Invalid time range");
        }
        public Layer(Project grade, boolean enabled, double strength, Set<String> biomes,
                     Atmosphere.Range altitude, Atmosphere.CircularRange time) {
            this("Layer", grade, enabled, strength, biomes, altitude, time);
        }
        public Layer(String name, Project grade, boolean enabled, double strength, Set<String> biomes,
                     Atmosphere.Range altitude, Atmosphere.CircularRange time) {
            this(name, enabled, strength, grade.values(), grade.curves(), biomes, altitude, time);
        }
        public Layer(boolean enabled, double strength, Map<String, Double> values, Curves.Channels curves,
                     Set<String> biomes, Atmosphere.Range altitude, Atmosphere.CircularRange time) {
            this("Layer", enabled, strength, values, curves, biomes, altitude, time);
        }
        /** Source compatibility for v4 grade records. */
        public Layer(String name,boolean enabled,double strength,Map<String,Double> values,Curves.Channels curves,
                     Set<String> biomes,Atmosphere.Range altitude,Atmosphere.CircularRange time) {
            this(name,enabled,strength,values,curves,biomes,altitude,time,null,null);
        }
        public Layer(String name,Project grade,boolean enabled,double strength,Set<String> biomes,
                     Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning) {
            this(name,enabled,strength,grade.values(),grade.curves(),biomes,altitude,time,weather,lightning);
        }
        public Layer(String name,boolean enabled,double strength,Map<String,Double> values,Curves.Channels curves,
                     Set<String> biomes,Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning) {
            this(name,enabled,strength,values,curves,biomes,altitude,time,weather,lightning,null);
        }
        public Layer(String name,Project grade,boolean enabled,double strength,Set<String> biomes,
                     Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning,String visualBiomeGroup) {
            this(name,enabled,strength,grade.values(),grade.curves(),biomes,altitude,time,weather,lightning,visualBiomeGroup);
        }
        /** Source compatibility: existing layers retain full-screen scope and no new signals. */
        public Layer(String name,boolean enabled,double strength,Map<String,Double> values,Curves.Channels curves,
                     Set<String> biomes,Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning,String visualBiomeGroup) {
            this(name,enabled,strength,values,curves,biomes,altitude,time,weather,lightning,visualBiomeGroup,WorldSignals.Controls.NONE);
        }
        public Layer(String name,Project grade,boolean enabled,double strength,Set<String> biomes,
                     Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning,String visualBiomeGroup,WorldSignals.Controls contextControls) {
            this(name,enabled,strength,grade.values(),grade.curves(),biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls);
        }
        /** Source compatibility: layers without options are automatic and have no area. */
        public Layer(String name,boolean enabled,double strength,Map<String,Double> values,Curves.Channels curves,
                     Set<String> biomes,Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning,String visualBiomeGroup,WorldSignals.Controls contextControls) {
            this(name,enabled,strength,values,curves,biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,Options.NONE);
        }
        public Layer(String name,Project grade,boolean enabled,double strength,Set<String> biomes,
                     Atmosphere.Range altitude,Atmosphere.CircularRange time,Atmosphere.Weather weather,Atmosphere.Range lightning,String visualBiomeGroup,WorldSignals.Controls contextControls,Options options) {
            this(name,enabled,strength,grade.values(),grade.curves(),biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,options);
        }
        public Layer withOptions(Options next){return new Layer(name,enabled,strength,values,curves,biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,next);}
        public Layer withEnabled(boolean next){return new Layer(name,next,strength,values,curves,biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,options);}
        public Layer withName(String next){return new Layer(next,enabled,strength,values,curves,biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,options);}
        public Layer withGrade(Project grade){return new Layer(name,enabled,strength,grade.values(),grade.curves(),biomes,altitude,time,weather,lightning,visualBiomeGroup,contextControls,options);}
        /** Stable /posteffect identifier path derived from the layer name. */
        public String commandId(){return Options.slug(name);}
        public double weight(Atmosphere.Context c) {
            if(!enabled)return 0;
            double w=strength;
            if(!biomes.isEmpty())w*=c!=null&&biomes.contains(c.biome())?1:0;
            if(visualBiomeGroup!=null)w*=c!=null&&VisualBiomes.previewMatch(visualBiomeGroup,c.biome())?1:0;
            if(altitude!=null)w*=c==null?0:altitude.weight(c.y());
            if(time!=null)w*=c==null?0:time.weight(c.dayTime());
            if(weather!=null)w*=c==null?0:weather.weight(c);
            if(lightning!=null)w*=c==null?0:Atmosphere.unitWeight(lightning,c.flash());
            if(contextControls.active())w*=c!=null&&contextControls.matches(c.signals())?1:0;
            if(options.area()!=null)w*=c==null?0:options.area().weight(c.x(),c.z());
            if(options.command())w*=c!=null&&c.commands()?1:0;
            return w;
        }
        public Project project() { return new Project(values, curves); }
    }
    /** Exact horizontal camera box, inclusive, with an inward feather in blocks. */
    public record Area(double minX, double maxX, double minZ, double maxZ, double feather) {
        public Area {
            for(double v:new double[]{minX,maxX,minZ,maxZ,feather})if(!Double.isFinite(v)||Math.abs(v)>3.0e7)throw new IllegalArgumentException("Area values must be finite world coordinates");
            if(minX>maxX||minZ>maxZ)throw new IllegalArgumentException("Area minimum must not exceed maximum");
            if(feather<0)throw new IllegalArgumentException("Area feather must not be negative");
        }
        public double weight(double x,double z){
            if(!Double.isFinite(x)||!Double.isFinite(z)||x<minX||x>maxX||z<minZ||z>maxZ)return 0;
            if(feather==0)return 1;
            double edge=Math.min(Math.min(x-minX,maxX-x),Math.min(z-minZ,maxZ-z))/feather;
            return Atmosphere.smoothUnit(edge);
        }
        public JsonObject json(){var j=new JsonObject();j.addProperty("minX",minX);j.addProperty("maxX",maxX);j.addProperty("minZ",minZ);j.addProperty("maxZ",maxZ);j.addProperty("feather",feather);return j;}
        public static Area read(JsonObject j){return new Area(requiredNumber(j,"minX"),requiredNumber(j,"maxX"),requiredNumber(j,"minZ"),requiredNumber(j,"maxZ"),requiredNumber(j,"feather"));}
    }
    /**
     * Layer options added in project v10. {@code command} layers are not part of the always-on chain:
     * each exports as its own vanilla post effect, switched with /posteffect, fading in over fadeSeconds.
     */
    public record Options(Area area, boolean command, double fadeSeconds) {
        public static final Options NONE=new Options(null,false,1);
        public Options {
            if(!Double.isFinite(fadeSeconds)||fadeSeconds<0||fadeSeconds>30)throw new IllegalArgumentException("Fade time must be 0–30 seconds");
        }
        public boolean isDefault(){return area==null&&!command&&fadeSeconds==1;}
        public Options withArea(Area a){return new Options(a,command,fadeSeconds);}
        public Options withCommand(boolean c){return new Options(area,c,fadeSeconds);}
        public Options withFade(double f){return new Options(area,command,f);}
        public static String slug(String name){
            String s=name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","_").replaceAll("^_+|_+$","");
            return s.isEmpty()?"layer":s.length()>48?s.substring(0,48):s;
        }
    }
    public Project(Map<String, Double> values, Curves.Channels curves) { this(values, curves, List.of()); }
    public record Control(String key, String label, double min, double max, double initial) {}
    public static final List<Control> CONTROLS = List.of(
        new Control("opacity", "Intensity", 0, 1, 1),
        new Control("exposure", "Exposure", -3, 3, 0),
        new Control("contrast", "Contrast", 0, 2, 1),
        new Control("saturation", "Saturation", 0, 2, 1),
        new Control("vibrance", "Vibrance", -1, 1, 0),
        new Control("shoulder", "Highlight roll-off", 0, 1, 0),
        new Control("vignette", "Vignette", 0, 1, 0),
        new Control("black", "Black point", 0, .95, 0),
        new Control("white", "White point", .05, 1, 1),
        new Control("gamma", "Midtone gamma", .2, 3, 1),
        new Control("red", "Red balance", 0, 2, 1),
        new Control("green", "Green balance", 0, 2, 1),
        new Control("blue", "Blue balance", 0, 2, 1));
    public static final List<String> PRESETS = List.of("Neutral", "Moonlit", "Golden dusk", "Dappled forest", "Cold mist");
    public Project {
        values = Map.copyOf(values);
        validateGrade(values, curves);
        layers = List.copyOf(layers);
        fog = List.copyOf(fog);
    }
    private static void validateGrade(Map<String, Double> values, Curves.Channels curves) {
        Objects.requireNonNull(curves);
        for (Control c : CONTROLS) {
            Double v = values.get(c.key);
            if (v == null || !Double.isFinite(v) || v < c.min || v > c.max)
                throw new IllegalArgumentException("Invalid " + c.label);
        }
        if (values.get("black") >= values.get("white")) throw new IllegalArgumentException("Black point must be below white point");
    }
    public static Project neutral() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (Control c : CONTROLS) values.put(c.key, c.initial);
        return new Project(values, Curves.identity());
    }
    public double get(String key) { return values.get(key); }
    public Project with(String key, double value) {
        Map<String, Double> next = new HashMap<>(values);
        if (key.equals("black")) value = Math.min(value, get("white") - .01);
        if (key.equals("white")) value = Math.max(value, get("black") + .01);
        next.put(key, value);
        return new Project(next, curves, layers, fog);
    }
    public Project withCurves(Curves.Channels curves) { return new Project(values, curves, layers, fog); }
    public Project withCurve(int channel, Curves.Curve curve) {
        var c = new ArrayList<>(List.of(curves.master(), curves.red(), curves.green(), curves.blue()));
        c.set(channel, curve);
        return new Project(values, new Curves.Channels(c.get(0), c.get(1), c.get(2), c.get(3)), layers, fog);
    }
    public Project withLayers(List<Layer> layers) { return new Project(values, curves, layers, fog); }

    public Project withFog(List<Fog> fog) { return new Project(values,curves,layers,fog); }

    /** Null context means world data is unavailable: conditioned layers have zero weight. */
    public Atmosphere.Color evaluate(Atmosphere.Color source, double x, double y, Atmosphere.Context context,
                                     Map<String, Double> biomeWeights) {
        if (biomeWeights != null) {
            double total = 0;
            for (double value : biomeWeights.values()) {
                if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid biome weights");
                total += value;
            }
            if (total > 1 + 1e-9) throw new IllegalArgumentException("Invalid biome weights");
        }
        Atmosphere.Color result = grade().apply(source, x, y, get("opacity"), curves);
        for (Layer layer : layers) {
            if (!layer.enabled()) continue;
            double weight = layer.strength();
            if (!layer.biomes().isEmpty()) {
                if (context == null) weight = 0;
                else if (biomeWeights == null) weight *= layer.biomes().contains(context.biome()) ? 1 : 0;
                else {
                    double biomeWeight = 0;
                    for (String biome : layer.biomes()) biomeWeight += biomeWeights.getOrDefault(biome, 0.0);
                    if (!Double.isFinite(biomeWeight) || biomeWeight < 0 || biomeWeight > 1 + 1e-9)
                        throw new IllegalArgumentException("Invalid biome weights");
                    weight *= Math.min(1, biomeWeight);
                }
            }
            if(layer.visualBiomeGroup()!=null)weight*=context!=null&&VisualBiomes.previewMatch(layer.visualBiomeGroup(),context.biome())?1:0;
            if (layer.altitude() != null) weight *= context == null ? 0 : layer.altitude().weight(context.y());
            if (layer.time() != null) weight *= context == null ? 0 : layer.time().weight(context.dayTime());
            if (layer.weather()!=null) weight *= context==null?0:layer.weather().weight(context);
            if (layer.lightning()!=null) weight *= context==null?0:Atmosphere.unitWeight(layer.lightning(),context.flash());
            if(layer.contextControls().active())weight*=context!=null&&layer.contextControls().matches(context.signals())?1:0;
            if(layer.options().area()!=null)weight*=context==null?0:layer.options().area().weight(context.x(),context.z());
            if(layer.options().command())weight*=context!=null&&context.commands()?1:0;
            Project p = layer.project();
            Atmosphere.Color graded = p.grade().apply(source, x, y, p.get("opacity"), p.curves());
            result = Atmosphere.blend(result, graded, weight);
        }
        return result;
    }
    public Atmosphere.Color evaluate(Atmosphere.Color source, double x, double y, Atmosphere.Context context) {
        return evaluate(source, x, y, context, null);
    }
    public Atmosphere.Grade grade() {
        return new Atmosphere.Grade(get("exposure"), get("black"), get("white"), get("gamma"), get("contrast"),
            get("saturation"), get("red"), get("green"), get("blue"), get("vignette"), get("shoulder"), get("vibrance"));
    }
    public static Project preset(String name) {
        Project p = neutral();
        String[] keys; double[] v;
        switch (name) {
            case "Neutral": return p;
            case "Moonlit": keys = new String[]{"exposure","saturation","red","green","blue","vignette"}; v = new double[]{-.35,.72,.78,.88,1.25,.25}; break;
            case "Golden dusk": keys = new String[]{"exposure","contrast","red","green","blue","vignette","shoulder","vibrance"}; v = new double[]{.12,1.04,1.08,1.02,.89,.12,.65,.18}; break;
            case "Dappled forest": keys = new String[]{"exposure","contrast","saturation","red","green","blue","shoulder","vibrance","vignette"}; v = new double[]{.15,1.06,.94,1.05,1.025,.94,.5,.2,.1}; break;
            case "Cold mist": keys = new String[]{"contrast","saturation","red","green","blue","black"}; v = new double[]{.82,.7,.85,1,1.15,.02}; break;
            default: throw new IllegalArgumentException("Unknown preset");
        }
        for (int i=0; i<keys.length; i++) p = p.with(keys[i],v[i]);
        return p;
    }
    public JsonObject json() {
        JsonObject root = new JsonObject(), state = new JsonObject(), channels = new JsonObject();
        root.addProperty("format", "shader-maker-project"); root.addProperty("version", layers.stream().anyMatch(l->!l.options().isDefault())?10:layers.stream().anyMatch(l->l.contextControls().active())?9:layers.stream().anyMatch(l->l.visualBiomeGroup()!=null&&l.visualBiomeGroup().startsWith("custom_"))?8:!fog.isEmpty()?7:layers.stream().anyMatch(l->l.visualBiomeGroup()!=null)?6:layers.stream().anyMatch(l->l.weather()!=null||l.lightning()!=null)?5:4); root.addProperty("target", "26.3");
        for (Control c : CONTROLS) state.addProperty(c.key, get(c.key));
        state.addProperty("biome", "");
        int i=0;
        for (Curves.Curve curve : List.of(curves.master(),curves.red(),curves.green(),curves.blue())) {
            JsonArray points = new JsonArray();
            for (Curves.Point point : curve.points()) { JsonArray a = new JsonArray(); a.add(point.input()); a.add(point.output()); points.add(a); }
            channels.add(List.of("master","red","green","blue").get(i++),points);
        }
        state.add("curves",channels); root.add("state",state);
        JsonArray layerArray = new JsonArray();
        for (Layer layer : layers) {
            JsonObject item = new JsonObject();
            item.addProperty("name", layer.name());
            item.addProperty("enabled", layer.enabled()); item.addProperty("strength", layer.strength());
            item.add("grade", layer.project().json().getAsJsonObject("state"));
            JsonArray biomes = new JsonArray(); layer.biomes().stream().sorted().forEach(biomes::add); item.add("biomes", biomes);
            if(layer.contextControls().active())item.add("contextControls",layer.contextControls().json());
            if(layer.visualBiomeGroup()!=null)item.addProperty("visualBiomeGroup",layer.visualBiomeGroup());
            if (layer.altitude() != null) {
                JsonObject a = new JsonObject(); a.addProperty("min",layer.altitude().min()); a.addProperty("max",layer.altitude().max()); a.addProperty("feather",layer.altitude().feather()); item.add("altitude",a);
            }
            if (layer.time() != null) {
                JsonObject t = new JsonObject(); t.addProperty("start",layer.time().start()); t.addProperty("end",layer.time().end()); t.addProperty("feather",layer.time().feather()); item.add("time",t);
            }
            if(layer.weather()!=null) {
                JsonObject w=new JsonObject(); w.add("rain",rangeJson(layer.weather().rain()));w.add("thunder",rangeJson(layer.weather().thunder()));item.add("weather",w);
            }
            if(layer.lightning()!=null)item.add("lightning",rangeJson(layer.lightning()));
            if(layer.options().area()!=null)item.add("area",layer.options().area().json());
            if(layer.options().command())item.addProperty("command",true);
            if(layer.options().fadeSeconds()!=1)item.addProperty("fadeSeconds",layer.options().fadeSeconds());
            layerArray.add(item);
        }
        root.add("layers",layerArray);
        if(!fog.isEmpty()||root.get("version").getAsInt()>=7){var array=new JsonArray();fog.forEach(f->array.add(f.json()));root.add("fog",array);}
        return root;
    }
    public String encode() { return new GsonBuilder().setPrettyPrinting().create().toJson(json()) + "\n"; }
    public static Project decode(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!"shader-maker-project".equals(root.get("format").getAsString()) || (!root.getAsJsonPrimitive("version").isNumber() || !Set.of(1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0).contains(root.get("version").getAsDouble()))
                || root.has("target") && !"26.3".equals(root.get("target").getAsString()))
                throw new IllegalArgumentException("Unsupported project format or Minecraft version");
            JsonObject s = root.getAsJsonObject("state");
            if (root.get("version").getAsDouble() >= 4) requireCompleteGrade(s);
            // v0.1 is static. Never silently bake or discard a browser project's conditions.
            for (String flag : List.of("altitudeEnabled","timeEnabled","facingEnabled"))
                if (s.has(flag) && s.get(flag).getAsBoolean()) throw new IllegalArgumentException("Disable context masks in the source project before importing");
            if (s.has("biome") && !s.get("biome").getAsString().isBlank()
                || number(s,"pulsePeriod",0)>0 && number(s,"pulseDepth",0)>0)
                throw new IllegalArgumentException("Disable biome masks and animated pulses before importing");
            Map<String, Double> values = new HashMap<>();
            for (Control c : CONTROLS) values.put(c.key,number(s,c.key,c.initial));
            Curves.Channels curves = Curves.identity();
            if (s.has("curves")) {
                List<Curves.Curve> cc = new ArrayList<>();
                for (String ch : List.of("master","red","green","blue")) {
                    List<Curves.Point> points = new ArrayList<>();
                    for (JsonElement e : s.getAsJsonObject("curves").getAsJsonArray(ch)) {
                        JsonArray a = e.getAsJsonArray();
                        if (a.size()!=2 || !a.get(0).isJsonPrimitive() || !a.get(1).isJsonPrimitive() || !a.get(0).getAsJsonPrimitive().isNumber() || !a.get(1).getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Invalid curve point");
                        points.add(new Curves.Point(a.get(0).getAsDouble(),a.get(1).getAsDouble()));
                    }
                    cc.add(new Curves.Curve(points));
                }
                curves = new Curves.Channels(cc.get(0),cc.get(1),cc.get(2),cc.get(3));
            }
            List<Layer> layers = new ArrayList<>();
            if (root.get("version").getAsDouble() >= 4) {
                JsonArray array = root.getAsJsonArray("layers");
                if (array == null) throw new IllegalArgumentException("Missing layers");
                for (JsonElement element : array) {
                    JsonObject item = element.getAsJsonObject();
                    JsonPrimitive enabled = item.getAsJsonPrimitive("enabled");
                    if (enabled == null || !enabled.isBoolean()) throw new IllegalArgumentException("Invalid layer enabled");
                    double strength = requiredNumber(item,"strength");
                    JsonObject gradeRoot = new JsonObject(); gradeRoot.addProperty("format","shader-maker-project");
                    gradeRoot.addProperty("version",3);
                    JsonObject layerGrade = item.getAsJsonObject("grade"); requireCompleteGrade(layerGrade);
                    gradeRoot.add("state",layerGrade);
                    Project grade = decode(gradeRoot.toString());
                    Set<String> biomes = new HashSet<>();
                    JsonArray biomeArray = item.getAsJsonArray("biomes");
                    if (biomeArray == null) throw new IllegalArgumentException("Missing layer biomes");
                    for (JsonElement biome : biomeArray) {
                        if (!biome.isJsonPrimitive() || !biome.getAsJsonPrimitive().isString() || !biomes.add(biome.getAsString()))
                            throw new IllegalArgumentException("Invalid layer biome");
                    }
                    Atmosphere.Range altitude = null;
                    if (item.has("altitude")) { JsonObject a = item.getAsJsonObject("altitude"); altitude = new Atmosphere.Range(requiredNumber(a,"min"),requiredNumber(a,"max"),requiredNumber(a,"feather")); }
                    Atmosphere.CircularRange time = null;
                    if (item.has("time")) { JsonObject t = item.getAsJsonObject("time"); time = new Atmosphere.CircularRange(requiredNumber(t,"start"),requiredNumber(t,"end"),24000,requiredNumber(t,"feather")); }
                    String name = "Layer " + (layers.size() + 1);
                    if (item.has("name")) {
                        JsonElement elementName = item.get("name");
                        if (!elementName.isJsonPrimitive() || !elementName.getAsJsonPrimitive().isString())
                            throw new IllegalArgumentException("Invalid layer name");
                        name = elementName.getAsString();
                    }
                    Atmosphere.Weather weather=null;Atmosphere.Range lightning=null;
                    if (root.get("version").getAsDouble()<5 && (item.has("weather")||item.has("lightning")))
                        throw new IllegalArgumentException("Weather / lightning require project version 5");
                    if(item.has("weather")){var w=item.getAsJsonObject("weather");weather=new Atmosphere.Weather(readRange(w.getAsJsonObject("rain")),readRange(w.getAsJsonObject("thunder")));}
                    if(item.has("lightning"))lightning=readRange(item.getAsJsonObject("lightning"));
                    String visualGroup=null;
                    if(item.has("visualBiomeGroup")) {
                        if(root.get("version").getAsDouble()<6||!item.getAsJsonPrimitive("visualBiomeGroup").isString())throw new IllegalArgumentException("Visual groups require project version 6");
                        visualGroup=item.get("visualBiomeGroup").getAsString();
                        if(visualGroup.startsWith("custom_")&&root.get("version").getAsDouble()<8)throw new IllegalArgumentException("Custom sky colours require project version 8");
                    }
                    var signals=WorldSignals.Controls.NONE;
                    if(item.has("contextControls")) {
                        if(root.get("version").getAsDouble()<9)throw new IllegalArgumentException("Render signals require project version 9");
                        signals=WorldSignals.Controls.read(item.getAsJsonObject("contextControls"));
                    }
                    var options=Options.NONE;
                    if(item.has("area")||item.has("command")||item.has("fadeSeconds")) {
                        if(root.get("version").getAsDouble()<10)throw new IllegalArgumentException("Area, command and fade options require project version 10");
                        Area area=item.has("area")?Area.read(item.getAsJsonObject("area")):null;
                        boolean command=false;
                        if(item.has("command")){var c=item.getAsJsonPrimitive("command");if(c==null||!c.isBoolean())throw new IllegalArgumentException("Invalid command flag");command=c.getAsBoolean();}
                        options=new Options(area,command,item.has("fadeSeconds")?requiredNumber(item,"fadeSeconds"):1);
                    }
                    layers.add(new Layer(name,grade,enabled.getAsBoolean(),strength,biomes,altitude,time,weather,lightning,visualGroup,signals,options));
                }
            }
            var fog=new ArrayList<Fog>();
            if(root.has("fog")){if(root.get("version").getAsDouble()<7)throw new IllegalArgumentException("Fog requires project version 7");for(var f:root.getAsJsonArray("fog"))fog.add(Fog.read(f.getAsJsonObject()));}
            else if(root.get("version").getAsDouble()>=7)throw new IllegalArgumentException("Missing fog objects");
            return new Project(values,curves,layers,fog);
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid Shader Maker project",e); }
    }
    private static JsonObject rangeJson(Atmosphere.Range range) {
        var j=new JsonObject();j.addProperty("min",range.min());j.addProperty("max",range.max());j.addProperty("feather",range.feather());return j;
    }
    private static Atmosphere.Range readRange(JsonObject j) {return new Atmosphere.Range(requiredNumber(j,"min"),requiredNumber(j,"max"),requiredNumber(j,"feather"));}
    private static void requireCompleteGrade(JsonObject state) {
        if (state == null) throw new IllegalArgumentException("Missing grade");
        for (Control c : CONTROLS) requiredNumber(state, c.key());
        if (!state.has("curves") || !state.get("curves").isJsonObject()) throw new IllegalArgumentException("Missing curves");
    }
    private static double requiredNumber(JsonObject s, String key) {
        if (s == null || !s.has(key)) throw new IllegalArgumentException("Missing number: " + key);
        return number(s,key,0);
    }
    private static double number(JsonObject s, String key, double fallback) {
        if (!s.has(key)) return fallback;
        JsonPrimitive v = s.getAsJsonPrimitive(key);
        if (!v.isNumber()) throw new IllegalArgumentException("Expected number: " + key);
        return v.getAsDouble();
    }
}
