package org.finos.ai.demo;

import java.util.List;

public record HumanTaskList(int totalCount, List<HumanTaskSummary> tasks) {}
