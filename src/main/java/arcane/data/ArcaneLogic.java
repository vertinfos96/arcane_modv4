package arcane.data;

import arcane.entity.ArcaneSkeletonEntity;
import arcane.entity.ArcaneWardenEntity;
import arcane.entity.CrystalGolemEntity;
import arcane.entity.ModEntities;
import arcane.item.Spells;
import arcane.net.SyncPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/** Общая логика RPG-системы: опыт, уровни, улучшения, команды. */
public final class ArcaneLogic {
	private ArcaneLogic() {
	}

	public static void registerEvents() {
		ClassAbilities.register();

		// Опыт за убийство монстров.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getAttacker() instanceof ServerPlayerEntity player && entity instanceof Monster) {
				int xp = Math.max(3, (int) (entity.getMaxHealth() / 3.0f));
				if (entity instanceof ArcaneWardenEntity) {
					xp = 1500;
					Achievements.grant(player, "warden_slayer");
				} else if (entity instanceof CrystalGolemEntity || entity instanceof ArcaneSkeletonEntity) {
					xp *= 2;
				}
				int wisdom = ArcaneData.get(player).rank(Skills.WISDOM);
				if (wisdom > 0) {
					xp = Math.round(xp * (1.0f + 0.15f * wisdom));
				}
				addXp(player, xp);
				ClassAbilities.onKill(player, source);
			}
		});

		// Отправляем данные клиенту при входе в мир.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sync(handler.getPlayer());
			Achievements.grant(handler.getPlayer(), "root");
		});

		// Команды: /arcane staff, /arcane xp <число>, /arcane reset, /arcane boss
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("arcane")
						.then(CommandManager.literal("staff").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							p.giveItemStack(new ItemStack(arcane.item.ModItems.ARCANE_STAFF));
							ctx.getSource().sendFeedback(() -> Text.translatable("command.arcane.staff"), false);
							return 1;
						}))
						.then(CommandManager.literal("xp").then(
								CommandManager.argument("amount", IntegerArgumentType.integer(1, 100000)).executes(ctx -> {
									ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
									int amount = IntegerArgumentType.getInteger(ctx, "amount");
									addXp(p, amount);
									ctx.getSource().sendFeedback(() -> Text.translatable("command.arcane.xp", amount), false);
									return 1;
								})))
						.then(CommandManager.literal("reset").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							ArcaneData.set(p, ArcaneData.EMPTY);
							sync(p);
							ctx.getSource().sendFeedback(() -> Text.translatable("command.arcane.reset"), false);
							return 1;
						}))
						.then(CommandManager.literal("boss").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							ServerWorld world = ctx.getSource().getWorld();
							ArcaneWardenEntity boss = ModEntities.ARCANE_WARDEN.create(world, SpawnReason.COMMAND);
							if (boss == null) {
								return 0;
							}
							Vec3d dir = p.getRotationVec(1.0f).multiply(1.0, 0.0, 1.0).normalize();
							Vec3d pos = p.getPos().add(dir.multiply(7.0));
							boss.setPosition(pos.x, pos.y, pos.z);
							world.spawnEntity(boss);
							ctx.getSource().sendFeedback(() -> Text.translatable("command.arcane.boss"), false);
							return 1;
						}))));
	}

	public static void addXp(ServerPlayerEntity player, int amount) {
		ArcaneData old = ArcaneData.get(player);
		ArcaneData updated = old.withXp(old.xp() + amount);
		ArcaneData.set(player, updated);
		if (updated.level() >= ArcaneData.MAX_LEVEL) {
			Achievements.grant(player, "legend");
		}
		if (updated.level() > old.level()) {
			player.sendMessage(Text.translatable("message.arcane.levelup", updated.level(), updated.points()), false);
			((ServerWorld) player.getWorld()).playSound(null, player.getBlockPos(),
					SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
		} else {
			player.sendMessage(Text.translatable("message.arcane.xp", amount), true);
		}
		sync(player);
	}

	public static void upgrade(ServerPlayerEntity player, int skill) {
		ArcaneData old = ArcaneData.get(player);
		ArcaneData updated = old.upgraded(skill);
		if (updated != old) {
			ArcaneData.set(player, updated);
			((ServerWorld) player.getWorld()).playSound(null, player.getBlockPos(),
					SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0f, 1.2f);
			boolean all = true;
			for (int cls = 0; cls < Skills.CLASS_COUNT; cls++) {
				boolean any = false;
				for (int i = 0; i < Skills.size(cls); i++) {
					if (updated.rank(Skills.first(cls) + i) > 0) {
						any = true;
					}
				}
				all &= any;
			}
			if (all) {
				Achievements.grant(player, "all_paths");
			}
		}
		sync(player);
	}

	public static void sync(ServerPlayerEntity player) {
		ClassAbilities.applyAttributes(player);
		ArcaneData d = ArcaneData.get(player);
		ServerPlayNetworking.send(player, new SyncPayload(d.xp(), d.ranks()));
	}

	/** Переключает выбранное заклинание посоха (клавиша Z / R с посохом в руке). */
	public static void cycleSpell(ServerPlayerEntity player) {
		ArcaneData data = ArcaneData.get(player);
		int next = data.nextSpell();
		ArcaneData.set(player, data.withSelected(next));
		player.sendMessage(Text.translatable("message.arcane.selected", Spells.name(next)), true);
		((ServerWorld) player.getWorld()).playSound(null, player.getBlockPos(),
				SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
	}
}
