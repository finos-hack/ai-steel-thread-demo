package org.finos.ai.demo.tokenomics;

import java.util.List;

public record TokenomicsSummaryResponse(
    boolean enabled,
    String currency,
    String useCase,
    long calls,
    long promptTokens,
    long completionTokens,
    long totalTokens,
    double estimatedCostUsd,
    double avgCostPerCallUsd,
    double costPer1kTokensUsd,
    List<String> availableUseCases) {}
