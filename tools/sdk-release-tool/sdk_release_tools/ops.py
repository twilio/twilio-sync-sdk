from boto.s3.key import Key
from sdk_release_tools import log
from sdk_release_tools.versions import parse_major_minor

import os

__all__ = ['Delete', 'Download', 'Pin', 'Unpin', 'Upload', 'delete',
           'download', 'pin', 'unpin', 'upload']


def absolute(root):
    if not os.path.isabs(root):
        root = os.path.join(os.getcwd(), root)
    return root


class Context(object):
    def __init__(self, root=None, variables=None, bucket=None, dry_run=True, silent=False,
                 copy_on_pin=False):
        self.root = root
        self.variables = variables or {}
        self.bucket = bucket
        self.rules = (bucket.get_website_configuration_obj().routing_rules if
                      bucket else None)
        self.dry_run = dry_run
        self.silent = silent
        self.copy_on_pin = copy_on_pin

    def absolute(self, key):
        """
        Get the absolute path to a key prepended by this Context's root, and
        interpolate any variables.
        """
        path = self.relative(key)
        if self.root:
            path = os.path.join(self.root, path)
        return absolute(path)

    def relative(self, key):
        """
        Get the relative path to a key, and interpolate any variables.
        """
        return key.format(**self.variables)


class Ops(object):
    def __init__(self, tree):
        self.tree = tree

    def _fold(self, context, tree=None):
        tree = tree or self.tree
        for key, value in tree.items():
            context = self._op(key, value, context)
        return context

    def _op(self, key, value, context):
        if key.endswith('/'):
            return self._op_dir(key, value, context)
        return self._op_file(key, value, context)

    def _op_dir(self, key, value, context):
        return context

    def _op_file(self, key, value, context):
        return context

    def run(self, context):
        return self._fold(context)


class Delete(Ops):
    def _op_dir(self, key, value, context):
        src = context.relative(value)
        for sub_key in context.bucket.list(src):
            context = self._op_file(sub_key.name, sub_key.name, context)
        return context

    def _op_file(self, key, value, context):
        src = context.relative(value)
        
        if not context.silent:
            response =  raw_input("Confirm deletion of " + src + " [y/n]: ").lower()
            if response == "yes" or response == "y":
                log.log("  Continuing deletion of: " + src + "\n")
                self._delete_file(src, context)
            else:
                log.log("  Skipping, " + src + " will be protected.\n")
        else:
            log.log(src)
            self._delete_file(src,context)
        return context

    def _delete_file(self, src, context):
        src_key = context.bucket.get_key(src)
        if not src_key:
            log.warn('  Key {} does not exist'.format(src))

        if not context.dry_run and src_key:
            context.bucket.delete_key(src)
            log.log("   " + src + " deleted")
        return context


class Download(Ops):
    def _op_dir(self, key, value, context):
        src = context.relative(value)
        for sub_key in context.bucket.list(src):
            context = self._op_file(
                os.path.join(key, sub_key.name[len(src):]), sub_key.name,
                context)
        return context

    def _op_file(self, key, value, context):
        src = context.relative(value)
        dst = context.absolute(key)
        log.log('{} -> {}'.format(src, dst))

        src_key = context.bucket.get_key(src)
        if not src_key:
            log.error('  Key {} does not exist'.format(src))

        if not context.dry_run and src_key:
            dst_dir = os.path.dirname(dst)
            try:
                os.makedirs(dst_dir)
            except:
                pass
            src_key.get_contents_to_filename(dst)

        return context


def reconfigure_website(bucket, rules):
    config = bucket.get_website_configuration_obj()
    bucket.configure_website(suffix=config.suffix, error_key=config.error_key,
                             routing_rules=rules)


class Pin(Ops):
    def _op(self, key, value, context):
        src = context.relative(key)
        dst = context.relative(value)
        log.log('{} -> {}'.format(src, dst))

        # Delete any previous RoutingRules. We have to use S3 Key redirects.
        for rule in list(context.rules):
            key_prefix = rule.condition.key_prefix
            if not src.startswith(key_prefix):
                continue

            replace_key_prefix = rule.redirect.replace_key_prefix

            try:
                major_minor = parse_major_minor(
                    os.path.split(key_prefix.rstrip('/'))[1])
            except:
                continue

            if (major_minor != parse_major_minor(
                    '{major}.{minor}'.format(**context.variables))):
                continue

            context.rules.remove(rule)

            log.warn('  Deleting RoutingRule that pointed to {}'.format(
                replace_key_prefix))

        # Create S3 Key redirect.
        src_key = context.bucket.get_key(src)
        if not src_key:
            log.log('  Creating S3 Key redirect')
        else:
            existing_redirect = src_key.get_redirect()
            log.warn('  Updating S3 Key redirect that pointed to {}'.format(
                existing_redirect))

        if not context.dry_run:
            if not src_key:
                src_key = Key(context.bucket)
                src_key.key = src
            src_key.set_redirect('/' + dst.lstrip('/'), headers={
                'Cache-Control': 'max-age=0, no-cache, no-store'
            })

        return context

    def run(self, context):
        context = super(Pin, self).run(context)
        if not context.dry_run:
            reconfigure_website(context.bucket, context.rules)
        return context


class Unpin(Ops):
    def _op(self, key, value, context):
        src = context.relative(key)
        dst = context.relative(value)
        log.log('{} -> {}'.format(src, dst))

        found = False

        # Delete any RoutingRules.
        for rule in list(context.rules):
            key_prefix = rule.condition.key_prefix
            if not src.startswith(key_prefix):
                continue

            replace_key_prefix = rule.redirect.replace_key_prefix

            try:
                major_minor = parse_major_minor(
                    os.path.split(key_prefix.rstrip('/'))[1])
            except:
                continue

            if (major_minor != parse_major_minor(
                    '{major}.{minor}'.format(**context.variables))):
                continue

            found = True
            context.rules.remove(rule)
            log.warn('  Deleting RoutingRule that pointed to {}'.format(
                replace_key_prefix))

        # Delete any S3 Key redirects.
        src_key = context.bucket.get_key(src)
        if src_key:
            found = True
            if not context.dry_run:
                context.bucket.delete_key(src)

        if not found:
            log.warn('  Redirect {} does not exist'.format(src))

        return context

    def run(self, context):
        context = super(Unpin, self).run(context)
        if not context.dry_run:
            reconfigure_website(context.bucket, context.rules)
        return context


class Upload(Ops):
    def _op_dir(self, key, value, context):
        srcdir = context.absolute(key)

        for path, _, srcs in os.walk(srcdir):
            for src in srcs:
                sub_path = path[len(context.absolute(key)):]
                sub_key = os.path.join(key, sub_path, src)
                sub_value = os.path.join(value, sub_path, src)
                context = self._op_file(sub_key, sub_value, context)

        return context

    def _op_file(self, key, value, context):
        src = context.absolute(key)
        dst = context.relative(value)
        log.log('{} -> {}'.format(src, dst))

        dst_key = context.bucket.get_key(dst)
        if dst_key:
            log.warn('  Updating Key')

        if not context.dry_run:
            if not dst_key:
                dst_key = Key(context.bucket)
                dst_key.key = dst
            dst_key.set_contents_from_filename(src, headers={
                'Cache-Control': 'max-age=315360000',
                'Expires': 'Thu, 31 Dec 2037 23:55:55 GMT'
            })

        return context


def delete(tree, **kwargs):
    return Delete(tree).run(Context(**kwargs))


def download(tree, **kwargs):
    return Download(tree).run(Context(**kwargs))


def pin(tree, **kwargs):
    return Pin(tree).run(Context(**kwargs))


def unpin(tree, **kwargs):
    return Unpin(tree).run(Context(**kwargs))


def upload(tree, **kwargs):
    return Upload(tree).run(Context(**kwargs))
