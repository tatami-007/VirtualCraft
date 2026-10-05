package dev.tatami.virtualcraft.client.model;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MushroomAnimationAssetsTest {
    private static final String ROOT = "/assets/virtual_craft/";
    private static final String PREFIX = "animation.mushroom_king.";

    private JsonObject read(String name) throws Exception {
        try(var stream=getClass().getResourceAsStream(ROOT+name)) {
            assertNotNull(stream,name);
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private JsonObject clips() throws Exception { return read("animations/virtual_mushroom_king.animation.json").getAsJsonObject("animations"); }
    private String time(int tick) { return java.math.BigDecimal.valueOf(tick).divide(java.math.BigDecimal.valueOf(20)).stripTrailingZeros().toPlainString(); }

    @Test void allEighteenClipsUseOnlyExistingBonesAndNoScaleOrEffectChannels() throws Exception {
        Set<String> bones=new HashSet<>();
        read("geo/virtual_mushroom_king.geo.json").getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject()
                .getAsJsonArray("bones").forEach(b->bones.add(b.getAsJsonObject().get("name").getAsString()));
        var animations=clips();
        assertEquals(18,animations.size());
        for(var entry:animations.entrySet()) {
            assertTrue(entry.getKey().startsWith(PREFIX));
            var clip=entry.getValue().getAsJsonObject();
            assertEquals(Set.of("loop","animation_length","bones"),clip.keySet());
            assertFalse(clip.getAsJsonObject("bones").isEmpty());
            for(var bone:clip.getAsJsonObject("bones").entrySet()) {
                assertTrue(bones.contains(bone.getKey()),bone.getKey());
                for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
                    assertTrue(Set.of("rotation","position").contains(channel.getKey()));
                    var frames=channel.getValue().getAsJsonObject();
                    assertTrue(frames.has("0"));
                    for(var frame:frames.entrySet()) {
                        assertTrue(Double.parseDouble(frame.getKey())<=clip.get("animation_length").getAsDouble()+1e-8);
                        assertEquals(3,frame.getValue().getAsJsonArray().size());
                        frame.getValue().getAsJsonArray().forEach(value->assertTrue(Double.isFinite(value.getAsDouble())));
                    }
                }
            }
        }
    }

    @Test void releaseContactAndPauseFramesMatchAuthoritativeGroupedCombatTimes() throws Exception {
        var animations=clips();
        Set<String> differentClips=new HashSet<>();
        for(var combo:MushroomCombatRules.COMBOS) {
            var clip=animations.getAsJsonObject(combo.animationId());
            assertNotNull(clip);
            assertFalse(clip.get("loop").getAsBoolean());
            assertEquals(combo.duration(),clip.get("animation_length").getAsDouble()*20,1e-7);
            differentClips.add(clip.getAsJsonObject("bones").toString());
            for(var bone:clip.getAsJsonObject("bones").entrySet()) {
                boolean secondary=Set.of("crown_cap","back_growth","jaw").contains(bone.getKey());
                for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
                    var frames=channel.getValue().getAsJsonObject();
                    for(var hit:combo.hits()) {
                        assertTrue(frames.has(time(hit.tick()-4)));
                        assertTrue(frames.has(time(hit.tick())));
                        if(!secondary) assertEquals(frames.get(time(hit.tick())),frames.get(time(hit.tick()+2)));
                    }
                    for(int i=1;i<combo.groups().size();i++) {
                        int prior=combo.groups().get(i-1).getLast().tick();
                        if(!secondary) assertEquals(frames.get(time(prior+8)),frames.get(time(prior+22)),"Explicit long pause");
                    }
                    frames.getAsJsonArray(time(combo.duration())).forEach(v->assertEquals(0,v.getAsDouble(),1e-8));
                }
            }
        }
        assertEquals(7,differentClips.size());
    }

    @Test void locomotionLoopsCloseAndParryIsAnArmOnlyAdditiveClip() throws Exception {
        var animations=clips();
        for(String name:new String[]{"idle","walk","turn_left","turn_right"}) {
            var clip=animations.getAsJsonObject(PREFIX+name);
            assertTrue(clip.get("loop").getAsBoolean());
            for(var bone:clip.getAsJsonObject("bones").entrySet()) for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
                var frames=channel.getValue().getAsJsonObject();
                var last=frames.get(time((int)Math.round(clip.get("animation_length").getAsDouble()*20))).getAsJsonArray();
                var first=frames.getAsJsonArray("0");
                for(int i=0;i<3;i++) assertEquals(first.get(i).getAsDouble(),last.get(i).getAsDouble(),1e-6,name);
            }
        }
        for(String side:new String[]{"left","right"}) {
            var clip=animations.getAsJsonObject(PREFIX+"parry_"+side);
            assertEquals(.4,clip.get("animation_length").getAsDouble());
            // Recoil the full arm without folding a thick wrist/cuff into its neighbour.
            assertEquals(Set.of(side+"_upper_arm"),clip.getAsJsonObject("bones").keySet());
            var recoil=clip.getAsJsonObject("bones").getAsJsonObject(side+"_upper_arm").getAsJsonObject("rotation");
            assertTrue(Math.abs(recoil.getAsJsonArray(time(1)).get(0).getAsDouble())>=8);
            recoil.getAsJsonArray(time(8)).forEach(v->assertEquals(0,v.getAsDouble(),1e-8));
        }
        assertEquals(3,animations.getAsJsonObject(PREFIX+"death").get("animation_length").getAsDouble());
        for(int i=0;i<2;i++) assertEquals(6,animations.getAsJsonObject(PREFIX+"ultimate_"+i).get("animation_length").getAsDouble());
    }

    @Test void hurtAndHeavyHurtHoldAVisiblePeakAndRecoverWithoutTouchingTheSkillClock() throws Exception {
        var animations=clips();
        for(String name:new String[]{"hurt","hurt_heavy"}) {
            int duration=name.equals("hurt")?12:16;
            var clip=animations.getAsJsonObject(PREFIX+name);
            assertEquals(duration,clip.get("animation_length").getAsDouble()*20,1e-8);
            var bones=clip.getAsJsonObject("bones");
            assertTrue(bones.keySet().containsAll(Set.of("torso","head","jaw","crown_cap","back_growth","left_upper_arm","right_upper_arm")));
            var head=bones.getAsJsonObject("head").getAsJsonObject("rotation");
            assertEquals(head.get(time(1)),head.get(time(3)),"Peak held for two ticks");
            assertTrue(Math.abs(head.getAsJsonArray(time(1)).get(0).getAsDouble())>=10);
            for(var bone:bones.entrySet()) for(var channel:bone.getValue().getAsJsonObject().entrySet()) {
                channel.getValue().getAsJsonObject().getAsJsonArray(time(duration))
                        .forEach(v->assertEquals(0,v.getAsDouble(),1e-8));
            }
        }
    }

    @Test void eyesBlinkWithoutScalingAndWalkingDoesNotTranslateHipJoints() throws Exception {
        var animations=clips();
        var idle=animations.getAsJsonObject(PREFIX+"idle").getAsJsonObject("bones");
        var walk=animations.getAsJsonObject(PREFIX+"walk").getAsJsonObject("bones");
        for(String side:new String[]{"left","right"}) {
            var eye=idle.getAsJsonObject(side+"_eye").getAsJsonObject("position");
            assertNotEquals(eye.get(time(0)),eye.get(time(20)));
            var lid=idle.getAsJsonObject(side+"_upper_eyelid");
            assertFalse(lid.has("scale"));
            assertEquals(-4.7,lid.getAsJsonObject("position").getAsJsonArray(time(53)).get(1).getAsDouble(),1e-6);
            assertEquals(0,lid.getAsJsonObject("position").getAsJsonArray(time(57)).get(1).getAsDouble(),1e-6);
            assertFalse(walk.getAsJsonObject(side+"_thigh").has("position"));
            assertTrue(walk.getAsJsonObject(side+"_shin").has("rotation"));
            assertTrue(walk.getAsJsonObject(side+"_foot").has("rotation"));
        }
    }
}
