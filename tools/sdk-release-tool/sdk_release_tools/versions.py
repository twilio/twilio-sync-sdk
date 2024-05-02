from functools import total_ordering

import re


__all__ = ['Version', 'MajorMinor', 'MajorMinorPatch', 'SemVer',
           'TwilioVersion', 'parse_major_minor', 'parse_version']


def drop_leading_v(string):
    if string and string[0] == 'v':
        return string[1:]
    return string


def contains_leading_zeroes(string):
    """
    Check if a string representing a digit contains leading zeroes.
    """
    return string.isdigit() and re.match(r'^0[0-9]', string)


def parse_major_minor_patch(string):
    """
    Parse major, minor, and patch versions out of a string and return the rest.
    """
    match = re.match(r'^([0-9]+)\.([0-9]+)\.([0-9]+)', string)

    exception = ValueError(
        ('A normal version number MUST take the form X.Y.Z where X, Y, and Z '
         'are non-negative integers, and MUST NOT contain leading zeroes: ' +
         string))

    if not match:
        raise exception

    identifiers = match.groups()
    if any([contains_leading_zeroes(identifier) for identifier in
            identifiers]):
        raise exception

    major = int(match.group(1))
    minor = int(match.group(2))
    patch = int(match.group(3))
    rest = string[match.end():]

    return (major, minor, patch, rest)


class Version(object):
    def __ne__(self, other):
        return not self == other


@total_ordering
class MajorMinor(Version):
    """
    A version number with major and minor components, e.g.

        1.2

    """
    def __eq__(self, other):
        return (self.major == other.major and
                self.minor == other.minor)

    def __init__(self, major, minor):
        self.major = major
        self.minor = minor

    def __le__(self, other):
        if self.major < other.major:
            return True
        elif self.major == other.major:
            return self.minor <= other.minor
        return False

    def __str__(self):
        return '{}.{}'.format(self.major, self.minor)

    @classmethod
    def parse(cls, string):
        """
        Parse a string to a MajorMinorPatch, or raise a ValueError.
        """
        string = drop_leading_v(string)

        match = re.match(r'^([0-9]+)\.([0-9]+)$', string)

        exception = ValueError('Unparsed: ' + string)

        if not match:
            raise exception

        identifiers = match.groups()
        if any([contains_leading_zeroes(identifier) for identifier in
                identifiers]):
            raise exception

        major = int(match.group(1))
        minor = int(match.group(2))

        return MajorMinor(major, minor)


@total_ordering
class MajorMinorPatch(MajorMinor):
    """
    A version number with major, minor, and patch components, e.g.

        1.2.3

    """
    def __eq__(self, other):
        return (super(MajorMinorPatch, self).__eq__(other) and
                self.patch == other.patch)

    def __init__(self, major, minor, patch):
        super(MajorMinorPatch, self).__init__(major, minor)
        self.patch = patch

    def __le__(self, other):
        if super(MajorMinorPatch, self).__le__(other):
            if not super(MajorMinorPatch, self).__eq__(other):
                return True
            return self.patch <= other.patch
        return False

    def __str__(self):
        return '{}.{}'.format(super(MajorMinorPatch, self).__str__(),
                              self.patch)

    @classmethod
    def parse(cls, string):
        """
        Parse a string to a MajorMinorPatch, or raise a ValueError.
        """
        string = drop_leading_v(string)

        (major, minor, patch, rest) = (
            parse_major_minor_patch(string))

        if rest:
            raise ValueError('Unparsed: ' + rest)

        return MajorMinorPatch(major, minor, patch)


def lt_pre_release(self, other):
    """
    Check to see if one SemVer's pre_release version is less than that of
    another. From the SemVer spec:

        Pre-release versions have a lower precedence than the associated
        normal version.

    """
    if self.pre_release and other.pre_release:
        return self.pre_release < other.pre_release
    elif self.pre_release and not other.pre_release:
        return True
    return False


def le_build_metadata(self, other):
    """
    Check to see if one SemVer's build_metadata is less than or equal to
    another. Like le_pre_release, this function gives versions with
    build_metadata a lower precedence than those without; however, it is
    unclear if this is useful and we may very well change it.
    """
    if self.build_metadata and other.build_metadata:
        return self.build_metadata <= other.build_metadata
    elif self.build_metadata and not other.build_metadata:
        return True
    return self.build_metadata == other.build_metadata


@total_ordering
class SemVer(MajorMinorPatch):
    """
    A Semantic Version (SemVer) number per the SemVer spec at

        <http://semver.org/>

    """
    def __eq__(self, other):
        return (super(SemVer, self).__eq__(other) and
                self.pre_release == other.pre_release and
                self.build_metadata == other.build_metadata)

    def __init__(self, major, minor, patch, pre_release=None,
                 build_metadata=None):
        super(SemVer, self).__init__(major, minor, patch)
        self.pre_release = pre_release
        self.build_metadata = build_metadata

    def __le__(self, other):
        if super(SemVer, self).__le__(other):
            if type(self) is type(other):
                if not super(SemVer, self).__eq__(other):
                    return True
                elif lt_pre_release(self, other):
                    return True
                elif self.pre_release == other.pre_release:
                    return le_build_metadata(self, other)
        return False

    def __str__(self):
        string = super(SemVer, self).__str__()
        if self.pre_release:
            string += '-' + self.pre_release
        if self.build_metadata:
            string += '+' + self.build_metadata
        return string

    @classmethod
    def parse(cls, string):
        """
        Parse a string to a SemVer, or raise a ValueError.
        """
        string = drop_leading_v(string)

        (major, minor, patch, rest) = (
            parse_major_minor_patch(string))

        # A pre-release version MAY be denoted by appending a hyphen and a
        # series of dot separated identifiers immediately following the patch
        # version.
        pre_release = None
        if rest and rest[0] == '-':

            match = re.match(r'-([0-9A-Za-z-.]+)', rest)
            if not match:
                raise ValueError(
                    ('Identifiers MUST comprise only ASCII alphanumerics and '
                     'hyphen [0-9A-Za-z-]' + string))

            pre_release = match.group(1)
            identifiers = pre_release.split('.')

            if not all(identifiers):
                raise ValueError('Identifiers MUST NOT be empty: ' + string)

            if any([contains_leading_zeroes(identifier) for identifier in
                    identifiers]):
                raise ValueError(
                    'Numeric identifiers MUST NOT include leading zeroes: ' +
                    string)

            rest = rest[match.end():]

        # Build metadata MAY be denoted by appending a plus sign and a series
        # of dot separated identifiers immediately following the patch or
        # pre-release version.
        build_metadata = None
        if rest and rest[0] == '+':

            match = re.match(r'\+([0-9A-Za-z-.]+)$', rest)
            if not match:
                raise ValueError(
                    ('Identifiers MUST comprise only ASCII alphanumerics and '
                     'hyphen [0-9A-Za-z-]: ' + string))

            build_metadata = match.group(1)
            identifiers = build_metadata.split('.')

            if not all(identifiers):
                return None

            rest = rest[match.end():]

        if rest:
            raise ValueError('Unparsed: ' + rest)

        return SemVer(major, minor, patch, pre_release, build_metadata)


@total_ordering
class TwilioVersion(MajorMinorPatch):
    """
    The Twilio version numbers we use have the form

        1.2.3.b4-deadbee

    and they aren't quite SemVer. Essentially, we are tacking on build metadata
    with a "." instead of a "+", and in this build metadata we specify a build
    number and the git commit hash.
    """
    def __eq__(self, other):
        return (super(TwilioVersion, self).__eq__(other) and
                self.build_number == other.build_number and
                self.git_commit == other.git_commit)

    def __init__(self, major, minor, patch, git_commit, build_number=1):
        super(TwilioVersion, self).__init__(major, minor, patch)
        self.git_commit = git_commit
        self.build_number = build_number

    def __le__(self, other):
        if super(TwilioVersion, self).__le__(other):
            if type(self) is type(other):
                if not super(TwilioVersion, self).__eq__(other):
                    return True
                elif self.build_number < other.build_number:
                    return True
                elif self.build_number == other.build_number:
                    return self.git_commit <= other.git_commit
        return False

    def __str__(self):
        return (super(TwilioVersion, self).__str__() +
                '.b{}-{}'.format(self.build_number, self.git_commit))

    @classmethod
    def parse(cls, string):
        """
        Parse a string to a TwilioVersion, or raise a ValueError.
        """
        string = drop_leading_v(string)

        (major, minor, patch, rest) = (
            parse_major_minor_patch(string))

        match = re.match(r'^\.b([0-9]+)-([a-z0-9]{7})$', rest)
        if not match:
            raise ValueError(
                'Expecting build number and git commit: ' + string)

        build_number = match.group(1)
        git_commit = match.group(2)
        rest = rest[match.end():]

        if contains_leading_zeroes(build_number):
            raise ValueError('Build number contains leading zeroes: ' + string)

        build_number = int(build_number)
        if not build_number:
            raise ValueError('Build number must be non-zero: ' + string)

        if rest:
            raise ValueError('Unparsed: ' + rest)

        return TwilioVersion(major, minor, patch, git_commit, build_number)


def parse_version(string):
    """
    Attempt to parse a string to a SemVer or TwilioVersion.
    """
    version_classes = [
        SemVer,
        TwilioVersion,
    ]
    for version_class in version_classes:
        try:
            return version_class.parse(string)
        except ValueError:
            pass
    raise ValueError('Input is neither a SemVer nor TwilioVersion: ' + string)

parse_major_minor = MajorMinor.parse
