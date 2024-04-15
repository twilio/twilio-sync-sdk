#!/usr/bin/env ruby
#
# Run kotlin-ios tests in simulatorArm64.
#
# The script is pretty simple. It runs the tests in the simulator and checks if there are any failed tests.
# If there are, it reruns the tests and checks again. If the same failed tests are still there, the script will exit with an error.
# To run the script, you need to provide the path to the test binary as an argument.

raise "Usage: ./run-tests-kotlin-ios.rb <TEST_BINARY> [MAX_RETRIES]" unless ARGV.length >= 1

# Executes a given command, prints and returns its output.
def exec_command(message, command, fail_on_error: true)
    puts "#{message} #{command}"

    output = []

    IO.popen(command) do |io|
        while line = io.gets
          puts line.chomp
          output << line.chomp
        end
        io.close
        raise "Command failed with code #{$?.exitstatus}: #{command}" if fail_on_error && !$?.success?
    end

    return output
end

TEST_BINARY = ARGV[0]
MAX_RETRIES = ARGV[1] ? ARGV[1].to_i : 2
IOS_SIMULATOR_DEVICE_ID = ENV['IOS_SIMULATOR_DEVICE_ID'] || (raise "IOS_SIMULATOR_DEVICE_ID env var is not set")

puts "TEST_BINARY: #{TEST_BINARY}"
puts "MAX_RETRIES: #{MAX_RETRIES}"
puts "IOS_SIMULATOR_DEVICE_ID: #{ENV['IOS_SIMULATOR_DEVICE_ID']}"

exec_command "Booting simulator:", "xcrun simctl boot #{IOS_SIMULATOR_DEVICE_ID}", fail_on_error: false

cmd_run_tests = "xcrun simctl spawn #{IOS_SIMULATOR_DEVICE_ID} #{TEST_BINARY}"
output = exec_command "Running tests:", cmd_run_tests, fail_on_error: false

regexp_ran = /\[==========\] (\d+) tests from \d+ test cases ran\. \(\d+ ms total\)/
ran_count = output.map { |line| line.match(regexp_ran)&.captures&.first }.compact.first

if ran_count.nil?
    puts "Tests execution error: Output doesn't contain passed tests count"
    exit 1
end

regexp_failed = /\[  FAILED  \] (.*) \(\d* ms\)/
failed_tests = output.map { |line| line.match(regexp_failed)&.captures&.first }.compact
flaky_tests = failed_tests

puts "#{ran_count} tests ran. #{failed_tests.size} failed."

MAX_RETRIES.times do |k|
    break if failed_tests.empty?

    puts "Rerun flaky tests: try #{k + 1}, Failed tests:"
    failed_tests.each { |test| puts test }

    # Rerun only failed tests
    cmd_run_failed_tests = "#{cmd_run_tests} --ktest_filter=#{failed_tests.join(':')}"
    output = exec_command "Running tests:", cmd_run_failed_tests, fail_on_error: false

    regexp_ok = /\[       OK \] (.*) \(\d* ms\)/
    ok_tests = output.map { |line| line.match(regexp_ok)&.captures&.first }.compact
    failed_tests -= ok_tests
end

# If any tests are still failed, remove them from the flaky tests list
flaky_tests -= failed_tests

if flaky_tests.any?
    puts "#{flaky_tests.size} FLAKY tests:"
    flaky_tests.each { |test| puts test }
end

if failed_tests.any?
    puts "#{failed_tests.size} tests FAILED:"
    failed_tests.each { |test| puts test }
    exit 1
end

puts "All tests passed."
