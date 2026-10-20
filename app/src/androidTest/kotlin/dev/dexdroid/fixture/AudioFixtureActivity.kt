package dev.dexdroid.fixture

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Process
import android.widget.TextView
import java.io.FileDescriptor
import java.io.PrintWriter

/** Separate-UID silent default-routed playback. No preferred device, volume or user media. */
class AudioFixtureActivity : Activity() {
    @Volatile private var running = false
    private var track: AudioTrack? = null
    private var playback: Thread? = null
    @Volatile private var frames = 0L
    @Volatile private var error = "none"

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val label = TextView(this)
        label.text = "DexDroid synthetic audio routing fixture\nSilent PCM, default media route"
        label.textSize = 24f
        label.setTextColor(-1)
        label.setBackgroundColor(-0x1000000)
        setContentView(label)
        val format =
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(16384)
                .build()
        running = true
        playback =
            Thread(
                    {
                        try {
                            track!!.play()
                            val silence = ShortArray(2048)
                            while (running) {
                                val written =
                                    track!!.write(
                                        silence,
                                        0,
                                        silence.size,
                                        AudioTrack.WRITE_BLOCKING,
                                    )
                                check(written > 0) { "PCM write rejected" }
                                frames += written
                            }
                        } catch (failure: Exception) {
                            error = failure.javaClass.simpleName
                        }
                    },
                    "Synthetic-audio-output",
                )
                .apply { start() }
    }

    override fun dump(
        prefix: String,
        fd: FileDescriptor?,
        writer: PrintWriter,
        args: Array<out String>?,
    ) {
        super.dump(prefix, fd, writer, args)
        writer.println(
            "AUDIO_FIXTURE uid=${Process.myUid()} routedType=${track?.routedDevice?.type ?: -1} frames=$frames error=$error"
        )
    }

    private fun stopPlayback() {
        running = false
        track?.pause()
        playback?.join(1000)
        track?.release()
        track = null
    }

    override fun onStop() {
        stopPlayback()
        super.onStop()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
