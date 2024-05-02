import { Octokit } from "octokit";
import * as semver from 'semver';
import { exec, execSync } from 'child_process';
import * as util from 'node:util';
import mustache from 'mustache';
import fs from 'fs';
import process from 'process';
import minimist from 'minimist';

const execAsync = util.promisify(exec);

//-------------------------------------------------------------------------------------------------
// Helper to exit with an error message.
//-------------------------------------------------------------------------------------------------
const bail = (error: string) => {
  console.error(error);
  process.exit(-1);
};

//-------------------------------------------------------------------------------------------------
// Check that git user can be configured.
//-------------------------------------------------------------------------------------------------
const validateGitContext = async () => {
  try {
    let {stdout,stderr} = await execAsync(`git config user.name`);
    if (stdout.length > 0) {
      console.debug(`git user.name: ${stdout}`);
      return;
    }
  } catch (e) {
    // Ignore: git config user.name fails with error code 1 if the key is not set
  }

  const githubUser = process.env.GITHUB_USERNAME ?? "";
  const githubEmail = process.env.GITHUB_USER_EMAIL ?? "";

  console.debug(`Configure git: ${githubUser}<${githubEmail}>`);

  if (githubUser.length == 0 || githubEmail.length == 0) {
    bail("Your job doesn't have 'rtd-github-api' context assigned, define GITHUB_USERNAME and GITHUB_USER_EMAIL");
  }
  await execAsync(`git config --global user.name "${githubUser}"`);
  await execAsync(`git config --global user.email "${githubEmail}"`);
};

//-------------------------------------------------------------------------------------------------
// Create an URL file in the package, which points at specific GH release.
//-------------------------------------------------------------------------------------------------
const generateInternetShortcut = async (filename, artifact) => {
  const repoByArtifact = {
    "sync": rcVersionPublish ? "twilio-sync-ios-internal" : "twilio-sync-ios",
    "sync-lib": rcVersionPublish ? "twilio-sync-ios-internal" : "twilio-sync-ios",
  };
  const repo = repoByArtifact[artifact];
  await fs.promises.writeFile(filename, `[InternetShortcut]\nURL=https://github.com/twilio/${repo}/releases/tag/v${releaseVersion}`);
};

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// [main] Start processing
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

await validateGitContext();

const octokit = new Octokit({ auth: process.env.GITHUB_TOKEN });

// NB: meow crashes esrun, so use minimist instead...
const helpText = `Usage:
  npx esrun tools/release-pipeline/index.ts <release-version-tag>

Example:
  npx esrun tools/release-pipeline/index.ts release-sync-ios-4.0.0-rc555`;

const args = minimist(process.argv.slice(2));

// latest release as passed in
const releaseTag = args['_'].at(0);

if (!releaseTag) {
  console.log(helpText);
  process.exit(-1);
}

console.log(`Releasing version ${releaseTag}`);

//-------------------------------------------------------------------------------------------------
// Convert a CircleCI release tag into product name and version.
//-------------------------------------------------------------------------------------------------
const parseTag = (tag: string): { product: string, version: string } => {
  const reg = tag.match(/release-(.+)-(\d+\.\d+\.\d+.*)$/);
  if (reg == null) {
    bail(`failed to parse version '${tag}'`);
  }
  const product = reg[1];
  const version = reg[2];
  return { product, version };
};

const repoMap = {
  "sync-ios": "twilio-sync-ios",
  "sync-ios-rc": "twilio-sync-ios-internal",
};

//-------------------------------------------------------------------------------------------------
// Get a name of release git repo from product.
//-------------------------------------------------------------------------------------------------
const productToRepo = (product: string, rc: boolean): string => repoMap[product + (rc ? "-rc" : "")];

// Are we publishing an RC?
const rcVersionPublish = releaseTag.includes("-rc");
console.debug(`Publishing an RC: ${rcVersionPublish}`);

// Git tag prefix for searching
const releaseVersion = parseTag(releaseTag).version;
const product = parseTag(releaseTag).product;
const prefix = `release-${product}-`;

const publishRepo = productToRepo(product, rcVersionPublish);
console.debug(`Publishing to repo ${publishRepo}`);

// Get the root of the monorepo
const monorepoDir = (await execAsync("git rev-parse --show-toplevel")).stdout.trim();
console.debug(`monorepoDir: ${monorepoDir}`);

// Local directory for temporary files
const localDir = `${monorepoDir}/Local`;
// Local directory for documentation
const docsDir = `${localDir}/docs`;
// Local directory for packages
const packagesDir = `${localDir}/Package`;
// Local directory for downloaded rc artifacts
const downloadsDir = `${localDir}/Downloads`;
// Directory with pre-built xcframeworks
const xcFrameworkDir = `${monorepoDir}/ios/Local/Products`;

fs.rmSync(localDir, { recursive: true, force: true });
fs.mkdirSync(localDir, { recursive: true });

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// For release builds - download rc-artifacts from the rc gh-release.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

// Download into current folder all assets of the gh-release tagged with the `tag` in the `repo`
const downloadRcArtifacts = async (repo: string, tag: string) => {
  const release = await octokit.request('GET /repos/{owner}/{repo}/releases/tags/{tag}', {
    owner: 'twilio',
    repo: repo,
    tag: tag,
    headers: { 'X-GitHub-Api-Version': '2022-11-28' }
  });

  for (const asset of release.data.assets) {
    console.debug(`Downloading ${asset.name} from ${tag} in ${repo}`);

    await execAsync(`curl -L ` +
      `-H "Accept: application/octet-stream" ` +
      `-H "Authorization: Bearer ${process.env.GITHUB_TOKEN}" ` +
      `-H "X-GitHub-Api-Version: 2022-11-28" ` +
      `-o "${asset.name}" ` +
      `https://api.github.com/repos/twilio/${repo}/releases/assets/${asset.id}`);
  }
};

const extractRcArtifacts = async () => {
  // List zip files in downloadsDir
  const files = fs.readdirSync(downloadsDir).filter((x) => x.endsWith(".zip"));

  // Extract zip files and move .xcframework(s) into build folder
  for (const file of files) {
    console.debug(`Extracting ${file}`);

    const fileName = file.substring(0, file.length - 4); // cut .zip in the end
    const extractDir = `${downloadsDir}/${fileName}`;

    await execAsync(`unzip -q -o ${file} -d ${extractDir}`);

    fs.readdirSync(extractDir)
      .filter((x) => x.endsWith(".xcframework"))
      .forEach((xcframework) => {
        fs.rmSync(`${xcFrameworkDir}/${xcframework}`, { recursive: true, force: true });
        fs.cpSync(`${extractDir}/${xcframework}`, `${xcFrameworkDir}/${xcframework}`, { recursive: true })
      });
  }
}

// Check the release tag actually exist on current commit.
const currentCommitTags = (await execAsync("git tag --points-at HEAD")).stdout.split("\n")

if (!currentCommitTags.includes(releaseTag)) {
  bail(`Tag ${releaseTag} does not point at the current commit`);
}

if (!rcVersionPublish) {
  // Check the rc tag actually exist and there is single rc tag on the current commit.

  const sameVersionRcTags = currentCommitTags.filter((x) => x.startsWith(`${releaseTag}-rc`));

  if (sameVersionRcTags.length == 0) {
    bail(`Tag ${releaseTag} is not an RC, but there are no RC tags on the current commit`);
  }

  if (sameVersionRcTags.length > 1) {
    bail(`Tag ${releaseTag} is not an RC, but there are more than one RC tags on the current commit: ${sameVersionRcTags.join(", ")}`);
  }

  // In promote-to-release workflow we don't build any xcframeworks, instead here we
  // download binaries from corresponding RC gh-release and put them into the folder where
  // pre-built xcframeworks should be located.

  fs.mkdirSync(downloadsDir);
  process.chdir(downloadsDir);

  const rcVersion = parseTag(sameVersionRcTags[0]).version
  const rcRepo = productToRepo(product, true); // Take an rc repo for the same product.

  await downloadRcArtifacts(rcRepo, `v${rcVersion}`);
  await extractRcArtifacts();
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Find the current release tag amongst the tags for the same product in the repo.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

const tags = await execAsync(`git tag --list ${prefix}*`);

let versionTags = tags.stdout.split("\n").filter(x => x.length > 0);

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// If we are publishing a final release, ignore all RC tags - we want to generate changelog since
// the previous public release.
// If we are publishing an RC, then generate changelog since previous RC.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

if (!rcVersionPublish) {
  versionTags = versionTags.filter((x) => !x.includes('-rc'));
}
let versions = versionTags.map((x) => parseTag(x).version);
versions.sort(semver.compareBuild);

let index = -1;
for (let i = 0; i < versions.length; ++i) {
  if (versions[i] === releaseVersion) {
    index = i;
    console.debug(`Found version ${releaseVersion} in position ${i}`);
    break;
  }
}
if (index == -1) {
  bail("Didn't find release version tag, did you tag the release?");
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Generate changelog.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

const currVersion = versions[index];
const prevVersion = index > 0 ? versions[index - 1] : currVersion;

console.debug(`Generating changelog between versions ${prevVersion}..${currVersion}`);
const changelog = (await execAsync(`cd ${monorepoDir}; git-cliff ${prefix}${prevVersion}..${prefix}${currVersion}`)).stdout;
console.log(changelog);

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Update Package.swift file in the publish repo.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

process.chdir(localDir);
await execAsync(`git clone git@github.com:twilio/${publishRepo}.git`);
process.chdir(publishRepo);

// Get main git branch
const mainBranch = (await execAsync("git branch --show-current")).stdout.trim();

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// To allow supporting several major releases in parallel, the publishing repo has
// per-major-release branches. Checkout the one corresponding to this release.
// (Create one if it doesn't exist)
//
// The branches are in format `release-1.x`, `release-2.x` and so on.
// The `main` branch tracks the latest available release and is shown on repo's front page.
//
// Push update to the appropriate release branch.
// Update main branch if necessary.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

const versionMajor = semver.parse(releaseVersion).major;
console.debug(`Major version is ${versionMajor}`);

// get all release branches, find the biggest major version, consider it latest
let releaseBranches =
  (await execAsync(`git branch -a --format '%(refname:short)' --list 'release-*' --list 'origin/release-*'`)).stdout.split("\n");
// @todo: shall we make special treatment for release-0.x branches as release-0.1.y etc?

let sortedBranches = releaseBranches.map((x) => x.replace("origin/", "")
  .replace("release-", "")
  .replace(".x", ""))
  .filter((x) => x.length > 0)
  .map((x) => Number(x));

sortedBranches.sort();

const latestBranch = sortedBranches.reverse()[0];
const releaseBranch = `release-${versionMajor}.x`;
let updateLatest = versionMajor == latestBranch;

console.debug(`Latest branch is ${latestBranch}`);
console.debug(`Release branch is ${releaseBranch}`);

if (Number(versionMajor) == latestBranch + 1) {
  console.debug(`Creating next major release branch: ${releaseBranch}`);
  await execAsync(`git branch ${releaseBranch} ${mainBranch} && git push origin ${releaseBranch}`);
  updateLatest = true;
}

console.debug(`updateLatest is ${updateLatest}`);

console.debug(`Checking out ${releaseBranch}`);
await execAsync(`git checkout ${releaseBranch}`);

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Create a draft release
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

// # Make a release
// Similar to gh release create ${DRAFT_FLAG} -n "" -t "$PROJECT_NAME iOS $FULL_RELEASE_VERSION" v$FULL_RELEASE_VERSION
// but using Octokit:

const releaseTitles = {
  "sync-ios": "Twilio Sync Client",
  "sync-ios-rc": "Twilio Sync Client",
};

// Create release
console.debug(`Creating gh-release for v${releaseVersion}`);
const ghRelease = await octokit.request('POST /repos/{owner}/{repo}/releases', {
  owner: 'twilio',
  repo: publishRepo,
  tag_name: `v${releaseVersion}`,
  target_commitish: `${releaseBranch}`,
  name: `${releaseTitles[product]} iOS ${releaseVersion}`,
  body: changelog,
  draft: true, // Release is a draft until changelog is reviewed and approved
  prerelease: rcVersionPublish,
  generate_release_notes: false,
  headers: { 'X-GitHub-Api-Version': '2022-11-28' }
});

const uploadAssetToRelease = async (file: string) => {
  console.debug(`Uploading ${file} to gh-release: ${ghRelease.data.upload_url}`);

  const asset = await octokit.request(`POST ${ghRelease.data.upload_url}`, {
    name: file,
    data: fs.readFileSync(`${packagesDir}/${file}`),
    headers: { 'X-GitHub-Api-Version': '2022-11-28' }
  });

  return asset;
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Generate Package.swift
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

//-------------------------------------------------------------------------------------------------
// Check that hash returned does look like a hash.
//-------------------------------------------------------------------------------------------------
const validateHashFormat = (artifact: string, h: string) => {
  if (!h.match(/[a-z0-9]{32}/)) {
    bail(`Invalid checksum for ${artifact}: ${h}`);
  }  
};

const makeDownloadUrl = (packageArchive: string, apiUrl: string): string =>
  rcVersionPublish
    ? apiUrl + ".zip"
    : `https://github.com/twilio/${publishRepo}/releases/download/v${releaseVersion}/${packageArchive}`;

//-------------------------------------------------------------------------------------------------
// Prepare zip file to attach to the release.
// - put xcframework in zip file in specific folder
// - generate and place documentation in package and for gh-pages publishing
// - return checksum
//-------------------------------------------------------------------------------------------------
const packageSingle = async (PACKAGE_NAME: string, PACKAGE_FILE: string): string => {
  console.debug(`packageSingle: ${PACKAGE_NAME}`);

  const PACKAGE_DIR = `${packagesDir}/${PACKAGE_NAME}`;
  fs.mkdirSync(PACKAGE_DIR, { recursive: true });

  const xcFrameworkMap = {
    "sync": `${monorepoDir}/ios/TwilioSync/output/xcframeworks/TwilioSync.xcframework`,
    "sync-lib": `${monorepoDir}/sdk/sync/sync-android-kt/build/XCFrameworks/release/TwilioSyncLib.xcframework`
  };
  const xcFramework = xcFrameworkMap[PACKAGE_NAME];
  console.debug(`xcFramework: ${xcFramework}`);

  const packagesWithDocumentation = {
      "sync": {
        "project": "TwilioSync",
        "hostingBasePath": rcVersionPublish ? `releases/${releaseVersion}/docs` : `twilio-sync-ios/releases/${releaseVersion}/docs`,
      }
  }

  const hasDocs = PACKAGE_NAME in packagesWithDocumentation

  if (hasDocs) {
    console.debug(`Generating documentation for ${PACKAGE_NAME}`);

    const { project, hostingBasePath } = packagesWithDocumentation[PACKAGE_NAME]
    const docArchivePath = `${monorepoDir}/ios/${project}/DerivedData/Build/Products/Release-iphoneos`
    const docArchiveName = `${project}.doccarchive`

    // Generate documentation
    fs.rmSync(`${docArchivePath}/${docArchiveName}`, { recursive: true, force: true });
    await execAsync(`${monorepoDir}/BuildScripts/build-docs-kotlin-ios.sh ${project} ${hostingBasePath}`, { cwd: monorepoDir });

    // Copy documentation into PACKAGE_DIR
    fs.cpSync(`${docArchivePath}/${docArchiveName}`, `${PACKAGE_DIR}/${docArchiveName}`, { recursive: true });

    // Also put documentation to gh-pages branch of release repo -- just save to a special dir here,
    // so we can pick it up later in the gh-pages publishing step in release repo.
    fs.cpSync(`${docArchivePath}/${docArchiveName}`, docsDir, { recursive: true });
  }

  await generateInternetShortcut(`${PACKAGE_DIR}/changelog.url`, PACKAGE_NAME);

  // Copy xcframework into PACKAGE_DIR
  const frameworkName = xcFramework.split('/').pop();
  fs.cpSync(xcFramework, `${PACKAGE_DIR}/${frameworkName}`, { recursive: true });

  // Copy license notices
  const noticesFiles = {
    "sync": "ios-sync.NOTICES.txt",
    "sync-lib": "ios-sync.NOTICES.txt",
  }

  fs.cpSync(`${monorepoDir}/sbom/generated/${noticesFiles[PACKAGE_NAME]}`, `${PACKAGE_DIR}/NOTICE.txt`);

  // Archive framework package
  await execAsync(`zip -yr ${packagesDir}/${PACKAGE_FILE} .`, { cwd: PACKAGE_DIR })

  // Calculate and remember checksum
  const CHECKSUM = (await execAsync(`shasum -a 256 ${packagesDir}/${PACKAGE_FILE} | awk '{print $1}'`)).stdout.trim();
  validateHashFormat(PACKAGE_NAME, CHECKSUM);

  return CHECKSUM;
};

//-------------------------------------------------------------------------------------------------
// Create Package.swift for Sync
// Package all necessary libraries, get their hashes and create package URLs.
//-------------------------------------------------------------------------------------------------
const packageSync = async () => {
  const syncPackage = "twilio-sync-" + releaseVersion + ".zip";
  const syncLibPackage = "twilio-sync-lib-" + releaseVersion + ".zip";
  const SDK_CHECKSUM = await packageSingle("sync", syncPackage);
  const LIB_CHECKSUM = await packageSingle("sync-lib", syncLibPackage);
  const PRODUCT = "Sync";

  const syncAsset = await uploadAssetToRelease(syncPackage);
  const syncLibAsset = await uploadAssetToRelease(syncLibPackage);

  const SDK_URL = makeDownloadUrl(syncPackage, syncAsset.data.url);
  const LIB_URL = makeDownloadUrl(syncLibPackage, syncLibAsset.data.url);

  const template = await fs.promises.readFile(path.resolve(__dirname, "template-Package.swift"), 'UTF-8');
  const packageSwift = mustache.render(template, { PRODUCT, SDK_URL, SDK_CHECKSUM, LIB_URL, LIB_CHECKSUM });

  await fs.promises.writeFile("Package.swift", packageSwift);
};

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Generate appropriate Package.swift for the package we're publishing
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

if (product == "sync-ios") {
  await packageSync();
} else {
  bail(`Have no idea how to distribute product ${product}`);
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Push new Package.swift
// Tag it with release version like v2.5.1
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

// git status must be non-empty with 1 file changed
const gitStatusOutput = (await execAsync("git status --short")).stdout.trim();
// stdout must match "^M Package.swift$"
if (!gitStatusOutput.match(/^M Package.swift$/)) {
  bail(`Package.swift is not found in git status output: ${gitStatusOutput}`);
}

await execAsync(`git add Package.swift ` +
    `&& git commit -m 'Update Package.swift to ${releaseVersion}' ` +
    `&& git tag v${releaseVersion} ` +
    `&& git push origin ${releaseBranch} ` +
    `&& git push origin --tags`);

if (updateLatest) {
  console.log(`Updating ${mainBranch} branch`);
  await execAsync(`git checkout ${mainBranch} && git merge --ff-only ${releaseBranch} && git push origin ${mainBranch}`);
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// Push release documentation to `gh-pages` branch of the release repo.
// Documentation is stored in per-version directories. index.html in the gh-pages branch root
// shall redirect to the latest available documentation. Update it together with the latest branch. 
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------

const gitBranchExists = async (branch: string): boolean => {
  const stdout = (await execAsync(`git branch -a --list ${branch}`)).stdout.trim();
  return stdout.length > 0;
};

const publishDocs = async() => {
  if (!await gitBranchExists("origin/gh-pages")) {
    console.debug("Creating gh-pages branch");
    await execAsync("git checkout --orphan gh-pages");
    await execAsync("git rm -rf .");
    await execAsync("git commit --allow-empty -m 'Initial gh-pages commit'");
    await execAsync("git push -u origin gh-pages");
  } else {
    console.debug("Checking out gh-pages branch");
    await execAsync("git checkout gh-pages");
  }

  fs.rmSync(`releases/${releaseVersion}/docs`, { recursive: true, force: true });
  fs.cpSync(docsDir, `releases/${releaseVersion}/docs`, { recursive: true });

  if (updateLatest) {
    const redirectPathsMap = {
        "sync-ios": `releases/${releaseVersion}/docs/documentation/twiliosync`,
    }
    const redirectPath = redirectPathsMap[product] ?? `releases/${releaseVersion}/docs`;
    const redirectUrl = rcVersionPublish ? redirectPath : `${publishRepo}/${redirectPath}`;
    const template = await fs.promises.readFile(path.resolve(__dirname, "template-docs-index.html"), 'UTF-8');
    const indexHtml = mustache.render(template, { REDIRECT_URL: redirectUrl });
    await fs.promises.writeFile("index.html", indexHtml);
  }

  console.debug("Publishing documentation to gh-pages");
  await execAsync(`git add . && git commit -m 'Documentation for v${releaseVersion}' && git push origin gh-pages`);
}

// Publish docs if docsDir is created during the package step
if (fs.existsSync(docsDir)) {
  await publishDocs();
}

//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
// [main] End processing.
//-------------------------------------------------------------------------------------------------
//-------------------------------------------------------------------------------------------------
