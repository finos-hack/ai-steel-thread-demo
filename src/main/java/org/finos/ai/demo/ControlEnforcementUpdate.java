package org.finos.ai.demo;

/** Runtime toggle for FINOS AIGF control enforcement (demo UI). */
public record ControlEnforcementUpdate(
    Boolean mi1Enabled, Boolean mi4Enabled, Boolean mi5Enabled, Boolean mi11Enabled) {}
