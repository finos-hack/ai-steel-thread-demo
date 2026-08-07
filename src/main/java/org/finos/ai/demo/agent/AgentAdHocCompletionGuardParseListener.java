package org.finos.ai.demo.agent;

import org.finos.fluxnova.bpm.engine.ActivityTypes;
import org.finos.fluxnova.bpm.engine.delegate.DelegateExecution;
import org.finos.fluxnova.bpm.engine.delegate.VariableScope;
import org.finos.fluxnova.bpm.engine.impl.Condition;
import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.finos.fluxnova.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ActivityImpl;
import org.finos.fluxnova.bpm.engine.impl.pvm.process.ScopeImpl;
import org.finos.fluxnova.bpm.engine.impl.util.xml.Element;
import org.finos.fluxnova.bpm.engine.shared.agent.AgentModelConstants;

/**
 * Prevents agentic ad-hoc subprocesses from auto-completing when a synchronous tool finishes.
 *
 * <p>Without {@code <completionCondition>${false}</completionCondition>}, Fluxnova completes an
 * ad-hoc scope as soon as its last active child ends. That deletes {@code AgentTask_CreditRisk}
 * while agent local variables still reference it ({@code ACT_FK_VAR_EXE}).
 */
class AgentAdHocCompletionGuardParseListener extends AbstractBpmnParseListener {

  private static final Condition NEVER_COMPLETE =
      new Condition() {
        @Override
        public boolean evaluate(DelegateExecution execution) {
          return false;
        }

        @Override
        public boolean evaluate(VariableScope scope, DelegateExecution execution) {
          return false;
        }

        @Override
        public boolean tryEvaluate(VariableScope scope, DelegateExecution execution) {
          return false;
        }
      };

  @Override
  public void parseSubProcess(Element element, ScopeImpl scope, ActivityImpl activity) {
    if (!ActivityTypes.SUB_PROCESS_AD_HOC.equals(element.getTagName())) {
      return;
    }
    Element ext = element.element("extensionElements");
    if (ext == null || ext.elementNS(AgentModelConstants.AGENT_NS, "config") == null) {
      return;
    }
    activity.setProperty(BpmnParse.PROPERTYNAME_AD_HOC_COMPLETION_CONDITION, NEVER_COMPLETE);
    activity.setProperty(BpmnParse.PROPERTYNAME_AD_HOC_COMPLETION_CONDITION_TEXT, "${false}");
  }
}
