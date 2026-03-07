# AMAN Concept (High Level)

## Status

This document is work in progress (WIP) and intentionally high-level.
It reflects current team understanding and is open to critique and corrections from contributors with deeper AMAN operational knowledge.

## Purpose

Arrival Management (AMAN) is a decision-support function that helps ATC organize inbound traffic into a stable, safe, and efficient landing sequence.  
It does not replace controller judgment. It supports it with predictions, sequencing proposals, and timing advisories.

At a high level, AMAN aims to:

- reduce holding and tactical vectoring;
- smooth demand at runway and metering points;
- improve predictability for en-route, approach, and tower teams;
- keep runway throughput stable under varying traffic and weather conditions.

## Trajectory Estimation (Conceptual)

AMAN trajectory prediction is usually runway-anchored:

- Predict runway-threshold ETA (estimated crossing time) and other key-point ETAs from route, performance, and wind data.
- Propagate estimates across the expected route/profile to derive timing at metering points and approach-relevant fixes.
- Use aircraft performance assumptions (speed, descent capability, configuration effects) to model travel time.
- Apply forecast/measured wind components along altitude bands and route segments.
- Continuously recompute as wind, route, runway mode, and aircraft intent data change.

AMAN first estimates how the aircraft is likely to progress in time (prediction), then sequence management assigns target times/advisories based on those predictions.

## How AMAN Works (General Flow)

### Predicted Time vs Target Time (ETA, STA)

- Predicted time (ETA): model output based on current trajectory/performance/wind assumptions; continuously updated and uncertain.
- Target time / STA (Scheduled Time of Arrival): planning/advisory output chosen by AMAN/controller to achieve runway and flow objectives.
- AMAN compares STA vs ETA to derive advisory intent (for example absorb delay early, or reduce excess delay where feasible).


1. AMAN ingests flight, surveillance, runway, and constraint data.
2. It predicts key times (for example metering-point and runway-threshold times).
3. It computes or updates an arrival sequence and target times.
4. It generates advisories (for example Time to Gain/Time to Lose, speed control, holding expectations).
5. Controllers apply clearances and can override or adjust the sequence.
6. AMAN continuously recalculates as traffic, runway mode, and weather change.

In more mature implementations, sequence support is extended upstream (Extended AMAN / XMAN), so delay absorption starts earlier in en-route phase instead of late tactical action near the TMA.

## Sequence Truth

To avoid ambiguity across positions, AMAN should define one shared sequence truth.

- Primary canonical anchor: planned runway-threshold crossing time (or landing time) per runway, typically expressed as STA.
- Secondary anchors: metering/fix/IAF times derived from the runway-based plan.
- Role-specific displays can differ, but they must reference the same underlying sequence object.
- If runway mode/configuration changes, sequence truth is recomputed from the new runway context.

This keeps planning, tactical execution, and coordination aligned across en-route, approach, tower, and supervisor views.

## Roles In AMAN Operations

### Sequence Manager / Planner Role

- owns the overall arrival sequence strategy for designated runway/airport flows;
- monitors sequence stability and manually adjusts when needed;
- coordinates AMAN advisories with feeder en-route sectors;
- adapts constraints when runway configuration, demand, or weather changes.

Note: this can be a dedicated position or combined with another ATCO position depending on local operations.

### En-route Controllers (Feeder Sectors)

- use AMAN advisories early (before top of descent where possible);
- apply speed/Mach and minor profile actions to deliver aircraft in sequence;
- reduce downstream compression and avoid unnecessary holding near destination.

### Approach Controllers (TMA)

- convert planned sequence into tactical spacing and final approach order;
- manage short-horizon sequence disturbances (late runway change, go-around, wind shifts);
- use AMAN target times plus local spacing tools as complementary support.

### Tower Controllers

- execute runway delivery in the final stage;
- focus on safe runway operations and local constraints;
- provide feedback when runway configuration/capacity changes affect the sequence.

### Supervisor / Flow Management

- oversees demand-capacity balance and runway mode strategy;
- coordinates cross-unit measures and contingency actions;
- uses AMAN outputs as part of wider ATFM/ATC decision-making.

## Different Views By ATC Position

AMAN is most effective when each position sees a role-tailored view instead of a one-size-fits-all screen.

### 1) Sequence Manager View (Planning View)

- timeline/sequence view by runway (and optionally by metering point);
- sequence edit controls (swap, freeze groups, apply constraints);
- stability horizons (where sequence should be increasingly stable);
- what-if tools for runway/weather/capacity scenarios.

This is the most planning-heavy AMAN view.

### 2) En-route Sector View (Feeder View)

- concise advisory cues embedded in CWP labels/lists;
- target-time conformance cues (early/late trend);
- simple actions: absorb delay early, avoid excessive tactical interventions.

This view should prioritize clarity and low interaction overhead.

### 3) Approach View (Execution View)

- short-horizon runway sequence with target threshold times;
- conformance trend and required spacing actions;
- quick awareness of disruptions requiring tactical resequencing.

This view balances planning intent with high-tempo tactical control.

### 4) Tower View (Runway Delivery View)

- near-term landing order and runway occupancy context;
- short list of arrivals with priority/restriction cues;
- minimal, runway-focused display integrated with tower workflow.

This view should avoid upstream clutter and keep final-stage information clear.

### 5) Supervisor / Flow View (Network View)

- aggregated demand vs capacity over time;
- runway mode and traffic-mix picture;
- coordination indicators across units/positions.

This view supports strategic and cross-position coordination decisions.

## Practical Design Principles

- Keep one shared sequence truth, but present role-specific detail levels.
- Integrate key AMAN advisories into primary controller HMI where possible.
- Allow manual override and transparent sequence edits.
- Prioritize sequence stability closer to runway.
- Extend sequence influence upstream when feasible to reduce late tactical workload.

## Scope Note

This document describes AMAN concept at a general level only.  
Local procedures, phraseology, minima, runway use policies, and system HMI details must be defined per ANSP/unit.

## Reference Basis

- ATMAS Implementation and Operations Guidance Document, Edition 1.5 (ICAO APAC), sections on AMAN/DMAN functions, HMI integration, and position roles/types.
- SESAR Extended AMAN OSED (Project 05.06.07), sequence manager responsibilities, advisory coordination with en-route sectors, and metering/stability horizon concepts.
- EUROCONTROL AMAN concept references (arrival management as sequencing/metering decision support and timeline-oriented operation).