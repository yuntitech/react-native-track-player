package com.guichaguri.trackplayer.module;

import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.facebook.react.bridge.*;
import com.google.android.exoplayer2.C;
import com.guichaguri.trackplayer.service.MusicBinder;
import com.guichaguri.trackplayer.service.MusicService;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * @author Guichaguri
 */
public class MusicModule extends ReactContextBaseJavaModule implements ServiceConnection {

    private MusicBinder binder;
    private MusicEvents eventHandler;
    private ArrayDeque<Runnable> initCallbacks = new ArrayDeque<>();
    private boolean connecting = false;

    public MusicModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "TrackPlayerModule";
    }

    @Override
    public void initialize() {
        ReactContext context = getReactApplicationContext();
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);

        eventHandler = new MusicEvents(context);
        manager.registerReceiver(eventHandler, new IntentFilter(Utils.EVENT_INTENT));
    }

    @Override
    public void onCatalystInstanceDestroy() {
        ReactContext context = getReactApplicationContext();

        if (eventHandler != null) {
            LocalBroadcastManager manager = LocalBroadcastManager.getInstance(context);

            manager.unregisterReceiver(eventHandler);
            eventHandler = null;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (MusicBinder) service;
        connecting = false;

        // Triggers all callbacks
        while (!initCallbacks.isEmpty()) {
            binder.post(initCallbacks.remove());
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
        connecting = false;
    }

    private void waitForConnection(Runnable r) {
        if (binder != null) {
            binder.post(r);
            return;
        } else {
            initCallbacks.add(r);
        }

        if (connecting) return;

        ReactApplicationContext context = getReactApplicationContext();

        // Binds the service to get a MediaWrapper instance
        Intent intent = new Intent(context, MusicService.class);
        context.startService(intent);
        intent.setAction(Utils.CONNECT_INTENT);
        context.bindService(intent, this, 0);

        connecting = true;
    }

    /* ****************************** API ****************************** */

    @Nullable
    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();

        // Capabilities
        constants.put("CAPABILITY_PLAY", PlaybackStateCompat.ACTION_PLAY);
        constants.put("CAPABILITY_PLAY_FROM_ID", PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID);
        constants.put("CAPABILITY_PLAY_FROM_SEARCH", PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
        constants.put("CAPABILITY_PAUSE", PlaybackStateCompat.ACTION_PAUSE);
        constants.put("CAPABILITY_STOP", PlaybackStateCompat.ACTION_STOP);
        constants.put("CAPABILITY_SEEK_TO", PlaybackStateCompat.ACTION_SEEK_TO);
        constants.put("CAPABILITY_SKIP", PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM);
        constants.put("CAPABILITY_SKIP_TO_NEXT", PlaybackStateCompat.ACTION_SKIP_TO_NEXT);
        constants.put("CAPABILITY_SKIP_TO_PREVIOUS", PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
        constants.put("CAPABILITY_SET_RATING", PlaybackStateCompat.ACTION_SET_RATING);
        constants.put("CAPABILITY_JUMP_FORWARD", PlaybackStateCompat.ACTION_FAST_FORWARD);
        constants.put("CAPABILITY_JUMP_BACKWARD", PlaybackStateCompat.ACTION_REWIND);

        // States
        constants.put("STATE_NONE", PlaybackStateCompat.STATE_NONE);
        constants.put("STATE_PLAYING", PlaybackStateCompat.STATE_PLAYING);
        constants.put("STATE_PAUSED", PlaybackStateCompat.STATE_PAUSED);
        constants.put("STATE_STOPPED", PlaybackStateCompat.STATE_STOPPED);
        constants.put("STATE_BUFFERING", PlaybackStateCompat.STATE_BUFFERING);

        // Rating Types
        constants.put("RATING_HEART", RatingCompat.RATING_HEART);
        constants.put("RATING_THUMBS_UP_DOWN", RatingCompat.RATING_THUMB_UP_DOWN);
        constants.put("RATING_3_STARS", RatingCompat.RATING_3_STARS);
        constants.put("RATING_4_STARS", RatingCompat.RATING_4_STARS);
        constants.put("RATING_5_STARS", RatingCompat.RATING_5_STARS);
        constants.put("RATING_PERCENTAGE", RatingCompat.RATING_PERCENTAGE);

        return constants;
    }

    @ReactMethod
    public void setupPlayer(ReadableMap data, final Promise promise) {
        final Bundle options = Arguments.toBundle(data);
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.setupPlayer(options, promise);
            }
        });
    }

    @ReactMethod
    public void destroy() {
        if (binder != null) binder.destroy();

        ReactContext context = getReactApplicationContext();
        if (context != null) context.unbindService(this);
    }

    @ReactMethod
    public void updateOptions(ReadableMap data, final Promise callback) {
        final Bundle options = Arguments.toBundle(data);

        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.updateOptions(options);
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void add(ReadableArray tracks, final String insertBeforeId, final Promise callback) {
        final ArrayList bundleList = Arguments.toList(tracks);

        waitForConnection(new Runnable() {
            @Override
            public void run() {
                // TODO: reject if missing attributes (not valid Track object)
                List<Track> trackList = Track.createTracks(getReactApplicationContext(), bundleList, binder.getRatingType());

                List<Track> queue = binder.getPlayback().getQueue();
                int index = -1;

                if (insertBeforeId != null) {
                    for (int i = 0; i < queue.size(); i++) {
                        if (queue.get(i).id.equals(insertBeforeId)) {
                            index = i;
                            break;
                        }
                    }
                } else {
                    index = queue.size();
                }

                if (index == -1) {
                    callback.reject("track_not_in_queue", "Given track ID was not found in queue");
                } else if (trackList == null || trackList.isEmpty()) {
                    callback.reject("invalid_track_object", "Track is missing a required key");
                } else if (trackList.size() == 1) {
                    binder.getPlayback().add(trackList.get(0), index, callback);
                } else {
                    binder.getPlayback().add(trackList, index, callback);
                }
            }
        });
    }

    @ReactMethod
    public void remove(ReadableArray tracks, final Promise callback) {
        final ArrayList trackList = Arguments.toList(tracks);

        waitForConnection(new Runnable() {
            @Override
            public void run() {
                List<Track> queue = binder.getPlayback().getQueue();
                List<Integer> indexes = new ArrayList<>();

                for (Object o : trackList) {
                    String id = o.toString();

                    for (int i = 0; i < queue.size(); i++) {
                        if (queue.get(i).id.equals(id)) {
                            indexes.add(i);
                            break;
                        }
                    }
                }

                if (indexes.size() > 0) {
                    binder.getPlayback().remove(indexes, callback);
                }
            }
        });
    }

    @ReactMethod
    public void removeUpcomingTracks(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().removeUpcomingTracks();
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void skip(final String track, final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().skip(track, callback);
            }
        });
    }

    @ReactMethod
    public void skipToNext(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().skipToNext(callback);
            }
        });
    }

    @ReactMethod
    public void skipToPrevious(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().skipToPrevious(callback);
            }
        });
    }

    @ReactMethod
    public void reset(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().reset();
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void play(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().play();
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void pause(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().pause();
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void stop(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().stop();
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void seekTo(final float seconds, final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                long secondsToSkip = Utils.toMillis(seconds);
                binder.getPlayback().seekTo(secondsToSkip);
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void setVolume(final float volume, final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().setVolume(volume);
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void getVolume(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                callback.resolve(binder.getPlayback().getVolume());
            }
        });
    }

    @ReactMethod
    public void setRate(final float rate, final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                binder.getPlayback().setRate(rate);
                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void getRate(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                callback.resolve(binder.getPlayback().getRate());
            }
        });
    }

    @ReactMethod
    public void getTrack(final String id, final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                List<Track> tracks = binder.getPlayback().getQueue();

                for (Track track : tracks) {
                    if (track.id.equals(id)) {
                        callback.resolve(Arguments.fromBundle(track.originalItem));
                        return;
                    }
                }

                callback.resolve(null);
            }
        });
    }

    @ReactMethod
    public void getQueue(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                List queue = new ArrayList();
                List<Track> tracks = binder.getPlayback().getQueue();

                for (Track track : tracks) {
                    queue.add(track.originalItem);
                }

                callback.resolve(Arguments.fromList(queue));
            }
        });
    }

    @ReactMethod
    public void getCurrentTrack(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                Track track = binder.getPlayback().getCurrentTrack();

                if (track == null) {
                    callback.resolve(null);
                } else {
                    callback.resolve(track.id);
                }
            }
        });
    }

    @ReactMethod
    public void getDuration(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                long duration = binder.getPlayback().getDuration();

                if (duration == C.TIME_UNSET) {
                    callback.resolve(Utils.toSeconds(0));
                } else {
                    callback.resolve(Utils.toSeconds(duration));
                }
            }
        });
    }

    @ReactMethod
    public void getBufferedPosition(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                long position = binder.getPlayback().getBufferedPosition();

                if (position == C.POSITION_UNSET) {
                    callback.resolve(Utils.toSeconds(0));
                } else {
                    callback.resolve(Utils.toSeconds(position));
                }
            }
        });
    }

    @ReactMethod
    public void getPosition(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                long position = binder.getPlayback().getPosition();

                if (position == C.POSITION_UNSET) {
                    callback.reject("unknown", "Unknown position");
                } else {
                    callback.resolve(Utils.toSeconds(position));
                }
            }
        });
    }

    @ReactMethod
    public void getState(final Promise callback) {
        waitForConnection(new Runnable() {
            @Override
            public void run() {
                callback.resolve(binder.getPlayback().getState());
            }
        });
    }
}
