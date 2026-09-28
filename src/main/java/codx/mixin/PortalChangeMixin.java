package codx.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import codx.actualportals.PortalLinker;
import codx.actualportals.PortalPlanes;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Notices portals being lit and put out.
 *
 * <p>A portal's planes are worked out once and then remembered, so something has to say
 * when they have stopped being true. Lighting one, breaking its frame, and — the one that
 * is easy to forget — digging the far side of a pair, which turns a portal that led nowhere
 * into one that leads somewhere.
 */
@Mixin(ServerLevel.class)
public class PortalChangeMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void actualportals$portalChanged(BlockPos pos, BlockState oldState, BlockState newState,
			int flags, CallbackInfo info) {
		if (oldState.is(Blocks.NETHER_PORTAL) || newState.is(Blocks.NETHER_PORTAL)) {
			PortalPlanes.changedAt((ServerLevel) (Object) this, pos);
		}

		if (newState.is(Blocks.NETHER_PORTAL) && !oldState.is(Blocks.NETHER_PORTAL)) {
			// Newly lit, so the other end may not exist yet. Dealt with at the end of the
			// tick; see PortalLinker for why not here.
			PortalLinker.lit((ServerLevel) (Object) this, pos);
		}
	}
}
