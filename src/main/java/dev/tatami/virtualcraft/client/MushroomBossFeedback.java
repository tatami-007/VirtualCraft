package dev.tatami.virtualcraft.client;

import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import dev.tatami.virtualcraft.network.MushroomBossReactionPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;

/** Receipt-time animation clocks. Keeps reaction offsets separate from the skill transition cache. */
public final class MushroomBossFeedback {
    public static final class Reaction {
        public final int kind,side;
        public final float strength;
        public final double start;
        public final Map<String,float[]> from=new HashMap<>(),last=new HashMap<>();
        private Reaction(MushroomBossReactionPayload p,double start,Reaction previous) {
            kind=p.kind(); side=p.side(); strength=Math.clamp(p.strength(),0,1); this.start=start;
            if(previous!=null) previous.last.forEach((bone,offset)->from.put(bone,offset.clone()));
        }
        public int duration() { return switch(kind) { case 0 -> 12; case 1 -> 16; case 2 -> 8; default -> 4; }; }
    }
    private static final class State { int sequence; Reaction hurt,guard; }
    private static final Map<VirtualMushroomKing,State> STATES=new WeakHashMap<>();
    private MushroomBossFeedback() {}

    public static void accept(MushroomBossReactionPayload payload) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || payload.kind()<0 || payload.kind()>3) return;
        if(!(mc.level.getEntity(payload.entityId()) instanceof VirtualMushroomKing boss)
                || !boss.getUUID().equals(payload.entityUuid()) || !boss.isAlive()) return;
        var state=STATES.computeIfAbsent(boss,e->new State());
        if(payload.sequence()<=state.sequence) return;
        state.sequence=payload.sequence();
        double now=mc.level.getGameTime();
        if(payload.kind()<=1) state.hurt=new Reaction(payload,now,live(state.hurt,now));
        else state.guard=new Reaction(payload,now,live(state.guard,now));
    }
    private static Reaction live(Reaction reaction,double now) { return reaction!=null && now-reaction.start<reaction.duration()?reaction:null; }
    public static Reaction hurt(VirtualMushroomKing boss,double now) {
        var state=STATES.get(boss); return state==null?null:live(state.hurt,now);
    }
    public static Reaction guard(VirtualMushroomKing boss,double now) {
        var state=STATES.get(boss); return state==null?null:live(state.guard,now);
    }
    public static void clear() { STATES.clear(); }
}
