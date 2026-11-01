package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import ru.school.firstmod.SoulLavaBounds;

/**
 * Сообщает утилитам границы снимка секций, доступного при компиляции
 * меша. Регион содержит ровно 3×3×3 секции, и обращение за его пределы
 * в ванильном коде приводит к {@code ArrayIndexOutOfBoundsException}.
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin implements SoulLavaBounds {
	@Shadow
	@Final
	private int minSectionX;

	@Shadow
	@Final
	private int minSectionY;

	@Shadow
	@Final
	private int minSectionZ;

	@Shadow
	@Final
	private SectionCopy[] sections;

	@Override
	public boolean containsPos(BlockPos pos) {
		int size = Math.round((float) Math.cbrt(this.sections.length));
		int sectionX = SectionPos.blockToSectionCoord(pos.getX());
		int sectionY = SectionPos.blockToSectionCoord(pos.getY());
		int sectionZ = SectionPos.blockToSectionCoord(pos.getZ());

		return sectionX >= this.minSectionX && sectionX < this.minSectionX + size
				&& sectionY >= this.minSectionY && sectionY < this.minSectionY + size
				&& sectionZ >= this.minSectionZ && sectionZ < this.minSectionZ + size;
	}
}
