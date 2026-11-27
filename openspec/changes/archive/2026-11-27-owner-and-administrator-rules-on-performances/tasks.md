## 1. Branch

- [x] 1.1 Create branch `test/performance-edit-rules` from an up-to-date `main`

## 2. Administrator edits Performances (one commit: `test(track): cover administrator edits of performances`)

- [x] 2.1 In `TrackPerformancesTest`, add a test where an Administrator edits the Performances of another Account's Track: 200 OK, key stored, `owner` unchanged (reuse the admin token used in `TrackApiTest`)
- [x] 2.2 Check `TrackPerformancesTest` already covers the Owner edit and the other-user 403 scenarios of the spec delta; add what is missing
- [x] 2.3 If a test fails, fix the production code in the `track` module and say so in the commit body

## 3. Spec and glossary

- [x] 3.1 Confirm `GLOSSARY.md` needs no change (Owner and Administrator already say "the Track and its Performances"); edit it only if a term differs from the spec delta

## 4. Verify and open the PR

- [x] 4.1 Run `./mvnw clean verify`
- [x] 4.2 README.adoc features: no user-visible change, so no update
- [x] 4.3 Archive the change on the branch (`/openspec-archive-change`)
- [ ] 4.4 Open the PR titled `test(track): cover administrator edits of performances`, body ending with `Closes #821`
