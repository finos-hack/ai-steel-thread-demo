package org.finos.ai.demo.tokenomics;

/** Thrown when a hard budget limit blocks an LLM call. */
public class TokenomicsBudgetExceededException extends RuntimeException {

  private final String useCase;
  private final double spentUsd;
  private final double limitUsd;

  public TokenomicsBudgetExceededException(String useCase, double spentUsd, double limitUsd) {
    super(
        "Hard budget exceeded for use case '"
            + useCase
            + "': spent ~$"
            + TokenomicsCostService.roundUsd(spentUsd)
            + " of $"
            + TokenomicsCostService.roundUsd(limitUsd)
            + " limit");
    this.useCase = useCase;
    this.spentUsd = spentUsd;
    this.limitUsd = limitUsd;
  }

  public String getUseCase() {
    return useCase;
  }

  public double getSpentUsd() {
    return spentUsd;
  }

  public double getLimitUsd() {
    return limitUsd;
  }
}
