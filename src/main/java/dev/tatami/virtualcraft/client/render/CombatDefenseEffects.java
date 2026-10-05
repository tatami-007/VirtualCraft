package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.network.CombatDefenseSuccessPayload;
import dev.tatami.virtualcraft.registry.ModSounds;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Shared defense feedback: bounded depth-tested world geometry, no gameplay state changes. */
public final class CombatDefenseEffects {
    private static ShaderInstance ghostShader, sparkShader, dodgeShader;
    private static PlayerModel<AbstractClientPlayer> wideModel, slimModel;
    private static ClientLevel world;
    private static long clock;
    private static final Map<UUID, Long> SEEN = new HashMap<>();
    private static final List<Trail> TRAILS = new ArrayList<>();
    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final List<GoldMote> GOLD = new ArrayList<>();
    private static final Map<UUID, Long> CAPTURED = new HashMap<>();
    private static final Map<UUID, Boolean> VALID_PLAYERS = new HashMap<>();
    private record Trail(UUID player, long start, long seed) {}
    private record Ghost(UUID player, Vec3 origin, ResourceLocation skin, List<float[]> vertices, long start) {}
    private record Spark(UUID player, Vec3 origin, Vec3 velocity, long start, int life, float width) {}
    private record GoldMote(UUID player, Vec3 origin, Vec3 velocity, long start, int life,
            float size, float phase, int seed) {}
    private CombatDefenseEffects() {}

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("defense_ghost"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> ghostShader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("defense_spark"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> sparkShader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("dodge_gold"),
                DefaultVertexFormat.POSITION_TEX_COLOR), shader -> dodgeShader = shader);
        wideModel = slimModel = null;
        clear();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID, path); }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new PoseCaptureLayer(renderer));
        }
    }

    public static void clear() {
        TRAILS.clear(); GHOSTS.clear(); SPARKS.clear(); GOLD.clear(); SEEN.clear(); CAPTURED.clear(); VALID_PLAYERS.clear(); world = null; clock = 0;
    }

    private static void checkWorld() {
        var level = Minecraft.getInstance().level;
        if (world != level) { clear(); world = level; }
    }

    public static void accept(CombatDefenseSuccessPayload packet) {
        checkWorld();
        if (world == null || !world.dimension().location().equals(packet.dimension())
                || packet.kind() < 0 || packet.kind() > 2 || SEEN.containsKey(packet.eventId())) return;
        if (!(world.getEntity(packet.playerId()) instanceof AbstractClientPlayer player)
                || !player.getUUID().equals(packet.playerUuid()) || !player.isAlive() || player.isSpectator()) return;
        SEEN.put(packet.eventId(), clock);
        boolean guard = packet.kind() == CombatDefenseSuccessPayload.PRECISE_GUARD;
        world.playLocalSound(player.getX(), player.getY(), player.getZ(),
                guard ? SoundEvents.ANVIL_LAND : ModSounds.MUSHROOM_DODGE_SUCCESS.get(),
                SoundSource.PLAYERS, guard ? 0.48F : 0.8F, guard ? 1.7F : 1F, false);
        if (player.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())>4096) return;
        Vec3 forward = player.getViewVector(1).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
        // Contact on the defending hand, not the feet; use the confirmed hand even if it has since broken.
        Vec3 contact = player.getEyePosition().add(forward.scale(0.85))
                .add(right.scale(packet.leftHand() ? -0.23 : 0.23)).add(0, -0.25, 0);
        var random = new Random(packet.eventId().getLeastSignificantBits());
        if (guard) {
            for (int i = 0; i < 22; i++) {
                Vec3 velocity = right.scale((random.nextDouble() - 0.5) * 0.38)
                        .add(forward.scale(0.035 + random.nextDouble() * 0.08)).add(0, 0.06 + random.nextDouble() * 0.16, 0);
                SPARKS.add(new Spark(player.getUUID(), contact, velocity, clock, 8 + random.nextInt(5), 0.012F));
            }
        } else {
            TRAILS.removeIf(t -> t.player.equals(player.getUUID()));
            TRAILS.add(new Trail(player.getUUID(), clock, packet.eventId().getLeastSignificantBits()));
            snapshot(player);
            emitGold(player, random, 144, true);
        }
        // Crowd/boss-spam caps are independent of graphics particle settings.
        while (SPARKS.size() > 384) SPARKS.removeFirst();
        while (TRAILS.size() > 24) TRAILS.removeFirst();
    }

    /** Emission is attached to frozen body surfaces; gold then drifts independently in world space. */
    private static void emitGold(AbstractClientPlayer player, Random random, int count, boolean onset) {
        Ghost source = null;
        for (int i = GHOSTS.size() - 1; i >= 0; i--) {
            if (GHOSTS.get(i).player.equals(player.getUUID())) { source = GHOSTS.get(i); break; }
        }
        Vec3 motion = player.position().subtract(new Vec3(player.xo, player.yo, player.zo));
        if (motion.lengthSqr() > 0.36) motion = motion.normalize().scale(0.6);
        Vec3 forward = player.getViewVector(1);
        Vec3 right = new Vec3(-forward.z, 0, forward.x).normalize();
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 start = player.position().add(0, player.getBbHeight() * random.nextDouble(), 0);
            if (source != null && source.vertices.size() >= 4) {
                int face = random.nextInt(source.vertices.size() / 4) * 4;
                float[] a = source.vertices.get(face), b = source.vertices.get(face + 1), d = source.vertices.get(face + 3);
                double u = random.nextDouble(), v = random.nextDouble();
                start = source.origin.add(a[0] + (b[0] - a[0]) * u + (d[0] - a[0]) * v,
                        a[1] + (b[1] - a[1]) * u + (d[1] - a[1]) * v,
                        a[2] + (b[2] - a[2]) * u + (d[2] - a[2]) * v);
            }
            // Push out from the sampled body instead of burying small lights inside the opaque player.
            Vec3 outward = start.subtract(player.position()).multiply(1, 0, 1);
            outward = outward.lengthSqr() > 1e-5 ? outward.normalize() : radial;
            start = start.add(outward.scale(onset && i % 5 == 0 ? 0.45 + random.nextDouble() * 0.6 : 0.18 + random.nextDouble() * 0.27));
            if (onset && i < 14 && player == Minecraft.getInstance().player) {
                start = player.getEyePosition().add(forward.scale(1.1 + random.nextDouble() * 0.35))
                        .add(right.scale((random.nextDouble() - 0.5) * 0.9)).add(0, -0.15 - random.nextDouble() * 0.4, 0);
            }
            Vec3 velocity = radial.scale(0.025 + random.nextDouble() * 0.045)
                    .add(motion.scale(-0.22)).add(0, 0.012 + random.nextDouble() * 0.025, 0);
            // Mostly readable foil flecks, a few larger pieces and fine dust between them.
            float tier = random.nextFloat();
            float size = tier < 0.20F ? 0.018F + random.nextFloat() * 0.009F
                    : tier < 0.88F ? 0.034F + random.nextFloat() * 0.020F
                    : 0.055F + random.nextFloat() * 0.018F;
            GOLD.add(new GoldMote(player.getUUID(), start, velocity, clock, 18 + random.nextInt(21),
                    size * 0.68F, random.nextFloat() * 6.283185F, random.nextInt(256)));
        }
        while (GOLD.size() > 1536) GOLD.removeFirst();
    }

    public static void tick() {
        checkWorld();
        if (world == null || Minecraft.getInstance().isPaused()) return;
        clock++;
        VALID_PLAYERS.clear();
        SEEN.entrySet().removeIf(e -> clock - e.getValue() > 100);
        CAPTURED.entrySet().removeIf(e -> clock - e.getValue() > 20);
        TRAILS.removeIf(t -> clock - t.start > 6 || !valid(t.player));
        for (Trail trail : TRAILS) {
            if ((clock - trail.start) % 2 == 0 && world.getPlayerByUUID(trail.player) instanceof AbstractClientPlayer player) {
                snapshot(player);
                emitGold(player, new Random(trail.seed + (clock - trail.start) * 7919), 24, false);
            }
        }
        GHOSTS.removeIf(g -> clock - g.start >= 10 || !valid(g.player));
        SPARKS.removeIf(s -> clock - s.start >= s.life || !valid(s.player));
        GOLD.removeIf(s -> clock - s.start >= s.life || !valid(s.player));
    }

    private static boolean valid(UUID id) {
        return VALID_PLAYERS.computeIfAbsent(id, key -> {
            var player=world.getPlayerByUUID(key);
            return player!=null && player.isAlive() && !player.isSpectator() && !player.isCreative();
        });
    }

    /** Bake an immutable skinned pose, never retain/mutate the live renderer's shared model. */
    private static void snapshot(AbstractClientPlayer player) {
        if (player.isInvisible()) return;
        var mc = Minecraft.getInstance();
        if (wideModel == null) {
            wideModel = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
            slimModel = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }
        var model = player.getSkin().model() == PlayerSkin.Model.SLIM ? slimModel : wideModel;
        model.setAllVisible(true);
        model.crouching = player.isCrouching();
        model.riding = player.isPassenger();
        model.young = false;
        model.attackTime = player.getAttackAnim(1);
        model.leftArmPose = player.getItemInHand(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT
                ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND).isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        model.rightArmPose = player.getItemInHand(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND).isEmpty()
                ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        float speed = player.walkAnimation.speed();
        float position = player.walkAnimation.position();
        model.prepareMobModel(player, position, speed, 1);
        model.setupAnim(player, position, speed, player.tickCount, Mth.wrapDegrees(player.yHeadRot - player.yBodyRot), player.getXRot());
        PoseStack pose = new PoseStack();
        pose.mulPose(Axis.YP.rotationDegrees(180 - player.yBodyRot));
        pose.scale(-0.9375F, -0.9375F, 0.9375F);
        pose.translate(0, -1.501, 0);
        Capture capture = new Capture();
        model.renderToBuffer(pose, capture, 0xF000F0, 0, -1);
        GHOSTS.add(new Ghost(player.getUUID(), player.position(), player.getSkin().texture(), List.copyOf(capture.vertices), clock));
        while (GHOSTS.size() > 64) GHOSTS.removeFirst();
    }

    /** When the body is rendered, replace the fallback with the exact live skin pose (including item arm poses). */
    private static final class PoseCaptureLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        PoseCaptureLayer(PlayerRenderer renderer) { super(renderer); }
        @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                float limbSwing, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (player.isInvisible() || Objects.equals(CAPTURED.get(player.getUUID()), clock)) return;
            boolean wanted = TRAILS.stream().anyMatch(t -> t.player.equals(player.getUUID()) && (clock - t.start) % 2 == 0);
            if (!wanted) return;
            CAPTURED.put(player.getUUID(), clock);
            Capture capture = new Capture();
            getParentModel().renderToBuffer(pose, capture, light, 0, -1);
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3 origin = player.getPosition(partial);
            Vec3 offset = camera.subtract(origin);
            for (float[] v : capture.vertices) { v[0] += (float)offset.x; v[1] += (float)offset.y; v[2] += (float)offset.z; }
            GHOSTS.removeIf(g -> g.player.equals(player.getUUID()) && g.start == clock);
            GHOSTS.add(new Ghost(player.getUUID(), origin, player.getSkin().texture(), List.copyOf(capture.vertices), clock));
            while (GHOSTS.size() > 64) GHOSTS.removeFirst();
        }
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        checkWorld();
        if (world == null || ghostShader == null || sparkShader == null || dodgeShader == null
                || (GHOSTS.isEmpty() && SPARKS.isEmpty() && GOLD.isEmpty())) return;
        var mc = Minecraft.getInstance();
        Vec3 camera = event.getCamera().getPosition();
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            RenderSystem.setShader(() -> ghostShader);
            // Preserve the original warm-gold skin/outline balance; mote tuning is independent.
            for (Ghost ghost : GHOSTS) {
                if (ghost.origin.distanceToSqr(camera) > 4096) continue;
                // Do not fill the first-person camera with one's own frozen face/body.
                if (mc.options.getCameraType().isFirstPerson() && mc.player != null
                        && ghost.player.equals(mc.player.getUUID()) && ghost.origin.distanceToSqr(mc.player.position()) < 0.35) continue;
                float life = Mth.clamp((clock + partial - ghost.start) / 10F, 0, 1);
                RenderSystem.setShaderTexture(0, ghost.skin);
                ghostShader.safeGetUniform("Fade").set(1 - life);
                var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                pose.pushPose();
                pose.translate(ghost.origin.x, ghost.origin.y, ghost.origin.z);
                Matrix4f matrix = pose.last().pose();
                for (float[] v : ghost.vertices) buffer.addVertex(matrix, v[0], v[1], v[2]).setUv(v[3], v[4]).setColor(255, 211, 114, 110);
                // Frozen player silhouettes deliberately bypass the emission target entirely.
                draw(buffer, false);
                pose.popPose();
            }
            RenderSystem.setShader(() -> sparkShader);
            var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Spark spark : SPARKS) {
                if (spark.origin.distanceToSqr(camera) > 4096) continue;
                float age = (float) (clock + partial - spark.start);
                float fade = Mth.clamp(1 - age / spark.life, 0, 1);
                Vec3 center = spark.origin.add(spark.velocity.scale(age)).add(0, -0.008 * age * age, 0);
                Vec3 axis = spark.velocity.add(0, -0.016 * age, 0).normalize();
                Vec3 side = axis.cross(camera.subtract(center)).normalize().scale(spark.width * (0.5 + fade * 0.5));
                Vec3 length = axis.scale(0.07 + fade * 0.10);
                quad(buffer, pose.last().pose(), center, length, side, fade, true);
            }
            draw(buffer);
            renderGold(pose.last().pose(), camera, partial, event);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
            pose.popPose();
        }
    }

    private static void renderGold(Matrix4f matrix, Vec3 camera, float partial, RenderLevelStageEvent event) {
        RenderSystem.setShader(() -> dodgeShader);
        // Alpha coverage keeps pale-gold foil readable on bright floors without additive white blobs.
        RenderSystem.defaultBlendFunc();
        var cameraRight = new org.joml.Vector3f(1, 0, 0).rotate(event.getCamera().rotation());
        var cameraUp = new org.joml.Vector3f(0, 1, 0).rotate(event.getCamera().rotation());
        Vec3 right = new Vec3(cameraRight.x, cameraRight.y, cameraRight.z);
        Vec3 up = new Vec3(cameraUp.x, cameraUp.y, cameraUp.z);
        var look=event.getCamera().getLookVector();
        // One batch; orientation is independent of velocity. No stretched motion trails.
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (GoldMote mote : GOLD) {
            if (mote.origin.distanceToSqr(camera) > 4096) continue;
            float age = (float)(clock + partial - mote.start);
            float life = Mth.clamp(age / mote.life, 0, 1);
            float fadeStart = 0.28F + (mote.seed % 7) * 0.04F;
            float progress = Mth.clamp((life - fadeStart) / (1 - fadeStart), 0, 1);
            float fade = Mth.clamp(age / 0.65F, 0, 1) * (1 - progress * progress * (3 - 2 * progress));
            if (fade<1F/255) continue;
            float turn = mote.phase + age * ((mote.seed & 1) == 0 ? 0.075F : -0.095F);
            float facing = 0.5F + 0.5F * Mth.sin(mote.phase + age * 0.20F);
            float glint = facing * facing * facing;
            double travel = (1 - Math.exp(-0.085 * age)) / 0.085;
            double x=mote.origin.x+mote.velocity.x*travel+(Mth.sin(mote.phase+age*.16F)-Mth.sin(mote.phase))*.045;
            double y=mote.origin.y+mote.velocity.y*travel-.00040*age*age;
            double z=mote.origin.z+mote.velocity.z*travel+(Mth.cos(mote.phase+age*.16F)-Mth.cos(mote.phase))*.045;
            double dx=x-camera.x,dy=y-camera.y,dz=z-camera.z;
            if (dx*look.x+dy*look.y+dz*look.z<-.12) continue;
            double distanceSquared=dx*dx+dy*dy+dz*dz;
            double size=mote.size*(distanceSquared>=1.5625?1:Mth.clamp(Math.sqrt(distanceSquared)/1.25,.35,1))
                    *(1-.18*progress);
            double cs=Mth.cos(turn),sn=Mth.sin(turn),width=size*(.66+.34*facing);
            // Scalar geometry avoids per-mote vectors, corner arrays and UV arrays every render frame.
            goldQuad(buffer,matrix,x,y,z,
                    (up.x*cs-right.x*sn)*size,(up.y*cs-right.y*sn)*size,(up.z*cs-right.z*sn)*size,
                    (right.x*cs+up.x*sn)*width,(right.y*cs+up.y*sn)*width,(right.z*cs+up.z*sn)*width,
                    mote.seed,glint,life,fade);
        }
        draw(buffer);
    }

    /** dodge_gold Color carries seed, facing highlight, normalized age, opacity (not RGB). */
    private static void goldQuad(BufferBuilder b, Matrix4f m, double x,double y,double z,
            double ax,double ay,double az,double sx,double sy,double sz,
            int seed, float glint, float life, float opacity) {
        int g=Math.round(255*glint),l=Math.round(255*life),a=Math.round(255*opacity);
        b.addVertex(m,(float)(x-ax-sx),(float)(y-ay-sy),(float)(z-az-sz)).setUv(0,0).setColor(seed,g,l,a);
        b.addVertex(m,(float)(x-ax+sx),(float)(y-ay+sy),(float)(z-az+sz)).setUv(1,0).setColor(seed,g,l,a);
        b.addVertex(m,(float)(x+ax+sx),(float)(y+ay+sy),(float)(z+az+sz)).setUv(1,1).setColor(seed,g,l,a);
        b.addVertex(m,(float)(x+ax-sx),(float)(y+ay-sy),(float)(z+az-sz)).setUv(0,1).setColor(seed,g,l,a);
    }

    private static void draw(BufferBuilder buffer) {
        draw(buffer,true);
    }
    private static void draw(BufferBuilder buffer,boolean bloom) {
        MeshData mesh = buffer.build();
        if (mesh!=null) {
            if (bloom) EffectBloom.drawWithBloom(mesh);
            else BufferUploader.drawWithShader(mesh);
        }
    }

    private static void quad(BufferBuilder b, Matrix4f m, Vec3 c, Vec3 a, Vec3 s, float fade, boolean guard) {
        Vec3[] points = { c.subtract(a).subtract(s), c.subtract(a).add(s), c.add(a).add(s), c.add(a).subtract(s) };
        float[][] uv = { {0,0}, {1,0}, {1,1}, {0,1} };
        for (int i = 0; i < 4; i++) b.addVertex(m, (float)points[i].x, (float)points[i].y, (float)points[i].z)
                .setUv(uv[i][0], uv[i][1]).setColor(255, guard ? 167 : 244, guard ? 48 : 178, (int)(255 * fade));
    }

    private static final class Capture implements VertexConsumer {
        final List<float[]> vertices = new ArrayList<>();
        private float[] current;
        public VertexConsumer addVertex(float x, float y, float z) { current = new float[]{x,y,z,0,0}; vertices.add(current); return this; }
        public VertexConsumer setUv(float u, float v) { current[3] = u; current[4] = v; return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
}
