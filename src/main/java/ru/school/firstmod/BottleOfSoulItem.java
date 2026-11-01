package ru.school.firstmod;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Бутылка с душой.
 *
 * <p>Выпивается как обычный напиток: 1,6 секунды (32 тика), анимация
 * питья и только по завершении — одно деление «Душевного переполнения»
 * и Тьма на 3 секунды. Сама бутылка при этом не тратится в начале
 * использования. Также служит топливом для печи с двойной скоростью.</p>
 */
public class BottleOfSoulItem extends Item {
	public BottleOfSoulItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		int current = ((AttachmentTarget) player).getAttachedOrElse(FirstMod.SOUL_OVERFLOW, 0);

		if (current >= FirstMod.SOUL_OVERFLOW_MAX) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(
						Component.translatable("message.first-mod.soul_overflow_full")
				);
			}
			return InteractionResult.FAIL;
		}

		// Запускает стандартное питьё из компонента CONSUMABLE.
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player) {
			int current = ((AttachmentTarget) player).getAttachedOrElse(FirstMod.SOUL_OVERFLOW, 0);

			if (current < FirstMod.SOUL_OVERFLOW_MAX) {
				int next = current + 1;
				((AttachmentTarget) player).setAttached(FirstMod.SOUL_OVERFLOW, next);

				player.sendOverlayMessage(
						Component.translatable(
								"message.first-mod.soul_overflow",
								next,
								FirstMod.SOUL_OVERFLOW_MAX
						)
				);
			}
		}

		// Стандартное употребление: уменьшает стопку и применяет Тьму.
		return super.finishUsingItem(stack, level, entity);
	}
}
