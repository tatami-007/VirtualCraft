package dev.tatami.virtualcraft.client.model;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.client.MushroomBossFeedback;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.EasingType;
import software.bernie.geckolib.animation.keyframe.AnimationPoint;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.model.GeoModel;

/** Stateless sampling of GeckoLib's baked clips: late tracking seeks directly to the server clock. */
public final class VirtualMushroomKingGeoModel extends GeoModel<VirtualMushroomKing> {
    private static final ResourceLocation MODEL = id("geo/virtual_mushroom_king.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/entity/mushroom_king/virtual_mushroom_king.png");
    private static final ResourceLocation ANIMATION = id("animations/virtual_mushroom_king.animation.json");
    private final Map<VirtualMushroomKing, Transition> transitions = new WeakHashMap<>();

    private static final class Transition {
        int sequence;
        double start, duration;
        Map<String,float[]> from = Map.of();
        Map<String,float[]> last = new HashMap<>();
        Transition(int sequence) { this.sequence = sequence; }
    }

    @Override
    public ResourceLocation getModelResource(VirtualMushroomKing entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(VirtualMushroomKing entity) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(VirtualMushroomKing entity) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(VirtualMushroomKing entity, long instanceId, AnimationState<VirtualMushroomKing> state) {
        // Baked bones are shared between entities. Reset every channel before sampling this instance.
        for (var bone : getAnimationProcessor().getRegisteredBones()) {
            var initial = bone.getInitialSnapshot();
            bone.updateRotation(initial.getRotX(), initial.getRotY(), initial.getRotZ());
            bone.updatePosition(0, 0, 0);
            bone.setScaleX(1); bone.setScaleY(1); bone.setScaleZ(1);
        }
        float partial = state.getPartialTick();
        int action = entity.animationAction();
        if (action >= 0 && action <= 8) {
            apply(entity, action < 7 ? "combo_" + action : "ultimate_" + (action - 7), entity.animationAge(partial), 1);
        } else if (action == 10 || !entity.isAlive()) {
            apply(entity, "death", action == 10 ? entity.animationAge(partial) : entity.deathTime + partial, 1);
        } else {
            apply(entity, "idle", (entity.level().getGameTime() + partial) % 80, 1);
            double movement = entity.walkAnimationWeight(partial);
            apply(entity, "walk", entity.walkAnimationTick(partial) % 40, movement);
            double turning = entity.turnAnimationWeight(partial);
            if (turning > 0.01) apply(entity, entity.animationTurn() < 0 ? "turn_left" : "turn_right",
                    entity.turnAnimationTick(partial) % 20, turning);
        }
        blendTransition(entity, partial);
        applyFace(entity, partial);
        // Feedback is deliberately outside the base-pose transition cache. It must
        // neither carry into the next skill nor replace its authoritative clock.
        if (entity.isAlive() && action != 10) {
            double now=entity.level().getGameTime()+partial;
            applyReaction(entity,MushroomBossFeedback.hurt(entity,now),now,partial);
            applyReaction(entity,MushroomBossFeedback.guard(entity,now),now,partial);
        }
    }

    private void applyFace(VirtualMushroomKing entity, float partial) {
        var clip = getAnimation(entity, "animation.mushroom_king.idle");
        if (clip == null) return;
        // A continuous facial clock, offset per entity, avoids synchronized blinking
        // and prevents each attack transition from restarting an eye movement.
        double tick = (entity.level().getGameTime() + partial + entity.getId() * 17L) % 80;
        boolean dying = entity.animationAction() == 10 || !entity.isAlive();
        for (var channel : clip.boneAnimations()) {
            String name = channel.boneName();
            boolean lid = name.equals("left_upper_eyelid") || name.equals("right_upper_eyelid");
            if (!lid && !name.equals("left_eye") && !name.equals("right_eye")) continue;
            var bone = getAnimationProcessor().getBone(name);
            if (bone == null) continue;
            if (dying) {
                bone.updatePosition(0, lid ? -4.7F * (float)Math.clamp(entity.animationAge(partial) / 12, 0, 1) : 0, 0);
            } else {
                var p = channel.positionKeyFrames();
                bone.updatePosition(sample(p.xKeyframes(), tick), sample(p.yKeyframes(), tick), sample(p.zKeyframes(), tick));
            }
        }
    }

    private void collectReaction(VirtualMushroomKing entity,String name,double tick,float weight,
            int side,boolean hurt,float partial,Map<String,float[]> offsets) {
        var clip=getAnimation(entity,"animation.mushroom_king."+name);
        if(clip==null) return;
        boolean attacking=entity.animationAction()>=0 && entity.animationAction()<=8;
        for (var channel : clip.boneAnimations()) {
            String boneName=channel.boneName();
            float scale=weight;
            if(hurt && attacking) {
                scale*=boneName.equals("head")||boneName.equals("jaw")?.85F:boneName.endsWith("upper_arm")?.25F:boneName.equals("torso")?.30F:.65F;
                if(contactArm(entity,boneName,partial)) scale=0;
            }
            var r=channel.rotationKeyFrames();
            float sign=hurt?side:1;
            offsets.put(boneName,new float[]{sample(r.xKeyframes(),tick)*scale,
                    sample(r.yKeyframes(),tick)*scale*sign,sample(r.zKeyframes(),tick)*scale*sign});
        }
    }

    private boolean contactArm(VirtualMushroomKing entity,String bone,float partial) {
        int action=entity.animationAction();
        if(action<0 || action>=7 || !bone.endsWith("upper_arm")) return false;
        var hits=MushroomCombatRules.COMBOS.get(action).hits();
        double age=entity.animationAge(partial);
        for(int i=0;i<hits.size();i++) {
            if(Math.abs(age-hits.get(i).tick())>4) continue;
            boolean both=i==hits.size()-1 && (action==1||action==3||action==5);
            if(both || bone.startsWith(i%2==0?"left":"right")) return true;
        }
        return false;
    }

    private void applyReaction(VirtualMushroomKing entity,MushroomBossFeedback.Reaction reaction,double now,float partial) {
        if(reaction==null) return;
        double age=Math.max(0,now-reaction.start);
        var offsets=new HashMap<String,float[]>();
        if(reaction.kind<=1) collectReaction(entity,reaction.kind==0?"hurt":"hurt_heavy",age,reaction.strength,reaction.side,true,partial,offsets);
        else {
            double tick=age*(reaction.kind==3?2:1);
            if(reaction.side!=1) collectReaction(entity,"parry_left",tick,reaction.strength,1,false,partial,offsets);
            if(reaction.side!=0) collectReaction(entity,"parry_right",tick,reaction.strength,1,false,partial,offsets);
        }
        double mix=Math.clamp(age/2,0,1); mix=mix*mix*(3-2*mix);
        // Blend offsets instead of adding successive hits' poses. Include bones
        // that cease participating so changing skill/contact masks cannot pop.
        reaction.from.forEach((name,pose)->offsets.putIfAbsent(name,new float[3]));
        for(var entry:offsets.entrySet()) {
            var bone=getAnimationProcessor().getBone(entry.getKey());
            if(bone==null) continue;
            float[] values=entry.getValue(),before=reaction.from.get(entry.getKey());
            if(before!=null && mix<1) for(int i=0;i<3;i++) values[i]=(float)(before[i]+(values[i]-before[i])*mix);
            if(reaction.kind<=1 && contactArm(entity,entry.getKey(),partial)) java.util.Arrays.fill(values,0);
            reaction.last.put(entry.getKey(),values);
            bone.updateRotation(bone.getRotX()+values[0],bone.getRotY()+values[1],bone.getRotZ()+values[2]);
            if(entry.getKey().equals("jaw")) bone.setRotX(Math.clamp(bone.getRotX(),(float)Math.toRadians(-28),(float)Math.toRadians(4)));
        }
    }

    private void blendTransition(VirtualMushroomKing entity, float partial) {
        double now = entity.level().getGameTime() + partial;
        var transition = transitions.computeIfAbsent(entity, e -> new Transition(e.animationSequence()));
        if (transition.sequence != entity.animationSequence()) {
            transition.sequence = entity.animationSequence();
            transition.from = new HashMap<>();
            transition.last.forEach((name, pose) -> transition.from.put(name, pose.clone()));
            transition.start = now - entity.animationAge(partial);
            transition.duration = entity.animationAction() == -1 ? 6 : entity.animationAction() == 9 ? 2 : 4;
        }
        double blend = transition.duration == 0 ? 1 : Math.clamp((now - transition.start) / transition.duration,0,1);
        blend = blend * blend * (3 - 2 * blend);
        for (var bone : getAnimationProcessor().getRegisteredBones()) {
            float[] pose = transition.last.computeIfAbsent(bone.getName(), key -> new float[6]);
            pose[0]=bone.getRotX(); pose[1]=bone.getRotY(); pose[2]=bone.getRotZ();
            pose[3]=bone.getPosX(); pose[4]=bone.getPosY(); pose[5]=bone.getPosZ();
            float[] previous = transition.from.get(bone.getName());
            if (blend < 1 && previous != null) {
                for (int i=0;i<6;i++) {
                    double delta = pose[i] - previous[i];
                    if (i<3) delta = Math.atan2(Math.sin(delta),Math.cos(delta));
                    pose[i] = (float)(previous[i] + delta*blend);
                }
                bone.updateRotation(pose[0],pose[1],pose[2]);
                bone.updatePosition(pose[3],pose[4],pose[5]);
            }
        }
        if (blend >= 1) transition.from = Map.of();
    }

    private void apply(VirtualMushroomKing entity, String name, double tick, double weight) {
        if (weight == 0) return;
        var clip = getAnimation(entity, "animation.mushroom_king." + name);
        if (clip == null) return;
        tick = Math.clamp(tick, 0, clip.length());
        for (var channel : clip.boneAnimations()) {
            var bone = getAnimationProcessor().getBone(channel.boneName());
            if (bone == null) continue;
            var r = channel.rotationKeyFrames();
            bone.updateRotation(bone.getRotX() + sample(r.xKeyframes(), tick) * (float)weight,
                    bone.getRotY() + sample(r.yKeyframes(), tick) * (float)weight,
                    bone.getRotZ() + sample(r.zKeyframes(), tick) * (float)weight);
            var p = channel.positionKeyFrames();
            bone.updatePosition(bone.getPosX() + sample(p.xKeyframes(), tick) * (float)weight,
                    bone.getPosY() + sample(p.yKeyframes(), tick) * (float)weight,
                    bone.getPosZ() + sample(p.zKeyframes(), tick) * (float)weight);
        }
    }

    private static float sample(List<Keyframe<MathValue>> frames, double tick) {
        double start = 0;
        for (var frame : frames) {
            if (tick <= start + frame.length()) {
                if (frame.length() == 0) return (float)frame.endValue().get();
                return (float)EasingType.lerpWithOverride(new AnimationPoint(frame, Math.max(0, tick - start),
                        frame.length(), frame.startValue().get(), frame.endValue().get()), null);
            }
            start += frame.length();
        }
        return frames.isEmpty() ? 0 : (float)frames.getLast().endValue().get();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path);
    }
}
