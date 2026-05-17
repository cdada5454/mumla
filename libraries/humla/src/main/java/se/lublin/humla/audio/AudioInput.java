/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package se.lublin.humla.audio;

import android.util.Log;

import se.lublin.humla.exception.AudioInitializationException;
import se.lublin.humla.exception.NativeAudioException;
import se.lublin.humla.protocol.AudioHandler;

/**
 * Created by andrew on 23/08/13.
 */
public class AudioInput implements Runnable {
    private static final String TAG = AudioInput.class.getName();

    public static final int[] SAMPLE_RATES = {48000, 44100, 16000, 8000};

    private long mNativeHandle;
    private final int mFrameSize;
    private final int mSampleRate;

    private boolean mRecording;

    public AudioInput(AudioInputListener listener, int audioSource, int targetSampleRate,
                      String echoCancellationMethod, boolean preprocessorEnabled)
            throws NativeAudioException, AudioInitializationException {
        if (!"webrtc".equals(echoCancellationMethod) && !"none".equals(echoCancellationMethod)) {
            Log.w(TAG, "Oboe input ignores legacy echo cancellation method: " + echoCancellationMethod);
        }

        try {
            // The native processing chain and Mumble Opus path operate on 10 ms frames at 48 kHz.
            // If the device cannot expose 48 kHz directly, AudioHandler will resample before encode.
            mNativeHandle = NativeAudioInput.nativeCreate(
                    listener,
                    AudioHandler.SAMPLE_RATE,
                    audioSource,
                    preprocessorEnabled);
        } catch (IllegalStateException | UnsatisfiedLinkError e) {
            throw new AudioInitializationException(e);
        }

        mSampleRate = NativeAudioInput.nativeGetSampleRate(mNativeHandle);
        mFrameSize = NativeAudioInput.nativeGetFrameSize(mNativeHandle);
        if (mSampleRate <= 0 || mFrameSize <= 0) {
            throw new AudioInitializationException("Unable to initialize Oboe AudioInput.");
        }
    }

    /**
     * Starts the recording thread.
     * Not thread-safe.
     */
    public void startRecording() {
        if (mNativeHandle == 0) return;
        NativeAudioInput.nativeStart(mNativeHandle);
        mRecording = true;
        Log.i(TAG, "started");
    }

    /**
     * Stops the record loop after the current iteration, joining it.
     * Not thread-safe.
     */
    public void stopRecording() {
        if(!mRecording) return;
        if (mNativeHandle != 0) {
            NativeAudioInput.nativeStop(mNativeHandle);
        }
        mRecording = false;
        Log.i(TAG, "stopped");
    }

    /**
     * Stops the record loop and waits on it to finish.
     * Releases native audio resources.
     * NOTE: It is not safe to call startRecording after.
     */
    public void shutdown() {
        stopRecording();
        if(mNativeHandle != 0) {
            NativeAudioInput.nativeDestroy(mNativeHandle);
            mNativeHandle = 0;
        }
    }

    public boolean isRecording() {
        return mNativeHandle != 0 && NativeAudioInput.nativeIsRecording(mNativeHandle);
    }

    /**
     * @return the sample rate used by the native Oboe input stream.
     */
    public int getSampleRate() {
        return mSampleRate;
    }

    /**
     * @return the frame size used, varying depending on the sample rate selected.
     */
    public int getFrameSize() {
        return mFrameSize;
    }

    @Override
    public void run() {
        startRecording();
    }

    public interface AudioInputListener {
        void onAudioInputReceived(short[] frame, int frameSize);
    }
}
