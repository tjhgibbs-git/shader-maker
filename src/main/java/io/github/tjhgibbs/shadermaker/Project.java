package io.github.tjhgibbs.shadermaker;

import com.google.gson.*;
import java.util.*;

/** Immutable v4 project, shared by the client, CPU preview and pack compiler. */
public record Project(Map<String, Double> values, Curves.Channels curves, List<Layer> layers) {
    /** The base grade remains unconditional. Layers run in list order over it. */
    public record Layer(String name, boolean enabled, double strength, Map<String, Double> values, Curves.Channels curves,
                        Set<String> biomes, Atmosphere.Range altitude, Atmosphere.CircularRange time) {
        public Layer {
            if (name == null || name.isBlank() || name.length() > 64 || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Invalid layer name");
            name = name.strip();
            if (!Double.isFinite(strength) || strength < 0 || strength > 1) throw new IllegalArgumentException("Invalid layer strength");
            values = Map.copyOf(values);
            validateGrade(values, curves);
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
        public Project project() { return new Project(values, curves); }
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
        return new Project(next, curves, layers);
    }
    public Project withCurves(Curves.Channels curves) { return new Project(values, curves, layers); }
    public Project withCurve(int channel, Curves.Curve curve) {
        var c = new ArrayList<>(List.of(curves.master(), curves.red(), curves.green(), curves.blue()));
        c.set(channel, curve);
        return new Project(values, new Curves.Channels(c.get(0), c.get(1), c.get(2), c.get(3)), layers);
    }
    public Project withLayers(List<Layer> layers) { return new Project(values, curves, layers); }

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
            if (layer.altitude() != null) weight *= context == null ? 0 : layer.altitude().weight(context.y());
            if (layer.time() != null) weight *= context == null ? 0 : layer.time().weight(context.dayTime());
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
        root.addProperty("format", "shader-maker-project"); root.addProperty("version", 4); root.addProperty("target", "26.3");
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
            if (layer.altitude() != null) {
                JsonObject a = new JsonObject(); a.addProperty("min",layer.altitude().min()); a.addProperty("max",layer.altitude().max()); a.addProperty("feather",layer.altitude().feather()); item.add("altitude",a);
            }
            if (layer.time() != null) {
                JsonObject t = new JsonObject(); t.addProperty("start",layer.time().start()); t.addProperty("end",layer.time().end()); t.addProperty("feather",layer.time().feather()); item.add("time",t);
            }
            layerArray.add(item);
        }
        root.add("layers",layerArray); return root;
    }
    public String encode() { return new GsonBuilder().setPrettyPrinting().create().toJson(json()) + "\n"; }
    public static Project decode(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!"shader-maker-project".equals(root.get("format").getAsString()) || (!root.getAsJsonPrimitive("version").isNumber() || !Set.of(1.0,2.0,3.0,4.0).contains(root.get("version").getAsDouble()))
                || root.has("target") && !"26.3".equals(root.get("target").getAsString()))
                throw new IllegalArgumentException("Unsupported project format or Minecraft version");
            JsonObject s = root.getAsJsonObject("state");
            if (root.get("version").getAsDouble() == 4) requireCompleteGrade(s);
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
            if (root.get("version").getAsDouble() == 4) {
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
                    layers.add(new Layer(name,grade,enabled.getAsBoolean(),strength,biomes,altitude,time));
                }
            }
            return new Project(values,curves,layers);
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid Shader Maker project",e); }
    }
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
