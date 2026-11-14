# Portfolio rebalance

Requires the [TraderX foundation](traderx.md), not any other workflow contribution. Choose the configured TraderX environment before starting. Open the catalog card **Portfolio rebalance**. Explore AI suggestions or manual targets, then approve a precise order basket.

A different directory person approves or rejects the frozen request. Directory selectors are demo identities, not authentication. Rejection makes no external writes. Ambiguous or partial writes stop for operations reconciliation; do not blindly retry. No account opening workflow is required to use existing accounts.

Portfolio targets are long-only, whole-share stocks, excluding cash. AI suggestions are optional and never execute trades. Targets and orders remain editable. Budget validation uses entered limits, not drifting quotes or buying power. Large instructions split into <=10,000-share orders, capped at 500 orders per review. Each receipt commits before the next action. Sells submit first but buys do not wait for sell fills. The matcher/downstream service may reprice. Model egress includes holdings, quotes and available securities, not account/person identities.
