package com.piku.client.core.network.image

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageHttpOutcomeTest {

    @Test
    fun statusCodesHaveOneClassification() {
        assertEquals(ImageHttpOutcome.SUCCESS, classifyImageStatus(200))
        assertEquals(ImageHttpOutcome.SUCCESS, classifyImageStatus(206))
        assertEquals(ImageHttpOutcome.REDIRECT, classifyImageStatus(302))
        assertEquals(ImageHttpOutcome.OBJECT_REJECTED, classifyImageStatus(403))
        assertEquals(ImageHttpOutcome.OBJECT_REJECTED, classifyImageStatus(404))
        assertEquals(ImageHttpOutcome.ROUTE_TROUBLE, classifyImageStatus(408))
        assertEquals(ImageHttpOutcome.ROUTE_TROUBLE, classifyImageStatus(429))
        assertEquals(ImageHttpOutcome.ROUTE_TROUBLE, classifyImageStatus(500))
        assertEquals(ImageHttpOutcome.ROUTE_TROUBLE, classifyImageStatus(503))
    }

    @Test
    fun onlyRouteTroubleProvesTheLineIsBroken() {
        val broken = listOf(408, 429, 500, 502, 503, 504)
        val intact = listOf(400, 401, 403, 404, 410)

        broken.forEach { assertEquals(it.toString(), ImageHttpOutcome.ROUTE_TROUBLE, classifyImageStatus(it)) }
        intact.forEach { assertEquals(it.toString(), ImageHttpOutcome.OBJECT_REJECTED, classifyImageStatus(it)) }
    }

    @Test
    fun redirectsAreNotObjectRejections() {
        assertEquals(ImageHttpOutcome.REDIRECT, classifyImageStatus(301))
        assertEquals(ImageHttpOutcome.REDIRECT, classifyImageStatus(307))
    }
}
