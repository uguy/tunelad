---
status: proposed
---

# A Set has its own identity, and is either Track-derived or Account-created

Accounts need to prepare named Sets to play with others, with Tunes they may not know yet, and to share them by reference rather than copy. So a Set is no longer unique per ordered list of Tunes (this supersedes that part of ADR 0001): it has its own identity and an optional name. A Track-derived Set mirrors its Track's Performances and is edited only through the Track; an Account-created Set belongs to its owner, who alone edits it. Other Accounts add it to their own sets by reference.

## Considered options

- **Keep one Set per ordered list, with Accounts' sets as private references (Learning list)**: rejected because it gives a Set no owner, no name, and no way to edit a list without changing every Track that points at it.
- **Copy a Set when shared**: rejected because the sharer's edits would no longer reach the people they play with.

## Consequences

- Identification creates a Set for its Track and never searches for an existing one.
- Editing a created Set changes it for everyone who references it.
- The Learning list and Prepared set terms are dropped (issue #822 is superseded).
