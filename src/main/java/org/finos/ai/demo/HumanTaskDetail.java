package org.finos.ai.demo;

import java.util.Date;
import java.util.Map;

/** Task detail with form schema and process context for filling the form. */
public record HumanTaskDetail(
    String taskId,
    String name,
    String taskDefinitionKey,
    String processInstanceId,
    String businessKey,
    Date created,
    String assignee,
    boolean claimed,
    HumanTaskFormSchema form,
    Map<String, Object> context,
    HumanTaskAgentConversation agentConversation) {}
