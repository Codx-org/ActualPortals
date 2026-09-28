package codx;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActualPortals implements ModInitializer {
	public static final String MOD_ID = "actual-portals";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		codx.actualportals.PortalRules.load();
		codx.actualportals.PortalPlanes.register();
		codx.actualportals.PortalLinker.init();
		LOGGER.info("Nether and end portals will be described as portal planes");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
