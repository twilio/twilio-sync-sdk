//
//  AssertTrowsTwilioError.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 30.01.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import XCTest
import TwilioSync

func assertThrowsTwilioError<T>(
    _ expression: @autoclosure () async throws -> T,
    file: StaticString = #filePath,
    line: UInt = #line,
    _ errorHandler: (_ error: TwilioError) -> Void = { _ in }
) async {
    do {
        _ = try await expression()
        // expected error to be thrown, but it was not
        XCTFail("assertThrowsTwilioError: Asynchronous call did not throw an error.", file: file, line: line)
    } catch let error as TwilioError {
        errorHandler(error)
    } catch {
        XCTFail("assertThrowsTwilioError: Asynchronous call has throw an unexpected error: \(error)", file: file, line: line)
    }
}
