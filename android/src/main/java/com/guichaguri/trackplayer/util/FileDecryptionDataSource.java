package com.guichaguri.trackplayer.util;


import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.guichaguri.trackplayer.TrackPlayer;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * A {@link DataSource} for reading encrypt local files.
 */
public final class FileDecryptionDataSource implements DataSource {


    private TrackPlayer mVideoPackage;

    /**
     * Thrown when IOException is encountered during local file read operation.
     */
    public static class FileDataSourceException extends IOException {

        public FileDataSourceException(IOException cause) {
            super(cause);
        }

    }

    private RandomAccessFile file;
    private Uri uri;
    private long bytesRemaining;
    private boolean opened;
    private boolean mDecrypt;

    public FileDecryptionDataSource(Context context) {
        this(context, null, true);
    }

    /**
     * @param listener An optional listener.
     */
    public FileDecryptionDataSource(Context context, TransferListener listener, boolean decrypt) {
        try {
            Field field = context.getClass().getDeclaredField("mTrackPlayer");
            field.setAccessible(true);
            mVideoPackage = (TrackPlayer) field.get(context);
            this.mDecrypt = decrypt;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {

    }

    @Override
    public long open(DataSpec dataSpec) throws FileDataSource.FileDataSourceException {
        try {
            uri = dataSpec.uri;
            file = new RandomAccessFile(dataSpec.uri.getPath(), "r");
            file.seek(dataSpec.position);
            bytesRemaining = dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            throw new FileDataSource.FileDataSourceException(e);
        }

        opened = true;

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws FileDataSource.FileDataSourceException {
        if (readLength == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int bytesRead;
            try {
                bytesRead = file.read(buffer, offset, (int) Math.min(bytesRemaining, readLength));
                if (mVideoPackage != null && this.mDecrypt) {
                    mVideoPackage.decrypt(buffer, offset, (int) Math.min(bytesRemaining, readLength));
                }
            } catch (IOException e) {
                throw new FileDataSource.FileDataSourceException(e);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
            }

            return bytesRead;
        }
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return null;
    }

    @Override
    public void close() throws FileDataSource.FileDataSourceException {
        uri = null;
        try {
            if (file != null) {
                file.close();
            }
        } catch (IOException e) {
            throw new FileDataSource.FileDataSourceException(e);
        } finally {
            file = null;
            if (opened) {
                opened = false;
            }
        }
    }

}
