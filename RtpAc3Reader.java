package com.google.android.exoplayer2.source.rtsp.reader;

import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.source.rtsp.RtpPayloadFormat;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
public final class RtpAc3Reader implements RtpPayloadReader {
    private static final int AC3_FRAME_TYPE_COMPLETE_FRAME = 0;
    private static final int AC3_FRAME_TYPE_INITIAL_FRAGMENT_A = 1;
    private static final int AC3_FRAME_TYPE_INITIAL_FRAGMENT_B = 2;
    private static final int AC3_FRAME_TYPE_NON_INITIAL_FRAGMENT = 3;
    private static final int AC3_PAYLOAD_HEADER_SIZE = 2;
    private int numBytesPendingMetadataOutput;
    private final RtpPayloadFormat payloadFormat;
    private long sampleTimeUsOfFramePendingMetadataOutput;
    private long startTimeOffsetUs;
    private TrackOutput trackOutput;
    private final ParsableBitArray scratchBitBuffer = new ParsableBitArray();
    private long firstReceivedTimestamp = -9223372036854775807L;

    public RtpAc3Reader(RtpPayloadFormat rtpPayloadFormat) {
        this.payloadFormat = rtpPayloadFormat;
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void createTracks(ExtractorOutput extractorOutput, int i) {
        TrackOutput trackOutputTrack = extractorOutput.track(i, 1);
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
        int unsignedByte = parsableByteArray.readUnsignedByte() & 3;
        int unsignedByte2 = parsableByteArray.readUnsignedByte() & 255;
        long sampleTimeUs = toSampleTimeUs(this.startTimeOffsetUs, j, this.firstReceivedTimestamp, this.payloadFormat.clockRate);
        if (unsignedByte == 0) {
            maybeOutputSampleMetadata();
            if (unsignedByte2 == 1) {
                processSingleFramePacket(parsableByteArray, sampleTimeUs);
                return;
            } else {
                processMultiFramePacket(parsableByteArray, unsignedByte2, sampleTimeUs);
                return;
            }
        }
        if (unsignedByte == 1 || unsignedByte == 2) {
            maybeOutputSampleMetadata();
        } else if (unsignedByte != 3) {
            throw new IllegalArgumentException(String.valueOf(unsignedByte));
        }
        processFragmentedPacket(parsableByteArray, z, unsignedByte, sampleTimeUs);
    }

    @Override // com.google.android.exoplayer2.source.rtsp.reader.RtpPayloadReader
    public void seek(long j, long j2) {
        this.firstReceivedTimestamp = j;
        this.startTimeOffsetUs = j2;
    }

    private void processSingleFramePacket(ParsableByteArray parsableByteArray, long j) {
        int iBytesLeft = parsableByteArray.bytesLeft();
        ((TrackOutput) Assertions.checkNotNull(this.trackOutput)).sampleData(parsableByteArray, iBytesLeft);
        ((TrackOutput) Util.castNonNull(this.trackOutput)).sampleMetadata(j, 1, iBytesLeft, 0, null);
    }

    private void processMultiFramePacket(ParsableByteArray parsableByteArray, int i, long j) {
        this.scratchBitBuffer.reset(parsableByteArray.getData());
        this.scratchBitBuffer.skipBytes(2);
        long j2 = j;
        for (int i2 = 0; i2 < i; i2++) {
            Ac3Util.SyncFrameInfo ac3SyncframeInfo = Ac3Util.parseAc3SyncframeInfo(this.scratchBitBuffer);
            ((TrackOutput) Assertions.checkNotNull(this.trackOutput)).sampleData(parsableByteArray, ac3SyncframeInfo.frameSize);
            ((TrackOutput) Util.castNonNull(this.trackOutput)).sampleMetadata(j2, 1, ac3SyncframeInfo.frameSize, 0, null);
            j2 += ((long) (ac3SyncframeInfo.sampleCount / ac3SyncframeInfo.sampleRate)) * DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE;
            this.scratchBitBuffer.skipBytes(ac3SyncframeInfo.frameSize);
        }
    }

    private void processFragmentedPacket(ParsableByteArray parsableByteArray, boolean z, int i, long j) {
        int iBytesLeft = parsableByteArray.bytesLeft();
        ((TrackOutput) Assertions.checkNotNull(this.trackOutput)).sampleData(parsableByteArray, iBytesLeft);
        this.numBytesPendingMetadataOutput += iBytesLeft;
        this.sampleTimeUsOfFramePendingMetadataOutput = j;
        if (z && i == 3) {
            outputSampleMetadataForFragmentedPackets();
        }
    }

    private void maybeOutputSampleMetadata() {
        if (this.numBytesPendingMetadataOutput > 0) {
            outputSampleMetadataForFragmentedPackets();
        }
    }

    private void outputSampleMetadataForFragmentedPackets() {
        ((TrackOutput) Util.castNonNull(this.trackOutput)).sampleMetadata(this.sampleTimeUsOfFramePendingMetadataOutput, 1, this.numBytesPendingMetadataOutput, 0, null);
        this.numBytesPendingMetadataOutput = 0;
    }

    private static long toSampleTimeUs(long j, long j2, long j3, int i) {
        return j + Util.scaleLargeTimestamp(j2 - j3, DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE, i);
    }
}
