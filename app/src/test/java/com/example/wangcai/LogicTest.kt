package com.example.wangcai

import org.junit.Test
import org.junit.Assert.*

class LogicTest {
    @Test
    fun testWeightDiff() {
        val tare = 100.0
        val gross = 250.0
        val prevGross = 100.0
        
        val net = gross - tare
        val prevNet = prevGross - tare
        val diff = net - prevNet
        
        assertEquals(150.0, diff, 0.01)
    }
}
