package org.finos.ai.demo.traderx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import tools.jackson.databind.*;

class TraderXStaleTest {
  static ProcessEngine engine;
  static TraderXClient client = mock(TraderXClient.class);
  static ObjectMapper json = new ObjectMapper();
  TraderXWorkflowService workflows;
  StalePlanning planning;
  static JsonNode node(String s) { return json.readTree(s); }
  static JsonNode order(String id,String status,int remaining) {
    return node("{\"orderId\":\""+id+"\",\"accountId\":10,\"security\":\"AAPL\",\"side\":\"Buy\",\"quantity\":10,\"remainingQuantity\":"+remaining+",\"limitPrice\":100,\"status\":\""+status+"\",\"createdAt\":\"2026-01-01T12:00:00Z\"}");
  }
  @BeforeAll static void deploy() {
    var cfg = new StandaloneInMemProcessEngineConfiguration();
    cfg.setJdbcUrl("jdbc:h2:mem:traderx-batch-test;DB_CLOSE_DELAY=-1");
    cfg.setDatabaseSchemaUpdate("true"); cfg.setHistory("full"); cfg.setJobExecutorActivate(false);
    cfg.setBeans(Map.of("traderXStaleExecution",new TraderXStaleExecution(client,json)));
    engine = cfg.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addClasspathResource("bpmn/TraderX_StaleOrderReview.bpmn").deploy();
  }
  @AfterAll static void close() { if (engine != null) engine.close(); }
  @BeforeEach void setup() {
    reset(client);
    workflows = new TraderXWorkflowService(client,engine.getRuntimeService(),engine.getTaskService(),engine.getHistoryService(),List.of(new StaleWorkflow(new StalePlanning(client,json))));
    planning = new StalePlanning(client,json);
    when(client.person(anyString())).thenReturn(node("{\"logonId\":\"user01\"}"));
    when(client.account(10)).thenReturn(node("{\"id\":10,\"displayName\":\"Test portfolio\"}"));
    when(client.memberships()).thenReturn(node("[{\"accountId\":10,\"username\":\"user01\"}]"));
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":10}]"));
    when(client.orders(10)).thenReturn(node("[]"));
    when(client.security(anyString())).thenReturn(node("{}"));
    when(client.quote(anyString())).thenReturn(node("{\"price\":100,\"asOf\":\"2026-11-05T12:00:00Z\"}"));
    when(client.order(anyString())).thenAnswer(x -> order(x.getArgument(0),"NEW",5));
  }



  StalePlanning.StaleReview stale(String... actions) {
    List<StalePlanning.OrderAction> rows = new ArrayList<>();
    for (int i=0;i<actions.length;i++) rows.add(new StalePlanning.OrderAction("order-"+i,actions[i]));
    return new StalePlanning.StaleReview("user01","Test order review",10,60,rows);
  }
  String startStale(String... actions) {
    var orders = json.createArrayNode(); for(int i=0;i<actions.length;i++) orders.add(order("order-"+i,"NEW",10));
    when(client.orders(10)).thenReturn(orders);
    return (String)workflows.startPlanned("stale","user01","Test",planning.stale(stale(actions),true)).get("id");
  }
  void approve(String id) { workflows.decide(id,new TraderXWorkflowService.Decision("user02","APPROVE","Reviewed test plan")); }
  void execute(String id) {
    var job=engine.getManagementService().createJobQuery().processInstanceId(id).singleResult();
    assertThat(job).isNotNull(); engine.getManagementService().executeJob(job.getId());
  }
  String status(String id) { return (String)workflows.detail(id).get("status"); }






  @Test void keepDoesNotCancelAndPartialOrderCancelsOnlyRemaining() {
    String id=startStale("KEEP","CANCEL");
    when(client.order("order-1")).thenReturn(order("order-1","PARTIALLY_FILLED",3));
    when(client.cancelOrder("order-1")).thenReturn(order("order-1","CANCELED",0));
    approve(id); execute(id);
    verify(client,never()).cancelOrder(anyString());
    execute(id); assertThat(status(id)).isEqualTo("REVIEWED");
    verify(client,times(1)).cancelOrder("order-1");
    assertThat(workflows.detail(id).get("results").toString()).contains("KEPT","CANCELED","PARTIALLY_FILLED");
  }
  @Test void orderFilledDuringApprovalIsSkipped() {
    String id=startStale("CANCEL"); approve(id);
    when(client.order("order-0")).thenReturn(order("order-0","FILLED",0)); execute(id);
    assertThat(status(id)).isEqualTo("REVIEWED"); verify(client,never()).cancelOrder(anyString());
    assertThat(workflows.detail(id).get("results").toString()).contains("SKIPPED_CLOSED");
  }
  @Test void fillRacingWithCancellationIsNotReportedCanceled() {
    String id=startStale("CANCEL"); approve(id);
    when(client.cancelOrder("order-0")).thenReturn(order("order-0","FILLED",0)); execute(id);
    assertThat(workflows.detail(id).get("results").toString()).contains("SKIPPED_CLOSED");
    assertThat(status(id)).isEqualTo("REVIEWED");
  }
  @Test void cancelTimeoutDoesNotRetryOrContinueToNextOrder() {
    String id=startStale("CANCEL","CANCEL"); approve(id);
    when(client.cancelOrder("order-0")).thenThrow(new IllegalStateException("Timeout")); execute(id);
    assertThat(status(id)).isEqualTo("NEEDS_RECONCILIATION");
    verify(client,times(1)).cancelOrder("order-0"); verify(client,never()).cancelOrder("order-1");
    assertThat(engine.getManagementService().createJobQuery().processInstanceId(id).count()).isZero();
  }
  @Test void rejectsUnknownOrderAndAgeFilterActuallyFilters() {
    var o=order("order-0","NEW",10).deepCopy();
    ((tools.jackson.databind.node.ObjectNode)o).put("createdAt",java.time.Instant.now().toString());
    when(client.orders(10)).thenReturn(json.createArrayNode().add(o));
    assertThat((List<?>)planning.stale(stale("CANCEL"),false).get("orders")).isEmpty();
    assertThatThrownBy(() -> planning.stale(stale("CANCEL"),true)).hasMessageContaining("no longer open");
  }
  @Test void staleReviewRejectsSelfApprovalAndWrongMembership() {
    String id=startStale("KEEP");
    assertThatThrownBy(() -> workflows.decide(id,new TraderXWorkflowService.Decision("user01","APPROVE","test"))).hasMessageContaining("other than");
    approve(id); when(client.memberships()).thenReturn(node("[]")); execute(id);
    assertThat(status(id)).isEqualTo("NEEDS_RECONCILIATION"); verify(client,never()).cancelOrder(anyString());
  }






}
