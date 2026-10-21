package com.mki.nightsiege.client;

/**
 * Added to the sky renderer and the environment probe by the blood moon mixins.
 * The client checks both at start-up and writes the result to the log, so a
 * missing blood moon look shows up there instead of failing silently.
 */
public interface BloodMoonSkyMarker {
}
