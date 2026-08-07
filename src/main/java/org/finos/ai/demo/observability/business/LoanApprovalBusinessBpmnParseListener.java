package org.finos.ai.demo.observability.business;

import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.core.model.CoreModelElement;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;

class LoanApprovalBusinessBpmnParseListener extends AbstractBpmnParseListener {

  private final ExecutionListener processExecutionListener;

  LoanApprovalBusinessBpmnParseListener(ExecutionListener processExecutionListener) {
    this.processExecutionListener = processExecutionListener;
  }

  @Override
  public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
    addExecutionListener(processDefinition, processExecutionListener, ExecutionListener.EVENTNAME_START);
    addExecutionListener(processDefinition, processExecutionListener, ExecutionListener.EVENTNAME_END);
  }

  private void addExecutionListener(
      CoreModelElement element, ExecutionListener listener, String eventName) {
    element.addBuiltInListener(eventName, listener);
  }

}
