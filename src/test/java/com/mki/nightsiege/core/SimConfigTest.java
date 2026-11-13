package com.mki.nightsiege.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Tor 10 (Chef 13.11. 03:20): the team simulation plays with the server's values on today's config, never an old one. */
class SimConfigTest {

    @Test
    void simConfigIsOnTodaysVersion() throws Exception {
        String json = Files.readString(Path.of("tools/sim/server-config.json"), StandardCharsets.UTF_8);
        Matcher v = Pattern.compile("\"settingsVersion\"\\s*:\\s*(\\d+)").matcher(json);
        assertTrue(v.find());
        assertEquals(SiegeConfig.SETTINGS_VERSION, Integer.parseInt(v.group(1)),
                "tools/sim/server-config.json: bring it up with the code's migrations (server values + sanitize)");
    }
}
