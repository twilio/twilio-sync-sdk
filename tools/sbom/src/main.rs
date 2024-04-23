use {
    anyhow::{anyhow, Result},
    clap::Parser,
    indexmap::IndexMap,
    serde_derive::Deserialize,
    std::{
        collections::BTreeMap,
        fmt::{Display, Formatter},
        fs::File,
        io::{Read, Write},
        path::PathBuf,
    },
    thiserror::Error,
};

const NOTICES_HEADER: &'static str = "\nThis file includes a list of third-party open source licenses used in this Twilio product.\n\n";

#[derive(Debug, Error)]
enum Error {
    #[error("❌ Could not read file {0}")]
    #[allow(dead_code)]
    BadFile(PathBuf),
    #[error("❌ Could not parse toml {0}")]
    TomlError(#[from] toml::de::Error),
}

#[derive(Parser)]
#[clap(name = "SBoM builder")]
#[clap(version = "1.0")]
#[clap(author = "Berkus <berkus@twilio.com>")]
#[clap(about = "Convert a sbom graph into NOTICES.txt file with license summary")]
struct Args {
    #[clap(required = true, value_parser)]
    pub paths: Vec<PathBuf>,
}

// TOML deserialization

#[derive(Debug, Deserialize)]
struct ProductName {
    #[allow(unused)]
    name: String,
}

pub type LinksSet = BTreeMap<String, String>;

#[derive(Debug, Deserialize, Clone)]
#[serde(rename_all = "lowercase")]
enum LicenseInfo {
    URL(String),
    File(PathBuf),
    Text(String),
}

type ResolvedLicenseInfo = String;

impl LicenseInfo {
    fn resolve(&self, base: &PathBuf) -> Result<ResolvedLicenseInfo> {
        match self {
            LicenseInfo::URL(from) => {
                let response = reqwest::blocking::get(from)?;
                if response.status().is_success() {
                    return Ok(response.text()?);
                }
                panic!(
                    "❌ Failed to retrieve contents of the URL {}, sbom is not updated.",
                    from
                )
            }
            LicenseInfo::File(file) => Ok(load_file(&resolve_file(base, file)?)?),
            LicenseInfo::Text(out) => Ok(out.into()),
        }
    }
}

#[derive(Debug, Deserialize, Clone)]
struct DependencyInfo {
    name: String,
    homepage: String,
    license: LicenseInfo,
    addons: Option<Vec<LicenseInfo>>,
    #[allow(unused)]
    spdx: String,
}

#[derive(Debug, Deserialize)]
struct Descriptor {
    product: Option<ProductName>,
    dependency: Option<DependencyInfo>,
    links: Option<LinksSet>,
}

#[derive(Default)]
struct OutputSbom {
    name: String,
    homepage: String,
    licenses: Vec<ResolvedLicenseInfo>,
}

impl Display for OutputSbom {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}\n - URL: {}\n\n", self.name, self.homepage)?;
        for license in &self.licenses {
            write!(f, "{}\n\n", license)?;
        }
        write!(f, "{}\n\n", "-".repeat(100))
    }
}

type Outputs = IndexMap<String, OutputSbom>;

fn parse_tree(path: &PathBuf, sbom: &Descriptor, outputs: &mut Outputs) -> Result<()> {
    if let Some(_product) = &sbom.product {
        parse_product(&path, &sbom, outputs)?;
    } else if let Some(_dependency) = &sbom.dependency {
        parse_dependency(&path, &sbom, outputs)?;
    } else {
        return Err(anyhow!(
            "❌ Malformed sbom descriptor in {}",
            path.display()
        ));
    }
    Ok(())
}

fn parse_product(path: &PathBuf, sbom: &Descriptor, outputs: &mut Outputs) -> Result<()> {
    if let Some(links) = &sbom.links {
        for (_k, v) in links {
            let path = resolve_file(&path, &v.into())?;
            let sbom = parse_file(&path)?;
            parse_tree(&path, &sbom, outputs)?;
        }
    }
    Ok(())
}

fn parse_dependency(base: &PathBuf, sbom: &Descriptor, outputs: &mut Outputs) -> Result<()> {
    let dep = sbom.dependency.as_ref().expect("Can't happen").clone();
    let mut e = outputs.entry(dep.name.clone()).or_default();

    e.name = dep.name;
    e.homepage = dep.homepage;
    e.licenses.push(dep.license.resolve(base)?);
    if let Some(addons) = dep.addons {
        println!("👀 Found {} addons", addons.len());
        for addon in addons {
            e.licenses.push(addon.resolve(base)?);
        }
    }

    // TODO: do something with spdx? Its use primarily would be to group all Apache licenses into one block instead of repeating it 700 times.
    // On the other hand, if they really want to read Apache license 700 times, let them.

    Ok(())
}

/// Resolve given `path` relative to the current file specified in `base`
fn resolve_file(base: &PathBuf, path: &PathBuf) -> Result<PathBuf> {
    let mut outfile = PathBuf::new();
    outfile.push(base);
    outfile.pop();
    outfile.push(path);
    Ok(outfile)
}

fn load_file(path: &PathBuf) -> Result<String> {
    println!("🏗 Loading from {}", path.display());
    let mut payload = String::with_capacity(1024);
    File::open(&path).and_then(|mut f| f.read_to_string(&mut payload))?;
    Ok(payload)
}

fn parse_file(path: &PathBuf) -> Result<Descriptor> {
    let payload = load_file(path)?;
    println!("👀 Parsing {}", payload);
    Ok(toml::from_str(&payload).map_err(Error::TomlError)?)
}

fn generate_sbom(path: PathBuf) -> Result<()> {
    let key = path.file_name().expect("No file name given");
    let mut outputs = Outputs::new();

    let sbom = parse_file(&path)?;
    parse_tree(&path, &sbom, &mut outputs)?;

    if !outputs.is_empty() {
        let mut outfile: PathBuf = "generated".into();
        outfile.push(key);
        outfile.set_extension("NOTICES.txt");

        let file = resolve_file(&path, &outfile)?;

        println!("✍️ Parsing complete, generating sbom {:?}...", file);

        let mut out = File::create(file)?;
        write!(out, "{}", NOTICES_HEADER)?;
        for item in outputs {
            write!(out, "{}", item.1)?;
        }
    } else {
        println!("❌ Parsing complete, but no sbom information found");
    }

    println!("🏁 Done.");

    Ok(())
}

fn main() -> Result<()> {
    let args = Args::parse();
    for path in args.paths {
        generate_sbom(path)?;
    }
    Ok(())
}
