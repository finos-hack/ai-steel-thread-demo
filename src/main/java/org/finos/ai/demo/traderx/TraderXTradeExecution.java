package org.finos.ai.demo.traderx;

import java.time.Instant;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** Async BPMN step; uncertain writes stop at an operations task, never an automatic resend. */
@Component("traderXTradeExecution")
public class TraderXTradeExecution implements JavaDelegate {
  private final TraderXClient client;
  public TraderXTradeExecution(TraderXClient client) { this.client = client; }

  @Override public void execute(DelegateExecution e) {
    if (!Boolean.TRUE.equals(e.getVariable("approved")) || e.getVariable("reviewer") == null)
      throw new IllegalStateException("A recorded human approval is required before any TraderX write");
    // A manually retried job must not resubmit a previously recorded success.
    if (Boolean.TRUE.equals(e.getVariable("externalSuccess"))) return;
    e.setVariable("externalSuccess", false);
    String step = "Revalidate approved request";
    try {

        int account = ((Number)e.getVariable("accountId")).intValue();
        client.account(account);
        TraderXWorkflowService.requireMembership(client, account, str(e, "requester"));
        client.security(str(e, "security"));
        step = "Submit trade order";
        // Persist the matcher-assigned ID; the legacy trade API does not preserve ledger correlation.
        var response = client.submitTrade(account, str(e, "security"),
            str(e, "side"), ((Number)e.getVariable("quantity")).intValue(),
            ((Number)e.getVariable("limitPrice")).doubleValue());
        String orderId = response.path("orderId").asText();
        if (orderId.isBlank()) throw new IllegalStateException("TraderX returned no order ID; reconcile before resubmitting");
        e.setVariable("orderId", orderId);
        e.setVariable("externalResponse", response.toString());
        e.setVariable("status", "SUBMITTED");

      e.setVariable("externalSuccess", true);
      e.setVariable("executedAt", Instant.now().toString());
    } catch (RuntimeException ex) {
      e.setVariable("status", "NEEDS_RECONCILIATION");
      e.setVariable("failedStep", step);
      e.setVariable("executionError", ex.getMessage() == null ? "TraderX operation failed" : ex.getMessage());
      e.setVariable("failedAt", Instant.now().toString());
    }
  }
  private String str(DelegateExecution e, String name) { return (String)e.getVariable(name); }
}
