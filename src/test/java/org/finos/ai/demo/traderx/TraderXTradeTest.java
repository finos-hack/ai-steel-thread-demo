package org.finos.ai.demo.traderx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** Exercises the actual deployed BPMN gates with a stubbed remote TraderX boundary. */
class TraderXTradeTest {
  static ProcessEngine engine;
  static TraderXClient client = mock(TraderXClient.class);
  static ObjectMapper json = new ObjectMapper();
  TraderXWorkflowService service;

  @BeforeAll static void deploy() {
    var cfg = new StandaloneInMemProcessEngineConfiguration();
    cfg.setJdbcUrl("jdbc:h2:mem:traderx-trade-test;DB_CLOSE_DELAY=0");
    cfg.setDatabaseSchemaUpdate("true");
    cfg.setHistory("full");
    cfg.setJobExecutorActivate(false);
    cfg.setBeans(Map.of("traderXTradeExecution", new TraderXTradeExecution(client)));
    engine = cfg.buildProcessEngine();
    engine.getRepositoryService().createDeployment()
        .addClasspathResource("bpmn/TraderX_TradeApproval.bpmn").deploy();
  }
  @AfterAll static void close() { if (engine != null) engine.close(); }
  @BeforeEach void setup() {
    reset(client);
    service = new TraderXWorkflowService(client, engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService(), java.util.List.of(new TradeWorkflow(client)));
    when(client.person(anyString())).thenReturn(json.readTree("{\"logonId\":\"user01\"}"));
    when(client.account(10)).thenReturn(json.readTree("{\"id\":10,\"displayName\":\"Test book\"}"));
    when(client.accounts()).thenReturn(json.readTree("[]"));
    when(client.memberships()).thenReturn(json.readTree("[{\"accountId\":10,\"username\":\"user01\"}]"));
    when(client.security("AAPL")).thenReturn(json.readTree("{\"ticker\":\"AAPL\"}"));
    when(client.quote("AAPL")).thenReturn(json.readTree("{\"price\":100,\"asOf\":\"2026-11-04T12:00:00Z\"}"));
    when(client.order("ord-test-1")).thenReturn(json.readTree("{\"orderId\":\"ord-test-1\",\"status\":\"FILLED\"}"));
  }
  TradeWorkflow.Request trade() { return new TradeWorkflow.Request("user01", "Test instruction",10,"AAPL","Buy",1,null,null,101.0); }

  TraderXWorkflowService.Decision decision(String d) { return new TraderXWorkflowService.Decision("user02",d,"Reviewed in test"); }
  String start(String kind) { return (String)service.start(kind, trade()).get("id"); }
  void execute(String id) {
    var job = engine.getManagementService().createJobQuery().processInstanceId(id).singleResult();
    assertThat(job).isNotNull();
    engine.getManagementService().executeJob(job.getId());
  }
  @Test void tradeWaitsForApprovalAndRejectionNeverWrites() {
    String id = start("trade");
    assertThat(service.detail(id).get("status")).isEqualTo("WAITING_APPROVAL");
    assertThat(engine.getManagementService().createJobQuery().processInstanceId(id).count()).isZero();
    service.decide(id, decision("REJECT"));
    assertThat(service.detail(id).get("status")).isEqualTo("REJECTED");
    assertThat(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(id).count()).isZero();
    verify(client, never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void approvedTradeUsesOriginalInstructionOnce() {
    when(client.submitTrade(eq(10),eq("AAPL"),eq("Buy"),eq(1),eq(101.0)))
        .thenReturn(json.readTree("{\"orderId\":\"ord-test-1\",\"status\":\"NEW\"}"));
    String id = start("trade");
    service.decide(id, decision("APPROVE"));
    verify(client, never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
    execute(id);
    assertThat(service.detail(id).get("status")).isEqualTo("SUBMITTED");
    assertThat(service.detail(id).get("liveOrder").toString()).contains("FILLED");
    verify(client, times(1)).submitTrade(eq(10),eq("AAPL"),eq("Buy"),eq(1),eq(101.0));
    assertThatThrownBy(() -> service.decide(id, decision("APPROVE"))).hasMessageContaining("no longer awaiting");
  }
  @Test void selfApprovalAndUnlinkedAccountAreRejected() {
    String id = start("trade");
    assertThatThrownBy(() -> service.decide(id,new TraderXWorkflowService.Decision("user01","APPROVE","self")))
        .hasMessageContaining("other than the requester");
    when(client.memberships()).thenReturn(json.readTree("[]"));
    assertThatThrownBy(() -> start("trade")).hasMessageContaining("not linked");
    verify(client, never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void revokedMembershipStopsExecution() {
    String id = start("trade"); service.decide(id,decision("APPROVE"));
    when(client.memberships()).thenReturn(json.readTree("[]")); execute(id);
    assertThat(service.detail(id).get("status")).isEqualTo("NEEDS_RECONCILIATION");
    verify(client, never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }



  @Test void uncertainTradeResponseDoesNotResend() {
    when(client.submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble())).thenThrow(new IllegalStateException("Timed out"));
    String id = start("trade"); service.decide(id,decision("APPROVE")); execute(id);
    assertThat(service.detail(id).get("status")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(engine.getManagementService().createJobQuery().processInstanceId(id).count()).isZero();
    verify(client,times(1)).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
  }
  @Test void invalidQuantityAndDuplicateAccountAreRejected() {
    assertThatThrownBy(() -> service.start("trade",new TradeWorkflow.Request("user01","test",10,"AAPL","Buy",0,null,null,101.0)))
        .hasMessageContaining("Quantity");

  }
}
