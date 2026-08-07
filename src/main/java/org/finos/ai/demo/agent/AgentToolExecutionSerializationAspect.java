package org.finos.ai.demo.agent;

import java.util.Set;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.finos.fluxnova.bpm.client.task.ExternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Serializes external-task handling for tools inside an agent ad-hoc subprocess.
 *
 * <p>When the agent dispatches multiple tools in parallel, completing their external tasks in
 * separate engine commands races on the shared ad-hoc scope execution and causes a persistence
 * error. Embedded workers run in this JVM, so per-process-instance synchronization is enough for
 * this demo.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentToolExecutionSerializationAspect {

  private static final Logger LOG = LoggerFactory.getLogger(AgentToolExecutionSerializationAspect.class);

  /** Topics for service tasks inside {@code AgentTask_CreditRisk}. */
  private static final Set<String> AGENT_TOOL_TOPICS = Set.of(
      "check-credit-score",
      "verify-employment");

  @Around("execution(* org.finos.fluxnova.bpm.client.task.ExternalTaskHandler.execute(..))")
  public void serializeConcurrentAgentToolExecutions(ProceedingJoinPoint joinPoint) throws Throwable {
    ExternalTask externalTask = (ExternalTask) joinPoint.getArgs()[0];
    if (!AGENT_TOOL_TOPICS.contains(externalTask.getTopicName())) {
      joinPoint.proceed();
      return;
    }

    String lockKey = "agent-tool:" + externalTask.getProcessInstanceId();
    synchronized (lockKey.intern()) {
      LOG.debug(
          "Handling agent tool task {} ({}) for process instance {}",
          externalTask.getId(), externalTask.getTopicName(), externalTask.getProcessInstanceId());
      joinPoint.proceed();
    }
  }

}
