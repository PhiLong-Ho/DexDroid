package dev.dexdroid;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.Predicate;

/** Visited modal destinations; callers own rendering and per-page view state. */
final class MenuNavigation<T> {
    private final ArrayDeque<T> parents = new ArrayDeque<>();
    private T current;

    T current() {
        return current;
    }

    void open(T next) {
        if (Objects.equals(current, next)) return;
        if (current != null) parents.push(current);
        current = next;
    }

    void replace(T next) {
        current = next;
    }

    boolean hasParent(Predicate<T> usable) {
        return parents.stream().anyMatch(usable);
    }

    T back(Predicate<T> usable) {
        while (!parents.isEmpty()) {
            T next = parents.pop();
            if (usable.test(next)) return current = next;
        }
        current = null;
        return null;
    }

    void clear() {
        parents.clear();
        current = null;
    }
}
