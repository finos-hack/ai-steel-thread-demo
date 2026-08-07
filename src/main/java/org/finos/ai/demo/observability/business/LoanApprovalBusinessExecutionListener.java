package org.finos.ai.demo.observability.business;

import java.util.Map;

import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.springframework.beans.factory.ObjectProvider;

class LoanApprovalBusinessExecutionListener implements ExecutionListener {

  private final LoanApprovalBusinessMetrics metrics;
  private final ObjectProvider<RuntimeService> runtimeService;
  private final boolean processScope;

  LoanApprovalBusinessExecutionListener(
      LoanApprovalBusinessMetrics metrics,
      ObjectProvider<RuntimeService> runtimeService,
      boolean processScope) {
    this.metrics = metrics;
    this.runtimeService = runtimeService;
    this.processScope = processScope;
  }

  @Override
  public void notify(DelegateExecution execution) {
    if (!processScope) {
      return;
    }
    String eventName = execution.getEventName();
    if (ExecutionListener.EVENTNAME_START.equals(eventName)) {
      metrics.recordProcessStarted(execution.getProcessDefinitionId());
      return;
    }
    if (ExecutionListener.EVENTNAME_END.equals(eventName)) {
      Map<String, Object> variables =
          runtimeService.getObject().getVariables(execution.getProcessInstanceId());
      metrics.recordProcessCompleted(execution.getProcessDefinitionId(), variables);
    }
  }

}
