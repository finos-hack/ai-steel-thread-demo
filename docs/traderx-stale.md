# Stale-order review

Requires the [TraderX foundation](traderx.md), not any other workflow contribution. Choose the configured TraderX environment before starting. Open the catalog card **Stale-order review**. Review Keep or Cancel decisions for aged orders before execution.

A different directory person approves or rejects the frozen request. Directory selectors are demo identities, not authentication. Rejection makes no external writes. Ambiguous or partial writes stop for operations reconciliation; do not blindly retry. No account opening workflow is required to use existing accounts.

Scan creation age, select Keep or Cancel, and review. Cancellation rechecks current status; completed orders are skipped and fills are not reversed.
