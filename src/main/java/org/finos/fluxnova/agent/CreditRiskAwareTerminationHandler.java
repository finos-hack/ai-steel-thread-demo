package org.finos.fluxnova.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.service.AgentTerminationHandler;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.trace.AgentTraceExporter;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.runtime.Execution;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.shared.model.ToolCallRequest;
import org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult;
import org.finos.fluxnova.worker.credit.CheckCreditScoreWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures {@code ServiceTask_CreditScoreCheck} runs before the credit risk ad-hoc subprocess
 * completes, even when the LLM tries to terminate after only running employment verification.
 */
public class CreditRiskAwareTerminationHandler implements AgentTerminationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(CreditRiskAwareTerminationHandler.class);

  private final RepositoryService repositoryService;
  private final AgentToolCatalogueRegistry toolCatalogueRegistry;
  private final AgentStateManager stateManager;
  private final ToolInvocationService toolInvocationService;

  public CreditRiskAwareTerminationHandler(
      RepositoryService repositoryService,
      AgentToolCatalogueRegistry toolCatalogueRegistry,
      AgentStateManager stateManager,
      ToolInvocationService toolInvocationService) {
    this.repositoryService = repositoryService;
    this.toolCatalogueRegistry = toolCatalogueRegistry;
    this.stateManager = stateManager;
    this.toolInvocationService = toolInvocationService;
  }

  @Override
  public void complete(RuntimeService runtimeService, String scopeExecutionId) {
    ProcessInstance processInstance =
        runtimeService
            .createProcessInstanceQuery()
            .processInstanceId(resolveProcessInstanceId(runtimeService, scopeExecutionId))
            .singleResult();
    if (processInstance == null) {
      completeWithTrace(runtimeService, scopeExecutionId);
      return;
    }

    List<String> activeActivities = runtimeService.getActiveActivityIds(scopeExecutionId);
    if (activeActivities == null
        || !activeActivities.contains(LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID)) {
      completeWithTrace(runtimeService, scopeExecutionId);
      return;
    }

    if (runtimeService.getVariable(
            processInstance.getId(), CheckCreditScoreWorker.VAR_CREDIT_SCORE)
        != null) {
      completeWithTrace(runtimeService, scopeExecutionId);
      return;
    }

    AgentToolCatalogue catalogue =
        toolCatalogueRegistry
            .resolve(
                repositoryService,
                processInstance.getProcessDefinitionId(),
                LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID)
            .orElse(null);
    if (catalogue == null
        || catalogue.findById(LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID).isEmpty()) {
      LOG.warn(
          "Credit score tool is unavailable for scope {}; completing ad-hoc subprocess anyway",
          scopeExecutionId);
      completeWithTrace(runtimeService, scopeExecutionId);
      return;
    }

    String toolCallId = "required-credit-score-" + UUID.randomUUID();
    LOG.info(
        "Credit score missing for scope {}; dispatching mandatory {} before completing ad-hoc subprocess",
        scopeExecutionId,
        LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID);

    stateManager.savePendingToolCalls(runtimeService, scopeExecutionId, Set.of(toolCallId));
    ToolInvocationResult result =
        toolInvocationService.invoke(
            runtimeService,
            scopeExecutionId,
            catalogue,
            new ToolCallRequest(toolCallId, LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID));
    if (!result.success()) {
      LOG.warn(
          "Mandatory credit score dispatch failed for scope {}: {}",
          scopeExecutionId,
          result.errorMessage());
      completeWithTrace(runtimeService, scopeExecutionId);
    }
  }

  private void completeWithTrace(RuntimeService runtimeService, String scopeExecutionId) {
    Map<String, Object> traceVariables =
        AgentTraceExporter.exportTraceVariables(stateManager, runtimeService, scopeExecutionId);
    if (traceVariables.isEmpty()) {
      runtimeService.completeAdHocSubProcess(scopeExecutionId);
      return;
    }
    runtimeService.completeAdHocSubProcess(scopeExecutionId, traceVariables);
  }

  private static String resolveProcessInstanceId(
      RuntimeService runtimeService, String scopeExecutionId) {
    ProcessInstance byScope =
        runtimeService
            .createProcessInstanceQuery()
            .processInstanceId(scopeExecutionId)
            .singleResult();
    if (byScope != null) {
      return byScope.getId();
    }

    Execution execution =
        runtimeService.createExecutionQuery().executionId(scopeExecutionId).singleResult();
    if (execution == null) {
      return scopeExecutionId;
    }
    return execution.getProcessInstanceId();
  }
}
