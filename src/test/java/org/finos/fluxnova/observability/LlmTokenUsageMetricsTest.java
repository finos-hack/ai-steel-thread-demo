package org.finos.fluxnova.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LlmTokenUsageMetricsTest {

  @Test
  void recordsPromptAndCompletionTokensPerWorkflowAndTask() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LlmTokenUsageMetrics metrics = new LlmTokenUsageMetrics(registry);
    AgentConfig config = new AgentConfig(
        "LoanApprovalProcess_AI:1:abc",
        "AgentTask_CreditRisk",
        "openai",
        "gpt-4o",
        "prompt",
        "AgentTask_CreditRisk");

    metrics.record(config, "AgentTask_CreditRisk", new TokenUsage(120, 30, 150));

    assertThat(registry.find("fluxnova.llm.tokens")
        .tag("workflow", "LoanApprovalProcess_AI")
        .tag("task", "AgentTask_CreditRisk")
        .tag("token_type", "prompt")
        .counter()
        .count()).isEqualTo(120);
    assertThat(registry.find("fluxnova.llm.tokens")
        .tag("token_type", "completion")
        .counter()
        .count()).isEqualTo(30);
    assertThat(registry.find("fluxnova.llm.calls").counter().count()).isEqualTo(1);
  }

  @Test
  void recordsDocumentIntelligenceTaskDirectly() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    LlmTokenUsageMetrics metrics = new LlmTokenUsageMetrics(registry);

    metrics.record(
        "LoanApprovalProcess_AI",
        "document-intelligence",
        "gpt-5.4",
        "openai",
        new TokenUsage(800, 120, 920));

    assertThat(registry.find("fluxnova.llm.tokens")
        .tag("workflow", "LoanApprovalProcess_AI")
        .tag("task", "document-intelligence")
        .tag("model", "gpt-5.4")
        .tag("provider", "openai")
        .tag("token_type", "prompt")
        .counter()
        .count()).isEqualTo(800);
    assertThat(registry.find("fluxnova.llm.tokens")
        .tag("task", "document-intelligence")
        .tag("token_type", "completion")
        .counter()
        .count()).isEqualTo(120);
    assertThat(registry.find("fluxnova.llm.calls")
        .tag("task", "document-intelligence")
        .counter()
        .count()).isEqualTo(1);
  }

}
