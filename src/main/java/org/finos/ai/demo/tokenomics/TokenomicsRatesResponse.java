package org.finos.ai.demo.tokenomics;

import java.util.List;

public record TokenomicsRatesResponse(String currency, List<TokenomicsRateView> rates) {}
