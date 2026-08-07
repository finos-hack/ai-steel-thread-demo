package org.finos.ai.demo.tokenomics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** AI tokenomics control-plane settings (estimated USD from a rate card). */
@ConfigurationProperties(prefix = "fluxnova.tokenomics")
public class TokenomicsProperties {

  private boolean enabled = true;

  private String currency = "USD";

  /** processDefinitionKey → use-case catalog id */
  private Map<String, String> useCaseMap = new LinkedHashMap<>(Map.of("LoanApprovalProcess_AI", "loan-approval"));

  private List<Rate> rates = new ArrayList<>(defaultRates());

  private List<Budget> budgets = new ArrayList<>(defaultBudgets());

  /**
   * Grafana dashboard path for estimated LLM cost (appended to mi-4 grafana base URL).
   */
  private String grafanaDashboardPath =
      "/d/fluxnova-ai-observability/fluxnova-ai-observability";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getCurrency() {
    return currency;
  }

  public void setCurrency(String currency) {
    this.currency = currency;
  }

  public Map<String, String> getUseCaseMap() {
    return useCaseMap;
  }

  public void setUseCaseMap(Map<String, String> useCaseMap) {
    this.useCaseMap = useCaseMap == null ? new LinkedHashMap<>() : useCaseMap;
  }

  public List<Rate> getRates() {
    return rates;
  }

  public void setRates(List<Rate> rates) {
    this.rates = rates == null ? new ArrayList<>() : rates;
  }

  public List<Budget> getBudgets() {
    return budgets;
  }

  public void setBudgets(List<Budget> budgets) {
    this.budgets = budgets == null ? new ArrayList<>() : budgets;
  }

  public String getGrafanaDashboardPath() {
    return grafanaDashboardPath;
  }

  public void setGrafanaDashboardPath(String grafanaDashboardPath) {
    this.grafanaDashboardPath = grafanaDashboardPath;
  }

  public String useCaseForWorkflow(String workflow) {
    if (workflow == null || workflow.isBlank()) {
      return "unknown";
    }
    return useCaseMap.getOrDefault(workflow, workflow);
  }

  private static List<Rate> defaultRates() {
    List<Rate> defaults = new ArrayList<>();
    defaults.add(rate("openai", "gpt-5.4", 2.50, 15.00, 0.25, 0.0));
    defaults.add(rate("openai", "gpt-5.6-luna", 0.20, 1.20, 0.02, 0.25));
    defaults.add(rate("anthropic", "claude-sonnet-5", 3.00, 15.00, 0.20, 2.50));
    return defaults;
  }

  private static List<Budget> defaultBudgets() {
    List<Budget> defaults = new ArrayList<>();
    Budget budget = new Budget();
    budget.setUseCase("loan-approval");
    budget.setPeriod("monthly");
    budget.setLimitUsd(1.0);
    budget.setWarnAt(0.8);
    budget.setHardLimit(false);
    defaults.add(budget);
    return defaults;
  }

  private static Rate rate(
      String provider,
      String model,
      double promptPer1m,
      double completionPer1m,
      double cacheInputPer1m,
      double cacheWritePer1m) {
    Rate rate = new Rate();
    rate.setProvider(provider);
    rate.setModel(model);
    rate.setPromptPer1m(promptPer1m);
    rate.setCompletionPer1m(completionPer1m);
    rate.setCacheInputPer1m(cacheInputPer1m);
    rate.setCacheWritePer1m(cacheWritePer1m);
    return rate;
  }

  public static class Budget {
    private String useCase = "loan-approval";
    /** Supported: monthly */
    private String period = "monthly";
    private double limitUsd = 1.0;
    /** Fraction of limit at which status becomes warn (0–1). */
    private double warnAt = 0.8;
    /** When true, LLM calls fail once spent ≥ limit. */
    private boolean hardLimit = false;

    public String getUseCase() {
      return useCase;
    }

    public void setUseCase(String useCase) {
      this.useCase = useCase;
    }

    public String getPeriod() {
      return period;
    }

    public void setPeriod(String period) {
      this.period = period;
    }

    public double getLimitUsd() {
      return limitUsd;
    }

    public void setLimitUsd(double limitUsd) {
      this.limitUsd = limitUsd;
    }

    public double getWarnAt() {
      return warnAt;
    }

    public void setWarnAt(double warnAt) {
      this.warnAt = warnAt;
    }

    public boolean isHardLimit() {
      return hardLimit;
    }

    public void setHardLimit(boolean hardLimit) {
      this.hardLimit = hardLimit;
    }
  }

  public static class Rate {
    private String provider = "openai";
    private String model = "*";
    /** USD per 1M prompt tokens */
    private double promptPer1m = 1.0;
    /** USD per 1M completion tokens */
    private double completionPer1m = 3.0;
    /** USD per 1M cache-read (cache input) tokens */
    private double cacheInputPer1m = 0.0;
    /** USD per 1M cache-write tokens */
    private double cacheWritePer1m = 0.0;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public double getPromptPer1m() {
      return promptPer1m;
    }

    public void setPromptPer1m(double promptPer1m) {
      this.promptPer1m = promptPer1m;
    }

    public double getCompletionPer1m() {
      return completionPer1m;
    }

    public void setCompletionPer1m(double completionPer1m) {
      this.completionPer1m = completionPer1m;
    }

    public double getCacheInputPer1m() {
      return cacheInputPer1m;
    }

    public void setCacheInputPer1m(double cacheInputPer1m) {
      this.cacheInputPer1m = cacheInputPer1m;
    }

    public double getCacheWritePer1m() {
      return cacheWritePer1m;
    }

    public void setCacheWritePer1m(double cacheWritePer1m) {
      this.cacheWritePer1m = cacheWritePer1m;
    }
  }
}
