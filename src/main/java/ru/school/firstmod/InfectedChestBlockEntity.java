package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-сущность Заражённого сундука.
 *
 * <p>Использует стандартное поведение сундука, но получает
 * собственную лут-таблицу и собственное название интерфейса.</p>
 */
public class InfectedChestBlockEntity extends ChestBlockEntity {
	public InfectedChestBlockEntity(BlockPos pos, BlockState state) {
		super(FirstMod.INFECTED_CHEST_BE, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.first-mod.infected_chest");
	}
}
