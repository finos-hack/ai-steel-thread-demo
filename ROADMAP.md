# Roadmap

Public plan for the AI Steel Thread Demo. **GitHub Issues are the source of truth.** This file only groups them by release.

## How to

1. **File an issue** with a template: [Feature](https://github.com/finos-hack/ai-steel-thread-demo/issues/new?template=feature.yml), [Bug](https://github.com/finos-hack/ai-steel-thread-demo/issues/new?template=bug.yml), [Patch](https://github.com/finos-hack/ai-steel-thread-demo/issues/new?template=patch.yml), [Docs](https://github.com/finos-hack/ai-steel-thread-demo/issues/new?template=docs.yml), or [Ops](https://github.com/finos-hack/ai-steel-thread-demo/issues/new?template=ops.yml).
2. **Apply one type label** (the template pre-selects it):

   | Type | Use for |
   |------|---------|
   | `type:feature` | New capability |
   | `type:bug` | Incorrect behaviour |
   | `type:patch` | Small fix or chore that is not a user-facing bug |
   | `type:docs` | Walkthrough, narration, README, Demo guide |
   | `type:ops` | Docker, CI, observability, infra |

3. **Set the milestone** to the target release — that *is* the horizon:
   - [v1.1.0](https://github.com/finos-hack/ai-steel-thread-demo/milestone/2) — next release
   - [v1.2.0](https://github.com/finos-hack/ai-steel-thread-demo/milestone/3) — following release
   - [Backlog](https://github.com/finos-hack/ai-steel-thread-demo/milestone/4) — unscheduled
4. **Link the issue** in the matching section below (`- [ ] #123 — short title`).
5. **Ship it:** open a PR that closes the issue (`Closes #123`). When the milestone is empty of open issues, tag the release (`v1.1.0`, …), publish a GitHub Release, close the milestone, and move the line into **Shipped**.
6. **Plan a later version:** create a new GitHub milestone (`v1.3.0`, …), add a section here, and add that name to the “Target release” dropdown in `.github/ISSUE_TEMPLATE/*.yml`.

Browse open work: [issues](https://github.com/finos-hack/ai-steel-thread-demo/issues) · [milestones](https://github.com/finos-hack/ai-steel-thread-demo/milestones).

Do not track work only in this file, and do not build a second backlog in the Launch UI. If the plan needs to appear in a demo, render this markdown the same way as the Demo guide.

## v1.1.0

_Nothing queued yet._

## v1.2.0

_Nothing queued yet._

## Backlog

Unscheduled. Move an issue onto a release milestone when it is in scope.

_Nothing queued yet._

## Shipped

- [v1.0.0](https://github.com/finos-hack/ai-steel-thread-demo/releases/tag/v1.0.0) — first public release (Launch UI, Demo guide, Docker stack, CALM Hub)
