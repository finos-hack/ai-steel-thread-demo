package org.finos.ai.demo.tokenomics;

import java.util.List;

public record TokenomicsBreakdownResponse(
    String currency,
    String useCase,
    String groupBy,
    List<TokenomicsLedger.BreakdownRow> rows) {}
