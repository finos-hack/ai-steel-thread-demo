package org.finos.ai.demo;

import java.util.Date;

/** Open BPMN user task summary for the Launch UI inbox. */
public record HumanTaskSummary(
    String taskId,
    String name,
    String taskDefinitionKey,
    String processInstanceId,
    String businessKey,
    Date created,
    String assignee,
    boolean claimed,
    String formSchemaId) {}
