package org.finos.ai.demo.traderx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import tools.jackson.databind.*;

class TraderXRebalanceTest {
  static ProcessEngine engine;
  static TraderXClient client = mock(TraderXClient.class);
  static ObjectMapper json = new ObjectMapper();
  TraderXWorkflowService workflows;
  RebalancePlanning planning;
  static JsonNode node(String s) { return json.readTree(s); }
  static JsonNode order(String id,String status,int remaining) {
    return node("{\"orderId\":\""+id+"\",\"accountId\":10,\"security\":\"AAPL\",\"side\":\"Buy\",\"quantity\":10,\"remainingQuantity\":"+remaining+",\"limitPrice\":100,\"status\":\""+status+"\",\"createdAt\":\"2026-01-01T12:00:00Z\"}");
  }
  @BeforeAll static void deploy() {
    var cfg = new StandaloneInMemProcessEngineConfiguration();
    cfg.setJdbcUrl("jdbc:h2:mem:traderx-rebalance-test;DB_CLOSE_DELAY=0");
    cfg.setDatabaseSchemaUpdate("true"); cfg.setHistory("full"); cfg.setJobExecutorActivate(false);
    cfg.setBeans(Map.of("traderXRebalanceExecution",new TraderXRebalanceExecution(client,json)));
    engine = cfg.buildProcessEngine();
    engine.getRepositoryService().createDeployment().addClasspathResource("bpmn/TraderX_PortfolioRebalance.bpmn").deploy();
  }
  @AfterAll static void close() { if (engine != null) engine.close(); }
  @BeforeEach void setup() {
    reset(client);
    workflows = new TraderXWorkflowService(client,engine.getRuntimeService(),engine.getTaskService(),engine.getHistoryService(),List.of(new RebalanceWorkflow(new RebalancePlanning(client,json))));
    planning = new RebalancePlanning(client,json);
    when(client.person(anyString())).thenReturn(node("{\"logonId\":\"user01\"}"));
    when(client.account(10)).thenReturn(node("{\"id\":10,\"displayName\":\"Test portfolio\"}"));
    when(client.memberships()).thenReturn(node("[{\"accountId\":10,\"username\":\"user01\"}]"));
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":10}]"));
    when(client.orders(10)).thenReturn(node("[]"));
    when(client.security(anyString())).thenReturn(node("{}"));
    when(client.quote(anyString())).thenReturn(node("{\"price\":100,\"asOf\":\"2026-11-05T12:00:00Z\"}"));
    when(client.order(anyString())).thenAnswer(x -> order(x.getArgument(0),"NEW",5));
  }
  RebalancePlanning.Rebalance rebalance(List<RebalancePlanning.Leg> legs) {
    return new RebalancePlanning.Rebalance("user01","Test rebalance",10,
        List.of(new RebalancePlanning.Target("AAPL",50.0),new RebalancePlanning.Target("MSFT",50.0)),legs);
  }
  List<RebalancePlanning.Leg> legs() {
    return List.of(new RebalancePlanning.Leg("AAPL","Sell",5,100.0),new RebalancePlanning.Leg("MSFT","Buy",5,100.0));
  }
  String startRebalance() { return (String)workflows.startPlanned("rebalance","user01","Test",planning.rebalance(rebalance(legs()),true)).get("id"); }


  void approve(String id) { workflows.decide(id,new TraderXWorkflowService.Decision("user02","APPROVE","Reviewed test plan")); }
  void execute(String id) {
    var job=engine.getManagementService().createJobQuery().processInstanceId(id).singleResult();
    assertThat(job).isNotNull(); engine.getManagementService().executeJob(job.getId());
  }
  String status(String id) { return (String)workflows.detail(id).get("status"); }
  @Test void calculatesWholeShareBasketSellsFirst() {
    var plan=planning.rebalance(rebalance(null),false);
    assertThat(plan.get("portfolioValue")).isEqualTo(1000.0);
    assertThat(plan.get("legs")).isEqualTo(legs());
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void rejectsBadWeightsShortsOpenOrdersAndUnfundedEdits() {
    assertThatThrownBy(() -> planning.rebalance(new RebalancePlanning.Rebalance("user01","test",10,List.of(new RebalancePlanning.Target("AAPL",90.0)),null),false)).hasMessageContaining("total 100");
    assertThatThrownBy(() -> planning.rebalance(rebalance(List.of(new RebalancePlanning.Leg("MSFT","Buy",5,100.0))),true)).hasMessageContaining("Buy cost at entered limits exceeds");
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":-1}]"));
    assertThatThrownBy(() -> planning.rebalance(rebalance(null),false)).hasMessageContaining("long-only");
    when(client.orders(10)).thenReturn(json.createArrayNode().add(order("open","NEW",2)));
    assertThatThrownBy(() -> planning.rebalance(rebalance(null),false)).hasMessageContaining("open orders");
  }
  @Test void persistsEachReceiptBeforeNextOrder() {
    when(client.submitTrade(10,"AAPL","Sell",5,100)).thenReturn(order("sell","NEW",5));
    when(client.submitTrade(10,"MSFT","Buy",5,100)).thenReturn(order("buy","NEW",5));
    String id=startRebalance();
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
    approve(id); execute(id);
    assertThat(status(id)).isEqualTo("EXECUTING");
    assertThat(engine.getRuntimeService().getVariable(id,"batchIndex")).isEqualTo(1);
    assertThat(engine.getRuntimeService().getVariable(id,"resultsJson").toString()).contains("sell");
    verify(client,never()).submitTrade(10,"MSFT","Buy",5,100);
    execute(id); assertThat(status(id)).isEqualTo("BASKET_SUBMITTED");
    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count()).isZero();
    verify(client,times(2)).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void secondOrderTimeoutRetainsFirstReceiptAndStops() {
    when(client.submitTrade(10,"AAPL","Sell",5,100)).thenReturn(order("sell","NEW",5));
    when(client.submitTrade(10,"MSFT","Buy",5,100)).thenThrow(new IllegalStateException("Timeout"));
    String id=startRebalance(); approve(id); execute(id); execute(id);
    assertThat(status(id)).isEqualTo("NEEDS_RECONCILIATION");
    String receipts=engine.getRuntimeService().getVariable(id,"resultsJson").toString();
    assertThat(receipts).contains("SUBMITTED","sell","UNKNOWN");
    assertThat(engine.getManagementService().createJobQuery().processInstanceId(id).count()).isZero();
    assertThat(engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getTaskDefinitionKey()).isEqualTo("Reconcile");
  }
  @Test void changedHoldingsBlockEveryWrite() {
    String id=startRebalance(); approve(id);
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":11}]")); execute(id);
    assertThat(status(id)).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(engine.getRuntimeService().getVariable(id,"resultsJson").toString()).contains("BLOCKED");
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void rejectedBasketWritesNothing() {
    String id=startRebalance(); workflows.decide(id,new TraderXWorkflowService.Decision("user02","REJECT","Not approved"));
    assertThat(status(id)).isEqualTo("REJECTED"); verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }






  @Test void largeInstructionsSplitBeforeApprovalAndExecuteWithSeparateReceipts() {
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":600002}]"));
    var request=rebalance(List.of(new RebalancePlanning.Leg("AAPL","Sell",300001,100.0),
        new RebalancePlanning.Leg("MSFT","Buy",300001,100.0)));
    var plan=planning.rebalance(request,true);
    var rows=json.valueToTree(plan).path("legs");
    assertThat(rows.size()).isEqualTo(62);
    assertThat(rows.get(0).path("quantity").asInt()).isEqualTo(10000);
    assertThat(rows.get(30).path("quantity").asInt()).isEqualTo(1);
    assertThat(rows.get(61).path("quantity").asInt()).isEqualTo(1);
    assertThat(rows.get(31).path("side").asText()).isEqualTo("Buy");
    assertThat(rows.get(31).path("limitPrice").asDouble()).isEqualTo(100.0);
    String id=(String)workflows.startPlanned("rebalance","user01","Large basket",plan).get("id");
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
    approve(id);
    var counter=new java.util.concurrent.atomic.AtomicInteger();
    when(client.submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble()))
        .thenAnswer(x -> order("split-"+counter.incrementAndGet(),"NEW",x.getArgument(3)));
    for(int i=0;i<62;i++) execute(id);
    assertThat(workflows.detail(id).get("status")).isEqualTo("BASKET_SUBMITTED");
    assertThat(((JsonNode)workflows.detail(id).get("results")).size()).isEqualTo(62);
    verify(client,times(30)).submitTrade(10,"AAPL","Sell",10000,100.0);
    verify(client).submitTrade(10,"AAPL","Sell",1,100.0);
  }
  @Test void splittingPreservesLargeTotalsAndStillRejectsOversellingOrExcessiveBatches() {
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":1000000}]"));
    var plan=planning.rebalance(rebalance(List.of(new RebalancePlanning.Leg("AAPL","Sell",470234,100.0),
        new RebalancePlanning.Leg("MSFT","Buy",470234,100.0))),true);
    var rows=json.valueToTree(plan).path("legs");
    long sell=0,buy=0;
    for(var row:rows) {
      assertThat(row.path("quantity").asInt()).isBetween(1,10000);
      if(row.path("side").asText().equals("Sell")) sell+=row.path("quantity").asInt();
      else buy+=row.path("quantity").asInt();
    }
    assertThat(sell).isEqualTo(470234); assertThat(buy).isEqualTo(470234);
    assertThatThrownBy(() -> planning.rebalance(rebalance(List.of(new RebalancePlanning.Leg("AAPL","Sell",1000001,100.0))),true))
        .hasMessageContaining("Cannot sell more");
    when(client.positions(10)).thenReturn(node("[{\"security\":\"AAPL\",\"quantity\":10000000}]"));
    assertThatThrownBy(() -> planning.rebalance(rebalance(List.of(new RebalancePlanning.Leg("AAPL","Sell",5000001,100.0))),true))
        .hasMessageContaining("500 split orders");
  }

  @Test void quoteMovementDoesNotInvalidateBalancedLimitInstructions() {
    when(client.quote("AAPL")).thenReturn(node("{\"price\":80}"));
    when(client.quote("MSFT")).thenReturn(node("{\"price\":150}"));
    var plan=planning.rebalance(rebalance(legs()),true);
    assertThat(plan.get("estimatedBuys")).isEqualTo(500.0);
    assertThat(plan.get("estimatedSells")).isEqualTo(500.0);
    assertThat(plan.get("budgetBasis")).isEqualTo("ENTERED_LIMITS");
    assertThat(json.valueToTree(plan).path("legs").get(1).path("quantity").asInt()).isEqualTo(5);
  }
  @Test void limitEditsThatOverspendAreRejectedEvenWhenQuotesLookBalanced() {
    assertThatThrownBy(() -> planning.rebalance(rebalance(List.of(
        new RebalancePlanning.Leg("AAPL","Sell",5,100.0),new RebalancePlanning.Leg("MSFT","Buy",5,101.0))),true))
        .hasMessageContaining("Buy cost at entered limits exceeds");
  }

}
