package codx.actualportals;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

/**
 * Which portal on the far side belongs to this one.
 *
 * <p>Vanilla's answer is "the nearest", which lets two portals share. Sharing is invisible
 * when a trip is a black screen, and very visible when you can see through: two doorways
 * side by side showing the same view, and stepping into either leaving you in the same
 * place.
 *
 * <p>So a partner is claimed rather than merely found. A far portal belongs to this one only
 * if, looked at from over there, this one is the nearest in return. Nothing is written down
 * to arrange that — the pairing is worked out from where the portals are, both times, so
 * there is no record to keep in step with a world somebody has been digging in.
 */
public final class PortalPairing {
	private PortalPairing() {
	}

	/** Where in {@code other} this portal's counterpart would be, scaled and clamped. */
	public static BlockPos scaled(ServerLevel level, ServerLevel other, PortalFrame frame) {
		double scale = DimensionType.getTeleportationScale(level.dimensionType(),
                                other.dimensionType());
		Vec3 centre = Vec3.atCenterOf(frame.min()).add(Vec3.atCenterOf(frame.max())).scale(0.5);
		return other.getWorldBorder().clampToBounds(centre.x * scale, centre.y, centre.z * scale);
	}

	/**
	 * The far portal this one may use, or nothing if it must dig its own.
	 *
	 * <p>Nothing also means "there is none yet", and the two cases are deliberately the same
	 * answer: either way what this portal needs is a frame of its own over there.
	 */
	public static @Nullable PortalFrame partnerOf(ServerLevel level, ServerLevel other,
			PortalFrame frame) {
		Optional<BlockPos> found = other.getPortalForcer().findClosestPortalPosition(
				scaled(level, other, frame), other.dimension() == Level.NETHER,
				other.getWorldBorder());

		if (found.isEmpty()) {
			return null;
		}

		PortalFrame candidate = PortalFrame.around(other, found.get());

        if (candidate == null || !PortalRules.exclusivePartners()) {
			return candidate;
		}

		return claims(other, level, candidate, frame) ? candidate : null;
	}

	/**
	 * Whether {@code candidate} would choose {@code frame} back.
	 *
	 * <p>The whole of the exclusivity rule. A portal that would pick somebody else is
	 * somebody else's, however close it happens to be to this one.
	 */
	private static boolean claims(ServerLevel side, ServerLevel back, PortalFrame candidate,
			PortalFrame frame) {
		Optional<BlockPos> theirs = back.getPortalForcer().findClosestPortalPosition(
				scaled(side, back, candidate), back.dimension() == Level.NETHER,
				back.getWorldBorder());

		return theirs.isPresent() && within(frame, theirs.get());
	}

	/** Whether this position is one of the blocks that make up the sheet. */
	private static boolean within(PortalFrame frame, BlockPos pos) {
		return pos.getX() >= frame.min().getX() && pos.getX() <= frame.max().getX()
				&& pos.getY() >= frame.min().getY() && pos.getY() <= frame.max().getY()
				&& pos.getZ() >= frame.min().getZ() && pos.getZ() <= frame.max().getZ();
	}
}
