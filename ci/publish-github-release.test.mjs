import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { configuration, inspectApk, publish, releaseMetadata, transport, TAG, TITLE } from './publish-github-release.mjs';

const sha = 'a'.repeat(40);
const bytes = Buffer.from('mock signed APK');
const hash = createHash('sha256').update(bytes).digest('hex');
const apk = { bytes, hash, version: '0.35 (aaaaaaaa) - [5.3.0]', versionCode: '1792185138' };
const config = { iid: 3, pipeline: '100', job: '200', sha, signer: 'b'.repeat(64),
  token: 'mock-github', jobToken: 'mock-gitlab' };
const state = (iid = 2) => `<!-- takgov-dashboard-v1 ${JSON.stringify({
  project: '6557', iid, sha, hash,
})} -->`;
const asset = (iid, id) => ({
  id, name: `takgov-6557-${iid}-${hash}.apk`, label: `takgov:6557:${iid}:${hash}`,
  size: bytes.length, state: 'uploaded',
});

function mock(options = {}) {
  let release = options.absent ? null : {
    id: 10, tag_name: TAG, body: options.body ?? state(options.iid), name: TITLE, draft: false, prerelease: false,
  };
  let files = [asset(2, 20), { id: 21, name: 'manual.apk', label: null }];
  if (options.readableOld) {
    const name = 'Physiology-Dashboard-0.35-p2.apk';
    files[0] = { ...files[0], name, label: name };
    release.body = `<!-- takgov-dashboard-v1 ${JSON.stringify({
      project: '6557', iid: 2, sha, hash, asset: name,
    })} -->`;
  }
  if (options.retry) files.push(asset(3, 30));
  if (options.collision) files.push({ ...asset(3, 30), label: null });
  if (options.pagination) files = [...files,
    ...Array.from({ length: 100 }, (_, i) => ({ id: 100 + i, name: `manual-${i}.txt`, label: null }))];
  const calls = [];
  let freshness = 0;
  return {
    calls,
    get release() { return release; },
    get files() { return files; },
    currentBranch: async () => {
      freshness++;
      return { commit: { id: options.stale || (options.staleAfterUpload && freshness > 1)
        ? 'c'.repeat(40) : sha } };
    },
    upload: async (id, name, label, contents) => {
      calls.push(['upload', id]);
      if (options.uploadFails) throw new Error('upload failed');
      assert.deepEqual(contents, bytes);
      const uploaded = { ...asset(3, 30), name, label };
      files.push(uploaded);
      return uploaded;
    },
    github: async (path, method = 'GET', data, binary) => {
      calls.push([method, path, data]);
      if (path === `/git/ref/tags/${TAG}`) {
        if (options.noTag) throw new Error('missing tag');
        return { ref: `refs/tags/${TAG}` };
      }
      if (path === `/releases/tags/${TAG}`) return release && structuredClone(release);
      if (path === '/releases' && method === 'POST') {
        assert.equal(data.draft, true);
        release = { id: 10, ...data };
        return structuredClone(release);
      }
      if (path.startsWith('/releases/10/assets?')) {
        const page = Number(new URL(`https://example.test${path}`).searchParams.get('page'));
        return files.slice((page - 1) * 100, page * 100);
      }
      if (path.startsWith('/releases/assets/') && binary) return options.badDownload ||
        (options.badOldHash && path === '/releases/assets/20') ? Buffer.from('wrong') : bytes;
      if (path.startsWith('/releases/assets/') && method === 'PATCH') {
        const item = files.find(file => file.id === Number(path.split('/').at(-1)));
        Object.assign(item, data);
        return item;
      }
      if (path.startsWith('/releases/assets/') && method === 'DELETE') {
        if (options.deleteFails) throw new Error('delete failed');
        files = files.filter(item => item.id !== Number(path.split('/').at(-1)));
        return null;
      }
      if (path === '/releases/10' && method === 'PATCH') {
        if (options.patchFails) throw new Error('patch failed');
        release = { ...release, ...data };
        if (options.readbackFails) release.prerelease = true;
        return structuredClone(release);
      }
      if (path === '/releases/10') return structuredClone(release);
      throw new Error(`Unexpected mock request ${method} ${path}`);
    },
  };
}

test('configuration restricts publication to canonical protected develop push', () => {
  const env = { CI_SERVER_HOST: 'git.tak.gov', CI_PROJECT_ID: '6557',
    CI_PROJECT_PATH: 'aegorsuch/atak-plugin-physiology-dashboard', CI_COMMIT_BRANCH: 'develop',
    CI_COMMIT_REF_PROTECTED: 'true', CI_PIPELINE_SOURCE: 'push', CI_COMMIT_SHA: sha,
    CI_PIPELINE_ID: '100', CI_PIPELINE_IID: '3', ARTIFACTS_JOB_ID: '200',
    TAK_SIGNER_SHA256: config.signer, GITHUB_RELEASE_TOKEN: config.token, CI_JOB_TOKEN: config.jobToken };
  assert.equal(configuration(env).iid, 3);
  for (const key of Object.keys(env)) assert.throws(() => configuration({ ...env, [key]: '' }), undefined, key);
  for (const value of ['merge_request_event', 'web', 'schedule']) {
    assert.throws(() => configuration({ ...env, CI_PIPELINE_SOURCE: value }));
  }
  for (const value of ['true', 'TRUE', '1', 'yes']) {
    assert.throws(() => configuration({ ...env, CI_DEBUG_TRACE: value }), /CI_DEBUG_TRACE/);
  }
});

test('APK allowlist, signing pin, package, debug flag and ambiguity are enforced', () => {
  const directory = mkdtempSync(join(tmpdir(), 'dashboard-release-test-'));
  const name = 'ATAK-Plugin-atak-plugin-physiology-dashboard-0.35-aaaaaaaa-5.3.0-civ-release.apk';
  const signatures = `Verified using v2 scheme (APK Signature Scheme v2): true\nSigner #1 certificate SHA-256 digest: ${config.signer}\n`;
  const badging = `package: name='com.aitech.atak.biometrics.plugin' versionCode='1' versionName='${apk.version}'\n`;
  const run = (sign = signatures, badge = badging) => tool => tool === 'apksigner' ? sign : badge;
  try {
    assert.throws(() => inspectApk(directory, config.signer, run()));
    writeFileSync(join(directory, name), bytes);
    writeFileSync(join(directory, 'debug.apk'), bytes);
    writeFileSync(join(directory, 'private.jks'), bytes);
    assert.equal(inspectApk(directory, config.signer, run()).hash, hash);
    assert.equal(inspectApk(directory, config.signer, run()).versionCode, '1');
    assert.throws(() => inspectApk(directory, config.signer, run(signatures,
      badging.replace("versionCode='1'", "versionCode='invalid'"))));
    assert.throws(() => inspectApk(directory, 'c'.repeat(64), run()));
    assert.throws(() => inspectApk(directory, config.signer, run(signatures.replace(': true', ': false'))));
    assert.throws(() => inspectApk(directory, config.signer, run(signatures, `${badging}application-debuggable\n`)));
    assert.throws(() => inspectApk(directory, config.signer, run(signatures, badging.replace('biometrics', 'other'))));
    assert.throws(() => inspectApk(directory, config.signer, () => { throw new Error('invalid signature'); }));
    writeFileSync(join(directory, name.replace('0.35', '0.36')), bytes);
    assert.throws(() => inspectApk(directory, config.signer, run()));
  } finally {
    rmSync(directory, { recursive: true });
  }
});

test('successful stable replacement verifies bytes before metadata and cleanup; unrelated assets survive', async () => {
  const api = mock({ pagination: true });
  await publish(config, apk, api);
  assert.equal(api.release.name, `${TITLE} (0.35-p3)`);
  assert.equal(api.release.prerelease, false);
  assert.equal(api.release.draft, false);
  assert.equal(api.files.length, 102);
  assert.ok(api.files.some(item => item.id === 21));
  assert.ok(!api.files.some(item => item.id === 20));
  const download = api.calls.findIndex(([method, path]) => method === 'GET' && path === '/releases/assets/30');
  const patch = api.calls.findIndex(([method]) => method === 'PATCH');
  const deletion = api.calls.findIndex(([method]) => method === 'DELETE');
  assert.ok(download < patch && patch < deletion);
  assert.ok(api.calls.filter(([method]) => method === 'DELETE').every(([, path]) => path === '/releases/assets/20'));
});

test('release formatting exposes artifact identity and provenance without ownership labels', () => {
  const metadata = releaseMetadata(config, apk);
  assert.equal(metadata.title, 'TAK.gov Forge CI/CD Pipeline Release (0.35-p3)');
  assert.match(metadata.body, /APK version: \*\*0\.35 \(aaaaaaaa\) - \[5\.3\.0\]\*\* \(versionCode \*\*1792185138\*\*\)/);
  assert.match(metadata.body, /GitLab pipeline \*\*100\*\* \(project pipeline \*\*3\*\*\)/);
  assert.ok(metadata.body.includes(`- Source commit: \`${sha}\` on TAK.gov \`develop\``));
  assert.ok(metadata.body.includes(`- SHA256: \`${hash}\``));
  assert.equal(metadata.name, 'Physiology-Dashboard-0.35-p3.apk');
  assert.match(metadata.body, /fixed release anchor tag/);
  assert.equal(metadata.body.replace(/<!--[\s\S]*?-->/g, '').trim(), [
    'Automatically published stable Physiology Dashboard CIV develop-channel build.',
    '',
    `- APK version: **${apk.version}** (versionCode **${apk.versionCode}**)`,
    '- Build: GitLab pipeline **100** (project pipeline **3**)',
    '- Pipeline: https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/pipelines/100',
    `- Source commit: \`${sha}\` on TAK.gov \`develop\``,
    `- SHA256: \`${hash}\``,
    '',
    'Install the APK above. GitHub\'s "Source code" archives come from the fixed release anchor tag, not from this build; the source commit above is authoritative and may not yet be mirrored to GitHub.',
  ].join('\n'));
  assert.equal(metadata.label, 'Physiology-Dashboard-0.35-p3.apk');
  assert.equal(releaseMetadata(config, { ...apk, version: '0.35-p3 (aaaaaaaa) - [5.3.0]' }).name,
    'Physiology-Dashboard-0.35-p3.apk');
  assert.ok(!metadata.label.includes(hash));
  assert.throws(() => releaseMetadata(config, { ...apk, versionCode: undefined }));
  assert.throws(() => releaseMetadata(config, { ...apk, version: 'guessed' }));
});

test('manual notes survive legacy migration and repeated managed updates exactly', () => {
  const legacy = [
    `Signed Physiology Dashboard CIV release: ${apk.version}`, '',
    `Canonical source: https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/commit/${sha}`,
    'Pipeline: https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/pipelines/100',
    'Signing job: https://git.tak.gov/aegorsuch/atak-plugin-physiology-dashboard/-/jobs/200',
    `SHA-256: ${hash}`, '',
    'Stable develop distribution; this is not TAK production-repository approval.',
    state(3),
  ].join('\n');
  const prefix = 'Manual introduction.\n\n';
  const suffix = '\n\nManual release instructions.\n';
  const first = releaseMetadata(config, apk, prefix + legacy + suffix);
  assert.ok(first.body.startsWith(prefix));
  assert.ok(first.body.endsWith(suffix));
  assert.equal(first.body.split('<!-- takgov-dashboard-v1 ').length, 2);
  assert.equal(releaseMetadata(config, apk, first.body).body, first.body);
  assert.throws(() => releaseMetadata(config, apk, first.body +
    '<!-- physiology-dashboard-rolling-release:managed -->'));
});

test('publishing preserves manual notes and never changes refs, asset names, URLs or bytes', async () => {
  const api = mock({ body: `Manual operating instructions.\n\n${state()}` });
  await publish(config, apk, api);
  assert.ok(api.release.body.startsWith('Manual operating instructions.\n\n'));
  assert.ok(api.files.some(item => item.name === 'Physiology-Dashboard-0.35-p3.apk'));
  const updates = api.calls.filter(([method]) => ['POST', 'PATCH'].includes(method));
  assert.ok(updates.every(([, path]) => !path.includes('/git/')));
  for (const [, path, data] of updates) {
    if (path.startsWith('/releases/assets/')) assert.deepEqual(Object.keys(data), ['label']);
  }
});

test('readable-label readback is required before old owned assets are removed', async () => {
  const api = mock({ retry: true });
  const original = api.github;
  api.github = async (path, method, data, ...rest) => {
    if (method === 'PATCH' && path === '/releases/assets/30') return {};
    return original(path, method, data, ...rest);
  };
  await assert.rejects(publish(config, apk, api), /New asset missing/);
  assert.ok(api.files.some(item => item.id === 20));
  assert.ok(!api.calls.some(([method]) => method === 'DELETE'));
});

test('readable old names require hidden ownership and hash verification before cleanup', async () => {
  const api = mock({ readableOld: true });
  await publish(config, apk, api);
  assert.ok(!api.files.some(item => item.id === 20));
  assert.ok(api.release.body.includes('"asset":"Physiology-Dashboard-0.35-p3.apk"'));
  const failed = mock({ readableOld: true, badOldHash: true });
  await assert.rejects(publish(config, apk, failed), /Old owned asset hash mismatch/);
  assert.ok(failed.files.some(item => item.id === 20));
  assert.ok(!failed.calls.some(([method]) => method === 'DELETE'));
  const unowned = mock({ readableOld: true });
  const original = unowned.github;
  unowned.github = async (...args) => {
    const result = await original(...args);
    if (args[0] === '/releases/10/assets?per_page=100&page=1') {
      return result.map(item => item.id === 20 ? { ...item, label: 'Manual APK' } : item);
    }
    return result;
  };
  await publish(config, apk, unowned);
  assert.ok(unowned.files.some(item => item.id === 20));
});

for (const failure of ['uploadFails', 'badDownload', 'patchFails', 'readbackFails', 'staleAfterUpload']) {
  test(`${failure} retains usable old APK and does not delete anything`, async () => {
    const api = mock({ [failure]: true });
    await assert.rejects(publish(config, apk, api));
    assert.ok(api.files.some(item => item.id === 20));
    assert.ok(!api.calls.some(([method]) => method === 'DELETE'));
  });
}

test('new release stays draft until verified; precreated tag is mandatory', async () => {
  const api = mock({ absent: true });
  await publish(config, apk, api);
  assert.equal(api.release.draft, false);
  assert.ok(api.calls.some(([method, path]) => method === 'POST' && path === '/releases'));
  const failed = mock({ absent: true, uploadFails: true });
  await assert.rejects(publish(config, apk, failed));
  assert.equal(failed.release.draft, true);
  const missing = mock({ noTag: true });
  await assert.rejects(publish(config, apk, missing));
  assert.ok(!missing.calls.some(([method]) => method === 'POST'));
});

test('older pipeline skips newer release; old SHA and wrong artifact fail before writes', async () => {
  const newer = mock({ iid: 4 });
  assert.ok((await publish(config, apk, newer)).skipped);
  assert.ok(!newer.calls.some(([method]) => ['POST', 'PATCH', 'DELETE', 'upload'].includes(method)));
  const stale = mock({ stale: true });
  await assert.rejects(publish(config, apk, stale));
  assert.equal(stale.calls.length, 0);
  await assert.rejects(publish(config, { ...apk, version: '0.35 (bbbbbbbb)' }, mock()));
});

test('retry reuses verified asset, rejects unowned collisions and identity changes', async () => {
  const retry = mock({ retry: true });
  await publish(config, apk, retry);
  assert.ok(!retry.calls.some(([method]) => method === 'upload'));
  const labelUpdate = retry.calls.find(([method, path]) =>
    method === 'PATCH' && path === '/releases/assets/30');
  assert.deepEqual(labelUpdate[2], { label: 'Physiology-Dashboard-0.35-p3.apk' });
  assert.equal(retry.files.find(item => item.id === 30).label, labelUpdate[2].label);
  assert.equal(retry.files.find(item => item.id === 30).name, `takgov-6557-3-${hash}.apk`);
  assert.ok(retry.release.body.includes(`"asset":"takgov-6557-3-${hash}.apk"`));
  await assert.rejects(publish(config, apk, mock({ collision: true })));
  await assert.rejects(publish(config, { ...apk, hash: 'f'.repeat(64) }, mock({ iid: 3 })));
  const unowned = mock();
  const original = unowned.github;
  unowned.github = async (...args) => args[0] === `/releases/tags/${TAG}`
    ? { id: 10, tag_name: TAG, body: 'Human release' } : original(...args);
  await assert.rejects(publish(config, apk, unowned));
});

test('cleanup failure surfaces explicitly after new APK is usable', async () => {
  const api = mock({ deleteFails: true });
  await assert.rejects(publish(config, apk, api), /delete failed/);
  assert.ok(api.files.some(item => item.id === 30));
  assert.equal(api.release.prerelease, false);
});

test('transport sends tokens only to their API, strips auth from approved download redirect', async () => {
  const seen = [];
  const api = transport(config, async (url, init) => {
    seen.push([String(url), init]);
    if (String(url).includes('/releases/assets/')) return new Response(null, {
      status: 302, headers: { location: 'https://release-assets.githubusercontent.com/mock' },
    });
    if (String(url).includes('release-assets.')) return new Response(bytes);
    return new Response(JSON.stringify({ id: sha }));
  });
  assert.deepEqual(await api.currentBranch(), { commit: { id: sha } });
  await api.github('/releases/assets/1', 'GET', undefined, true);
  assert.deepEqual(seen[0][1].headers, { 'JOB-TOKEN': config.jobToken });
  assert.equal(seen[0][0], 'https://git.tak.gov/api/v4/projects/6557/repository/commits/develop');
  assert.equal(seen[1][1].headers.Authorization, `Bearer ${config.token}`);
  assert.equal(seen[2][1].headers, undefined);
  const denied = transport(config, async () => new Response(null, {
    status: 302, headers: { location: 'https://evil.test/mock' },
  }));
  await assert.rejects(denied.github('/releases/assets/1', 'GET', undefined, true), /Untrusted/);
  const unauthorized = transport(config, async () => new Response('sensitive body', { status: 403 }));
  await assert.rejects(unauthorized.github('/releases'), error => !error.message.includes('sensitive'));
});
