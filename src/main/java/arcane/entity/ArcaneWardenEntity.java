package arcane.entity;

import net.minecraft.entity.Entity;
import arcane.data.ClassAbilities;
import arcane.data.Fx;
import arcane.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Арканный Страж — босс.
 * Большой и очень крепкий, показывает полосу здоровья, бьёт по площади
 * и призывает Кристальных големов, когда теряет здоровье.
 */
public class ArcaneWardenEntity extends ZombieEntity {

	private final ServerBossBar bossBar = new ServerBossBar(
			Text.translatable("entity.arcane.arcane_warden"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

	private int stage = 0;

	public ArcaneWardenEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
		this.setPersistent();
		// Броня и корона нарисованы на текстуре, в руке — Клинок Стража.
		this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WARDEN_BLADE));
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return ZombieEntity.createZombieAttributes()
				.add(EntityAttributes.MAX_HEALTH, 300.0)
				.add(EntityAttributes.ATTACK_DAMAGE, 12.0)
				.add(EntityAttributes.MOVEMENT_SPEED, 0.27)
				.add(EntityAttributes.ARMOR, 10.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(EntityAttributes.FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.SCALE, 1.8);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		bossBar.setPercent(this.getHealth() / this.getMaxHealth());
		aura(world);
		if (this.age % 40 == 0 && !this.getMainHandStack().isOf(ModItems.WARDEN_BLADE)) {
			this.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.WARDEN_BLADE));
		}

		LivingEntity target = this.getTarget();
		if (target == null || !this.isAlive()) {
			return;
		}
		// Удар по площади каждые 6 секунд.
		if (this.age % 120 == 0) {
			slam(world);
		}
		// Звёздопад: молнии с задержкой (раньше появляется предупреждающий круг).
		int period = stage >= 2 ? 130 : 210;
		if (this.age % period == 60) {
			starfall(world, target);
		}
		// Призыв помощников на 2/3 и 1/3 здоровья.
		float ratio = this.getHealth() / this.getMaxHealth();
		if (ratio < 0.66f && stage < 1) {
			stage = 1;
			summonGolems(world, 2);
		}
		if (ratio < 0.33f && stage < 2) {
			stage = 2;
			summonGolems(world, 3);
		}
	}

	/** Постоянная аура: три вращающиеся спирали света и фиолетовый дым. */
	private void aura(ServerWorld world) {
		if (this.age % 2 == 0) {
			double base = this.age * 0.2;
			double rise = (this.age % 40) / 40.0 * 3.6;
			for (int i = 0; i < 3; i++) {
				double a = base + i * (Math.PI * 2.0 / 3.0);
				world.spawnParticles(ParticleTypes.END_ROD, this.getX() + Math.cos(a) * 2.0, this.getY() + 0.2 + rise,
						this.getZ() + Math.sin(a) * 2.0, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		if (this.age % 6 == 0) {
			world.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 2.0, this.getZ(), 8, 0.7, 1.3, 0.7, 0.4);
		}
	}

	/** Три точки у цели: сначала вспыхивают круги, через 1,5 секунды в них бьёт молния. */
	private void starfall(ServerWorld world, LivingEntity target) {
		Vec3d base = target.getPos();
		for (int i = 0; i < 3; i++) {
			double angle = this.random.nextDouble() * Math.PI * 2.0;
			double dist = i == 0 ? 0.0 : 3.0 + this.random.nextDouble() * 2.0;
			Vec3d spot = base.add(Math.cos(angle) * dist, 0.1, Math.sin(angle) * dist);
			for (int t = 0; t < 30; t += 3) {
				final int tt = t;
				ClassAbilities.later(t + 1, () -> {
					Fx.circle(world, spot, 2.4 - tt * 0.03, ParticleTypes.END_ROD, 26);
					world.spawnParticles(ParticleTypes.ENCHANT, spot.x, spot.y + 0.3, spot.z, 6, 1.0, 0.2, 1.0, 0.3);
				});
			}
			ClassAbilities.later(32, () -> {
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setPosition(spot);
					bolt.setCosmetic(true);
					world.spawnEntity(bolt);
				}
				for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, new Box(spot, spot).expand(2.5, 3.0, 2.5),
						pl -> pl.isAlive() && !pl.isSpectator())) {
					p.damage(world, world.getDamageSources().mobAttack(this), 9.0f);
				}
				world.spawnParticles(ParticleTypes.END_ROD, spot.x, spot.y + 0.5, spot.z, 40, 1.2, 1.5, 1.2, 0.2);
			});
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.8f);
	}

	private void slam(ServerWorld world) {
		for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, this.getBoundingBox().expand(7.0),
				pl -> pl.isAlive() && !pl.isSpectator())) {
			p.damage(world, world.getDamageSources().mobAttack(this), 10.0f);
			p.takeKnockback(1.4, this.getX() - p.getX(), this.getZ() - p.getZ());
		}
		world.spawnParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 1.0, this.getZ(), 80, 3.0, 1.0, 3.0, 0.5);
		for (int i = 0; i < 36; i++) {
			double a = i * Math.PI / 18.0;
			world.spawnParticles(ParticleTypes.EXPLOSION, this.getX() + Math.cos(a) * 5.0, this.getY() + 0.3,
					this.getZ() + Math.sin(a) * 5.0, 1, 0.0, 0.0, 0.0, 0.0);
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.6f);
	}

	private void summonGolems(ServerWorld world, int count) {
		for (int i = 0; i < count; i++) {
			CrystalGolemEntity golem = ModEntities.CRYSTAL_GOLEM.create(world, SpawnReason.MOB_SUMMONED);
			if (golem != null) {
				double angle = this.random.nextDouble() * Math.PI * 2.0;
				golem.setPosition(this.getX() + Math.cos(angle) * 3.0, this.getY(), this.getZ() + Math.sin(angle) * 3.0);
				world.spawnEntity(golem);
			}
		}
		world.playSound(null, this.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.0f, 1.2f);
	}

	@Override
	public void onStartedTrackingBy(ServerPlayerEntity player) {
		super.onStartedTrackingBy(player);
		bossBar.addPlayer(player);
	}

	@Override
	public void onStoppedTrackingBy(ServerPlayerEntity player) {
		super.onStoppedTrackingBy(player);
		bossBar.removePlayer(player);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		bossBar.clearPlayers();
	}

	@Override
	public boolean isBaby() {
		return false;
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
