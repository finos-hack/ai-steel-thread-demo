package org.finos.ai.demo.tokenomics;

import java.time.Instant;

/** Spend vs budget for one use-case policy, including end-of-period forecast. */
public record TokenomicsBudgetView(
    String useCase,
    String period,
    double limitUsd,
    double warnAt,
    boolean hardLimit,
    double spentUsd,
    double remainingUsd,
    double burnRatio,
    String status,
    double forecastEndOfPeriodUsd,
    boolean forecastExceedsLimit,
    Instant periodStart,
    Instant periodEnd,
    double elapsedDays,
    double remainingDays) {}
