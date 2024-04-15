from sdk_release_tools import log
from sdk_release_tools.versions import parse_version

import os
import subprocess


def get_version(rpm):
    rpm_cmd = 'rpm -qp --queryformat \'%{VERSION}\' ' + rpm
    rpm_version = subprocess.check_output(rpm_cmd, shell=True)
    rpm_version = rpm_version.replace('_', '-')
    if rpm_version.startswith('release-'):
        rpm_version = rpm_version[8:]
    return parse_version(rpm_version)


def unpack(rpm, prefix=None):
    prefix = prefix or '/tmp/'
    version = get_version(rpm)
    dst = prefix + str(version)
    log.log(rpm)
    log.info('  Unpacking to ' + dst)
    unpack_cmd = (
        'rm -rf {}; rpm2cpio {} | cpio -idmv; mv mnt {}'.format(dst, rpm, dst))
    FNULL = open(os.devnull, 'wb')
    subprocess.check_call(unpack_cmd, shell=True, stdout=FNULL, stderr=FNULL,
                          close_fds=True)
    return dst
