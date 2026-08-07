package org.finos.ai.demo.tokenomics;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo/tokenomics")
public class TokenomicsController {

  private final TokenomicsService tokenomicsService;
  private final TokenomicsInsightService insightService;

  public TokenomicsController(
      TokenomicsService tokenomicsService, TokenomicsInsightService insightService) {
    this.tokenomicsService = tokenomicsService;
    this.insightService = insightService;
  }

  @GetMapping("/summary")
  public TokenomicsSummaryResponse summary(
      @RequestParam(name = "useCase", required = false) String useCase,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to) {
    return tokenomicsService.summary(useCase, from, to);
  }

  @GetMapping("/breakdown")
  public TokenomicsBreakdownResponse breakdown(
      @RequestParam(name = "useCase", required = false) String useCase,
      @RequestParam(name = "from", required = false) Instant from,
      @RequestParam(name = "to", required = false) Instant to,
      @RequestParam(name = "groupBy", required = false, defaultValue = "providerModel") String groupBy) {
    return tokenomicsService.breakdown(useCase, from, to, groupBy);
  }

  @GetMapping("/rates")
  public TokenomicsRatesResponse rates() {
    return tokenomicsService.rates();
  }

  @GetMapping("/budgets")
  public TokenomicsBudgetsResponse budgets(
      @RequestParam(name = "useCase", required = false) String useCase) {
    return tokenomicsService.budgets(useCase);
  }

  @PatchMapping("/budgets")
  public TokenomicsBudgetsResponse updateBudget(@RequestBody TokenomicsBudgetUpdate update) {
    return tokenomicsService.updateBudget(update);
  }

  @GetMapping("/insights")
  public TokenomicsInsightsResponse insights(
      @RequestParam(name = "useCase", required = false) String useCase) {
    return insightService.insights(useCase);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public java.util.Map<String, String> badRequest(IllegalArgumentException exception) {
    return java.util.Map.of(
        "message", exception.getMessage() == null ? "bad request" : exception.getMessage());
  }
}
