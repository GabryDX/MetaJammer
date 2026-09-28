package com.heronikostudios.metajammer.di

import org.junit.Assert.assertNotNull
import org.junit.Test

class AppModuleTest {

    @Test
    fun testAppModuleIsNotNull() {
        assertNotNull("appModule should be defined", appModule)
    }
}
