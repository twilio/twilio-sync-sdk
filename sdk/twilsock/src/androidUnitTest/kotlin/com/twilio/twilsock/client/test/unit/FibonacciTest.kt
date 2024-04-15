package com.twilio.twilsock.client.test.unit

import com.twilio.util.fibonacci
import kotlin.test.Test
import kotlin.test.assertEquals

class FibonacciTest {

    @Test
    fun test() {
        assertEquals(0, fibonacci(0).toInt())
        assertEquals(1, fibonacci(1).toInt())
        assertEquals(1, fibonacci(2).toInt())
        assertEquals(2, fibonacci(3).toInt())
        assertEquals(3, fibonacci(4).toInt())
        assertEquals(5, fibonacci(5).toInt())
        assertEquals(8, fibonacci(6).toInt())
        assertEquals(12586269025, fibonacci(50).toLong())
    }
}
