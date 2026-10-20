package dev.dexdroid

import java.util.ArrayDeque
import java.util.function.Predicate

/** Visited modal destinations; callers own rendering and per-page view state. */
class MenuNavigation<T> {
    private val parents: ArrayDeque<T?> = ArrayDeque<T?>()
    private var current: T? = null

    fun current(): T? {
        return current
    }

    fun open(next: T?) {
        if (current == next) return
        if (current != null) parents.push(current!!)
        current = next
    }

    fun replace(next: T?) {
        current = next
    }

    fun hasParent(usable: Predicate<T?>?): Boolean {
        return parents.stream()!!.anyMatch(usable)
    }

    fun back(usable: Predicate<T?>?): T? {
        while (!parents.isEmpty()) {
            val next = parents.pop()
            if (usable!!.test(next)) {
                current = next
                return next
            }
        }
        current = null
        return null
    }

    fun clear() {
        parents.clear()
        current = null
    }
}
