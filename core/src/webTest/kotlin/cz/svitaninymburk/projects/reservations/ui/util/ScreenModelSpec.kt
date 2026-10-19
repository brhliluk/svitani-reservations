package cz.svitaninymburk.projects.reservations.ui.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Dispatchers.Unconfined: block() below has no real suspension point, so the launched
// coroutine runs to completion synchronously on the calling thread/stack — no need for
// kotlinx-coroutines-test or runBlocking to await it before asserting.
private class TestModel : ScreenModel(CoroutineScope(Dispatchers.Unconfined)) {
    fun handleLeft() = handle("boom".left(), errorMessage = { it }, onSuccess = { error("must not run") })
    fun handleRight(sink: (Int) -> Unit) = handle(42.right(), errorMessage = { "err" }, onSuccess = sink)
    fun toastNow() = showToast("hi", ToastType.Success)

    fun runThrowing(loadingStates: MutableList<Boolean>) = run<String, Int>(
        loading = { loadingStates.add(it) },
        errorMessage = { it },
        block = { throw IllegalStateException("boom from block") },
    )

    fun runOk(loadingStates: MutableList<Boolean>) = run<String, Int>(
        loading = { loadingStates.add(it) },
        errorMessage = { it },
        block = { Either.Right(42) },
        onSuccess = {},
    )
}

class ScreenModelSpec {
    @Test
    fun handleLeftSetsErrorToastFromMappedMessage() {
        val m = TestModel()
        m.handleLeft()
        assertEquals("boom", m.toast?.message)
        assertEquals(ToastType.Error, m.toast?.type)
    }

    @Test
    fun handleRightRunsOnSuccessAndLeavesToastNull() {
        val m = TestModel()
        var seen: Int? = null
        m.handleRight { seen = it }
        assertEquals(42, seen)
        assertNull(m.toast)
    }

    @Test
    fun dismissToastClearsToast() {
        val m = TestModel()
        m.toastNow()
        assertEquals("hi", m.toast?.message)
        m.dismissToast()
        assertNull(m.toast)
    }

    @Test
    fun runCatchesThrownExceptionAsErrorToastAndResetsLoading() {
        val m = TestModel()
        val loadingStates = mutableListOf<Boolean>()
        m.runThrowing(loadingStates)
        assertEquals("boom from block", m.toast?.message)
        assertEquals(ToastType.Error, m.toast?.type)
        assertEquals(listOf(true, false), loadingStates)
    }

    @Test
    fun runOnSuccessLeavesToastNullAndResetsLoading() {
        val m = TestModel()
        val loadingStates = mutableListOf<Boolean>()
        m.runOk(loadingStates)
        assertNull(m.toast)
        assertEquals(listOf(true, false), loadingStates)
    }
}
