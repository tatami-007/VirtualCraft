package dev.tatami.virtualcraft.boss;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MushroomDamageResourcesTest {
    @Test
    void damageNeverScalesWithDifficultyAndBothBypassShields() throws Exception {
        for (String name : new String[]{"mushroom_strike","mushroom_ultimate"}) {
            var data = json("/data/virtual_craft/damage_type/"+name+".json");
            assertEquals("never",data.get("scaling").getAsString());
            assertEquals("virtual_craft."+name,data.get("message_id").getAsString());
        }
        var shield = json("/data/minecraft/tags/damage_type/bypasses_shield.json");
        assertFalse(shield.get("replace").getAsBoolean());
        assertEquals(Set.of("virtual_craft:mushroom_strike","virtual_craft:mushroom_ultimate"), values(shield));
    }

    @Test
    void onlyUltimateBypassesHurtCooldownNotArmourResistanceEnchantmentsOrTotems() throws Exception {
        var cooldown = json("/data/minecraft/tags/damage_type/bypasses_cooldown.json");
        assertFalse(cooldown.get("replace").getAsBoolean());
        assertEquals(Set.of("virtual_craft:mushroom_ultimate"), values(cooldown));
        for (String name : new String[]{"bypasses_armor","bypasses_resistance","bypasses_effects","bypasses_enchantments","bypasses_invulnerability"}) {
            try (InputStream stream = getClass().getResourceAsStream("/data/minecraft/tags/damage_type/"+name+".json")) {
                if (stream != null) {
                    var tag = JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                    assertTrue(values(tag).stream().noneMatch(value -> value.startsWith("virtual_craft:mushroom_")));
                }
            }
        }
    }

    @Test
    void bothNewDeathMessagesHaveTranslations() throws Exception {
        for (String lang : new String[]{"zh_cn","en_us"}) {
            var translations = json("/assets/virtual_craft/lang/"+lang+".json");
            for (String type : new String[]{"mushroom_strike","mushroom_ultimate"}) {
                assertTrue(translations.has("death.attack.virtual_craft."+type));
                assertTrue(translations.has("death.attack.virtual_craft."+type+".player"));
            }
        }
    }

    private Set<String> values(JsonObject object) {
        return object.getAsJsonArray("values").asList().stream().map(value -> value.getAsString()).collect(Collectors.toSet());
    }
    private JsonObject json(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream,path);
            return JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
