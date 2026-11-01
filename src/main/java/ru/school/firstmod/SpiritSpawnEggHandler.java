package ru.school.firstmod;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Яйцо призыва Духа Лавомерки.
 *
 * <p>Ванильная Лавомерка при использовании яйца на мобе создаёт детёныша
 * обычной Лавомерки. Перехватываем это действие и всегда создаём детёныша
 * именно Духа Лавомерки.</p>
 */
public final class SpiritSpawnEggHandler {
	private SpiritSpawnEggHandler() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(SpiritSpawnEggHandler::interact);
	}

	private static InteractionResult interact(
			Player player,
			Level level,
			InteractionHand hand,
			Entity entity,
			EntityHitResult hitResult
	) {
		ItemStack held = player.getItemInHand(hand);

		if (!held.is(FirstMod.SOUL_STRIDER_SPIRIT_SPAWN_EGG)) {
			return InteractionResult.PASS;
		}
		// Дух наследует Strider, поэтому проверка покрывает и его,
		// и обычную Лавомерку.
		if (!(entity instanceof Strider) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		// На клиенте достаточно взмаха рукой: сервер сам создаст детёныша.
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		ServerLevel serverLevel = (ServerLevel) level;
		SoulStriderSpirit baby = FirstMod.SOUL_STRIDER_SPIRIT.create(
				serverLevel,
				EntitySpawnReason.SPAWN_ITEM_USE
		);

		if (baby == null) {
			return InteractionResult.FAIL;
		}

		baby.setBaby(true);
		baby.snapTo(entity.getX(), entity.getY(), entity.getZ(), 0.0F, 0.0F);
		baby.applyComponentsFromItemStack(held);

		if (entity instanceof SoulStriderSpirit spirit) {
			baby.setLakeKey(spirit.getLakeKey());
		}

		if (!serverLevel.tryAddFreshEntityWithPassengers(baby)) {
			return InteractionResult.FAIL;
		}

		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}

		serverLevel.sendParticles(
				ParticleTypes.SOUL,
				baby.getX(), baby.getY() + 0.35D, baby.getZ(),
				14, 0.3D, 0.35D, 0.3D, 0.01D
		);
		entity.playSound(SoundEvents.STRIDER_HAPPY, 1.0F, 1.15F);

		return InteractionResult.SUCCESS;
	}
}
