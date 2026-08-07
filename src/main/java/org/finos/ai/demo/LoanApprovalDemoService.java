package org.finos.ai.demo;

import static org.finos.fluxnova.bpm.engine.variable.Variables.fileValue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.finos.fluxnova.bpm.engine.HistoryService;
import org.finos.fluxnova.bpm.engine.RepositoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.history.HistoricActivityInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricDetail;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableUpdate;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.shared.model.TokenUsage;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.finos.fluxnova.bpm.engine.variable.VariableMap;
import org.finos.fluxnova.bpm.engine.variable.Variables;
import org.finos.fluxnova.bpm.engine.variable.value.FileValue;
import org.finos.ai.demo.acceptance.Mi5AcceptanceProperties;
import org.finos.ai.demo.acceptance.Mi5AcceptanceReport;
import org.finos.ai.demo.acceptance.Mi5AcceptanceService;
import org.finos.ai.demo.dlp.Mi1DataLeakagePrevention;
import org.finos.ai.demo.dlp.Mi1DlpProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackProperties;
import org.finos.ai.demo.feedback.Mi11FeedbackVariables;
import org.finos.ai.demo.observability.LlmCallMetadataVariables;
import org.finos.ai.demo.observability.LlmTokenUsageVariables;
import org.finos.ai.demo.observability.Mi4ObservabilityProperties;
import org.finos.ai.demo.worker.document.DocumentIntelligenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LoanApprovalDemoService {

  private static final int MIN_REQUESTED_AMOUNT = 10_000;
  private static final int MAX_REQUESTED_AMOUNT = 100_000;
  static final String VAR_CREDIT_RISK_AGENT_PROVIDER = "creditRiskAgentProvider";
  static final String VAR_CREDIT_RISK_AGENT_MODEL = "creditRiskAgentModel";
  static final String VAR_CREDIT_RISK_AGENT_CACHE_STRATEGY = "creditRiskAgentCacheStrategy";
  static final String VAR_CREDIT_RISK_AGENT_PROMPT_CACHE_KEY = "creditRiskAgentPromptCacheKey";
  static final String VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER = "documentProcessingAgentProvider";
  static final String VAR_DOCUMENT_PROCESSING_AGENT_MODEL = "documentProcessingAgentModel";
  static final String VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY =
      "documentProcessingAgentCacheStrategy";
  static final String VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY =
      "documentProcessingAgentPromptCacheKey";
  private static final String DEFAULT_AGENT_PROVIDER = "openai";
  private static final String DEFAULT_AGENT_MODEL = "gpt-5.4";
  private static final String DEFAULT_PROMPT_CACHE_KEY = "loan-approval";
  private static final String DEFAULT_ANTHROPIC_CACHE_STRATEGY = "SYSTEM_AND_TOOLS";
  private static final List<String> AGENT_PROVIDERS = List.of("openai", "anthropic");
  private static final List<String> ANTHROPIC_CACHE_STRATEGIES =
      List.of(
          "NONE",
          "TOOLS_ONLY",
          "SYSTEM_ONLY",
          "SYSTEM_AND_TOOLS",
          "CONVERSATION_HISTORY");
  private static final Map<String, List<String>> AGENT_MODELS_BY_PROVIDER;
  static {
    Map<String, List<String>> models = new LinkedHashMap<>();
    models.put("openai", List.of("gpt-5.4", "gpt-5.6-luna"));
    models.put("anthropic", List.of("claude-sonnet-5"));
    AGENT_MODELS_BY_PROVIDER = Map.copyOf(models);
  }
  private static final String[] LOAN_PURPOSES = {
    "Personal",
    "Debt Consolidation",
    "Home Renovation",
    "Education",
    "Emergency",
    "Travel",
    "Medical Emergency",
    "Car"
  };
  private static final List<String> STATUS_LOG_ACTIVITY_TYPES =
      List.of(
          "startEvent",
          "endEvent",
          "serviceTask",
          "userTask",
          "task",
          "scriptTask",
          "businessRuleTask",
          "sendTask",
          "receiveTask",
          "callActivity",
          "subProcess",
          "adHocSubProcess",
          "transaction");

  private final RuntimeService runtimeService;
  private final RepositoryService repositoryService;
  private final HistoryService historyService;
  private final TaskService taskService;
  private final Mi1DataLeakagePrevention dataLeakagePrevention;
  private final Mi1DlpProperties mi1DlpProperties;
  private final Mi4ObservabilityProperties mi4ObservabilityProperties;
  private final Mi5AcceptanceProperties mi5AcceptanceProperties;
  private final Mi5AcceptanceService mi5AcceptanceService;
  private final Mi11FeedbackProperties mi11FeedbackProperties;
  private final String processDefinitionKey;
  private final String attachedFileVariable;
  private final Resource[] sampleIdCards;

  public LoanApprovalDemoService(
      RuntimeService runtimeService,
      RepositoryService repositoryService,
      HistoryService historyService,
      TaskService taskService,
      Mi1DataLeakagePrevention dataLeakagePrevention,
      Mi1DlpProperties mi1DlpProperties,
      Mi4ObservabilityProperties mi4ObservabilityProperties,
      Mi5AcceptanceProperties mi5AcceptanceProperties,
      Mi5AcceptanceService mi5AcceptanceService,
      Mi11FeedbackProperties mi11FeedbackProperties,
      ResourcePatternResolver resourcePatternResolver,
      @Value("${fluxnova.loan-approval-demo.process-definition-key:LoanApprovalProcess_AI}")
          String processDefinitionKey,
      @Value("${fluxnova.document-intelligence.attached-file-variable:attachedFile}")
          String attachedFileVariable,
      @Value(
              "${fluxnova.loan-approval-demo.sample-id-cards-pattern:classpath:sample/sample-id-card*.png}")
          String sampleIdCardsPattern) {
    this.runtimeService = runtimeService;
    this.repositoryService = repositoryService;
    this.historyService = historyService;
    this.taskService = taskService;
    this.dataLeakagePrevention = dataLeakagePrevention;
    this.mi1DlpProperties = mi1DlpProperties;
    this.mi4ObservabilityProperties = mi4ObservabilityProperties;
    this.mi5AcceptanceProperties = mi5AcceptanceProperties;
    this.mi5AcceptanceService = mi5AcceptanceService;
    this.mi11FeedbackProperties = mi11FeedbackProperties;
    this.processDefinitionKey = processDefinitionKey;
    this.attachedFileVariable = attachedFileVariable;
    this.sampleIdCards = loadSampleIdCards(resourcePatternResolver, sampleIdCardsPattern);
  }

  public LoanApprovalDemoOptions options() {
    Mi5AcceptanceReport lastReport = mi5AcceptanceService.lastReport();
    return new LoanApprovalDemoOptions(
        processDefinitionKey,
        MIN_REQUESTED_AMOUNT,
        MAX_REQUESTED_AMOUNT,
        List.of(LOAN_PURPOSES),
        sampleDocumentNames(),
        AGENT_PROVIDERS,
        AGENT_MODELS_BY_PROVIDER.values().stream().flatMap(List::stream).distinct().toList(),
        AGENT_MODELS_BY_PROVIDER,
        ANTHROPIC_CACHE_STRATEGIES,
        new LoanApprovalDemoOptions.Mi1DlpStatus(
            mi1DlpProperties.isEnabled(),
            mi1DlpProperties.isStrict(),
            mi1DlpProperties.isRedactStatusApi(),
            List.copyOf(mi1DlpProperties.getCreditRiskContextAllowlist()),
            List.copyOf(mi1DlpProperties.getForbiddenVariableNames())),
        new LoanApprovalDemoOptions.Mi4ObservabilityStatus(
            mi4ObservabilityProperties.isEnabled(),
            mi4ObservabilityProperties.isSanitizedAuditLog(),
            mi4ObservabilityProperties.isPersistCallMetadata(),
            mi4ObservabilityProperties.getModelVersionLabel(),
            mi4ObservabilityProperties.getGrafanaBaseUrl(),
            List.copyOf(mi4ObservabilityProperties.getDashboardPaths())),
        new LoanApprovalDemoOptions.Mi5AcceptanceStatus(
            mi5AcceptanceProperties.isEnabled(),
            mi5AcceptanceProperties.getPassRateThreshold(),
            mi5AcceptanceProperties.getAigfCatalogUrl(),
            List.copyOf(mi5AcceptanceProperties.getCriteriaCategories()),
            lastReport),
        new LoanApprovalDemoOptions.Mi11FeedbackStatus(
            mi11FeedbackProperties.isEnabled(),
            mi11FeedbackProperties.getAigfCatalogUrl(),
            mi11FeedbackProperties.getLoanOfficerTaskDefinitionKey()));
  }

  /** Live toggle of mi-1 / mi-4 / mi-5 / mi-11 enforcement for the demo UI (in-memory until restart). */
  public LoanApprovalDemoOptions updateControlEnforcement(ControlEnforcementUpdate update) {
    if (update == null) {
      return options();
    }
    if (update.mi1Enabled() != null) {
      mi1DlpProperties.setEnabled(update.mi1Enabled());
    }
    if (update.mi4Enabled() != null) {
      mi4ObservabilityProperties.setEnabled(update.mi4Enabled());
    }
    if (update.mi5Enabled() != null) {
      mi5AcceptanceProperties.setEnabled(update.mi5Enabled());
    }
    if (update.mi11Enabled() != null) {
      mi11FeedbackProperties.setEnabled(update.mi11Enabled());
    }
    return options();
  }

  public Mi5AcceptanceReport runAcceptanceSuite() {
    return mi5AcceptanceService.runSuite();
  }

  public Mi5AcceptanceReport lastAcceptanceReport() {
    return mi5AcceptanceService.lastReport();
  }

  /** Per-instance compliance evidence (reuses redacted status + historic variables). */
  public LoanApprovalComplianceReport complianceReport(String processInstanceId) {
    ProcessInstanceStatus status = processInstanceStatus(processInstanceId);
    Map<String, Object> variables = loadProcessVariableValues(processInstanceId);
    LoanApprovalComplianceReport.ControlPosture controls =
        ComplianceControlSnapshot.resolvePosture(
            variables,
            mi1DlpProperties,
            mi4ObservabilityProperties,
            mi5AcceptanceProperties,
            mi11FeedbackProperties);
    LoanApprovalComplianceReport.SatSnapshot sat =
        ComplianceControlSnapshot.resolveSat(
            variables, controls.mi5Enabled(), mi5AcceptanceService.lastReport());
    LoanApprovalComplianceReport report =
        LoanApprovalComplianceEvidence.build(status, variables, controls, sat);
    return enrichLlmContextKeys(report, variables);
  }

  /** PDF bytes for the per-instance compliance evidence report. */
  public byte[] complianceReportPdf(String processInstanceId) {
    return LoanApprovalCompliancePdfRenderer.render(complianceReport(processInstanceId));
  }

  public HumanReviewStatus humanReview(String processInstanceId) {
    requireHistoricProcessInstance(processInstanceId);
    Map<String, Object> variables = loadProcessVariableValues(processInstanceId);
    LoanApprovalComplianceReport.ControlPosture posture =
        ComplianceControlSnapshot.resolvePosture(
            variables,
            mi1DlpProperties,
            mi4ObservabilityProperties,
            mi5AcceptanceProperties,
            mi11FeedbackProperties);
    Task task = findOpenLoanOfficerTask(processInstanceId);
    return new HumanReviewStatus(
        processInstanceId,
        task != null,
        task == null ? null : task.getId(),
        task == null ? null : task.getName(),
        mi11FeedbackProperties.getLoanOfficerTaskDefinitionKey(),
        task == null ? null : task.getCreateTime(),
        asString(variables.get("approvalDecision")),
        asInteger(variables.get("creditScore")),
        asString(variables.get("creditRiskBand")),
        asDouble(variables.get("aiRiskScore")),
        asString(variables.get(Mi11FeedbackVariables.LOAN_OFFICER_DECISION)),
        asBoolean(variables.get(Mi11FeedbackVariables.AGREES_WITH_AI)),
        asString(variables.get(Mi11FeedbackVariables.COMMENT)),
        posture.mi11Enabled());
  }

  public HumanReviewStatus submitHumanFeedback(
      String processInstanceId, HumanFeedbackRequest request) {
    requireHistoricProcessInstance(processInstanceId);
    Map<String, Object> variables = loadProcessVariableValues(processInstanceId);
    LoanApprovalComplianceReport.ControlPosture posture =
        ComplianceControlSnapshot.resolvePosture(
            variables,
            mi1DlpProperties,
            mi4ObservabilityProperties,
            mi5AcceptanceProperties,
            mi11FeedbackProperties);
    if (!posture.mi11Enabled()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "mi-11 human feedback is not enabled for this instance");
    }
    if (request == null
        || request.loanOfficerDecision() == null
        || request.loanOfficerDecision().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "loanOfficerDecision is required");
    }
    String decision = request.loanOfficerDecision().trim().toUpperCase();
    if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "loanOfficerDecision must be APPROVE or REJECT");
    }
    if (request.agreesWithAi() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "agreesWithAi is required");
    }

    Task task = findOpenLoanOfficerTask(processInstanceId);
    if (task == null) {
      if (variables.get(Mi11FeedbackVariables.LOAN_OFFICER_DECISION) != null) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Loan officer review has already been completed");
      }
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "No open loan officer review task (UserTask_LoanOfficerReview) for this instance");
    }

    Map<String, Object> completion = new LinkedHashMap<>();
    completion.put(Mi11FeedbackVariables.LOAN_OFFICER_DECISION, decision);
    completion.put(Mi11FeedbackVariables.AGREES_WITH_AI, request.agreesWithAi());
    completion.put(Mi11FeedbackVariables.SUBMITTED_AT, Instant.now().toString());
    if (request.comment() != null && !request.comment().isBlank()) {
      String comment = request.comment().trim();
      if (comment.length() > Mi11FeedbackVariables.MAX_COMMENT_LENGTH) {
        comment = comment.substring(0, Mi11FeedbackVariables.MAX_COMMENT_LENGTH) + "…";
      }
      completion.put(Mi11FeedbackVariables.COMMENT, comment);
    }
    if (request.categories() != null && !request.categories().isEmpty()) {
      completion.put(
          Mi11FeedbackVariables.CATEGORIES,
          request.categories().stream()
              .filter(c -> c != null && !c.isBlank())
              .map(String::trim)
              .reduce((a, b) -> a + "," + b)
              .orElse(null));
    }
    taskService.complete(task.getId(), completion);
    return humanReview(processInstanceId);
  }

  private Task findOpenLoanOfficerTask(String processInstanceId) {
    String taskKey = mi11FeedbackProperties.getLoanOfficerTaskDefinitionKey();
    List<Task> tasks =
        taskService
            .createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskDefinitionKey(taskKey)
            .active()
            .list();
    return tasks.isEmpty() ? null : tasks.getFirst();
  }

  private static Double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Double.parseDouble(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Boolean asBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    if (value instanceof String text && !text.isBlank()) {
      return Boolean.parseBoolean(text.trim());
    }
    return null;
  }

  private Map<String, Object> loadProcessVariableValues(String processInstanceId) {
    ProcessInstanceStatus.ControlPosture controls = resolveStatusControls(processInstanceId);
    boolean redactStatus = controls.mi1Enabled() && controls.mi1RedactStatusApi();
    Map<String, Object> values = new LinkedHashMap<>();
    historyService
        .createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId)
        .disableBinaryFetching()
        .disableCustomObjectDeserialization()
        .list()
        .forEach(
            variable -> {
              if (variable.getName() == null || variable.getName().isBlank()) {
                return;
              }
              if (variable.getName().startsWith("_agent")) {
                return;
              }
              Object sanitized =
                  sanitizeVariableValue(
                      variable.getName(),
                      variable.getValue(),
                      variable.getTypeName(),
                      redactStatus);
              values.put(variable.getName(), sanitized);
            });
    return values;
  }

  private static LoanApprovalComplianceReport enrichLlmContextKeys(
      LoanApprovalComplianceReport report, Map<String, Object> variables) {
    if (report.llmCalls().isEmpty()) {
      return report;
    }
    List<LoanApprovalComplianceReport.LlmCallEvidence> enriched = new ArrayList<>();
    for (LoanApprovalComplianceReport.LlmCallEvidence call : report.llmCalls()) {
      if (call.contextKeys() != null && !call.contextKeys().isBlank()) {
        enriched.add(call);
        continue;
      }
      Object scoped =
          variables.get(
              LlmCallMetadataVariables.scopedName(
                  LlmCallMetadataVariables.CONTEXT_KEYS, call.activityId()));
      Object unscoped = variables.get(LlmCallMetadataVariables.CONTEXT_KEYS);
      String keys =
          scoped != null
              ? String.valueOf(scoped)
              : unscoped != null ? String.valueOf(unscoped) : null;
      enriched.add(
          new LoanApprovalComplianceReport.LlmCallEvidence(
              call.activityId(),
              call.activityName(),
              call.model(),
              call.provider(),
              call.modelVersion(),
              call.durationMs(),
              call.status(),
              keys,
              call.totalTokens(),
              call.estimatedCostUsd()));
    }
    return new LoanApprovalComplianceReport(
        report.processInstanceId(),
        report.businessKey(),
        report.state(),
        report.generatedAt(),
        report.overallStatus(),
        report.controls(),
        report.decisionPath(),
        report.checks(),
        List.copyOf(enriched),
        report.sat());
  }

  public Resource sampleDocumentResource(String sampleDocument) {
    if (sampleDocument == null || sampleDocument.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sampleDocument is required");
    }
    return resolveSampleIdCard(sampleDocument);
  }

  public LoanApprovalInstanceList listInstances() {
    List<HistoricProcessInstance> processInstances =
        historyService
            .createHistoricProcessInstanceQuery()
            .processDefinitionKey(processDefinitionKey)
            .orderByProcessInstanceStartTime()
            .desc()
            .list();

    List<LoanApprovalInstanceSummary> summaries =
        processInstances.stream().map(this::toInstanceSummary).toList();
    int activeCount =
        (int)
            summaries.stream()
                .filter(summary -> HistoricProcessInstance.STATE_ACTIVE.equals(summary.state()))
                .count();
    return new LoanApprovalInstanceList(
        processDefinitionKey, activeCount, summaries.size(), summaries);
  }

  public LoanApprovalInstanceSummary getInstance(String processInstanceId) {
    HistoricProcessInstance processInstance = requireHistoricProcessInstance(processInstanceId);
    return toInstanceSummary(processInstance);
  }

  private LoanApprovalInstanceSummary toInstanceSummary(HistoricProcessInstance processInstance) {
    Map<String, HistoricVariableInstance> variables =
        historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(processInstance.getId())
            .list()
            .stream()
            .filter(variable -> variable.getName() != null)
            .collect(
                java.util.stream.Collectors.toMap(
                    HistoricVariableInstance::getName,
                    variable -> variable,
                    (left, right) -> left,
                    LinkedHashMap::new));

    String complianceOverallStatus;
    try {
      complianceOverallStatus = complianceReport(processInstance.getId()).overallStatus();
    } catch (RuntimeException exception) {
      complianceOverallStatus = "UNKNOWN";
    }

    return new LoanApprovalInstanceSummary(
        processInstance.getId(),
        processInstance.getBusinessKey(),
        processDefinitionKey,
        processInstance.getState(),
        processInstance.getStartTime(),
        processInstance.getEndTime(),
        asInteger(variableValue(variables.get("requestedAmount"))),
        asString(variableValue(variables.get("loanPurpose"))),
        resolveSampleDocumentName(variables.get(attachedFileVariable)),
        asString(variableValue(variables.get(VAR_CREDIT_RISK_AGENT_PROVIDER))),
        asString(variableValue(variables.get(VAR_CREDIT_RISK_AGENT_MODEL))),
        asString(variableValue(variables.get(VAR_CREDIT_RISK_AGENT_CACHE_STRATEGY))),
        asString(variableValue(variables.get(VAR_CREDIT_RISK_AGENT_PROMPT_CACHE_KEY))),
        asString(variableValue(variables.get(VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER))),
        asString(variableValue(variables.get(VAR_DOCUMENT_PROCESSING_AGENT_MODEL))),
        asString(variableValue(variables.get(VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY))),
        asString(variableValue(variables.get(VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY))),
        currentActivityNames(processInstance),
        complianceOverallStatus);
  }

  private List<String> currentActivityNames(HistoricProcessInstance processInstance) {
    if (!HistoricProcessInstance.STATE_ACTIVE.equals(processInstance.getState())) {
      return List.of();
    }
    return historyService
        .createHistoricActivityInstanceQuery()
        .processInstanceId(processInstance.getId())
        .unfinished()
        .orderByHistoricActivityInstanceStartTime()
        .asc()
        .list()
        .stream()
        .filter(activity -> STATUS_LOG_ACTIVITY_TYPES.contains(activity.getActivityType()))
        .map(
            activity ->
                activity.getActivityName() == null || activity.getActivityName().isBlank()
                    ? activity.getActivityId()
                    : activity.getActivityName())
        .distinct()
        .toList();
  }

  private HistoricProcessInstance requireHistoricProcessInstance(String processInstanceId) {
    if (processInstanceId == null || processInstanceId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "processInstanceId is required");
    }
    HistoricProcessInstance processInstance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
    if (processInstance == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Process instance '" + processInstanceId + "' was not found");
    }
    return processInstance;
  }

  private static Object variableValue(HistoricVariableInstance variable) {
    return variable == null ? null : variable.getValue();
  }

  private static String resolveSampleDocumentName(HistoricVariableInstance attachedFile) {
    if (attachedFile == null) {
      return null;
    }
    Object value = attachedFile.getValue();
    if (value instanceof FileValue fileValue && fileValue.getFilename() != null) {
      return fileValue.getFilename();
    }
    if (attachedFile.getTypedValue() instanceof FileValue typedFile
        && typedFile.getFilename() != null) {
      return typedFile.getFilename();
    }
    return null;
  }

  private static Integer asInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Integer.parseInt(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static final int MAX_VARIABLE_STRING_LENGTH = 800;

  public ProcessInstanceStatus processInstanceStatus(String processInstanceId) {
    HistoricProcessInstance processInstance = requireHistoricProcessInstance(processInstanceId);
    ProcessInstanceStatus.ControlPosture controls = resolveStatusControls(processInstanceId);
    boolean redactStatus = controls.mi1Enabled() && controls.mi1RedactStatusApi();

    Map<String, ActivityVariables> variablesByActivity =
        loadVariablesByActivityInstance(processInstanceId, redactStatus);

    List<ProcessInstanceStatus.ActivityStatusEntry> activities =
        historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(processInstanceId)
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list()
            .stream()
            .filter(activity -> STATUS_LOG_ACTIVITY_TYPES.contains(activity.getActivityType()))
            .map(activity -> toActivityStatusEntry(activity, variablesByActivity))
            .toList();

    List<String> currentActivityNames =
        activities.stream()
            .filter(activity -> "active".equals(activity.status()))
            .map(ProcessInstanceStatus.ActivityStatusEntry::activityName)
            .distinct()
            .toList();

    return new ProcessInstanceStatus(
        processInstance.getId(),
        processInstance.getBusinessKey(),
        processInstance.getState(),
        processInstance.getStartTime(),
        processInstance.getEndTime(),
        currentActivityNames,
        activities,
        controls);
  }

  private ProcessInstanceStatus.ControlPosture resolveStatusControls(String processInstanceId) {
    Map<String, Object> snapshot = loadComplianceSnapshotVariables(processInstanceId);
    if (ComplianceControlSnapshot.hasSnapshot(snapshot)) {
      LoanApprovalComplianceReport.ControlPosture posture =
          ComplianceControlSnapshot.resolvePosture(
              snapshot,
              mi1DlpProperties,
              mi4ObservabilityProperties,
              mi5AcceptanceProperties,
              mi11FeedbackProperties);
      return new ProcessInstanceStatus.ControlPosture(
          posture.mi1Enabled(),
          posture.mi1RedactStatusApi(),
          posture.mi4Enabled(),
          posture.mi11Enabled(),
          true);
    }
    return new ProcessInstanceStatus.ControlPosture(
        mi1DlpProperties.isEnabled(),
        mi1DlpProperties.isRedactStatusApi(),
        mi4ObservabilityProperties.isEnabled(),
        mi11FeedbackProperties.isEnabled(),
        false);
  }

  private Map<String, Object> loadComplianceSnapshotVariables(String processInstanceId) {
    Map<String, Object> values = new LinkedHashMap<>();
    historyService
        .createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId)
        .list()
        .forEach(
            variable -> {
              String name = variable.getName();
              if (name == null) {
                return;
              }
              if (name.equals(ComplianceControlSnapshot.MI1_ENABLED)
                  || name.equals(ComplianceControlSnapshot.MI1_REDACT_STATUS_API)
                  || name.equals(ComplianceControlSnapshot.MI4_ENABLED)
                  || name.equals(ComplianceControlSnapshot.MI5_ENABLED)
                  || name.equals(ComplianceControlSnapshot.MI11_ENABLED)
                  || name.equals(ComplianceControlSnapshot.SNAPSHOTTED_AT)
                  || name.equals(ComplianceControlSnapshot.SAT_LAST_ACCEPTED)
                  || name.equals(ComplianceControlSnapshot.SAT_LAST_PASS_RATE)
                  || name.equals(ComplianceControlSnapshot.SAT_LAST_RAN_AT)
                  || name.equals(ComplianceControlSnapshot.SAT_NOTE)) {
                values.put(name, variable.getValue());
              }
            });
    return values;
  }

  private Map<String, ActivityVariables> loadVariablesByActivityInstance(
      String processInstanceId, boolean redactStatusApi) {
    Map<String, ActivityVariables> byActivity = new LinkedHashMap<>();
    List<HistoricDetail> details =
        historyService
            .createHistoricDetailQuery()
            .processInstanceId(processInstanceId)
            .variableUpdates()
            .disableBinaryFetching()
            .disableCustomObjectDeserialization()
            .orderByTime()
            .asc()
            .list();

    for (HistoricDetail detail : details) {
      if (!(detail instanceof HistoricVariableUpdate update)) {
        continue;
      }
      String activityInstanceId = update.getActivityInstanceId();
      if (activityInstanceId == null || activityInstanceId.isBlank()) {
        continue;
      }
      String name = update.getVariableName();
      if (name == null || name.isBlank() || name.startsWith("_agent")) {
        continue;
      }

      ProcessInstanceStatus.VariableEntry entry =
          new ProcessInstanceStatus.VariableEntry(
              name,
              update.getTypeName(),
              sanitizeVariableValue(name, update.getValue(), update.getTypeName(), redactStatusApi));

      ActivityVariables activityVariables =
          byActivity.computeIfAbsent(activityInstanceId, ignored -> new ActivityVariables());
      if (isInputVariable(name, update)) {
        activityVariables.inputs.put(name, entry);
      } else {
        activityVariables.outputs.put(name, entry);
      }
    }
    return byActivity;
  }

  private static boolean isInputVariable(String name, HistoricVariableUpdate update) {
    if (name.startsWith("Output_") || name.startsWith("output")) {
      return false;
    }
    if (name.startsWith("Input_") || name.startsWith("input")) {
      return true;
    }
    Boolean initial = update.isInitial();
    return Boolean.TRUE.equals(initial);
  }

  private Object sanitizeVariableValue(
      String name, Object value, String typeName, boolean redactStatusApi) {
    Object redacted = dataLeakagePrevention.redactForStatusApi(name, value, redactStatusApi);
    if (redacted != value) {
      return redacted;
    }
    if (value == null) {
      return null;
    }
    if ("File".equalsIgnoreCase(typeName)
        || "Bytes".equalsIgnoreCase(typeName)
        || value instanceof byte[]) {
      return "<" + (typeName == null ? "binary" : typeName.toLowerCase()) + ">";
    }
    if (value instanceof String text) {
      if (text.length() <= MAX_VARIABLE_STRING_LENGTH) {
        return text;
      }
      return text.substring(0, MAX_VARIABLE_STRING_LENGTH) + "…";
    }
    return value;
  }

  private static ProcessInstanceStatus.ActivityStatusEntry toActivityStatusEntry(
      HistoricActivityInstance activity, Map<String, ActivityVariables> variablesByActivity) {
    String status;
    if (activity.isCanceled()) {
      status = "canceled";
    } else if (activity.getEndTime() == null) {
      status = "active";
    } else {
      status = "completed";
    }
    String activityName =
        activity.getActivityName() == null || activity.getActivityName().isBlank()
            ? activity.getActivityId()
            : activity.getActivityName();

    ActivityVariables variables =
        variablesByActivity.getOrDefault(activity.getId(), ActivityVariables.EMPTY);
    ActivityVariables processScoped =
        variablesByActivity.getOrDefault(activity.getProcessInstanceId(), ActivityVariables.EMPTY);
    // Start-form / start-process variables are recorded against the process instance id.
    if ("startEvent".equals(activity.getActivityType())) {
      variables = ActivityVariables.merge(variables, processScoped);
    }

    ProcessInstanceStatus.TokenUsageSummary tokenUsage =
        extractTokenUsage(variables, activity.getActivityId(), processScoped);
    return new ProcessInstanceStatus.ActivityStatusEntry(
        activity.getId(),
        activity.getActivityId(),
        activityName,
        activity.getActivityType(),
        status,
        activity.getStartTime(),
        activity.getEndTime(),
        activity.getDurationInMillis(),
        tokenUsage,
        withoutTokenUsageVariables(variables.inputs),
        withoutTokenUsageVariables(variables.outputs));
  }

  private static ProcessInstanceStatus.TokenUsageSummary extractTokenUsage(
      ActivityVariables variables, String activityId, ActivityVariables processScoped) {
    TokenUsage usage =
        LlmTokenUsageVariables.fromVariables(
            valueOf(variables, LlmTokenUsageVariables.PROMPT_TOKENS),
            valueOf(variables, LlmTokenUsageVariables.COMPLETION_TOKENS),
            valueOf(variables, LlmTokenUsageVariables.TOTAL_TOKENS));
    if (usage == null && activityId != null && !activityId.isBlank()) {
      usage =
          LlmTokenUsageVariables.fromVariables(
              valueOf(
                  processScoped,
                  LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.PROMPT_TOKENS, activityId)),
              valueOf(
                  processScoped,
                  LlmTokenUsageVariables.scopedName(
                      LlmTokenUsageVariables.COMPLETION_TOKENS, activityId)),
              valueOf(
                  processScoped,
                  LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.TOTAL_TOKENS, activityId)));
    }
    // Scoped vars may also have been historic-updated against the activity instance itself.
    if (usage == null && activityId != null && !activityId.isBlank()) {
      usage =
          LlmTokenUsageVariables.fromVariables(
              valueOf(
                  variables,
                  LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.PROMPT_TOKENS, activityId)),
              valueOf(
                  variables,
                  LlmTokenUsageVariables.scopedName(
                      LlmTokenUsageVariables.COMPLETION_TOKENS, activityId)),
              valueOf(
                  variables,
                  LlmTokenUsageVariables.scopedName(LlmTokenUsageVariables.TOTAL_TOKENS, activityId)));
    }

    String model = metadataValue(variables, processScoped, activityId, LlmCallMetadataVariables.MODEL);
    String provider =
        metadataValue(variables, processScoped, activityId, LlmCallMetadataVariables.PROVIDER);
    String modelVersion =
        metadataValue(variables, processScoped, activityId, LlmCallMetadataVariables.VERSION);
    Long durationMs =
        asLong(
            metadataRaw(variables, processScoped, activityId, LlmCallMetadataVariables.DURATION_MS));
    String status =
        metadataValue(variables, processScoped, activityId, LlmCallMetadataVariables.STATUS);

    if (usage == null && model == null && provider == null && durationMs == null && status == null) {
      return null;
    }
    int prompt = usage == null ? 0 : usage.promptTokens();
    int completion = usage == null ? 0 : usage.completionTokens();
    int total = usage == null ? 0 : usage.totalTokens();
    Double estimatedCost =
        firstCost(
            valueOf(variables, LlmTokenUsageVariables.ESTIMATED_COST_USD),
            activityId == null
                ? null
                : valueOf(
                    processScoped,
                    LlmTokenUsageVariables.scopedName(
                        LlmTokenUsageVariables.ESTIMATED_COST_USD, activityId)),
            activityId == null
                ? null
                : valueOf(
                    variables,
                    LlmTokenUsageVariables.scopedName(
                        LlmTokenUsageVariables.ESTIMATED_COST_USD, activityId)));
    return new ProcessInstanceStatus.TokenUsageSummary(
        prompt,
        completion,
        total,
        model,
        provider,
        modelVersion,
        durationMs,
        status,
        estimatedCost);
  }

  private static Double firstCost(Object... values) {
    for (Object value : values) {
      Double parsed = LlmTokenUsageVariables.costFromVariable(value);
      if (parsed != null) {
        return parsed;
      }
    }
    return null;
  }

  private static String metadataValue(
      ActivityVariables variables,
      ActivityVariables processScoped,
      String activityId,
      String baseName) {
    Object raw = metadataRaw(variables, processScoped, activityId, baseName);
    return raw == null ? null : String.valueOf(raw);
  }

  private static Object metadataRaw(
      ActivityVariables variables,
      ActivityVariables processScoped,
      String activityId,
      String baseName) {
    Object local = valueOf(variables, baseName);
    if (local != null) {
      return local;
    }
    if (activityId == null || activityId.isBlank()) {
      return null;
    }
    Object scoped = valueOf(processScoped, LlmCallMetadataVariables.scopedName(baseName, activityId));
    if (scoped != null) {
      return scoped;
    }
    return valueOf(variables, LlmCallMetadataVariables.scopedName(baseName, activityId));
  }

  private static Long asLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        return Long.parseLong(text.trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static Object valueOf(ActivityVariables variables, String name) {
    ProcessInstanceStatus.VariableEntry output = variables.outputs.get(name);
    if (output != null) {
      return output.value();
    }
    ProcessInstanceStatus.VariableEntry input = variables.inputs.get(name);
    return input == null ? null : input.value();
  }

  private static List<ProcessInstanceStatus.VariableEntry> withoutTokenUsageVariables(
      Map<String, ProcessInstanceStatus.VariableEntry> variables) {
    return variables.values().stream()
        .filter(
            entry ->
                !LlmTokenUsageVariables.isTokenUsageVariable(entry.name())
                    && !LlmCallMetadataVariables.isMetadataVariable(entry.name()))
        .toList();
  }

  private static final class ActivityVariables {
    private static final ActivityVariables EMPTY = new ActivityVariables();

    private final Map<String, ProcessInstanceStatus.VariableEntry> inputs = new LinkedHashMap<>();
    private final Map<String, ProcessInstanceStatus.VariableEntry> outputs = new LinkedHashMap<>();

    private static ActivityVariables merge(ActivityVariables left, ActivityVariables right) {
      if (right == null || right == EMPTY) {
        return left == null ? EMPTY : left;
      }
      if (left == null || left == EMPTY) {
        return right;
      }
      ActivityVariables merged = new ActivityVariables();
      merged.inputs.putAll(left.inputs);
      merged.outputs.putAll(left.outputs);
      merged.inputs.putAll(right.inputs);
      merged.outputs.putAll(right.outputs);
      return merged;
    }
  }

  public StartLoanApprovalResponse startWithSampleIdCard(StartLoanApprovalRequest request) {
    StartLoanApprovalRequest safeRequest =
        request == null
            ? new StartLoanApprovalRequest(
                null, null, null, null, null, null, null, null, null, null, null, null)
            : request;
    return startWithSampleIdCard(
        safeRequest.businessKey(),
        safeRequest.requestedAmount(),
        safeRequest.loanPurpose(),
        safeRequest.sampleDocument(),
        safeRequest.creditRiskAgentProvider(),
        safeRequest.creditRiskAgentModel(),
        safeRequest.creditRiskAgentCacheStrategy(),
        safeRequest.creditRiskAgentPromptCacheKey(),
        safeRequest.documentProcessingAgentProvider(),
        safeRequest.documentProcessingAgentModel(),
        safeRequest.documentProcessingAgentCacheStrategy(),
        safeRequest.documentProcessingAgentPromptCacheKey());
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey, Integer requestedAmount, String loanPurpose) {
    return startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey, Integer requestedAmount, String loanPurpose, String sampleDocument) {
    return startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        sampleDocument,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey,
      Integer requestedAmount,
      String loanPurpose,
      String sampleDocument,
      String creditRiskAgentProvider,
      String creditRiskAgentModel) {
    return startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        sampleDocument,
        creditRiskAgentProvider,
        creditRiskAgentModel,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey,
      Integer requestedAmount,
      String loanPurpose,
      String sampleDocument,
      String creditRiskAgentProvider,
      String creditRiskAgentModel,
      String documentProcessingAgentProvider,
      String documentProcessingAgentModel) {
    return startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        sampleDocument,
        creditRiskAgentProvider,
        creditRiskAgentModel,
        null,
        null,
        documentProcessingAgentProvider,
        documentProcessingAgentModel,
        null,
        null);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey,
      Integer requestedAmount,
      String loanPurpose,
      String sampleDocument,
      String creditRiskAgentProvider,
      String creditRiskAgentModel,
      String creditRiskAgentCacheStrategy,
      String creditRiskAgentPromptCacheKey,
      String documentProcessingAgentProvider,
      String documentProcessingAgentModel) {
    return startWithSampleIdCard(
        businessKey,
        requestedAmount,
        loanPurpose,
        sampleDocument,
        creditRiskAgentProvider,
        creditRiskAgentModel,
        creditRiskAgentCacheStrategy,
        creditRiskAgentPromptCacheKey,
        documentProcessingAgentProvider,
        documentProcessingAgentModel,
        null,
        null);
  }

  public StartLoanApprovalResponse startWithSampleIdCard(
      String businessKey,
      Integer requestedAmount,
      String loanPurpose,
      String sampleDocument,
      String creditRiskAgentProvider,
      String creditRiskAgentModel,
      String creditRiskAgentCacheStrategy,
      String creditRiskAgentPromptCacheKey,
      String documentProcessingAgentProvider,
      String documentProcessingAgentModel,
      String documentProcessingAgentCacheStrategy,
      String documentProcessingAgentPromptCacheKey) {
    ensureProcessDeployed();

    String resolvedBusinessKey =
        businessKey == null || businessKey.isBlank()
            ? "loan-demo-" + Instant.now().toEpochMilli()
            : businessKey;

    Resource sampleIdCard = resolveSampleIdCard(sampleDocument);
    byte[] sampleBytes = readSampleIdCardBytes(sampleIdCard);
    String sampleMimeType =
        DocumentIntelligenceService.resolveImageMimeType(
                null, sampleIdCard.getFilename(), sampleBytes)
            .toString();
    int resolvedRequestedAmount = resolveRequestedAmount(requestedAmount);
    String resolvedLoanPurpose = resolveLoanPurpose(loanPurpose);
    String resolvedCreditRiskProvider =
        resolveAgentProvider(creditRiskAgentProvider, "creditRiskAgentProvider");
    String resolvedCreditRiskModel =
        resolveAgentModel(
            resolvedCreditRiskProvider, creditRiskAgentModel, "creditRiskAgentModel");
    String resolvedCacheStrategy =
        resolveCacheStrategy(
            resolvedCreditRiskProvider,
            creditRiskAgentCacheStrategy,
            "creditRiskAgentCacheStrategy");
    String resolvedPromptCacheKey =
        resolvePromptCacheKey(resolvedCreditRiskProvider, creditRiskAgentPromptCacheKey);
    String resolvedDocumentProvider =
        resolveAgentProvider(
            documentProcessingAgentProvider, "documentProcessingAgentProvider");
    String resolvedDocumentModel =
        resolveAgentModel(
            resolvedDocumentProvider,
            documentProcessingAgentModel,
            "documentProcessingAgentModel");
    String resolvedDocumentCacheStrategy =
        resolveCacheStrategy(
            resolvedDocumentProvider,
            documentProcessingAgentCacheStrategy,
            "documentProcessingAgentCacheStrategy");
    String resolvedDocumentPromptCacheKey =
        resolvePromptCacheKey(resolvedDocumentProvider, documentProcessingAgentPromptCacheKey);

    VariableMap variables =
        Variables.createVariables()
            .putValue(
                attachedFileVariable,
                fileValue(sampleIdCard.getFilename())
                    .file(sampleBytes)
                    .mimeType(sampleMimeType)
                    .create())
            .putValue("requestedAmount", resolvedRequestedAmount)
            .putValue("loanPurpose", resolvedLoanPurpose)
            .putValue(VAR_CREDIT_RISK_AGENT_PROVIDER, resolvedCreditRiskProvider)
            .putValue(VAR_CREDIT_RISK_AGENT_MODEL, resolvedCreditRiskModel)
            .putValue(VAR_CREDIT_RISK_AGENT_CACHE_STRATEGY, resolvedCacheStrategy)
            .putValue(VAR_CREDIT_RISK_AGENT_PROMPT_CACHE_KEY, resolvedPromptCacheKey)
            .putValue(VAR_DOCUMENT_PROCESSING_AGENT_PROVIDER, resolvedDocumentProvider)
            .putValue(VAR_DOCUMENT_PROCESSING_AGENT_MODEL, resolvedDocumentModel)
            .putValue(VAR_DOCUMENT_PROCESSING_AGENT_CACHE_STRATEGY, resolvedDocumentCacheStrategy)
            .putValue(
                VAR_DOCUMENT_PROCESSING_AGENT_PROMPT_CACHE_KEY, resolvedDocumentPromptCacheKey);

    ComplianceControlSnapshot.capture(
            mi1DlpProperties,
            mi4ObservabilityProperties,
            mi5AcceptanceProperties,
            mi11FeedbackProperties,
            mi5AcceptanceService.lastReport())
        .forEach(variables::putValue);

    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(
            processDefinitionKey, resolvedBusinessKey, variables);

    return new StartLoanApprovalResponse(
        processInstance.getProcessInstanceId(),
        resolvedBusinessKey,
        processDefinitionKey,
        attachedFileVariable,
        sampleIdCard.getFilename(),
        resolvedRequestedAmount,
        resolvedLoanPurpose,
        resolvedCreditRiskProvider,
        resolvedCreditRiskModel,
        resolvedCacheStrategy,
        resolvedPromptCacheKey,
        resolvedDocumentProvider,
        resolvedDocumentModel,
        resolvedDocumentCacheStrategy,
        resolvedDocumentPromptCacheKey);
  }

  private Resource resolveSampleIdCard(String sampleDocument) {
    if (sampleDocument == null || sampleDocument.isBlank()) {
      return pickRandomSampleIdCard();
    }
    for (Resource sampleIdCard : sampleIdCards) {
      if (sampleDocument.equals(sampleIdCard.getFilename())) {
        return sampleIdCard;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "sampleDocument must be one of: " + String.join(", ", sampleDocumentNames()));
  }

  private List<String> sampleDocumentNames() {
    return Arrays.stream(sampleIdCards)
        .map(Resource::getFilename)
        .filter(name -> name != null && !name.isBlank())
        .toList();
  }

  private int resolveRequestedAmount(Integer requestedAmount) {
    if (requestedAmount == null) {
      return pickRandomRequestedAmount();
    }
    if (requestedAmount < MIN_REQUESTED_AMOUNT || requestedAmount > MAX_REQUESTED_AMOUNT) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "requestedAmount must be between "
              + MIN_REQUESTED_AMOUNT
              + " and "
              + MAX_REQUESTED_AMOUNT);
    }
    return requestedAmount;
  }

  private String resolveLoanPurpose(String loanPurpose) {
    if (loanPurpose == null || loanPurpose.isBlank()) {
      return pickRandomLoanPurpose();
    }
    for (String allowedPurpose : LOAN_PURPOSES) {
      if (allowedPurpose.equalsIgnoreCase(loanPurpose)) {
        return allowedPurpose;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "loanPurpose must be one of: " + String.join(", ", LOAN_PURPOSES));
  }

  private String resolveAgentProvider(String provider, String fieldName) {
    if (provider == null || provider.isBlank()) {
      return DEFAULT_AGENT_PROVIDER;
    }
    for (String allowed : AGENT_PROVIDERS) {
      if (allowed.equalsIgnoreCase(provider)) {
        return allowed;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        fieldName + " must be one of: " + String.join(", ", AGENT_PROVIDERS));
  }

  private String resolveAgentModel(String provider, String model, String fieldName) {
    List<String> allowedModels =
        AGENT_MODELS_BY_PROVIDER.getOrDefault(provider, List.of(DEFAULT_AGENT_MODEL));
    if (model == null || model.isBlank()) {
      return allowedModels.getFirst();
    }
    for (String allowed : allowedModels) {
      if (allowed.equalsIgnoreCase(model)) {
        return allowed;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        fieldName
            + " must be one of: "
            + String.join(", ", allowedModels)
            + " for provider "
            + provider);
  }

  private String resolveCacheStrategy(String provider, String cacheStrategy, String fieldName) {
    if (!"anthropic".equals(provider)) {
      return "";
    }
    if (cacheStrategy == null || cacheStrategy.isBlank()) {
      return DEFAULT_ANTHROPIC_CACHE_STRATEGY;
    }
    for (String allowed : ANTHROPIC_CACHE_STRATEGIES) {
      if (allowed.equalsIgnoreCase(cacheStrategy.trim())) {
        return allowed;
      }
    }
    throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        fieldName + " must be one of: " + String.join(", ", ANTHROPIC_CACHE_STRATEGIES));
  }

  private String resolvePromptCacheKey(String provider, String promptCacheKey) {
    if (!"openai".equals(provider)) {
      return "";
    }
    if (promptCacheKey == null || promptCacheKey.isBlank()) {
      return DEFAULT_PROMPT_CACHE_KEY;
    }
    return promptCacheKey.trim();
  }

  private int pickRandomRequestedAmount() {
    return ThreadLocalRandom.current()
        .nextInt(MIN_REQUESTED_AMOUNT, MAX_REQUESTED_AMOUNT + 1);
  }

  private String pickRandomLoanPurpose() {
    return LOAN_PURPOSES[ThreadLocalRandom.current().nextInt(LOAN_PURPOSES.length)];
  }

  private Resource pickRandomSampleIdCard() {
    return sampleIdCards[ThreadLocalRandom.current().nextInt(sampleIdCards.length)];
  }

  private void ensureProcessDeployed() {
    long deployedDefinitions =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(processDefinitionKey)
            .count();
    if (deployedDefinitions == 0) {
      throw new ResponseStatusException(
          HttpStatus.PRECONDITION_FAILED,
          "Process definition '"
              + processDefinitionKey
              + "' is not deployed. Ensure src/main/resources/bpmn/LoanApprovalProcess_AI.bpmn "
              + "is on the classpath (auto-deployment) or deploy it from Fluxnova Modeler.");
    }
  }

  private static Resource[] loadSampleIdCards(
      ResourcePatternResolver resourcePatternResolver, String pattern) {
    try {
      Resource[] resources = resourcePatternResolver.getResources(pattern);
      if (resources.length == 0) {
        throw new IllegalStateException("No sample ID cards found matching " + pattern);
      }
      Arrays.sort(resources, (left, right) -> {
        String leftName = left.getFilename() == null ? "" : left.getFilename();
        String rightName = right.getFilename() == null ? "" : right.getFilename();
        return leftName.compareTo(rightName);
      });
      return resources;
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to load sample ID cards from " + pattern, exception);
    }
  }

  private byte[] readSampleIdCardBytes(Resource sampleIdCard) {
    try (InputStream inputStream = readSampleIdCard(sampleIdCard)) {
      return inputStream.readAllBytes();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read sample ID card", exception);
    }
  }

  private InputStream readSampleIdCard(Resource sampleIdCard) {
    try {
      if (!sampleIdCard.exists()) {
        throw new IllegalStateException(
            "Sample ID card not found at " + sampleIdCard.getDescription());
      }
      return sampleIdCard.getInputStream();
    } catch (IOException exception) {
      throw new UncheckedIOException("Failed to read sample ID card", exception);
    }
  }

}
