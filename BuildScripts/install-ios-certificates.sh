set -x

[ -z "$CIRCLECI" ] && echo "Nothing to do on non-circleci node" && exit 0
[ -z "$MATCH_FILE" ] && echo "MATCH_FILE env var is undefined. Add proper circleci context" && exit 1

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

PLATFORM=ios
echo "$MATCH_FILE" | base64 --decode > Matchfile
fastlane match development --readonly --keychain_name $KEYCHAIN_NAME --platform $PLATFORM
