package dev.tatami.virtualcraft.gameplay;

import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.MushroomAfflictionState;
import dev.tatami.virtualcraft.boss.MushroomAfflictionType;
import dev.tatami.virtualcraft.boss.MushroomCombatRules;
import dev.tatami.virtualcraft.network.MushroomPlayerControlPayload;
import dev.tatami.virtualcraft.registry.ModMobEffects;
import java.util.HashMap;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server owns ownership/expiry; vanilla effects supply cure behaviour, without status UI. */
public final class MushroomAfflictionHandler {
    private static final ResourceLocation SPEED = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,"mushroom_control_speed");
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static long sequence;
    private static final class Session {
        final MushroomAfflictionState state=new MushroomAfflictionState();
        MushroomAfflictionState.Control sent;
        final Map<MushroomAfflictionType,Long> projectedUntil=new EnumMap<>(MushroomAfflictionType.class);
        boolean projecting;
    }
    private MushroomAfflictionHandler() {}

    public static void apply(ServerPlayer player, UUID source, List<MushroomCombatRules.Affliction> effects, int rotation) {
        if (effects.isEmpty() || !valid(player)) return;
        var session=SESSIONS.computeIfAbsent(player.getUUID(),id->new Session());
        long now=player.serverLevel().getGameTime();
        for(var effect:effects) session.state.apply(source,effect.type(),effect.ticks(),effect.level(),rotation,now);
        project(player,session,now);
    }

    public static void clearSource(MinecraftServer server, UUID source) {
        if (server==null) return;
        for(var entry:SESSIONS.entrySet()) {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if (player==null) continue;
            long now=player.serverLevel().getGameTime();
            entry.getValue().state.removeSource(source,now);
            project(player,entry.getValue(),now);
        }
    }

    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var session=SESSIONS.get(player.getUUID());
        if (!valid(player)) { clear(player,true); return; }
        if(session==null) return;
        long now=player.serverLevel().getGameTime();
        session.state.prune(now);
        // A canceled addition or an external cure must not be immediately reapplied.
        for(var type:MushroomAfflictionType.values()) {
            if(session.state.active(type,now)!=null && !player.hasEffect(ModMobEffects.mushroom(type))
                    && session.projectedUntil.getOrDefault(type,0L)>now) session.state.clear(type,now);
        }
        project(player,session,now);
        if(session.state.empty(now)) SESSIONS.remove(player.getUUID());
    }

    public static void onRemoved(MobEffectEvent.Remove event) {
        if(event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        var session=SESSIONS.get(player.getUUID());
        if(session==null || session.projecting) return;
        for(var type:MushroomAfflictionType.values()) if(event.getEffect().equals(ModMobEffects.mushroom(type))) {
            long now=player.serverLevel().getGameTime();
            session.state.clear(type,now);
            removeLegacySpeedModifier(player);
            sync(player,session,now);
            break;
        }
    }

    private static void project(ServerPlayer player, Session session, long now) {
        session.state.prune(now);
        session.projecting=true;
        try {
            for(var type:MushroomAfflictionType.values()) {
                var holder=ModMobEffects.mushroom(type);
                var active=session.state.active(type,now);
                var existing=player.getEffect(holder);
                if(active==null) {
                    if(existing!=null) player.removeEffect(holder);
                    session.projectedUntil.remove(type);
                } else {
                    int duration=(int)Math.min(Integer.MAX_VALUE,active.until()-now);
                    if(existing==null || existing.getAmplifier()!=active.level() || Math.abs(existing.getDuration()-duration)>1) {
                        if(existing!=null) player.removeEffect(holder);
                        if(!player.addEffect(new MobEffectInstance(holder,duration,active.level(),false,false,false))) {
                            session.state.clear(type,now);
                        }
                    }
                    if(player.hasEffect(holder)) session.projectedUntil.put(type,active.until());
                }
            }
        } finally { session.projecting=false; }
        removeLegacySpeedModifier(player);
        sync(player,session,now);
    }

    private static void removeLegacySpeedModifier(ServerPlayer player) {
        // Input restriction preserves normal FOV, jump power and external velocity.
        // Remove the old modifier when upgrading an existing player/session.
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);
    }

    private static void sync(ServerPlayer player,Session session,long now) {
        var control=session.state.control(now);
        if(control.equals(session.sent)) return;
        session.sent=control;
        PacketDistributor.sendToPlayer(player,new MushroomPlayerControlPayload(player.level().dimension().location(),++sequence,
                control.snareUntil(),control.rotationUntil(),control.degrees(),control.dragUntil(),control.movementScale()));
    }

    private static boolean valid(ServerPlayer p) { return p.isAlive() && !p.isCreative() && !p.isSpectator(); }

    private static void clear(ServerPlayer player,boolean send) {
        var session=SESSIONS.remove(player.getUUID());
        if(session!=null) session.projecting=true;
        for(var type:MushroomAfflictionType.values()) player.removeEffect(ModMobEffects.mushroom(type));
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);
        if(send && session!=null) PacketDistributor.sendToPlayer(player,new MushroomPlayerControlPayload(player.level().dimension().location(),++sequence,0,0,0,0,1));
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof ServerPlayer p) clear(p,true); }
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p) clear(p,false); }
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) clear(p,true); }
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) clear(p,true); }
    public static void onDeath(LivingDeathEvent e) { if(!e.isCanceled() && e.getEntity() instanceof ServerPlayer p) clear(p,true); }
    public static void onServerStop(ServerStoppedEvent e) { SESSIONS.clear(); sequence=0; }
}
