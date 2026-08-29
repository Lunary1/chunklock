---
name: Performance or memory report
about: High RAM use, lag, TPS drops, or a suspected leak
title: ''
labels: bug, performance
assignees: ''

---

**What you are seeing**
High memory, low TPS, lag spikes, or a crash. Describe the symptom and when it started.

**Numbers**
Concrete measurements help far more than impressions:
- RAM allocated to the server (`-Xmx`):
- RAM actually used when the problem appears:
- TPS during the problem: <!-- /tps -->
- Player count when it happens:
- How long the server runs before it appears:

**Does a restart fix it, and for how long?**
This is the single most useful question for telling a leak apart from a config problem.

**Chunklock debug output**
Run `/chunklock debug memory` while the problem is happening and paste the output.

```
paste here
```

If you can, run it once shortly after a restart and again once the problem appears — the
difference between the two is more informative than either on its own.

**Server details**
- Chunklock version:
- Server software and version:
- Java version and startup flags:
- Other plugins, especially hologram, border, or world-management plugins:

**Chunklock configuration**
- Holograms enabled, and which provider:
- Borders enabled, and which style:
- Storage backend: <!-- MapDB or MySQL -->
- Roughly how many chunks are claimed server-wide:

**Timings or profiler report**
A Spark or Timings link is the fastest route to a diagnosis:
- Spark: `/spark profiler --timeout 300` then share the link
- Timings: `/timings on`, wait for the problem, then `/timings paste`

**Additional context**
Anything unusual about the deployment — anarchy-style, heavy modification, very large world,
unusual player behavior.
