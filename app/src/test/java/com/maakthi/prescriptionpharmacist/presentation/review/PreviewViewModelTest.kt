package com.maakthi.prescriptionpharmacist.presentation.review

import com.maakthi.prescriptionpharmacist.core.image.FakeImagePipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var pipeline: FakeImagePipeline

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        pipeline = FakeImagePipeline()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load shows real quality and upload size`() = runTest {
        val viewModel = PreviewViewModel(pipeline)
        viewModel.load("/tmp/final.png")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("/tmp/final.png", state.imagePath)
        assertEquals(3, state.uploadSizeKb) // 3 KB from the fake
        assertEquals(3200, state.quality?.width)
        assertFalse(state.isLoading)
        assertFalse(state.showError)
        assertEquals(listOf("/tmp/final.png"), pipeline.uploadCalls)
    }

    @Test
    fun `analysis is blocked without explicit consent`() = runTest {
        val viewModel = PreviewViewModel(pipeline)
        viewModel.load("/tmp/final.png")
        advanceUntilIdle()

        val events = mutableListOf<PreviewEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.startAnalysis()
        advanceUntilIdle()

        // Nothing leaves the device before the pharmacist consents.
        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `consent unblocks analysis`() = runTest {
        val viewModel = PreviewViewModel(pipeline)
        viewModel.load("/tmp/final.png")
        advanceUntilIdle()

        val events = mutableListOf<PreviewEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.setConsent(true)
        viewModel.startAnalysis()
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals(
            "/tmp/final.png",
            (events[0] as PreviewEvent.StartAnalysis).imagePath
        )
        job.cancel()
    }

    @Test
    fun `revoking consent blocks analysis again`() = runTest {
        val viewModel = PreviewViewModel(pipeline)
        viewModel.load("/tmp/final.png")
        advanceUntilIdle()

        val events = mutableListOf<PreviewEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.setConsent(true)
        viewModel.setConsent(false)
        viewModel.startAnalysis()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        job.cancel()
    }

    @Test
    fun `failed quality measurement surfaces an error`() = runTest {
        pipeline.qualityThrows = true
        val viewModel = PreviewViewModel(pipeline)
        viewModel.load("/tmp/broken.png")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showError)
        viewModel.dismissError()
        assertFalse(viewModel.state.value.showError)
    }
}
