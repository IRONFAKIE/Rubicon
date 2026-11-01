package ru.school.firstmod;

import java.util.function.BiConsumer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Заражённый сундук — сундук с рубиновыми наростами.
 *
 * <p>Сундук можно добыть только киркой железного уровня или выше
 * с зачарованием «Шёлковое касание». Все остальные попытки
 * разрушения отменяются отдельным обработчиком событий, а
 * {@link #getDestroyProgress} не даёт даже начать разрушение.</p>
 *
 * <p>Взрывы и поршни не могут уничтожить или переместить сундук:
 * он не реагирует на взрывы и имеет реакцию {@code IMMOVEABLE}.</p>
 */
public class InfectedChestBlock extends ChestBlock {
	public InfectedChestBlock(Properties properties) {
		super(
				() -> FirstMod.INFECTED_CHEST_BE,
				SoundEvents.CHEST_OPEN,
				SoundEvents.CHEST_CLOSE,
				properties
		);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new InfectedChestBlockEntity(pos, state);
	}

	/**
	 * Заражённый сундук всегда одиночный: это упрощает модель и
	 * исключает потерю лута при образовании двойного сундука.
	 */
	@Override
	public boolean chestCanConnectTo(BlockState state) {
		return false;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (!InfectedChestProtection.canHarvest(player.getMainHandItem(), state)) {
			return 0.0F;
		}

		return super.getDestroyProgress(state, player, level, pos);
	}

	/**
	 * Взрыв не должен ни разрушать сундук, ни выбрасывать его содержимое.
	 */
	@Override
	protected void onExplosionHit(
			BlockState state,
			ServerLevel level,
			BlockPos pos,
			Explosion explosion,
			BiConsumer<ItemStack, BlockPos> dropConsumer
	) {
		// Намеренно ничего не делаем.
	}

	@Override
	public boolean dropFromExplosion(Explosion explosion) {
		return false;
	}
}
