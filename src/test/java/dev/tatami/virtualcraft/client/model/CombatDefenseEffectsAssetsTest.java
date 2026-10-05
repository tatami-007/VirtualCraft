package dev.tatami.virtualcraft.client.model;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Resource/linkage checks only; in-game appearance still needs a real client. */
class CombatDefenseEffectsAssetsTest {
    private static final String ROOT = "/assets/virtual_craft/";
    private byte[] bytes(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream(ROOT + name)) {
            assertNotNull(stream, name);
            return stream.readAllBytes();
        }
    }
    private String text(String name) throws Exception { return new String(bytes(name), StandardCharsets.UTF_8); }
    private JsonObject json(String name) throws Exception { return JsonParser.parseString(text(name)).getAsJsonObject(); }

    @Test void allDedicatedShaderProgramsResolveTheirStagesAndVertexContract() throws Exception {
        for (String name : new String[]{"defense_ghost", "defense_spark", "dodge_gold"}) {
            String base = "shaders/core/" + name;
            var program = json(base + ".json");
            assertEquals("virtual_craft:" + name, program.get("vertex").getAsString());
            assertEquals("virtual_craft:" + name, program.get("fragment").getAsString());
            String vertex = text(base + ".vsh");
            String fragment = text(base + ".fsh");
            assertTrue(vertex.startsWith("#version 150"));
            assertTrue(fragment.startsWith("#version 150"));
            Set<String> attributes = new HashSet<>();
            program.getAsJsonArray("attributes").forEach(a -> attributes.add(a.getAsString()));
            assertEquals(Set.of("Position", "UV0", "Color"), attributes);
            for (var value : program.getAsJsonArray("uniforms")) {
                var uniform = value.getAsJsonObject();
                assertEquals(uniform.get("count").getAsInt(), uniform.getAsJsonArray("values").size());
                assertTrue((vertex + fragment).contains(uniform.get("name").getAsString()));
            }
        }
    }

    @Test void dodgeIsProceduralAndGhostKeepsItsWarmGoldPalette() throws Exception {
        var gold = json("shaders/core/dodge_gold.json");
        assertTrue(gold.getAsJsonArray("samplers").isEmpty());
        String ghost = text("shaders/core/defense_ghost.fsh");
        assertTrue(ghost.contains("texCoord * 64.0"));
        assertTrue(ghost.contains("vec3(1.0, 0.96, 0.76)"));
        assertFalse(ghost.contains("vec3(0.88, 0.98, 1.0)"));
    }

    @Test void bloomProgramsResolvePackagedScreenVertexAndAllSamplerContracts() throws Exception {
        for (String name : new String[]{"effect_bloom_blur","effect_bloom_composite"}) {
            var manifest=json("shaders/core/"+name+".json");
            String vertex=manifest.get("vertex").getAsString().split(":")[1];
            String fragment=manifest.get("fragment").getAsString().split(":")[1];
            assertTrue(text("shaders/core/"+vertex+".vsh").startsWith("#version 150"));
            String source=text("shaders/core/"+fragment+".fsh");
            for(var sampler:manifest.getAsJsonArray("samplers"))
                assertTrue(source.contains("sampler2D "+sampler.getAsJsonObject().get("name").getAsString()));
            for(var value:manifest.getAsJsonArray("uniforms")) {
                var uniform=value.getAsJsonObject();
                assertEquals(uniform.get("count").getAsInt(),uniform.getAsJsonArray("values").size());
                assertTrue(source.contains(uniform.get("name").getAsString()));
            }
        }
    }

    @Test void ghostPreservesSkinTransparencyAndSparkIsProceduralNotVanillaAtlas() throws Exception {
        var ghost = json("shaders/core/defense_ghost.json");
        assertEquals("Sampler0", ghost.getAsJsonArray("samplers").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(text("shaders/core/defense_ghost.fsh").contains("skin.a < 0.1"));
        assertTrue(json("shaders/core/defense_spark.json").getAsJsonArray("samplers").isEmpty());
        assertTrue(text("shaders/core/defense_spark.fsh").contains("fwidth(diamond)"));
    }

    @Test void dodgeSoundIsPackagedVorbisAndSoundEventPointsToIt() throws Exception {
        var sound = json("sounds.json").getAsJsonObject("mushroom_king.dodge_success");
        assertNotNull(sound);
        assertEquals("virtual_craft:mushroom_king/dodge_success",
                sound.getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        byte[] audio = bytes("sounds/mushroom_king/dodge_success.ogg");
        assertTrue(audio.length > 1000);
        assertEquals("OggS", new String(audio, 0, 4, StandardCharsets.US_ASCII));
        assertTrue(new String(audio, StandardCharsets.ISO_8859_1).contains("vorbis"));
    }
}
