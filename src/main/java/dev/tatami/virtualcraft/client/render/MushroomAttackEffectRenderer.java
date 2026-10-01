package dev.tatami.virtualcraft.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.tatami.virtualcraft.boss.MushroomAttackVolume;
import dev.tatami.virtualcraft.boss.MushroomComboPattern;
import dev.tatami.virtualcraft.boss.MushroomKingAttackRules;
import dev.tatami.virtualcraft.entity.VirtualMushroomKing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Release geometry that appears around impact while telegraphs remain independently visible.
 *
 * <p>Three layers per frame: textured bark roots with glowing veins, alpha-blended solid
 * shapes (readable on the bright virtual terrain), then additive light from {@link MushroomFx}.
 */
public final class MushroomAttackEffectRenderer {
	private static final Palette MID = new Palette(0xFFF6EA, 0xFF8FC6, 0x4A2436);
	private static final Palette LOW = new Palette(MushroomFx.AMBER_PALE, MushroomFx.AMBER, 0x4A3310);
	private static final Palette HIGH = new Palette(MushroomFx.SKY_PALE, MushroomFx.SKY, 0x173247);
	private static final Palette SPORE = new Palette(MushroomFx.SPORE_PALE, MushroomFx.SPORE, 0x3A1630);
	private static final Palette ULTIMATE = new Palette(MushroomFx.AMBER_PALE, MushroomFx.MYCELIUM, 0x10302C);

	private static final MushroomRootMaterial ROOT_MATERIAL = new MushroomRootMaterial();
	private static final MushroomFx FX = new MushroomFx();
	private static boolean loggedLiveRender;
	private static final int[] CONVERGENCE_OFFSETS = {42, 26, 12, 0};
	/** Camera for the frame being built; spore pods add a billboard halo. */
	private static Vec3 frameCamera = Vec3.ZERO;
	private static final Map<VirtualMushroomKing, GroundAttack> GROUND_ATTACKS = new WeakHashMap<>();

	private record GroundAnchor(Vec3 origin, Vec3 forward, double clientTime, float age) {}

	private static final class GroundAttack {
		final int attack, variant;
		int lastTick;
		final Map<Integer, GroundAnchor> moves = new HashMap<>();
		GroundAttack(int attack, int variant, int tick) {
			this.attack = attack;
			this.variant = variant;
			lastTick = tick;
		}
	}

	private MushroomAttackEffectRenderer() {
	}

	public static void render(PoseStack poseStack) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			GROUND_ATTACKS.clear();
			return;
		}
		ROOT_MATERIAL.clear();
		FX.clear();
		Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
		frameCamera = camera;
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		// Alpha blending keeps dark silhouettes readable on the bright virtual terrain.
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorShader);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,
				DefaultVertexFormat.POSITION_COLOR);
		Matrix4f pose = poseStack.last().pose();
		boolean wrote = false;
		for (var entity : minecraft.level.entitiesForRendering()) {
			if (entity instanceof VirtualMushroomKing boss
					&& (boss.isAlive() || boss.deathTime > 0)
					&& boss.position().distanceToSqr(camera) <= 96.0 * 96.0) {
				float time = boss.tickCount + minecraft.getTimer().getGameTimeDeltaPartialTick(false);
				ROOT_MATERIAL.veins(veinColor(boss), .72F + .28F * Mth.sin(time * .21F));
				if (boss.isAlive() && boss.getAttackState() != 0) {
					wrote |= renderAttack(buffer, pose, boss, camera);
				} else {
					GROUND_ATTACKS.remove(boss);
				}
				wrote |= renderRitual(buffer, pose, boss, camera);
			}
		}
		MeshData mesh = buffer.build();
		// Root-only attacks need a draw even when the overlay emitted no vertices.
		ROOT_MATERIAL.draw(pose, camera);
		if (mesh != null) {
			if (wrote) {
				RenderSystem.setShader(GameRenderer::getPositionColorShader);
				BufferUploader.drawWithShader(mesh);
			} else {
				mesh.close();
			}
		}
		FX.flush(pose);
		if(wrote && !loggedLiveRender) {
			org.apache.logging.log4j.LogManager.getLogger().info(
					"Mushroom presentation v4 ACTIVE: textured particles + spore pods + root waves + additive fx; source={}",
					MushroomAttackEffectRenderer.class.getProtectionDomain().getCodeSource().getLocation());
			loggedLiveRender=true;
		}
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		poseStack.popPose();
	}

	/** Each status form tints the mycelial veins; the base form keeps the signature teal. */
	private static int veinColor(VirtualMushroomKing boss) {
		return switch (boss.getForm()) {
			case VirtualMushroomKing.FORM_RED -> 0xFF6A4A;
			case VirtualMushroomKing.FORM_GREEN -> 0x8CFF5A;
			case VirtualMushroomKing.FORM_PURPLE -> 0xC77DFF;
			default -> MushroomFx.MYCELIUM;
		};
	}

	private static boolean renderAttack(BufferBuilder buffer, Matrix4f pose,
			VirtualMushroomKing boss, Vec3 camera) {
		int attack = boss.getAttackState();
		if (!MushroomKingAttackRules.isKnownAttack(attack)) return false;
		int syncedTick = boss.getAttackTick();
		GroundAttack ground = GROUND_ATTACKS.get(boss);
		if (ground == null || ground.attack != attack || ground.variant != boss.getAttackVariant()
				|| syncedTick < ground.lastTick) {
			ground = new GroundAttack(attack, boss.getAttackVariant(), syncedTick);
			GROUND_ATTACKS.put(boss, ground);
		}
		ground.lastTick = syncedTick;
		float tick = boss.getAttackTick()
				+ Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Vec3 forward = forward(boss.getLockedYaw());
		MushroomComboPattern combo = boss.getComboPattern();
		boolean wrote = false;
		if (combo != null) {
			for (int i=0;i<combo.stepCount();i++) {
				var step=combo.step(i);
				float age=tick-step.impactTick();
				// Never show the next move before its actual cue window.
				if (tick < combo.cueTick(i)) continue;
				wrote |= renderMove(buffer,pose,boss,forward,camera,
						MushroomEffectStyle.forMove(step.move()),age,i,combo.advanceStart(i));
			}
		} else {
			for (int wave=1;wave<=MushroomKingAttackRules.waveCount(attack);wave++) {
				float age=tick-MushroomKingAttackRules.impactTick(attack,wave);
				wrote |= renderMove(buffer,pose,boss,forward,camera,
						MushroomEffectStyle.forAttack(attack,wave),age,wave-1,
						MushroomAttackVolume.surgeStart(wave));
			}
		}
		return wrote;
	}

	private static boolean renderMove(BufferBuilder b, Matrix4f p, VirtualMushroomKing boss,
			Vec3 f, Vec3 camera, MushroomEffectStyle style, float age, int beat, double advance) {
		if (age < (style == MushroomEffectStyle.CONVERGENCE ? -60 : -7)) return false;
		float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Vec3 o = boss.getPosition(partial);
		boolean grounded = switch (style) {
			case STOMP_LEFT, STOMP_RIGHT, QUAKE, ERUPTION, SURGE, CONVERGENCE -> true;
			default -> false;
		};
		if (grounded && (age >= 0 || style == MushroomEffectStyle.CONVERGENCE)) {
			GroundAttack ground = GROUND_ATTACKS.get(boss);
			int key = style.ordinal() * 32 + beat;
			GroundAnchor anchor = ground.moves.get(key);
			double clientTime = (double)boss.tickCount + partial;
			if (anchor == null) {
				if (age > 16) return false;
				anchor = new GroundAnchor(o, f, clientTime, age);
				ground.moves.put(key, anchor);
			}
			// Once erupted, roots stay planted even when the next combo locks a new yaw.
			// Animate from the local render clock rather than resetting on synced tick packets.
			o = anchor.origin();
			f = anchor.forward();
			age = anchor.age() + (float)(clientTime - anchor.clientTime());
		}
		if (age > 16) return false;
		boolean wrote = switch(style) {
			case SWEEP_LEFT,SWEEP_RIGHT -> renderSculptedSweep(b,p,o,f,camera,age,style==MushroomEffectStyle.SWEEP_LEFT);
			case HAMMER -> renderHeavyContact(b,p,o,f,camera,age);
			case CLAP -> renderClosingClaws(b,p,o,f,camera,age,false);
			case CLAMP -> renderClosingClaws(b,p,o,f,camera,age,true);
			case UPPERCUT -> renderRisingRoot(b,p,o,f,camera,age);
			case DRIVE,RUSH -> renderRam(b,p,o,f,camera,age,style==MushroomEffectStyle.RUSH);
			case STOMP_LEFT,STOMP_RIGHT,QUAKE,ERUPTION,SURGE ->
				renderRootGarden(b,p,o,f,camera,age,style,beat,advance);
			case CANOPY -> renderClosingCanopy(b,p,o,f,camera,age);
			case BREATH,NEEDLES -> renderSporeRelease(b,p,o,f,camera,age,style==MushroomEffectStyle.NEEDLES,beat);
			case CONVERGENCE -> renderConvergingGarden(b,p,o,camera,age);
		};
		if (wrote) renderReleaseAccents(b,p,boss,o,f,camera,style,age,beat,advance);
		return wrote;
	}

	private enum ReleaseKind { SLICE, CRUSH, PINCH, CHARGE, LIFT, ROOT, PLUME, NEEDLE }
	private record Release(Vec3 contact, Palette palette, ReleaseKind kind, double weight) {}

	/** Every move has a directional release, not just another colour of the same glow sphere. */
	private static void renderReleaseAccents(BufferBuilder b, Matrix4f p, VirtualMushroomKing boss,
			Vec3 o, Vec3 f, Vec3 camera, MushroomEffectStyle style, float age, int beat, double advance) {
		if (age >= 0) {
			GroundAttack attack = GROUND_ATTACKS.get(boss);
			int key = style.ordinal() * 32 + beat;
			GroundAnchor anchor = attack.moves.get(key);
			double time = (double)boss.tickCount + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
			if (anchor == null) {
				anchor = new GroundAnchor(o,f,time,age);
				attack.moves.put(key,anchor);
			}
			o=anchor.origin(); f=anchor.forward();
			age=anchor.age()+(float)(time-anchor.clientTime());
		}
		Vec3 side = new Vec3(-f.z,0,f.x);
		Release release = switch (style) {
			case SWEEP_LEFT -> new Release(o.add(f.scale(3.4)).add(side.scale(-.8)).add(0,1.35,0),MID,ReleaseKind.SLICE,.8);
			case SWEEP_RIGHT -> new Release(o.add(f.scale(3.4)).add(side.scale(.8)).add(0,1.35,0),MID,ReleaseKind.SLICE,.8);
			case HAMMER -> new Release(o.add(f.scale(2.75)).add(0,.12,0),LOW,ReleaseKind.CRUSH,1.3);
			case CLAP -> new Release(o.add(f.scale(2.65)).add(0,1.8,0),MID,ReleaseKind.PINCH,1.0);
			case CLAMP -> new Release(o.add(f.scale(2.2)).add(0,.12,0),LOW,ReleaseKind.PINCH,1.1);
			case DRIVE -> new Release(o.add(f.scale(2.4)).add(0,1.25,0),MID,ReleaseKind.CHARGE,.85);
			case RUSH -> new Release(o.add(f.scale(2.4)).add(0,1.9,0),MID,ReleaseKind.CHARGE,1.1);
			case UPPERCUT -> new Release(o.add(f.scale(3.1)).add(0,1.5,0),MID,ReleaseKind.LIFT,1.0);
			case STOMP_LEFT -> new Release(o.add(f.scale(.8)).add(side.scale(.98)),LOW,ReleaseKind.ROOT,.8);
			case STOMP_RIGHT -> new Release(o.add(f.scale(.8)).add(side.scale(-.98)),LOW,ReleaseKind.ROOT,.8);
			case QUAKE -> new Release(o.add(f.scale(1.8)),LOW,ReleaseKind.CRUSH,1.5);
			case ERUPTION -> new Release(o.add(f.scale(2.25)),SPORE,ReleaseKind.PLUME,1.0);
			case SURGE -> new Release(o.add(f.scale(advance)),LOW,ReleaseKind.ROOT,1.0);
			case CANOPY -> new Release(o.add(f.scale(3.2)).add(0,2.2,0),HIGH,ReleaseKind.CRUSH,.85);
			case BREATH -> new Release(o.add(f.scale(.9)).add(0,2.75,0),SPORE,ReleaseKind.PLUME,.55);
			case NEEDLES -> new Release(o.add(f.scale(.9)).add(0,2.75,0),SPORE,ReleaseKind.NEEDLE,.5);
			case CONVERGENCE -> new Release(o,ULTIMATE,ReleaseKind.ROOT,1.8);
		};
		if (style == MushroomEffectStyle.CONVERGENCE) {
			for (int stage=0;stage<4;stage++) {
				float local=age+CONVERGENCE_OFFSETS[stage];
				if(local<0 || local>12) continue;
				double radius=stage==3?18:4+stage*5;
				for(int sector=0;sector<8;sector++) {
					double angle=sector*Math.PI/4+stage*.23;
					Vec3 site=o.add(Math.cos(angle)*radius,.12,Math.sin(angle)*radius);
					impactDebris(b,p,site,new Vec3(-Math.cos(angle),0,-Math.sin(angle)),camera,
							local,ULTIMATE,ReleaseKind.ROOT,.65,sector+stage*8);
				}
			}
			return;
		}
		if (age<0) {
			float tension=MushroomEffectMotion.smooth((age+7)/7);
			for(int strand=0;strand<3;strand++) {
				double sign=(strand&1)==0?1:-1;
				Vec3 tip=release.contact().add(side.scale(sign*(.16+strand*.06)))
						.add(0,.1+strand*.07,0);
				Vec3 root=tip.subtract(f.scale(.6-.25*tension)).add(side.scale(sign*.13))
						.add(0,.18*Math.sin(tension*Math.PI),0);
				rootSegment(b,p,root,root.lerp(tip,.55).add(0,.06,0),.022,.012,tension*.8F);
				rootSegment(b,p,root.lerp(tip,.55).add(0,.06,0),tip,.012,.001,tension*.8F);
			}
			return;
		}
		if (style==MushroomEffectStyle.ERUPTION) {
			for(int burst=0;burst<3;burst++) {
				Vec3 site=o.add(f.scale(2.25+burst*1.05)).add(side.scale((burst&1)==0?-.48:.48));
				impactDebris(b,p,site,f,camera,age-burst*1.4F,SPORE,ReleaseKind.PLUME,.75,beat*7+burst);
			}
		} else if (style==MushroomEffectStyle.SURGE) {
			for(int crest=0;crest<4;crest++) {
				impactDebris(b,p,release.contact().add(f.scale(crest*1.1)),f,camera,
						age-crest*1.3F,LOW,ReleaseKind.ROOT,.65,beat*7+crest);
			}
		} else {
			impactDebris(b,p,release.contact(),f,camera,age,release.palette(),release.kind(),
					release.weight(),beat+style.ordinal()*5);
		}
	}

	/** Seeded ballistic chips + torn dust wisps. Rebuilt deterministically, never re-spawned each frame. */
	private static void impactDebris(BufferBuilder b, Matrix4f p, Vec3 center, Vec3 forward,
			Vec3 camera, float age, Palette palette, ReleaseKind kind, double weight, int seed) {
		if(age<0 || age>12) return;
		float alpha=1-MushroomEffectMotion.smooth((age-5)/7);
		Vec3 lateral=new Vec3(-forward.z,0,forward.x);
		for(int chip=0;chip<9;chip++) {
			double angle=chip*2.399963+seed*.37;
			Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
			Vec3 velocity=switch(kind) {
				case SLICE -> lateral.scale(Math.cos(angle)*.23).add(forward.scale(.09));
				case PINCH -> lateral.scale((chip%2==0?1:-1)*(.16+chip*.009));
				case CHARGE, NEEDLE -> forward.scale(.23+chip*.012).add(lateral.scale(Math.sin(angle)*.06));
				case LIFT -> forward.scale(.05).add(radial.scale(.06));
				case CRUSH, ROOT, PLUME -> radial.scale(.10+(chip%4)*.035);
			};
			double lift=kind==ReleaseKind.LIFT?.27:kind==ReleaseKind.PLUME?.22:.10+(chip%3)*.025;
			Vec3 at=center.add(velocity.scale(age*weight)).add(0,
					Math.max(-.05,age*lift-age*age*.011),0);
			double size=(.045+(chip%3)*.018)*weight;
			Vec3 axis=new Vec3(Math.cos(angle+age*.24),.65,Math.sin(angle+age*.24)).normalize();
			Vec3 cross=axis.cross(new Vec3(0,1,0)).normalize();
			Vec3 up=axis.cross(cross);
			Vec3 tip=at.add(axis.scale(size*1.7));
			Vec3 base=at.subtract(axis.scale(size));
			Vec3[] rim={base.add(cross.scale(size*.55)),base.add(up.scale(size*.32)),
					base.subtract(cross.scale(size*.55)),base.subtract(up.scale(size*.32))};
			for(int face=0;face<4;face++) {
				triangle(b,p,tip,rim[face],rim[(face+1)%4],face%2==0?0xA59171:0x514336,alpha);
			}
			if(chip%3==0 && age<3) {
				FX.trail(at.subtract(velocity.scale(1.2)),at,camera,.014,palette.main(),alpha*.45F);
			}
			if(chip%2==0 && kind!=ReleaseKind.NEEDLE) {
				Vec3 view=camera.subtract(at).normalize();
				Vec3 right=view.cross(new Vec3(0,1,0));
				if(right.lengthSqr()<1e-7) right=new Vec3(1,0,0);
				right=right.normalize();
				Vec3 upright=right.cross(view).normalize();
				double spread=(.08+age*.025)*weight;
				gradientTriangle(b,p,at.add(right.scale(-spread)),at.add(upright.scale(spread*.55)),
						at.add(right.scale(spread*1.8)),0x847B68,0,alpha*.14F,0);
			}
		}
		if(kind==ReleaseKind.ROOT || (kind==ReleaseKind.CRUSH && weight>=1.1)
				|| (kind==ReleaseKind.PINCH && weight>1.05)) {
			for(int crack=0;crack<7;crack++) {
				double angle=crack*2.399963+seed*.37;
				Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
				Vec3 tangent=new Vec3(-radial.z,0,radial.x);
				Vec3 start=center.add(radial.scale(.18)).add(0,.015,0);
				Vec3 bend=start.add(radial.scale(weight*.45)).add(tangent.scale(Math.sin(crack*3.1)*.12));
				Vec3 end=bend.add(radial.scale(weight*.35));
				groundBranch(b,p,start,bend,tangent,.035,.018,0x494335,alpha*.65F);
				groundBranch(b,p,bend,end,tangent,.018,.001,0x494335,alpha*.45F);
			}
		}
	}

	private static void rootSegment(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b,
			double radiusA, double radiusB, float alpha) {
		ROOT_MATERIAL.add(a,b,radiusA,radiusB,alpha);
	}

	/** Expanding ground ring that reads as a shockwave from any camera height. */
	private static void shockwave(Vec3 center, float age, double speed, double maxRadius,
			float duration, int rgb, float strength) {
		if (age < 0 || age > duration) return;
		float t = age / duration;
		double radius = Math.min(maxRadius, .35 + age * speed);
		float alpha = strength * (1 - t) * (1 - t);
		FX.ground(MushroomFx.Tex.SHOCKWAVE, center.add(0, .07, 0), radius, .17, rgb, alpha*.35F, true);
	}

	private static boolean renderSculptedSweep(BufferBuilder buffer, Matrix4f pose,
			Vec3 origin, Vec3 forward, Vec3 camera, float age, boolean left) {
		if (age < -6 || age > 11) return false;
		double sign=left ? 1 : -1;
		Vec3 side=new Vec3(-forward.z,0,forward.x);
		Vec3 hand=origin.add(forward.scale(.35)).add(side.scale(sign*1.65)).add(0,1.55,0);
		Vec3 end=origin.add(forward.scale(3.65)).add(side.scale(-sign*.8)).add(0,1.35,0);
		float release=MushroomEffectMotion.strike(age,6);
		float alpha=age<=3 ? 1 : Mth.clamp((11-age)/8,0,1);
		Vec3 previous=hand;
		for (int part=1;part<=14;part++) {
			double t=part/14.0;
			Vec3 at=hand.lerp(end,t*(.18+.82*release))
					.add(side.scale(sign*Math.sin(t*Math.PI)*(.65-.45*release)))
					.add(0,Math.sin(t*Math.PI)*.24,0);
			rootSegment(buffer,pose,previous,at,.30*(1-t)+.06,.28*(1-t)+.025,alpha);
			if (part%4==0) {
				Vec3 tip=at.add(side.scale(sign*.22)).add(0,.3,0);
				rootSegment(buffer,pose,at,tip,.07,.005,alpha);
			}
			previous=at;
		}
		if (age<0) {
			// Energy pools in the swinging hand during the windup.
			float charge=(age+6)/6;
			FX.billboard(MushroomFx.Tex.GLOW,hand,camera,.45+charge*.55,0,MID.main(),.35F+charge*.45F);
		}
		// Thin trailing filaments, never an opaque disk over the boss silhouette.
		if (age>=0 && age<6) {
			float swing=Mth.clamp(1-age/6,0,1);
			// Continuous, tapering swept edge ties the motion together instead of isolated sticks.
			Vec3 previousEdge=hand;
			for(int segment=1;segment<=20;segment++) {
				double t=segment/20.0;
				Vec3 edge=hand.lerp(end,t).add(side.scale(sign*Math.sin(t*Math.PI)*.48))
						.add(0,Math.sin(t*Math.PI)*.24,0);
				Vec3 inner=edge.add(forward.scale(-Math.sin(t*Math.PI)*.38));
				gradientTriangle(buffer,pose,previousEdge,edge,inner,MID.main(),alpha*.42F,alpha*.42F,0);
				// Additive crescent: the head of the swing is brightest.
				FX.trail(previousEdge,edge,camera,.045+.075*Math.sin(t*Math.PI),MID.main(),swing*(float)(.15+.45*t));
				previousEdge=edge;
			}
			for(int trail=0;trail<3;trail++) {
				Vec3 tail=end.add(side.scale(sign*(trail+1)*.17)).add(0,.1*trail,0);
				energyRibbon(buffer,pose,hand,tail,camera,.035,MID,alpha*(.30F-trail*.07F));
			}
			if(age<2) impactCross(buffer,pose,end,camera,.75,MID,1-age/2);
		}
		return true;
	}

	private static boolean renderHeavyContact(BufferBuilder buffer, Matrix4f pose,
			Vec3 origin, Vec3 forward, Vec3 camera, float age) {
		if(age < -7 || age > 16) return false;
		Vec3 side=new Vec3(-forward.z,0,forward.x);
		Vec3 contact=origin.add(forward.scale(2.75)).add(0,.12,0);
		if(age<0) {
			// Suspended root knuckles gather inward during the real windup.
			float charge=(age+7)/7;
			Vec3 fist=origin.add(forward.scale(.65)).add(0,3.8+.25*charge,0)
					.lerp(contact.add(0,.45,0),MushroomEffectMotion.strike(age,7));
			for(int i=0;i<5;i++) {
				double a=i*Mth.TWO_PI/5;
				Vec3 at=fist.add(Math.cos(a)*(.6-.25*charge),Math.sin(a)*.35,0);
				rootSegment(buffer,pose,at,at.add(forward.scale(.45)),.13,.06,.8F);
			}
			FX.billboard(MushroomFx.Tex.GLOW,fist,camera,.6+charge*.9,0,MID.main(),.3F+charge*.5F);
			FX.billboard(MushroomFx.Tex.FLARE,fist,camera,.4+charge*.7,charge*1.4,MID.core(),charge*.6F);
			return true;
		}
		float fade=Mth.clamp(1-age/16,0,1);
		if(age<2) impactCross(buffer,pose,contact.add(0,.3,0),camera,.85,MID,1-age/2);
		shockwave(contact,age,.42,5.2,12,MID.main(),.95F);
		shockwave(contact,age-2.5F,.3,3.6,10,LOW.main(),.55F);
		// Travelling chips within the forward melee footprint; no extra damage.
		for(int i=0;i<18;i++) {
			float local=age-(i%3)*1.5F;
			if(local<0) continue;
			double lateral=((i%7)-3)*.23;
			double advance=.6+(i/7)*.65+local*.035;
			double lift=Math.max(0,local*(.14+(i%4)*.018)-local*local*.012);
			Vec3 at=origin.add(forward.scale(advance)).add(side.scale(lateral)).add(0,.08+lift,0);
			Vec3 tip=at.add(side.scale(.06*(i%2==0?1:-1))).add(0,.16+(i%3)*.08,0);
			rootSegment(buffer,pose,at,tip,.10,.025,fade);
		}
		for(int ray=0;ray<7;ray++) {
			float local=age-ray*.3F;
			if(local<0 || local>10) continue;
			Vec3 direction=rotateY(forward,(ray-3)*8);
			Vec3 a=origin.add(direction.scale(.4+local*.18)).add(0,.055,0);
			Vec3 b=a.add(direction.scale(.45));
			groundBranch(buffer,pose,a,b,side,.045,.015,0xB9A06B,fade*.7F);
			FX.groundGlowLine(a,b,.12,MID.main(),fade*.55F);
		}
		return true;
	}

	private static void sporePod(BufferBuilder b, Matrix4f p, Vec3 at, double radius, float alpha) {
		if(radius<=0 || alpha<=0) return;
		// Squat cap and hanging gills, rather than identical diamond-shaped gems.
		Vec3 crown=at.add(0,radius*.62,0), stem=at.add(0,-radius*.8,0);
		for(int face=0;face<8;face++) {
			double a=face*Math.PI/4, next=(face+1)*Math.PI/4;
			Vec3 rimA=at.add(Math.cos(a)*radius,0,Math.sin(a)*radius);
			Vec3 rimB=at.add(Math.cos(next)*radius,0,Math.sin(next)*radius);
			int cap=face<3?0xF07AA8:face<6?0x9A2F63:0xC94A7E;
			triangle(b,p,crown,rimA,rimB,cap,alpha);
			triangle(b,p,stem,rimB,rimA,face%2==0?0xC9A98E:0xF2DCC8,alpha*.85F);
		}
		FX.billboard(MushroomFx.Tex.GLOW,at,frameCamera,radius*3.2,0,SPORE.main(),alpha*.5F);
	}

	private static float fade(float age) {
		return age<0 ? .95F : 1-MushroomEffectMotion.smooth((age-6)/10);
	}

	private static boolean renderClosingClaws(BufferBuilder b, Matrix4f p, Vec3 o, Vec3 f,
			Vec3 camera, float age, boolean ground) {
		Vec3 side=new Vec3(-f.z,0,f.x);
		float close=MushroomEffectMotion.strike(age,6), alpha=fade(age);
		double height=ground ? .12 : 1.8;
		Palette palette=ground ? LOW : MID;
		Vec3 center=o.add(f.scale(ground ? 2.2 : 2.65)).add(0,height,0);
		for(int sign:new int[]{-1,1}) {
			Vec3 a=o.add(f.scale(.55)).add(side.scale(sign*2)).add(0,height+.25,0);
			Vec3 tip=center.add(side.scale(sign*(1-close)*1.6));
			Vec3 prev=a;
			for(int i=1;i<=10;i++) {
				double t=i/10.0;
				Vec3 at=a.lerp(tip,t).add(side.scale(sign*.25*Math.sin(t*Math.PI)));
				rootSegment(b,p,prev,at,.32*(1-t)+.04,.27*(1-t)+.015,alpha);prev=at;
			}
			// Split, hooked claw tips; emission stays inside the bark rather than in a ball.
			for (int tooth=0; tooth<3; tooth++) {
				Vec3 hook=tip.add(f.scale(.24+tooth*.09)).add(side.scale(sign*tooth*.13))
						.add(0,(ground?.16:-.12)*(tooth+1),0);
				rootSegment(b,p,tip,hook,.075-tooth*.014,.004,alpha);
			}
			if(age<0) FX.trail(a,tip,camera,.12,palette.main(),close*.35F);
		}
		if(age>=0 && age<3) impactCross(b,p,center,camera,.9,palette,1-age/3);
		if(ground) shockwave(center.add(0,-.12,0),age,.36,3.8,11,palette.main(),.9F);
		else if(age>=0 && age<6) FX.billboard(MushroomFx.Tex.GLOW,center,camera,1.2+age*.35,0,palette.main(),(1-age/6)*.7F);
		return true;
	}

	private static boolean renderRisingRoot(BufferBuilder b, Matrix4f p, Vec3 o, Vec3 f,Vec3 camera,float age) {
		float progress=MushroomEffectMotion.strike(age,5), alpha=fade(age);
		Vec3 side=new Vec3(-f.z,0,f.x), prev=o.add(f.scale(.2)).add(side.scale(-.65)).add(0,.2,0);
		Vec3 start=prev;
		for(int i=1;i<=14;i++) {
			double t=i/14.0*progress;
			Vec3 at=o.add(f.scale(.2+3.05*t)).add(side.scale(-.65+1.1*t)).add(0,.2+2.4*t*t,0);
			rootSegment(b,p,prev,at,.24*(1-t)+.06,.23*(1-t)+.02,alpha);prev=at;
		}
		FX.billboard(MushroomFx.Tex.GLOW,prev,camera,.5+progress*.5,0,MID.main(),alpha*.8F);
		if(age>=0 && age<6) {
			FX.trail(start,prev,camera,.28,MID.main(),(1-age/6)*.8F);
			if(age<2) impactCross(b,p,prev,camera,.8,MID,1-age/2);
		}
		shockwave(start.add(0,-.2,0),age+2,.25,2.2,9,LOW.main(),.5F);
		return true;
	}

	private static boolean renderRam(BufferBuilder b, Matrix4f p,Vec3 o,Vec3 f,Vec3 camera,float age,boolean shoulder) {
		Vec3 side=new Vec3(-f.z,0,f.x);
		float progress=MushroomEffectMotion.strike(age,5), alpha=fade(age);
		Vec3 hub=o.add(f.scale(.9+progress*1.5)).add(0,shoulder?1.9:1.25,0);
		for(int i=-3;i<=3;i++) {
			Vec3 base=hub.add(side.scale(i*.22)).add(0,Math.sin(i*.8)*.24,0);
			rootSegment(b,p,base.subtract(f.scale(.7)),base,.18,.06,alpha);
			if(age>=0 && age<5) {
				energyRibbon(b,p,base.subtract(f.scale(1.1)),base,camera,.045,MID,alpha*.4F);
				FX.trail(base.subtract(f.scale(2.2)),base,camera,.1,MID.main(),alpha*.45F);
			}
		}
		if(age<0) FX.billboard(MushroomFx.Tex.GLOW,hub,camera,.8+progress*.6,0,MID.main(),progress*.55F);
		if(age>=0 && age<3) impactCross(b,p,hub,camera,.8,MID,1-age/3);
		// Dust ring kicked up where the boss plants its front foot.
		shockwave(o.add(f.scale(1.3)),age,.3,3.0,10,LOW.main(),.45F);
		return true;
	}

	private static boolean renderRootGarden(BufferBuilder b,Matrix4f p,Vec3 o,Vec3 f,Vec3 camera,
			float age,MushroomEffectStyle style,int beat,double advance) {
		Vec3 side=new Vec3(-f.z,0,f.x);
		Vec3 foot=o.add(f.scale(.75)).add(side.scale(style==MushroomEffectStyle.STOMP_LEFT ? .95 : -.95)).add(0,.06,0);
		if(age<0) {
			float charge=MushroomEffectMotion.smooth((age+7)/7);
			for(int root=0;root<5;root++) {
				double angle=root*2.399963;
				Vec3 outer=foot.add(Math.cos(angle)*.42,0,Math.sin(angle)*.42);
				Vec3 tip=outer.lerp(foot,charge);
				groundBranch(b,p,outer,tip,new Vec3(-Math.sin(angle),0,Math.cos(angle)),.025,.005,LOW.main(),.42F);
				FX.groundGlowLine(outer,tip,.09,LOW.main(),.5F*charge);
			}
			FX.ground(MushroomFx.Tex.GLOW,foot,.8+charge*.6,0,LOW.main(),.25F+charge*.35F,true);
			return true;
		}
		switch(style) {
			case STOMP_LEFT,STOMP_RIGHT -> shockwave(foot,age,.32,2.6,10,LOW.main(),.9F);
			case QUAKE -> {
				Vec3 center=o.add(f.scale(1.8));
				shockwave(center,age,.45,3.8,12,LOW.main(),1);
				shockwave(center,age-3,.4,3.2,11,LOW.core(),.6F);
				if(age<3) impactCross(b,p,center.add(0,.35,0),camera,1.1,LOW,1-age/3);
			}
			case ERUPTION -> {
				for(int burst=1;burst<=3;burst++) {
					Vec3 center=o.add(f.scale(1.2+burst*1.05)).add(side.scale((burst&1)==0?.48:-.48));
					shockwave(center,age-(burst-1)*1.4F,.3,1.6,9,SPORE.main(),.8F);
				}
			}
			case SURGE -> {
				Vec3 start=o.add(f.scale(advance));
				FX.groundGlowLine(start,start.add(f.scale(Math.min(4.7,age*.9+.4))),.55,LOW.main(),fade(age)*.6F);
			}
			default -> {
			}
		}
		int count=style==MushroomEffectStyle.SURGE ? 10 : 16;
		for(int i=0;i<count;i++) {
			float local=age-(i%4)*1.15F;
			if(local<0 || local>12) continue;
			Vec3 base;
			if(style==MushroomEffectStyle.SURGE) {
				base=o.add(f.scale(advance+i*.39)).add(side.scale((i%2==0?1:-1)*.36));
			} else if(style==MushroomEffectStyle.QUAKE) {
				double angle=i*Mth.TWO_PI/count, radius=.75+(i%3)*.35;
				base=o.add(f.scale(1.8)).add(Math.cos(angle)*radius,0,Math.sin(angle)*radius);
			} else if(style==MushroomEffectStyle.STOMP_LEFT || style==MushroomEffectStyle.STOMP_RIGHT) {
				double angle=i*Mth.TWO_PI/count;
				base=foot.add(Math.cos(angle)*.65,-.06,Math.sin(angle)*.65);
			} else {
				double angle=(i/15.0-.5)*1.1, radius=.7+(i%4)*.65;
				base=o.add(rotateY(f,Math.toDegrees(angle)).scale(radius));
			}
			double rise=MushroomEffectMotion.rootRise(local,12)*(.45+(i%3)*.22);
			if (rise < .005) continue;
			Vec3 tip=base.add(f.scale(.16*rise)).add(0,.02+rise,0);
			float rootFade=1-MushroomEffectMotion.smooth((local-8)/4);
			rootSegment(b,p,base.add(0,-.02,0),tip,.16,.01,rootFade);
			if(i%2==0) FX.billboard(MushroomFx.Tex.GLOW,tip,camera,.22,0,LOW.main(),rootFade*.55F);
			if(style==MushroomEffectStyle.ERUPTION) sporePod(b,p,tip.add(0,local*.045,0),.09,fade(age));
		}
		return true;
	}

	private static boolean renderClosingCanopy(BufferBuilder b,Matrix4f p,Vec3 o,Vec3 f,Vec3 camera,float age) {
		float close=MushroomEffectMotion.strike(age,7), alpha=fade(age);
		float recoil=MushroomEffectMotion.smooth((age-2)/6);
		double heading=Math.atan2(f.z,f.x);
		for(int rib=0;rib<11;rib++) {
			double angle=heading+(rib-5)*.16+Math.sin(rib*2.3)*.025;
			Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
			Vec3 tangent=new Vec3(-radial.z,0,radial.x);
			Vec3 at=o.add(radial.scale(.8)).add(0,4.25,0);
			for(int j=1;j<=9;j++) {
				double t=j/9.0;
				double radius=.8+t*(3.45-close*.55)-t*t*t*close*.45;
				double height=4.25+Math.sin(t*Math.PI)*(.45+rib%3*.06)
						-t*t*(.35+close*1.95-recoil*.6);
				Vec3 next=o.add(radial.scale(radius)).add(tangent.scale(Math.sin(t*Math.PI)*.09))
						.add(0,height,0);
				rootSegment(b,p,at,next,.20*(1-t)+.015,.17*(1-t)+.004,alpha);
				if(j>=3 && j<=7 && (j+rib)%2==0) {
					Vec3 gill=next.add(radial.scale(-.12))
							.add(0,-(.16+(rib%3)*.09)*(1+close*.6),0);
					rootSegment(b,p,next,gill,.035,.002,alpha);
					gradientTriangle(b,p,at,next,gill,0x53635F,alpha*.6F,alpha*.6F,0);
				}
				if(age>=0 && age<2 && j>=6) {
					FX.trail(next.add(0,.45,0),next,camera,.025,HIGH.main(),(1-age/2)*.4F);
				}
				at=next;
			}
			if(age>=0 && age<8) {
				Vec3 drop=at.add(radial.scale(age*.045)).add(0,-age*.025-age*age*.004,0);
				sporePod(b,p,drop,.055+(rib%3)*.012,alpha*.65F);
			}
		}
		if(age>=0 && age<1.5F) {
			impactCross(b,p,o.add(f.scale(3.2)).add(0,2.2,0),camera,.75,HIGH,1-age/1.5F);
		}
		return true;
	}

	private static boolean renderSporeRelease(BufferBuilder b,Matrix4f p,Vec3 o,Vec3 f,Vec3 camera,float age,boolean needles,int beat) {
		Vec3 side=new Vec3(-f.z,0,f.x), mouth=o.add(f.scale(.9)).add(0,2.75,0);
		float alpha=fade(age);
		if(age<0) {
			float charge=(age+7)/7;
			for(int i=0;i<6;i++) {
				double a=i*2.399963+beat*.4;
				Vec3 outer=mouth.add(side.scale(Math.cos(a)*.28*(1-charge*.65)))
						.add(0,Math.sin(a)*.22,0).subtract(f.scale(.24));
				Vec3 lip=mouth.add(side.scale(Math.cos(a)*.08)).add(0,Math.sin(a)*.07,0);
				rootSegment(b,p,outer,lip,.035,.003,.95F);
			}
		} else if(needles) {
			double length=MushroomAttackVolume.NEEDLE_LENGTH;
			for(int i=-1;i<=1;i++) {
				Vec3 head=mouth.add(f.scale(Math.min(length,age*2.1+1))).add(side.scale(i*.22));
				rootSegment(b,p,head.subtract(f.scale(.55)),head,.055,.005,alpha);
				energyRibbon(b,p,head.subtract(f.scale(1.2)),head,camera,.025,SPORE,alpha*.5F);
				FX.trail(head.subtract(f.scale(2.6)),head,camera,.035,SPORE.main(),alpha*.55F);
				rootSegment(b,p,head.subtract(f.scale(.3)),head.add(side.scale(i*.07)),.025,.002,alpha);
			}
			if(age<2) FX.billboard(MushroomFx.Tex.FLARE,mouth,camera,1.1,0,SPORE.main(),1-age/2);
		} else {
			// Muzzle only: travelling damaging pods are drawn by their entity renderer.
			for(int i=0;i<18;i++) {
				double a=i*2.399963, r=.08+age*.025+(i%3)*.045;
				Vec3 at=mouth.add(f.scale(.05+age*.09+(i%3)*.13))
						.add(side.scale(Math.cos(a)*r)).add(0,Math.sin(a)*r,0);
				sporePod(b,p,at,.035+(i%3)*.013,alpha*.7F);
			}
			for(int wisp=0;wisp<5;wisp++) {
				double a=wisp*2.399963+age*.12;
				Vec3 start=mouth.add(side.scale(Math.cos(a)*.12)).add(0,Math.sin(a)*.1,0);
				Vec3 end=start.add(f.scale(.35+age*.14)).add(side.scale(Math.cos(a)*age*.015))
						.add(0,Math.sin(a)*age*.018,0);
				FX.trail(start,end,camera,.025+(wisp%2)*.01,SPORE.main(),alpha*.2F);
			}
		}
		return true;
	}

	private static boolean renderConvergingGarden(BufferBuilder b,Matrix4f p,Vec3 o,Vec3 camera,float age) {
		// Actual server stage beats: 18,34,48,60. No invented safe wedge.
		for(int stage=0;stage<4;stage++) {
			float local=age+CONVERGENCE_OFFSETS[stage];
			if(local<0 || local>16) continue;
			double radius=stage==3 ? 18 : 4+stage*5;
			float stageFade=1-MushroomEffectMotion.smooth((local-11)/5);
			shockwave(o,local-2,(float)(radius/8),radius+1,9,stage==3?ULTIMATE.core():ULTIMATE.main(),.7F);
			for(int ray=0;ray<16;ray++) {
				double a=ray*Mth.TWO_PI/16;
				Vec3 base=o.add(Math.cos(a)*radius,.08,Math.sin(a)*radius);
				double rise=MushroomEffectMotion.rootRise(local,16);
				if (rise < .005) continue;
				double height=rise*(stage==3?1.65:.85);
				Vec3 inward=new Vec3(-Math.cos(a),0,-Math.sin(a));
				Vec3 bend=new Vec3(-Math.sin(a),0,Math.cos(a));
				Vec3 previous=base;
				for(int joint=1;joint<=5;joint++) {
					double t=joint/5.0;
					Vec3 next=base.add(inward.scale(t*t*rise*1.1))
							.add(bend.scale(Math.sin(t*Math.PI)*.18*rise*(ray%2==0?1:-1)))
							.add(0,Math.sin(t*Math.PI*.65)*height,0);
					rootSegment(b,p,previous,next,.22*(1-t)+.025,.20*(1-t)+.008,stageFade);
					previous=next;
				}
				if(ray%3==0) {
					rootSegment(b,p,previous,previous.add(bend.scale(.18*rise)).add(0,.14*rise,0),
							.025,.001,stageFade);
				}
			}
		}
		if(age<0) {
			// Twisted fungal cords draw inward; no giant spherical charge column.
			float charge=Mth.clamp((age+60)/60,0,1);
			for(int cord=0;cord<6;cord++) {
				double heading=cord*Math.PI/3;
				Vec3 previous=o.add(Math.cos(heading)*2.5,.12,Math.sin(heading)*2.5);
				for(int joint=1;joint<=8;joint++) {
					double t=joint/8.0, angle=heading+t*charge*.7, radius=2.5*(1-t)+.3;
					Vec3 next=o.add(Math.cos(angle)*radius,.12+t*t*charge*2.8,Math.sin(angle)*radius);
					rootSegment(b,p,previous,next,.035*(1-t)+.009,.025*(1-t)+.003,charge*.85F);
					if(joint>5) FX.trail(previous,next,camera,.015,ULTIMATE.main(),charge*.25F);
					previous=next;
				}
			}
		}
		if(age>=0 && age<3) impactCross(b,p,o.add(0,2.2,0),camera,1.8,ULTIMATE,1-age/3);
		shockwave(o,age,2.4,MushroomAttackVolume.CONVERGENCE_ARENA_RADIUS,12,ULTIMATE.core(),1);
		return true;
	}

	private static boolean renderRitual(BufferBuilder b, Matrix4f p,
			VirtualMushroomKing boss, Vec3 camera) {
		float partial=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		Vec3 o=boss.getPosition(partial), f=forward(boss.getYRot()), side=new Vec3(-f.z,0,f.x);
		boolean wrote=false;
		if(boss.getSummonTicks()>0) {
			float progress=Mth.clamp(1-(boss.getSummonTicks()-partial)/VirtualMushroomKing.SUMMON_DURATION,0,1);
			for(int i=0;i<12;i++) {
				double a=i*Mth.TWO_PI/12, radius=3.8-progress*1.5;
				Vec3 base=o.add(Math.cos(a)*radius,.05,Math.sin(a)*radius);
				Vec3 tip=base.add(-Math.cos(a)*progress*.55,Math.sin(progress*Math.PI)*1.4,-Math.sin(a)*progress*.55);
				rootSegment(b,p,base,tip,.14,.01,1-progress*.6F);
				sporePod(b,p,base.add(0,progress*3+(i%3)*.2,0),.06,1-progress);
			}
			float bloom=Mth.sin(progress*Mth.PI);
			FX.ground(MushroomFx.Tex.SIGIL,o.add(0,.08,0),4.2-progress*.8,progress*2.2,MushroomFx.MYCELIUM,.35F+bloom*.55F,true);
			FX.ground(MushroomFx.Tex.GLOW,o.add(0,.07,0),4.5,0,MushroomFx.MYCELIUM_DEEP,.4F*bloom,true);
			FX.billboard(MushroomFx.Tex.GLOW,o.add(0,2.6,0),camera,1.5+progress*2.5,0,MushroomFx.MYCELIUM,bloom*.4F);
			if(progress>.9F) shockwave(o,(progress-.9F)*80,.9,7,8,MushroomFx.BONE,1);
			wrote=true;
		}
		if(boss.getPlateBreakTicks()>0) {
			float age=16-boss.getPlateBreakTicks()+partial;
			Vec3 center=switch(boss.getBreakingPlate()) {
				case 0 -> o.add(side.scale(1.4)).add(0,2.8,0);
				case 1 -> o.add(side.scale(-1.4)).add(0,2.8,0);
				case 2 -> o.add(f.scale(.8)).add(0,2.05,0);
				default -> o.add(f.scale(-.8)).add(0,2.15,0);
			};
			for(int i=0;i<12;i++) {
				double a=i*2.399963, radius=.08+age*(.04+(i%3)*.015);
				Vec3 at=center.add(Math.cos(a)*radius,age*.065-age*age*.008,Math.sin(a)*radius);
				rootSegment(b,p,at,at.add(.07,.18+(i%3)*.06,.04),.12,.035,fade(age));
			}
			if(age<2) impactCross(b,p,center,camera,.6,MID,1-age/2);
			if(age<6) FX.billboard(MushroomFx.Tex.GLOW,center,camera,.8+age*.3,0,MushroomFx.AMBER,(1-age/6)*.8F);
			wrote=true;
		}
		if(boss.getStaggerTicks()>0) {
			float phase=(boss.tickCount+partial)*.07F;
			for(int i=0;i<7;i++) {
				double a=i*Mth.TWO_PI/7+phase;
				Vec3 at=o.add(Math.cos(a)*.9,3.5+(i%2)*.2,Math.sin(a)*.9);
				sporePod(b,p,at,.05,.42F);
				FX.billboard(MushroomFx.Tex.FLARE,at,camera,.18,phase*3+i,MushroomFx.AMBER_PALE,.5F);
			}
			wrote=true;
		}
		if(boss.deathTime>0) {
			float age=boss.deathTime+partial, progress=Mth.clamp(age/64,0,1);
			// Three stages: dim fragments, rising spores, sparse final dissipation.
			for(int i=0;i<24;i++) {
				double a=i*2.399963, radius=.35+progress*(1+(i%4)*.4);
				Vec3 at=o.add(Math.cos(a)*radius,1+(i%5)*.25+progress*3,Math.sin(a)*radius);
				float alpha=(1-progress)*(age<18?.28F:.72F);
				sporePod(b,p,at,.04+(i%3)*.025,alpha);
				if(age<22 && i%2==0) rootSegment(b,p,at.add(0,-progress*2,0),at.add(.08,-progress*2+.2,0),.12,.02,1-progress*2);
			}
			FX.billboard(MushroomFx.Tex.GLOW,o.add(0,2+progress*2,0),camera,2+progress*3,0,MushroomFx.MYCELIUM,(1-progress)*.5F);
			FX.ground(MushroomFx.Tex.SIGIL,o.add(0,.08,0),3.5+progress*2,-age*.04,MushroomFx.MYCELIUM,(1-progress)*.7F,true);
			wrote=true;
		}
		return wrote;
	}


	private static Vec3 forward(float yawDegrees) {
		double yaw = Math.toRadians(yawDegrees);
		return new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
	}

	private static Vec3 rotateY(Vec3 direction, double degrees) {
		double radians = Math.toRadians(degrees);
		double cos = Math.cos(radians);
		double sin = Math.sin(radians);
		return new Vec3(direction.x * cos - direction.z * sin, 0.0,
				direction.x * sin + direction.z * cos);
	}

	private static void energyRibbon(BufferBuilder buffer, Matrix4f pose, Vec3 from, Vec3 to,
			Vec3 camera, double width, Palette palette, float alpha) {
		Vec3 axis = to.subtract(from);
		if (axis.lengthSqr() < 1.0E-7 || alpha <= 0) return;
		Vec3 direction = axis.normalize();
		Vec3 side = direction.cross(camera.subtract(from.add(to).scale(.5)));
		if (side.lengthSqr() < 1.0E-7) {
			side = direction.cross(Math.abs(direction.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0));
		}
		side = side.normalize();
		// The curve depends on normalized progress, not world coordinates: no frame-to-frame flicker.
		for (int i=0; i<12; i++) {
			double t0=i/12.0, t1=(i+1)/12.0;
			Vec3 a=from.add(axis.scale(t0)).add(side.scale(MushroomEffectMotion.ribbonBend(t0)*width*2));
			Vec3 b=from.add(axis.scale(t1)).add(side.scale(MushroomEffectMotion.ribbonBend(t1)*width*2));
			double w0=width*MushroomEffectMotion.ribbonWidth(t0), w1=width*MushroomEffectMotion.ribbonWidth(t1);
			// Vertex alpha falls to zero at both edges, not three opaque rectangular strips.
			for (int sign : new int[]{-1,1}) {
				Vec3 edgeA=a.add(side.scale(sign*w0*2.4)), edgeB=b.add(side.scale(sign*w1*2.4));
				gradientTriangle(buffer,pose,a,b,edgeB,palette.main(),alpha*.45F,alpha*.45F,0);
				gradientTriangle(buffer,pose,a,edgeB,edgeA,palette.main(),alpha*.45F,0,0);
			}
			quad(buffer,pose,a.add(side.scale(w0*.23)),b.add(side.scale(w1*.23)),
					b.subtract(side.scale(w1*.23)),a.subtract(side.scale(w0*.23)),palette.core(),alpha*.8F);
		}
		FX.trail(from,to,camera,width*5,palette.main(),alpha);
	}

	private static void impactCross(BufferBuilder buffer, Matrix4f pose, Vec3 center,
			Vec3 camera, double radius, Palette palette, float alpha) {
		if (alpha<=0) return;
		Vec3 view=camera.subtract(center).normalize();
		Vec3 side=view.cross(new Vec3(0,1,0));
		if(side.lengthSqr()<1.0E-7) side=new Vec3(1,0,0);
		side=side.normalize();
		Vec3 up=side.cross(view).normalize();
		// A short asymmetric contact burst. No circular "target" pasted over the boss.
		for(int ray=0;ray<9;ray++) {
			double angle=ray*2.399963;
			double reach=radius*(ray%3==0?1:.38+(ray%4)*.10)*(1.35-.35*alpha);
			Vec3 direction=side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
			Vec3 across=side.scale(-Math.sin(angle)).add(up.scale(Math.cos(angle)));
			Vec3 tip=center.add(direction.scale(reach));
			Vec3 left=center.add(across.scale(radius*.075*alpha));
			Vec3 right=center.subtract(across.scale(radius*.075*alpha));
			gradientTriangle(buffer,pose,left,tip,right,palette.main(),alpha*.75F,0,alpha*.75F);
			gradientTriangle(buffer,pose,center.add(across.scale(radius*.019)),
					tip,center.subtract(across.scale(radius*.019)),palette.core(),alpha,0,alpha);
		}
		// A compact, fast hot core. The dark debris carries the weight, not screen-filling bloom.
		FX.billboard(MushroomFx.Tex.FLARE,center,camera,radius*.42,.4,palette.core(),alpha*alpha*.8F);
	}

	private static void groundBranch(BufferBuilder buffer, Matrix4f pose, Vec3 start, Vec3 end,
			Vec3 side, double startWidth, double endWidth, int color, float alpha) {
		quad(buffer, pose, start.add(side.scale(startWidth)), end.add(side.scale(endWidth)),
				end.add(side.scale(-endWidth)), start.add(side.scale(-startWidth)), color, alpha);
	}

	private static void gradientTriangle(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c,
			int color, float alphaA, float alphaB, float alphaC) {
		vertex(buffer,pose,a,color,alphaA);
		vertex(buffer,pose,b,color,alphaB);
		vertex(buffer,pose,c,color,alphaC);
	}

	private static void triangle(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c,
			int color, float alpha) {
		vertex(buffer, pose, a, color, alpha);
		vertex(buffer, pose, b, color, alpha);
		vertex(buffer, pose, c, color, alpha);
	}

	private static void quad(BufferBuilder buffer, Matrix4f pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
			int color, float alpha) {
		triangle(buffer, pose, a, b, c, color, alpha);
		triangle(buffer, pose, a, c, d, color, alpha);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f pose, Vec3 point,
			int color, float alpha) {
		buffer.addVertex(pose, (float)point.x, (float)point.y, (float)point.z)
				.setColor((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF,
						Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F));
	}

	private record Palette(int core, int main, int dark) {
	}
}
