import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readdirSync, readFileSync, lstatSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

export const REPO = 'Ascent-Integrated-Tech/biometrics-atak-plugin';
export const TAG = 'tak-gov-pipeline';
export const TITLE = 'TAK.gov Forge CI/CD Pipeline Release';
const PROJECT = '6557';
const SOURCE = 'aegorsuch/atak-plugin-physiology-dashboard';
const OWNER = 'takgov-dashboard-v1';
const MANAGED_START = '<!-- physiology-dashboard-rolling-release:managed -->';
const MANAGED_END = '<!-- /physiology-dashboard-rolling-release:managed -->';
const digest = bytes => createHash('sha256').update(bytes).digest('hex');
const requireThat = (condition, message) => {
  if (!condition) throw new Error(message);
};

export function configuration(env) {
  requireThat(!['true', '1', 'yes'].includes((env.CI_DEBUG_TRACE ?? '').toLowerCase()),
    'Disable CI_DEBUG_TRACE before publishing');
  requireThat(env.CI_SERVER_HOST === 'git.tak.gov' &&
    env.CI_PROJECT_ID === PROJECT && env.CI_PROJECT_PATH === SOURCE &&
    env.CI_COMMIT_BRANCH === 'develop' && env.CI_COMMIT_REF_PROTECTED === 'true' &&
    env.CI_PIPELINE_SOURCE === 'push', 'Only protected canonical develop push pipelines may publish');
  for (const key of ['CI_PIPELINE_ID', 'CI_PIPELINE_IID', 'ARTIFACTS_JOB_ID']) {
    requireThat(/^[1-9]\d*$/.test(env[key] ?? '') && Number.isSafeInteger(Number(env[key])),
      `Invalid ${key}`);
  }
  requireThat(/^[a-f0-9]{40}$/.test(env.CI_COMMIT_SHA ?? ''), 'Invalid source SHA');
  requireThat(/^[a-f0-9]{64}$/.test(env.TAK_SIGNER_SHA256 ?? ''), 'Configure reviewed TAK_SIGNER_SHA256');
  requireThat(env.GITHUB_RELEASE_TOKEN && env.CI_JOB_TOKEN, 'Required CI credentials are missing');
  return {
    iid: Number(env.CI_PIPELINE_IID), pipeline: env.CI_PIPELINE_ID,
    job: env.ARTIFACTS_JOB_ID, sha: env.CI_COMMIT_SHA,
    signer: env.TAK_SIGNER_SHA256, token: env.GITHUB_RELEASE_TOKEN,
    jobToken: env.CI_JOB_TOKEN,
  };
}

export function inspectApk(directory, signer, run = execFileSync) {
  const candidates = readdirSync(directory).filter(name =>
    /^ATAK-Plugin-atak-plugin-physiology-dashboard-.+-civ-release\.apk$/.test(name));
  requireThat(candidates.length === 1, 'Expected exactly one dashboard CIV release APK');
  const file = resolve(directory, candidates[0]);
  requireThat(lstatSync(file).isFile(), 'APK must be a regular file, not a symlink');
  const signatures = run('apksigner', ['verify', '--verbose', '--print-certs', file],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  const certificates = [...signatures.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]+)$/gm)];
  requireThat(certificates.length === 1 && certificates[0][1] === signer,
    'APK signer does not match the approved TAK signing certificate');
  requireThat(/Verified using v[23] scheme .*: true/.test(signatures), 'APK requires v2 or v3 signing');
  const badging = run('aapt', ['dump', 'badging', file],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] });
  requireThat(/^package: name='com\.aitech\.atak\.biometrics\.plugin' /m.test(badging) &&
    !/^application-debuggable/m.test(badging), 'Wrong package or debuggable APK');
  const version = badging.match(/^package: .*versionName='([^']+)'/m)?.[1];
  requireThat(version && /^[A-Za-z0-9 .()[\]_-]+$/.test(version), 'Invalid APK version');
  const versionCode = badging.match(/^package: .*versionCode='([1-9]\d*)'/m)?.[1];
  requireThat(versionCode, 'Invalid APK version code');
  const bytes = readFileSync(file);
  requireThat(bytes.length > 0, 'Empty APK');
  return { bytes, hash: digest(bytes), version, versionCode };
}

export function transport(config, fetcher = fetch) {
  async function request(url, method = 'GET', data, binary = false, allow404 = false) {
    const target = new URL(url);
    requireThat(target.protocol === 'https:' &&
      ['api.github.com', 'uploads.github.com', 'git.tak.gov'].includes(target.hostname),
    'Untrusted API destination');
    const headers = target.hostname === 'git.tak.gov'
      ? { 'JOB-TOKEN': config.jobToken }
      : { Authorization: `Bearer ${config.token}`, 'X-GitHub-Api-Version': '2022-11-28',
        Accept: binary ? 'application/octet-stream' : 'application/vnd.github+json' };
    if (data !== undefined) headers['Content-Type'] = Buffer.isBuffer(data)
      ? 'application/vnd.android.package-archive' : 'application/json';
    let response = await fetcher(url, {
      method, headers, redirect: 'manual', signal: AbortSignal.timeout(300_000),
      body: data === undefined ? undefined : Buffer.isBuffer(data) ? data : JSON.stringify(data),
    });
    if (binary && response.status === 302) {
      const location = new URL(response.headers.get('location'));
      requireThat(location.protocol === 'https:' &&
        location.hostname === 'release-assets.githubusercontent.com', 'Untrusted asset redirect');
      response = await fetcher(location, { redirect: 'error', signal: AbortSignal.timeout(300_000) });
    }
    if (allow404 && response.status === 404) return null;
    requireThat(response.ok, `API ${method} ${target.hostname}${target.pathname} failed (${response.status})`);
    if (response.status === 204) return null;
    return binary ? Buffer.from(await response.arrayBuffer()) : response.json();
  }
  return {
    github: (path, method, data, binary, allow404) =>
      request(`https://api.github.com/repos/${REPO}${path}`, method, data, binary, allow404),
    upload: (id, name, label, bytes) => request(
      `https://uploads.github.com/repos/${REPO}/releases/${id}/assets?${new URLSearchParams({ name, label })}`,
      'POST', bytes),
    currentBranch: () => request(`https://git.tak.gov/api/v4/projects/${PROJECT}/repository/branches/develop`),
  };
}

function marker(body) {
  const match = (body ?? '').match(/<!-- takgov-dashboard-v1 (\{[^\n]+\}) -->/);
  requireThat(match, 'Existing rolling release is not publisher-owned');
  const state = JSON.parse(match[1]);
  requireThat(state.project === PROJECT && Number.isSafeInteger(state.iid) && state.iid > 0 &&
    /^[a-f0-9]{40}$/.test(state.sha) && /^[a-f0-9]{64}$/.test(state.hash),
  'Invalid rolling release ownership marker');
  return state;
}

function owned(asset, state) {
  const legacy = asset.name.match(/^takgov-6557-([1-9]\d*)-([a-f0-9]{64})\.apk$/);
  if (legacy) return asset.label === `takgov:${PROJECT}:${legacy[1]}:${legacy[2]}` ||
    asset.label === `Download signed Physiology Dashboard APK (pipeline p${legacy[1]})` ||
    new RegExp(`^Physiology-Dashboard-[0-9][A-Za-z0-9._-]*-p${legacy[1]}\\.apk$`).test(asset.label ?? '');
  const readable = asset.name.match(/^Physiology-Dashboard-[0-9][A-Za-z0-9._-]*-p([1-9]\d*)\.apk$/);
  return readable && state?.asset === asset.name && state.iid === Number(readable[1]) &&
    asset.label === asset.name;
}

export function releaseMetadata(config, apk, previousBody = '', assetName) {
  const version = apk.version.match(/^([0-9][A-Za-z0-9._-]*) \([a-f0-9]+\) - \[[0-9.]+\]$/)?.[1];
  requireThat(version, 'Invalid dashboard APK version format');
  requireThat(/^[1-9]\d*$/.test(apk.versionCode ?? ''), 'Invalid APK version code');
  const buildVersion = version.endsWith(`-p${config.iid}`) ? version : `${version}-p${config.iid}`;
  const readableName = `Physiology-Dashboard-${buildVersion}.apk`;
  const name = assetName ?? readableName;
  const title = `${TITLE} (${buildVersion})`;
  const owner = `<!-- ${OWNER} ${JSON.stringify({
    project: PROJECT, iid: config.iid, sha: config.sha, hash: apk.hash, asset: name,
  })} -->`;
  const section = [
    MANAGED_START,
    '',
    'Automatically published signed Physiology Dashboard CIV stable develop-channel build.',
    '',
    `- APK version: \`${apk.version}\` (versionCode \`${apk.versionCode}\`)`,
    `- Build: GitLab pipeline \`${config.pipeline}\` (project pipeline IID \`${config.iid}\`)`,
    `- Pipeline: [#${config.pipeline}](https://git.tak.gov/${SOURCE}/-/pipelines/${config.pipeline})`,
    `- Source commit: [\`${config.sha}\`](https://git.tak.gov/${SOURCE}/-/commit/${config.sha}) on TAK.gov \`develop\``,
    `- Signing job: [#${config.job}](https://git.tak.gov/${SOURCE}/-/jobs/${config.job})`,
    `- SHA-256: \`${apk.hash}\``,
    '',
    `[Download signed APK](https://github.com/${REPO}/releases/download/${TAG}/${name})`,
    '',
    'Stable develop distribution; this is not TAK production-repository approval.',
    'GitHub **Source code** archives use the fixed anchor tag; the source SHA above is authoritative.',
    owner,
    MANAGED_END,
  ].join('\n');
  let body;
  if (previousBody.includes(MANAGED_START) || previousBody.includes(MANAGED_END)) {
    requireThat(previousBody.split(MANAGED_START).length === 2 &&
      previousBody.split(MANAGED_END).length === 2 &&
      previousBody.indexOf(MANAGED_START) < previousBody.indexOf(MANAGED_END),
    'Invalid managed release notes boundaries');
    body = previousBody.slice(0, previousBody.indexOf(MANAGED_START)) + section +
      previousBody.slice(previousBody.indexOf(MANAGED_END) + MANAGED_END.length);
  } else {
    // Migrate only the exact legacy generated block; retain surrounding manual notes.
    const legacy = /Signed Physiology Dashboard CIV release: [^\n]+\n\nCanonical source: https:\/\/git\.tak\.gov\/aegorsuch\/atak-plugin-physiology-dashboard\/-\/commit\/[a-f0-9]{40}\nPipeline: https:\/\/git\.tak\.gov\/aegorsuch\/atak-plugin-physiology-dashboard\/-\/pipelines\/[1-9]\d*\nSigning job: https:\/\/git\.tak\.gov\/aegorsuch\/atak-plugin-physiology-dashboard\/-\/jobs\/[1-9]\d*\nSHA-256: [a-f0-9]{64}\n\nStable develop distribution; this is not TAK production-repository approval\.\n<!-- takgov-dashboard-v1 \{[^\n]+\} -->/;
    if (legacy.test(previousBody)) {
      body = previousBody.replace(legacy, () => section);
    } else {
      const withoutMarker = previousBody.replace(/<!-- takgov-dashboard-v1 \{[^\n]+\} -->/, '');
      body = withoutMarker + (withoutMarker ? '\n\n' : '') + section;
    }
  }
  return { title, body, label: readableName, name };
}

async function assets(api, id) {
  const result = [];
  for (let page = 1; ; page++) {
    const batch = await api.github(`/releases/${id}/assets?per_page=100&page=${page}`);
    result.push(...batch);
    if (batch.length < 100) return result;
  }
}

export async function publish(config, apk, api) {
  requireThat(apk.version.includes(config.sha.slice(0, 8)), 'APK version does not match pipeline SHA');
  const metadata = releaseMetadata(config, apk);
  const fresh = async () => requireThat((await api.currentBranch()).commit.id === config.sha,
    'Source SHA is no longer canonical develop HEAD');
  await fresh();
  // The fixed tag is provisioned separately; never move or create source refs.
  await api.github(`/git/ref/tags/${TAG}`);
  let release = await api.github(`/releases/tags/${TAG}`, 'GET', undefined, false, true);
  if (release) {
    requireThat(release.tag_name === TAG, 'Unexpected rolling release tag');
    const previous = marker(release.body);
    if (previous.iid > config.iid) return { skipped: 'Newer pipeline already published' };
    requireThat(previous.iid !== config.iid ||
      (previous.sha === config.sha && previous.hash === apk.hash), 'Pipeline identity collision');
  } else {
    release = await api.github('/releases', 'POST', {
      tag_name: TAG, name: metadata.title, draft: true, prerelease: false,
      body: `<!-- ${OWNER} ${JSON.stringify({
        project: PROJECT, iid: config.iid, sha: config.sha, hash: apk.hash,
      })} -->`,
    });
  }
  const legacyName = `takgov-${PROJECT}-${config.iid}-${apk.hash}.apk`;
  const existingAssets = await assets(api, release.id);
  const existing = existingAssets.filter(item =>
    item.name === metadata.name || item.name === legacyName);
  requireThat(existing.length <= 1, 'Multiple assets for this pipeline; inspect manually');
  const name = existing[0]?.name ?? metadata.name;
  const label = metadata.label;
  const currentState = { iid: config.iid, hash: apk.hash, asset: name };
  let asset = existing[0];
  if (asset) {
    requireThat(owned(asset, currentState) && asset.state === 'uploaded' && asset.size === apk.bytes.length,
      'Existing candidate asset is incomplete or not publisher-owned; inspect manually');
  } else {
    asset = await api.upload(release.id, name, label, apk.bytes);
  }
  requireThat(asset.name === name && owned(asset, currentState) &&
    asset.state === 'uploaded' && asset.size === apk.bytes.length, 'Upload metadata mismatch');
  const downloaded = await api.github(`/releases/assets/${asset.id}`, 'GET', undefined, true);
  requireThat(downloaded.length === apk.bytes.length && digest(downloaded) === apk.hash,
    'Uploaded APK download verification failed; old assets retained');
  await fresh();
  const live = await api.github(`/releases/${release.id}`);
  const state = marker(live.body);
  requireThat(live.tag_name === TAG && state.iid <= config.iid &&
    (state.iid !== config.iid || (state.sha === config.sha && state.hash === apk.hash)),
  'Rolling release changed during upload');
  const { title, body } = releaseMetadata(config, apk, live.body, name);
  if (asset.label !== label) {
    await api.github(`/releases/assets/${asset.id}`, 'PATCH', { label });
  }
  await api.github(`/releases/${release.id}`, 'PATCH', {
    name: title, body, draft: false, prerelease: false, make_latest: 'true',
  });
  const confirmed = await api.github(`/releases/${release.id}`);
  requireThat(confirmed.name === title && confirmed.body === body && confirmed.tag_name === TAG &&
    confirmed.draft === false && confirmed.prerelease === false, 'Release readback mismatch; old assets retained');
  const listed = await assets(api, release.id);
  requireThat(listed.some(item => item.id === asset.id && owned(item, currentState) && item.label === label &&
    item.state === 'uploaded' && item.size === apk.bytes.length), 'New asset missing; old assets retained');
  await fresh();
  for (const old of listed) {
    if (old.id !== asset.id && owned(old, state)) {
      const iid = Number(old.name.match(/^takgov-6557-([1-9]\d*)-/)?.[1] ??
        old.name.match(/-p([1-9]\d*)\.apk$/)?.[1]);
      if (iid < config.iid) {
        const expectedHash = old.name.match(/^takgov-6557-[1-9]\d*-([a-f0-9]{64})\.apk$/)?.[1] ??
          state.hash;
        const oldBytes = await api.github(`/releases/assets/${old.id}`, 'GET', undefined, true);
        requireThat(digest(oldBytes) === expectedHash, 'Old owned asset hash mismatch; retained');
        await api.github(`/releases/assets/${old.id}`, 'DELETE');
      }
    }
  }
  return { published: name };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const config = configuration(process.env);
    const apk = inspectApk(process.cwd(), config.signer);
    console.log(await publish(config, apk, transport(config)));
  } catch (error) {
    // Do not print tool output, API response bodies, credentials or signed URLs.
    console.error(error instanceof Error && !('stderr' in error) ? error.message : 'APK inspection failed');
    process.exitCode = 1;
  }
}
