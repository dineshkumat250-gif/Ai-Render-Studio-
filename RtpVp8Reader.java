package com.google.android.exoplayer2.source.rtsp.reader;

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
final class RtpVp8Reader implements RtpPayloadReader {
    private static final long MEDIA_CLOCK_FREQUENCY = 90000;
    private static final String TAG = "RtpVP8Reader";
    private final RtpPayloadFormat payloadFormat;
    private TrackOutput trackOutput;
    private long firstReceivedTimestamp = -9223372036854775807L;
    private int previousSequenceNumber = -1;
    private int fragmentedSampleSizeBytes = -1;
    private long fragmentedSampleTimeUs = -9223372036854775807L;
    private long startTimeOffsetUs = 0;
    private boolean gotFirstPacketOfVp8Frame = false;
    private boolean isKeyFrame = false;
    private boolean isOutputFormatSet = false;

    public RtpVp8Reader(RtpPayloadFormat rtpPayloadFormat) {
        this.payloadFormat = rtpPayloadFormat;
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void createTracks(ExtractorOutput extractorOutput, int i) {
        TrackOutput trackOutputTrack = extractorOutput.track(i, 2);
        this.trackOutput = trackOutputTrack;
        trackOutputTrack.format(this.payloadFormat.format);
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void onReceivingFirstPacket(long j, int i) {
        Assertions.checkState(this.firstReceivedTimestamp == -9223372036854775807L);
        this.firstReceivedTimestamp = j;
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void consume(ParsableByteArray parsableByteArray, long j, int i, boolean z) {
        Assertions.checkStateNotNull(this.trackOutput);
        if (validateVp8Descriptor(parsableByteArray, i)) {
            if (this.fragmentedSampleSizeBytes == -1 && this.gotFirstPacketOfVp8Frame) {
                this.isKeyFrame = (parsableByteArray.peekUnsignedByte() & 1) == 0;
            }
            if (!this.isOutputFormatSet) {
                int position = parsableByteArray.getPosition();
                parsableByteArray.setPosition(position + 6);
                int littleEndianUnsignedShort = parsableByteArray.readLittleEndianUnsignedShort() & 16383;
                int littleEndianUnsignedShort2 = parsableByteArray.readLittleEndianUnsignedShort() & 16383;
                parsableByteArray.setPosition(position);
                if (littleEndianUnsignedShort != this.payloadFormat.format.width || littleEndianUnsignedShort2 != this.payloadFormat.format.height) {
                    this.trackOutput.format(this.payloadFormat.format.buildUpon().setWidth(littleEndianUnsignedShort).setHeight(littleEndianUnsignedShort2).build());
                }
                this.isOutputFormatSet = true;
            }
            int iBytesLeft = parsableByteArray.bytesLeft();
            this.trackOutput.sampleData(parsableByteArray, iBytesLeft);
            int i2 = this.fragmentedSampleSizeBytes;
            if (i2 == -1) {
                this.fragmentedSampleSizeBytes = iBytesLeft;
            } else {
                this.fragmentedSampleSizeBytes = i2 + iBytesLeft;
            }
            this.fragmentedSampleTimeUs = toSampleUs(this.startTimeOffsetUs, j, this.firstReceivedTimestamp);
            if (z) {
                outputSampleMetadataForFragmentedPackets();
            }
            this.previousSequenceNumber = i;
        }
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void seek(long j, long j2) {
        this.firstReceivedTimestamp = j;
        this.fragmentedSampleSizeBytes = -1;
        this.startTimeOffsetUs = j2;
    }

    private boolean validateVp8Descriptor(ParsableByteArray parsableByteArray, int i) {
        int unsignedByte = parsableByteArray.readUnsignedByte();
        if ((unsignedByte & 16) == 16 && (unsignedByte & 7) == 0) {
            if (this.gotFirstPacketOfVp8Frame && this.fragmentedSampleSizeBytes > 0) {
                outputSampleMetadataForFragmentedPackets();
            }
            this.gotFirstPacketOfVp8Frame = true;
        } else if (this.gotFirstPacketOfVp8Frame) {
            int nextSequenceNumber = RtpPacket.getNextSequenceNumber(this.previousSequenceNumber);
            if (i < nextSequenceNumber) {
                Log.w(TAG, Util.formatInvariant("Received RTP packet with unexpected sequence number. Expected: %d; received: %d. Dropping packet.", Integer.valueOf(nextSequenceNumber), Integer.valueOf(i)));
                return false;
            }
        } else {
            Log.w(TAG, "RTP packet is not the start of a new VP8 partition, skipping.");
            return false;
        }
        if ((unsignedByte & 128) != 0) {
            int unsignedByte2 = parsableByteArray.readUnsignedByte();
            if ((unsignedByte2 & 128) != 0 && (parsableByteArray.readUnsignedByte() & 128) != 0) {
                parsableByteArray.skipBytes(1);
            }
            if ((unsignedByte2 & 64) != 0) {
                parsableByteArray.skipBytes(1);
            }
            if ((unsignedByte2 & 32) != 0 || (unsignedByte2 & 16) != 0) {
                parsableByteArray.skipBytes(1);
            }
        }
        return true;
    }

    private void outputSampleMetadataForFragmentedPackets() {
        TrackOutput trackOutput = (TrackOutput) Assertions.checkNotNull(this.trackOutput);
        long j = this.fragmentedSampleTimeUs;
        boolean z = this.isKeyFrame;
        trackOutput.sampleMetadata(j, z ? 1 : 0, this.fragmentedSampleSizeBytes, 0, null);
        this.fragmentedSampleSizeBytes = 0;
        this.fragmentedSampleTimeUs = -9223372036854775807L;
        this.gotFirstPacketOfVp8Frame = false;
    }

    private static long toSampleUs(long j, long j2, long j3) {
        return j + Util.scaleLargeTimestamp(j2 - j3, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE, MEDIA_CLOCK_FREQUENCY);
    }
}
