package codx.actualportals;

import com.google.gson.JsonObject;

import codx.ActualPortals;
import codx.planeshift.ConfigFile;

/**
 * What this mod does with portals, as the world's owner has asked for it.
 *
 * <p>Kept in Planeshift's file rather than one of its own. There is one question a player
 * has — how do doorways behave here — and answering it in two places is a way of hiding
 * half the answer.
 */
public final class PortalRules {
	private static final String EXCLUSIVE_KEY = "portals_get_their_own_partner";

	/**
	 * Whether every portal is given a partner of its own.
	 *
	 * <p>Vanilla shares: light two portals near each other and they come out of the same one
	 * in the Nether, which keeps the Nether from filling up with frames. That is a fine
	 * bargain when a portal is a black screen and you cannot tell — and a poor one when you
	 * can see through it, because two doorways side by side then show the same view and
	 * going into either puts you in the same place.
	 *
	 * <p>On, each gets its own, and two doorways are two places. The cost is honest: more
	 * frames dug in the Nether, and two far sides held open where one used to do.
	 */
	private static boolean exclusivePartners = true;

	private PortalRules() {
	}

	/** Whether every portal is given a partner of its own. */
	public static boolean exclusivePartners() {
		return exclusivePartners;
	}

	public static void load() {
		JsonObject root = ConfigFile.read();

		if (root.has(EXCLUSIVE_KEY)) {
			exclusivePartners = root.get(EXCLUSIVE_KEY).getAsBoolean();
		} else {
			root.addProperty("_" + EXCLUSIVE_KEY,
					"Whether each portal gets a partner of its own instead of sharing the nearest "
							+ "one. Off is vanilla linking: two portals near each other come out of "
							+ "the same frame, and so show the same view.");
			root.addProperty(EXCLUSIVE_KEY, exclusivePartners);
			ConfigFile.write(root);
		}

		ActualPortals.LOGGER.info("Portals get their own partner: {}", exclusivePartners);
	}
}
