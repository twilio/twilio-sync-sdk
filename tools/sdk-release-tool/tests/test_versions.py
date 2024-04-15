from random import shuffle
from sdk_release_tools.versions import (MajorMinorPatch, SemVer, TwilioVersion,
                                        parse_version)
import unittest


class TestMajorMinorPatch(unittest.TestCase):
    version_class = MajorMinorPatch

    valid_sorted = [
        '0.0.1',
        '0.1.0',
        '1.0.0',
        '1.2.3',
        '1.2.30',
        '1.20.3',
        '10.2.3',
    ]

    invalid = [
        '',
        '1',
        '0.1',
        '01.2.3',
        '1.02.3',
        '1.2.03',
        'x.y.z',
    ]

    def test_parse(self):
        for version in self.valid_sorted:
            assert self.version_class.parse(version)
        for version in self.invalid:
            with self.assertRaises(ValueError):
                self.version_class.parse(version)

    def test_str(self):
        for version in self.valid_sorted:
            assert str(self.version_class.parse(version)) == version

    def test_sort(self):
        shuffled_and_sorted = map(self.version_class.parse, self.valid_sorted)
        shuffle(shuffled_and_sorted)
        shuffled_and_sorted.sort()
        assert map(str, shuffled_and_sorted) == self.valid_sorted


class TestSemVer(TestMajorMinorPatch):
    version_class = SemVer

    valid_sorted = [
        '0.1.0',
        '1.2.3-alpha',
        '1.2.3-beta',
        '1.2.3-dev+build',
        '1.2.3-dev',
        '1.2.3+build',
        '1.2.3',
    ]

    invalid = [
        '00.00.00',
        '01.2.3',
        '1.02.3',
        '1.2.03',
        'x.y.z',
    ]


class TestTwilioVersion(TestMajorMinorPatch):
    version_class = TwilioVersion

    valid_sorted = [
        '1.2.3.b1-deadbee',
        '1.2.3.b20-aaaaaaa',
        '1.2.3.b20-deadbee',
    ]

    invalid = [
        '1.2.3.b0-deadbee',
        '1.2.3.b1-deadbe',
        '1.2.3',
        '1.2.3-dev',
        '1.2.3-dev+build',
        '1.2.3+build',
    ]


def test_parse_version():
    versions = (TestMajorMinorPatch.valid_sorted +
                TestSemVer.valid_sorted +
                TestTwilioVersion.valid_sorted)
    for version in versions:
        assert parse_version(version)
