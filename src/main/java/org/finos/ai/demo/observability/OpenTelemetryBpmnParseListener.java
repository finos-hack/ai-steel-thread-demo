package org.finos.ai.demo.observability;

import org.finos.fluxnova.bpm.engine.delegate.DelegateListener;
import org.finos.fluxnova.bpm.engine.delegate.ExecutionListener;
import org.finos.fluxnova.bpm.engine.delegate.TaskListener;
import org.finos.fluxnova.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.core.model.CoreModelElement;
import org.finos.fluxnova.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ActivityImpl;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ScopeImpl;
import org.finos.fluxnova.bpm.engine.impl.task.TaskDefinition;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;

class OpenTelemetryBpmnParseListener extends AbstractBpmnParseListener {

  private final ExecutionListener processExecutionListener;
  private final ExecutionListener activityExecutionListener;
  private final TaskListener taskListener;

  OpenTelemetryBpmnParseListener(
      ExecutionListener processExecutionListener,
      ExecutionListener activityExecutionListener,
      TaskListener taskListener) {
    this.processExecutionListener = processExecutionListener;
    this.activityExecutionListener = activityExecutionListener;
    this.taskListener = taskListener;
  }

  @Override
  public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
    addExecutionListener(processDefinition, processExecutionListener, ExecutionListener.EVENTNAME_START);
    addExecutionListener(processDefinition, processExecutionListener, ExecutionListener.EVENTNAME_END);
  }

  @Override
  public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
    addTaskListener(taskDefinition(activity));
  }

  @Override
  public void parseStartEvent(Element startEventElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseEndEvent(Element endEventElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseServiceTask(Element serviceTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseScriptTask(Element scriptTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseBusinessRuleTask(Element businessRuleTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseManualTask(Element manualTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseReceiveTask(Element receiveTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseSendTask(Element sendTaskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseTask(Element taskElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseCallActivity(Element callActivityElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseSubProcess(Element subProcessElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseTransaction(Element transactionElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseExclusiveGateway(Element exclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseInclusiveGateway(Element inclusiveGwElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseParallelGateway(Element parallelGwElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseEventBasedGateway(Element eventBasedGwElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseIntermediateCatchEvent(Element intermediateEventElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseIntermediateThrowEvent(Element intermediateEventElement, ScopeImpl scope, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  @Override
  public void parseBoundaryEvent(Element boundaryEventElement, ScopeImpl scopeElement, ActivityImpl activity) {
    addExecutionListener(activity);
  }

  private void addExecutionListener(ActivityImpl activity) {
    addExecutionListener(activity, activityExecutionListener, ExecutionListener.EVENTNAME_START);
    addExecutionListener(activity, activityExecutionListener, ExecutionListener.EVENTNAME_END);
  }

  private void addExecutionListener(
      CoreModelElement element,
      ExecutionListener listener,
      String eventName) {
    element.addBuiltInListener(eventName, listener);
  }

  private void addTaskListener(TaskDefinition taskDefinition) {
    taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_CREATE, taskListener);
    taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_COMPLETE, taskListener);
    taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_DELETE, taskListener);
  }

  private TaskDefinition taskDefinition(ActivityImpl activity) {
    UserTaskActivityBehavior activityBehavior = (UserTaskActivityBehavior) activity.getActivityBehavior();
    return activityBehavior.getTaskDefinition();
  }

}
