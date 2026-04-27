set -x

[ -z "$CIRCLECI" ] && echo "Nothing to do on non-circleci node" && exit 0

pushd `dirname $0`

PROJECT_DIR=./`git rev-parse --show-cdup`
KEYCHAIN_NAME=${1:-"fastlane_tmp_keychain"}
KEYCHAIN_FOLDER=$HOME/Library/Keychains

echo "Creating keychain $KEYCHAIN_FOLDER/$KEYCHAIN_NAME"
security create-keychain -p "" "$KEYCHAIN_FOLDER/$KEYCHAIN_NAME"
[ $? -eq 48 ] && echo "Keychain is already created"

echo "Setup keychain"
security default-keychain -s "$KEYCHAIN_FOLDER/$KEYCHAIN_NAME"
security unlock-keychain -p "" "$KEYCHAIN_FOLDER/$KEYCHAIN_NAME"
security set-keychain-settings -t 3600 -l -u

echo "Install certificates and profiles"

# Validate required environment variables
[ -z "$MATCH_GIT_URL" ] && echo "MATCH_GIT_URL env var is undefined. Add to rtd-ci-tokens circleci context" && exit 1
[ -z "$MATCH_GIT_BRANCH" ] && echo "MATCH_GIT_BRANCH env var is undefined. Add to rtd-ci-tokens circleci context" && exit 1
[ -z "$MATCH_USERNAME" ] && echo "MATCH_USERNAME env var is undefined. Add to rtd-ci-tokens circleci context" && exit 1

# Create Matchfile from environment variables (stored in CircleCI context)
cat > Matchfile << EOF
git_url("${MATCH_GIT_URL}")
git_branch("${MATCH_GIT_BRANCH}")
storage_mode("git")
type("development")
app_identifier(["com.twilio.rtd.*"])
username("${MATCH_USERNAME}")
EOF

fastlane match development --readonly --keychain_name $KEYCHAIN_NAME --platform ios
