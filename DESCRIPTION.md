# Actual Portals

Nether portals you can see through. Stand in front of one and the Nether is right there
through the frame — its terrain, its light, its fog, the ghast drifting past. Walk in and you
are simply in it. No purple sheet, no black screen, no pause.

They are still nether portals. Same obsidian, same flint and steel, same linking, same
distance ratio. The only thing that changed is that the doorway is a doorway.

## What is different

- **You can see through it**, live, from either side. Not a texture and not a still.
- **You walk through it.** No loading screen and no camera jump: you keep your speed and
  your direction, and the step you took is the step you take.
- **Both ends exist from the moment you light it.** Vanilla waits until somebody walks in
  before it digs the far side, which is fine when the trip is a black screen and no good
  when you are looking at it.
- **The far side is the same size as the near one.** A four-wide portal comes out
  four-wide, because through a doorway you can see both ends at once.
- **The portal blocks are not in your way.** You cannot break them, they cannot hurt you,
  and you do not get stuck in them. Break the frame to put a portal out, the way it looks
  like it ought to work.
- **Things come through with you.** Mobs, items, and water or lava poured in at the edge.

## Using it

Build a nether portal. Light it. Look at it.

That is the whole of it — there is nothing to craft, no new block, and no command. Every
portal in a world you already have becomes see-through the first time its chunk is loaded.

## Settings

In `config/planeshift.json`, alongside the library's own:

```json
"portals_get_their_own_partner": true
```

Vanilla lets two portals near each other share one on the far side, which keeps the Nether
from filling up with frames. That is a fine bargain when a portal is a black screen and you
cannot tell — and a poor one when you can see through it, because two doorways side by side
then show the same view and going into either puts you in the same place. On, each portal
gets a partner of its own. The cost is honest: more frames dug in the Nether, and two far
sides held open where one used to do.

The same file sets how many doorways may show their far side at once and how far away they
stay visible, which is where to look if you want to spend less on rendering.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- **Planeshift**, which does the seeing and the crossing

## Status

Under active development. Lighting, seeing through, crossing in both directions and breaking
the frame all work. Expect rough edges around lighting at the seam, and around portals built
in places vanilla would not have put one.
