# Scheduler CoPilot

AI-driven factory shop floor scheduler with human-in-the-loop decision making. Built for a national hackathon — the AI element is dominant by design.

## Architecture

```
Machine Failure Detected
        ↓
OR-Tools CP-SAT (deterministic)
  ├─ Time-Optimal Schedule  (minimize makespan)
  └─ Cost-Optimal Schedule  (minimize disrupted machines)
        ↓
Claude claude-opus-4-7 (agentic tool loop)
  ├─ Reads both schedules via tools
  ├─ Checks cascade risk per machine
  └─ Produces structured analysis + recommendation
        ↓
Human Operator (POST /api/schedule/choose)
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
First run downloads OR-Tools native libraries (~7s). The app starts on `http://localhost:8080`.

**Terminal 2 — Frontend**
```bash
cd frontend
npm install
npm run dev
```
Opens on `http://localhost:5173`. The Vite dev server proxies all API calls to the backend.

**Swagger UI:** `http://localhost:8080/swagger-ui.html`

## Demo Flow

1. Open `http://localhost:5173` — three machine cards show green pulsing heartbeats and the current Gantt chart
2. Click **⚠ Simulate Failure** on any machine
3. The machine card turns red, the Gantt grays out that row, a spinner shows *"Claude analyzing…"*
4. After 15–30 seconds the analysis panel slides in:
   - **SITUATION** — what failed and immediate impact
   - **Option A (Fast)** — time-optimal schedule with makespan metric and mini-Gantt
   - **Option B (Cheap)** — cost-optimal schedule with disruption count and mini-Gantt
   - **Cascade Risk** — which machines are at risk of overload
   - **Claude's Recommendation** — which option it recommends and why
5. Click **⚡ Apply Option A** or **💰 Apply Option B** — the main Gantt updates live
6. A green toast confirms the schedule was applied

## API Reference

### Machine Control
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/machines` | List all machines with status and last heartbeat |
| `POST` | `/machines/{id}/down` | Block heartbeat to simulate failure |
| `GET` | `/schedule` | Current job schedule (`Map<machineId, List<Job>>`) |

### Decision Flow
| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/api/schedule/failure` | `{ "machineId": "M1" }` | Run OR-Tools + Claude analysis, returns session |
| `GET` | `/api/schedule/pending` | — | Active sessions waiting for operator decision |
| `GET` | `/api/schedule/session/{id}` | — | Fetch analysis for a specific session |
| `POST` | `/api/schedule/choose` | `{ "sessionId": "...", "userMessage": "go with the faster option" }` | Submit operator decision in plain English |

The `/choose` endpoint accepts any natural-language message — Claude interprets it to determine the chosen option. If confidence is below 0.6 it returns a clarification request instead of applying a schedule.

### Response shape — `POST /api/schedule/failure`
```json
{
  "sessionId": "uuid",
  "claudeAnalysis": "SITUATION: ...\n\nOPTION A (Fast): ...\n\nOPTION B (Cheap): ...\n\nCASCADE RISK: ...\n\nMY RECOMMENDATION: ...",
  "optionAText": "...",
  "optionBText": "...",
  "optionASchedule": { "assignments": { "M2": [...], "M3": [...] } },
  "optionBSchedule": { "assignments": { "M2": [...] } },
  "optionAMetrics": { "makespan": 90, "disruptedCount": 1, "machineLoads": { "M2": 90 } },
  "optionBMetrics": { "makespan": 120, "disruptedCount": 1, "machineLoads": { "M2": 120 } },
  "expiresAt": 1713456789000
}
```

Sessions expire after 5 minutes.

## Project Structure

```
src/main/java/com/scheduler/
├── config/
│   ├── CorsConfig.java               # CORS for Vite dev server
│   └── DataInitializer.java          # Seeds machines M1-M3 and jobs J1-J5 on startup
├── controller/
│   ├── MachineController.java        # POST /machines/{id}/down
│   ├── ShopFloorController.java      # GET /machines, GET /schedule
│   └── ScheduleDecisionController.java
├── model/
│   ├── Machine.java                  # id, status, lastHeartbeat
│   ├── Job.java                      # id, machineId, duration (minutes)
│   ├── Schedule.java                 # Map<machineId, List<Job>>
│   ├── ScheduleMetrics.java          # makespan, disruptedCount, machineLoads
│   └── ScheduleWithMetrics.java      # Schedule + ScheduleMetrics
├── service/
│   ├── HeartbeatService.java         # @Scheduled 2s — updates lastHeartbeat
│   ├── FailureDetectionService.java  # @Scheduled 3s — detects stale heartbeats (>5s)
│   ├── OptimizationService.java      # @Async — triggers decision flow on failure
│   ├── CpSatSchedulingService.java   # OR-Tools CP-SAT solver
│   ├── ClaudeAgentService.java       # Manual tool-use loop with claude-opus-4-7
│   ├── ScheduleDecisionService.java  # Orchestrates CP-SAT → Claude → session
│   └── ChoiceInterpreterService.java # Parses operator choice with claude-haiku-4-5
├── client/
│   └── AnthropicClient.java          # SDK wrapper (sendMessage + getSdkClient)
└── store/
    └── InMemoryStore.java            # ConcurrentHashMap state (no database)

frontend/src/
├── App.jsx                           # State, polling, layout
└── components/
    ├── MachineCard.jsx               # Status card with heartbeat pulse animation
    ├── GanttChart.jsx                # Horizontal bar chart, job-colored bars
    └── AnalysisPanel.jsx             # Claude analysis, option cards, decision buttons
```

## Running Tests

```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=ScheduleDecisionServiceTest
```

All services are unit-tested with Mockito. Tests mock `CpSatSchedulingService` and `ClaudeAgentService` so they run without OR-Tools or an API key.

## License

MIT
