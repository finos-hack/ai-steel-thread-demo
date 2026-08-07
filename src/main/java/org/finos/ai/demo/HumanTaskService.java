package org.finos.ai.demo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.finos.fluxnova.bpm.engine.HistoryService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.finos.fluxnova.bpm.engine.task.TaskQuery;
import org.finos.ai.demo.feedback.Mi11FeedbackVariables;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Launch UI tasklist: list / claim / complete BPMN user tasks without Cockpit. */
@Service
public class HumanTaskService {

  private static final Set<String> CONTEXT_VARIABLES =
      Set.of(
          "requestedAmount",
          "loanPurpose",
          "approvalDecision",
          "creditScore",
          "creditRiskBand",
          "aiRiskScore",
          "employmentVerified",
          "annualIncome",
          "isFraudulent",
          "fraudRiskScore",
          "isInvalid",
          "complianceStatus",
          Mi11FeedbackVariables.LOAN_OFFICER_DECISION,
          Mi11FeedbackVariables.AGREES_WITH_AI,
          Mi11FeedbackVariables.COMMENT);

  /** Agent traces shown unredacted to loan officers for MANUAL_REVIEW (authorized human path). */
  private static final Set<String> AGENT_CONVERSATION_VARIABLES =
      Set.of("agentLastResponse", "agentConversationTrace", "agentConversationHistory");

  private final TaskService taskService;
  private final HistoryService historyService;
  private final String processDefinitionKey;
  private final String claimUserId;

  public HumanTaskService(
      TaskService taskService,
      HistoryService historyService,
      @Value("${fluxnova.loan-approval-demo.process-definition-key:LoanApprovalProcess_AI}")
          String processDefinitionKey,
      @Value("${fluxnova.bpm.admin-user.id:demo}") String claimUserId) {
    this.taskService = taskService;
    this.historyService = historyService;
    this.processDefinitionKey = processDefinitionKey;
    this.claimUserId = claimUserId;
  }

  public HumanTaskList listOpenTasks(String processInstanceId) {
    TaskQuery query =
        taskService
            .createTaskQuery()
            .processDefinitionKey(processDefinitionKey)
            .active()
            .orderByTaskCreateTime()
            .desc();
    if (processInstanceId != null && !processInstanceId.isBlank()) {
      query = query.processInstanceId(processInstanceId.trim());
    }
    List<Task> tasks = query.list();
    List<HumanTaskSummary> summaries = new ArrayList<>(tasks.size());
    for (Task task : tasks) {
      summaries.add(toSummary(task));
    }
    return new HumanTaskList(summaries.size(), List.copyOf(summaries));
  }

  public HumanTaskDetail getTask(String taskId) {
    Task task = requireActiveTask(taskId);
    return toDetail(task);
  }

  public HumanTaskDetail claim(String taskId) {
    Task task = requireActiveTask(taskId);
    String assignee = task.getAssignee();
    if (assignee == null || assignee.isBlank()) {
      taskService.claim(taskId, claimUserId);
    } else if (!claimUserId.equals(assignee)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Task is already claimed by '" + assignee + "'");
    }
    return toDetail(requireActiveTask(taskId));
  }

  public HumanTaskDetail complete(String taskId, HumanTaskCompleteRequest request) {
    Task task = requireActiveTask(taskId);
    if (task.getAssignee() == null || task.getAssignee().isBlank()) {
      taskService.claim(taskId, claimUserId);
      task = requireActiveTask(taskId);
    } else if (!claimUserId.equals(task.getAssignee())) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Task is already claimed by '" + task.getAssignee() + "'");
    }

    HumanTaskFormSchema schema =
        HumanTaskSchemas.forTaskDefinitionKey(task.getTaskDefinitionKey());
    Map<String, Object> variables = normalizeAndValidate(schema, request);
    ProcessReviewContext review =
        loadReviewContext(task.getProcessInstanceId(), schema.taskDefinitionKey());

    taskService.complete(taskId, variables);

    // Task is gone after complete — return the last known detail snapshot without reloading.
    return new HumanTaskDetail(
        task.getId(),
        task.getName(),
        task.getTaskDefinitionKey(),
        task.getProcessInstanceId(),
        resolveBusinessKey(task.getProcessInstanceId()),
        task.getCreateTime(),
        claimUserId,
        true,
        schema,
        review.context(),
        review.agentConversation());
  }

  private Map<String, Object> normalizeAndValidate(
      HumanTaskFormSchema schema, HumanTaskCompleteRequest request) {
    Map<String, Object> incoming =
        request == null || request.variables() == null
            ? Map.of()
            : request.variables();
    Map<String, Object> completion = new LinkedHashMap<>();

    for (HumanTaskFormField field : schema.fields()) {
      Object raw = incoming.get(field.id());
      if (raw == null || (raw instanceof String text && text.isBlank())) {
        if (field.required()) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST, "Field '" + field.id() + "' is required");
        }
        continue;
      }
      Object normalized = normalizeField(field, raw);
      completion.put(field.id(), normalized);
    }

    if (HumanTaskSchemas.LOAN_OFFICER.equals(schema.taskDefinitionKey())) {
      completion.putIfAbsent(Mi11FeedbackVariables.SUBMITTED_AT, Instant.now().toString());
    }

    // Allow unknown schemas to pass through free-form variables (demo escape hatch).
    if (schema.fields().isEmpty()) {
      incoming.forEach(
          (key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
              completion.put(key, value);
            }
          });
    }

    return completion;
  }

  private static Object normalizeField(HumanTaskFormField field, Object raw) {
    String type = field.type() == null ? "string" : field.type();
    return switch (type) {
      case "enum" -> {
        String value = String.valueOf(raw).trim().toUpperCase();
        if (field.options() != null && !field.options().contains(value)) {
          throw new ResponseStatusException(
              HttpStatus.BAD_REQUEST,
              "Field '" + field.id() + "' must be one of " + field.options());
        }
        yield value;
      }
      case "boolean" -> {
        if (raw instanceof Boolean bool) {
          yield bool;
        }
        yield Boolean.parseBoolean(String.valueOf(raw).trim());
      }
      case "textarea", "string" -> {
        String text = String.valueOf(raw).trim();
        if (Mi11FeedbackVariables.COMMENT.equals(field.id())
            && text.length() > Mi11FeedbackVariables.MAX_COMMENT_LENGTH) {
          text = text.substring(0, Mi11FeedbackVariables.MAX_COMMENT_LENGTH) + "…";
        }
        yield text;
      }
      default -> raw;
    };
  }

  private Task requireActiveTask(String taskId) {
    if (taskId == null || taskId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taskId is required");
    }
    Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
    if (task == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Task '" + taskId + "' was not found");
    }
    if (task.getProcessDefinitionId() != null
        && !task.getProcessDefinitionId().startsWith(processDefinitionKey + ":")) {
      Task owned =
          taskService
              .createTaskQuery()
              .taskId(taskId)
              .processDefinitionKey(processDefinitionKey)
              .active()
              .singleResult();
      if (owned == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "Task '" + taskId + "' is not part of this demo process");
      }
      return owned;
    }
    return task;
  }

  private HumanTaskSummary toSummary(Task task) {
    String assignee = task.getAssignee();
    boolean claimed = assignee != null && !assignee.isBlank();
    return new HumanTaskSummary(
        task.getId(),
        task.getName(),
        task.getTaskDefinitionKey(),
        task.getProcessInstanceId(),
        resolveBusinessKey(task.getProcessInstanceId()),
        task.getCreateTime(),
        assignee,
        claimed,
        task.getTaskDefinitionKey());
  }

  private HumanTaskDetail toDetail(Task task) {
    String assignee = task.getAssignee();
    boolean claimed = assignee != null && !assignee.isBlank();
    HumanTaskFormSchema schema =
        HumanTaskSchemas.forTaskDefinitionKey(task.getTaskDefinitionKey());
    ProcessReviewContext review =
        loadReviewContext(task.getProcessInstanceId(), schema.taskDefinitionKey());
    return new HumanTaskDetail(
        task.getId(),
        task.getName(),
        task.getTaskDefinitionKey(),
        task.getProcessInstanceId(),
        resolveBusinessKey(task.getProcessInstanceId()),
        task.getCreateTime(),
        assignee,
        claimed,
        schema,
        review.context(),
        review.agentConversation());
  }

  private String resolveBusinessKey(String processInstanceId) {
    if (processInstanceId == null || processInstanceId.isBlank()) {
      return null;
    }
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
    return instance == null ? null : instance.getBusinessKey();
  }

  private ProcessReviewContext loadReviewContext(String processInstanceId, String taskDefinitionKey) {
    Map<String, Object> context = new LinkedHashMap<>();
    Map<String, Object> conversationVars = new LinkedHashMap<>();
    if (processInstanceId == null || processInstanceId.isBlank()) {
      return new ProcessReviewContext(context, null);
    }
    boolean includeConversation = HumanTaskSchemas.LOAN_OFFICER.equals(taskDefinitionKey);
    List<HistoricVariableInstance> variables =
        historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(processInstanceId)
            .disableBinaryFetching()
            .disableCustomObjectDeserialization()
            .list();
    for (HistoricVariableInstance variable : variables) {
      String name = variable.getName();
      if (name == null) {
        continue;
      }
      String base = baseVariableName(name);
      if (CONTEXT_VARIABLES.contains(name) || CONTEXT_VARIABLES.contains(base)) {
        putPreferUnscoped(context, base, name, variable.getValue());
      }
      if (includeConversation
          && (AGENT_CONVERSATION_VARIABLES.contains(name)
              || AGENT_CONVERSATION_VARIABLES.contains(base)
              || name.startsWith("_agentConversation")
              || base.startsWith("agentConversation")
              || "agentLastResponse".equals(base))) {
        String key = normalizeConversationKey(base, name);
        putPreferUnscoped(conversationVars, key, name, variable.getValue());
      }
    }

    HumanTaskAgentConversation conversation = null;
    if (includeConversation) {
      conversation =
          new HumanTaskAgentConversation(
              asText(conversationVars.get("agentLastResponse")),
              asText(conversationVars.get("agentConversationTrace")),
              conversationVars.get("agentConversationHistory"));
      if (conversation.isEmpty()) {
        conversation = null;
      }
    }
    return new ProcessReviewContext(context, conversation);
  }

  private static void putPreferUnscoped(
      Map<String, Object> target, String canonicalKey, String rawName, Object value) {
    if (value == null) {
      return;
    }
    boolean scoped = rawName.contains("__");
    if (!target.containsKey(canonicalKey) || !scoped) {
      target.put(canonicalKey, value);
    }
  }

  private static String baseVariableName(String name) {
    int sep = name.indexOf("__");
    if (sep > 0) {
      return name.substring(0, sep);
    }
    if (name.startsWith("_") && name.length() > 1) {
      return name.substring(1);
    }
    return name;
  }

  private static String normalizeConversationKey(String base, String rawName) {
    if (AGENT_CONVERSATION_VARIABLES.contains(base)) {
      return base;
    }
    if (rawName.startsWith("_agentConversationHistory")
        || "agentConversationHistory".equals(base)) {
      return "agentConversationHistory";
    }
    if (rawName.contains("Trace") || base.contains("Trace")) {
      return "agentConversationTrace";
    }
    if (rawName.contains("LastResponse") || base.contains("LastResponse")) {
      return "agentLastResponse";
    }
    return base;
  }

  private static String asText(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return text.isBlank() ? null : text;
    }
    return String.valueOf(value);
  }

  private record ProcessReviewContext(
      Map<String, Object> context, HumanTaskAgentConversation agentConversation) {}
}
