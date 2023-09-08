package com.guichaguri.trackplayer.service;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

import com.facebook.react.HeadlessJsTaskService;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;

import javax.annotation.Nullable;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.session.MediaButtonReceiver;

/**
 * @author Guichaguri
 */
public class MusicService extends HeadlessJsTaskService {

    String notificationChannelName = "yunti_music_notification" ;

    MusicManager manager;
    Handler handler;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationCompat.Builder notificationBuilder ;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = createNotificationChannel("bookln_service_730", "音频通知");
            NotificationChannel channel = new NotificationChannel(channelId,notificationChannelName,manager.IMPORTANCE_HIGH) ;
            channel.enableLights(true);
            channel.setShowBadge(true);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
            notificationBuilder = new NotificationCompat.Builder(this, channelId );
        }else{
            notificationBuilder = new NotificationCompat.Builder(this) ;
        }
        try {
            startForeground(1, notificationBuilder.build());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId,String channelName){
        NotificationChannel notificationChannel =  new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        notificationChannel.setLightColor(Color.BLUE);
        notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(notificationChannel);
        return channelId;
    }

    @Nullable
    @Override
    protected HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        return new HeadlessJsTaskConfig("TrackPlayer", Arguments.createMap(), 0, true);
    }

    @Override
    public void onHeadlessJsTaskFinish(int taskId) {
        // Overridden to prevent the service from being terminated
    }

    public void emit(String event, Bundle data) {
        Intent intent = new Intent(Utils.EVENT_INTENT);

        intent.putExtra("event", event);
        if(data != null) intent.putExtra("data", data);

        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    public void destroy() {
        if(handler != null) {
            handler.removeMessages(0);
            handler = null;
        }

        if(manager != null) {
            manager.destroy();
            manager = null;
        }
    }

    private void onStartForeground() {
        boolean serviceForeground = false;

        if(manager != null) {
            // The session is only active when the service is on foreground
            serviceForeground = manager.getMetadata().getSession().isActive();
        }

        if(!serviceForeground) {
            ReactInstanceManager reactInstanceManager = getReactNativeHost().getReactInstanceManager();
            ReactContext reactContext = reactInstanceManager.getCurrentReactContext();

            // Checks whether there is a React activity
            if(reactContext == null || !reactContext.hasCurrentActivity()) {
//                String channel = null;
//
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    channel = NotificationChannel.DEFAULT_CHANNEL_ID;
//                }

                // Sets the service to foreground with an empty notification
//                startForeground(1, new NotificationCompat.Builder(this, channel).build());
                // Stops the service right after
                stopSelf();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if(Utils.CONNECT_INTENT.equals(intent.getAction())) {
            return new MusicBinder(this, manager);
        }

        return super.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null && Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
            // Check if the app is on background, then starts a foreground service and then ends it right after
            onStartForeground();

            if(manager != null) {
                MediaButtonReceiver.handleIntent(manager.getMetadata().getSession(), intent);
            }

            return START_NOT_STICKY;
        }

        manager = new MusicManager(this);
        handler = new Handler();

        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        destroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        if (manager == null || manager.shouldStopWithApp()) {
            stopSelf();
        }
    }
}
