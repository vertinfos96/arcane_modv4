package arcane.entity;

import arcane.data.Fx;
import arcane.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Кристальный голем: крепкий враг с кристальной булавой.
 * Способности: «Сокрушающий удар» по площади и «Кристальный панцирь» (стойкость при малом здоровье).
 */
public class CrystalGolemEntity extends ZombieEntity {

	private boolean shelled = false;

	public CrystalGolemEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRYSTAL_MACE));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 40.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 6.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.25)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.5);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		// Всегда держим свою булаву (ваниль иногда выдаёт зомби железный меч).
		if (this.age % 40 == 0 && !this.getMainHandStack().isOf(ModItems.CRYSTAL_MACE)) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CRYSTAL_MACE));
		}
		// Мерцание кристаллов.
		if (this.age % 6 == 0) {
			world.spawnParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 1.0, this.getZ(), 2, 0.35, 0.7, 0.35, 0.01);
			if (this.age % 24 == 0) {
				world.spawnParticles(ParticleTypes.ENCHANT, this.getX(), this.getY() + 1.6, this.getZ(), 6, 0.4, 0.4, 0.4, 0.3);
			}
		}

		LivingEntity target = this.getTarget();
		if (target == null || !this.isAlive()) {
			return;
		}
		// Сокрушающий удар: раз в 7 секунд, если цель рядом.
		if (this.age % 140 == 0 && this.squaredDistanceTo(target) < 36.0) {
			slam(world);
		}
		// Кристальный панцирь: при здоровье ниже 50% — один раз получает стойкость.
		if (!shelled && this.getHealth() < this.getMaxHealth() * 0.5f) {
			shelled = true;
			this.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 1));
			Fx.spiral(world, this.getPos(), 1.0, 2.4, 3.0, 40, ParticleTypes.END_ROD);
			world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.HOSTILE, 1.5f, 0.6f);
		}
	}

	private void slam(ServerWorld world) {
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(4.5),
				pl -> pl.isAlive() && !pl.isSpectator())) {
			p.damage(world, world.getDamageSources().mobAttack(this), 7.0f);
			p.takeKnockback(1.0, this.getX() - p.getX(), this.getZ() - p.getZ());
		}
		Vec3d c = this.getPos().add(0.0, 0.15, 0.0);
		for (int i = 1; i <= 4; i++) {
			Fx.circle(world, c, i * 1.1, ParticleTypes.ELECTRIC_SPARK, 12 + i * 6);
		}
		world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y + 0.3, c.z, 30, 1.5, 0.2, 1.5, 0.15);
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.2f, 0.7f);
	}

	@Override
	protected boolean burnsInDaylight() {
		return false;
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}
}
