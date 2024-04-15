//
//  TestActors.swift
//  TwilioSyncTests
//
//  Created by Dmitry Kalita on 07.02.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation

actor ListActor<T> {
    var list: [T] = []
    
    func append(_ value: T) {
        list.append(value)
    }
}
