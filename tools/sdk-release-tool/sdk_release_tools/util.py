from collections import OrderedDict
from sdk_release_tools import ops
from sdk_release_tools import rpm
from sdk_release_tools.aws import get_bucket
from sdk_release_tools.versions import parse_major_minor, parse_version
import json
import os



def load_schema(schema_name):
    filepath = (schema_name if schema_name.endswith('.json') else
                '{}.json'.format(schema_name))
    with open(filepath) as schema_file:
        return json.loads(schema_file.read())


def get_variables(schema, version):
    """
    Get the variables defined in the schema and merge them with any version
    number variables (e.g., "major", "minor", "patch", etc.).
    """
    variables = schema.get('variables', {})
    variables.update(version.__dict__)
    variables.update(version=str(version))
    return variables


def delete(realm, schema, version, dry_run=True, silent=False):
    artifacts = schema.get('artifacts', {})
    variables = get_variables(schema, version)
    return ops.delete(artifacts, bucket=get_bucket(realm), variables=variables,
                      dry_run=dry_run, silent=silent)


def download(realm, schema, version, root, dry_run=True):
    artifacts = schema.get('artifacts', {})
    variables = get_variables(schema, version)
    return ops.download(artifacts, root=root, bucket=get_bucket(realm),
                        variables=variables, dry_run=dry_run)


def pin(realm, schema, version, dry_run=False):
    rules = schema.get('pin', {})
    variables = get_variables(schema, version)
    copy_on_pin = schema.get('copy_on_pin', False)
    return ops.pin(rules, bucket=get_bucket(realm), variables=variables,
                   dry_run=dry_run, copy_on_pin=copy_on_pin)


def pin_latest(realm, schema, version, dry_run=False):
    rules = schema.get('latest', {})
    variables = get_variables(schema, version)
    copy_on_pin = schema.get('copy_on_pin', False)
    return ops.pin(rules, bucket=get_bucket(realm), variables=variables,
                   dry_run=dry_run, copy_on_pin=copy_on_pin)


def unpin(realm, schema, version, dry_run=False):
    rules = schema.get('pin', {})
    variables = get_variables(schema, version)
    return ops.unpin(rules, bucket=get_bucket(realm), variables=variables,
                     dry_run=dry_run)


def unpin_latest(realm, schema, version, dry_run=False):
    rules = schema.get('latest', {})
    variables = get_variables(schema, version)
    return ops.unpin(rules, bucket=get_bucket(realm), variables=variables,
                     dry_run=dry_run)


def upload(realm, schema, version, root, dry_run=True):
    artifacts = schema.get('artifacts', {})
    variables = get_variables(schema, version)
    if not os.path.isdir(root):
        root = rpm.unpack(root)
    return ops.upload(artifacts, root=root, bucket=get_bucket(realm),
                      variables=variables, dry_run=dry_run)


def get_cors(realm):
    bucket = get_bucket(realm)
    return bucket.get_cors()

def get_versions(realm, schema):
    bucket = get_bucket(realm)
    config = bucket.get_website_configuration_obj()

    unordered_versions = []
    versions_dir = schema.get('versions').format(**schema.get('variables', {}))

    for key in bucket.list(versions_dir, '/'):
        try:
            version = parse_version(os.path.split(key.name.rstrip('/'))[1])
        except:
            continue
        unordered_versions.append(version)

    ordered_versions = OrderedDict()
    for version in sorted(unordered_versions):
        ordered_versions[str(version)] = None

    major_minor_versions_dir = schema.get('major_minor_versions').format(
        **schema.get('variables', {}))
    ordered_major_minors = OrderedDict()

    latest = None

    # First, try to identify any versions pinned by S3 Key redirects.
    for key in bucket.list(major_minor_versions_dir, '/'):
        major_minor = None
        try:
            major_minor = parse_major_minor(
                os.path.split(key.name.rstrip('/'))[1])
        except:
            if os.path.split(key.name.rstrip('/'))[1] != "latest":
                continue

        # This is a little bit of a hack: we are going to iterate through the
        # prefixes until we find one that matches a key. Once we have the key,
        # we need to check if it has a redirect to a version number.
        version = None
        for key in bucket.list(key.name, '/'):
            if key.name.endswith('/'):
                continue
            key = bucket.get_key(key.name)
            if not key:
                continue
            redirect = key.get_redirect()
            if not redirect:
                continue
            if redirect.startswith('/' + versions_dir.lstrip('/')):
                redirect = redirect[len('/' + versions_dir.lstrip('/')):]
                try:
                    version = parse_version(redirect.split('/')[0])
                except:
                    continue
                break

        if not version:
            continue

        if not major_minor:
            latest = version

        version_str = str(version)
        if version_str in ordered_versions:
            ordered_versions[version_str] = major_minor
        ordered_major_minors[str(major_minor)] = version

    # Then, try to identify any versions pinned by RoutingRules (legacy).
    for rule in config.routing_rules:
        key_prefix = rule.condition.key_prefix
        if not key_prefix.startswith(major_minor_versions_dir):
            continue

        replace_key_prefix = rule.redirect.replace_key_prefix

        try:
            major_minor = parse_major_minor(
                os.path.split(key_prefix.rstrip('/'))[1])
            version = parse_version(
                os.path.split(replace_key_prefix.rstrip('/'))[1])
        except:
            continue

        version_str = str(version)
        if version_str in ordered_versions:
            ordered_versions[version_str] = major_minor
        ordered_major_minors[str(major_minor)] = version

    return (ordered_versions, ordered_major_minors, latest)


def version_exists(realm, schema, version):
    ordered_versions, _, _ = get_versions(realm, schema)
    return str(version) in ordered_versions


def get_pinned_by(realm, schema, version):
    """
    Get the major/minor pair that pins a version.
    """
    ordered_versions, _, _ = get_versions(realm, schema)
    return ordered_versions.get(str(version))
