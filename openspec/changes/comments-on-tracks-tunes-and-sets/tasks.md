## 1. Branch and persistence

- [x] 1.1 Branch `feat/comments` created from an up-to-date `main` (already done by `/next-slice`).
- [x] 1.2 Add `comment` module skeleton (`package-info.java` with allowed dependencies), Liquibase `comment.sql` registered in the master changelog, `Comment` entity and reactive repository. Verify: `ModularApplicationTests` and a repository test on Testcontainers PostgreSQL pass. Commit `feat(comment): add comment storage`.

## 2. Read, post, edit, delete API

- [x] 2.1 `Subjects` existence check over `track`, `tune`, `set` public APIs; `CommentService` with text validation and author-or-Administrator rule; controllers for the five endpoints; anonymous GET allowed in security config; OpenAPI annotations. Verify: integration tests cover every scenario of `specs/comment` (200/201/204/400/401/403/404, ordering, edited flag). Commit `feat(comment): comment on tracks, tunes and sets api`.
- [x] 2.2 `TrackDeleted` cleanup listener. Verify: test deleting a Track with Comments removes them. Commit `feat(comment): delete comments with their track`.

## 3. Live updates

- [x] 3.1 `CommentChanged` event in `comment::event`, published on post/edit/delete/cleanup; `LiveBridge` publishes `comment.changed` to everyone; `live` `allowedDependencies` updated. Verify: live test shows anonymous and authenticated clients receive `comment.changed` with the subject id and no text; `ModularApplicationTests` passes. Commit `feat(live): announce comment changes`.

## 4. UI

- [x] 4.1 `Comments.svelte` (list, post form, own edit/delete, Administrator delete, sign-in invitation, live refetch) on the Tune and Set detail pages and in the expanded track of the tracks list (Tracks have no detail page), with vitest component tests and Playwright e2e (API mocked with `page.route`) for anonymous, post, own-actions scenarios. Verify: `bun run lint`, `bun run check`, vitest and e2e pass; contrast and keyboard use checked. Commit `feat(ui): comments on tracks, tunes and sets`.

## 5. Docs and delivery

- [x] 5.1 Update `GLOSSARY.md` (`Comment`: flat plain text, author or Administrator edits/deletes, public; `Live update` mentions Comments) and README.adoc features. Verify: terms match the specs. Commit `docs(comment): describe comments`.
- [ ] 5.2 Run `./mvnw clean verify`; it must pass. Verify: exit code 0.
- [ ] 5.3 Push and open the PR titled `feat(comment): comment on tracks, tunes and sets`, body ending with `Closes #844`. Verify: PR exists and `workflow-pr-title` passes.
