package com.maakthi.prescriptionpharmacist.presentation.capture

import com.maakthi.prescriptionpharmacist.core.image.CropWindow
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
class CropEditViewModelTest {

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
    fun `load populates state from pipeline`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals("/tmp/source.jpg", state.originalPath)
        assertEquals(3200, state.imageWidth)
        assertEquals(2400, state.imageHeight)
        assertFalse(state.isLoading)
    }

    @Test
    fun `rotate re-renders from the original with rotation applied`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.rotate90()
        advanceUntilIdle()

        // load() does not render; the rotation triggers the first render.
        assertEquals(1, pipeline.renderCalls.size)
        val (path, options) = pipeline.renderCalls[0]
        assertEquals("/tmp/source.jpg", path)
        assertEquals(90, options.rotationDegrees)
        assertEquals(90, viewModel.state.value.rotationDegrees)
    }

    @Test
    fun `flip toggles the horizontal flip`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.flip()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.flipHorizontal)

        viewModel.flip()
        advanceUntilIdle()
        assertFalse(viewModel.state.value.flipHorizontal)
    }

    @Test
    fun `enhancements are rendered with the requested values`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.setBrightness(1.4f)
        viewModel.setContrast(1.2f)
        viewModel.setSharpness(0.5f)
        viewModel.commitEnhancements()
        advanceUntilIdle()

        val options = pipeline.renderCalls.last().second
        assertEquals(1.4f, options.brightness, 0.001f)
        assertEquals(1.2f, options.contrast, 0.001f)
        assertEquals(0.5f, options.sharpness, 0.001f)
    }

    @Test
    fun `save emits Saved with a full window at zoom 1`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        val events = mutableListOf<CropEditEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        // Frame matches the fitted image exactly (0.25 * 3200x2400).
        viewModel.save(800 to 600)
        advanceUntilIdle()

        val (path, options) = pipeline.renderCalls.last()
        assertEquals("/tmp/source.jpg", path)
        assertEquals(CropWindow.FULL, options.cropWindow)
        assertEquals(1, events.size)
        assertEquals(pipeline.renderResult, (events[0] as CropEditEvent.Saved).imagePath)
        job.cancel()
    }

    @Test
    fun `save with zoom applies a smaller crop window`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.setZoom(2f)
        viewModel.save(800 to 600)
        advanceUntilIdle()

        val options = pipeline.renderCalls.last().second
        val window = requireNotNull(options.cropWindow)
        assertTrue(window.width < 1f)
        assertTrue(window.height < 1f)
        // Centered crop.
        assertEquals(0.25f, window.left, 0.001f)
        assertEquals(0.25f, window.top, 0.001f)
    }

    @Test
    fun `render failure shows error and emits nothing`() = runTest {
        pipeline.renderResult = null
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        val events = mutableListOf<CropEditEvent>()
        val job = launch { viewModel.events.collect { events += it } }

        viewModel.save(800 to 600)
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertTrue(viewModel.state.value.showError)
        job.cancel()
    }

    @Test
    fun `auto deskew compensates the measured skew`() = runTest {
        pipeline.qualityResult = pipeline.qualityResult.copy(skewDegrees = 5)
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.autoDeskew()
        advanceUntilIdle()

        assertEquals(355, viewModel.state.value.rotationDegrees)
        assertEquals(0, viewModel.state.value.skewDegrees)
    }

    @Test
    fun `reset neutralizes transforms and crop state`() = runTest {
        val viewModel = CropEditViewModel(pipeline)
        viewModel.load("/tmp/source.jpg")
        advanceUntilIdle()

        viewModel.rotate90()
        viewModel.setZoom(3f)
        viewModel.setBrightness(1.5f)
        advanceUntilIdle()
        viewModel.reset()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(0, state.rotationDegrees)
        assertEquals(1f, state.zoom, 0.001f)
        assertEquals(1f, state.brightness, 0.001f)
        assertEquals(0f, state.offsetX, 0.001f)
    }
}
