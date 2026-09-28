package codx.actualportals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import codx.ActualPortals;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Digs the other end of a portal as soon as it is lit.
 *
 * <p>Vanilla leaves the far side undug until somebody walks in, which is fine when the trip
 * is a black screen — you never see the moment it is decided. It is not fine for a doorway
 * you look through: a portal whose far side does not exist yet has nothing to show, so it
 * would stay a plain empty frame until its first use and only then come alive.
 *
 * <p>So the digging is moved earlier. The same call vanilla makes, made at lighting time
 * instead of at walking-in time, which means the work and the terrain are exactly what they
 * would have been — only sooner, and while you are standing there rather than mid-step.
 *
 * <p>Deferred to the end of the tick rather than done as the block lands. Carving one world
 * from inside another's block update is a good way to find out which parts of the chunk
 * system do not expect to be re-entered.
 */
public final class PortalLinker {
	/** Portal blocks seen appearing this tick, by the level they appeared in. */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> lit = new HashMap<>();

	private PortalLinker() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PortalLinker::tick);
	}

	/** A portal block appeared here. */
	public static void lit(ServerLevel level, BlockPos pos) {
		lit.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
	}

	private static void tick(MinecraftServer server) {
		if (lit.isEmpty()) {
			return;
		}

		Map<ResourceKey<Level>, Set<BlockPos>> pending = new HashMap<>(lit);
		lit.clear();

		pending.forEach((key, positions) -> {
			ServerLevel level = server.getLevel(key);

			if (level != null) {
				link(level, positions);
			}
		});
	}

	private static void link(ServerLevel level, Set<BlockPos> positions) {
		ServerLevel other = destination(level);

		if (other == null) {
			return;
		}

		// A portal is lit several blocks at a time, and they are all one doorway. Each
		// sheet is dealt with once, however many of its blocks arrived.
		List<PortalFrame> done = new ArrayList<>();

		for (BlockPos pos : positions) {
			if (done.stream().anyMatch(frame -> within(frame, pos))) {
				continue;
			}

			PortalFrame frame = PortalFrame.around(level, pos);

			if (frame == null) {
				// Lit and put out again within the tick.
				continue;
			}

			done.add(frame);
			dig(level, other, frame);
		}
	}

	/** Makes the far end of this portal, unless there already is one. */
	private static void dig(ServerLevel level, ServerLevel other, PortalFrame frame) {
		BlockPos around = PortalPairing.scaled(level, other, frame);

		if (PortalPairing.partnerOf(level, other, frame) != null) {
			// Already somewhere of its own to go. This is also what stops the portal we are
			// about to make from turning round and making another one back: once it is
			// there, each of the pair picks the other.
			return;
		}

		// The same size as this one, not vanilla's fixed two-by-three. Both ends are visible
		// at once through a plane, and a doorway that changes size halfway through is the
		// first thing anybody notices.
		Optional<BlockPos> made = PortalDigger.dig(other, around, frame.axis(),
				frame.width(), frame.height());

		if (made.isEmpty()) {
			// Nothing is broken by this: the portal still works, it simply cannot be seen
			// through until vanilla digs the far side the old way, on the first trip.
			ActualPortals.LOGGER.info("No room for the far side of the portal at {} in {}",
					frame.min().toShortString(), level.dimension().identifier());
			return;
		}

		PortalPlanes.changedAt(level, frame.min());
		PortalPlanes.changedAt(other, made.get());
	}

	private static boolean within(PortalFrame frame, BlockPos pos) {
		return pos.getX() >= frame.min().getX() && pos.getX() <= frame.max().getX()
				&& pos.getY() >= frame.min().getY() && pos.getY() <= frame.max().getY()
				&& pos.getZ() >= frame.min().getZ() && pos.getZ() <= frame.max().getZ();
	}

	private static ServerLevel destination(ServerLevel level) {
		if (level.dimension().equals(Level.OVERWORLD)) {
			return level.getServer().getLevel(Level.NETHER);
		}

		if (level.dimension().equals(Level.NETHER)) {
			return level.getServer().getLevel(Level.OVERWORLD);
		}

		return null;
	}
}
