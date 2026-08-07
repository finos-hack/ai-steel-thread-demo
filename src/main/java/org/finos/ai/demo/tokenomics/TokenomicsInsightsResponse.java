package org.finos.ai.demo.tokenomics;

import java.util.List;

public record TokenomicsInsightsResponse(
    String currency,
    String useCase,
    List<TokenomicsInsightTip> tips,
    List<String> providers,
    List<String> models,
    String grafanaUrl) {}
