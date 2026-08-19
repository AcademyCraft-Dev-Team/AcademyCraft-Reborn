# Skill execution events

## Decision

Skill execution uses NeoForge's common event bus instead of a private hook
pipeline. `Skill.executeActive` and `Skill.executeContinuous` remain the
stable execution entry points; they publish typed events at the boundaries
where outside effects can make a decision or observe a result.

## Event contract

- `SkillExecutionPreEvent` is cancellable and is posted before cost calculation.
- `SkillExecutionCostEvent` is cancellable and exposes the final cost after
  built-in proficiency/category/symbiosis modifiers but before calculation
  intensity and CP occupation. Subscribers may call `setCost`.
- `SkillExecutionStartEvent` is posted after CP occupation and immediately
  before the action.
- `SkillExecutionFinishEvent` is posted from `finally` and carries success and
  an optional failure. It is not cancellable because CP is already occupied.
- Semantic events such as `TeleportExecutionEvent` are ordinary NeoForge
  events posted by the skill at the successful operation boundary.

The event payload carries the skill, player, execution mode, context, and
actual cost. No thread-local execution frame or implicit event lookup is used.
An event bus event cannot wrap a continuation, so there is deliberately no
generic `around` phase.

## Toggle and lifecycle

Effects that need a lifetime register a listener through the existing
`ServerContext`/`AbilitySystemServer.registerContext` path and unregister it
when the context ends. A context may remain registered for its own tick state
while a boolean activation gate stops new semantic events.

## Verification

`SkillExecutionEventTest` checks cancellation, mutable cost, and explicit
completion/failure state. The production search must contain no
`ActiveExecutionHook`, `SkillExecutionPipeline`, `ThreadLocal` execution frame,
or `Skill.emitExecutionEvent` references.
