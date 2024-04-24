#!/usr/local/bin/python
from sdk_release_tools import log
from sdk_release_tools.aws import (get_routing_rules, load_routing_rules,
                                   update_routing_rules)
from sdk_release_tools.cli import parse_args
from sdk_release_tools.util import (delete, download, get_cors, get_pinned_by,
                                    get_versions, load_schema,
                                    pin, pin_latest, unpin, unpin_latest,
                                    upload, version_exists)
from sdk_release_tools.versions import parse_major_minor, parse_version


import sys


def main():
    sys.argv[0] = 'sdk-release-tool'
    args = parse_args()
    action = args.action
    realm = args.realm

    if action == 'delete':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        if not version_exists(realm, schema, version):
            raise Exception('Version {} does not exist'.format(version))
        elif get_pinned_by(realm, schema, version) and not args.force:
            raise Exception(('Cannot delete a pinned version; '
                             'use -f or --force to override'))
        delete(realm, schema, version, args.dry_run, args.silent)

    elif action == 'download':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        if not version_exists(realm, schema, args.version):
            raise Exception('Version {} does not exist'.format(version))
        download(realm, schema, version, args.destination, args.dry_run)

    elif action == 'list':
        schema = load_schema(args.product)
        ordered_versions, _, latest = get_versions(realm, schema)
        for version, major_minor in ordered_versions.items():
            line = version
            if major_minor:
                line += ' <- {}'.format(major_minor)
            if str(version) == str(latest):
                line += ' (latest)'
            log.log(line)

    elif action == 'list-routing-rules':
        get_routing_rules(realm)
        return

    elif action == 'pin':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        if not version_exists(realm, schema, version):
            raise Exception('Version {} does not exist'.format(version))
        elif (hasattr(version, 'pre_release') and version.pre_release and
              not args.force):
            raise Exception(('Cannot pin a pre-release version; '
                             'use -f or --force to override'))
        pin(realm, schema, version, args.dry_run)

    elif action == 'pin-latest':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        _, _, latest_version = get_versions(realm, schema)
        if not version_exists(realm, schema, version):
            raise Exception('Version {} does not exist'.format(version))
        elif (hasattr(version, 'pre_release') and version.pre_release and
              not args.force):
            raise Exception(('Cannot pin a pre-release version; '
                             'use -f or --force to override'))
        elif (latest_version and version < latest_version and not args.force):
            raise Exception(('Cannot pin version earlier than latest as next latest; '
                             'use -f or --force to override'))
        pin_latest(realm, schema, version, args.dry_run)

    elif action == 'unpin':
        schema = load_schema(args.product)
        major_minor = parse_major_minor(args.version)
        unpin(realm, schema, major_minor, args.dry_run)

    elif action == 'unpin-latest':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        unpin_latest(realm, schema, version, args.dry_run)

    elif action == 'update-routing-rules':
        routing_rules_xml_file = args.xml
        routing_rules = load_routing_rules(routing_rules_xml_file)
        update_routing_rules(realm, routing_rules, args.dry_run)
        return

    elif action == 'get-cors':
        get_cors(realm)
        return

    elif action == 'upload':
        schema = load_schema(args.product)
        version = parse_version(args.version)
        if version_exists(realm, schema, version) and not args.force:
            raise Exception(('Cannot overwrite an existing version; '
                             'use -f or --force to override'))
        upload(realm, schema, version, args.source, args.dry_run)


if __name__ == '__main__':
    main()
