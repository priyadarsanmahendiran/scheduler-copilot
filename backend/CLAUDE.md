# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the application
./mvnw spring-boot:run

# Build (skip tests)
./mvnw package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=MachineServiceTest
```


## Architecture

This is a Spring Boot 3 / Java 17 backend simulating a factory shop floor scheduler. There is no database — all state lives in `InMemoryStore` (a `@Component` backed by `ConcurrentHashMap`s).

### Package layout

```
com.scheduler
├── model/          # Plain POJOs: Machine, Job, Schedule
├── store/          # InMemoryStore — single source of truth for runtime state
├── service/        # MachineService — business logic over the store
├── controller/     # ShopFloorController — REST endpoints (/machines, /schedule)
└── config/         # DataInitializer — seeds machines M1–M3 and jobs J1–J5 via @PostConstruct
```

### Key design points

- **`InMemoryStore`** is the only place state is mutated. Services and controllers depend on it via constructor injection.
- **`Schedule`** wraps a `Map<String, List<Job>>` (machineId → jobs). It is set wholesale by `DataInitializer` and will later be replaced by scheduling logic.
- **`MachineService`** is the only entry point for mutating machine state (`updateMachineStatus`, `updateHeartbeat`). Controllers should not touch the store directly for machine mutations.
- No scheduling, optimization, or AI logic exists yet — those are future layers to add on top of this foundation.
