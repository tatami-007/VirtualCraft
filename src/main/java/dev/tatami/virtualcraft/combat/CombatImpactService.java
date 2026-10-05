package dev.tatami.virtualcraft.combat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;

/** Suppresses just the scoped vanilla damage impulse, then posts one configured knockback. */
public final class CombatImpactService {
    private static final Set<UUID> DAMAGE_SCOPES = new HashSet<>();
    private CombatImpactService() {}

    public static boolean hurt(ServerPlayer player, DamageSource source, float damage) {
        boolean added = DAMAGE_SCOPES.add(player.getUUID());
        try { return player.hurt(source,damage); }
        finally { if (added) DAMAGE_SCOPES.remove(player.getUUID()); }
    }

    public static void suppressVanilla(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DAMAGE_SCOPES.contains(player.getUUID())) event.setCanceled(true);
    }

    public static void apply(ServerPlayer player, Vec3 bossPosition, float yaw, AttackImpulse impulse, float multiplier) {
        if (!player.isAlive() || multiplier<=0 || impulse.horizontal()<=0 && impulse.vertical()<=0) return;
        double angle = Math.toRadians(yaw);
        Vec3 direction=player.position().subtract(bossPosition).multiply(1,0,1);
        if (direction.lengthSqr()<1e-8) direction=new Vec3(-Math.sin(angle),0,Math.cos(angle));
        direction=direction.normalize();
        double side=player.position().subtract(bossPosition).dot(new Vec3(Math.cos(angle),0,Math.sin(angle)))<0?-1:1;
        double rotate=switch(impulse.direction()) {
            case LEFT_SWEEP -> 35;
            case RIGHT_SWEEP -> -35;
            case SIDE -> side*90;
            case PULL -> 180;
            default -> 0;
        };
        direction=direction.yRot((float)Math.toRadians(rotate));
        var event=new LivingKnockBackEvent(player,impulse.horizontal()*multiplier,-direction.x,-direction.z);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) return;
        double eventScale=impulse.horizontal()>0?Math.max(0,event.getStrength())/(impulse.horizontal()*multiplier):1;
        var reduced=impulse.reduced(multiplier*eventScale,player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        Vec3 push=new Vec3(-event.getRatioX(),0,-event.getRatioZ()).normalize().scale(reduced.horizontal());
        double vertical=reduced.vertical();
        if (push.lengthSqr()==0 && vertical==0) return;
        Vec3 old=player.getDeltaMovement();
        player.setDeltaMovement(old.x+push.x,vertical==0?old.y:Math.max(0,old.y)+vertical,old.z+push.z);
        player.hurtMarked=true;
        CombatDefenseService.invalidateJump(player);
    }
}
