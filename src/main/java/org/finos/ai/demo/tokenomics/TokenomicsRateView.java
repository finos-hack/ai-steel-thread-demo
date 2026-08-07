package org.finos.ai.demo.tokenomics;

public record TokenomicsRateView(
    String provider,
    String model,
    double promptPer1m,
    double completionPer1m,
    double cacheInputPer1m,
    double cacheWritePer1m) {}
