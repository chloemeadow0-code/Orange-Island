package com.orangeisland.app.data

import org.junit.Assert.*
import org.junit.Test

class EmbeddingIndexerTest {

    @Test
    fun floatsToBytes_bytesToFloats_roundTrip() {
        val original = floatArrayOf(0.1f, -0.5f, 1.0f, 0.0f, 3.14f)
        val bytes = EmbeddingIndexer.floatsToBytes(original)
        val recovered = EmbeddingIndexer.bytesToFloats(bytes)
        assertEquals(original.size, recovered.size)
        for (i in original.indices) {
            assertEquals(original[i], recovered[i], 0.0001f)
        }
    }

    @Test
    fun floatsToBytes_bytesToFloats_empty() {
        val original = floatArrayOf()
        val bytes = EmbeddingIndexer.floatsToBytes(original)
        val recovered = EmbeddingIndexer.bytesToFloats(bytes)
        assertEquals(0, recovered.size)
    }

    @Test
    fun cosineSimilarity_identical() {
        val vec = floatArrayOf(1f, 2f, 3f)
        val sim = EmbeddingIndexer.cosineSimilarity(vec, vec)
        assertEquals(1.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarity_orthogonal() {
        val a = floatArrayOf(1f, 0f, 0f)
        val b = floatArrayOf(0f, 1f, 0f)
        val sim = EmbeddingIndexer.cosineSimilarity(a, b)
        assertEquals(0.0f, sim, 0.0001f)
    }

    @Test
    fun cosineSimilarity_knownValues() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(4f, 5f, 6f)
        val sim = EmbeddingIndexer.cosineSimilarity(a, b)
        // dot=32, normA=sqrt(14)=3.7417, normB=sqrt(77)=8.7750, sim=32/(3.7417*8.7750)=0.9746
        assertEquals(0.9746f, sim, 0.001f)
    }

    @Test
    fun cosineSimilarity_zeroVector() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        val sim = EmbeddingIndexer.cosineSimilarity(a, b)
        assertEquals(0.0f, sim, 0.0001f)
    }
}
