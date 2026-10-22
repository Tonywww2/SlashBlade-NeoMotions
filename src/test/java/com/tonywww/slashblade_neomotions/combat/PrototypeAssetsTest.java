package com.tonywww.slashblade_neomotions.combat;

import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class PrototypeAssetsTest {
    @Test void nativeObjParserAcceptsAllWeaponAndInventoryGroups() throws Exception {
        for (String name : new String[]{"greatsword_prototype", "dualblade_prototype", "switchaxe_prototype"}) {
            try (var stream = getClass().getResourceAsStream("/assets/slashblade_neomotions/model/" + name + ".obj")) {
                assertNotNull(stream);
                var obj = new WavefrontObject(name, stream);
                var groups = obj.groupObjects.stream().collect(Collectors.toMap(g -> g.name, g -> g));
                assertTrue(groups.keySet().containsAll(Set.of("blade", "sheath", "blade_damaged", "blade_fragment", "item_blade", "item_bladens", "item_damaged")));
                for (var group : groups.values()) {
                    assertFalse(group.faces.isEmpty(), group.name);
                    for (var face : group.faces) {
                        assertEquals(3, face.vertices.length);
                        assertEquals(3, face.textureCoordinates.length);
                        for (var v : face.vertices) assertTrue(Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z));
                    }
                }
                float intactTip = groups.get("blade").faces.stream().flatMap(f -> java.util.Arrays.stream(f.vertices)).map(v -> v.x).min(Float::compare).orElseThrow();
                float brokenTip = groups.get("blade_damaged").faces.stream().flatMap(f -> java.util.Arrays.stream(f.vertices)).map(v -> v.x).min(Float::compare).orElseThrow();
                if (!name.equals("switchaxe_prototype")) assertTrue(brokenTip > intactTip + 50, "Broken blade did not lose its tip");
                else {
                    assertTrue(groups.keySet().containsAll(Set.of("blade_core", "blade_edge", "blade_edge_damaged", "sheath_damaged")));
                    float edgeTip = groups.get("blade_edge").faces.stream().flatMap(f -> java.util.Arrays.stream(f.vertices)).map(v -> v.x).max(Float::compare).orElseThrow();
                    float damagedEdge = groups.get("blade_edge_damaged").faces.stream().flatMap(f -> java.util.Arrays.stream(f.vertices)).map(v -> v.x).max(Float::compare).orElseThrow();
                    assertTrue(edgeTip - damagedEdge > 50);
                }
                for (var face : groups.get("item_blade").faces)
                    for (var v : face.vertices) assertTrue(Math.abs(v.x) <= 52 && Math.abs(v.y) <= 52, "Inventory model extends outside its icon");
            }
        }
    }
    @Test void metalAtlasAndExactNamesArePackaged() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/slashblade_neomotions/model/prototype_metal.png")) {
            assertNotNull(stream);
            var texture = ImageIO.read(stream);
            assertEquals(256, texture.getWidth()); assertEquals(256, texture.getHeight());
            assertNotEquals(texture.getRGB(20,20), texture.getRGB(84,20));
        }
        try (var stream = getClass().getResourceAsStream("/assets/slashblade_neomotions/lang/zh_cn.json")) {
            String language = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(language.contains("大剑 ⌈⌋ 试作型"));
            assertTrue(language.contains("双刀 ⌈⌋ 试作型"));
            assertTrue(language.contains("斩斧 试作型"));
        }
    }
}
