package org.finos.ai.demo.traderx;
import static org.finos.ai.demo.traderx.TraderXWorkflowService.*;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
@Component
public class RebalancePlanning extends TraderXPlanningSupport {
  public record Target(String security, Double percent) {}
  public record Leg(String security, String side, Integer quantity, Double limitPrice) {}
  public record Rebalance(String requester, String reason, Integer accountId, List<Target> targets, List<Leg> legs) {}

  public RebalancePlanning(TraderXClient client,ObjectMapper json) { super(client,json); }
  public Map<String,Object> rebalance(Rebalance r, boolean submitting) {
    if (r == null) throw bad("Rebalance request is required");
    JsonNode account = validateAccount(r.requester(), r.accountId());
    if (r.targets() == null || r.targets().isEmpty() || r.targets().size() > 20)
      throw bad("Choose 1–20 target securities with weights totaling 100%");
    Map<String,Double> targets = new TreeMap<>();
    double sum = 0;
    for (Target t : r.targets()) {
      if (t == null || t.percent() == null || !Double.isFinite(t.percent()) || t.percent() < 0 || t.percent() > 100)
        throw bad("Target weights must be between 0 and 100");
      String ticker = ticker(t.security());
      if (targets.put(ticker,t.percent()) != null) throw bad("Duplicate target security: " + ticker);
      client.security(ticker); sum += t.percent();
    }
    if (Math.abs(sum - 100) > 0.0001) throw bad("Target weights must total 100%");
    requireNoOpenOrders(client,r.accountId());
    Map<String,Integer> holdings = holdings(client.positions(r.accountId()));
    var universe = new TreeSet<>(holdings.keySet()); universe.addAll(targets.keySet());
    Map<String,Double> prices = new TreeMap<>();
    Map<String,String> quoteTimes = new TreeMap<>();
    double value = 0;
    for (String ticker : universe) {
      JsonNode q = client.quote(ticker);
      double price = q.path("price").asDouble();
      if (!Double.isFinite(price) || price <= 0) throw bad("No usable quote for " + ticker);
      prices.put(ticker,price); quoteTimes.put(ticker,q.path("asOf").asText());
      value += holdings.getOrDefault(ticker,0)*price;
    }
    if (value <= 0) throw bad("This account has no positive stock holdings to rebalance; choose a funded demo portfolio");
    List<Map<String,Object>> allocations = new ArrayList<>();
    List<Leg> proposed = new ArrayList<>();
    for (String ticker : universe) {
      int current = holdings.getOrDefault(ticker,0);
      double percent = targets.getOrDefault(ticker,0.0);
      double desired = Math.floor(value*percent/100/prices.get(ticker));
      if (desired > Integer.MAX_VALUE) throw bad("Target quantity is too large");
      int target = (int)desired;
      int delta = target - current;
      allocations.add(Map.of("security",ticker,"currentQuantity",current,"currentValue",current*prices.get(ticker),
          "currentPercent",current*prices.get(ticker)/value*100,"targetPercent",percent,"targetQuantity",target,
          "quotePrice",prices.get(ticker),"quoteAsOf",quoteTimes.get(ticker)));
      if (delta != 0) proposed.add(new Leg(ticker,delta > 0 ? "Buy" : "Sell",Math.abs(delta),prices.get(ticker)));
    }
    proposed.sort(Comparator.comparing((Leg l) -> l.side().equals("Sell") ? 0 : 1).thenComparing(Leg::security));
    List<Leg> chosen = submitting ? r.legs() : proposed;
    if (submitting && (chosen == null || chosen.isEmpty())) throw bad("Review and submit at least one proposed order");
    if (chosen == null || chosen.size() > 40) throw bad("Basket is too large");
    Set<String> seen = new HashSet<>();
    List<Leg> validated = new ArrayList<>();
    for (Leg leg : chosen) {
      if (leg == null) throw bad("Invalid basket instruction");
      String ticker = ticker(leg.security());
      if (!seen.add(ticker)) throw bad("Duplicate basket security");
      Leg generated = proposed.stream().filter(l -> l.security().equals(ticker)).findFirst().orElse(null);
      if (generated == null || !generated.side().equals(leg.side())) throw bad("Holdings or target direction changed; regenerate the proposal");
      if (leg.quantity() == null || leg.quantity() < 1)
        throw bad("Each basket instruction must contain a positive whole-share quantity");
      if (leg.side().equals("Sell") && leg.quantity() > holdings.getOrDefault(ticker,0)) throw bad("Cannot sell more shares than currently held");
      if (leg.limitPrice() == null || !Double.isFinite(leg.limitPrice()) || leg.limitPrice() <= 0 || leg.limitPrice() > 1000000)
        throw bad("Each limit price must be positive and at most 1000000");
      validated.add(new Leg(ticker,leg.side(),leg.quantity(),leg.limitPrice()));
    }
    validated.sort(Comparator.comparing((Leg l) -> l.side().equals("Sell") ? 0 : 1).thenComparing(Leg::security));
    double buyValue = 0, sellValue = 0;
    for (Leg l : validated) {
      if (l.side().equals("Buy")) buyValue += l.quantity()*l.limitPrice();
      else sellValue += l.quantity()*l.limitPrice();
    }
    // TraderX has no cash endpoint. This flow redistributes existing invested stock value only.
    if (submitting && buyValue > sellValue + 0.01) throw bad("Buy cost at entered limits exceeds sell proceeds at entered limits. Balance buy quantities or revise the limits before review; cash balances are not available");
    Map<String,Object> plan = new LinkedHashMap<>();
    plan.put("accountId",r.accountId()); plan.put("accountName",account.path("displayName").asText());
    plan.put("holdings",holdings); plan.put("allocations",allocations); plan.put("legs",validated);
    long orderCount = validated.stream().mapToLong(l -> (l.quantity().longValue()+9999)/10000).sum();
    if (submitting && orderCount > 500) throw bad("This basket requires " + orderCount + " orders; reduce quantities to at most 500 split orders per review");
    plan.put("orderCount",orderCount);
    plan.put("maxOrderQuantity",10000);
    if (submitting) {
      List<Leg> split = new ArrayList<>();
      for (Leg l : validated) {
        for (int remaining=l.quantity(); remaining>0;) {
          int quantity=Math.min(10000,remaining);
          split.add(new Leg(l.security(),l.side(),quantity,l.limitPrice()));
          remaining-=quantity;
        }
      }
      plan.put("instructions",validated);
      plan.put("legs",split);
    }
    plan.put("budgetBasis","ENTERED_LIMITS");
    plan.put("portfolioValue",value); plan.put("estimatedBuys",buyValue); plan.put("estimatedSells",sellValue);
    plan.put("roundingResidual",sellValue-buyValue); plan.put("snapshotAt",Instant.now().toString());
    return plan;
  }

  /** Only anonymous market data leaves the service for AI suggestions. */
  public Map<String,Object> aiSnapshot(String requester, Integer accountId) {
    validateAccount(requester,accountId);
    requireNoOpenOrders(client,accountId);
    Map<String,Integer> held = holdings(client.positions(accountId));
    if (held.isEmpty() || held.size() > 20) throw bad("AI suggestions require 1–20 long-only holdings");
    List<Map<String,Object>> rows = new ArrayList<>();
    double total = 0;
    for (var h : held.entrySet()) {
      JsonNode q = client.quote(h.getKey());
      double price = q.path("price").asDouble();
      if (!Double.isFinite(price) || price <= 0) throw bad("No usable quote for " + h.getKey());
      double value = h.getValue()*price; total += value;
      rows.add(Map.of("security",h.getKey(),"quantity",h.getValue(),"quotePrice",price,
          "value",value,"quoteAsOf",q.path("asOf").asText()));
    }
    List<Map<String,String>> universe = new ArrayList<>();
    for (JsonNode s : client.securities()) universe.add(Map.of("security",s.path("ticker").asText(),
        "companyName",s.path("companyName").asText()));
    if (total <= 0) throw bad("This account has no positive stock holdings");
    return Map.of("holdings",rows,"portfolioValue",total,"availableSecurities",universe,
        "snapshotAt",Instant.now().toString());
  }


}
