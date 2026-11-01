package ru.school.firstmod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Волшебный рубин и его защищённые варианты.
 *
 * <p>ПКМ активирует силу рубина и тратит 1 единицу прочности.</p>
 *
 * <p>Когда прочность опускается до 1, рубин разряжается: с шансом 50%
 * повышается этап порчи, при повышении в игрока бьёт молния, после чего
 * прочность возвращается к максимуму.</p>
 *
 * <p>Обычный рубин без коронки после использования уходит на перезарядку
 * 10 секунд. Рубины в коронках перезарядки не имеют.</p>
 */
public class MagicRubyItem extends Item {
	/** Перезарядка обычного рубина без коронки. */
	private static final int COOLDOWN_TICKS = 10 * 20;

	/** Шанс повышения этапа порчи при разрядке. */
	private static final float CORRUPTION_CHANCE = 0.50F;

	private static final int SPEED_DURATION_TICKS = 20 * 20;          // Скорость III — 20 сек
	private static final int REGENERATION_DURATION_TICKS = 3 * 20;    // Регенерация II — 3 сек
	private static final int EXTRA_EFFECT_DURATION_TICKS = 10 * 20;   // Тошнота / Слепота / Голод / Замедление

	private static final int RED = 0xFF2020;
	private static final int PINK = 0xFF69B4;
	private static final int MURKY_PINK = 0xB05070;
	private static final int BLACK = 0x141414;
	private static final int GREEN = 0x33CC33;

	/** Тип рубина: определяет прочность и шанс поломки коронки. */
	public enum Kind {
		/** Обычный рубин. */
		BASE(20),
		/** Защищённый золотой коронкой рубин. */
		PROTECTED(25),
		/** Улучшенный рубин в незеритовой коронке. */
		ADVANCED(42, 0.05F);

		private final int maxDurability;
		private final float crownBreakChance;

		Kind(int maxDurability) {
			this(maxDurability, 0.0F);
		}

		Kind(int maxDurability, float crownBreakChance) {
			this.maxDurability = maxDurability;
			this.crownBreakChance = crownBreakChance;
		}

		public int maxDurability() {
			return maxDurability;
		}

		public float crownBreakChance() {
			return crownBreakChance;
		}
	}

	private final Kind kind;

	public MagicRubyItem(Kind kind, Properties properties) {
		super(properties);
		this.kind = kind;
	}

	public Kind kind() {
		return kind;
	}

	/**
	 * Имя предмета зависит от этапа порчи.
	 *
	 * <p>Ключи {@code item.first-mod.ruby_stage_2..5} задают точные названия
	 * «Подиспорченный рубин», «Испорченный рубин», «Разочарованный рубин»
	 * и «Осквернённый рубин». Обычный (первый) этап использует стандартное
	 * имя предмета.</p>
	 */
	@Override
	public Component getName(ItemStack stack) {
		if (kind == Kind.BASE) {
			int stage = stage(stack);

			if (stage >= 2 && stage <= 5) {
				return Component.translatable("item.first-mod.ruby_stage_" + stage);
			}
		}

		return super.getName(stack);
	}

	// ------------------------------------------------------------------
	// Использование
	// ------------------------------------------------------------------

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		// Разряженный рубин или предмет на перезарядке не срабатывает.
		if (isDischarged(stack) || player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		activate(serverLevel, serverPlayer, stack, hand);
		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------
	// Активация по этапу порчи
	// ------------------------------------------------------------------

	private void activate(ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand) {
		RandomSource random = player.getRandom();
		int stage = stage(stack);

		// Частицы: аура коронки и цвет этапа порчи.
		spawnCrownAura(level, player);
		spawnStageParticles(level, player, stage);

		switch (stage) {
			case 1 -> applyBaseEffects(player);
			case 2 -> {
				applyBaseEffects(player);
				player.hurtServer(level, level.damageSources().magic(), 2.0F);
			}
			case 3 -> {
				applyBaseEffects(player);
				player.hurtServer(level, level.damageSources().magic(), 6.0F);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, EXTRA_EFFECT_DURATION_TICKS, 0));
			}
			case 4 -> {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, EXTRA_EFFECT_DURATION_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_DURATION_TICKS, 1));
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_DURATION_TICKS, 2));

				damageInventory(player, 0.25F);

				if (random.nextFloat() < 0.05F) {
					player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
					return;
				}
			}
			case 5 -> {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EXTRA_EFFECT_DURATION_TICKS, 1));
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EXTRA_EFFECT_DURATION_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EXTRA_EFFECT_DURATION_TICKS, 1));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, EXTRA_EFFECT_DURATION_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 5 * 20, 0));

				breakRandomDamageableItem(player, stack);

				if (random.nextFloat() < 0.05F) {
					teleportToNether(level, player);
				}

				if (random.nextFloat() < 0.20F) {
					player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
					return;
				}
			}
			default -> applyBaseEffects(player);
		}

		// Тратим 1 прочность, но никогда не опускаемся ниже 1.
		int newDamage = Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + 1);
		stack.setDamageValue(newDamage);

		// Обычный рубин без коронки уходит на перезарядку.
		if (kind == Kind.BASE) {
			player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		}

		if (newDamage >= stack.getMaxDamage() - 1) {
			onDischarged(level, player, stack, hand);
		}
	}

	private static void applyBaseEffects(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_DURATION_TICKS, 2));
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_DURATION_TICKS, 1));
	}

	// ------------------------------------------------------------------
	// Разрядка, порча и молния
	// ------------------------------------------------------------------

	/**
	 * Разрядка рубина. Ни один предмет не выбрасывается на землю:
	 * стадия, прочность и разделение рубина с коронкой происходят
	 * только внутри инвентаря игрока.
	 */
	private void onDischarged(ServerLevel level, ServerPlayer player, ItemStack stack, InteractionHand hand) {
		RandomSource random = player.getRandom();
		int previousStage = stage(stack);
		int newStage = previousStage;

		// 50% — этап порчи повышается (не выше 5).
		if (random.nextFloat() < CORRUPTION_CHANCE) {
			newStage = Math.min(5, previousStage + 1);
			stack.set(FirstMod.RUBY_STAGE, newStage);

			// Молния бьёт только если этап действительно повысился.
			if (newStage > previousStage) {
				strikeLightning(level, player);
			}
		}

		// Рубин снова полностью заряжен для следующего использования.
		stack.setDamageValue(0);

		switch (kind) {
			case BASE -> {
				// Остаётся в инвентаре, перезарядка уже выдана в activate().
			}
			case PROTECTED -> {
				// Золотая коронка всегда сохраняется и возвращается в инвентарь,
				// а рубин становится обычным. На землю ничего не выбрасывается.
				if (player.getInventory().add(new ItemStack(FirstMod.CROWN))) {
					player.setItemInHand(hand, createRuby(FirstMod.MAGIC_RUBY, newStage));
				}
			}
			case ADVANCED -> {
				// Рубин всегда остаётся в инвентаре. Только 5% — коронка ломается,
				// в остальных 95% коронка сохраняется, а рубин снова заряжен.
				if (random.nextFloat() < kind.crownBreakChance()) {
					player.setItemInHand(hand, createRuby(FirstMod.MAGIC_RUBY, newStage));
				}
			}
		}
	}

	/** Создаёт новый рубин указанного этапа с полной прочностью. */
	private static ItemStack createRuby(Item item, int stage) {
		ItemStack stack = new ItemStack(item);
		stack.set(FirstMod.RUBY_STAGE, stage);
		stack.setDamageValue(0);
		return stack;
	}

	private static void strikeLightning(ServerLevel level, ServerPlayer player) {
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);

		if (bolt == null) {
			return;
		}

		bolt.setPos(player.getX(), player.getY(), player.getZ());
		bolt.setCause(player);
		level.addFreshEntity(bolt);
	}

	// ------------------------------------------------------------------
	// Эффекты на инвентарь
	// ------------------------------------------------------------------

	private static void damageInventory(ServerPlayer player, float fraction) {
		for (ItemStack invStack : player.getInventory().getNonEquipmentItems()) {
			if (invStack.isEmpty() || !invStack.isDamageableItem()) {
				continue;
			}

			int max = invStack.getMaxDamage();
			int loss = Math.max(1, Math.round(max * fraction));
			invStack.setDamageValue(Math.min(max - 1, invStack.getDamageValue() + loss));
		}
	}

	private static void breakRandomDamageableItem(ServerPlayer player, ItemStack source) {
		List<ItemStack> candidates = new ArrayList<>();

		for (ItemStack invStack : player.getInventory().getNonEquipmentItems()) {
			if (!invStack.isEmpty() && invStack != source && invStack.isDamageableItem()) {
				candidates.add(invStack);
			}
		}

		if (candidates.isEmpty()) {
			return;
		}

		ItemStack victim = candidates.get(player.getRandom().nextInt(candidates.size()));
		victim.setCount(0);
	}

	// ------------------------------------------------------------------
	// Телепорт в Незер
	// ------------------------------------------------------------------

	private static void teleportToNether(ServerLevel level, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		ServerLevel nether = server.getLevel(Level.NETHER);

		if (nether == null) {
			return;
		}

		int x = Mth.floor(player.getX() / 8.0);
		int z = Mth.floor(player.getZ() / 8.0);
		int y = nether.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		y = Mth.clamp(y + 1, nether.getMinY() + 4, 120);

		Vec3 target = new Vec3(x + 0.5, y, z + 0.5);

		player.teleport(new TeleportTransition(
				nether,
				target,
				Vec3.ZERO,
				player.getYRot(),
				player.getXRot(),
				TeleportTransition.PLAY_PORTAL_SOUND
		));
	}

	// ------------------------------------------------------------------
	// Утилиты
	// ------------------------------------------------------------------

	public static int stage(ItemStack stack) {
		return stack.getOrDefault(FirstMod.RUBY_STAGE, 1);
	}

	/** Рубин разряжен, если у него осталась последняя единица прочности. */
	public static boolean isDischarged(ItemStack stack) {
		return stack.getDamageValue() >= stack.getMaxDamage() - 1;
	}

	private static void spawnParticles(ServerLevel level, ServerPlayer player, int color, int count) {
		level.sendParticles(
				new DustParticleOptions(color, 1.0F),
				player.getX(), player.getY() + 1.0, player.getZ(),
				count, 0.6, 0.8, 0.6, 0.0
		);
	}

	/** Цветные частицы, соответствующие текущему этапу порчи. */
	private static void spawnStageParticles(ServerLevel level, ServerPlayer player, int stage) {
		switch (stage) {
			case 1 -> spawnParticles(level, player, RED, 16);
			case 2 -> spawnParticles(level, player, PINK, 20);
			case 3 -> spawnParticles(level, player, MURKY_PINK, 24);
			case 4 -> spawnParticles(level, player, BLACK, 30);
			case 5 -> spawnParticles(level, player, GREEN, 36);
			default -> {
			}
		}
	}

	/**
	 * Аура коронки при активации рубина.
	 *
	 * <p>Золотая коронка даёт кольцо из {@link ParticleTypes#FLAME},
	 * незеритовая — кольцо из голубого {@link ParticleTypes#SOUL_FIRE_FLAME}
	 * с добавлением частиц {@link ParticleTypes#SOUL}.</p>
	 *
	 * <p>Для обычного рубина без коронки аура не спавнится.</p>
	 */
	private void spawnCrownAura(ServerLevel level, ServerPlayer player) {
		if (kind == Kind.BASE) {
			return;
		}

		boolean advanced = kind == Kind.ADVANCED;
		int ringPoints = 24;
		double radius = 0.9;
		RandomSource random = player.getRandom();

		for (int i = 0; i < ringPoints; i++) {
			double angle = (Math.PI * 2.0 / ringPoints) * i;
			double x = player.getX() + Math.cos(angle) * radius;
			double z = player.getZ() + Math.sin(angle) * radius;
			double y = player.getY() + 0.1 + random.nextDouble() * 0.9;

			if (advanced) {
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.0, 0.02, 0.0, 0.005);
			} else {
				level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0.0, 0.02, 0.0, 0.005);
			}
		}

		// Дополнительный столб частиц вокруг игрока.
		if (advanced) {
			level.sendParticles(
					ParticleTypes.SOUL,
					player.getX(), player.getY() + 1.0, player.getZ(),
					10, 0.35, 0.8, 0.35, 0.01
			);
		} else {
			level.sendParticles(
					ParticleTypes.FLAME,
					player.getX(), player.getY() + 1.0, player.getZ(),
					10, 0.35, 0.8, 0.35, 0.02
			);
		}
	}
}
