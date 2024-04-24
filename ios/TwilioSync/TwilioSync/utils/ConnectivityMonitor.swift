//
//  ConnectivityMonitor.swift
//  TwilioSync
//
//  Created by Dmitry Kalita on 19.03.2024.
//
//  Copyright © Twilio, Inc. All rights reserved.
//

import Foundation
import Network
import TwilioSyncLib

class ConnectivityMonitor : TwilsockConnectivityMonitor {
    
    var isNetworkAvailable: Bool = true
    
    var onChanged: () -> Void = {}
    
    private let monitor = NWPathMonitor()
    
    private let logger = KotlinLogger("ConnectivityMonitor")

    init() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            logger.d { "pathUpdateHandler path.status: \(path.status)" }

            let isAvailable = path.status == .satisfied
            
            if (isNetworkAvailable != isAvailable) {
                isNetworkAvailable = isAvailable
                onChanged()
            }
        }
    }
    
    func start() {
        monitor.start(queue: DispatchQueue.global(qos: .background))
    }
    
    func stop() {
        monitor.cancel()
    }
}
