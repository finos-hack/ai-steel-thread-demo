package org.finos.ai.demo.tokenomics;

import java.util.List;

public record TokenomicsBudgetsResponse(String currency, List<TokenomicsBudgetView> budgets) {}
