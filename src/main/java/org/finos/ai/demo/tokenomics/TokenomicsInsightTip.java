package org.finos.ai.demo.tokenomics;

/** One optimization / allocation tip derived from ledger aggregates. */
public record TokenomicsInsightTip(
    String id, String severity, String title, String detail) {}
