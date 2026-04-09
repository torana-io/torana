# Releasing Torana

This project uses Release Please to manage releases and the next development snapshot.

The process creates two different pull requests around each release:

- `chore(main): release X.Y.Z`
- `chore(main): release X.Y.(Z+1)-SNAPSHOT`

They are not interchangeable. Merge them at different times.

## What each release PR means

### `chore(main): release X.Y.Z`

This PR removes `-SNAPSHOT`, updates the changelog, and prepares the actual release.

When you merge it:

- the `vX.Y.Z` GitHub release is created
- the `Release Please` workflow runs on `main`
- the `publish` job uploads artifacts to Maven Central
- Release Please opens the next snapshot PR

This is the PR that cuts the release.

### `chore(main): release X.Y.(Z+1)-SNAPSHOT`

This PR bumps the project back to the next development version after the release.

When you merge it:

- `main` becomes the next development version again
- future work continues on the next snapshot

This PR does not publish a release.

## Merge order

Always merge in this order:

1. Merge normal feature and fix PRs into `main`.
2. Wait for `CI` on `main` to be green.
3. Merge `chore(main): release X.Y.Z`.
4. Wait for the `Release Please` workflow on that merge commit to finish.
5. Confirm the `publish` job succeeded.
6. Only then merge `chore(main): release X.Y.(Z+1)-SNAPSHOT`.

Do not merge the snapshot PR before the release publish has succeeded.

## Timing guide

### Before merging `chore(main): release X.Y.Z`

Merge it when all of these are true:

- you want to cut a release now
- the release PR content looks correct
- the branch is up to date enough for your needs
- the last `main` CI run is green

### After merging `chore(main): release X.Y.Z`

Wait for these things:

- the `Release Please` workflow starts on `main`
- the `release-please` job succeeds
- the `publish` job succeeds
- the new snapshot PR is opened

If `publish` is still running, do not merge the snapshot PR yet.

### When to merge `chore(main): release X.Y.(Z+1)-SNAPSHOT`

Merge it only after:

- the release tag already exists
- the `publish` job for that release is green
- you are done handling any release failures

That leaves `main` in the correct state for the next iteration of development.

## The normal happy path

Example for releasing `0.1.8`:

1. Finish and merge normal work into `main`.
2. Wait for `CI` on `main` to pass.
3. Review and merge `chore(main): release 0.1.8`.
4. Open Actions and watch the `Release Please` workflow triggered by that merge.
5. Wait for `publish` to finish successfully.
6. Confirm Release Please opened `chore(main): release 0.1.9-SNAPSHOT`.
7. Merge `chore(main): release 0.1.9-SNAPSHOT`.
8. Continue development on `main`.

End state:

- `v0.1.8` is published to Maven Central
- `main` is on `0.1.9-SNAPSHOT`

## What to do if publish fails

If the release PR was merged but `publish` failed:

1. Do not merge the snapshot PR yet.
2. Fix the workflow or secret problem first.
3. Re-run the failed workflow if that can still publish the release.
4. If the original release cannot be republished automatically, use the manual workflow:
   - Actions
   - `Manual Publish`
   - `Run workflow`
   - `ref = vX.Y.Z`
5. After Maven Central publish succeeds, merge the snapshot PR.

## Manual publish workflow

This repository provides a manual workflow for publishing an existing tag:

- workflow: `Manual Publish`
- input: `ref`
- example: `v0.1.7`

Use it when:

- the release tag already exists
- the automatic publish job was skipped or failed
- you do not want to publish from your local machine

## Operational checklist

For every release:

- merge normal work first
- make sure `main` CI is green
- merge `chore(main): release X.Y.Z`
- wait for `publish` to succeed
- verify the next snapshot PR exists
- merge `chore(main): release X.Y.(Z+1)-SNAPSHOT`
- verify `main` is back on a `-SNAPSHOT` version

## Quick rule to remember

If the PR removes `-SNAPSHOT`, merge it to cut the release.

If the PR adds `-SNAPSHOT`, merge it only after the release has already been published.
