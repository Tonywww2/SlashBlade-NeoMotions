package com.tonywww.slashblade_neomotions.combat;

import dev.kosmx.playerAnim.core.data.gson.AnimationSerializing;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Arrays;
import java.io.InputStreamReader;
import com.google.gson.JsonParser;
import static org.junit.jupiter.api.Assertions.*;

class AnimationAssetsTest {
    @Test void sourceCollisionWindowsRemainAlignedWithServerTicks() {
        var actions=java.util.stream.Stream.concat(java.util.stream.Stream.of(GreatSwordMove.values()).map(m->m.source),
                java.util.stream.Stream.of(DualBladeMove.values()).map(m->m.source)).toList();
        for(var action:actions) for(var hit:action.hits) {
            assertTrue(hit.tick()*3>=hit.beginFrame()&&hit.tick()*3<=hit.endFrame(),"Contact left source hit window: "+action.motionId);
            assertTrue(hit.tick()<=action.duration);
        }
        assertEquals(16,SourceAction.chargeThreshold(1));assertEquals(31,SourceAction.chargeThreshold(2));
        assertEquals(46,SourceAction.chargeThreshold(3));assertEquals(60,SourceAction.overheatTick());
        assertEquals(11,GreatSwordMove.CHARGED_SLASH.source.window("_Pre_STORNG_CHARGE",-1));
        assertEquals(16,GreatSwordMove.CHARGED_SLASH.source.window("_STORNG_CHARGE",-1));
        assertEquals(6,DualBladeMove.FLURRY.strikes.size());
        assertEquals(0,DualBladeMove.LANDING.strikes.size(),"Source landing has no extra attack");
    }
    @Test void everyComboHasPlayableBodyAndBladeAnimations() throws Exception {
        for (var move : GreatSwordMove.values()) {
            String base = "/assets/slashblade_neomotions/";
            try (var json = getClass().getResourceAsStream(base + "player_animation/" + move.path + ".json")) {
                assertNotNull(json, move.path);
                var clips = AnimationSerializing.deserializeAnimation(json);
                assertEquals(1, clips.size(), move.path);
                assertEquals(move.duration, clips.get(0).endTick, move.path);
                assertEquals(move == GreatSwordMove.READY, clips.get(0).isInfinite, move.path);
            }
            try (var vmd = getClass().getResourceAsStream(base + "motions/" + move.path + ".vmd")) {
                assertNotNull(vmd, move.path);
                byte[] bytes = vmd.readAllBytes();
                assertTrue(new String(bytes, 0, 30, StandardCharsets.US_ASCII).startsWith("Vocaloid Motion Data 0002"));
                var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int count = buffer.getInt(50);
                assertEquals(54 + count * 111 + 20, bytes.length, move.path);
                int maxFrame = 0;
                for (int i = 0; i < count; i++) maxFrame = Math.max(maxFrame, buffer.getInt(54 + i * 111 + 15));
                assertEquals(move.duration * SourceAction.VMD_FRAMES_PER_TICK, maxFrame, "VMD and damage timeline disagree: " + move.path);
            }
        }
    }
    @Test void dualAnimationsUseBothNativeHardpointsAndAnimateTheSheath() throws Exception {
        for (var move : DualBladeMove.values()) {
            String base = "/assets/slashblade_neomotions/";
            try (var json = getClass().getResourceAsStream(base + "player_animation/" + move.path + ".json")) {
                assertNotNull(json, move.path);
                var clips = AnimationSerializing.deserializeAnimation(json);
                assertEquals(1, clips.size());
                assertEquals(move.duration, clips.get(0).endTick);
                assertEquals(move == DualBladeMove.READY, clips.get(0).isInfinite);
            }
            try (var vmd = getClass().getResourceAsStream(base + "motions/" + move.path + ".vmd")) {
                assertNotNull(vmd, move.path);
                byte[] bytes = vmd.readAllBytes();
                var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
                int count = buffer.getInt(50), lastFrame = move.duration * SourceAction.VMD_FRAMES_PER_TICK;
                assertEquals((lastFrame + 1) * 2, count);
                assertEquals(54 + count * 111 + 20, bytes.length);
                var bladePoses = new HashSet<String>();
                var sheathPoses = new HashSet<String>();
                for (int i = 0; i < count; i++) {
                    int offset = 54 + i * 111;
                    String bone = new String(bytes, offset, 15, StandardCharsets.US_ASCII).replace("\0", "");
                    assertEquals(i % 2 == 0 ? "hardpointA" : "hardpointB", bone);
                    assertEquals(i / 2, buffer.getInt(offset + 15));
                    double norm = 0;
                    for (int f = 0; f < 7; f++) {
                        float value = buffer.getFloat(offset + 19 + f * 4);
                        assertTrue(Float.isFinite(value));
                        if (f >= 3) norm += value * value;
                    }
                    assertEquals(1, norm, .00001, "Invalid quaternion: " + move.path);
                    (i % 2 == 0 ? bladePoses : sheathPoses).add(Arrays.toString(Arrays.copyOfRange(bytes, offset + 19, offset + 47)));
                }
                if (move != DualBladeMove.READY) {
                    assertTrue(bladePoses.size() > 3, "Blade is static: " + move.path);
                    assertTrue(sheathPoses.size() > 3, "Sheath still attached to waist: " + move.path);
                }
                assertNotEquals(bladePoses, sheathPoses, "Both weapons use the same hand: " + move.path);
            }
        }
    }
}
