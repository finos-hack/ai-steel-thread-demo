package org.finos.ai.demo.tokenomics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TokenomicsServiceTest {

  private TokenomicsProperties properties;
  private TokenomicsLedger ledger;
  private TokenomicsService service;
  private Clock clock;

  @BeforeEach
  void setUp() {
    properties = new TokenomicsProperties();
    ledger = new TokenomicsLedger();
    TokenomicsCostService costService = new TokenomicsCostService(properties);
    @SuppressWarnings("unchecked")
    ObjectProvider<org.finos.ai.demo.observability.ProcessScopedLlmTokenUsageWriter> writer =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(writer.getIfAvailable()).thenReturn(null);
    clock = Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC);
    @SuppressWarnings("unchecked")
    ObjectProvider<Clock> clockProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(clockProvider.getIfAvailable()).thenReturn(clock);
    service =
        new TokenomicsService(
            properties, costService, ledger, new SimpleMeterRegistry(), writer, clockProvider);
  }

  @Test
  void estimate_usesExactModelRate() {
    Double cost =
        new TokenomicsCostService(properties)
            .estimateUsd("openai", "gpt-5.4", new TokenUsage(1_000_000, 1_000_000, 2_000_000));
    assertThat(cost).isEqualTo(17.5);
  }

  @Test
  void estimate_openAiCacheReadIsNotDoubleChargedAtPromptRate() {
    // prompt=1M includes 400k cached; bill 600k@2.50 + 400k@0.25 + 0 completion
    Double cost =
        new TokenomicsCostService(properties)
            .estimateUsd(
                "openai",
                "gpt-5.4",
                new TokenUsage(1_000_000, 0, 1_000_000, 400_000, 0));
    assertThat(cost).isEqualTo(1.6);
  }

  @Test
  void estimate_openAiGpt56LunaAppliesCacheWriteRate() {
    // 100k uncached prompt @0.20 + 50k cache write @0.25 + 10k completion @1.20
    Double cost =
        new TokenomicsCostService(properties)
            .estimateUsd(
                "openai",
                "gpt-5.6-luna",
                new TokenUsage(100_000, 10_000, 110_000, 0, 50_000));
    assertThat(cost).isEqualTo(0.0445);
  }

  @Test
  void estimate_anthropicCachesAreBilledSeparatelyFromPrompt() {
    // 100k prompt @3.00 + 200k cache read @0.20 + 50k cache write @2.50 + 10k completion @15
    Double cost =
        new TokenomicsCostService(properties)
            .estimateUsd(
                "anthropic",
                "claude-sonnet-5",
                new TokenUsage(100_000, 10_000, 110_000, 200_000, 50_000));
    assertThat(cost).isEqualTo(0.615);
  }

  @Test
  void onLlmCall_recordsLedgerAndSummary() {
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(100_000, 20_000, 120_000));

    TokenomicsSummaryResponse summary = service.summary("loan-approval", null, null);
    assertThat(summary.calls()).isEqualTo(1);
    assertThat(summary.useCase()).isEqualTo("loan-approval");
    assertThat(summary.estimatedCostUsd()).isGreaterThan(0);
    assertThat(summary.availableUseCases()).contains("loan-approval", "all");

    TokenomicsBreakdownResponse breakdown =
        service.breakdown("loan-approval", null, null, "task");
    assertThat(breakdown.rows()).hasSize(1);
    assertThat(breakdown.rows().getFirst().key()).isEqualTo("AgentTask_CreditRisk");
  }

  @Test
  void summary_filtersByUseCase() {
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "document-intelligence",
        "gpt-5.4",
        "openai",
        new TokenUsage(10_000, 1_000, 11_000));

    assertThat(service.summary("other-case", null, null).calls()).isEqualTo(0);
    assertThat(service.summary("all", null, null).calls()).isEqualTo(1);
  }

  @Test
  void budgets_reportSpentWarnAndForecast() {
    // 1M prompt @ $2.50 + 1M completion @ $15 = $17.50 → exceeds $1 default limit
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(1_000_000, 1_000_000, 2_000_000));

    TokenomicsBudgetsResponse response = service.budgets("loan-approval");
    assertThat(response.budgets()).hasSize(1);
    TokenomicsBudgetView budget = response.budgets().getFirst();
    assertThat(budget.spentUsd()).isEqualTo(17.5);
    assertThat(budget.status()).isEqualTo("exceeded");
    assertThat(budget.forecastEndOfPeriodUsd()).isGreaterThan(budget.spentUsd());
    assertThat(budget.forecastExceedsLimit()).isTrue();
    assertThat(budget.periodStart()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
  }

  @Test
  void updateBudget_togglesHardLimitAndLimit() {
    service.updateBudget(new TokenomicsBudgetUpdate("loan-approval", 0.01, 0.5, true, null));
    TokenomicsBudgetView budget = service.budgets("loan-approval").budgets().getFirst();
    assertThat(budget.limitUsd()).isEqualTo(0.01);
    assertThat(budget.warnAt()).isEqualTo(0.5);
    assertThat(budget.hardLimit()).isTrue();
  }

  @Test
  void enforceHardBudget_blocksWhenExceeded() {
    service.updateBudget(new TokenomicsBudgetUpdate("loan-approval", 0.01, 0.8, true, null));
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(100_000, 20_000, 120_000));

    assertThatThrownBy(() -> service.enforceHardBudget("LoanApprovalProcess_AI"))
        .isInstanceOf(TokenomicsBudgetExceededException.class)
        .hasMessageContaining("loan-approval");
  }

  @Test
  void enforceHardBudget_allowsWhenSoftOnly() {
    service.updateBudget(new TokenomicsBudgetUpdate("loan-approval", 0.01, 0.8, false, null));
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(100_000, 20_000, 120_000));

    service.enforceHardBudget("LoanApprovalProcess_AI");
  }

  @Test
  void budgets_warnStatusBeforeExceed() {
    service.updateBudget(new TokenomicsBudgetUpdate("loan-approval", 1.0, 0.5, false, null));
    // ~$0.625 prompt-only at gpt-5.4 rates (250k * 2.5 / 1e6) → burn 62.5% ≥ warnAt 50%
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(250_000, 0, 250_000));

    TokenomicsBudgetView budget = service.budgets("loan-approval").budgets().getFirst();
    assertThat(budget.spentUsd()).isEqualTo(0.625);
    assertThat(budget.status()).isEqualTo("warn");
  }

  @Test
  void rates_includeConfiguredLiveModels() {
    assertThat(service.rates().rates())
        .extracting(TokenomicsRateView::model)
        .containsExactly("gpt-5.4", "gpt-5.6-luna", "claude-sonnet-5");
    assertThat(service.rates().rates())
        .extracting(TokenomicsRateView::provider)
        .containsExactly("openai", "openai", "anthropic");
    assertThat(service.rates().rates())
        .extracting(TokenomicsRateView::cacheInputPer1m)
        .containsExactly(0.25, 0.02, 0.20);
    assertThat(service.rates().rates())
        .extracting(TokenomicsRateView::cacheWritePer1m)
        .containsExactly(0.0, 0.25, 2.50);
  }

  @Test
  void breakdown_byProviderModel_separatesInstanceModels() {
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(10_000, 2_000, 12_000));
    service.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.6-luna",
        "openai",
        new TokenUsage(20_000, 4_000, 24_000));

    assertThat(service.breakdown("loan-approval", null, null, "providerModel").rows())
        .extracting(TokenomicsLedger.BreakdownRow::key)
        .containsExactly("openai/gpt-5.4", "openai/gpt-5.6-luna");
  }
}
