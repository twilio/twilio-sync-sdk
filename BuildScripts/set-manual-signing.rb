#!/usr/bin/env ruby
#
# A script that changes the 'CODE_SIGN_STYLE' build setting to 'Manual' for a given target.
# Run this script from within a bash script which prepares environment variables or pass
# required environment variables:
#   - SHORT_NAME
#   - FRAMEWORK_NAME
#
# Usage:
#   set-manual-signing.rb <target-name>
#
require 'xcodeproj'

raise "Usage: set-manual-signing.rb <target-name> <provision-profile-name> <path/to/xcodeproj>" unless ARGV.length == 3

project_path = ARGV[2]
begin
    project = Xcodeproj::Project.open(project_path)

    puts "Enabling testability for all targets"
    project.build_configurations.each do |config|
        config.build_settings["ENABLE_TESTABILITY"] = "YES"
    end

    test_host = project.native_targets.find { |target| target.name == ARGV[0] }
    puts "Found target #{test_host}"

    test_host.build_configurations.each do |config|
        puts "Updating signing for #{config}"
        config.build_settings["CODE_SIGN_STYLE"] = "Manual"
        config.build_settings["PROVISIONING_PROFILE_SPECIFIER"] = ARGV[1]
    end
ensure
    puts "Saving the project"
    project.save
end
