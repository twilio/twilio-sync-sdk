from argparse import ArgumentParser
from sdk_release_tools import log

__all__ = ['parse_args']


def parse_realms(parser):
    realm_grp = parser.add_mutually_exclusive_group()
    for realm in ['dev', 'stage', 'prod']:
        realm_grp.add_argument('--' + realm, action='store_const',
                               const=realm, dest='realm',
                               help=realm + ' realm flag')
    return parser


def parse_dry_run(parser):
    parser.add_argument('--dry-run', action='store_true', default=False,
                        dest='dry_run', help='do not perform any updates')
    return parser


def parse_delete_action(parser):
    parser = parser.add_parser('delete', help=('delete product artifacts at a '
                                               'version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str,
                        help='the product to delete')
    parser.add_argument('version', type=str,
                        help='the version number to delete at, e.g. "1.2.3"')
    parser.add_argument('-f', '--force', action='store_true', default=False,
                        help=('force a delete regardless of whether or not '
                              'the artifact is pinned by a major/minor '
                              'version'))
    parser.add_argument('-s', '--silent', action='store_true', default=False, help=('skip user confirmation of files to be deleted'))
    parse_dry_run(parser)
    return parser


def parse_download_action(parser):
    parser = parser.add_parser('download', help=('download product artifacts '
                                                 'from a version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to download')
    parser.add_argument('version', type=str,
                        help=('the version number to download from, e.g. '
                              '"1.2.3"; you may also pass a major/minor pair '
                              'to download the pinned version number, e.g. '
                              '"v1.0"'))
    parser.add_argument('destination', type=str,
                        help=('the directory to download to'))
    parse_dry_run(parser)
    return parser


def parse_list_action(parser):
    parser = parser.add_parser('list', help=('list the version numbers of '
                                             'uploaded product artifacts and '
                                             'any pinned major/minor pairs'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to list')
    return parser


def parse_list_routing_rules_action(parser):
    parser = parser.add_parser('list-routing-rules',
                               help=('list Routing Rules XML'))
    parse_realms(parser)
    return parser


def parse_pin_action(parser):
    parser = parser.add_parser('pin', help=('pin a major/minor pair to a '
                                            'version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to pin')
    parser.add_argument('version', type=str,
                        help='the version number to pin, e.g. "1.2.3"')
    parser.add_argument('-f', '--force', action='store_true', default=False,
                        help=('force a pin regardless of whether or not the '
                              'version is a pre-release version'))
    parse_dry_run(parser)
    return parser


def parse_pin_latest_action(parser):
    parser = parser.add_parser('pin-latest',
                               help=('pin "latest" to a version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to pin')
    parser.add_argument('version', type=str,
                        help='the version number to pin, e.g. "1.2.3"')
    parser.add_argument('-f', '--force', action='store_true', default=False,
                        help=('force a pin regardless of whether or not the '
                              'version is a pre-release version'))
    parse_dry_run(parser)
    return parser


def parse_unpin_action(parser):
    parser = parser.add_parser('unpin', help=('unpin a major/minor pair from '
                                              'a version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to unpin')
    parser.add_argument('version', type=str,
                        help='the major/minor pair to unpin, e.g. "v1.2"')
    parse_dry_run(parser)
    return parser


def parse_unpin_latest_action(parser):
    parser = parser.add_parser('unpin-latest',
                               help=('unpin "latest" from a version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to unpin')
    parser.add_argument('version', type=str,
                        help='the version number to unpin, e.g. "1.2.3"')
    parse_dry_run(parser)
    return parser


def parse_upload_action(parser):
    parser = parser.add_parser('upload', help=('upload product artifacts to '
                                               'a version number'))
    parse_realms(parser)
    parser.add_argument('product', type=str, help='the product to upload')
    parser.add_argument('version', type=str,
                        help='the version number to upload to, e.g. "1.2.3"')
    parser.add_argument('source', type=str,
                        help='a directory or RPM containing the artifacts')
    parser.add_argument('-f', '--force', action='store_true', default=False,
                        help=('force an upload regardless of whether or not '
                              'the artifact already exists'))
    parse_dry_run(parser)
    return parser

def parse_get_cors_action(parser):
    parser = parser.add_parser('get-cors',
                               help=('Get the cors settings for the realm'))
    parse_realms(parser)
    return parser


def parse_update_routing_rules_action(parser):
    parser = parser.add_parser('update-routing-rules',
                               help=('manually update Routing Rules '
                                     '(dangerous)'))
    parse_realms(parser)
    parser.add_argument('xml', type=str,
                        help=('the Routing Rules XML file'))
    parse_dry_run(parser)
    return parser


def parse_args():
    parser = ArgumentParser(description=('Manage Twilio SDK releases on the '
                                         'CDN'))

    action_parser = parser.add_subparsers(help='sub-command help',
                                          dest='action')
    parse_delete_action(action_parser)
    parse_download_action(action_parser)
    parse_list_action(action_parser)
    parse_list_routing_rules_action(action_parser)
    parse_pin_action(action_parser)
    parse_pin_latest_action(action_parser)
    parse_unpin_action(action_parser)
    parse_unpin_latest_action(action_parser)
    parse_upload_action(action_parser)
    parse_get_cors_action(action_parser)
    parse_update_routing_rules_action(action_parser)

    args = parser.parse_args()
    # if parser.has_errors:
    #     parser.handle_error()
    if not hasattr(args, 'realm') or not args.realm:
        args.realm = 'dev'
        log.warn('  No realm specified, assuming dev')
    if not args.action:
        args.action = 'list'

    return args
