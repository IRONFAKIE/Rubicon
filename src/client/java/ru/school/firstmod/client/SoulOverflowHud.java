package ru.school.firstmod.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import ru.school.firstmod.FirstMod;

/**
 * Полоса «Душевного переполнения» над ванильной шкалой опыта.
 *
 * <p>Полоса сделана в стиле ванильного опыта: шесть узких делений,
 * разделённых прозрачными промежутками. Пустые деления не рисуются,
 * фон и рамка отсутствуют. При нулевом значении элемент полностью
 * скрыт. Ширина и позиция рассчитаны в GUI-координатах, поэтому полоса
 * корректно выглядит при любом разрешении и масштабе интерфейса.</p>
 */
public class SoulOverflowHud implements HudElement {
	private static final int SEGMENTS = FirstMod.SOUL_OVERFLOW_MAX;
	private static final int SEGMENT_WIDTH = 29;
	private static final int SEGMENT_HEIGHT = 1;
	private static final int GAP = 1;
	private static final int TOTAL_WIDTH = SEGMENTS * SEGMENT_WIDTH + (SEGMENTS - 1) * GAP;

	/**
	 * Ванильная шкала опыта занимает строки {@code guiHeight - 29 … - 25}.
	 * Строка {@code guiHeight - 30} — единственная свободная полная
	 * строка прямо над ней, между шкалой опыта и здоровьем/голодом.
	 */
	private static final int BOTTOM_OFFSET = 30;

	private static final int FILLED_COLOR = 0xFF35D6FF;

	@Override
	public void extractRenderState(GuiGraphicsExtractor gui, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Player player = minecraft.player;

		if (player == null || player.isSpectator()) {
			return;
		}

		int filled = Math.max(0, Math.min(
				player.getAttachedOrElse(FirstMod.SOUL_OVERFLOW, 0),
				SEGMENTS
		));

		// При нуле не рисуем вообще ничего: ни фона, ни пустых делений.
		if (filled == 0) {
			return;
		}

		int left = (gui.guiWidth() - TOTAL_WIDTH) / 2;
		int top = gui.guiHeight() - BOTTOM_OFFSET;

		for (int index = 0; index < filled; index++) {
			int x = left + index * (SEGMENT_WIDTH + GAP);

			gui.fill(
					x,
					top,
					x + SEGMENT_WIDTH,
					top + SEGMENT_HEIGHT,
					FILLED_COLOR
			);
		}
	}
}
