package org.finos.ai.demo.traderx;

import java.time.Instant;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/** Async BPMN step; uncertain writes stop at an operations task, never an automatic resend. */
@Component("traderXAccountExecution")
public class TraderXAccountExecution implements JavaDelegate {
  private final TraderXClient client;
  public TraderXAccountExecution(TraderXClient client) { this.client = client; }

  @Override public void execute(DelegateExecution e) {
    if (!Boolean.TRUE.equals(e.getVariable("approved")) || e.getVariable("reviewer") == null)
      throw new IllegalStateException("A recorded human approval is required before any TraderX write");
    // A manually retried job must not resubmit a previously recorded success.
    if (Boolean.TRUE.equals(e.getVariable("externalSuccess"))) return;
    e.setVariable("externalSuccess", false);
    String step = "Revalidate approved request";
    try {

        client.person(str(e, "owner"));
        TraderXWorkflowService.requireUniqueName(client, str(e, "displayName"));
        step = "Create account";
        var account = client.createAccount(str(e, "displayName"));
        int id = account.path("id").asInt();
        if (id <= 0) throw new IllegalStateException("TraderX did not return a new account ID");
        e.setVariable("createdAccountId", id);
        e.setVariable("accountResponse", account.toString());
        step = "Link account owner";
        var owner = client.linkOwner(id, str(e, "owner"));
        e.setVariable("externalResponse", owner.toString());
        e.setVariable("status", "OPENED");

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
