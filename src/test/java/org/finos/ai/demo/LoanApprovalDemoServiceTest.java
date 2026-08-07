package org.finos.ai.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.finos.fluxnova.bpm.engine.HistoryService;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstanceQuery;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstanceQuery;
import org.finos.fluxnova.bpm.engine.repository.ProcessDefinitionQuery;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.finos.fluxnova.bpm.engine.task.TaskQuery;
import org.finos.fluxnova.bpm.engine.variable.VariableMap;
import org.finos.ai.demo.acceptance.Mi5AcceptanceProperties;
import org.finos.ai.demo.acceptance.Mi5AcceptanceService;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackVariables;
import org.finos.ai.demo.observability.Mi4ObservabilityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LoanApprovalDemoServiceTest {

  private RuntimeService runtimeService;
  private RepositoryService repositoryService;
  private HistoryService historyService;
  private TaskService taskService;
  private ProcessDefinitionQuery processDefinitionQuery;
  private LoanApprovalDemoService service;
  private Mi11FeedbackProperties mi11Properties;

  @BeforeEach
  void setUp() throws Exception {
    runtimeService = mock(RuntimeService.class);
    repositoryService = mock(RepositoryService.class);
    historyService = mock(HistoryService.class);
    taskService = mock(TaskService.class);
    processDefinitionQuery = mock(ProcessDefinitionQuery.class);
    ResourcePatternResolver resourcePatternResolver = mock(ResourcePatternResolver.class);
    Mi1DataLeakagePrevention dlp =
        new Mi1DataLeakagePrevention(new Mi1DlpProperties(), new SimpleMeterRegistry());
    Mi1DlpProperties dlpProperties = new Mi1DlpProperties();
    Mi4ObservabilityProperties mi4Properties = new Mi4ObservabilityProperties();
    Mi5AcceptanceProperties mi5Properties = new Mi5AcceptanceProperties();
    Mi5AcceptanceService mi5Service =
        new Mi5AcceptanceService(mi5Properties, dlp, dlpProperties);
    mi11Properties = new Mi11FeedbackProperties();

    when(repositoryService.createProcessDefinitionQuery()).thenReturn(processDefinitionQuery);
    when(processDefinitionQuery.processDefinitionKey("LoanApprovalProcess_AI"))
        .thenReturn(processDefinitionQuery);
    when(resourcePatternResolver.getResources("classpath:sample/sample-id-card*.png"))
        .thenReturn(
            new Resource[] {
              sampleResource("sample-id-card.png"),
              sampleResource("sample-id-card-2.png")
            });

    service =
        new LoanApprovalDemoService(
            runtimeService,
            repositoryService,
            historyService,
            taskService,
            dlp,
            dlpProperties,
            mi4Properties,
            mi5Properties,
            mi5Service,
            mi11Properties,
            resourcePatternResolver,
            "LoanApprovalProcess_AI",
            "attachedFile",
            "classpath:sample/sample-id-card*.png");
  }

  @Test
  void startWithSampleIdCard_startsProcessWithAttachedFile() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response = service.startWithSampleIdCard("loan-42", null, null);

    assertThat(response.processInstanceId()).isEqualTo("pi-123");
    assertThat(response.businessKey()).isEqualTo("loan-42");
    assertThat(response.processDefinitionKey()).isEqualTo("LoanApprovalProcess_AI");
    assertThat(response.attachedFileVariable()).isEqualTo("attachedFile");
    assertThat(response.sampleDocument())
        .isIn("sample-id-card.png", "sample-id-card-2.png");
    assertThat(response.requestedAmount()).isBetween(10_000, 100_000);
    assertThat(response.loanPurpose())
        .isIn(
            "Personal",
            "Debt Consolidation",
            "Home Renovation",
            "Education",
            "Emergency",
            "Travel",
            "Medical Emergency",
            "Car");

    assertThat(capturedVariables.get().get("requestedAmount")).isEqualTo(response.requestedAmount());
    assertThat(capturedVariables.get().get("loanPurpose")).isEqualTo(response.loanPurpose());
    assertThat(capturedVariables.get().get("creditRiskAgentProvider")).isEqualTo("openai");
    assertThat(capturedVariables.get().get("creditRiskAgentModel")).isEqualTo("gpt-5.4");
    assertThat(capturedVariables.get().get("documentProcessingAgentProvider")).isEqualTo("openai");
    assertThat(capturedVariables.get().get("documentProcessingAgentModel")).isEqualTo("gpt-5.4");
    assertThat(response.creditRiskAgentProvider()).isEqualTo("openai");
    assertThat(response.creditRiskAgentModel()).isEqualTo("gpt-5.4");
    assertThat(response.documentProcessingAgentProvider()).isEqualTo("openai");
    assertThat(response.documentProcessingAgentModel()).isEqualTo("gpt-5.4");
    assertThat(capturedVariables.get().get("attachedFile")).isNotNull();
    assertThat(capturedVariables.get().get(ComplianceControlSnapshot.MI1_ENABLED)).isEqualTo(true);
    assertThat(capturedVariables.get().get(ComplianceControlSnapshot.MI4_ENABLED)).isEqualTo(true);
    assertThat(capturedVariables.get().get(ComplianceControlSnapshot.MI5_ENABLED)).isEqualTo(true);
    assertThat(capturedVariables.get().get(ComplianceControlSnapshot.MI11_ENABLED)).isEqualTo(true);
    assertThat(capturedVariables.get().get(ComplianceControlSnapshot.SNAPSHOTTED_AT)).isNotNull();

    verify(runtimeService)
        .startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class));
  }

  @Test
  void startWithSampleIdCard_picksRandomlyAmongSampleImages() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), any(), any(VariableMap.class)))
        .thenReturn(processInstance);

    Set<String> selectedDocuments =
        IntStream.range(0, 20)
            .mapToObj(i -> service.startWithSampleIdCard(null, null, null).sampleDocument())
            .collect(Collectors.toSet());

    assertThat(selectedDocuments)
        .containsExactlyInAnyOrder("sample-id-card.png", "sample-id-card-2.png");
  }

  @Test
  void startWithSampleIdCard_failsWhenProcessNotDeployed() {
    when(processDefinitionQuery.count()).thenReturn(0L);

    assertThatThrownBy(() -> service.startWithSampleIdCard("loan-42", null, null))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode())
                  .isEqualTo(HttpStatus.PRECONDITION_FAILED);
              assertThat(responseStatusException.getReason()).contains("not deployed");
            });
  }

  @Test
  void startWithSampleIdCard_usesProvidedLoanDetails() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard("loan-42", 25_000, "Car");

    assertThat(response.requestedAmount()).isEqualTo(25_000);
    assertThat(response.loanPurpose()).isEqualTo("Car");
    assertThat(capturedVariables.get().get("requestedAmount")).isEqualTo(25_000);
    assertThat(capturedVariables.get().get("loanPurpose")).isEqualTo("Car");
  }

  @Test
  void startWithSampleIdCard_usesSelectedCreditRiskAgentModel() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard(
            "loan-42", 25_000, "Car", null, "openai", "gpt-5.6-luna");

    assertThat(response.creditRiskAgentProvider()).isEqualTo("openai");
    assertThat(response.creditRiskAgentModel()).isEqualTo("gpt-5.6-luna");
    assertThat(capturedVariables.get().get("creditRiskAgentProvider")).isEqualTo("openai");
    assertThat(capturedVariables.get().get("creditRiskAgentModel")).isEqualTo("gpt-5.6-luna");
  }

  @Test
  void startWithSampleIdCard_usesSelectedAnthropicCreditRiskAgent() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard(
            "loan-42",
            25_000,
            "Car",
            null,
            "anthropic",
            "claude-sonnet-5",
            "CONVERSATION_HISTORY",
            null,
            "anthropic",
            "claude-sonnet-5");

    assertThat(response.creditRiskAgentProvider()).isEqualTo("anthropic");
    assertThat(response.creditRiskAgentModel()).isEqualTo("claude-sonnet-5");
    assertThat(response.creditRiskAgentCacheStrategy()).isEqualTo("CONVERSATION_HISTORY");
    assertThat(response.creditRiskAgentPromptCacheKey()).isEqualTo("");
    assertThat(response.documentProcessingAgentProvider()).isEqualTo("anthropic");
    assertThat(response.documentProcessingAgentModel()).isEqualTo("claude-sonnet-5");
    assertThat(response.documentProcessingAgentCacheStrategy()).isEqualTo("SYSTEM_AND_TOOLS");
    assertThat(response.documentProcessingAgentPromptCacheKey()).isEqualTo("");
    assertThat(capturedVariables.get().get("creditRiskAgentProvider")).isEqualTo("anthropic");
    assertThat(capturedVariables.get().get("creditRiskAgentModel")).isEqualTo("claude-sonnet-5");
    assertThat(capturedVariables.get().get("creditRiskAgentCacheStrategy"))
        .isEqualTo("CONVERSATION_HISTORY");
    assertThat(capturedVariables.get().get("documentProcessingAgentProvider"))
        .isEqualTo("anthropic");
    assertThat(capturedVariables.get().get("documentProcessingAgentModel"))
        .isEqualTo("claude-sonnet-5");
    assertThat(capturedVariables.get().get("documentProcessingAgentCacheStrategy"))
        .isEqualTo("SYSTEM_AND_TOOLS");
  }

  @Test
  void startWithSampleIdCard_defaultsOpenAiPromptCacheKey() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    AtomicReference<VariableMap> capturedVariables = new AtomicReference<>();
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenAnswer(
            invocation -> {
              capturedVariables.set(invocation.getArgument(2));
              return processInstance;
            });

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard(
            "loan-42", 25_000, "Car", null, "openai", "gpt-5.4");

    assertThat(response.creditRiskAgentPromptCacheKey()).isEqualTo("loan-approval");
    assertThat(response.creditRiskAgentCacheStrategy()).isEqualTo("");
    assertThat(capturedVariables.get().get("creditRiskAgentPromptCacheKey"))
        .isEqualTo("loan-approval");
  }

  @Test
  void startWithSampleIdCard_rejectsUnknownCreditRiskAgentModel() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    assertThatThrownBy(
            () ->
                service.startWithSampleIdCard(
                    "loan-42", 25_000, "Car", null, "openai", "gpt-unknown"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(responseStatusException.getReason()).contains("creditRiskAgentModel");
            });
  }

  @Test
  void startWithSampleIdCard_rejectsInvalidLoanPurpose() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    assertThatThrownBy(() -> service.startWithSampleIdCard("loan-42", 25_000, "Vacation"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(responseStatusException.getReason()).contains("loanPurpose");
            });
  }

  @Test
  void options_returnsPurposesAmountsAndSampleDocuments() {
    LoanApprovalDemoOptions options = service.options();

    assertThat(options.processDefinitionKey()).isEqualTo("LoanApprovalProcess_AI");
    assertThat(options.minRequestedAmount()).isEqualTo(10_000);
    assertThat(options.maxRequestedAmount()).isEqualTo(100_000);
    assertThat(options.loanPurposes()).contains("Car", "Personal");
    assertThat(options.sampleDocuments())
        .containsExactly("sample-id-card-2.png", "sample-id-card.png");
    assertThat(options.creditRiskAgentProviders()).containsExactly("openai", "anthropic");
    assertThat(options.creditRiskAgentModels())
        .containsExactly("gpt-5.4", "gpt-5.6-luna", "claude-sonnet-5");
    assertThat(options.agentModelsByProvider())
        .containsEntry("openai", List.of("gpt-5.4", "gpt-5.6-luna"))
        .containsEntry("anthropic", List.of("claude-sonnet-5"));
    assertThat(options.anthropicCacheStrategies())
        .containsExactly(
            "NONE",
            "TOOLS_ONLY",
            "SYSTEM_ONLY",
            "SYSTEM_AND_TOOLS",
            "CONVERSATION_HISTORY");
    assertThat(options.mi1Dlp().enabled()).isTrue();
    assertThat(options.mi1Dlp().creditRiskContextAllowlist()).contains("creditScore");
    assertThat(options.mi1Dlp().forbiddenVariableNames()).contains("idNumber", "attachedFile");
    assertThat(options.mi4Observability().enabled()).isTrue();
    assertThat(options.mi4Observability().sanitizedAuditLog()).isTrue();
    assertThat(options.mi4Observability().dashboardPaths()).isNotEmpty();
    assertThat(options.mi5Acceptance().enabled()).isTrue();
    assertThat(options.mi5Acceptance().passRateThreshold()).isEqualTo(0.9);
    assertThat(options.mi5Acceptance().criteriaCategories()).isNotEmpty();
    assertThat(options.mi11Feedback().enabled()).isTrue();
    assertThat(options.mi11Feedback().loanOfficerTaskDefinitionKey())
        .isEqualTo("UserTask_LoanOfficerReview");
  }

  @Test
  void updateControlEnforcement_togglesMi1Mi4Mi5AndMi11() {
    assertThat(service.options().mi1Dlp().enabled()).isTrue();
    assertThat(service.options().mi4Observability().enabled()).isTrue();
    assertThat(service.options().mi5Acceptance().enabled()).isTrue();
    assertThat(service.options().mi11Feedback().enabled()).isTrue();

    LoanApprovalDemoOptions off =
        service.updateControlEnforcement(new ControlEnforcementUpdate(false, false, false, false));
    assertThat(off.mi1Dlp().enabled()).isFalse();
    assertThat(off.mi4Observability().enabled()).isFalse();
    assertThat(off.mi5Acceptance().enabled()).isFalse();
    assertThat(off.mi11Feedback().enabled()).isFalse();

    LoanApprovalDemoOptions mi1On =
        service.updateControlEnforcement(new ControlEnforcementUpdate(true, null, null, null));
    assertThat(mi1On.mi1Dlp().enabled()).isTrue();
    assertThat(mi1On.mi4Observability().enabled()).isFalse();
    assertThat(mi1On.mi5Acceptance().enabled()).isFalse();
    assertThat(mi1On.mi11Feedback().enabled()).isFalse();

    LoanApprovalDemoOptions mi11On =
        service.updateControlEnforcement(new ControlEnforcementUpdate(null, null, null, true));
    assertThat(mi11On.mi11Feedback().enabled()).isTrue();
  }

  @Test
  @SuppressWarnings("unchecked")
  void submitHumanFeedback_completesLoanOfficerTaskViaTaskService() {
    HistoricProcessInstance historic = mock(HistoricProcessInstance.class);
    HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
    when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
    when(processQuery.processInstanceId("pi-1")).thenReturn(processQuery);
    when(processQuery.singleResult()).thenReturn(historic);

    HistoricVariableInstanceQuery variableQuery = mock(HistoricVariableInstanceQuery.class);
    when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
    when(variableQuery.processInstanceId("pi-1")).thenReturn(variableQuery);
    when(variableQuery.disableBinaryFetching()).thenReturn(variableQuery);
    when(variableQuery.disableCustomObjectDeserialization()).thenReturn(variableQuery);
    java.util.List<HistoricVariableInstance> variables =
        java.util.List.of(
            historicVariable(ComplianceControlSnapshot.MI1_ENABLED, true),
            historicVariable(ComplianceControlSnapshot.MI1_REDACT_STATUS_API, true),
            historicVariable(ComplianceControlSnapshot.MI4_ENABLED, true),
            historicVariable(ComplianceControlSnapshot.MI5_ENABLED, true),
            historicVariable(ComplianceControlSnapshot.MI11_ENABLED, true),
            historicVariable(ComplianceControlSnapshot.SNAPSHOTTED_AT, "2026-08-02T12:00:00Z"),
            historicVariable("approvalDecision", "MANUAL_REVIEW"));
    when(variableQuery.list()).thenReturn(variables);

    TaskQuery taskQuery = mock(TaskQuery.class);
    Task task = mock(Task.class);
    when(taskService.createTaskQuery()).thenReturn(taskQuery);
    when(taskQuery.processInstanceId("pi-1")).thenReturn(taskQuery);
    when(taskQuery.taskDefinitionKey("UserTask_LoanOfficerReview")).thenReturn(taskQuery);
    when(taskQuery.active()).thenReturn(taskQuery);
    when(taskQuery.list()).thenReturn(java.util.List.of(task)).thenReturn(java.util.List.of());
    when(task.getId()).thenReturn("task-1");
    when(task.getName()).thenReturn("Human: Loan Officer Review");
    when(task.getCreateTime()).thenReturn(new java.util.Date());

    AtomicReference<java.util.Map<String, Object>> completed = new AtomicReference<>();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              completed.set(invocation.getArgument(1));
              return null;
            })
        .when(taskService)
        .complete(eq("task-1"), any(java.util.Map.class));

    HumanReviewStatus status =
        service.submitHumanFeedback(
            "pi-1", new HumanFeedbackRequest("APPROVE", true, "Looks good", null));

    assertThat(completed.get().get(Mi11FeedbackVariables.LOAN_OFFICER_DECISION)).isEqualTo("APPROVE");
    assertThat(completed.get().get(Mi11FeedbackVariables.AGREES_WITH_AI)).isEqualTo(true);
    assertThat(completed.get().get(Mi11FeedbackVariables.COMMENT)).isEqualTo("Looks good");
    assertThat(completed.get().get(Mi11FeedbackVariables.SUBMITTED_AT)).isNotNull();
    assertThat(status.taskOpen()).isFalse();
    assertThat(status.mi11Enabled()).isTrue();
  }

  private static HistoricVariableInstance historicVariable(String name, Object value) {
    HistoricVariableInstance variable = mock(HistoricVariableInstance.class);
    when(variable.getName()).thenReturn(name);
    when(variable.getValue()).thenReturn(value);
    when(variable.getTypeName()).thenReturn(value == null ? "Null" : value.getClass().getSimpleName());
    return variable;
  }

  @Test
  void startWithSampleIdCard_usesSelectedSampleDocument() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    ProcessInstance processInstance = mock(ProcessInstance.class);
    when(processInstance.getProcessInstanceId()).thenReturn("pi-123");
    when(runtimeService.startProcessInstanceByKey(
            eq("LoanApprovalProcess_AI"), eq("loan-42"), any(VariableMap.class)))
        .thenReturn(processInstance);

    StartLoanApprovalResponse response =
        service.startWithSampleIdCard("loan-42", 25_000, "Car", "sample-id-card-2.png");

    assertThat(response.sampleDocument()).isEqualTo("sample-id-card-2.png");
  }

  @Test
  void startWithSampleIdCard_rejectsUnknownSampleDocument() {
    when(processDefinitionQuery.count()).thenReturn(1L);

    assertThatThrownBy(
            () -> service.startWithSampleIdCard("loan-42", 25_000, "Car", "missing.png"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
              assertThat(responseStatusException.getReason()).contains("sampleDocument");
            });
  }

  @Test
  void sampleDocumentResource_returnsKnownSample() {
    Resource sample = service.sampleDocumentResource("sample-id-card-2.png");
    assertThat(sample.getFilename()).isEqualTo("sample-id-card-2.png");
  }

  @Test
  void sampleDocumentResource_rejectsUnknownSample() {
    assertThatThrownBy(() -> service.sampleDocumentResource("missing.png"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception -> {
              ResponseStatusException responseStatusException = (ResponseStatusException) exception;
              assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }

  private static Resource sampleResource(String filename) {
    return new ByteArrayResource(filename.getBytes(StandardCharsets.UTF_8)) {
      @Override
      public String getFilename() {
        return filename;
      }
    };
  }

}
