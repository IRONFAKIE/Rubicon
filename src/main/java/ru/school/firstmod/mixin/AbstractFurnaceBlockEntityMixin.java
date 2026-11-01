package ru.school.firstmod.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import ru.school.firstmod.FirstMod;
import ru.school.firstmod.SoulFurnaceAccess;

/**
 * Не даёт получить расплавы ни на каком топливе, кроме Бутылки с душой.
 *
 * <p>Проверка выполняется в момент, когда ванильная печь собирается
 * поджечь новую порцию топлива. Если результат плавки — расплав, а топливо
 * не бутылка с душой, длительность горения принудительно обнуляется:
 * печь не разжигается, топливо не тратится, прогресс не растёт. Поэтому
 * обойти ограничение углём, лавой, автоматизацией или перезагрузкой чанка
 * нельзя.</p>
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin implements SoulFurnaceAccess {
	@Shadow
	protected NonNullList<ItemStack> items;

	@Shadow
	private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

	@Shadow
	private int cookingTimer;

	@Unique
	private boolean firstmod$soulFuel;

	@Override
	public boolean firstmod$isSoulFuel() {
		return this.firstmod$soulFuel;
	}

	@Override
	public void firstmod$setSoulFuel(boolean value) {
		this.firstmod$soulFuel = value;
	}

	@Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true)
	private void firstmod$onlySoulFuelSmeltsMelts(
			ServerLevel level,
			ItemStack fuel,
			CallbackInfoReturnable<Integer> callback
	) {
		boolean soulFuel = !fuel.isEmpty() && fuel.is(FirstMod.BOTTLE_OF_SOUL);
		this.firstmod$soulFuel = soulFuel;

		if (soulFuel) {
			return;
		}

		ItemStack input = this.items.get(0);
		if (input.isEmpty()) {
			return;
		}

		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		var recipe = this.quickCheck.getRecipeFor(recipeInput, level);
		if (recipe.isEmpty()) {
			return;
		}

		AbstractCookingRecipe cooking = recipe.get().value();
		if (FirstMod.isMelt(cooking.assemble(recipeInput))) {
			callback.setReturnValue(0);
		}
	}

	/**
	 * Страховка от смены топлива или входа прямо во время горения.
	 *
	 * <p>Если в печи лежит предмет, плавящийся в расплав, а текущая порция
	 * топлива — не бутылка с душой, прогресс плавки обнуляется каждый тик.
	 * Так расплав нельзя получить, подложив уголь до или после того, как
	 * в печь попал блок, и автоматизация/перезагрузка чанка тоже не помогают.</p>
	 */
	@Inject(method = "serverTick", at = @At("HEAD"))
	private static void firstmod$resetMeltProgress(
			ServerLevel level,
			BlockPos pos,
			BlockState state,
			AbstractFurnaceBlockEntity furnace,
			CallbackInfo callback
	) {
		AbstractFurnaceBlockEntityMixin self = (AbstractFurnaceBlockEntityMixin) (Object) furnace;

		if (self.firstmod$soulFuel) {
			return;
		}

		ItemStack input = self.items.get(0);
		if (input.isEmpty()) {
			return;
		}

		SingleRecipeInput recipeInput = new SingleRecipeInput(input);
		var recipe = self.quickCheck.getRecipeFor(recipeInput, level);
		if (recipe.isEmpty()) {
			return;
		}

		if (FirstMod.isMelt(recipe.get().value().assemble(recipeInput))) {
			self.cookingTimer = 0;
		}
	}

	@Inject(method = "saveAdditional", at = @At("TAIL"))
	private void firstmod$saveSoulFuel(ValueOutput output, CallbackInfo callback) {
		output.putBoolean("FirstModSoulFuel", this.firstmod$soulFuel);
	}

	@Inject(method = "loadAdditional", at = @At("TAIL"))
	private void firstmod$loadSoulFuel(ValueInput input, CallbackInfo callback) {
		this.firstmod$soulFuel = input.getBooleanOr("FirstModSoulFuel", false);
	}
}
