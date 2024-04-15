# Software Bill of Materials

This directory contains information for assembling the SDK SBoM files, NOTICES.txt that will be published in the distribution tarballs.

## SBOM files

SBOM files are toml files which define either a product or a dependency (based on the root section \[product\] or \[dependency\])

### Product

A product defines list of links to dependencies, which are also sbom files (and could contain both products and dependencies, making it a graph).

```
[product]
name = "iOS Sync SDK"

[links]
boost = "boost.sbom"
name = "file.sbom"
```

### Dependency

A dependency defines a license and additional comments.

```
[dependency]
name = "Boost libraries"
homepage = "homepage or repo url"
license = { // either of:
	text = "plain text",
	file = "file with text",
	url = "url with text"
}
spdx = "BSL-1.0"

# Could contain multiple entries, but is optional
addons = [
{ url = "location of additional info" },
{ file = "file with additional info" },
{ text = "Additional info" },
{ text = """
Additional info could
also
be
multiline.
"""
}]
```

## Output

This graph is resolved by the sbom tool in `tools/sbom` folder to generate NOTICES.txt files in `generated/` folder.

> These files _should_ be committed to the repository and copied to the appropriate places when generating the redistributable tarballs.

Run `../tools/sbom/target/release/sbom ios-sync.sbom` to generate `generated/ios-sync.NOTICES.txt` file with all dependencies license information.

You can also go to `tools/sbom` and use the Justfile there:

```
# Build everything
just all
# Build only android-sync
just android-sync
```

To only build sbom tool:

```
# Install cargo if you don't have it
curl https://sh.rustup.rs -sSf | sh # See: https://doc.rust-lang.org/cargo/getting-started/installation.html
source "$HOME/.cargo/env"

# Build the sbom tool
cd tools/sbom
cargo build --release

# Install just if you don't have it
brew install just
```
