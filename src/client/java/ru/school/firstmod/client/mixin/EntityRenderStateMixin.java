package ru.school.firstmod.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import ru.school.firstmod.client.SoulFireRenderState;

/** Добавляет к состоянию отрисовки флаг «горит синим огнём душ». */
@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements SoulFireRenderState {
	@Unique
	private boolean firstmod$soulFire;

	@Override
	public boolean firstmod$isSoulFire() {
		return this.firstmod$soulFire;
	}

	@Override
	public void firstmod$setSoulFire(boolean value) {
		this.firstmod$soulFire = value;
	}
}
