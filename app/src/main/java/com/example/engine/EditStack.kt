package com.example.engine

import android.graphics.Bitmap

/**
 * Snapshot of an editor state stored inside the Undo/Redo stacks.
 */
data class EditSnapshot(
    val bitmap: Bitmap,
    val adjustments: ImageAdjustments,
    val actionLabel: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Generic LIFO Stack manager for Undo & Redo history.
 * - Pushing a new state saves the previous state on the Undo stack and clears the Redo stack.
 * - Undoing pops from the Undo stack and pushes the current state onto the Redo stack.
 * - Redoing pops from the Redo stack and pushes the current state onto the Undo stack.
 */
class EditStack<T>(private val maxCapacity: Int = 20) {

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    fun push(previousState: T) {
        undoStack.addLast(previousState)
        if (undoStack.size > maxCapacity) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    fun undo(currentState: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentState)
        if (redoStack.size > maxCapacity) {
            redoStack.removeFirst()
        }
        return undoStack.removeLast()
    }

    fun redo(currentState: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentState)
        if (undoStack.size > maxCapacity) {
            undoStack.removeFirst()
        }
        return redoStack.removeLast()
    }

    /**
     * Jumps directly back [steps] items in the undo stack (1 = immediate previous state).
     */
    fun undoSteps(steps: Int, currentState: T): T? {
        if (steps <= 0 || steps > undoStack.size) return null
        var active = currentState
        repeat(steps) {
            active = undo(active) ?: return active
        }
        return active
    }

    /**
     * Jumps directly forward [steps] items in the redo stack (1 = immediate next state).
     */
    fun redoSteps(steps: Int, currentState: T): T? {
        if (steps <= 0 || steps > redoStack.size) return null
        var active = currentState
        repeat(steps) {
            active = redo(active) ?: return active
        }
        return active
    }

    fun peekUndo(): T? = undoStack.lastOrNull()
    fun peekRedo(): T? = redoStack.lastOrNull()

    /** Returns undo items ordered from top of stack (most recent) to oldest. */
    fun undoHistoryTopFirst(): List<T> = undoStack.asReversed().toList()

    /** Returns redo items ordered from top of stack (next to redo) to furthest. */
    fun redoHistoryTopFirst(): List<T> = redoStack.asReversed().toList()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
