package dev.tatami.virtualcraft.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Batched geometry: airborne pollen, tumbling wood splinters and short contact streaks. */
public final class MushroomCombatParticle extends Particle {
	public enum Kind { POLLEN, SPLINTER, CONTACT }
	private static final ParticleRenderType LIGHT_MATERIAL = new ParticleRenderType() {
		@Override public BufferBuilder begin(Tesselator tessellator, TextureManager textures) {
			RenderSystem.enableBlend();
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
					com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
			RenderSystem.depthMask(false);
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
			return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
		}
		@Override public String toString() { return "virtual_craft:mushroom_textured_light_v3"; }
	};
	private static final ParticleRenderType MATERIAL = new ParticleRenderType() {
		@Override public BufferBuilder begin(Tesselator tessellator, TextureManager textures) {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(false);
			RenderSystem.setShader(GameRenderer::getPositionColorShader);
			return tessellator.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
		}
		@Override public String toString() { return "virtual_craft:mushroom_combat_v2"; }
	};
	private final Kind kind;
	private final float size;
	private final float phase;
	private final SpriteSet sprites;

	private MushroomCombatParticle(ClientLevel level,double x,double y,double z,
			double vx,double vy,double vz,Kind kind,SpriteSet sprites) {
		super(level,x,y,z);
		this.kind=kind;
		this.sprites=sprites;
		phase=random.nextFloat()*Mth.TWO_PI;
		size=switch(kind) {
			case POLLEN -> .035F+random.nextFloat()*.055F;
			case SPLINTER -> .08F+random.nextFloat()*.12F;
			case CONTACT -> .12F+random.nextFloat()*.12F;
		};
		lifetime=switch(kind) { case POLLEN -> 22+random.nextInt(12); case SPLINTER -> 16+random.nextInt(8); case CONTACT -> 5; };
		setParticleSpeed(vx,vy+(kind==Kind.POLLEN?.014:0),vz);
		hasPhysics=kind==Kind.SPLINTER;
		gravity=hasPhysics?.65F:0;
		friction=kind==Kind.CONTACT?.72F:.94F;
		setSize(size*2,size*2);
	}

	@Override public void tick() {
		super.tick();
		if(kind==Kind.POLLEN) {
			xd+=Math.sin(phase+age*.12)*.0012;
			zd+=Math.cos(phase+age*.12)*.0012;
		}
	}

	@Override public void render(VertexConsumer out,Camera camera,float partial) {
		float time=age+partial,life=Mth.clamp(1-time/lifetime,0,1);
		if(life<=0) return;
		Vec3 at=new Vec3(Mth.lerp(partial,xo,x),Mth.lerp(partial,yo,y),Mth.lerp(partial,zo,z)).subtract(camera.getPosition());
		Vector3f horizontal=new Vector3f(1,0,0).rotate(camera.rotation());
		Vector3f vertical=new Vector3f(0,1,0).rotate(camera.rotation());
		Vec3 right=new Vec3(horizontal.x,horizontal.y,horizontal.z),up=new Vec3(vertical.x,vertical.y,vertical.z);
		if(kind==Kind.SPLINTER) {
			double angle=phase+time*.19;
			Vec3 axis=new Vec3(Math.cos(angle)*.65,Math.sin(angle),Math.sin(angle*.7)*.65).normalize();
			Vec3 side=axis.cross(new Vec3(0,1,0));
			if(side.lengthSqr()<.001) side=new Vec3(1,0,0);
			side=side.normalize().scale(size*.30);
			Vec3 depth=axis.cross(side).normalize().scale(size*.22);
			Vec3 tip=at.add(axis.scale(size)),base=at.subtract(axis.scale(size*.7));
			Vec3[] rim={base.add(side),base.add(depth),base.subtract(side),base.subtract(depth)};
			int light=getLightColor(partial);
			float shade=.48F+.52F*Math.max((light>>4)&15,(light>>20)&15)/15F;
			for(int face=0;face<4;face++) triangle(out,tip,rim[face],rim[(face+1)%4],
					face%2==0?0xB9A17C:0x685245,Math.min(1,life*3)*shade,Math.min(1,life*3)*shade,Math.min(1,life*3)*shade);
		} else {
			float opacity=kind==Kind.POLLEN?Math.min(1,time/3)*Math.min(1,life*3)*.38F:life*life*.85F;
			double radius=size*(kind==Kind.CONTACT?1+(1-life)*.8:1);
			double angle=phase+time*.025,cos=Math.cos(angle),sin=Math.sin(angle);
			Vec3 r=right.scale(cos*radius).add(up.scale(sin*radius));
			Vec3 u=up.scale(cos*radius).subtract(right.scale(sin*radius))
					.scale(kind==Kind.CONTACT?.35:1);
			TextureAtlasSprite sprite=sprites.get(Math.min(age,lifetime-1),lifetime);
			int color=kind==Kind.POLLEN?0xFFA3D2:0xFFF0D8;
			texturedVertex(out,at.subtract(r).subtract(u),sprite.getU0(),sprite.getV1(),color,opacity);
			texturedVertex(out,at.subtract(r).add(u),sprite.getU0(),sprite.getV0(),color,opacity);
			texturedVertex(out,at.add(r).add(u),sprite.getU1(),sprite.getV0(),color,opacity);
			texturedVertex(out,at.add(r).subtract(u),sprite.getU1(),sprite.getV1(),color,opacity);
		}
	}

	private static void texturedVertex(VertexConsumer out,Vec3 p,float u,float v,int color,float alpha) {
		out.addVertex((float)p.x,(float)p.y,(float)p.z).setUv(u,v)
				.setColor((color>>16)&255,(color>>8)&255,color&255,Math.round(alpha*255)).setLight(0xF000F0);
	}

	private static void triangle(VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,int color,float aa,float ab,float ac) {
		vertex(out,a,color,aa);vertex(out,b,color,ab);vertex(out,c,color,ac);
		// Double-sided geometry without leaking a global culling change to other particle batches.
		vertex(out,c,color,ac);vertex(out,b,color,ab);vertex(out,a,color,aa);
	}
	private static void vertex(VertexConsumer out,Vec3 p,int color,float alpha) {
		out.addVertex((float)p.x,(float)p.y,(float)p.z).setColor((color>>16)&255,(color>>8)&255,color&255,Math.round(alpha*255));
	}
	@Override public ParticleRenderType getRenderType() { return kind==Kind.SPLINTER?MATERIAL:LIGHT_MATERIAL; }

	public static ParticleProvider<SimpleParticleType> provider(Kind kind,SpriteSet sprites) {
		return (type,level,x,y,z,vx,vy,vz)-> {
			double distance=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceToSqr(x,y,z);
			if(distance>64*64 || distance<.8*.8) return null;
			ParticleStatus setting=Minecraft.getInstance().options.particles().get();
			if(kind!=Kind.CONTACT && setting!=ParticleStatus.ALL
					&& level.random.nextFloat()<(setting==ParticleStatus.MINIMAL?.75F:.4F)) return null;
			return new MushroomCombatParticle(level,x,y,z,vx,vy,vz,kind,sprites);
		};
	}
}
