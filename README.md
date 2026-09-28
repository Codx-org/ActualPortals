# Actual Portals

Nether portals you can see through and walk through. No purple sheet, no black screen —
the Nether is simply there on the other side of the frame, and you step into it.

Built on [Planeshift](../planeshift.md), which draws the far side and carries you across.
This mod only says where the openings are and keeps the two ends in step.

## What it shows a mod developer

Planeshift takes planes from a **provider**. This mod is the case where the planes are
*found in the world*: they are made of blocks, they appear when a portal is lit and vanish
when its frame is broken, and no list of them is kept anywhere. That is what the per-chunk
provider is for — it is asked about one chunk at a time and its answers are cached per
chunk, so "which planes are there" becomes "which blocks are in this chunk":

```java
// PortalPlanes.init()
Planes.registerInChunks(PortalPlanes::collect);
```

The whole cost of that cache is remembering to drop it when the blocks change:

```java
Planes.invalidate(level, chunkOf(pos));   // a portal lit, or a frame broken
```

`PortalPlanes.changedAt` hears about every portal block that appears or disappears and
coalesces them, so a player breaking a frame invalidates a handful of chunks once rather
than once per block. The far side's chunks are invalidated too: its planes lead back here,
and a doorway whose partner has gone must stop being a doorway on both sides at the same
moment.

## Joining the two ends

`PlaneBoundary.between` builds a pair of planes whose transforms are exact inverses —
walk through one, walk back, and you are where you started to the last decimal:

```java
out.accept(PlaneBoundary.between(
        here.dimension(),  shape,     facing,
        there.dimension(), farShape,  farFacing,
        turn).a());
```

Deriving both ends from one call is the point. Writing the two transforms out separately
is how you end up walking into the Nether, walking back, and arriving somewhere you have
never been — which reads as a physics bug and is really a data one.

## The parts that are about nether portals, not about planes

- **`PortalFrame`** walks a lit portal's sheet to find how wide and how tall it is, and
  turns that into a `PlaneShape.Rectangle` with an inset of half a block — so the surface
  lies down the middle of the sheet, where the purple used to be, rather than half a block
  in front of it.
- **`PortalLinker`** digs the far side the moment a portal is lit, instead of waiting for
  somebody to walk in. Vanilla can wait, because you never see the moment it decides; a
  doorway you look through cannot, or it stays an empty frame until its first use.
- **`PortalDigger`** makes the far portal the same size as the near one, because through a
  plane you can see both at once and a doorway that changes size across the wall is the
  first thing you notice. Its bottom row is sunk into the ground, the way a portal you
  built yourself is: a frame standing *on* the ground leaves its doorway a block up from
  everything around it, so you drop out of it and have to jump to get back in.
- **`PortalPairing`** keeps portals in exclusive pairs, so two portals in one place never
  share a far side and take turns being the one that is drawn.
- **`HidePortalSurfaceMixin`** stops vanilla drawing the purple sheet, and
  **`PortalNotAThingMixin`** stops the portal blocks being collidable, breakable or
  hazardous, so the frame is the only part you can hit.

## One rule worth knowing

A portal whose counterpart does not exist yet yields **no plane at all**. A plane leading
somewhere undecided is worse than no plane, because the view through it would have to
guess. So a freshly lit portal is a plain portal until the linker has dug its far side —
which, here, is the same tick.
