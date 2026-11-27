---
name: next-slice
description: Pick the next unblocked roadmap slice from GitHub Issues and start its OpenSpec change. Use when the user says "next slice", "what's next on the roadmap", or "/next-slice [#issue]".
allowed-tools: Bash(gh:*), Bash(openspec:*), Bash(jq:*)
---

Start work on the next roadmap slice. Slices are sub-issues of the issue labelled `roadmap`
(conventions in `docs/agents/issue-tracker.md`). Planning only: do not write code.

1. If an issue number was given, use it. Otherwise find the frontier:
   - `gh issue list --label roadmap --state open` to get the roadmap parent, then its open sub-issues.
   - Drop any that has an assignee or an open blocker (`gh api repos/{owner}/{repo}/issues/<n> --jq .issue_dependencies_summary`).
   - Take the lowest-numbered remaining slice and confirm it with the user if more than one is left.
2. `gh issue view <n> --json number,title,body,labels` and read the slice. Read `GLOSSARY.md` and `docs/adr/`.
3. Claim it: `gh issue edit <n> --add-assignee @me`.
4. Move its card to **In progress** on the project (`uguy/projects/1`, see `docs/agents/issue-tracker.md`).
5. Invoke `openspec-propose` with the slice as input:
   - change name: kebab-case of the issue title
   - first line of `proposal.md`: `Issue: #<n>`
   - use the issue's glossary terms, modules, out of scope and suggested PR title as the starting point
   - add a task to update `GLOSSARY.md` if a term changed, and a final task to open the PR with `Closes #<n>`
6. Stop after the artifacts exist. Implementation starts with `/openspec-apply-change`.
