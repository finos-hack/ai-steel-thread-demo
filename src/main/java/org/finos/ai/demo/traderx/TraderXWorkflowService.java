package org.finos.ai.demo.traderx;

import java.time.Instant;
import java.util.*;
import org.finos.fluxnova.bpm.engine.*;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@Service
public class TraderXWorkflowService {
  private final tools.jackson.databind.ObjectMapper json = new tools.jackson.databind.ObjectMapper();
  private final TraderXClient client;
  private final Map<String,TraderXWorkflowDefinition> definitions;
  private final RuntimeService runtime;
  private final TaskService tasks;
  private final HistoryService history;

  public TraderXWorkflowService(TraderXClient client, RuntimeService runtime,
      TaskService tasks, HistoryService history, List<TraderXWorkflowDefinition> definitions) {
    this.definitions = new LinkedHashMap<>();
    for (var definition : definitions) {
      if (this.definitions.put(definition.kind(), definition) != null) throw new IllegalStateException("Duplicate TraderX workflow registration");
    }
    this.client = client; this.runtime = runtime; this.tasks = tasks; this.history = history;
  }

  public record Decision(String reviewer, String decision, String comment) {}

  public Map<String, Object> options() {
    return Map.of("accounts", client.accounts(), "memberships", client.memberships(),
        "securities", client.securities());
  }
  public JsonNode people(String search) { return client.people(text(search, "Search", 80)); }
  public JsonNode quote(String ticker) { return client.quote(ticker(ticker)); }

  public Map<String,Object> start(String kind, Object request) {
    if (request == null) throw bad("Request is required");
    var input = json.valueToTree(request);
    var variables = new LinkedHashMap<>(definition(kind).prepare(input));
    return startPrepared(kind,input.path("requester").asText(),input.path("reason").asText(),variables);
  }
  private Map<String,Object> startPrepared(String kind,String requester,String reason,Map<String,Object> v) {
    requester=text(requester,"Requester",80); client.person(requester);
    v.put("requester",requester); v.put("reason",text(reason,"Reason",1000));
    v.put("workflowKind",kind); v.put("status","WAITING_APPROVAL"); v.put("approved",false);
    v.put("requestedAt",Instant.now().toString());
    var instance=runtime.startProcessInstanceByKey(key(kind),"TX-"+UUID.randomUUID(),v);
    return detail(instance.getId());
  }
  public Map<String,Object> plannedVariables(String kind,Map<String,Object> plan) {
    var itemsKey=definition(kind).batchItemsKey();
    if(itemsKey==null) throw bad("This workflow does not accept a batch plan");
    Map<String,Object> v=new LinkedHashMap<>();
    v.put("accountId",plan.get("accountId")); v.put("accountName",plan.get("accountName"));
    v.put("planJson",storeJson(json.writeValueAsString(plan))); v.put("resultsJson","[]");
    v.put("batchIndex",0); v.put("batchSize",((List<?>)plan.get(itemsKey)).size());
    return v;
  }
  public Map<String,Object> startPlanned(String kind,String requester,String reason,Map<String,Object> plan) {
    return startPrepared(kind,requester,reason,plannedVariables(kind,plan));
  }

  // Demo runs one API replica. The engine's task/async-job transaction additionally guards concurrent completion.
  public synchronized Map<String, Object> decide(String id, Decision d) {
    var instance = requireInstance(id);
    Task task = tasks.createTaskQuery().processInstanceId(id).taskDefinitionKey("Review").singleResult();
    if (task == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is no longer awaiting approval");
    if (d == null || !Set.of("APPROVE", "REJECT").contains(Objects.toString(d.decision(), "")))
      throw bad("Choose APPROVE or REJECT");
    String reviewer = text(d.reviewer(), "Reviewer", 80);
    String comment = text(d.comment(), "Review note", 1000);
    if (reviewer.equals(runtime.getVariable(id, "requester"))) throw bad("Choose a reviewer other than the requester");
    client.person(reviewer);
    boolean approved = d.decision().equals("APPROVE");
    tasks.setAssignee(task.getId(), reviewer);
    tasks.complete(task.getId(), Map.of("approved", approved, "reviewer", reviewer,
        "reviewComment", comment, "reviewedAt", Instant.now().toString(),
        "status", approved ? "QUEUED" : "REJECTED"));
    return detail(instance.getId());
  }

  public List<Map<String, Object>> list(String kind) {
    var rows = new ArrayList<>(history.createHistoricProcessInstanceQuery().processDefinitionKey(key(kind))
        .orderByProcessInstanceStartTime().desc().listPage(0, 100).stream()
        .map(this::summary).toList());
    return rows;
  }
  public Map<String, Object> detail(String id) {
    var instance = requireInstance(id);
    Map<String, Object> result = summary(instance);
    var v = variables(id);
    result.put("variables", v);
    result.put("activities", history.createHistoricActivityInstanceQuery().processInstanceId(id)
        .orderByHistoricActivityInstanceStartTime().asc().list().stream()
        .map(a -> {
          Map<String, Object> row = new LinkedHashMap<>();
          row.put("name", a.getActivityName()); row.put("type", a.getActivityType());
          row.put("startedAt", a.getStartTime()); row.put("endedAt", a.getEndTime());
          return row;
        }).toList());
    result.put("incidents", runtime.createIncidentQuery().processInstanceId(id).list().stream()
        .map(i -> Map.of("message", Objects.toString(i.getIncidentMessage(), "Execution incident"))).toList());
    if (v.containsKey("orderId")) {
      try { result.put("liveOrder", client.order((String)v.get("orderId"))); }
      catch (RuntimeException ex) { result.put("liveStatusError", "TraderX order status is temporarily unavailable"); }
    }
    if (v.containsKey("planJson")) {
      result.put("plan",json.readTree(readJson(v.get("planJson"))));
      var receipts = json.readTree(readJson(v.getOrDefault("resultsJson","[]")));
      result.put("results",receipts);
      List<Map<String,Object>> live = new ArrayList<>();
      for (JsonNode receipt : receipts) {
        String orderId = receipt.path("orderId").asText();
        if (orderId.isBlank()) continue;
        try { live.add(Map.of("orderId",orderId,"order",client.order(orderId))); }
        catch (RuntimeException ex) { live.add(Map.of("orderId",orderId,"error","Live status unavailable")); }
      }
      result.put("liveOrders",live);
    }
    return result;
  }
  private Map<String, Object> summary(HistoricProcessInstance instance) {
    Map<String, Object> v = variables(instance.getId());
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", instance.getId()); row.put("businessKey", instance.getBusinessKey());
    row.put("kind", v.get("workflowKind")); row.put("status", v.get("status"));
    row.put("requester", v.get("requester")); row.put("startedAt", instance.getStartTime());
    row.put("endedAt", instance.getEndTime());
    String kind = Objects.toString(v.get("workflowKind"));
    row.put("title",definition(kind).title(v));
    return row;
  }
  private Map<String, Object> variables(String id) {
    Map<String, Object> v = new LinkedHashMap<>();
    history.createHistoricVariableInstanceQuery().processInstanceId(id).list()
        .forEach(x -> v.put(x.getName(), x.getValue()));
    return v;
  }
  private HistoricProcessInstance requireInstance(String id) {
    var i = history.createHistoricProcessInstanceQuery().processInstanceId(id).singleResult();
    if (i == null || definitions.values().stream().noneMatch(d -> d.processKey().equals(i.getProcessDefinitionKey())))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TraderX workflow not found");
    return i;
  }
  static void requireMembership(TraderXClient client, int accountId, String username) {
    for (JsonNode member : client.memberships())
      if (member.path("accountId").asInt() == accountId && member.path("username").asText().equals(username)) return;
    throw bad("Requester is not linked to the selected TraderX account");
  }
  static void requireUniqueName(TraderXClient client, String name) {
    for (JsonNode a : client.accounts())
      if (a.path("displayName").asText().equalsIgnoreCase(name)) throw bad("An account with this name already exists");
  }
  private TraderXWorkflowDefinition definition(String kind) {
    var result=definitions.get(kind);
    if(result==null) throw bad("Unknown or unavailable workflow kind");
    return result;
  }
  private String key(String kind) { return definition(kind).processKey(); }
  static String text(String value, String label, int max) {
    if (value == null || value.isBlank() || value.trim().length() > max)
      throw bad(label + " is required (maximum " + max + " characters)");
    return value.trim();
  }
  static String ticker(String value) {
    String ticker = text(value, "Security", 12).toUpperCase(Locale.ROOT);
    if (!ticker.matches("[A-Z0-9.\\-]+")) throw bad("Invalid ticker");
    return ticker;
  }
  static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
  // Fluxnova string variables use VARCHAR(4000); large baskets/receipts use its binary storage.
  static Object storeJson(String text) {
    return text.length() > 3500 ? text.getBytes(java.nio.charset.StandardCharsets.UTF_8) : text;
  }
  static String readJson(Object value) {
    return value instanceof byte[] bytes ? new String(bytes,java.nio.charset.StandardCharsets.UTF_8) : (String)value;
  }

}
