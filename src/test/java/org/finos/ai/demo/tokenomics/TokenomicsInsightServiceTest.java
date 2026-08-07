package org.finos.ai.demo.tokenomics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class TokenomicsInsightServiceTest {

  private TokenomicsService tokenomicsService;
  private TokenomicsInsightService insightService;

  @BeforeEach
  void setUp() {
    TokenomicsProperties properties = new TokenomicsProperties();
    TokenomicsLedger ledger = new TokenomicsLedger();
    TokenomicsCostService costService = new TokenomicsCostService(properties);
    @SuppressWarnings("unchecked")
    ObjectProvider<org.finos.ai.demo.observability.ProcessScopedLlmTokenUsageWriter> writer =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(writer.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<Clock> clockProvider = org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(clockProvider.getIfAvailable())
        .thenReturn(Clock.fixed(Instant.parse("2026-08-15T12:00:00Z"), ZoneOffset.UTC));
    tokenomicsService =
        new TokenomicsService(
            properties, costService, ledger, new SimpleMeterRegistry(), writer, clockProvider);

    @SuppressWarnings("unchecked")
    ObjectProvider<org.finos.ai.demo.observability.Mi4ObservabilityProperties> mi4 =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(mi4.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<TokenomicsService> tokenomicsProvider =
        org.mockito.Mockito.mock(ObjectProvider.class);
    org.mockito.Mockito.when(tokenomicsProvider.getIfAvailable()).thenReturn(tokenomicsService);

    insightService =
        new TokenomicsInsightService(properties, ledger, costService, mi4, tokenomicsProvider);
  }

  @Test
  void insights_emptyLedger_hasInfoTip() {
    TokenomicsInsightsResponse response = insightService.insights("all");
    assertThat(response.tips()).extracting(TokenomicsInsightTip::id).contains("empty-ledger");
    assertThat(response.grafanaUrl()).contains("fluxnova-ai-observability");
  }

  @Test
  void insights_afterLiveCalls_groupsByProviderAndModel() {
    tokenomicsService.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.4",
        "openai",
        new TokenUsage(50_000, 10_000, 60_000));
    tokenomicsService.onLlmCall(
        "LoanApprovalProcess_AI",
        "AgentTask_CreditRisk",
        "gpt-5.6-luna",
        "openai",
        new TokenUsage(80_000, 20_000, 100_000));

    TokenomicsInsightsResponse response = insightService.insights("loan-approval");
    assertThat(response.providers()).containsExactly("openai");
    assertThat(response.models()).contains("gpt-5.4", "gpt-5.6-luna");
    assertThat(response.tips()).extracting(TokenomicsInsightTip::id).contains("single-provider");

    assertThat(tokenomicsService.breakdown("loan-approval", null, null, "providerModel").rows())
        .extracting(TokenomicsLedger.BreakdownRow::key)
        .contains("openai/gpt-5.4", "openai/gpt-5.6-luna");
  }
}
