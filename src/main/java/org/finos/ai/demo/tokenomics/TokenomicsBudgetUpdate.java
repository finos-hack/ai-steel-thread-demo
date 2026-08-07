package org.finos.ai.demo.tokenomics;

/** Partial update for a budget policy (null fields leave existing values). */
public record TokenomicsBudgetUpdate(
    String useCase, Double limitUsd, Double warnAt, Boolean hardLimit, String period) {}
