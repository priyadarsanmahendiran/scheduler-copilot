# Scheduler CoPilot

AI-driven factory shop floor scheduler with human-in-the-loop decision making. Built for a national hackathon — the AI element is dominant by design.

## Architecture

### Failure Flow
```
Machine Failure Detected
        ↓
OR-Tools CP-SAT (deterministic)
  ├─ Time-Optimal Schedule  (minimize makespan)
  └─ Cost-Optimal Schedule  (minimize disrupted machines)
        ↓
Claude claude-opus-4-7 (agentic tool loop)
  ├─ Reads all machine statuses + loads
  ├─ Retrieves both schedules via tools
  ├─ Checks cascade risk per machine
  └─ Produces structured analysis + recommendation
        ↓
Human Operator (POST /api/schedule/choose)
        ↓
Schedule Applied
```

### Recovery Flow
```
Machine Self-Recovery Detected (heartbeat resumes)
        ↓
OR-Tools CP-SAT
  ├─ Rebalance (full re-optimisation across all machines)
  └─ Restore   (original jobs returned to recovered machine)
        ↓
Claude claude-opus-4-7
  ├─ Assesses recovered machine stability
  ├─ Compares disruption cost of rebalance vs restore
  └─ Recommends option considering SLA impact
        ↓
Human Operator Decision
        ↓
Schedule Applied
```

**Design principle:** OR-Tools owns all scheduling math — results are mathematically optimal and deterministic, never hallucinated. Claude reasons about tradeoffs and cascade risks using the OR-Tools output as ground truth. The human makes the final call.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.2.4, Java 17 |
| Optimization | Google OR-Tools CP-SAT 9.12.4544 |
| AI | Anthropic Java SDK 2.17.0, `claude-opus-4-7` |
| Choice Interpreter | `claude-haiku-4-5` (fast natural-language parsing) |
| Frontend | React 18, Vite 5, Tailwind CSS 3 |
| Live Updates | Server-Sent Events (SSE) |
| API Docs | SpringDoc OpenAPI (Swagger UI) |

## Prerequisites

- Java 17+
- Node.js 18+
- Maven 3.8+ (or use the `mvnw` wrapper)
- Anthropic API key — get one at [console.anthropic.com](https://console.anthropic.com)

## Running

**Terminal 1 — Backend**
```bash
export ANTHROPIC_API_KEY=sk-ant-...
./mvnw spring-boot:run
```
The app starts on `http://localhost:8080`.

**Terminal 2 — Frontend**
```bash
cd frontend
npm install
npm run dev
```
Opens on `http://localhost:5173`.

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

## Demo Flow

### Failure scenario
1. Open `http://localhost:5173` — 15 machine cards show green pulsing heartbeats and the current Gantt chart
2. Click **✖ Simulate Failure** on any machine (or multiple back-to-back)
3. Machine card turns red, Gantt grays out that row, spinner shows *"Claude analyzing…"*
4. After 15–30 seconds the analysis panel slides in:
   - **SITUATION** — what failed, all offline machines, immediate impact
   - **Option A (Fast Track)** — time-optimal schedule with makespan metric and mini-Gantt
   - **Option B (Cost Optimal)** — cost-optimal schedule with disruption count and mini-Gantt
   - **SLA breach warnings** — jobs that will miss deadlines highlighted in red
   - **Cascade Risk** — which machines are at risk of overload
   - **Claude's Recommendation** — which option and why
5. Ask Claude follow-up questions via the chat box before deciding
6. Click **Apply Option A** or **Apply Option B** — the main Gantt updates live
7. A green toast confirms the schedule was applied

> **Multi-machine failure:** Bring down multiple machines back-to-back. The last failure analysis wins — it accounts for all currently-offline machines. Stale analyses (that would redistribute jobs to already-down machines) are discarded automatically. The *Bring Online* button is disabled until a rescheduling decision is applied.

### Recovery scenario
1. With one or more machines down and a schedule applied, click **↑ Bring Online** on any machine
2. Machine recovers immediately; a recovery analysis starts automatically
3. Analysis panel shows **Rebalance** (full re-optimisation) vs **Restore** (return original jobs)
4. Apply the preferred option

> **Multi-machine recovery:** Bring up multiple machines back-to-back. The last recovery analysis wins — it sees all recovered machines and produces a valid schedule for all of them. Stale analyses are discarded. *Simulate Failure* is disabled on running machines until the recovery decision is applied.

### Predictive maintenance
Machines develop **HIGH** or **MEDIUM** risk badges when heartbeat intervals become irregular (detected via rolling variance). A degradation simulation is available to trigger this manually.

## API Reference

### Machine Control
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/machines` | List all machines with status, heartbeat, and risk level |
| `POST` | `/machines/{id}/down` | Simulate failure (blocks heartbeat, sets status DOWN) |
| `POST` | `/machines/{id}/heartbeat` | Ingest real heartbeat — auto-detects DOWN→RUNNING self-recovery |
| `POST` | `/machines/{id}/self-recover` | Simulate self-recovery (alias for heartbeat on a DOWN machine) |
| `POST` | `/machines/{id}/degrade` | Start heartbeat jitter simulation for predictive maintenance |
| `GET` | `/schedule` | Current job schedule (`Map<machineId, List<Job>>`) |
| `GET` | `/api/events` | SSE stream — pushes `machines`, `schedule`, and `pending` events |

### Decision Flow
| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/schedule/failure` | `{ "machineId": "M1" }` | Run OR-Tools + Claude failure analysis, returns session |
| `POST` | `/api/schedule/recovery` | `{ "machineId": "M1" }` | Run OR-Tools + Claude recovery analysis, returns session |
| `GET` | `/api/schedule/pending` | — | Active sessions waiting for operator decision |
| `GET` | `/api/schedule/session/{id}` | — | Fetch analysis for a specific session |
| `POST` | `/api/schedule/choose` | `{ "sessionId": "...", "userMessage": "go with the faster option" }` | Submit operator decision in plain English |
| `POST` | `/api/schedule/chat` | `{ "sessionId": "...", "message": "..." }` | Ask Claude a follow-up question about the analysis |

The `/choose` endpoint accepts any natural-language message — Claude interprets it to determine the chosen option. If confidence is below 0.6 it returns a clarification request instead of applying a schedule.

### Response shape — `POST /api/schedule/failure` or `/api/schedule/recovery`
```json
{
  "sessionId": "uuid",
  "sessionType": "failure",
  "claudeAnalysis": "SITUATION: ...\n\nOPTION A ...\n\nOPTION B ...\n\nCASCADE RISK: ...\n\nMY RECOMMENDATION: ...",
  "optionAText": "...",
  "optionBText": "...",
  "optionASchedule": { "assignments": { "M2": [...], "M3": [...] } },
  "optionBSchedule": { "assignments": { "M2": [...] } },
  "optionAMetrics": { "makespan": 90, "disruptedCount": 1, "machineLoads": { "M2": 90 } },
  "optionBMetrics": { "makespan": 120, "disruptedCount": 1, "machineLoads": { "M2": 120 } },
  "optionASla": { "breachCount": 0, "breachedJobIds": [] },
  "optionBSla": { "breachCount": 2, "breachedJobIds": ["J3", "J7"] },
  "claudeUnavailable": false,
  "expiresAt": 1713456789000
}
```

Sessions expire after 5 minutes.

## Demo Data

15 machines (M1–M15), 24 jobs (J1–J24) seeded on startup. Job deadlines are calibrated so the current balanced schedule meets all SLAs, but cost-optimal redistribution (concentrating work onto fewer machines) triggers SLA breaches — making the tradeoff visible in the UI.

## Key Design Decisions

**Last-wins for concurrent failures/recoveries.** When multiple machines fail (or recover) back-to-back, each new analysis clears all in-flight and completed analyses for other machines. The final analysis uses the full current set of offline/online machines, preventing stale schedules that redistribute jobs to unavailable machines.

**Sentinel-encoded mode.** Pending sessions use `"ANALYZING"` vs `"ANALYZING_RECOVERY"` sentinels (not machine status) to distinguish failure vs recovery mode. This survives race conditions where machine status hasn't propagated via SSE yet.

**Write-time guard.** Both failure and recovery analyses check at write time whether their sentinel was superseded. If cleared by a newer analysis, the result is silently discarded — no stale session is exposed to the operator.

**OR-Tools as ground truth.** Claude is never asked to invent schedules. It calls tools (`get_time_optimal_schedule`, `get_cost_optimal_schedule`, `check_cascade_risk`) that return pre-computed OR-Tools results, then reasons over them.

## Project Structure

```
src/main/java/com/scheduler/
├── config/
│   ├── CorsConfig.java               # CORS for Vite dev server
│   └── DataInitializer.java          # Seeds 15 machines and 24 jobs on startup
├── controller/
│   ├── MachineController.java        # Machine state + heartbeat endpoints
│   ├── ShopFloorController.java      # GET /machines, GET /schedule, SSE /api/events
│   └── ScheduleDecisionController.java
├── model/
│   ├── Machine.java                  # id, status, lastHeartbeat, riskLevel, heartbeatBlocked
│   ├── Job.java                      # id, machineId, duration, deadline (minutes)
│   ├── Schedule.java                 # Map<machineId, List<Job>>
│   ├── DecisionSession.java          # sessionId, sessionType, options, chat history
│   ├── ScheduleMetrics.java          # makespan, disruptedCount, machineLoads
│   ├── ScheduleDecisionResponse.java # Full API response including SLA breach results
│   └── ScheduleWithMetrics.java      # Schedule + ScheduleMetrics
├── service/
│   ├── HeartbeatService.java         # @Scheduled 2s — updates lastHeartbeat for running machines
│   ├── FailureDetectionService.java  # @Scheduled 3s — detects stale heartbeats (>5s)
│   ├── PredictiveMaintenanceService.java # Rolling heartbeat variance → risk level badges
│   ├── OptimizationService.java      # @Async — triggers failure/recovery decision flow
│   ├── CpSatSchedulingService.java   # OR-Tools CP-SAT (time-optimal, cost-optimal, rebalance)
│   ├── ClaudeAgentService.java       # Tool-use loop with claude-opus-4-7 (failure + recovery)
│   ├── ScheduleDecisionService.java  # Orchestrates CP-SAT → Claude → session lifecycle
│   ├── ChoiceInterpreterService.java # Parses operator choice with claude-haiku-4-5
│   ├── MachineService.java           # Machine state mutations + self-recovery detection
│   └── SseBroadcaster.java           # Pushes machines/schedule/pending events to all clients
├── client/
│   └── AnthropicClient.java          # SDK wrapper
└── store/
    └── InMemoryStore.java            # ConcurrentHashMap state — broadcasts on mutation

frontend/src/
├── App.jsx                           # SSE subscription, pending session tracking, state
└── components/
    ├── MachineCard.jsx               # Status, heartbeat, risk badge, action buttons
    ├── GanttChart.jsx                # Horizontal bar chart with SLA breach highlighting
    └── AnalysisPanel.jsx             # Claude analysis, Gantt previews, chat, decision buttons
```

## License

MIT
