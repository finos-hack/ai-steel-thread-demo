package org.finos.ai.demo.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.ResolvedContext;
import org.finos.fluxnova.bpm.engine.ai.agent.llm.service.SpringAiLlmService;
import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.LlmResponse;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.observability.LlmTokenUsageMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class LoanApprovalLlmServiceMi1Test {

  @Mock SpringAiLlmService delegate;
  @Mock LlmTokenUsageMetrics tokenUsageMetrics;

  private LoanApprovalLlmService service;

  @BeforeEach
  void setUp() {
    Mi1DataLeakagePrevention dlp =
        new Mi1DataLeakagePrevention(new Mi1DlpProperties(), new SimpleMeterRegistry());
    @SuppressWarnings("unchecked")
    org.springframework.beans.factory.ObjectProvider<
            org.finos.ai.demo.tokenomics.TokenomicsService>
        tokenomics = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    org.mockito.Mockito.when(tokenomics.getIfAvailable()).thenReturn(null);
    service = new LoanApprovalLlmService(delegate, tokenUsageMetrics, dlp, tokenomics);
  }

  @Test
  void stripsForbiddenContextBeforeDelegateCall() {
    AgentConfig config =
        new AgentConfig(
            "LoanApprovalProcess_AI:1:abc",
            LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID,
            "openai",
            "gpt-5.4",
            "system",
            LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID);
    AgentToolCatalogue catalogue =
        new AgentToolCatalogue("def-1", LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID, List.of());
    ResolvedContext unsafe =
        new ResolvedContext(Map.of("creditScore", 710, "idNumber", "SECRET", "firstName", "Ada"));

    when(delegate.call(eq(config), eq(catalogue), any(ResolvedContext.class), isNull()))
        .thenReturn(new LlmResponse("ok", List.of(), List.of(), new TokenUsage(1, 1, 2)));

    service.call(config, catalogue, unsafe, null);

    ArgumentCaptor<ResolvedContext> contextCaptor = ArgumentCaptor.forClass(ResolvedContext.class);
    verify(delegate).call(eq(config), eq(catalogue), contextCaptor.capture(), isNull());
    assertThat(contextCaptor.getValue().variables())
        .containsOnlyKeys("creditScore")
        .doesNotContainKeys("idNumber", "firstName");
    verify(tokenUsageMetrics)
        .recordCall(
            eq("LoanApprovalProcess_AI"),
            eq(LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID),
            eq("gpt-5.4"),
            eq("openai"),
            eq("gpt-5.4"),
            org.mockito.ArgumentMatchers.anyLong(),
            eq(new TokenUsage(1, 1, 2)),
            eq(true),
            isNull(),
            eq(java.util.List.of("creditScore")));
  }
}
