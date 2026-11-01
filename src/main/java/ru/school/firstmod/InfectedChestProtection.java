package ru.school.firstmod;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Защита Заражённого сундука от «неправильной» добычи.
 *
 * <p>Сундук можно разрушить только киркой железного уровня или выше,
 * зачарованной на «Шёлковое касание». Проверка выполняется в трёх
 * местах: при начале разрушения (левый клик), непосредственно перед
 * разрушением блока и в расчёте скорости разрушения. Благодаря этому
 * неверный инструмент не может ни сломать сундук, ни выбросить его
 * содержимое, ни создать дубликаты.</p>
 */
public final class InfectedChestProtection {
	private InfectedChestProtection() {
	}

	public static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			BlockState state = level.getBlockState(pos);

			if (!state.is(FirstMod.INFECTED_CHEST)) {
				return InteractionResult.PASS;
			}
			if (canHarvest(player.getItemInHand(hand), state)) {
				return InteractionResult.PASS;
			}

			return InteractionResult.FAIL;
		});

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!state.is(FirstMod.INFECTED_CHEST)) {
				return true;
			}

			return canHarvest(player.getMainHandItem(), state);
		});
	}

	/**
	 * Проверяет, что инструмент одновременно является правильным для
	 * блока (железо или лучше) и имеет «Шёлковое касание».
	 */
	public static boolean canHarvest(ItemStack tool, BlockState state) {
		if (tool.isEmpty() || !tool.isCorrectToolForDrops(state)) {
			return false;
		}

		ItemEnchantments enchantments = tool.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null || enchantments.isEmpty()) {
			return false;
		}

		for (Holder<Enchantment> enchantment : enchantments.keySet()) {
			if (enchantment.is(Enchantments.SILK_TOUCH)) {
				return true;
			}
		}

		return false;
	}
}
