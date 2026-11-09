package de.maxhenkel.radio.radio;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author https://github.com/mrchaoss1
 */
public class IcyMetadataInputStream extends FilterInputStream {

    private final int metaInterval;
    private int bytesUntilMeta;

    public IcyMetadataInputStream(InputStream in, int metaInterval) {
        super(in);
        this.metaInterval = metaInterval;
        this.bytesUntilMeta = metaInterval;
    }

    @Override
    public int read() throws IOException {
        if (bytesUntilMeta == 0) {
            skipMetadata();
            bytesUntilMeta = metaInterval;
        }
        int b = in.read();
        if (b != -1) {
            bytesUntilMeta--;
        }
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (bytesUntilMeta == 0) {
            skipMetadata();
            bytesUntilMeta = metaInterval;
        }
        int toRead = Math.min(len, bytesUntilMeta);
        int read = in.read(b, off, toRead);
        if (read > 0) {
            bytesUntilMeta -= read;
        }
        return read;
    }

    private void skipMetadata() throws IOException {
        int lengthByte = in.read();
        if (lengthByte <= 0) {
            return;
        }
        long remaining = (long) lengthByte * 16L;
        while (remaining > 0L) {
            long skipped = in.skip(remaining);
            if (skipped <= 0L) {
                if (in.read() == -1) {
                    return;
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }
}
