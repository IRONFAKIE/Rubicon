package ru.school.firstmod;

import java.util.List;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Боевая часть «Душевного переполнения».
 *
 * <p>Любой удар расходует все накопленные деления и применяет эффекты
 * соответствующего уровня — от обычного поджога до трёх блуждающих душ
 * и мощного отбрасывания.</p>
 */
public final class SoulOverflowHandler {
	/** Сколько тиков цель горит именно синим огнём душ. */
	private static final int SOUL_FIRE_TICKS = 100;

	private SoulOverflowHandler() {
	}

	public static void init() {
		AttackEntityCallback.EVENT.register(SoulOverflowHandler::onAttack);
	}

	private static InteractionResult onAttack(
			net.minecraft.world.entity.player.Player player,
			Level level,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer serverPlayer) || !(entity instanceof LivingEntity target)) {
			return InteractionResult.PASS;
		}
		if (target == player || !target.isAlive()) {
			return InteractionResult.PASS;
		}

		int segments = ((AttachmentTarget) player).getAttachedOrElse(FirstMod.SOUL_OVERFLOW, 0);
		if (segments <= 0) {
			return InteractionResult.PASS;
		}

		// Любой удар расходует абсолютно все деления.
		((AttachmentTarget) player).setAttached(FirstMod.SOUL_OVERFLOW, 0);
		applyEffects(serverPlayer, target, Math.min(segments, FirstMod.SOUL_OVERFLOW_MAX));
		return InteractionResult.PASS;
	}

	private static void applyEffects(ServerPlayer player, LivingEntity target, int segments) {
		ServerLevel level = player.level();
		float attackDamage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);

		// 1 деление — обычное пламя.
		target.igniteForTicks(100);

		if (segments >= 2) {
			// 2 деления — синее пламя душ и иссушение.
			igniteWithSoulFire(level, target);
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 0));
			soulFlames(level, target, 24);
		}

		if (segments >= 3) {
			// 3 деления — гарантированный критический удар.
			player.crit(target);
			target.hurtServer(level, player.damageSources().playerAttack(player), attackDamage * 0.5F);
			soulFlames(level, target, 18);
		}

		if (segments >= 4) {
			int souls = segments >= FirstMod.SOUL_OVERFLOW_MAX ? 3 : 1;
			for (int i = 0; i < souls; i++) {
				spawnSoul(level, player, target, i);
			}
		}

		if (segments >= 5) {
			applySoulSplash(level, player, target, attackDamage);
		}

		if (segments >= FirstMod.SOUL_OVERFLOW_MAX) {
			// 6 делений — 75% шанс отбросить цель на большое расстояние.
			if (player.getRandom().nextFloat() < 0.75F) {
				Vec3 direction = target.position().subtract(player.position());
				if (direction.lengthSqr() < 0.001D) {
					direction = player.getLookAngle();
				}
				direction = new Vec3(direction.x, 0.0D, direction.z).normalize();
				// Импульс ~2.1 блока/тик с учётом трения даёт отлёт примерно на 8 блоков.
				target.push(direction.x * 2.1D, 0.55D, direction.z * 2.1D);
				soulFlames(level, target, 30);
			}
		}
	}

	private static void applySoulSplash(ServerLevel level, ServerPlayer player, LivingEntity target, float attackDamage) {
		AABB area = target.getBoundingBox().inflate(5.0D);
		List<LivingEntity> nearby = level.getEntitiesOfClass(
				LivingEntity.class,
				area,
				candidate -> candidate != player && candidate != target && candidate.isAlive()
		);

		float splashDamage = attackDamage * 0.5F;
		for (LivingEntity victim : nearby) {
			victim.hurtServer(level, player.damageSources().playerAttack(player), splashDamage);
			igniteWithSoulFire(level, victim);
			soulFlames(level, victim, 8);
		}

		// Двойной «душевный» сплеш по самой цели.
		target.hurtServer(level, player.damageSources().playerAttack(player), attackDamage * 0.25F);
		soulFlames(level, target, 20);
	}

	private static void spawnSoul(ServerLevel level, ServerPlayer player, LivingEntity target, int index) {
		WanderingSoul soul = FirstMod.WANDERING_SOUL.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (soul == null) {
			return;
		}

		double angle = (Math.PI * 2.0D / 3.0D) * index + player.getRandom().nextDouble() * 0.5D;
		double x = target.getX() + Math.cos(angle) * 1.4D;
		double z = target.getZ() + Math.sin(angle) * 1.4D;
		soul.snapTo(x, target.getY() + 1.2D, z, player.getRandom().nextFloat() * 360.0F, 0.0F);
		soul.setTargetUuid(target.getUUID());
		level.addFreshEntity(soul);
	}

	private static void igniteWithSoulFire(ServerLevel level, LivingEntity target) {
		target.igniteForTicks(SOUL_FIRE_TICKS);
		((AttachmentTarget) target).setAttached(
				FirstMod.SOUL_FIRE_UNTIL,
				level.getGameTime() + SOUL_FIRE_TICKS
		);
	}

	private static void soulFlames(ServerLevel level, LivingEntity entity, int count) {
		level.sendParticles(
				ParticleTypes.SOUL_FIRE_FLAME,
				entity.getX(),
				entity.getY() + entity.getBbHeight() * 0.5D,
				entity.getZ(),
				count,
				0.35D,
				0.45D,
				0.35D,
				0.02D
		);
	}
}
