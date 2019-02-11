package com.guichaguri.trackplayer.service.player;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.EventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.guichaguri.trackplayer.service.MusicManager;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Guichaguri
 */
public class ExoPlayback implements EventListener {

    private final Context context;
    private final MusicManager manager;
    private final SimpleExoPlayer player;
    private final SimpleCache cache;
    private final long cacheMaxSize;

    private ConcatenatingMediaSource source;
    private List<Track> queue = Collections.synchronizedList(new ArrayList<Track>());

    // https://github.com/google/ExoPlayer/issues/2728
    private int lastKnownWindow = C.INDEX_UNSET;
    private long lastKnownPosition = C.POSITION_UNSET;
    private int previousState = PlaybackStateCompat.STATE_NONE;

    public ExoPlayback(Context context, MusicManager manager, SimpleExoPlayer player, long maxCacheSize) {
        this.context = context;
        this.manager = manager;
        this.player = player;
        this.cacheMaxSize = maxCacheSize;

        if(cacheMaxSize > 0) {
            File cacheDir = new File(context.getCacheDir(), "TrackPlayer");
            cache = new SimpleCache(cacheDir, new LeastRecentlyUsedCacheEvictor(cacheMaxSize));
        } else {
            cache = null;
        }

        player.addListener(this);
        resetQueue();
    }

    private void resetQueue() {
        queue.clear();

        source = new ConcatenatingMediaSource();
        player.prepare(source);

        lastKnownWindow = C.INDEX_UNSET;
        lastKnownPosition = C.POSITION_UNSET;

        manager.onReset();
    }

    public DataSource.Factory enableCaching(DataSource.Factory ds) {
        if(cache == null || cacheMaxSize <= 0) return ds;

        return new CacheDataSourceFactory(cache, ds, CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, cacheMaxSize);
    }

    public List<Track> getQueue() {
        return queue;
    }

    public void add(Track track, int index, Promise promise) {
        queue.add(index, track);
        source.addMediaSource(index, track.toMediaSource(context, cacheMaxSize), Utils.toRunnable(promise));

        if (queue.size() == 1) {
            player.prepare(source);
        }
    }

    public void add(Collection<Track> tracks, int index, Promise promise) {
        List<MediaSource> trackList = new ArrayList<>();

        for(Track track : tracks) {
            trackList.add(track.toMediaSource(context, cacheMaxSize));
        }

        queue.addAll(index, tracks);
        source.addMediaSources(index, trackList, Utils.toRunnable(promise));

        if (queue.size() == tracks.size()) {
            player.prepare(source);
        }
    }

    public void remove(List<Integer> indexes, Promise promise) {
        Collections.sort(indexes);

        for(int i = indexes.size() - 1; i >= 0; i--) {
            int index = indexes.get(i);

            queue.remove(index);

            if(i == 0) {
                source.removeMediaSource(index, Utils.toRunnable(promise));
            } else {
                source.removeMediaSource(index, null);
            }
        }
    }

    public void removeUpcomingTracks() {
        int currentIndex = player.getCurrentWindowIndex();
        if (currentIndex == C.INDEX_UNSET) return;

        for (int i = queue.size() - 1; i > currentIndex; i--) {
            queue.remove(i);
            source.removeMediaSource(i, null);
        }
    }

    public Track getCurrentTrack() {
        int index = player.getCurrentWindowIndex();
        return index == C.INDEX_UNSET || index < 0 || index >= queue.size() ? null : queue.get(index);
    }

    public void skip(String id, Promise promise) {
        for(int i = 0; i < queue.size(); i++) {
            if(id.equals(queue.get(i).id)) {
                lastKnownWindow = player.getCurrentWindowIndex();
                lastKnownPosition = player.getCurrentPosition();

                seekToDefaultPosition(i);
                promise.resolve(null);
                return;
            }
        }

        promise.reject("track_not_in_queue", "Given track ID was not found in queue");
    }

    public void skipToPrevious(Promise promise) {
        int prev = player.getPreviousWindowIndex();

        if(prev == C.INDEX_UNSET) {
            promise.reject("no_previous_track", "There is no previous track");
            return;
        }

        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();

        seekToDefaultPosition(prev);
        promise.resolve(null);
    }

    public void skipToNext(Promise promise) {
        int next = player.getNextWindowIndex();

        if(next == C.INDEX_UNSET) {
            promise.reject("queue_exhausted", "There is no tracks left to play");
            return;
        }

        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();

        seekToDefaultPosition(next);
        promise.resolve(null);
    }

    public void play() {
        player.setPlayWhenReady(true);
    }

    public void pause() {
        player.setPlayWhenReady(false);
    }

    public void stop() {
        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();

        player.stop(false);
        player.seekTo(0);
    }

    public void reset() {
        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();

        player.stop(true);
        resetQueue();
    }

    public boolean isRemote() {
        return false;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    public long getDuration() {
        return player.getDuration();
    }

    public void seekTo(long time) {
        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();

        player.seekTo(time);
    }

    public float getVolume() {
        return player.getVolume();
    }

    public void setVolume(float volume) {
        player.setVolume(volume);
    }

    public float getRate() {
        return player.getPlaybackParameters().speed;
    }

    public void setRate(float rate) {
        player.setPlaybackParameters(new PlaybackParameters(rate, player.getPlaybackParameters().pitch));
    }

    public int getState() {
        switch(player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return player.getPlayWhenReady() ? PlaybackStateCompat.STATE_BUFFERING : PlaybackStateCompat.STATE_NONE;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            case Player.STATE_IDLE:
                return PlaybackStateCompat.STATE_NONE;
            case Player.STATE_READY:
                return player.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        }
        return PlaybackStateCompat.STATE_NONE;
    }

    public void destroy() {
        player.release();
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {
        Log.d(Utils.LOG, "onTimelineChanged: " + reason);

        if ((reason == Player.TIMELINE_CHANGE_REASON_PREPARED || reason == Player.TIMELINE_CHANGE_REASON_DYNAMIC) && !timeline.isEmpty()) {
            onPositionDiscontinuity(Player.DISCONTINUITY_REASON_INTERNAL);
        }
    }

    @Override
    public void onPositionDiscontinuity(int reason) {
        Log.d(Utils.LOG, "onPositionDiscontinuity: " + reason);

        if(lastKnownWindow != player.getCurrentWindowIndex()) {
            Track previous = lastKnownWindow == C.INDEX_UNSET ? null : queue.get(lastKnownWindow);
            Track next = getCurrentTrack();

            // Track changed because it ended
            // We'll use its duration instead of the last known position
            if (reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION && lastKnownWindow != C.INDEX_UNSET) {
                if (lastKnownWindow >= player.getCurrentTimeline().getWindowCount()) return;
                long duration = player.getCurrentTimeline().getWindow(lastKnownWindow, new Window()).getDurationMs();
                if(duration != C.TIME_UNSET) lastKnownPosition = duration;
            }

            manager.onTrackUpdate(previous, lastKnownPosition, next);
        }

        lastKnownWindow = player.getCurrentWindowIndex();
        lastKnownPosition = player.getCurrentPosition();
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {
        // Buffering updates
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        int state = getState();

        if(state != previousState) {
            if(Utils.isPlaying(state) && !Utils.isPlaying(previousState)) {
                manager.onPlay();
            } else if(Utils.isPaused(state) && !Utils.isPaused(previousState)) {
                manager.onPause();
            } else if(Utils.isStopped(state) && !Utils.isStopped(previousState)) {
                manager.onStop();
            }

            manager.onStateChange(state);
            previousState = state;

            if(state == PlaybackStateCompat.STATE_STOPPED) {
                manager.onEnd(getCurrentTrack(), getPosition());
            }
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        // Repeat mode update
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
        // Shuffle mode update
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        manager.onError("exoplayer", error.getCause().getMessage());
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        // Speed or pitch changes
    }

    @Override
    public void onSeekProcessed() {
        // Finished seeking
    }

    private void seekToDefaultPosition(int windowIndex) {
        //https://fabric.io/yunti/android/apps/com.yunti.zzm/issues/5c3d432af8b88c29634c6b2f?time=last-seven-days
        //先避免上面崩溃，还不知道什么情况下会进入到return
        Timeline timeline = player.getCurrentTimeline();
        if (timeline == null
                || windowIndex < 0
                || (!timeline.isEmpty() && windowIndex >= timeline.getWindowCount())) {
            return;
        }
        player.seekToDefaultPosition(windowIndex);
    }

}
