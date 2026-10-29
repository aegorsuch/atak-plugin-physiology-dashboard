import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const yaml = readFileSync(new URL('../.gitlab-ci.yml', import.meta.url), 'utf8').replace(/\r\n/g, '\n');
const jobs = Object.fromEntries([...yaml.matchAll(/^(\w+):\r?\n((?:[ \t]+[^\n]*\n|\r?\n)*)/gm)]
  .map(([, name, body]) => [name, body]));
const rule = '$CI_SERVER_HOST == "git.tak.gov" && $CI_PROJECT_ID == "6557" && ' +
  '$CI_PROJECT_PATH == "aegorsuch/atak-plugin-physiology-dashboard" && ' +
  '$CI_COMMIT_BRANCH == "main" && $CI_COMMIT_REF_PROTECTED == "true" && $CI_PIPELINE_SOURCE == "push"';

test('build and sole publisher require protected canonical main pushes without an opt-in flag', () => {
  assert.deepEqual(Object.keys(jobs), [
    'include', 'release_build_and_sign', 'test_github_release_publisher', 'publish_github_pipeline_release',
  ]);
  for (const name of ['release_build_and_sign', 'publish_github_pipeline_release']) {
    assert.equal(jobs[name].split('  rules:\n')[1]?.trim(), `- if: '${rule}'`);
  }
  assert.doesNotMatch(yaml, /Ascent-Integrated-Tech|GITHUB_RELEASE_TOKEN|PUBLISH_PERSONAL_GITHUB|develop/);
});

test('publisher stays post-success, serialized, personal-only, and syncs main before publishing', () => {
  const publisher = jobs.publish_github_pipeline_release;
  assert.match(publisher, /stage: \.post/);
  assert.match(publisher, /dependencies:\n    - release_build_and_sign/);
  assert.match(publisher, /resource_group: dashboard-github-rolling-release/);
  assert.match(publisher, /interruptible: false/);
  assert.match(publisher, /environment:\n    name: github-personal-rolling-release/);
  assert.match(publisher, /GITHUB_RELEASE_REPOSITORY: "aegorsuch\/atak-plugin-physiology-dashboard"/);
  assert.match(publisher, /GIT_DEPTH: "0"/);
  assert.match(publisher, /- node ci\/sync-personal-github-main\.mjs\n    - node ci\/publish-github-release\.mjs/);
  assert.match(publisher, /CI_DEBUG_TRACE/);
  assert.match(publisher, /apksigner aapt git/);
  assert.doesNotMatch(publisher, /allow_failure: true|when: always|needs:/);
  assert.match(jobs.release_build_and_sign, /extends: \.build_and_sign_jdk17/);
  assert.match(jobs.release_build_and_sign, /variants: "civ"/);
  assert.match(jobs.test_github_release_publisher, /stage: test/);
  assert.match(jobs.test_github_release_publisher, /node --test ci\/\*\.test\.mjs/);
  assert.doesNotMatch(jobs.test_github_release_publisher, /allow_failure: true|rules:/);
});
