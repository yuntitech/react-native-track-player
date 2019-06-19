package com.guichaguri.trackplayer.util;

import android.content.Context;

import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * A {@link DataSource.Factory} that produces {@link FileDecryptionDataSource}.
 * Created by kangqiang on 2017/7/20.
 */

public final class FileDecryptionDataSourceFactory implements DataSource.Factory {

    private final TransferListener listener;
    private Context mContext;
    private boolean mDecrypt;

    public FileDecryptionDataSourceFactory(Context context) {
        this(context, null, true);
    }

    public FileDecryptionDataSourceFactory(Context context, TransferListener listener, boolean decrypt) {
        this.listener = listener;
        this.mContext = context;
        this.mDecrypt = decrypt;
    }

    @Override
    public DataSource createDataSource() {
        return new FileDecryptionDataSource(mContext, listener, mDecrypt);
    }

}

