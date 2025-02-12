/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazon.alexa.auto.media.player;

import static androidx.media.AudioManagerCompat.AUDIOFOCUS_GAIN;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.amazon.alexa.auto.apps.common.util.Preconditions;
import com.amazon.alexa.auto.media.aacs.handlers.IMediaDuckingObserver;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages the Audio Focus for Media Playback.
 *
 * Making changes related to Audio Focus. Please test your changes with following:
 * https://developer.amazon.com/docs/fire-tv/testing-audio-focus.html
 */
public class MediaPlayerAudioFocusController implements AudioManager.OnAudioFocusChangeListener, AutoCloseable {
    private static final String TAG = MediaPlayerAudioFocusController.class.getSimpleName();

    @VisibleForTesting
    static final float VOLUME_MULTIPLIER_DUCK = 0.2f;
    @VisibleForTesting
    static final float VOLUME_MULTIPLIER_NORMAL = 1.0f;

    /**
     * Interface implemented by Player for playback control.
     */
    public interface PlayerController {
        /**
         * Start the playback now. This method is called when intent to
         * Play the media is established and only thing missing had been
         * the acquisition of audio focus.
         */
        void startPlaybackNow();

        /**
         * Request that media playback can be resumed, if it was terminated
         * on account of loosing audio focus transiently.
         */
        void requestResumingPlayback();

        /**
         * Request that media playback should be paused because audio focus
         * has been lost transiently.
         */
        void requestPausePlayback();

        /**
         * Request that media playback be stopped because audio focus has been
         * lost permanently.
         */
        void requestStopPlayback();

        /**
         * Adjust the media playback volume because either media players audio
         * focus has been ordered to duck, or if it is recovering from duck state
         * to normal state.
         *
         * @param volumeMultiplier Volume multiplier between 0 to 1.
         */
        void adjustPlaybackVolume(float volumeMultiplier);

        /**
         * Called when Audio focus could not be acquired for the playback.
         */
        void failedToAcquireFocus();
    }

    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({AUDIO_FOCUS_STATE_NONE, AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION, AUDIO_FOCUS_STATE_ACQUIRED,
            AUDIO_FOCUS_STATE_DUCKED, AUDIO_FOCUS_STATE_LOST_TRANSIENT})
    private @interface AudioFocusState {}

    private static final int AUDIO_FOCUS_STATE_NONE = 0;
    private static final int AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION = 1;
    private static final int AUDIO_FOCUS_STATE_ACQUIRED = 2;
    private static final int AUDIO_FOCUS_STATE_DUCKED = 3;
    private static final int AUDIO_FOCUS_STATE_LOST_TRANSIENT = 4;

    // Dependencies.
    @NonNull
    private final AudioManager mAudioManager;

    // Late Dependencies.
    private PlayerController mPlayerController;

    // Internal Dependencies.
    @NonNull
    private AudioFocusRequest mFocusRequest;

    // State.
    @AudioFocusState
    private int mCurrentState;

    private boolean mMayDuck;
    private IMediaDuckingObserver mMediaDuckingObserver;

    /**
     * Construct an instance of {@link MediaPlayerAudioFocusController}.
     *
     * @param audioManager {@link AudioManager} Android Audio Manager from which
     *        audio focus would be requested.
     */
    public MediaPlayerAudioFocusController(@NonNull AudioManager audioManager) {
        mAudioManager = audioManager;
        mCurrentState = AUDIO_FOCUS_STATE_NONE;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        Log.d(TAG,
                "Focus Changed. Current audio focus state: " + focusStateToString(mCurrentState)
                        + " Focus change: " + focusChangeToString(focusChange));

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                switch (mCurrentState) {
                    case AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION:
                        mPlayerController.startPlaybackNow();
                        break;
                    case AUDIO_FOCUS_STATE_LOST_TRANSIENT:
                        // Coming out of focus loss transient state.
                        mPlayerController.requestResumingPlayback();
                        break;
                    case AUDIO_FOCUS_STATE_ACQUIRED:
                        break; // Nothing to do, we already have a focus.
                    case AUDIO_FOCUS_STATE_NONE:
                        // Play since we gained focus
                        Log.v(TAG, "Focus acquired when state was none");
                        mPlayerController.startPlaybackNow();
                        break;
                }
                mCurrentState = AUDIO_FOCUS_STATE_ACQUIRED;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                mCurrentState = AUDIO_FOCUS_STATE_NONE;
                mPlayerController.requestStopPlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                mCurrentState = AUDIO_FOCUS_STATE_LOST_TRANSIENT;
                mPlayerController.requestPausePlayback();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                Log.w(TAG, "Received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK unexpectedly. setWillPauseWhenDucked is false");
                // Since AudioFocus request has setWillPauseWhenDucked false
                break;
            case AudioManager.AUDIOFOCUS_NONE:
                // Don't need to do anything since focus was not requested, gained, or lost
                mCurrentState = AUDIO_FOCUS_STATE_NONE;
                break;
        }
    }

    public void startDucking() {
        mPlayerController.adjustPlaybackVolume(VOLUME_MULTIPLIER_DUCK);
    }

    private void startClientsideDucking() {
        mPlayerController.adjustPlaybackVolume(VOLUME_MULTIPLIER_DUCK);
        if (mMediaDuckingObserver != null) {
            mMediaDuckingObserver.reportClientDuckingState(true);
        }
    }

    public void stopDucking() {
        mPlayerController.adjustPlaybackVolume(VOLUME_MULTIPLIER_NORMAL);
    }

    private void stopClientsideDucking() {
        mPlayerController.adjustPlaybackVolume(VOLUME_MULTIPLIER_NORMAL);
        if (mMediaDuckingObserver != null) {
            mMediaDuckingObserver.reportClientDuckingState(false);
        }
    }

    @Override
    public void close() {
        Log.i(TAG, "Disposing the Audio Focus Controller");
        abandonAudioFocus();
    }

    /**
     * Sets the {@link PlayerController} required by this object.
     *
     * @param controller PlayerController.
     */
    public void setPlayerController(@NonNull PlayerController controller) {
        mPlayerController = controller;
    }

    public void resetAudioFocusRequest() {
        mFocusRequest = makeAudioFocusRequest();
    }

    /**
     * Start the playback after acquiring the audio focus.
     *
     * The playback may not begin immediately if acquisition of focus
     * is delivered asynchronously.
     */
    public void startPlaybackAfterAcquiringFocus() {
        Preconditions.checkNotNull(mPlayerController);

        Log.d(TAG,
                "Start Playback (after acquiring focus). Current audio focus state: "
                        + focusStateToString(mCurrentState));

        switch (mCurrentState) {
            case AUDIO_FOCUS_STATE_ACQUIRED:
            case AUDIO_FOCUS_STATE_DUCKED:
                // Focus already acquired. Continue with playback.
                mPlayerController.startPlaybackNow();
                break;
            case AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION:
                Log.d(TAG, "Waiting for audio focus from prior request");
                break;
            case AUDIO_FOCUS_STATE_LOST_TRANSIENT:
            case AUDIO_FOCUS_STATE_NONE: {
                int focusResponse = requestAudioFocus();
                Log.d(TAG, "Audio focus response: " + focusResponse);

                switch (focusResponse) {
                    case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                        mCurrentState = AUDIO_FOCUS_STATE_ACQUIRED;
                        mPlayerController.startPlaybackNow();
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                        mCurrentState = AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION;
                        break;
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                    default:
                        mCurrentState = AUDIO_FOCUS_STATE_NONE;
                        mPlayerController.failedToAcquireFocus();
                        break;
                }
                break;
            }
        }
    }

    public void setMixability(boolean mayDuck, IMediaDuckingObserver audioPlayerHandler) {
        this.mMayDuck = mayDuck;
        mMediaDuckingObserver = audioPlayerHandler;
    }

    /**
     * Abandon the audio focus if we have acquired one.
     */
    public void relinquishAudioFocusIfCurrentlyAcquired() {
        Log.d(TAG, "Relinquishing Audio Focus. Current state: " + focusStateToString(mCurrentState));
        if (mCurrentState == AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION || mCurrentState == AUDIO_FOCUS_STATE_DUCKED
                || mCurrentState == AUDIO_FOCUS_STATE_ACQUIRED) {
            abandonAudioFocus();
            mCurrentState = AUDIO_FOCUS_STATE_NONE;
        }
    }

    /**
     * Request Audio Focus for Media Playback.
     *
     * @return Response returned by {@link AudioManager##requestAudioFocus()}
     */
    private int requestAudioFocus() {
        Log.d(TAG, "Requesting Audio Focus");
        return mAudioManager.requestAudioFocus(mFocusRequest);
    }

    /**
     * Abandon Audio Focus for Media Playback.
     *
     * @return Response returned by {@link AudioManager##abandonAudioFocusRequest()}
     */
    private int abandonAudioFocus() {
        Log.d(TAG, "Abandoning Audio Focus");
        if (mFocusRequest != null) {
            Log.d(TAG, "Abandoning Audio Focus");
            return mAudioManager.abandonAudioFocusRequest(mFocusRequest);
        } else {
            Log.d(TAG, "No audio focus request to abandon. Doing nothing.");
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
    }

    /**
     * Make Audio Focus request required by {@link AudioManager}.
     *
     * @return An instance of {@link AudioFocusRequest}.
     */
    private AudioFocusRequest makeAudioFocusRequest() {
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                                  .setUsage(AudioAttributes.USAGE_MEDIA)
                                                  // Todo: Set content type to speech for audibles
                                                  .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                  .build();

        return new AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this)
                .build();
    }

    /**
     * Convert Focus Change integer to String for logging.
     *
     * @param focusChange Focus change integer value.
     * @return String corresponding to input focus change.
     */
    private static String focusChangeToString(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                return "AUDIOFOCUS_GAIN";
            case AudioManager.AUDIOFOCUS_LOSS:
                return "AUDIOFOCUS_LOSS";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                return "AUDIOFOCUS_LOSS_TRANSIENT";
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                return "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                return "AUDIOFOCUS_GAIN_TRANSIENT";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                return "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                return "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
            case AudioManager.AUDIOFOCUS_NONE:
                return "AUDIOFOCUS_NONE";
            default:
                return "unknown" + focusChange;
        }
    }

    /**
     * Convert Focus State integer to String for logging.
     *
     * @param focusState Focus State integer value.
     * @return String corresponding to input focus state.
     */
    private static String focusStateToString(int focusState) {
        switch (focusState) {
            case AUDIO_FOCUS_STATE_NONE:
                return "AUDIO_FOCUS_STATE_NONE";
            case AUDIO_FOCUS_STATE_ACQUIRED:
                return "AUDIO_FOCUS_STATE_ACQUIRED";
            case AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION:
                return "AUDIO_FOCUS_STATE_WAITING_FOR_ACQUISITION";
            case AUDIO_FOCUS_STATE_DUCKED:
                return "AUDIO_FOCUS_STATE_DUCKED";
            case AUDIO_FOCUS_STATE_LOST_TRANSIENT:
                return "AUDIO_FOCUS_STATE_LOST_TRANSIENT";
            default:
                return "unrecognized:" + focusState;
        }
    }
}
