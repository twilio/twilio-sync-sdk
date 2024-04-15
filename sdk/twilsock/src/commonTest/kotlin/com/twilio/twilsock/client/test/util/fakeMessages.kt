//
//  Twilio Twilsock Client
//
// Copyright © Twilio, Inc. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
//
package com.twilio.twilsock.client.test.util

import com.twilio.test.util.joinLines
import com.twilio.twilsock.client.Status
import com.twilio.util.json
import kotlinx.serialization.encodeToString

fun buildFakeMessage(headers: String, payload: String = ""): String {
    val postfix = if (payload.isNotEmpty()) "\r\n" else ""
    return "TWILSOCK V3.0 ${headers.length}\r\n$headers\r\n$payload$postfix"
}

fun fakeInitReply(requestId: String) = buildFakeMessage(
    headers = """
        {
           "method":"reply",
           "id":"$requestId",
           "payload_size":0,
           "status":{
              "code":200,
              "status":"OK"
           },
           "capabilities":[
              "client_update"
           ],
           "continuation_token":"eyJmb3JtYXQiOiJydGQtY3QtMSIsImVuZHBvaW50SWQiOiJ0d2kxLWYyZjNiODYzMGMwODRmYWM4YzMzNzFlZWQ4OTI5YzMyIiwiZ3JhbnRzIjp7ImlkZW50aXR5IjoiQ2xpZW50LTUwMGMtMTAwMHAiLCJjaGF0Ijp7InNlcnZpY2Vfc2lkIjoiSVM0MzhlZjNkZWQxNGY0Yzk4YmIwYjU3MWU3MTFmY2VjMCIsInB1c2hfY3JlZGVudGlhbF9zaWQiOiJDUmE3ODNmNTQ1Yjc1YTI3NTQ0ZjhmNmY1NmFlOGE3MDViIiwiZW5kcG9pbnRfaWQiOiJjb252ZXJzYXRpb25zOkNsaWVudC01MDBjLTEwMDBwOmRldmljZTEifSwiaXBfbWVzc2FnaW5nIjp7InNlcnZpY2Vfc2lkIjoiSVM0MzhlZjNkZWQxNGY0Yzk4YmIwYjU3MWU3MTFmY2VjMCIsInB1c2hfY3JlZGVudGlhbF9zaWQiOiJDUmE3ODNmNTQ1Yjc1YTI3NTQ0ZjhmNmY1NmFlOGE3MDViIiwiZW5kcG9pbnRfaWQiOiJjb252ZXJzYXRpb25zOkNsaWVudC01MDBjLTEwMDBwOmRldmljZTEifX19.b/d4SGPJ7AJC81nQqS+WvcCNqPEVfdrPcis1c5KuYmM",
           "continuation_token_status":{
              "reissued":true,
              "reissue_reason":"DIFFERENT_IDENTITY",
              "reissue_message":"Different identity"
           },
           "init_registrations":[
              {
                 "notification_ctx_id":"b47fee75-2cf1-4791-934a-987cc44a6e5a",
                 "type":"ers",
                 "product":"ip_messaging",
                 "message_types":[
                    "twilio.sync.event",
                    "com.twilio.rtd.cds.document",
                    "twilio.ipmsg.typing_indicator",
                    "com.twilio.rtd.cds.list",
                    "com.twilio.rtd.cds.map"
                 ]
              }
           ]
        }
    """.joinLines()
)

internal fun fakeUpdateTokenReply(requestId: String, status: Status = Status.Ok) = buildFakeMessage(
    headers = """
        {
           "method":"reply",
           "id":"$requestId",
           "payload_size":0,
           "status":${json.encodeToString(status)}
        }
    """.joinLines()
)

internal fun fakeUpstreamRequestReply(
    requestId: String,
    status: Status = Status.Ok,
    httpStatus: Status = Status.Ok,
    payload: String = "",
) = buildFakeMessage(
    headers = """
        {
           "method":"reply",
           "id":"$requestId",
           "payload_size":${payload.length},
           "payload_type":"text/plain",
           "status":${json.encodeToString(status)},
           "http_headers":{
              "server":"envoy",
              "date":"Fri, 11 Mar 2022 11:29:28 GMT",
              "content-type":"text/plain",
              "content-length":"15",
              "i-twilio-upstream-request-id":"RU15e6a81edfa8487eaaa203a4bbcd606b",
              "i-twilio-request-id":"RQ1c495347d2654744abe4bd5e21b48998",
              "vary":"Accept-Encoding",
              "x-shenanigans":"none",
              "strict-transport-security":"max-age=31536000",
              "x-envoy-upstream-service-time":"1"
           },
           "http_status":${json.encodeToString(httpStatus)}
        }
    """.joinLines(),

    payload = payload
)

fun fakeTooManyRequestsReply(requestId: String) = buildFakeMessage(
    headers = """
        {
           "method":"reply",
           "id":"$requestId",
           "payload_size":0,
           "status":{
              "code":429,
              "status":"TOO_MANY_REQUESTS",
              "description":"Too many requests",
              "errorCode":51202
           }
        }    
    """.joinLines()
)
