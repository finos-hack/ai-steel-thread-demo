package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.stereotype.Component;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import tools.jackson.databind.*;
@Component("traderXRebalanceExecution")
public class TraderXRebalanceExecution extends TraderXBatchExecution {
  public TraderXRebalanceExecution(TraderXClient client,ObjectMapper json) { super(client,json); }
  protected String itemsKey() { return "legs"; }
  protected String completedStatus() { return "BASKET_SUBMITTED"; }
  protected void executeItem(DelegateExecution e,JsonNode item,JsonNode plan,Map<String,Object> receipt,Attempt attempt) {
    int accountId=plan.path("accountId").asInt();
    int index=((Number)e.getVariable("batchIndex")).intValue();
    int size=((Number)e.getVariable("batchSize")).intValue();

        if (index == 0) {
          TraderXPlanningSupport.requireNoOpenOrders(client,accountId);
          var current = json.valueToTree(TraderXPlanningSupport.holdings(client.positions(accountId)));
          if (!current.equals(plan.path("holdings"))) throw new IllegalStateException("Positions changed after review; create a fresh proposal");
        }
        client.security(item.path("security").asText());
        attempt.step = "Submit basket order " + (index+1) + " of " + size;
        attempt.writeAttempted = true;
        JsonNode response = client.submitTrade(accountId,item.path("security").asText(),item.path("side").asText(),
            item.path("quantity").asInt(),item.path("limitPrice").asDouble());
        String orderId = response.path("orderId").asText();
        if (orderId.isBlank()) throw new IllegalStateException("No order ID returned; reconcile before resubmitting");
        receipt.put("orderId",orderId); receipt.put("outcome","SUBMITTED"); receipt.put("response",response);

  }
}
