# Issue triage

How issues in this repo are classified. Written down so the scheme survives gaps in
development rather than being re-invented each time.

Every open issue should carry **one priority**, **at least one area**, and a **milestone**.

## Priority

Ordered by what it costs the project, not by how annoying it is. Chunklock is a paid plugin,
so anything that produces a refund request outranks everything else.

| Label | Meaning | Response |
|---|---|---|
| `priority-p0` | Server-killing: crashes, data loss, memory exhaustion | Drop other work |
| `priority-p1` | Breaks gameplay or blocks progression | Fix before the next release |
| `priority-p2` | Wrong behavior, but the game is still playable | Schedule normally |
| `priority-p3` | Cosmetic, or a nice-to-have improvement | When convenient |

Feature requests are normally `p3` unless they unblock something else.

## Area

Where the work lands. Multiple areas are fine — `#82` is both `area-economy` (root cause) and
`area-ui` (the path that triggers it).

- `area-economy` — cost calculation, unlock requirements, Vault and material payments
- `area-holograms` — hologram lifecycle, providers, display state
- `area-borders` — chunk borders, barriers, visual boundaries
- `area-persistence` — MapDB/MySQL storage, claims, migrations
- `area-ui` — GUIs, menus, player-facing messages
- `area-config` — config files, validation, defaults

Historically bugs cluster in `area-economy`. That subsystem changed fastest, and it shows.

## Type

`bug`, `enhancement`, `documentation`, `performance`, `design-decision`.

`design-decision` marks issues that need a product call before code can be written. These are
kept **separate** from the bug they arose from — merging them buries the decision inside a bug
fix and makes the bug unfixable in isolation. See `#82` and `#83` for the worked example.

## Workflow labels

- `needs-info` — waiting on the reporter; not actionable yet
- `needs-live-validation` — fixed and unit-tested, but never verified on a running server.
  A fix is not done until this label comes off.
- `regression-test-needed` — the fix must ship with a test that would have caught the bug

## Milestones

Milestones are releases, not sprints.

- **2.2.1 - Unlock GUI stability** — narrow bugfix release
- **2.3.0 - Price commitment** — the economy change
- **2.4.0 - Feature restart** — return to feature work
- **Backlog - Unscheduled** — triaged and accepted, no release committed yet

Keeping releases narrow is deliberate. A release carrying both a memory fix and an economy
redesign is one where neither can be validated cleanly, and a rollback throws away both.

## Branches

- `fix/<issue-number>-<short-slug>` for bugs — e.g. `fix/82-unlock-gui-material-cycling`
- `feature/<short-slug>` for features
- `chore/<short-slug>` for tooling, CI, docs

Create bug branches from the **Development** panel on the issue itself, or with
`gh issue develop <number>`. That creates a real link, so the issue closes automatically when
the PR merges and the branch shows on the issue.

## Closing

Reference the issue in the PR body with `Closes #NN`. Before closing a bug, confirm the fix
was actually observed working — not just that the test passes. `needs-live-validation` exists
because that gap is easy to miss.
