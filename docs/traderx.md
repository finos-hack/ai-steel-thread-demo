# TraderX integration foundation

No business workflow is deployed by this foundation. Workflow contributions register Spring `TraderXWorkflowDefinition` beans, their own BPMN and execution delegates, and a frontend `traderx/<name>/workflow.tsx` default descriptor. The catalog discovers installed descriptors; no workflow depends on another workflow.

## Choose the environment explicitly

For local state 009, find the ingress HOST port using its Docker Compose port mapping or `docker compose port <ingress-service> 80`. Set `TRADERX_BASE_URL=http://host.docker.internal:<host-port>` when this demo backend runs in Docker, and `TRADERX_PUBLIC_URL=http://localhost:<host-port>` for browser links. For a host-run backend use localhost for both. The example 18080 is a placeholder, not a claim about the state-009 default. On Linux, configure host.docker.internal with Docker's host-gateway mapping or use a reachable host address.

For the public environment explicitly set both values to `https://demo-advanced.traderx.finos.org`. There is no automatic failover: otherwise a failed local connection could send writes to the shared public demo. Never supply credentials in these URLs. The browser receives only the public URL, not the internal API URL.

Compatibility is based on routes, not state labels: accounts `/account-service/account/`, memberships `/account-service/accountuser/`, people `/people-service/People/GetMatchingPeople` and `GetPerson`, stocks `/reference-data/stocks`, quotes `/price-publisher/prices/{ticker}`. Trading/batch contributions additionally require the order matcher contract; account opening requires account and owner-link writes. Confirm the chosen generated state's contracts before enabling writes. The public read routes have been checked; public write behavior has not been tested as part of this contribution.

All calls go through fixed server-side routes; no arbitrary URL proxy or browser CORS requirement. Directory selection is a demo identity, not authentication. A different reviewer must approve before asynchronous external writes. Ambiguous writes require reconciliation, not blind retries. Persistent engine data uses a named volume. Large JSON plans and receipts use binary variables rather than VARCHAR(4000). Tests stub external APIs; they do not mutate the public demo.
