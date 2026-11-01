package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Блок-сущность приёмника активатора.
 *
 * <p>Хранит предметный фильтр. Пустой фильтр означает «принимать всех».
 * Если фильтр задан, приёмник срабатывает только тогда, когда у игрока
 * есть такой предмет в инвентаре или в контейнере поблизости.</p>
 */
public class ActivatorReceiverBlockEntity extends BlockEntity {
	private static final String FILTER_KEY = "Filter";
	private static final int CONTAINER_RADIUS = 8;

	private ItemStack filter = ItemStack.EMPTY;

	public ActivatorReceiverBlockEntity(BlockPos pos, BlockState state) {
		super(FirstMod.ACTIVATOR_RECEIVER_BE, pos, state);
	}

	public ItemStack getFilter() {
		return this.filter;
	}

	public void setFilter(ItemStack filter) {
		this.filter = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
		setChanged();
	}

	/**
	 * Может ли этот приёмник сработать для указанного игрока.
	 */
	public boolean accepts(Level level, Player player) {
		if (this.filter.isEmpty()) {
			return true;
		}

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (!stack.isEmpty() && ItemStack.isSameItem(stack, this.filter)) {
				return true;
			}
		}

		BlockPos pos = getBlockPos();
		BlockPos min = pos.offset(-CONTAINER_RADIUS, -4, -CONTAINER_RADIUS);
		BlockPos max = pos.offset(CONTAINER_RADIUS, 4, CONTAINER_RADIUS);

		for (BlockPos candidate : BlockPos.betweenClosed(min, max)) {
			if (candidate.equals(pos)) {
				continue;
			}

			BlockEntity blockEntity = level.getBlockEntity(candidate);
			if (!(blockEntity instanceof Container container)) {
				continue;
			}

			for (int slot = 0; slot < container.getContainerSize(); slot++) {
				ItemStack stack = container.getItem(slot);
				if (!stack.isEmpty() && ItemStack.isSameItem(stack, this.filter)) {
					return true;
				}
			}
		}

		return false;
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);

		if (!this.filter.isEmpty()) {
			output.store(FILTER_KEY, ItemStack.OPTIONAL_CODEC, this.filter);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.filter = input.read(FILTER_KEY, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}
}
