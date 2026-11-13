package org.finos.ai.demo.traderx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.ObjectMapper;

class TraderXRebalanceAiTest {
  final ObjectMapper json = new ObjectMapper();
  final TraderXClient client = mock(TraderXClient.class);
  final RebalancePlanning planning = new RebalancePlanning(client,json);
  final TraderXRebalanceAi ai = new TraderXRebalanceAi(planning,null,null,json,"test-model");
  final TraderXRebalanceAi.Request request = new TraderXRebalanceAi.Request("user01",10);
  @BeforeEach void setup() {
    when(client.person("user01")).thenReturn(json.readTree("{\"fullName\":\"PRIVATE PERSON\"}"));
    when(client.account(10)).thenReturn(json.readTree("{\"displayName\":\"PRIVATE ACCOUNT\"}"));
    when(client.memberships()).thenReturn(json.readTree("[{\"accountId\":10,\"username\":\"user01\"}]"));
    when(client.positions(10)).thenReturn(json.readTree("[{\"security\":\"AAPL\",\"quantity\":10}]"));
    when(client.orders(10)).thenReturn(json.readTree("[]"));
    when(client.securities()).thenReturn(json.readTree("[{\"ticker\":\"AAPL\",\"companyName\":\"Apple\"},{\"ticker\":\"MSFT\",\"companyName\":\"Microsoft\"}]"));
    when(client.security(anyString())).thenReturn(json.readTree("{}"));
    when(client.quote(anyString())).thenReturn(json.readTree("{\"price\":100,\"asOf\":\"2026-11-05T12:00:00Z\"}"));
  }
  TraderXRebalanceAi.Strategy strategy(double weight) {
    return new TraderXRebalanceAi.Strategy("Option " + weight,"Explanation","Tradeoffs",
        List.of(new RebalancePlanning.Target("AAPL",weight),new RebalancePlanning.Target("MSFT",100-weight)));
  }
  @Test void snapshotDoesNotExposeIdentitiesAndValidatedSuggestionsNeverWrite() {
    var snapshot = planning.aiSnapshot("user01",10);
    assertThat(json.writeValueAsString(snapshot)).doesNotContain("PRIVATE","user01","accountId");
    var result=ai.validate(request,new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(60),strategy(90))),snapshot);
    assertThat((List<?>)result.get("strategies")).hasSize(3);
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
    verify(client,never()).cancelOrder(anyString());
  }
  @Test void rejectsInventedSecuritiesDuplicateStrategiesAndBadTotals() {
    var snapshot=planning.aiSnapshot("user01",10);
    var invalid=new TraderXRebalanceAi.Strategy("Bad","Reason","Risk",List.of(new RebalancePlanning.Target("FAKE",100.0)));
    assertThatThrownBy(() -> ai.validate(request,new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(60),invalid)),snapshot)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> ai.validate(request,new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(50),strategy(90))),snapshot)).isInstanceOf(IllegalStateException.class);
    var badTotal=new TraderXRebalanceAi.Strategy("Bad","Reason","Risk",List.of(new RebalancePlanning.Target("AAPL",80.0)));
    assertThatThrownBy(() -> ai.validate(request,new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(60),badTotal)),snapshot)).isInstanceOf(IllegalStateException.class);
  }
  @Test void membershipAndOpenOrdersPreventModelEgress() {
    assertThatThrownBy(() -> ai.suggest(new TraderXRebalanceAi.Request("other",10))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    when(client.orders(10)).thenReturn(json.readTree("[{\"status\":\"NEW\",\"remainingQuantity\":1}]"));
    assertThatThrownBy(() -> ai.suggest(request)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }
  @Test void rejectsIncompleteResponseAndChangedHoldings() {
    var snapshot=planning.aiSnapshot("user01",10);
    assertThatThrownBy(() -> ai.validate(request,null,snapshot)).isInstanceOf(IllegalStateException.class);
    when(client.positions(10)).thenReturn(json.readTree("[{\"security\":\"AAPL\",\"quantity\":-10}]"));
    assertThatThrownBy(() -> ai.validate(request,new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(60),strategy(90))),snapshot)).isInstanceOf(IllegalStateException.class);
  }
  @Test void modelResponseBecomesThreeValidatedReadOnlyBaskets() {
    var providers=mock(org.finos.fluxnova.bpm.engine.ai.agent.llm.provider.AgentProviderRegistry.class);
    var model=mock(org.springframework.ai.chat.model.ChatModel.class, CALLS_REAL_METHODS);
    var metrics=mock(org.finos.ai.demo.observability.LlmTokenUsageMetrics.class);
    when(providers.get("openai")).thenReturn(model);
    when(model.getDefaultOptions()).thenReturn(org.springframework.ai.chat.prompt.ChatOptions.builder().build());
    var generated=new TraderXRebalanceAi.Suggestions(List.of(strategy(50),strategy(60),strategy(90)));
    when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
        new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
            new org.springframework.ai.chat.messages.AssistantMessage(json.writeValueAsString(generated))))));
    var service=new TraderXRebalanceAi(planning,providers,metrics,json,"test-model");
    assertThat((List<?>)service.suggest(request).get("strategies")).hasSize(3);
    var prompt=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);
    verify(model).call(prompt.capture());
    assertThat(prompt.getValue().getContents()).contains("AAPL","quantity").doesNotContain("PRIVATE","user01","accountId");
    verify(client,never()).submitTrade(anyInt(),anyString(),anyString(),anyInt(),anyDouble());
    when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenThrow(new IllegalStateException("429: You have no credits remaining"));
    assertThatThrownBy(() -> service.suggest(request)).hasMessageContaining("credits are exhausted");
  }

}
