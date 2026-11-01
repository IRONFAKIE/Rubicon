package ru.school.firstmod;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Блуждающая душа, которую призывает «Душевное переполнение».
 *
 * <p>Душа летит к цели и каждую секунду наносит магический урон. Через
 * некоторое время исчезает. Визуально использует модель векса, но с
 * собственной голубой текстурой.</p>
 */
public class WanderingSoul extends Vex {
	private static final int LIFETIME = 240;
	private static final float DAMAGE = 2.0F;

	private UUID targetUuid;

	public WanderingSoul(EntityType<? extends WanderingSoul> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.setPersistenceRequired();
	}

	public void setTargetUuid(UUID targetUuid) {
		this.targetUuid = targetUuid;
	}

	public UUID getTargetUuid() {
		return this.targetUuid;
	}

	@Override
	protected void registerGoals() {
		// Никакого стандартного ИИ векса: душой управляет собственный tick().
	}

	@Override
	public boolean removeWhenFarAway(double distanceToClosestPlayer) {
		return false;
	}

	@Override
	public boolean requiresCustomPersistence() {
		return true;
	}

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			return;
		}

		if (this.tickCount > LIFETIME) {
			this.discard();
			return;
		}

		LivingEntity target = this.getBoundTarget();
		if (target == null || !target.isAlive()) {
			this.discard();
			return;
		}

		Vec3 aim = target.getEyePosition().subtract(this.position());
		double distance = aim.length();

		if (distance > 1.4D) {
			Vec3 velocity = aim.normalize().scale(0.42D);
			this.setDeltaMovement(velocity);
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		} else {
			this.setDeltaMovement(this.getDeltaMovement().scale(0.55D));
		}

		if (this.tickCount % 20 == 0 && this.level() instanceof ServerLevel serverLevel) {
			target.hurtServer(serverLevel, this.damageSources().magic(), DAMAGE);
			serverLevel.sendParticles(
					ParticleTypes.SOUL,
					target.getX(),
					target.getY() + target.getBbHeight() * 0.5D,
					target.getZ(),
					10,
					0.35D,
					0.45D,
					0.35D,
					0.01D
			);
		}
	}

	private LivingEntity getBoundTarget() {
		if (this.targetUuid == null) {
			return null;
		}
		if (this.level().getEntity(this.targetUuid) instanceof LivingEntity living) {
			return living;
		}
		return null;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.targetUuid != null) {
			output.store("Target", UUIDUtil.CODEC, this.targetUuid);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.targetUuid = input.read("Target", UUIDUtil.CODEC).orElse(null);
	}
}
