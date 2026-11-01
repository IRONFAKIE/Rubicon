package ru.school.firstmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Активатор — беспроводная красная кнопка.
 *
 * <p>Обычный активатор является заготовкой. После добавления
 * волшебного рубина в crafting он превращается в активированный
 * активатор: правый клик запускает все приёмники в радиусе 24 блоков,
 * которые принимают предметный фильтр игрока. Приседание при клике по
 * приёмнику оставляет клик ему — так задаётся фильтр.</p>
 */
public class ActivatorItem extends Item {
	private final boolean activated;

	public ActivatorItem(boolean activated, Properties properties) {
		super(properties);
		this.activated = activated;
	}

	public boolean isActivated() {
		return this.activated;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!this.activated) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(
						Component.translatable("message.first-mod.activator.inactive")
								.withStyle(ChatFormatting.GRAY)
				);
			}

			return InteractionResult.SUCCESS;
		}

		if (!level.isClientSide()) {
			int count = ActivatorReceiverBlock.pulseNearby(level, player);
			player.sendOverlayMessage(
					count > 0
							? Component.translatable("message.first-mod.activator.sent", count)
							: Component.translatable("message.first-mod.activator.none")
									.withStyle(ChatFormatting.GRAY)
			);
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();

		// Приседание оставляем блоку: так приёмник получает фильтр.
		if (!this.activated || player == null || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}

		return use(context.getLevel(), player, context.getHand());
	}
}
