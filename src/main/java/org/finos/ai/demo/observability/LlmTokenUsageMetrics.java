package org.finos.ai.demo.observability;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.finos.fluxnova.bpm.engine.ai.agent.model.AgentConfig;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.ai.demo.tokenomics.TokenomicsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.ObjectProvider;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * mi-4 AI observability: tokens, call counts, latency, errors, and sanitized audit lines.
 */
public final class LlmTokenUsageMetrics {

  private static final Logger LOG = LoggerFactory.getLogger(LlmTokenUsageMetrics.class);

  private static final String TOKENS = "fluxnova.llm.tokens";
  private static final String CALLS = "fluxnova.llm.calls";
  private static final String DURATION = "fluxnova.llm.duration";
  private static final String ERRORS = "fluxnova.llm.errors";

  private final MeterRegistry registry;
  private final ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter;
  private final ObjectProvider<Mi4ObservabilityProperties> mi4Properties;
  private final ObjectProvider<TokenomicsService> tokenomicsService;

  public LlmTokenUsageMetrics(MeterRegistry registry) {
    this(registry, null, null, null);
  }

  public LlmTokenUsageMetrics(
      MeterRegistry registry, ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter) {
    this(registry, processScopedWriter, null, null);
  }

  public LlmTokenUsageMetrics(
      MeterRegistry registry,
      ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter,
      ObjectProvider<Mi4ObservabilityProperties> mi4Properties) {
    this(registry, processScopedWriter, mi4Properties, null);
  }

  public LlmTokenUsageMetrics(
      MeterRegistry registry,
      ObjectProvider<ProcessScopedLlmTokenUsageWriter> processScopedWriter,
      ObjectProvider<Mi4ObservabilityProperties> mi4Properties,
      ObjectProvider<TokenomicsService> tokenomicsService) {
    this.registry = registry;
    this.processScopedWriter = processScopedWriter;
    this.mi4Properties = mi4Properties;
    this.tokenomicsService = tokenomicsService;
  }

  public void record(AgentConfig agentConfig, String agentTaskId, TokenUsage usage) {
    recordCall(
        workflowKey(agentConfig.processDefinitionId()),
        agentTaskId == null || agentTaskId.isBlank() ? agentConfig.elementId() : agentTaskId,
        agentConfig.model() == null ? "unknown" : agentConfig.model(),
        agentConfig.provider() == null ? "unknown" : agentConfig.provider(),
        modelVersionLabel(),
        0L,
        usage,
        true,
        null,
        List.of());
  }

  public void recordFromChatResponse(
      String workflow, String task, String model, String provider, ChatResponse response) {
    record(workflow, task, model, provider, SpringAiTokenUsageExtractor.from(response));
  }

  public TokenUsage recordFromChatResponseAndReturn(
      String workflow, String task, String model, String provider, ChatResponse response) {
    TokenUsage usage = SpringAiTokenUsageExtractor.from(response);
    record(workflow, task, model, provider, usage);
    return usage;
  }

  public void record(
      String workflow, String task, String model, String provider, TokenUsage usage) {
    recordCall(workflow, task, model, provider, modelVersionLabel(), 0L, usage, true, null, List.of());
  }

  /**
   * Full mi-4 call recording: tokens, latency, success/error, sanitized audit, process metadata.
   * Token/call counters always emit; latency, errors, audit, and process metadata honor mi-4 flags.
   */
  public void recordCall(
      String workflow,
      String task,
      String model,
      String provider,
      String modelVersion,
      long durationMs,
      TokenUsage usage,
      boolean success,
      String errorType,
      Collection<String> contextKeys) {
    String resolvedWorkflow = blankToUnknown(workflow);
    String resolvedTask = blankToUnknown(task);
    String resolvedModel = blankToUnknown(model);
    String resolvedProvider = blankToUnknown(provider);
    String resolvedVersion = blankToUnknown(modelVersion);
    List<String> keys =
        contextKeys == null
            ? List.of()
            : contextKeys.stream().filter(k -> k != null && !k.isBlank()).sorted().toList();

    if (usage != null) {
      if (usage.promptTokens() > 0) {
        registry
            .counter(
                TOKENS,
                tags(resolvedWorkflow, resolvedTask, resolvedModel, resolvedProvider, "prompt"))
            .increment(usage.promptTokens());
      }
      if (usage.completionTokens() > 0) {
        registry
            .counter(
                TOKENS,
                tags(
                    resolvedWorkflow,
                    resolvedTask,
                    resolvedModel,
                    resolvedProvider,
                    "completion"))
            .increment(usage.completionTokens());
      }
    }

    registry
        .counter(
            CALLS,
            "workflow",
            resolvedWorkflow,
            "task",
            resolvedTask,
            "model",
            resolvedModel,
            "provider",
            resolvedProvider,
            "status",
            success ? "success" : "failure")
        .increment();

    if (mi4Enabled() && durationMs > 0) {
      Timer.builder(DURATION)
          .tag("workflow", resolvedWorkflow)
          .tag("task", resolvedTask)
          .tag("model", resolvedModel)
          .tag("provider", resolvedProvider)
          .tag("model_version", resolvedVersion)
          .register(registry)
          .record(durationMs, TimeUnit.MILLISECONDS);
    }

    if (mi4Enabled() && !success) {
      registry
          .counter(
              ERRORS,
              "workflow",
              resolvedWorkflow,
              "task",
              resolvedTask,
              "model",
              resolvedModel,
              "provider",
              resolvedProvider,
              "error_type",
              blankToUnknown(errorType))
          .increment();
    }

    if (sanitizedAuditEnabled()) {
      LOG.info(
          "LLM audit workflow={} task={} provider={} model={} version={} durationMs={} status={} "
              + "promptTokens={} completionTokens={} totalTokens={} contextKeys={} errorType={}",
          resolvedWorkflow,
          resolvedTask,
          resolvedProvider,
          resolvedModel,
          resolvedVersion,
          durationMs,
          success ? "success" : "failure",
          usage == null ? 0 : usage.promptTokens(),
          usage == null ? 0 : usage.completionTokens(),
          usage == null ? 0 : usage.totalTokens(),
          keys.isEmpty() ? "-" : String.join(",", keys),
          success ? "-" : blankToUnknown(errorType));
    } else if (usage != null) {
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

    if (processScopedWriter != null) {
      ProcessScopedLlmTokenUsageWriter writer = processScopedWriter.getIfAvailable();
      if (writer != null) {
        if (usage != null) {
          writer.accumulateOnCurrentExecution(usage);
        }
        if (mi4Enabled() && persistCallMetadata()) {
          writer.recordCallMetadataOnCurrentExecution(
              resolvedModel,
              resolvedProvider,
              resolvedVersion,
              durationMs,
              success ? "success" : "failure",
              keys,
              success ? null : errorType);
        }
      }
    }

    if (tokenomicsService != null && usage != null) {
      TokenomicsService tokenomics = tokenomicsService.getIfAvailable();
      if (tokenomics != null) {
        tokenomics.onLlmCall(
            resolvedWorkflow, resolvedTask, resolvedModel, resolvedProvider, usage);
      }
    }
  }

  private boolean mi4Enabled() {
    if (mi4Properties == null) {
      return true;
    }
    Mi4ObservabilityProperties props = mi4Properties.getIfAvailable();
    return props == null || props.isEnabled();
  }

  private boolean sanitizedAuditEnabled() {
    if (mi4Properties == null) {
      return true;
    }
    Mi4ObservabilityProperties props = mi4Properties.getIfAvailable();
    return props == null || (props.isEnabled() && props.isSanitizedAuditLog());
  }

  private boolean persistCallMetadata() {
    if (mi4Properties == null) {
      return true;
    }
    Mi4ObservabilityProperties props = mi4Properties.getIfAvailable();
    return props == null || (props.isEnabled() && props.isPersistCallMetadata());
  }

  private String modelVersionLabel() {
    if (mi4Properties == null) {
      return "pinned-config";
    }
    Mi4ObservabilityProperties props = mi4Properties.getIfAvailable();
    if (props == null || props.getModelVersionLabel() == null || props.getModelVersionLabel().isBlank()) {
      return "pinned-config";
    }
    return props.getModelVersionLabel();
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

  private static String blankToUnknown(String value) {
    return value == null || value.isBlank() ? "unknown" : value;
  }

  public static String workflowKey(String processDefinitionId) {
    if (processDefinitionId == null || processDefinitionId.isBlank()) {
      return "unknown";
    }
    int colon = processDefinitionId.indexOf(':');
    return colon > 0 ? processDefinitionId.substring(0, colon) : processDefinitionId;
  }
}
