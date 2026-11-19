// Conventional Commits check, run on PR titles by .github/workflows/workflow-pr-title.yml
// (PRs are squash-merged, so the PR title becomes the commit message on main).
export default {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Dependabot titles can exceed 100 chars and cannot be edited: warn instead of failing.
    'header-max-length': [1, 'always', 100],
  },
};
