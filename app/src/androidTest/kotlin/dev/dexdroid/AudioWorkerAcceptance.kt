package dev.dexdroid

import android.app.Instrumentation
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Controlled Binder doubles: exercises real worker/Handler races without changing audio policy. */
internal class AudioWorkerAcceptance(private val runner: Instrumentation) {
    fun run(): String {
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val selecting = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val client = SessionAudio(Handler(Looper.getMainLooper())) {}
        val fake =
            Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "asBinder" -> Binder()
                    "audioBegin" -> {
                        calls.add("begin:${args!![0]}")
                        SessionAudioState(available = true).bundle()
                    }
                    "audioQuery" -> {
                        calls.add("query:${args!![0]}")
                        SessionAudioState(available = true).bundle()
                    }
                    "audioSelect" -> {
                        calls.add("select:${args!![0]}")
                        selecting.countDown()
                        check(complete.await(5, TimeUnit.SECONDS))
                        SessionAudioState(available = true, selected = "retired", confirmed = true)
                            .bundle()
                    }
                    "audioEnd" -> {
                        calls.add("end:${args!![0]}")
                        SessionAudioState().bundle()
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as IDeskService
        fun main(action: () -> Unit) = runner.runOnMainSync { action() }
        fun await(condition: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 5000
            while (!condition() && SystemClock.uptimeMillis() < end) Thread.sleep(10)
            check(condition())
        }
        try {
            main { client.begin(fake) }
            await { client.state.available }
            main { client.select("speaker") }
            check(selecting.await(5, TimeUnit.SECONDS))
            main {
                repeat(1000) { client.refresh() }
                client.end()
                client.begin(fake)
            }
            complete.countDown()
            await {
                client.state.available && !client.state.pending && client.state.selected.isEmpty()
            }
            val copy = synchronized(calls) { calls.toList() }
            check(copy.indexOf("select:1") < copy.indexOf("end:1"))
            check(copy.indexOf("end:1") < copy.indexOf("begin:2"))
            check(copy.count { it.startsWith("query:") } <= 2)
            check(client.state.selected != "retired")
            return "PASS controlled audio Stop compensates after in-flight selection before replacement begin\n" +
                "PASS controlled audio refresh flood is coalesced\n" +
                "PASS controlled stale audio completion cannot replace new session state\n" +
                releaseBarrier()
        } finally {
            complete.countDown()
            main { client.close() }
        }
    }

    fun retryAfterUnconfirmedEnd(): String {
        val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
        val permitCleanup = java.util.concurrent.atomic.AtomicBoolean(false)
        val client = SessionAudio(Handler(Looper.getMainLooper())) {}
        val fake =
            Proxy.newProxyInstance(
                IDeskService::class.java.classLoader,
                arrayOf(IDeskService::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "asBinder" -> Binder()
                    "audioBegin" -> {
                        calls.add("begin:${args!![0]}")
                        SessionAudioState(available = true).bundle()
                    }
                    "audioSelect" ->
                        SessionAudioState(available = true, selected = "speaker").bundle()
                    "audioQuery" -> SessionAudioState(available = true).bundle()
                    "audioEnd" -> {
                        calls.add("end:${args!![0]}")
                        if (!permitCleanup.get())
                            throw IllegalStateException("Controlled unavailable cleanup")
                        SessionAudioState().bundle()
                    }
                    else -> throw UnsupportedOperationException(method.name)
                }
            } as IDeskService
        fun main(action: () -> Unit) = runner.runOnMainSync { action() }
        fun await(condition: () -> Boolean) {
            val end = SystemClock.uptimeMillis() + 5000
            while (!condition() && SystemClock.uptimeMillis() < end) Thread.sleep(10)
            check(condition())
        }
        try {
            main { client.begin(fake) }
            await { client.state.available }
            main { client.select("speaker") }
            await { !client.state.pending && client.state.selected == "speaker" }
            main {
                client.end()
                client.begin(fake)
            }
            await { !client.state.pending && client.state.uncertain }
            check(!calls.contains("begin:2"))
            permitCleanup.set(true)
            main { client.end() }
            await { !client.state.pending && !client.state.uncertain }
            check(calls.count { it == "end:1" } == 2)
            check(!calls.contains("end:2"))
            return "PASS controlled uncertain old audio cleanup retains original epoch for Stop retry\n"
        } finally {
            permitCleanup.set(true)
            main { client.close() }
        }
    }

    fun releaseBarrier(): String {
        for (rejectEnd in listOf(false, true)) {
            val entered = CountDownLatch(1)
            val unblock = CountDownLatch(1)
            val done = CountDownLatch(2)
            val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
            val calls = java.util.Collections.synchronizedList(mutableListOf<String>())
            val client = SessionAudio(Handler(Looper.getMainLooper())) {}
            val fake =
                Proxy.newProxyInstance(
                    IDeskService::class.java.classLoader,
                    arrayOf(IDeskService::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "asBinder" -> Binder()
                        "audioBegin" -> {
                            entered.countDown()
                            check(unblock.await(5, TimeUnit.SECONDS))
                            calls.add("begin-completed")
                            SessionAudioState(available = true).bundle()
                        }
                        "audioEnd" -> {
                            calls.add("end")
                            if (rejectEnd) throw IllegalStateException("Controlled unconfirmed end")
                            SessionAudioState().bundle()
                        }
                        else -> throw UnsupportedOperationException(method.name)
                    }
                } as IDeskService
            try {
                runner.runOnMainSync { client.begin(fake) }
                check(entered.await(5, TimeUnit.SECONDS))
                runner.runOnMainSync {
                    repeat(2) {
                        client.end { ok ->
                            results.add(ok)
                            done.countDown()
                        }
                    }
                }
                check(results.isEmpty()) // active is still null while begin is blocked.
                unblock.countDown()
                check(done.await(5, TimeUnit.SECONDS))
                check(results == listOf(!rejectEnd, !rejectEnd))
                check(calls.indexOf("begin-completed") < calls.indexOf("end"))
                check(results.size == 2)
            } finally {
                unblock.countDown()
                runner.runOnMainSync { client.close() }
            }
        }
        val idle = SessionAudio(Handler(Looper.getMainLooper())) {}
        val idleDone = CountDownLatch(1)
        runner.runOnMainSync {
            idle.end {
                check(it)
                idleDone.countDown()
            }
            idle.close()
        }
        check(idleDone.await(5, TimeUnit.SECONDS))
        val closedDone = CountDownLatch(1)
        runner.runOnMainSync {
            idle.end {
                check(!it)
                closedDone.countDown()
            }
        }
        check(closedDone.await(5, TimeUnit.SECONDS))
        return "PASS audio cleanup barrier waits for in-flight begin and acknowledges each end once\n" +
            "PASS audio cleanup barrier reports unconfirmed end and closed owner without false success\n" +
            "PASS idle audio cleanup barrier survives close after queuing acknowledgement\n"
    }
}
