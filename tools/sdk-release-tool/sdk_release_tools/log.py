from clint.textui import colored

import sys


def debug(message):
    print(colored.green(message))


def error(message):
    print(colored.red(message))
    sys.exit(1)


def info(message):
    print(colored.blue(message))


def log(message):
    print(message)


def warn(message):
    print(colored.yellow(message))
