# Generation prompt

The full specification this project was generated from. It is the original prompt with the
documentation/Postman/CI deliverables folded in (see the **DOCS, POSTMAN & CI** section and
the added `DELIVERABLES` bullets).

---

Create a fresh Quarkus project named "bian-fraud-detection" in the current directory.

## ENVIRONMENT
- Java: OpenJDK 25.0.1 LTS. Set maven.compiler.release=25.
- Quarkus: 3.33.2 (LTS). Use the io.quarkus.platform BOM.
- Build: Maven (not Gradle). Group id: nz.co.ksktech. Artifact: bian-fraud-detection.
- Package root: nz.co.ksktech.fraud

## EXTENSIONS (add via quarkus-bom)
- quarkus-rest, quarkus-rest-jackson
- quarkus-hibernate-orm-panache, quarkus-jdbc-postgresql
- quarkus-langchain4j-ai-gemini AND quarkus-langchain4j-ollama (BOTH on the classpath).
  Select the active provider at build time via quarkus.langchain4j.chat-model.provider
  (= ${LLM_PROVIDER}, default ai-gemini). Gemini key from GEMINI_API_KEY.
- quarkus-smallrye-openapi, quarkus-smallrye-health
- quarkus-flyway
- quarkus-junit5, rest-assured (test)

## BIAN STRUCTURE
This is a Fraud Detection Service Domain. Follow the BIAN verb vocabulary for endpoints.
Build a JAX-RS resource FraudDetectionResource with:
  - POST /fraud-detection/initiate            -> create FraudEvaluation control record, return ref
  - POST /fraud-detection/{ref}/evaluate      -> run AI triage, set riskScore + reasoning + decision
  - GET  /fraud-detection/{ref}/retrieve      -> return the control record
  - PUT  /fraud-detection/{ref}/update        -> analyst disposition override
  - POST /fraud-detection/{ref}/notify        -> emit + log an alert event

## DOMAIN MODEL (JPA / Panache entity) FraudEvaluation
  - referenceId (UUID, the BIAN control record id)
  - transactionId, amount, type, nameOrig, nameDest, oldbalanceOrg, newbalanceOrig
  - riskScore (0-100), decision (CLEAR | REVIEW | BLOCK)
  - aiReasoning (text), caseNarrative (text)
  - status (INITIATED | EVALUATED | DISPOSED)
  - createdAt, updatedAt
Use Flyway migration V1__init.sql to create the table.

## DATA LOADING
- Add a DataLoader (@Startup or a CLI command) that reads ./data/*.csv (PaySim format:
  step,type,amount,nameOrig,oldbalanceOrg,newbalanceOrig,nameDest,oldbalanceDest,
  newbalanceDest,isFraud,isFlaggedFraud) and loads the FIRST 100000 rows into a
  staging table fraud_txn_sample. Make the limit configurable via
  app.dataset.max-rows (default 100000) and the path via app.dataset.path (default ./data).
- Skip loading if the staging table is already populated.

## AI LAYER (LangChain4j)
- Define a @RegisterAiService FraudTriageAgent with a method:
    FraudVerdict triage(String transactionJson)
  System prompt: act as a fraud analyst. Given a transaction, return a JSON object with
  riskScore (0-100), decision (CLEAR/REVIEW/BLOCK), reasoning (short), and a caseNarrative
  (2-3 sentences suitable for an analyst case file). Instruct it to return ONLY JSON.
- Parse the JSON safely into a FraudVerdict record. On parse failure, default to REVIEW
  with the raw text stored in reasoning.
- The /evaluate endpoint calls this agent, persists the result on the control record.

  > Implementation note: the agent method returns the raw model `String` and a
  > `FraudTriageService` does the defensive parse into `FraudVerdict` (extract the first
  > `{ … }`, clamp riskScore, validate decision, REVIEW fallback). This keeps the
  > "parse safely" requirement under our control and makes the agent trivially mockable.

## REQUEST/RESPONSE LOGGING (match the fundlens project at /Users/senthilkumar/google-ai/fundlens)
- Add a JAX-RS @Provider ContainerRequestFilter + ContainerResponseFilter that logs:
  method, path, a generated correlationId (UUID, also returned as X-Correlation-Id header
  and stored in MDC), request body, response status, response body, and duration in ms.
- Use SLF4J/JBoss logging. Truncate logged bodies to a configurable max length
  (app.logging.max-body=2000). Redact nothing for now (synthetic data) but leave a
  clearly-marked hook for PII redaction.
- Mirror fundlens's log format (`>>>` / `<<<`) and the correlationId/MDC pattern. If a logging
  filter or interceptor already exists in fundlens, replicate its structure and naming here.
- Secret hygiene: the langchain4j Gemini client's built-in HTTP request logger prints the API key
  in the request URL (`?key=...`), and it does so via `Logger.infof("...url: %s...", url)` — so the
  key sits in a log-record PARAMETER, not the message string. Support wants the full HTTP view
  (url, headers, JSON body) with the key redacted, so: keep `log-requests`/`log-responses`
  enabled (gated by `LOG_LLM`) and redact the key with a `SecretRedactingLogFilter` that masks
  `key=...` / `AIza...` / `AQ....` in BOTH the message and every String parameter. Attach the
  filter programmatically at startup (`LogRedactionInstaller`) — the declarative
  `quarkus.log.console.filter` / `@LoggingFilter` did NOT reliably attach. A wiring test builds the
  exact `ExtLogRecord` (PRINTF, key-in-parameter) the client emits and asserts the FORMATTED output
  is redacted, so this can't silently regress.

## CONFIG (application.properties)
- quarkus.langchain4j.chat-model.provider=${LLM_PROVIDER:ai-gemini} (BUILD-TIME; ai-gemini|ollama)
- quarkus.langchain4j.ai.gemini.api-key=${GEMINI_API_KEY:} (note: prefix is `ai.gemini`, dotted)
- quarkus.langchain4j.ai.gemini.chat-model.model-id=${GEMINI_MODEL:gemini-3.1-flash-lite}
- quarkus.langchain4j.ollama.base-url + chat-model.model-id (qwen3:30b) for the ollama path
- %test pins the provider to ai-gemini (WireMock stubs the Gemini HTTP path) +
  %test.quarkus.langchain4j.ai.gemini.api-key=test-gemini-key (the agent is mocked/WireMock'd)
- quarkus.datasource.db-kind=postgresql + jdbc url localhost:5434/fraud
  (the spec said 5432; the host port was moved to 5434 because 5432 was already
  allocated by another local Postgres container — the container still listens on 5432)
- quarkus.hibernate-orm.database.generation=none (Flyway owns the schema)
- app.dataset.path=./data, app.dataset.max-rows=100000
- app.logging.max-body=2000
- Scope the explicit datasource url/credentials to %dev/%prod so the %test profile falls
  back to Quarkus Dev Services (Testcontainers Postgres); disable the DataLoader in %test.

## TESTS
- A @QuarkusTest that hits /initiate then /retrieve and asserts the control record exists,
  plus update (analyst override → DISPOSED), notify, and the 404 paths for every operation.
- A @QuarkusTest that mocks the FraudTriageAgent bean and asserts /evaluate sets riskScore +
  decision (and that unparseable output falls back to REVIEW).
- **WireMock integration tests** (similar to congress-trade-watcher's WireMockTestResource):
  a `QuarkusTestResourceLifecycleManager` that boots WireMock and points
  `quarkus.langchain4j.ai.gemini.base-url` at it, so the test drives the REAL agent →
  langchain4j → Gemini HTTP client (the client calls `{base-url}/v1beta/models/{model}:generateContent`).
  Stub the `generateContent` endpoint with verdict variations: BLOCK, CLEAR, JSON wrapped in
  markdown/prose, out-of-range score (clamped), unknown decision (→ REVIEW), and non-JSON
  (→ REVIEW fallback); verify the client actually called generateContent.
- **Unit tests** (plain JUnit, no Quarkus boot): FraudTriageServiceTest covering every parser
  branch; FraudVerdictTest (fallback factory); FraudDtosTest (entity → EvaluationResponse).
- Test deps: add `org.wiremock:wiremock-standalone` and `org.assertj:assertj-core` (test scope).

## DOCS, POSTMAN & CI (match the fundlens project at /Users/senthilkumar/google-ai/fundlens)
- **Postman collection** at `docs/postman/bian-fraud-detection.postman_collection.json`,
  organised into folders with many variations: a Lifecycle chain; Initiate variations
  (TRANSFER drain, CASH_OUT, PAYMENT, DEBIT, CASH_IN); self-contained Evaluate scenarios
  (seed → evaluate per risk profile, incl. re-evaluate); Update variations (CLEAR/BLOCK/REVIEW);
  Notify variations (default/custom); and Error cases (404s). Use `{{baseUrl}}` (default
  `localhost:8080`) and a `{{ref}}` collection variable; `initiate` requests capture the
  returned `referenceId` into `{{ref}}` via a test script, and each request has `pm.test`
  assertions so the collection runs green in the Collection Runner.
- **Architecture diagram** at `docs/architecture.puml`, rendered to `docs/architecture.svg`
  (`plantuml -tsvg -o . docs/architecture.puml`). Mirror fundlens's colour key
  (blue = REST, purple = orchestration/agents, green = data, cyan = services/eventing,
  orange = external).
- **Sequence diagrams**, one per endpoint, sources in `docs/sequence/*.puml` (PlantUML,
  reusing fundlens's skinparam header) rendered to `docs/sequence/rendered/*.svg`
  (`plantuml -tsvg -o rendered docs/sequence/*.puml`).
- **README** updated with a CI badge, the embedded architecture SVG, and the per-endpoint
  sequence SVGs (the lead endpoint inline, the rest in `<details>` blocks), plus a Postman
  paragraph — matching fundlens's README layout.
- **GitHub Actions workflow** at `.github/workflows/ci.yml` matching fundlens's
  (`actions/checkout@v5`, `actions/setup-java@v5`, `cache: maven`, `./mvnw verify`, upload
  surefire reports on failure) but on **JDK 25**. CI is offline: the agent is mocked or
  WireMock'd (no real Gemini calls) and the DataLoader is disabled in `%test`, with Dev
  Services providing Postgres.

## DELIVERABLES
- Working `./mvnw quarkus:dev` startup.
- A `.env` / `.env.example` (gitignored .env) holding GEMINI_API_KEY, modeled on
  /Users/senthilkumar/google-ai/congress-trade-watcher/.env (Quarkus auto-loads .env in dev).
- A README.md with: prerequisites (JDK 25, a Gemini API key, Postgres running),
  the Kaggle download steps, and example curl commands for each BIAN endpoint, plus the
  CI badge, architecture diagram, sequence diagrams, and Postman collection reference.
- A docker-compose.yml for Postgres+pgvector for local dev.
- A Postman collection under `docs/postman/`.
- Architecture + per-endpoint sequence diagrams under `docs/` (PlantUML sources + rendered SVGs).
- A GitHub Actions CI workflow under `.github/workflows/`.

Generate the full project now, then show me the directory tree and the README.
