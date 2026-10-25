import test from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { unlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { syncPersonalDevelop } from './sync-personal-github-develop.mjs';

const sourceSha = 'a'.repeat(40);
const oldSha = 'b'.repeat(40);
const divergentSha = 'c'.repeat(40);
const token = 'personal-secret-token';
const config = {
  host: 'git.tak.gov',
  projectId: '6557',
  projectPath: 'aegorsuch/atak-plugin-physiology-dashboard',
  branch: 'develop',
  protected: 'true',
  pipelineSource: 'push',
  sha: sourceSha,
  jobToken: 'job-secret-token',
  personalToken: token,
};

function fixture(failures = {}) {
  const state = {
    remoteSha: failures.remoteSha ?? oldSha,
    fetchedSha: failures.fetchedSha ?? failures.remoteSha ?? oldSha,
    canonicalCalls: 0,
    personalCalls: 0,
    personalRepoCalls: 0,
    git: [],
    gitCommandArgs: [],
    gitEnvironments: [],
  };
  const fetcher = async (url, init) => {
    assert.equal(init.redirect, 'error');
    if (url.includes('git.tak.gov')) {
      assert.deepEqual(init.headers, { 'JOB-TOKEN': config.jobToken });
      state.canonicalCalls++;
      return new Response(JSON.stringify({
        id: failures.canonicalChangesAfterSync && state.canonicalCalls > 2 ? divergentSha : sourceSha,
      }));
    }
    if (url === 'https://api.github.com/repos/aegorsuch/atak-plugin-physiology-dashboard') {
      assert.equal(init.headers.Authorization, `Bearer ${token}`);
      state.personalRepoCalls++;
      return new Response(JSON.stringify({
        full_name: 'aegorsuch/atak-plugin-physiology-dashboard',
        visibility: 'public',
        archived: false,
        default_branch: 'develop',
      }));
    }
    assert.equal(url, 'https://api.github.com/repos/aegorsuch/atak-plugin-physiology-dashboard/branches/develop');
    assert.equal(init.headers.Authorization, `Bearer ${token}`);
    state.personalCalls++;
    return new Response(JSON.stringify({ commit: { sha: state.remoteSha } }));
  };
  const run = (command, args, gitOptions) => {
    if (command !== 'git') throw new Error('Unexpected command');
    const gitArgs = args.slice(4);
    state.git.push(gitArgs);
    state.gitCommandArgs.push(args);
    state.gitEnvironments.push(gitOptions.env);
    switch (gitArgs[0]) {
      case 'rev-parse':
        if (gitArgs[1] === 'HEAD') return `${failures.checkoutSha ?? sourceSha}\n`;
        return gitArgs[1] === '--is-shallow-repository' ? 'false\n' : `${state.fetchedSha}\n`;
      case 'fetch':
        if (failures.fetchMismatch) state.fetchedSha = divergentSha;
        else state.fetchedSha = state.remoteSha;
        return '';
      case 'merge-base':
        if (failures.divergent) throw Object.assign(new Error('not ancestor'), { status: 1 });
        return '';
      case 'push':
        if (failures.pushFails) throw Object.assign(new Error('push rejected'), { status: 1 });
        state.remoteSha = sourceSha;
        return 'ok\n';
      default:
        throw new Error(`Unexpected git ${args[0]}`);
    }
  };
  return { state, fetcher, run };
}

test('already synchronized mirror is verified without any Git writes', async () => {
  const current = fixture({ remoteSha: sourceSha });
  const result = await syncPersonalDevelop(config, current);
  assert.deepEqual(result, { synchronized: false, sha: sourceSha });
  assert.equal(current.state.git.length, 0);
  assert.equal(current.state.canonicalCalls, 2);
  assert.equal(current.state.personalCalls, 2);
  assert.equal(current.state.personalRepoCalls, 2);
});

test('personal develop uses only a non-force fast-forward refspec, then rechecks both tips', async () => {
  const fastForward = fixture();
  const result = await syncPersonalDevelop(config, fastForward);
  assert.deepEqual(result, { synchronized: true, sha: sourceSha });
  const push = fastForward.state.git.find(args => args[0] === 'push');
  assert.ok(push);
  assert.deepEqual(push.slice(1), [
    '--no-follow-tags', '--recurse-submodules=no',
    'https://github.com/aegorsuch/atak-plugin-physiology-dashboard.git',
    `${sourceSha}:refs/heads/develop`,
  ]);
  assert.ok(!push.includes('--force'));
  assert.ok(!push.some(arg => arg.includes('refs/tags')));
  assert.equal(fastForward.state.remoteSha, sourceSha);
  assert.equal(fastForward.state.canonicalCalls, 3);
  assert.equal(fastForward.state.personalCalls, 2);
  assert.equal(fastForward.state.personalRepoCalls, 2);
  const env = fastForward.state;
  assert.ok(env.git.some(args => args[0] === 'fetch' &&
    args.includes('--no-tags') && args.includes('--no-recurse-submodules')));
});

test('stale pipelines are rejected before Git commands', async () => {
  const stale = fixture();
  await assert.rejects(syncPersonalDevelop({ ...config, sha: divergentSha }, stale),
    /no longer canonical/);
  assert.equal(stale.state.git.length, 0);
});

test('a mismatched local checkout is rejected before fetching or pushing', async () => {
  const mismatch = fixture({ checkoutSha: divergentSha });
  await assert.rejects(syncPersonalDevelop(config, mismatch), /checkout HEAD does not match/);
  assert.ok(!mismatch.state.git.some(args => ['fetch', 'push'].includes(args[0])));
});

test('divergent personal branch is never force-pushed or rewritten', async () => {
  const divergent = fixture({ divergent: true });
  await assert.rejects(syncPersonalDevelop(config, divergent),
    /not an ancestor.*refusing non-fast-forward/);
  assert.ok(!divergent.state.git.some(args => args[0] === 'push'));
  assert.equal(divergent.state.remoteSha, oldSha);
});

test('a mirror branch change between API read and fetch is rejected', async () => {
  const changed = fixture({ fetchMismatch: true });
  await assert.rejects(syncPersonalDevelop(config, changed), /changed while it was being fetched/);
  assert.ok(!changed.state.git.some(args => args[0] === 'push'));
});

test('push rejection is surfaced and never retried with force', async () => {
  const rejected = fixture({ pushFails: true });
  await assert.rejects(syncPersonalDevelop(config, rejected), /git push failed/);
  assert.ok(rejected.state.git.some(args => args[0] === 'push'));
  assert.ok(!rejected.state.git.some(args => args.includes('--force')));
  assert.equal(rejected.state.remoteSha, oldSha);
});

test('a changed canonical head after sync blocks release continuation', async () => {
  const advanced = fixture({ canonicalChangesAfterSync: true });
  await assert.rejects(syncPersonalDevelop(config, advanced), /advanced during personal mirror sync/);
  assert.equal(advanced.state.remoteSha, sourceSha);
});

test('only protected canonical develop push pipelines with separate personal credentials sync', async () => {
  for (const patch of [
    { host: 'example.com' },
    { projectId: '1' },
    { projectPath: 'someone/repo' },
    { branch: 'feature' },
    { protected: 'false' },
    { pipelineSource: 'web' },
    { personalToken: '' },
    { debugTrace: 'true' },
  ]) {
    const blocked = fixture({ remoteSha: sourceSha });
    await assert.rejects(syncPersonalDevelop({ ...config, ...patch }, blocked));
    assert.equal(blocked.state.git.length, 0);
  }
});

test('personal credentials are repository-scoped and Git uses isolated safe configuration', async () => {
  const isolated = fixture();
  await syncPersonalDevelop(config, isolated);
  const encoded = Buffer.from(`x-access-token:${token}`).toString('base64');
  const expectedPrefix = [
    '-c', 'credential.helper=',
    '-c', 'http.extraheader=',
  ];
  for (let i = 0; i < isolated.state.gitEnvironments.length; i++) {
    const env = isolated.state.gitEnvironments[i];
    const args = isolated.state.gitCommandArgs[i];
    assert.deepEqual(args.slice(0, expectedPrefix.length), expectedPrefix);
    assert.ok(!args.join(' ').includes(token));
    assert.ok(!args.join(' ').includes(config.jobToken));
    assert.equal(env.GIT_CONFIG_NOSYSTEM, '1');
    assert.ok(env.GIT_CONFIG_GLOBAL);
    assert.equal(env.GIT_TERMINAL_PROMPT, '0');
    assert.ok(!Object.values(env).includes(token));
    assert.equal(env.GIT_CONFIG_COUNT, '3');
    assert.equal(env.GIT_CONFIG_KEY_0, 'http.followRedirects');
    assert.equal(env.GIT_CONFIG_VALUE_0, 'false');
    assert.equal(env.GIT_CONFIG_KEY_1, 'core.hooksPath');
    assert.equal(env.GIT_CONFIG_VALUE_1, process.platform === 'win32' ? 'NUL' : '/dev/null');
    assert.equal(env.GIT_CONFIG_KEY_2,
      'http.https://github.com/aegorsuch/atak-plugin-physiology-dashboard.git.extraheader');
    assert.equal(env.GIT_CONFIG_VALUE_2, `Authorization: Basic ${encoded}`);
  }
  assert.ok(isolated.state.git.every(args => !args.join(' ').includes(token)));
});

test('real Git matches the auth header only to the personal GitHub repository URL', () => {
  const header = 'Authorization: Basic dGVzdA==';
  const globalConfig = join(tmpdir(), `dashboard-git-config-${process.pid}-${Date.now()}`);
  writeFileSync(globalConfig, '[http]\n\textraheader = Authorization: Basic bGVnYWN5\n');
  try {
    const env = {
      ...process.env,
      GIT_CONFIG_NOSYSTEM: '1',
      GIT_CONFIG_GLOBAL: globalConfig,
      GIT_CONFIG_COUNT: '3',
      GIT_CONFIG_KEY_0: 'http.followRedirects',
      GIT_CONFIG_VALUE_0: 'false',
      GIT_CONFIG_KEY_1: 'core.hooksPath',
      GIT_CONFIG_VALUE_1: process.platform === 'win32' ? 'NUL' : '/dev/null',
      GIT_CONFIG_KEY_2: 'http.https://github.com/aegorsuch/atak-plugin-physiology-dashboard.git.extraheader',
      GIT_CONFIG_VALUE_2: header,
    };
    const git = args => execFileSync('git', [
      '-c', 'credential.helper=', '-c', 'http.extraheader=',
      'config', '--get-urlmatch', 'http.extraheader', ...args,
    ], { encoding: 'utf8', env });
    assert.equal(git(['https://github.com/aegorsuch/atak-plugin-physiology-dashboard.git']).trim(), header);
    assert.equal(git(['https://git.tak.gov/aegorsuch/project.git']).trim(), '');
  } finally {
    unlinkSync(globalConfig);
  }
});
