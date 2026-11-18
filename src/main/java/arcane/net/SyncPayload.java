package arcane.net;

import arcane.ArcaneMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.List;

/** Сервер -> клиент: текущие данные прокачки игрока. */
public record SyncPayload(int xp, List<Integer> ranks) implements CustomPayload {
	public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(ArcaneMod.id("sync"));
	public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.INTEGER, SyncPayload::xp,
			PacketCodecs.INTEGER.collect(PacketCodecs.toList()), SyncPayload::ranks,
			SyncPayload::new);

	@Override
	public CustomPayload.Id<? extends CustomPayload> getId() {
		return ID;
	}
}
