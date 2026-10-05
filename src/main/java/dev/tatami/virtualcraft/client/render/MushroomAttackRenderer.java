package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.tatami.virtualcraft.VirtualCraft;
import dev.tatami.virtualcraft.boss.MushroomAttackForm;
import dev.tatami.virtualcraft.entity.MushroomAttackEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Physical attack bodies; cap/tip/cord highlights feed the shared bloom without changing the atlas. */
public final class MushroomAttackRenderer extends EntityRenderer<MushroomAttackEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(VirtualCraft.MODID,
            "textures/entity/mushroom_king/virtual_mushroom_king.png");
    private final Box core = new Box(30,28,-4,-4,-4,8,8,8);
    private final Box cap = new Box(30,1,-5,-2,-5,10,2,10);
    private final Box root = new Box(86,1,-2,-10,-2,4,10,4);
    private final Box tip = new Box(58,1,-1,-4,-1,2,4,2);
    private final Box cord = new Box(86,1,-8,-1,-1,16,2,2);

    public MushroomAttackRenderer(EntityRendererProvider.Context context) { super(context); }

    /** Per-face UVs stay inside one atlas island at one texel per model unit. */
    private static final class Box {
        private final Face[] faces;
        Box(float u,float v,float x,float y,float z,float w,float h,float d) {
            float a=x+w,b=y+h,c=z+d;
            faces=new Face[]{
                    new Face(u,v,w,h,0,0,-1,new float[]{x,y,z,a,y,z,a,b,z,x,b,z}),
                    new Face(u,v,w,h,0,0,1,new float[]{a,y,c,x,y,c,x,b,c,a,b,c}),
                    new Face(u,v,d,h,-1,0,0,new float[]{x,y,c,x,y,z,x,b,z,x,b,c}),
                    new Face(u,v,d,h,1,0,0,new float[]{a,y,z,a,y,c,a,b,c,a,b,z}),
                    new Face(u,v,w,d,0,-1,0,new float[]{x,y,c,a,y,c,a,y,z,x,y,z}),
                    new Face(u,v,w,d,0,1,0,new float[]{x,b,z,a,b,z,a,b,c,x,b,c})};
        }
        void render(PoseStack pose,VertexConsumer vertices,int light,int overlay) {
            for(var face:faces) face.render(pose.last(),vertices,light,overlay);
        }
    }

    private record Face(float u,float v,float width,float height,float nx,float ny,float nz,float[] xyz) {
        void render(PoseStack.Pose pose,VertexConsumer vertices,int light,int overlay) {
            for(int i=0;i<4;i++) {
                vertices.addVertex(pose,xyz[i*3]/16,xyz[i*3+1]/16,xyz[i*3+2]/16).setColor(-1)
                        .setUv((u+(i==1||i==2?width:0))/128,(v+(i>=2?height:0))/128)
                        .setOverlay(overlay).setLight(light).setNormal(pose,nx,ny,nz);
            }
        }
    }

    @Override public void render(MushroomAttackEntity entity, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        double impact = entity.impactAge(partial);
        boolean coreForm = entity.form() == MushroomAttackForm.HUNTING_CORE;
        if (!coreForm && impact < -4) return;
        float rise = (float)Math.clamp((impact + 4) / 4, 0, 1);
        float retract = (float)Math.clamp((impact - 2) / 6, 0, 1);
        var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        var glow = EffectBloom.emissionBuffer(RenderType.entityTranslucentEmissive(TEXTURE));
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.scale(1,-1,1);
        if (coreForm) {
            float size = entity.power() >= 2 ? 1.3F : 1;
            pose.translate(0, -0.3 + retract * 0.65, 0);
            pose.scale(size,size,size);
            pose.mulPose(Axis.XP.rotationDegrees((float)entity.flightAge(partial) * 12));
            core.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
            pose.translate(0,-0.24,0);
            cap.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
            cap.render(pose,glow,0xF000F0,OverlayTexture.NO_OVERLAY);
        } else if (entity.form() == MushroomAttackForm.ROOT_SPIKES) {
            int count = 3 + Math.min(2,entity.power());
            for (int i=0;i<count;i++) {
                pose.pushPose();
                pose.translate((i-(count-1)*0.5)*0.36, (1-rise+retract)*0.9, (i%2)*0.16);
                pose.mulPose(Axis.ZP.rotationDegrees((i-(count-1)*0.5F)*-9));
                root.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
                pose.translate(0,-0.61,0);
                tip.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
                tip.render(pose,glow,0xF000F0,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        } else {
            // Side-grown stalks carry a horizontal cord at standing chest height, above crouching height.
            pose.translate(0,(1-rise+retract)*1.8,0);
            for (int side : new int[]{-1,1}) {
                pose.pushPose();
                pose.translate(side*0.9,0,0);
                for(int n=0;n<3;n++) {
                    root.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
                    pose.translate(0,-0.54,0);
                }
                pose.popPose();
            }
            pose.translate(0,-1.64, -Mth.clamp((float)impact,0,2)*0.18F);
            for (int side : new int[]{-1,1}) {
                pose.pushPose();
                pose.translate(side*0.48,0,0);
                cord.render(pose,vertices,light,OverlayTexture.NO_OVERLAY);
                cord.render(pose,glow,0xF000F0,OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        pose.popPose();
        super.render(entity,yaw,partial,pose,buffers,light);
    }

    @Override public ResourceLocation getTextureLocation(MushroomAttackEntity entity) { return TEXTURE; }
}
