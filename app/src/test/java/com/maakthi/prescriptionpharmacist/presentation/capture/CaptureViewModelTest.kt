package com.maakthi.prescriptionpharmacist.presentation.capture

import com.maakthi.prescriptionpharmacist.core.image.FakeImagePipeline
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

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
    fun `gallery pick with readable image emits ImageReady`() = runTest {
        val viewModel = CaptureViewModel(pipeline)
        val events = mutableListOf<CaptureEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.onGalleryImagePicked("content://media/picked")
        advanceUntilIdle()

        assertEquals(listOf<String?>("content://media/picked"), pipeline.importCalls)
        assertEquals(1, events.size)
        assertEquals(pipeline.importResult, (events[0] as CaptureEvent.ImageReady).imagePath)
        assertEquals(null, viewModel.error.value)
        job.cancel()
    }

    @Test
    fun `gallery pick with unreadable image reports error`() = runTest {
        pipeline.importResult = null
        val viewModel = CaptureViewModel(pipeline)
        val events = mutableListOf<CaptureEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.onGalleryImagePicked("content://broken")
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(ErrorCode.IMAGE_UNREADABLE, viewModel.error.value)
        job.cancel()
    }

    @Test
    fun `captured photo with valid file emits ImageReady`() = runTest {
        val viewModel = CaptureViewModel(pipeline)
        val events = mutableListOf<CaptureEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.onPhotoCaptured("/tmp/rx_1.jpg")
        advanceUntilIdle()

        assertEquals(1, events.size)
        assertEquals("/tmp/rx_1.jpg", (events[0] as CaptureEvent.ImageReady).imagePath)
        job.cancel()
    }

    @Test
    fun `failed capture reports error`() = runTest {
        val viewModel = CaptureViewModel(pipeline)
        val events = mutableListOf<CaptureEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.onPhotoCaptured(null)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(ErrorCode.IMAGE_UNREADABLE, viewModel.error.value)

        viewModel.clearError()
        assertEquals(null, viewModel.error.value)
        job.cancel()
    }

    @Test
    fun `capture target comes from the pipeline`() = runTest {
        val viewModel = CaptureViewModel(pipeline)
        assertEquals(pipeline.captureTarget, viewModel.createCaptureTarget())
    }

    @Test
    fun `old temporary copies are cleaned on start`() = runTest {
        CaptureViewModel(pipeline)
        advanceUntilIdle()
        assertTrue(pipeline.cleanupCount >= 1)
    }
}
