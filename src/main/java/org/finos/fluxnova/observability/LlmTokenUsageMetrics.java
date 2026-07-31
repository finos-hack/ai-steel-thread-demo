package org.finos.fluxnova.observability;

import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;

import io.micrometer.core.instrument.MeterRegistry;

public final class LlmTokenUsageMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(LlmTokenUsageMetrics.class);

  private static final String TOKENS = "fluxnova.llm.tokens";
  private static final String CALLS = "fluxnova.llm.calls";

  private final MeterRegistry registry;

  public LlmTokenUsageMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void record(AgentConfig agentConfig, String agentTaskId, TokenUsage usage) {
    record(
        workflowKey(agentConfig.processDefinitionId()),
        agentTaskId == null || agentTaskId.isBlank() ? agentConfig.elementId() : agentTaskId,
        agentConfig.model() == null ? "unknown" : agentConfig.model(),
        agentConfig.provider() == null ? "unknown" : agentConfig.provider(),
        usage);
  }

  public void recordFromChatResponse(
      String workflow, String task, String model, String provider, ChatResponse response) {
    record(workflow, task, model, provider, SpringAiTokenUsageExtractor.from(response));
  }

  public void record(
      String workflow, String task, String model, String provider, TokenUsage usage) {
    if (usage == null) {
      return;
    }

    String resolvedWorkflow = workflow == null || workflow.isBlank() ? "unknown" : workflow;
    String resolvedTask = task == null || task.isBlank() ? "unknown" : task;
    String resolvedModel = model == null || model.isBlank() ? "unknown" : model;
    String resolvedProvider = provider == null || provider.isBlank() ? "unknown" : provider;

    if (usage.promptTokens() > 0) {
      registry.counter(TOKENS, tags(resolvedWorkflow, resolvedTask, resolvedModel, resolvedProvider, "prompt"))
          .increment(usage.promptTokens());
    }
    if (usage.completionTokens() > 0) {
      registry.counter(TOKENS, tags(resolvedWorkflow, resolvedTask, resolvedModel, resolvedProvider, "completion"))
          .increment(usage.completionTokens());
    }
    registry.counter(
            CALLS,
            "workflow", resolvedWorkflow,
            "task", resolvedTask,
            "model", resolvedModel,
            "provider", resolvedProvider)
        .increment();

    LOG.info(
        "LLM tokens workflow={} task={} provider={} model={} prompt={} completion={} total={}",
        resolvedWorkflow,
        resolvedTask,
        resolvedProvider,
        resolvedModel,
        usage.promptTokens(),
        usage.completionTokens(),
        usage.totalTokens());
  }

  private static String[] tags(
      String workflow, String task, String model, String provider, String tokenType) {
    return new String[] {
        "workflow", workflow,
        "task", task,
        "model", model,
        "provider", provider,
        "token_type", tokenType
    };
  }

  public static String workflowKey(String processDefinitionId) {
    if (processDefinitionId == null || processDefinitionId.isBlank()) {
      return "unknown";
    }
    int colon = processDefinitionId.indexOf(':');
    return colon > 0 ? processDefinitionId.substring(0, colon) : processDefinitionId;
  }

}
