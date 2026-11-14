package org.finos.ai.demo.traderx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import org.finos.fluxnova.bpm.engine.ProcessEngine;
import org.finos.fluxnova.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

/** Exercises the actual deployed BPMN gates with a stubbed remote TraderX boundary. */
class TraderXAccountTest {
  static ProcessEngine engine;
  static TraderXClient client = mock(TraderXClient.class);
  static ObjectMapper json = new ObjectMapper();
  TraderXWorkflowService service;

  @BeforeAll static void deploy() {
    var cfg = new StandaloneInMemProcessEngineConfiguration();
    cfg.setJdbcUrl("jdbc:h2:mem:traderx-account-test;DB_CLOSE_DELAY=0");
    cfg.setDatabaseSchemaUpdate("true");
    cfg.setHistory("full");
    cfg.setJobExecutorActivate(false);
    cfg.setBeans(Map.of("traderXAccountExecution", new TraderXAccountExecution(client)));
    engine = cfg.buildProcessEngine();
    engine.getRepositoryService().createDeployment()
        .addClasspathResource("bpmn/TraderX_AccountOpening.bpmn").deploy();
  }
  @AfterAll static void close() { if (engine != null) engine.close(); }
  @BeforeEach void setup() {
    reset(client);
    service = new TraderXWorkflowService(client, engine.getRuntimeService(), engine.getTaskService(), engine.getHistoryService(), java.util.List.of(new AccountWorkflow(client)));
    when(client.person(anyString())).thenReturn(json.readTree("{\"logonId\":\"user01\"}"));
    when(client.account(10)).thenReturn(json.readTree("{\"id\":10,\"displayName\":\"Test book\"}"));
    when(client.accounts()).thenReturn(json.readTree("[]"));
    when(client.memberships()).thenReturn(json.readTree("[{\"accountId\":10,\"username\":\"user01\"}]"));
    when(client.security("AAPL")).thenReturn(json.readTree("{\"ticker\":\"AAPL\"}"));
    when(client.quote("AAPL")).thenReturn(json.readTree("{\"price\":100,\"asOf\":\"2026-11-04T12:00:00Z\"}"));
    when(client.order("ord-test-1")).thenReturn(json.readTree("{\"orderId\":\"ord-test-1\",\"status\":\"FILLED\"}"));
  }

  AccountWorkflow.Request account() { return new AccountWorkflow.Request("user01", "Test account",null,null,null,null,"Demo account", "user01",null); }
  TraderXWorkflowService.Decision decision(String d) { return new TraderXWorkflowService.Decision("user02",d,"Reviewed in test"); }
  String start(String kind) { return (String)service.start(kind, account()).get("id"); }
  void execute(String id) {
    var job = engine.getManagementService().createJobQuery().processInstanceId(id).singleResult();
    assertThat(job).isNotNull();
    engine.getManagementService().executeJob(job.getId());
  }




  @Test void accountIsCreatedThenOwnerLinkedOnlyAfterApproval() {
    when(client.createAccount("Demo account")).thenReturn(json.readTree("{\"id\":42}"));
    when(client.linkOwner(42,"user01")).thenReturn(json.readTree("{\"accountId\":42,\"username\":\"user01\"}"));
    String id = start("account");
    verify(client,never()).createAccount(anyString());
    service.decide(id,decision("APPROVE")); execute(id);
    assertThat(service.detail(id).get("status")).isEqualTo("OPENED");
    var ordered = inOrder(client); ordered.verify(client).createAccount("Demo account"); ordered.verify(client).linkOwner(42,"user01");
  }
  @Test void rejectedAccountDoesNotCreateAnything() {
    String id = start("account"); service.decide(id,decision("REJECT"));
    assertThat(service.detail(id).get("status")).isEqualTo("REJECTED");
    verify(client,never()).createAccount(anyString()); verify(client,never()).linkOwner(anyInt(),anyString());
  }
  @Test void partialAccountFailureRetainsIdAndRequiresReconciliation() {
    when(client.createAccount("Demo account")).thenReturn(json.readTree("{\"id\":42}"));
    when(client.linkOwner(42,"user01")).thenThrow(new IllegalStateException("Timed out"));
    String id = start("account"); service.decide(id,decision("APPROVE")); execute(id);
    assertThat(service.detail(id).get("status")).isEqualTo("NEEDS_RECONCILIATION");
    assertThat(engine.getRuntimeService().getVariable(id,"createdAccountId")).isEqualTo(42);
    assertThat(engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult().getTaskDefinitionKey()).isEqualTo("Reconcile");
    assertThat(engine.getManagementService().createJobQuery().processInstanceId(id).count()).isZero();
    verify(client,times(1)).createAccount(anyString());
  }

  @Test void invalidQuantityAndDuplicateAccountAreRejected() {
    when(client.accounts()).thenReturn(json.readTree("[{\"displayName\":\"DEMO ACCOUNT\"}]"));
    assertThatThrownBy(() -> start("account")).hasMessageContaining("already exists");
  }
}
