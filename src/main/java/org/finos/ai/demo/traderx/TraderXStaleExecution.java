package org.finos.ai.demo.traderx;
import java.util.*;
import org.springframework.stereotype.Component;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import tools.jackson.databind.*;
@Component("traderXStaleExecution")
public class TraderXStaleExecution extends TraderXBatchExecution {
  public TraderXStaleExecution(TraderXClient client,ObjectMapper json) { super(client,json); }
  protected String itemsKey() { return "orders"; }
  protected String completedStatus() { return "REVIEWED"; }
  protected void executeItem(DelegateExecution e,JsonNode item,JsonNode plan,Map<String,Object> receipt,Attempt attempt) {
    int accountId=plan.path("accountId").asInt();
    int index=((Number)e.getVariable("batchIndex")).intValue();
    int size=((Number)e.getVariable("batchSize")).intValue();

        JsonNode snapshot = item.path("order");
        String orderId = snapshot.path("orderId").asText();
        receipt.put("orderId",orderId);
        JsonNode current = client.order(orderId);
        if (current.path("accountId").asInt() != accountId) throw new IllegalStateException("Order account no longer matches approved request");
        receipt.put("observedBefore",current);
        if (item.path("action").asText().equals("KEEP")) {
          receipt.put("outcome","KEPT"); receipt.put("response",current);
        } else if (!TraderXPlanningSupport.open(current)) {
          receipt.put("outcome","SKIPPED_CLOSED"); receipt.put("response",current);
        } else {
          attempt.step = "Cancel order " + orderId;
          attempt.writeAttempted = true;
          JsonNode response = client.cancelOrder(orderId);
          receipt.put("response",response);
          String state = response.path("status").asText();
          if (Set.of("CANCELED","CANCELLED").contains(state)) receipt.put("outcome","CANCELED");
          else if (!TraderXPlanningSupport.open(response)) receipt.put("outcome","SKIPPED_CLOSED");
          else throw new IllegalStateException("Cancellation returned a still-open order; inspect TraderX");
        }

  }
}
