package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.tatami.virtualcraft.VirtualCraft;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Opt-in world emission and a three-scale HDR bloom pyramid. Never extracts the scene/HUD or player ghosts. */
public final class EffectBloom {
    public static final RenderType PARTICLE_EMISSION = RenderType.create("virtual_craft:particle_emission",
            DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS, 4096, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getParticleShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_PARTICLES,false,false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL).createCompositeState(false));
    private static ShaderInstance blur, composite;
    private static RenderTarget emission, nearGlow, middleGlow, wideGlow;
    private static VertexBuffer immediate, screenQuad;
    private static boolean frameOpen, prepared, deferred;
    private static long frame;
    private static final int MAX_BATCHES = 32;
    private static final int[] savedViewport = new int[4];
    private static ClientLevel world;
    private static final Map<RenderType, Batch> batches = new LinkedHashMap<>();
    private static final class Batch implements AutoCloseable {
        final ByteBufferBuilder memory = new ByteBufferBuilder(4096);
        BufferBuilder vertices;
        long lastUsed;
        final org.joml.Matrix4f modelView = new org.joml.Matrix4f();
        final org.joml.Matrix4f projection = new org.joml.Matrix4f();
        VertexConsumer begin(RenderType type) {
            if (vertices == null) {
                vertices = new BufferBuilder(memory,type.mode(),type.format());
                modelView.set(RenderSystem.getModelViewMatrix());
                projection.set(RenderSystem.getProjectionMatrix());
            }
            lastUsed = frame;
            return vertices;
        }
        void reset() { vertices=null; memory.clear(); }
        @Override public void close() { memory.close(); }
    }
    private EffectBloom() {}

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        release();
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("effect_bloom_blur"),
                DefaultVertexFormat.BLIT_SCREEN), shader -> blur = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), id("effect_bloom_composite"),
                DefaultVertexFormat.BLIT_SCREEN), shader -> composite = shader);
    }
    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,name); }
    private static boolean enabled() { return frameOpen && blur != null && composite != null; }

    /** Copy only explicitly emissive geometry. Ordinary model/terrain/UI buffers are untouched. */
    public static VertexConsumer buffer(MultiBufferSource source, RenderType type) {
        VertexConsumer normal = source.getBuffer(type);
        return enabled() ? VertexMultiConsumer.create(normal, emissionBuffer(type)) : normal;
    }

    /** Additional emission-only geometry, e.g. the tips/cap of a physical attack body. */
    public static VertexConsumer emissionBuffer(RenderType type) {
        if (!enabled()) return DISCARD;
        Batch batch=batches.get(type);
        if (batch==null) {
            if (batches.size()>=MAX_BATCHES) return DISCARD;
            batch=new Batch(); batches.put(type,batch);
        }
        deferred=true;
        return batch.begin(type);
    }

    /** Immediate effects upload once, drawing the same geometry to the scene and the emission mask. */
    public static void drawWithBloom(MeshData mesh) {
        if (!enabled()) { BufferUploader.drawWithShader(mesh); return; }
        if (immediate == null) immediate = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        ShaderInstance shader = RenderSystem.getShader();
        immediate.bind();
        immediate.upload(mesh); // consumes MeshData, just as BufferUploader does
        try {
            immediate.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT,savedViewport);
            try {
                prepare();
                emission.bindWrite(false);
                immediate.bind();
                immediate.drawWithShader(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(), shader);
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previous);
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                RenderSystem.viewport(savedViewport[0],savedViewport[1],savedViewport[2],savedViewport[3]);
            }
        } finally { VertexBuffer.unbind(); }
    }

    private static RenderTarget target(int w, int h, boolean depth) {
        var target = new TextureTarget(w,h,depth,Minecraft.ON_OSX);
        target.setClearColor(0,0,0,0);
        target.setFilterMode(GL11.GL_LINEAR);
        if (!depth) {
            // Retain emission energy above 1 in the pyramid; scene/mask formats stay unchanged.
            GlStateManager._bindTexture(target.getColorTextureId());
            GlStateManager._texImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,
                    GL11.GL_RGBA,GL11.GL_FLOAT,null);
            GlStateManager._bindTexture(0);
            target.bindWrite(false);
            target.checkStatus();
        }
        return target;
    }

    private static void prepare() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        if (emission == null || emission.width != main.width || emission.height != main.height
                || emission.isStencilEnabled() != main.isStencilEnabled()) {
            destroyTargets();
            emission = target(main.width,main.height,true);
            if (main.isStencilEnabled()) emission.enableStencil();
            nearGlow = target(Math.max(1,main.width/2),Math.max(1,main.height/2),false);
            middleGlow = target(Math.max(1,main.width/4),Math.max(1,main.height/4),false);
            wideGlow = target(Math.max(1,main.width/8),Math.max(1,main.height/8),false);
            prepared = false;
        }
        if (!prepared) {
            // RenderTarget.clear also clears depth; temporarily permit that write, then restore it.
            boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            RenderSystem.depthMask(true);
            emission.clear(Minecraft.ON_OSX);
            emission.copyDepthFrom(main);
            RenderSystem.depthMask(depthWrite);
            prepared = true;
        }
    }

    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            var level = Minecraft.getInstance().level;
            if (world != level) { release(); world = level; Minecraft.getInstance().getMainRenderTarget().bindWrite(true); }
            resetBatches();
            frame++;
            prepared = false;
            frameOpen = level != null;
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            if (!enabled()) return;
            try {
                if (!prepared && !deferred) return; // no bloom work/allocation for an empty frame
                finish();
            } finally { frameOpen = false; resetBatches(); }
        }
    }

    private static void finish() {
        var main = Minecraft.getInstance().getMainRenderTarget();
        ShaderInstance previousShader = RenderSystem.getShader();
        try {
            boolean hadImmediateEmission=prepared;
            prepare();
            // Deferred entity emission uses completed opaque scene depth, including foreground entities.
            if (hadImmediateEmission && deferred) emission.copyDepthFrom(main);
            for (var entry : batches.entrySet()) {
                if (entry.getValue().vertices==null) continue;
                MeshData mesh = entry.getValue().vertices.build();
                if (mesh == null) continue;
                RenderType type = entry.getKey();
                type.setupRenderState();
                try {
                    // Bind AFTER RenderType's output shard (Fabulous may otherwise redirect it).
                    emission.bindWrite(true);
                    RenderSystem.depthMask(false);
                    if (immediate == null) immediate = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
                    immediate.bind();
                    immediate.upload(mesh);
                    immediate.drawWithShader(entry.getValue().modelView,entry.getValue().projection,RenderSystem.getShader());
                } finally { VertexBuffer.unbind(); type.clearRenderState(); }
            }
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.disableBlend();
            // Three isotropic downsample passes: 0.328 screen areas, formerly 0.625.
            pass(emission,nearGlow,1F/emission.width,1F/emission.height,1);
            pass(nearGlow,middleGlow,1.75F/nearGlow.width,1.75F/nearGlow.height,0);
            pass(middleGlow,wideGlow,2F/middleGlow.width,2F/middleGlow.height,0);
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE,
                    GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
            composite.setSampler("NearSampler",nearGlow.getColorTextureId());
            composite.setSampler("MiddleSampler",middleGlow.getColorTextureId());
            composite.setSampler("FarSampler",wideGlow.getColorTextureId());
            composite.safeGetUniform("Strength").set(1.15F);
            screen(composite);
        } finally {
            main.bindWrite(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void pass(RenderTarget input,RenderTarget output,float dx,float dy,float extract) {
        output.bindWrite(true);
        blur.setSampler("DiffuseSampler",input.getColorTextureId());
        blur.safeGetUniform("Direction").set(dx,dy);
        blur.safeGetUniform("Extract").set(extract);
        screen(blur);
    }
    private static void screen(ShaderInstance shader) {
        if (screenQuad==null) {
            screenQuad=new VertexBuffer(VertexBuffer.Usage.STATIC);
            var quad=RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.BLIT_SCREEN);
            quad.addVertex(0,0,0); quad.addVertex(1,0,0); quad.addVertex(1,1,0); quad.addVertex(0,1,0);
            screenQuad.bind(); screenQuad.upload(quad.buildOrThrow());
        } else screenQuad.bind();
        shader.apply();
        screenQuad.draw();
        shader.clear();
        VertexBuffer.unbind();
    }
    private static void resetBatches() {
        deferred=false;
        batches.values().removeIf(batch -> {
            batch.reset();
            if (frame-batch.lastUsed>120) { batch.close(); return true; }
            return false;
        });
    }
    private static void destroyTargets() {
        for (var target : new RenderTarget[]{emission,nearGlow,middleGlow,wideGlow}) if (target != null) target.destroyBuffers();
        emission=nearGlow=middleGlow=wideGlow=null;
    }
    public static void release() {
        batches.values().forEach(Batch::close); batches.clear(); destroyTargets();
        if (immediate != null) { immediate.close(); immediate=null; }
        if (screenQuad != null) { screenQuad.close(); screenQuad=null; }
        prepared=frameOpen=deferred=false; world=null; frame=0;
    }
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        public VertexConsumer addVertex(float x,float y,float z) { return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    };
}
