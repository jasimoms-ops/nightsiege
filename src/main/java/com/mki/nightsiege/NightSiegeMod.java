package com.mki.nightsiege;

import com.mki.nightsiege.game.SiegeManager;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NightSiegeMod implements ModInitializer {

    public static final String MOD_ID = "nightsiege";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        SiegeManager.get().register();
        LOGGER.info("[Night Siege] bereit");
    }
}
