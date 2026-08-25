package com.example.wangcai

import com.example.wangcai.data.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PetLogicTest {

    @Test
    fun testWeightCalculation() {
        val tareWeight = 100f // 碗重
        val currentGross = 250f // 当前总重 (碗 + 粮)
        val prevGross = 100f // 之前总重 (空碗)
        
        val netWeight = currentGross - tareWeight
        val prevNetWeight = prevGross - tareWeight
        val diff = netWeight - prevNetWeight
        
        assertEquals(150f, diff) // 应该增加了150g
    }

    @Test
    fun testEatCalculation() {
        val tareWeight = 100f
        val currentGross = 200f // 剩200g总重 (100g粮)
        val prevGross = 300f // 之前300g总重 (200g粮)
        
        val netWeight = currentGross - tareWeight
        val prevNetWeight = prevGross - tareWeight
        val diff = netWeight - prevNetWeight
        
        assertEquals(-100f, diff) // 应该减少了100g (吃了100g)
    }
}
