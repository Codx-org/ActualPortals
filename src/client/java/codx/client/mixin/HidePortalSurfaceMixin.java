package codx.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Takes the purple sheet out of a nether portal, so the far side can be seen instead.
 *
 * <p>The swirl is the whole reason you cannot see through a portal: it is an opaque-enough
 * surface sitting exactly where the view through ought to be. With it gone the frame is a
 * hole, and Planeshift draws the other dimension into it.
 *
 * <p>Only the drawing. The block is still there and still does everything it did — it
 * still takes you to the Nether, still keeps its own sounds and particles, still counts as
 * a lit portal. This is the one thing about it that changes.
 *
 * <p>Client-side, because it is a question about pixels. A server has no opinion on what a
 * block looks like.
 */
@Mixin(BlockBehaviour.class)
public class HidePortalSurfaceMixin {
	@Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
	private void actualportals$hidePortalSurface(BlockState state,
			CallbackInfoReturnable<RenderShape> info) {
		if (state.getBlock() instanceof NetherPortalBlock) {
			info.setReturnValue(RenderShape.INVISIBLE);
		}
	}
}
