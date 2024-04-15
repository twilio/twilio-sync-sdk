from boto.s3.connection import S3Connection, OrdinaryCallingFormat

import json
import os


__all__ = ['get_bucket', 'get_routing_rules', 'update_routing_rules']


def get_bucket(environment):
    """
    Get the SDK S3 bucket for the given realm, e.g. media.twiliocdn.com. This
    requires AWS credentials for AWS user cdn-sdki in either a JSON file at the
    root of this project or in environment variables.
    """
    def get_aws_creds(environment, aws_user):
        if (os.getenv('AWS_' + environment.upper() + '_ACCESS_KEY_ID') and
                os.getenv('AWS_' + environment.upper() + '_SECRET_ACCESS_KEY')):
            return (os.getenv('AWS_' + environment.upper() + '_ACCESS_KEY_ID'),
                    os.getenv('AWS_' + environment.upper() + '_SECRET_ACCESS_KEY'))
        aws_user_json_path = os.path.join(
            os.path.dirname(os.path.realpath(__file__)),
            '../{}.{}.json'.format(aws_user, environment))
        with open(aws_user_json_path) as aws_user_json_file:
            aws_user_json = json.loads(aws_user_json_file.read())
            return (aws_user_json['AccessKeyId'],
                    aws_user_json['SecretAccessKey'])
        raise (Exception(
            'Unable to find AWS credentials for user {} in realm {}'.format(
                aws_user, environment)))

    def create_s3_conn(environment, aws_user):
        key_id, secret_key = get_aws_creds(environment, aws_user)
        return S3Connection(aws_access_key_id=key_id,
                            aws_secret_access_key=secret_key,
                            calling_format=OrdinaryCallingFormat())

    conn = create_s3_conn(environment, 'cdn-sdki')
    bucket_name_prefix = environment
    if environment == 'prod':
        bucket_name_prefix = 'media'
    bucket_name = bucket_name_prefix + '.twiliocdn.com'
    bucket = conn.get_bucket(bucket_name)
    return bucket


def get_routing_rules(realm):
    bucket = get_bucket(realm)
    config = bucket.get_website_configuration_obj()
    print(config.routing_rules.to_xml())


def load_routing_rules(routing_rules_xml_file_path):
    class ToXML(object):
        def __init__(self, xml):
            self.xml = xml

        def to_xml(self):
            return self.xml
    with open(routing_rules_xml_file_path) as routing_rules_xml_file:
        return ToXML(routing_rules_xml_file.read())


def update_routing_rules(realm, routing_rules, dry_run=True):
    bucket = get_bucket(realm)
    config = bucket.get_website_configuration_obj()
    if not dry_run:
        bucket.configure_website(suffix=config.suffix,
                                 error_key=config.error_key,
                                 routing_rules=routing_rules)
