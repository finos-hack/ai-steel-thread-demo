package org.finos.ai.demo.observability;

import java.util.List;
import java.util.Map;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.impl.context.BpmnExecutionContext;
import org.finos.fluxnova.bpm.engine.impl.context.Context;
import org.finos.fluxnova.bpm.engine.impl.jobexecutor.JobExecutorContext;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.JobEntity;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Accumulates LLM token usage onto the current BPMN execution / process instance so the demo
 * status API can attribute them to agentic activities.
 *
 * <p>Agent orchestration runs as a job handler where {@link BpmnExecutionContext} is often unset,
 * so this also resolves the execution from {@link JobExecutorContext}. Tokens are stored as
 * process-instance variables keyed by activity id so they survive ad-hoc local-variable cleanup.
 */
@Component
public class ProcessScopedLlmTokenUsageWriter {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessScopedLlmTokenUsageWriter.class);

  private final ObjectProvider<RuntimeService> runtimeService;

  public ProcessScopedLlmTokenUsageWriter(ObjectProvider<RuntimeService> runtimeService) {
    this.runtimeService = runtimeService;
  }

  public void accumulateOnCurrentExecution(TokenUsage usage) {
    if (usage == null) {
      return;
    }
    ExecutionEntity execution = resolveCurrentExecution();
    if (execution == null) {
      LOG.debug("No BPMN/job execution in context; skipping process-scoped token persistence");
      return;
    }
    RuntimeService service = runtimeService.getIfAvailable();
    if (service == null) {
      return;
    }

    String activityId = execution.getActivityId();
    String processInstanceId = execution.getProcessInstanceId();
    if (activityId == null || activityId.isBlank() || processInstanceId == null) {
      return;
    }

    TokenUsage merged =
        LlmTokenUsageVariables.merge(
            LlmTokenUsageVariables.fromVariables(
                service.getVariable(
                    processInstanceId, LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.PROMPT_TOKENS, activityId)),
                service.getVariable(
                    processInstanceId,
                    LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.COMPLETION_TOKENS, activityId)),
                service.getVariable(
                    processInstanceId,
                    LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.TOTAL_TOKENS, activityId))),
            usage);

    service.setVariables(processInstanceId, LlmTokenUsageVariables.toScopedVariables(activityId, merged));

    // Also keep activity-local copies while the scope is alive (useful mid-run).
    try {
      service.setVariablesLocal(execution.getId(), LlmTokenUsageVariables.toVariables(merged));
    } catch (RuntimeException exception) {
      LOG.debug(
          "Could not set local token variables on execution {}: {}",
          execution.getId(),
          exception.getMessage());
    }

    LOG.info(
        "Recorded LLM tokens processInstance={} activity={}: prompt={} completion={} total={}",
        processInstanceId,
        activityId,
        merged.promptTokens(),
        merged.completionTokens(),
        merged.totalTokens());
  }

  /** Accumulates estimated USD cost onto the current process instance (activity-scoped). */
  public void accumulateEstimatedCostOnCurrentExecution(double estimatedCostUsd) {
    ExecutionEntity execution = resolveCurrentExecution();
    if (execution == null) {
      return;
    }
    RuntimeService service = runtimeService.getIfAvailable();
    if (service == null) {
      return;
    }
    String activityId = execution.getActivityId();
    String processInstanceId = execution.getProcessInstanceId();
    if (activityId == null || activityId.isBlank() || processInstanceId == null) {
      return;
    }
    String costVar =
        LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.ESTIMATED_COST_USD, activityId);
    Double existing = LlmTokenUsageVariables.costFromVariable(service.getVariable(processInstanceId, costVar));
    double merged = LlmTokenUsageVariables.mergeCost(existing, estimatedCostUsd);
    service.setVariables(
        processInstanceId, LlmTokenUsageVariables.toScopedCostVariables(activityId, merged));
  }

  /** Persists sanitized last-call metadata (mi-4) on the process instance. */
  public void recordCallMetadataOnCurrentExecution(
      String model,
      String provider,
      String version,
      long durationMs,
      String status,
      List<String> contextKeys,
      String errorType) {
    ExecutionEntity execution = resolveCurrentExecution();
    if (execution == null) {
      return;
    }
    RuntimeService service = runtimeService.getIfAvailable();
    if (service == null) {
      return;
    }
    String activityId = execution.getActivityId();
    String processInstanceId = execution.getProcessInstanceId();
    if (activityId == null || activityId.isBlank() || processInstanceId == null) {
      return;
    }

    Map<String, Object> variables =
        LlmCallMetadataVariables.toScopedVariables(
            activityId, model, provider, version, durationMs, status, contextKeys, errorType);
    if (variables.isEmpty()) {
      return;
    }
    service.setVariables(processInstanceId, variables);
  }

  private static ExecutionEntity resolveCurrentExecution() {
    BpmnExecutionContext executionContext = Context.getBpmnExecutionContext();
    if (executionContext != null && executionContext.getExecution() != null) {
      return executionContext.getExecution();
    }

    JobEntity currentJob = null;
    JobExecutorContext jobExecutorContext = Context.getJobExecutorContext();
    if (jobExecutorContext != null) {
      currentJob = jobExecutorContext.getCurrentJob();
    }
    if (currentJob == null && Context.getCommandContext() != null) {
      currentJob = Context.getCommandContext().getCurrentJob();
    }
    return currentJob == null ? null : currentJob.getExecution();
  }
}
