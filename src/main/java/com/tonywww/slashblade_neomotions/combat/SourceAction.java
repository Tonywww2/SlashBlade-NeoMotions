package com.tonywww.slashblade_neomotions.combat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Shared runtime timing from extracted MOT/CLIP/USER data. No handwritten hit timers. */
public final class SourceAction {
    public static final int PLAYBACK_SPEED = 4, VMD_FRAMES_PER_TICK = 6;
    private static final JsonObject DATA = read();
    private static final Map<String, SourceAction> CACHE = new HashMap<>();
    public record Hit(int tick, float roll, float pitch, String hand, boolean body, float beginFrame, float endFrame) {}
    public final int duration, cancelTick, motionId, blendTicks, hyperArmorEndTick;
    public final List<Hit> hits;
    private final JsonObject json;
    private SourceAction(JsonObject json) {
        this.json = json;
        duration = json.get("duration").getAsInt(); cancelTick = json.get("cancelTick").getAsInt();
        motionId = json.get("motionId").getAsInt();
        blendTicks = json.get("blendTicks").getAsInt();
        hyperArmorEndTick = json.get("hyperArmorEndTick").getAsInt();
        var parsed = new ArrayList<Hit>();
        for (var entry : json.getAsJsonArray("hits")) {
            var h = entry.getAsJsonObject();
            parsed.add(new Hit(h.get("tick").getAsInt(), h.get("roll").getAsFloat(), h.get("pitch").getAsFloat(), h.get("hand").getAsString(),
                    h.get("body").getAsBoolean(), h.get("frame").getAsFloat(), h.get("endFrame").getAsFloat()));
        }
        hits = List.copyOf(parsed);
    }
    private static JsonObject read() {
        try (var stream = SourceAction.class.getResourceAsStream("/assets/slashblade_neomotions/motions/source_actions.json")) {
            if (stream == null) throw new IllegalStateException("Missing extracted action manifest");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new IllegalStateException("Invalid extracted action manifest", e); }
    }
    public static synchronized SourceAction get(String path) {
        return CACHE.computeIfAbsent(path, p -> new SourceAction(DATA.getAsJsonObject("actions").getAsJsonObject(p)));
    }
    public record ComponentPart(String group, String track) {}
    public static List<ComponentPart> components(String profile) {
        var result = new ArrayList<ComponentPart>();
        for (var entry : DATA.getAsJsonObject(profile).getAsJsonArray("components")) {
            var part = entry.getAsJsonObject();
            result.add(new ComponentPart(part.get("group").getAsString(), part.get("track").getAsString()));
        }
        return List.copyOf(result);
    }
    public static int chargeThreshold(int level) { return DATA.getAsJsonArray("chargeTicks").get(level).getAsInt(); }
    public static int overheatTick() { return DATA.get("overheatTick").getAsInt(); }
    public static int overheatLevel() { return DATA.get("overheatLevel").getAsInt(); }
    public static float tackleDamageRate() { return DATA.get("tackleDamageRate").getAsFloat(); }
    public static float switchAxeParameter(String name) {
        return DATA.getAsJsonObject("switchaxe").getAsJsonObject("parameters").get(name).getAsFloat();
    }
    public String primaryNext() {
        var next = json.get("primaryNext");
        return next == null || next.isJsonNull() ? null : next.getAsString();
    }
    public int modeChangeTick() { return json.has("modeChangeTick") ? json.get("modeChangeTick").getAsInt() : -1; }
    public int window(String field, int fallback) {
        var value = json.getAsJsonObject("cancelFrames").get(field);
        return value == null ? fallback : (int) Math.ceil(value.getAsDouble() / 3.0);
    }
    /** Already adapted to blocks by the converter; only horizontal displacement is physical. */
    public double rootDelta(int tick, int axis) {
        if (tick < 1 || tick > duration) return 0;
        var frames = json.getAsJsonArray("root");
        return frames.get(tick).getAsJsonArray().get(axis).getAsDouble()
                - frames.get(tick - 1).getAsJsonArray().get(axis).getAsDouble();
    }
}
