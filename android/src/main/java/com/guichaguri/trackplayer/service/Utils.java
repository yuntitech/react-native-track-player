package com.guichaguri.trackplayer.service;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.facebook.react.bridge.Promise;
import com.facebook.react.modules.network.OkHttpClientProvider;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

/**
 * @author Guichaguri
 */
public class Utils {

    public static final String EVENT_INTENT = "com.guichaguri.trackplayer.event";
    public static final String CONNECT_INTENT = "com.guichaguri.trackplayer.connect";
    public static final String LOG = "RNTrackPlayer";

    public static Runnable toRunnable(final Promise promise) {
        return new Runnable() {
            @Override
            public void run() {
                promise.resolve(null);
            }
        };
    }

    public static long toMillis(double seconds) {
        return (long) (seconds * 1000);
    }

    public static double toSeconds(long millis) {
        return millis / 1000D;
    }

    public static boolean isLocal(Uri uri) {
        if (uri == null) return false;

        String scheme = uri.getScheme();
        String host = uri.getHost();

        return scheme == null ||
                scheme.equals(ContentResolver.SCHEME_FILE) ||
                scheme.equals(ContentResolver.SCHEME_ANDROID_RESOURCE) ||
                scheme.equals(ContentResolver.SCHEME_CONTENT) ||
                scheme.equals("res") ||
                host == null ||
                host.equals("localhost") ||
                host.equals("127.0.0.1") ||
                host.equals("[::1]");
    }

    public static Uri getUri(Context context, Bundle data, String key) {
        if (!data.containsKey(key)) return null;
        Object obj = data.get(key);

        if (obj instanceof String) {
            // Remote or Local Uri

            return Uri.parse((String) obj);

        } else if (obj instanceof Bundle) {
            // require/import

            String uri = ((Bundle) obj).getString("uri");

            ResourceDrawableIdHelper helper = ResourceDrawableIdHelper.getInstance();
            int id = helper.getResourceDrawableId(context, uri);

            if (id > 0) {
                // In production, we can obtain the resource uri
                Resources res = context.getResources();

                return new Uri.Builder()
                        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                        .authority(res.getResourcePackageName(id))
                        .appendPath(res.getResourceTypeName(id))
                        .appendPath(res.getResourceEntryName(id))
                        .build();
            } else {
                // During development, the resources might come directly from the metro server
                return Uri.parse(uri);
            }

        }

        return null;
    }

    public static boolean isPlaying(int state) {
        return state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_BUFFERING;
    }

    public static boolean isPaused(int state) {
        return state == PlaybackStateCompat.STATE_PAUSED;
    }

    public static boolean isStopped(int state) {
        return state == PlaybackStateCompat.STATE_NONE || state == PlaybackStateCompat.STATE_STOPPED;
    }

    public static RatingCompat getRating(Bundle data, String key, int ratingType) {
        if (!data.containsKey(key)) {
            return RatingCompat.newUnratedRating(ratingType);
        } else if (ratingType == RatingCompat.RATING_HEART) {
            return RatingCompat.newHeartRating(data.getBoolean(key, true));
        } else if (ratingType == RatingCompat.RATING_THUMB_UP_DOWN) {
            return RatingCompat.newThumbRating(data.getBoolean(key, true));
        } else if (ratingType == RatingCompat.RATING_PERCENTAGE) {
            return RatingCompat.newPercentageRating(data.getFloat(key, 0));
        } else {
            return RatingCompat.newStarRating(ratingType, data.getFloat(key, 0));
        }
    }

    public static void setRating(Bundle data, String key, RatingCompat rating) {
        if (!rating.isRated()) return;
        int ratingType = rating.getRatingStyle();

        if (ratingType == RatingCompat.RATING_HEART) {
            data.putBoolean(key, rating.hasHeart());
        } else if (ratingType == RatingCompat.RATING_THUMB_UP_DOWN) {
            data.putBoolean(key, rating.isThumbUp());
        } else if (ratingType == RatingCompat.RATING_PERCENTAGE) {
            data.putDouble(key, rating.getPercentRating());
        } else {
            data.putDouble(key, rating.getStarRating());
        }
    }

    public static DataSource.Factory buildDataSourceFactory(Context context, DefaultBandwidthMeter bandwidthMeter) {
        Context appContext = context.getApplicationContext();
        return new DefaultDataSourceFactory(appContext, bandwidthMeter,
                buildHttpDataSourceFactory(appContext, bandwidthMeter));
    }

    private static HttpDataSource.Factory buildHttpDataSourceFactory(Context context, DefaultBandwidthMeter bandwidthMeter) {
        return new OkHttpDataSourceFactory(OkHttpClientProvider.getOkHttpClient(), "bookln_saas", bandwidthMeter);
    }

}
