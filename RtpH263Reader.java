package com.google.android.exoplayer2.source.rtsp.reader;

import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPacket;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
final class RtpH263Reader implements RtpPayloadReader {
    private static final int I_VOP = 0;
    private static final long MEDIA_CLOCK_FREQUENCY = 90000;
    private static final int PICTURE_START_CODE = 128;
    private static final String TAG = "RtpH263Reader";
    private int fragmentedSampleSizeBytes;
    private int height;
    private boolean isKeyFrame;
    private boolean isOutputFormatSet;
    private final RtpPayloadFormat payloadFormat;
    private long startTimeOffsetUs;
    private TrackOutput trackOutput;
    private int width;
    private long firstReceivedTimestamp = -9223372036854775807L;
    private int previousSequenceNumber = -1;

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void onReceivingFirstPacket(long j, int i) {
    }

    public RtpH263Reader(RtpPayloadFormat rtpPayloadFormat) {
        this.payloadFormat = rtpPayloadFormat;
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void createTracks(ExtractorOutput extractorOutput, int i) {
        TrackOutput trackOutputTrack = extractorOutput.track(i, 2);
        this.trackOutput = trackOutputTrack;
        trackOutputTrack.format(this.payloadFormat.format);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class Oo0Oooo0oOO00.o0o0oOO0O00O00o$o00OO0OoO0O00o
    	at Oo0Oooo0oOO00.o0o0oOO0O00O00o.OOO0OooOo0O000(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:17)
    	at oOOoooOoo000.O0OO00O0oooo0o.o00O0Oo0o0O(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:3)
    	at OOO0OoO0oo0o.O0ooOOOO00OOOOO.o00oo0o0o000oOo(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:14)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.OOOO00oOO0O0o(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:102)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.ooO0Oo0O0OOo0(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:74)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.OOoo0o0ooo0O(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:21)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.OOoooOoOo0OO(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:1)
    	at OOoo0Oo0o0o.O0OOo00OooOo0.apply(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:44)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.o00O0Oo0o0O(r8-map-id-1cb614e7550a21cca547024543da20a4bf37f3c2b972eb8925541b38dd266c11:32)
     */
    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void consume(ParsableByteArray parsableByteArray, long j, int i, boolean z) {
        Assertions.checkStateNotNull(this.trackOutput);
        int position = parsableByteArray.getPosition();
        int unsignedShort = parsableByteArray.readUnsignedShort();
        Object[] objArr = (unsignedShort & DecoderReuseEvaluation.DISCARD_REASON_VIDEO_ROTATION_CHANGED) > 0;
        if ((unsignedShort & 512) != 0 || (unsignedShort & 504) != 0 || (unsignedShort & 7) != 0) {
            Log.w(TAG, "Dropping packet: video reduncancy coding is not supported, packet header VRC, or PLEN or PEBIT is non-zero");
            return;
        }
        if (objArr != false) {
            if ((parsableByteArray.peekUnsignedByte() & 252) < 128) {
                Log.w(TAG, "Picture start Code (PSC) missing, dropping packet.");
                return;
            } else {
                parsableByteArray.getData()[position] = 0;
                parsableByteArray.getData()[position + 1] = 0;
                parsableByteArray.setPosition(position);
            }
        } else {
            int nextSequenceNumber = RtpPacket.getNextSequenceNumber(this.previousSequenceNumber);
            if (i != nextSequenceNumber) {
                Log.w(TAG, Util.formatInvariant("Received RTP packet with unexpected sequence number. Expected: %d; received: %d. Dropping packet.", Integer.valueOf(nextSequenceNumber), Integer.valueOf(i)));
                return;
            }
        }
        if (this.fragmentedSampleSizeBytes == 0) {
            parseVopHeader(parsableByteArray, this.isOutputFormatSet);
            if (!this.isOutputFormatSet && this.isKeyFrame) {
                if (this.width != this.payloadFormat.format.width || this.height != this.payloadFormat.format.height) {
                    this.trackOutput.format(this.payloadFormat.format.buildUpon().setWidth(this.width).setHeight(this.height).build());
                }
                this.isOutputFormatSet = true;
            }
        }
        int iBytesLeft = parsableByteArray.bytesLeft();
        this.trackOutput.sampleData(parsableByteArray, iBytesLeft);
        this.fragmentedSampleSizeBytes += iBytesLeft;
        if (z) {
            if (this.firstReceivedTimestamp == -9223372036854775807L) {
                this.firstReceivedTimestamp = j;
            }
            this.trackOutput.sampleMetadata(toSampleUs(this.startTimeOffsetUs, j, this.firstReceivedTimestamp), this.isKeyFrame ? 1 : 0, this.fragmentedSampleSizeBytes, 0, null);
            this.fragmentedSampleSizeBytes = 0;
            this.isKeyFrame = false;
        }
        this.previousSequenceNumber = i;
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void seek(long j, long j2) {
        this.firstReceivedTimestamp = j;
        this.fragmentedSampleSizeBytes = 0;
        this.startTimeOffsetUs = j2;
    }

    private void parseVopHeader(ParsableByteArray parsableByteArray, boolean z) {
        int position = parsableByteArray.getPosition();
        if (((parsableByteArray.readUnsignedInt() >> 10) & 63) == 32) {
            int iPeekUnsignedByte = parsableByteArray.peekUnsignedByte();
            int i = (iPeekUnsignedByte >> 1) & 1;
            if (!z && i == 0) {
                int i2 = (iPeekUnsignedByte >> 2) & 7;
                if (i2 == 1) {
                    this.width = 128;
                    this.height = 96;
                } else {
                    int i3 = i2 - 2;
                    this.width = 176 << i3;
                    this.height = 144 << i3;
                }
            }
            parsableByteArray.setPosition(position);
            this.isKeyFrame = i == 0;
            return;
        }
        parsableByteArray.setPosition(position);
        this.isKeyFrame = false;
    }

    private static long toSampleUs(long j, long j2, long j3) {
        return j + Util.scaleLargeTimestamp(j2 - j3, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE, MEDIA_CLOCK_FREQUENCY);
    }
}
