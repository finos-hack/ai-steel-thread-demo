package org.finos.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.finos.fluxnova.bpm.engine.HistoryService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstanceQuery;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstanceQuery;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.finos.fluxnova.bpm.engine.task.TaskQuery;
import org.finos.ai.demo.feedback.Mi11FeedbackVariables;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class HumanTaskServiceTest {

  private TaskService taskService;
  private HistoryService historyService;
  private HumanTaskService service;
  private TaskQuery taskQuery;
  private HistoricProcessInstanceQuery processQuery;
  private HistoricVariableInstanceQuery variableQuery;

  @BeforeEach
  void setUp() {
    taskService = mock(TaskService.class);
    historyService = mock(HistoryService.class);
    taskQuery = mock(TaskQuery.class);
    processQuery = mock(HistoricProcessInstanceQuery.class);
    variableQuery = mock(HistoricVariableInstanceQuery.class);
    service =
        new HumanTaskService(
            taskService, historyService, "LoanApprovalProcess_AI", "demo");

    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processDefinitionKey("LoanApprovalProcess_AI")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
    when(taskQuery.desc()).thenReturn(taskQuery);
    when(taskQuery.taskId(any())).thenReturn(taskQuery);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId(any())).thenReturn(processQuery);
    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId(any())).thenReturn(variableQuery);
    when(variableQuery.disableBinaryFetching()).thenReturn(variableQuery);
    when(variableQuery.disableCustomObjectDeserialization()).thenReturn(variableQuery);
    when(variableQuery.list()).thenReturn(List.of());
  }

  @Test
  void listOpenTasks_returnsSummaries() {
    Task task = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, null);
    when(taskQuery.list()).thenReturn(List.of(task));
    stubBusinessKey("pi-1", "loan-1");

    HumanTaskList list = service.listOpenTasks(null);

    assertThat(list.totalCount()).isEqualTo(1);
    assertThat(list.tasks().getFirst().taskId()).isEqualTo("task-1");
    assertThat(list.tasks().getFirst().claimed()).isFalse();
    assertThat(list.tasks().getFirst().businessKey()).isEqualTo("loan-1");
  }

  @Test
  void claim_assignsDemoUser() {
    Task unclaimed = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, null);
    Task claimed = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, "demo");
    when(taskQuery.singleResult()).thenReturn(unclaimed, claimed);
    stubBusinessKey("pi-1", "loan-1");

    HumanTaskDetail detail = service.claim("task-1");

    verify(taskService).claim("task-1", "demo");
    assertThat(detail.claimed()).isTrue();
    assertThat(detail.assignee()).isEqualTo("demo");
  }

  @Test
  void claim_conflictsWhenClaimedByOther() {
    Task claimed = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, "alice");
    when(taskQuery.singleResult()).thenReturn(claimed);

    assertThatThrownBy(() -> service.claim("task-1"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
  }

  @Test
  @SuppressWarnings("unchecked")
  void complete_loanOfficerWritesDecision() {
    Task claimed = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, "demo");
    when(taskQuery.singleResult()).thenReturn(claimed);
    stubBusinessKey("pi-1", "loan-1");
    AtomicReference<Map<String, Object>> completed = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              completed.set(invocation.getArgument(1));
              return null;
            })
        .when(taskService)
        .complete(eq("task-1"), any(Map.class));

    service.complete(
        "task-1",
        new HumanTaskCompleteRequest(
            Map.of(
                Mi11FeedbackVariables.LOAN_OFFICER_DECISION,
                "approve",
                Mi11FeedbackVariables.AGREES_WITH_AI,
                true,
                Mi11FeedbackVariables.COMMENT,
                "ok")));

    assertThat(completed.get().get(Mi11FeedbackVariables.LOAN_OFFICER_DECISION))
        .isEqualTo("APPROVE");
    assertThat(completed.get().get(Mi11FeedbackVariables.AGREES_WITH_AI)).isEqualTo(true);
    assertThat(completed.get().get(Mi11FeedbackVariables.COMMENT)).isEqualTo("ok");
    assertThat(completed.get().get(Mi11FeedbackVariables.SUBMITTED_AT)).isNotNull();
  }

  @Test
  void complete_rejectsMissingRequiredField() {
    Task claimed = mockTask("task-1", HumanTaskSchemas.COMPLIANCE_OFFICER, "demo");
    when(taskQuery.singleResult()).thenReturn(claimed);

    assertThatThrownBy(
            () -> service.complete("task-1", new HumanTaskCompleteRequest(Map.of())))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            ex -> {
              ResponseStatusException response = (ResponseStatusException) ex;
              assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(response.getReason()).contains("complianceStatus");
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void complete_complianceAcceptsCleared() {
    Task claimed = mockTask("task-2", HumanTaskSchemas.COMPLIANCE_OFFICER, "demo");
    when(taskQuery.singleResult()).thenReturn(claimed);
    stubBusinessKey("pi-1", "loan-1");
    AtomicReference<Map<String, Object>> completed = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              completed.set(invocation.getArgument(1));
              return null;
            })
        .when(taskService)
        .complete(eq("task-2"), any(Map.class));

    service.complete(
        "task-2", new HumanTaskCompleteRequest(Map.of("complianceStatus", "CLEARED")));

    assertThat(completed.get().get("complianceStatus")).isEqualTo("CLEARED");
  }

  @Test
  void getTask_loanOfficerIncludesUnredactedAgentConversation() {
    Task task = mockTask("task-1", HumanTaskSchemas.LOAN_OFFICER, null);
    when(taskQuery.singleResult()).thenReturn(task);
    stubBusinessKey("pi-1", "loan-1");

    HistoricVariableInstance decision = mock(HistoricVariableInstance.class);
    when(decision.getName()).thenReturn("approvalDecision");
    when(decision.getValue()).thenReturn("MANUAL_REVIEW");

    HistoricVariableInstance lastResponse = mock(HistoricVariableInstance.class);
    when(lastResponse.getName()).thenReturn("agentLastResponse");
    when(lastResponse.getValue()).thenReturn("Recommend manual review due to income variance.");

    HistoricVariableInstance trace = mock(HistoricVariableInstance.class);
    when(trace.getName()).thenReturn("agentConversationTrace__AgentTask_CreditRisk");
    when(trace.getValue()).thenReturn("assistant: checking employment\ntool: VerifyEmployment");

    HistoricVariableInstance history = mock(HistoricVariableInstance.class);
    when(history.getName()).thenReturn("_agentConversationHistory");
    when(history.getValue())
        .thenReturn("[{\"role\":\"assistant\",\"content\":\"Need employment check\"}]");

    when(variableQuery.list())
        .thenReturn(List.of(decision, lastResponse, trace, history));

    HumanTaskDetail detail = service.getTask("task-1");

    assertThat(detail.context()).containsEntry("approvalDecision", "MANUAL_REVIEW");
    assertThat(detail.agentConversation()).isNotNull();
    assertThat(detail.agentConversation().lastResponse())
        .contains("Recommend manual review");
    assertThat(detail.agentConversation().trace()).contains("VerifyEmployment");
    assertThat(detail.agentConversation().history()).isNotNull();
  }

  @Test
  void getTask_complianceDoesNotExposeAgentConversation() {
    Task task = mockTask("task-2", HumanTaskSchemas.COMPLIANCE_OFFICER, "demo");
    when(taskQuery.singleResult()).thenReturn(task);
    stubBusinessKey("pi-1", "loan-1");

    HistoricVariableInstance lastResponse = mock(HistoricVariableInstance.class);
    when(lastResponse.getName()).thenReturn("agentLastResponse");
    when(lastResponse.getValue()).thenReturn("secret agent text");
    when(variableQuery.list()).thenReturn(List.of(lastResponse));

    HumanTaskDetail detail = service.getTask("task-2");

    assertThat(detail.agentConversation()).isNull();
  }

  private Task mockTask(String id, String definitionKey, String assignee) {
    Task task = mock(Task.class);
    when(task.getId()).thenReturn(id);
    when(task.getName()).thenReturn("Human task");
    when(task.getTaskDefinitionKey()).thenReturn(definitionKey);
    when(task.getProcessInstanceId()).thenReturn("pi-1");
    when(task.getProcessDefinitionId()).thenReturn("LoanApprovalProcess_AI:1:abc");
    when(task.getCreateTime()).thenReturn(new Date());
    when(task.getAssignee()).thenReturn(assignee);
    return task;
  }

  private void stubBusinessKey(String processInstanceId, String businessKey) {
    HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
    when(instance.getBusinessKey()).thenReturn(businessKey);
    when(processQuery.processInstanceId(processInstanceId)).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(instance);
  }
}
