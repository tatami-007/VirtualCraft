package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** One textured bark batch plus an additive bioluminescent vein pass for all root segments. */
final class MushroomRootMaterial {
	private static final ResourceLocation BARK=ResourceLocation.fromNamespaceAndPath(
			"virtual_craft","textures/effect/mushroom_king/root_bark.png");
	private static final ResourceLocation VEINS=ResourceLocation.fromNamespaceAndPath(
			"virtual_craft","textures/effect/mushroom_king/root_veins.png");
	private final List<Root> roots=new ArrayList<>();
	private int veinColor=MushroomFx.MYCELIUM;
	private float veinPulse=1;
	void add(Vec3 a,Vec3 b,double ra,double rb,float alpha) {
		if(a.distanceToSqr(b)>1e-8 && alpha>0) roots.add(new Root(a,b,ra,rb,alpha,veinColor,veinPulse));
	}
	/** Vein tint and brightness for this frame's batch (the ultimate shifts toward amber). */
	void veins(int rgb,float pulse) { veinColor=rgb; veinPulse=pulse; }
	void clear() { roots.clear(); }
	void draw(Matrix4f pose,Vec3 camera) {
		if(roots.isEmpty()) return;
		roots.sort(Comparator.comparingDouble((Root root)->root.a.add(root.b).scale(.5).distanceToSqr(camera)).reversed());
		try {
			RenderSystem.depthMask(true);
			pass(pose,BARK,false,true);
			RenderSystem.depthMask(false);
			pass(pose,BARK,false,false);
			RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
			pass(pose,VEINS,true,false);
			RenderSystem.defaultBlendFunc();
		} finally { RenderSystem.depthMask(false); RenderSystem.defaultBlendFunc(); roots.clear(); }
	}
	private void pass(Matrix4f pose,ResourceLocation texture,boolean glow,boolean solid) {
		try(ByteBufferBuilder bytes=new ByteBufferBuilder(65536)) {
			BufferBuilder out=new BufferBuilder(bytes,VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_TEX_COLOR);
			for(Root root:roots) {
				if(!glow && (root.alpha>=.85F)!=solid) continue;
				root.emit(out,pose,glow);
			}
			MeshData mesh=out.build();
			if(mesh!=null) {
				RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
				RenderSystem.setShaderTexture(0,texture);
				BufferUploader.drawWithShader(mesh);
			}
		}
	}
	private record Root(Vec3 a,Vec3 b,double ra,double rb,float alpha,int veinColor,float pulse) {
		void emit(BufferBuilder out,Matrix4f pose,boolean glow) {
			Vec3 axis=b.subtract(a).normalize();
			// Minimal rotation from +Y: continuous even for an almost vertical root.
			// cross(UP) + a threshold fallback used to flip bark faces as roots grew.
			double denominator=1+axis.y;
			Vec3 side=denominator<1e-8 ? new Vec3(1,0,0)
					: new Vec3(1-axis.x*axis.x/denominator,-axis.x,
							-axis.x*axis.z/denominator).normalize();
			Vec3 up=axis.cross(side).normalize();
			// Texture runs along the root at a fixed density so long roots never smear the grain.
			float v0=0,v1=(float)(a.distanceTo(b)*1.8);
			// Veins thin out toward tips; a hair wider shell keeps them from z-fighting the bark.
			double grow=glow?1.015:1;
			float veinAlpha=alpha*pulse*.28F*(float)Math.min(1,(ra+rb)*3.2);
			for(int face=0;face<8;face++) {
				double angle=face*Math.PI/4,next=(face+1)*Math.PI/4;
				Vec3 n0=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
				Vec3 n1=side.scale(Math.cos(next)).add(up.scale(Math.sin(next)));
				Vec3 normal=n0.add(n1).normalize();
				float shade=glow?1:(float)(.42+.58*Math.max(0,normal.dot(new Vec3(-.35,.85,.39))));
				double grain0=.91+.09*Math.sin(face*2.4+a.x*.17+a.z*.13);
				double grain1=.91+.09*Math.sin((face+1)*2.4+a.x*.17+a.z*.13);
				Vec3 a0=a.add(n0.scale(ra*grow*grain0)),a1=a.add(n1.scale(ra*grow*grain1)),
						b0=b.add(n0.scale(rb*grow*grain0)),b1=b.add(n1.scale(rb*grow*grain1));
				float u0=face/8F,u1=(face+1)/8F;
				int rgb=glow?veinColor:0xD9C9AF;
				float al=glow?veinAlpha:alpha;
				vertex(out,pose,a0,u0,v0,shade,rgb,al);vertex(out,pose,b0,u0,v1,shade,rgb,al);vertex(out,pose,b1,u1,v1,shade,rgb,al);
				vertex(out,pose,a0,u0,v0,shade,rgb,al);vertex(out,pose,b1,u1,v1,shade,rgb,al);vertex(out,pose,a1,u1,v0,shade,rgb,al);
				if(glow) continue;
				// Close tips and cut ends so roots do not read as hollow drinking straws.
				vertex(out,pose,b,.5F,v1,shade*.8F,rgb,al);vertex(out,pose,b1,u1,v1,shade*.8F,rgb,al);vertex(out,pose,b0,u0,v1,shade*.8F,rgb,al);
				vertex(out,pose,a,.5F,v0,shade*.65F,rgb,al);vertex(out,pose,a0,u0,v0,shade*.65F,rgb,al);vertex(out,pose,a1,u1,v0,shade*.65F,rgb,al);
			}
		}
	}
	private static void vertex(BufferBuilder out,Matrix4f pose,Vec3 p,float u,float v,float shade,int rgb,float alpha) {
		out.addVertex(pose,(float)p.x,(float)p.y,(float)p.z).setUv(u,v)
				.setColor((int)(((rgb>>16)&255)*shade),(int)(((rgb>>8)&255)*shade),(int)((rgb&255)*shade),
						Math.round(Math.min(1,alpha)*255));
	}
}
