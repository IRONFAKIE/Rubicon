package ru.school.firstmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Дух Лавомерки — неподвижный и неуязвимый дух, живущий в лавовых озёрах
 * Долины песка душ.
 *
 * <p>Моб наследует ванильную Лавомерку и использует её модели и анимации,
 * поэтому визуально остаётся узнаваемым Страйдером. Отличаются только
 * сине-бирюзовые текстуры.</p>
 *
 * <p>ИИ полностью отключён: моб не ходит, не поворачивается к цели и не
 * поддаётся гравитации или течениям лавы. Урон по нему игнорируется.</p>
 *
 * <p>ПКМ тонированной бутылкой превращает моба в предмет
 * {@code first-mod:bottle_of_soul}, удаляет духа из мира и накладывает
 * на игрока Тьму на 8 секунд.</p>
 */
public class SoulStriderSpirit extends Strider {
	/** Стабильный ключ озера, к которому привязан дух (для перезарядки). */
	private String lakeKey = "";

	public SoulStriderSpirit(EntityType<? extends SoulStriderSpirit> type, Level level) {
		super(type, level);

		// Никакого ИИ, гравитации и внешних толчков.
		this.setNoAi(true);
		this.setNoGravity(true);
		this.setPermanentlyInvulnerable(true);
		this.setPersistenceRequired();
		this.setDeltaMovement(Vec3.ZERO);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Strider.createAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.ARMOR, 30.0D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
	}

	@Override
	protected void registerGoals() {
		// Намеренно пусто: у духа нет ни одной цели ИИ.
	}

	@Override
	protected boolean isImmobile() {
		return true;
	}

	@Override
	public void travel(Vec3 movement) {
		// Полностью блокируем любое перемещение, включая всплытие в лаве.
		this.setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canCollideWith(Entity entity) {
		return false;
	}

	@Override
	public void push(Entity entity) {
		// Духа нельзя сдвинуть сущностями.
	}

	@Override
	public void push(double x, double y, double z) {
		// Духа нельзя сдвинуть взрывами, поршнями и прочими толчками.
	}

	@Override
	public boolean hurtServer(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
		return true;
	}

	@Override
	public boolean isInvulnerable() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	/** Ключ озера, в котором дух был рождён. */
	public String getLakeKey() {
		return this.lakeKey;
	}

	public void setLakeKey(String lakeKey) {
		this.lakeKey = lakeKey == null ? "" : lakeKey;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (!this.lakeKey.isEmpty()) {
			output.putString("FirstModLakeKey", this.lakeKey);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.lakeKey = input.getStringOr("FirstModLakeKey", "");
	}

	/**
	 * Не даём ванильной Лавомерке заспавнить наездника-зомби и закрепляем
	 * духа в мире, чтобы он не исчезал при удалении игрока.
	 */
	@Override
	public SpawnGroupData finalizeSpawn(
			ServerLevelAccessor level,
			DifficultyInstance difficulty,
			EntitySpawnReason reason,
			SpawnGroupData spawnData
	) {
		this.setPersistenceRequired();
		return spawnData;
	}

	/**
	 * Потомок духа — всегда Дух Лавомерки, а не ванильная Лавомерка.
	 *
	 * <p>Ванильная Лавомерка возвращает здесь {@code EntityType.STRIDER},
	 * из-за чего яйцо призыва духа превращалось в детёныша обычной
	 * Лавомерки. Возвращаем собственный тип.</p>
	 */
	@Override
	public SoulStriderSpirit getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
		SoulStriderSpirit baby = FirstMod.SOUL_STRIDER_SPIRIT.create(level, EntitySpawnReason.BREEDING);

		if (baby != null && otherParent instanceof SoulStriderSpirit spirit) {
			baby.setLakeKey(spirit.getLakeKey());
		}

		return baby;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack held = player.getItemInHand(hand);

		// Только тонированная бутылка способна удержать духа.
		if (!held.is(FirstMod.TINTED_BOTTLE)) {
			return InteractionResult.PASS;
		}

		// На клиенте достаточно проиграть анимацию взмаха.
		if (this.level().isClientSide()) {
			return InteractionResult.SUCCESS;
		}

		if (!player.hasInfiniteMaterials()) {
			held.shrink(1);
		}

		ItemStack soulBottle = new ItemStack(FirstMod.BOTTLE_OF_SOUL);
		if (!player.getInventory().add(soulBottle)) {
			player.drop(soulBottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}

		// Дух вырывается: на 8 секунд мир вокруг игрока погружается во тьму.
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 160, 0));

		this.playSound(SoundEvents.BOTTLE_FILL, 1.0F, 1.0F);

		if (this.level() instanceof ServerLevel serverLevel) {
			// Озеро уходит на перезарядку: новый дух появится не сразу.
			if (!this.lakeKey.isEmpty()) {
				SoulSpiritCooldowns.get(serverLevel).startCooldown(this.lakeKey, serverLevel.getGameTime());
			}

			serverLevel.sendParticles(
					ParticleTypes.SOUL,
					this.getX(), this.getY() + 0.8D, this.getZ(),
					24, 0.45D, 0.65D, 0.45D, 0.02D
			);
			serverLevel.sendParticles(
					ParticleTypes.SOUL_FIRE_FLAME,
					this.getX(), this.getY() + 1.0D, this.getZ(),
					16, 0.35D, 0.55D, 0.35D, 0.01D
			);
		}

		this.discard();
		return InteractionResult.SUCCESS;
	}

	/**
	 * Проверка естественного спавна: позиция должна находиться в лаве,
	 * а над столбом лавы должен быть открытый воздух (поверхность озера).
	 */
	public static boolean checkSpawnRules(
			EntityType<SoulStriderSpirit> type,
			LevelAccessor level,
			EntitySpawnReason reason,
			BlockPos pos,
			RandomSource random
	) {
		if (reason == EntitySpawnReason.SPAWN_ITEM_USE
				|| reason == EntitySpawnReason.COMMAND
				|| reason == EntitySpawnReason.DISPENSER) {
			return true;
		}

		if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
			return false;
		}

		BlockPos.MutableBlockPos cursor = pos.mutable();
		while (level.getFluidState(cursor.move(Direction.UP)).is(FluidTags.LAVA)) {
			// Поднимаемся к поверхности лавового озера.
		}

		return level.getBlockState(cursor).isAir();
	}
}
