package org.finos.ai.demo.agent;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolCatalogue;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.model.AgentToolEntry;
import org.finos.fluxnova.bpm.engine.ai.agent.discovery.registry.AgentToolCatalogueRegistry;
import org.finos.fluxnova.bpm.engine.ai.agent.orchestrator.state.AgentStateManager;
import org.finos.fluxnova.bpm.engine.ai.agent.service.ToolInvocationService;
import org.finos.fluxnova.bpm.engine.runtime.Execution;
import org.finos.fluxnova.bpm.engine.runtime.ExecutionQuery;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstanceQuery;
import org.finos.fluxnova.bpm.engine.shared.model.ConversationEntry;
import org.finos.ai.demo.worker.credit.CheckCreditScoreWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditRiskAwareTerminationHandlerTest {

  private static final String SCOPE_ID = "scope-1";
  private static final String PI_ID = "pi-1";
  private static final String DEF_ID = "def-1";

  @Mock RepositoryService repositoryService;
  @Mock AgentToolCatalogueRegistry toolCatalogueRegistry;
  @Mock AgentStateManager stateManager;
  @Mock ToolInvocationService toolInvocationService;
  @Mock RuntimeService runtimeService;
  @Mock ProcessInstanceQuery processInstanceQuery;
  @Mock ExecutionQuery executionQuery;
  @Mock ProcessInstance processInstance;
  @Mock Execution execution;

  CreditRiskAwareTerminationHandler handler;

  @BeforeEach
  void setUp() {
    handler =
        new CreditRiskAwareTerminationHandler(
            repositoryService, toolCatalogueRegistry, stateManager, toolInvocationService);
  }

  @Test
  void complete_withCreditScore_publishesTraceToProcessInstanceAndClearsLocals() {
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(null, processInstance);
    when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
    when(executionQuery.executionId(SCOPE_ID)).thenReturn(executionQuery);
    when(executionQuery.singleResult()).thenReturn(execution);
    when(execution.getProcessInstanceId()).thenReturn(PI_ID);
    when(processInstance.getId()).thenReturn(PI_ID);
    when(runtimeService.getActiveActivityIds(SCOPE_ID))
        .thenReturn(List.of(LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID));
    when(runtimeService.getVariable(PI_ID, CheckCreditScoreWorker.VAR_CREDIT_SCORE)).thenReturn(720);
    when(stateManager.loadHistory(runtimeService, SCOPE_ID))
        .thenReturn(List.of(ConversationEntry.assistant("Done.", List.of())));
    when(runtimeService.getVariableLocal(SCOPE_ID, "_agentConversationHistory"))
        .thenReturn("[{\"role\":\"ASSISTANT\",\"content\":\"Done.\"}]");
    when(runtimeService.getVariablesLocal(SCOPE_ID))
        .thenReturn(Map.of("_agentConversationHistory", "[]", "adHocActivityStarted", true));

    handler.complete(runtimeService, SCOPE_ID);

    verify(runtimeService).setVariables(eq(PI_ID), anyMap());
    verify(runtimeService)
        .removeVariablesLocal(
            eq(SCOPE_ID), eq(Set.of("_agentConversationHistory", "adHocActivityStarted")));
    verify(runtimeService).completeAdHocSubProcess(SCOPE_ID);
    verify(runtimeService, never()).completeAdHocSubProcess(eq(SCOPE_ID), anyMap());
  }

  @Test
  void complete_withoutCreditScore_dispatchesMandatoryTool() {
    when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
    when(processInstanceQuery.processInstanceId(anyString())).thenReturn(processInstanceQuery);
    when(processInstanceQuery.singleResult()).thenReturn(null, processInstance);
    when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
    when(executionQuery.executionId(SCOPE_ID)).thenReturn(executionQuery);
    when(executionQuery.singleResult()).thenReturn(execution);
    when(execution.getProcessInstanceId()).thenReturn(PI_ID);
    when(processInstance.getId()).thenReturn(PI_ID);
    when(processInstance.getProcessDefinitionId()).thenReturn(DEF_ID);
    when(runtimeService.getActiveActivityIds(SCOPE_ID))
        .thenReturn(List.of(LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID));
    when(runtimeService.getVariable(PI_ID, CheckCreditScoreWorker.VAR_CREDIT_SCORE)).thenReturn(null);

    AgentToolCatalogue catalogue =
        new AgentToolCatalogue(
            DEF_ID,
            LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID,
            List.of(
                new AgentToolEntry(
                    LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID,
                    "Check credit",
                    "desc",
                    Set.of(),
                    Set.of("creditScore"))));
    when(toolCatalogueRegistry.resolve(
            repositoryService, DEF_ID, LoanApprovalAgentConfiguration.CREDIT_RISK_AGENT_ID))
        .thenReturn(java.util.Optional.of(catalogue));
    when(toolInvocationService.invoke(any(), any(), any(), any()))
        .thenReturn(
            org.finos.fluxnova.bpm.engine.shared.model.ToolInvocationResult.success("tc-1"));
    when(stateManager.loadHistory(runtimeService, SCOPE_ID))
        .thenReturn(List.of(ConversationEntry.assistant("Skipping credit score.", List.of())));

    handler.complete(runtimeService, SCOPE_ID);

    verify(stateManager)
        .saveHistory(
            eq(runtimeService),
            eq(SCOPE_ID),
            org.mockito.ArgumentMatchers.argThat(
                history ->
                    history.size() == 2
                        && history.get(1).toolCalls() != null
                        && history.get(1).toolCalls().size() == 1
                        && history
                            .get(1)
                            .toolCalls()
                            .get(0)
                            .toolId()
                            .equals(LoanApprovalAgentConfigRegistry.CREDIT_SCORE_TOOL_ID)
                        && history
                            .get(1)
                            .toolCalls()
                            .get(0)
                            .toolCallId()
                            .startsWith("required-credit-score-")));
    verify(toolInvocationService).invoke(any(), eq(SCOPE_ID), eq(catalogue), any());
    verify(runtimeService, never()).completeAdHocSubProcess(anyString());
    verify(runtimeService, never()).completeAdHocSubProcess(anyString(), anyMap());
  }
}
