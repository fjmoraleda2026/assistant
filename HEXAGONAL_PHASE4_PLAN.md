# Hexagonal Phase 4 Plan (Physical Package Move)

## Goal
Move classes from a mixed core layout into explicit hexagonal packages without changing runtime behavior.

## Target Package Layout
- com.assistant.application.usecase
- com.assistant.application.service
- com.assistant.application.port.in
- com.assistant.application.port.out
- com.assistant.domain.model
- com.assistant.infrastructure.adapter.in.kafka
- com.assistant.infrastructure.adapter.in.telegram
- com.assistant.infrastructure.adapter.in.http
- com.assistant.infrastructure.adapter.out.ai
- com.assistant.infrastructure.adapter.out.persistence
- com.assistant.infrastructure.adapter.out.memory
- com.assistant.infrastructure.adapter.out.messaging
- com.assistant.infrastructure.config

## Move Order (Safe)
1. Move only ports (in/out) to application.port.* and fix imports.
2. Move use-case services to application.service and keep Spring annotations.
3. Move adapters in (kafka/telegram/http) to infrastructure.adapter.in.*.
4. Move adapters out (ai/persistence/memory/messaging) to infrastructure.adapter.out.*.
5. Move domain model to domain.model and keep JPA annotations temporarily.
6. Move configuration classes to infrastructure.config.
7. Compile after each step; no functional changes.

## Non-Goals in Phase 4
- No logic rewrite.
- No endpoint/topic/property changes.
- No database schema changes.

## Acceptance Checklist
- mvn -q -DskipTests compile passes after each move block.
- No adapter depends on concrete application services directly (ports only).
- Application services depend only on application/domain + out ports.
- Incoming adapters call in ports only.
