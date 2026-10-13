package com.google.android.exoplayer2.source.rtsp;

import android.net.Uri;
import android.util.Base64;
import android.util.Pair;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.extractor.ts.PsExtractor;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.CodecSpecificDataUtil;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.NalUnitUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
final class RtspMediaTrack {
    private static final String AAC_CODECS_PREFIX = "mp4a.40.";
    private static final int DEFAULT_H263_HEIGHT = 288;
    private static final int DEFAULT_H263_WIDTH = 352;
    private static final int DEFAULT_MP4V_HEIGHT = 288;
    private static final int DEFAULT_MP4V_WIDTH = 352;
    private static final int DEFAULT_VP8_HEIGHT = 240;
    private static final int DEFAULT_VP8_WIDTH = 320;
    private static final int DEFAULT_VP9_HEIGHT = 240;
    private static final int DEFAULT_VP9_WIDTH = 320;
    private static final String GENERIC_CONTROL_ATTR = "*";
    private static final String H264_CODECS_PREFIX = "avc1.";
    private static final String MPEG4_CODECS_PREFIX = "mp4v.";
    private static final int OPUS_CLOCK_RATE = 48000;
    private static final String PARAMETER_AMR_INTERLEAVING = "interleaving";
    private static final String PARAMETER_AMR_OCTET_ALIGN = "octet-align";
    private static final String PARAMETER_H265_SPROP_MAX_DON_DIFF = "sprop-max-don-diff";
    private static final String PARAMETER_H265_SPROP_PPS = "sprop-pps";
    private static final String PARAMETER_H265_SPROP_SPS = "sprop-sps";
    private static final String PARAMETER_H265_SPROP_VPS = "sprop-vps";
    private static final String PARAMETER_MP4V_CONFIG = "config";
    private static final String PARAMETER_PROFILE_LEVEL_ID = "profile-level-id";
    private static final String PARAMETER_SPROP_PARAMS = "sprop-parameter-sets";
    public final RtpPayloadFormat payloadFormat;
    public final Uri uri;

    public RtspMediaTrack(MediaDescription mediaDescription, Uri uri) {
        Assertions.checkArgument(mediaDescription.attributes.containsKey(SessionDescription.ATTR_CONTROL));
        this.payloadFormat = generatePayloadFormat(mediaDescription);
        this.uri = extractTrackUri(uri, (String) Util.castNonNull((String) mediaDescription.attributes.get(SessionDescription.ATTR_CONTROL)));
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            RtspMediaTrack rtspMediaTrack = (RtspMediaTrack) obj;
            if (this.payloadFormat.equals(rtspMediaTrack.payloadFormat) && this.uri.equals(rtspMediaTrack.uri)) {
                return true;
            }
        }
        return false;
    }

    public int hashCode() {
        return ((217 + this.payloadFormat.hashCode()) * 31) + this.uri.hashCode();
    }

    /* JADX WARN: switch over string: strings are not added: [[audio/ac3], [audio/g711-alaw], [audio/g711-mlaw]] */
    static RtpPayloadFormat generatePayloadFormat(MediaDescription mediaDescription) {
        int iInferChannelCount;
        Format.Builder builder = new Format.Builder();
        if (mediaDescription.bitrate > 0) {
            builder.setAverageBitrate(mediaDescription.bitrate);
        }
        int i = mediaDescription.rtpMapAttribute.payloadType;
        String str = mediaDescription.rtpMapAttribute.mediaEncoding;
        String mimeTypeFromRtpMediaType = RtpPayloadFormat.getMimeTypeFromRtpMediaType(str);
        builder.setSampleMimeType(mimeTypeFromRtpMediaType);
        int i2 = mediaDescription.rtpMapAttribute.clockRate;
        if ("audio".equals(mediaDescription.mediaType)) {
            iInferChannelCount = inferChannelCount(mediaDescription.rtpMapAttribute.encodingParameters, mimeTypeFromRtpMediaType);
            builder.setSampleRate(i2).setChannelCount(iInferChannelCount);
        } else {
            iInferChannelCount = -1;
        }
        ImmutableMap<String, String> fmtpParametersAsMap = mediaDescription.getFmtpParametersAsMap();
        switch (mimeTypeFromRtpMediaType) {
            case "audio/mp4a-latm":
                Assertions.checkArgument(iInferChannelCount != -1);
                Assertions.checkArgument(!fmtpParametersAsMap.isEmpty());
                processAacFmtpAttribute(builder, fmtpParametersAsMap, iInferChannelCount, i2);
                break;
            case "audio/3gpp":
            case "audio/amr-wb":
                Assertions.checkArgument(iInferChannelCount == 1, "Multi channel AMR is not currently supported.");
                Assertions.checkArgument(!fmtpParametersAsMap.isEmpty(), "fmtp parameters must include octet-align.");
                Assertions.checkArgument(fmtpParametersAsMap.containsKey(PARAMETER_AMR_OCTET_ALIGN), "Only octet aligned mode is currently supported.");
                Assertions.checkArgument(!fmtpParametersAsMap.containsKey(PARAMETER_AMR_INTERLEAVING), "Interleaving mode is not currently supported.");
                break;
            case "audio/opus":
                Assertions.checkArgument(iInferChannelCount != -1);
                Assertions.checkArgument(i2 == 48000, "Invalid OPUS clock rate.");
                break;
            case "video/mp4v-es":
                Assertions.checkArgument(!fmtpParametersAsMap.isEmpty());
                processMPEG4FmtpAttribute(builder, fmtpParametersAsMap);
                break;
            case "video/3gpp":
                builder.setWidth(352).setHeight(288);
                break;
            case "video/avc":
                Assertions.checkArgument(!fmtpParametersAsMap.isEmpty());
                processH264FmtpAttribute(builder, fmtpParametersAsMap);
                break;
            case "video/hevc":
                Assertions.checkArgument(!fmtpParametersAsMap.isEmpty());
                processH265FmtpAttribute(builder, fmtpParametersAsMap);
                break;
            case "video/x-vnd.on2.vp8":
                builder.setWidth(320).setHeight(PsExtractor.VIDEO_STREAM_MASK);
                break;
            case "video/x-vnd.on2.vp9":
                builder.setWidth(320).setHeight(PsExtractor.VIDEO_STREAM_MASK);
                break;
            case "audio/raw":
                builder.setPcmEncoding(RtpPayloadFormat.getRawPcmEncodingType(str));
                break;
        }
        Assertions.checkArgument(i2 > 0);
        return new RtpPayloadFormat(builder.build(), i, i2, fmtpParametersAsMap);
    }

    private static int inferChannelCount(int i, String str) {
        if (i != -1) {
            return i;
        }
        return str.equals(MimeTypes.AUDIO_AC3) ? 6 : 1;
    }

    private static void processAacFmtpAttribute(Format.Builder builder, ImmutableMap<String, String> immutableMap, int i, int i2) {
        Assertions.checkArgument(immutableMap.containsKey(PARAMETER_PROFILE_LEVEL_ID));
        builder.setCodecs(AAC_CODECS_PREFIX + ((String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_PROFILE_LEVEL_ID))));
        builder.setInitializationData(ImmutableList.of((Object) AacUtil.buildAacLcAudioSpecificConfig(i2, i)));
    }

    private static void processMPEG4FmtpAttribute(Format.Builder builder, ImmutableMap<String, String> immutableMap) {
        String str = (String) immutableMap.get(PARAMETER_MP4V_CONFIG);
        if (str != null) {
            byte[] bytesFromHexString = Util.getBytesFromHexString(str);
            builder.setInitializationData(ImmutableList.of((Object) bytesFromHexString));
            Pair<Integer, Integer> videoResolutionFromMpeg4VideoConfig = CodecSpecificDataUtil.getVideoResolutionFromMpeg4VideoConfig(bytesFromHexString);
            builder.setWidth(((Integer) videoResolutionFromMpeg4VideoConfig.first).intValue()).setHeight(((Integer) videoResolutionFromMpeg4VideoConfig.second).intValue());
        } else {
            builder.setWidth(352).setHeight(288);
        }
        String str2 = (String) immutableMap.get(PARAMETER_PROFILE_LEVEL_ID);
        StringBuilder sb = new StringBuilder(MPEG4_CODECS_PREFIX);
        if (str2 == null) {
            str2 = IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE;
        }
        builder.setCodecs(sb.append(str2).toString());
    }

    private static byte[] getInitializationDataFromParameterSet(String str) {
        byte[] bArrDecode = Base64.decode(str, 0);
        byte[] bArr = new byte[bArrDecode.length + NalUnitUtil.NAL_START_CODE.length];
        System.arraycopy(NalUnitUtil.NAL_START_CODE, 0, bArr, 0, NalUnitUtil.NAL_START_CODE.length);
        System.arraycopy(bArrDecode, 0, bArr, NalUnitUtil.NAL_START_CODE.length, bArrDecode.length);
        return bArr;
    }

    private static void processH264FmtpAttribute(Format.Builder builder, ImmutableMap<String, String> immutableMap) {
        Assertions.checkArgument(immutableMap.containsKey(PARAMETER_SPROP_PARAMS));
        String[] strArrSplit = Util.split((String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_SPROP_PARAMS)), ",");
        Assertions.checkArgument(strArrSplit.length == 2);
        ImmutableList immutableListOf = ImmutableList.of((Object) getInitializationDataFromParameterSet(strArrSplit[0]), (Object) getInitializationDataFromParameterSet(strArrSplit[1]));
        builder.setInitializationData(immutableListOf);
        byte[] bArr = (byte[]) immutableListOf.get(0);
        NalUnitUtil.SpsData spsNalUnit = NalUnitUtil.parseSpsNalUnit(bArr, NalUnitUtil.NAL_START_CODE.length, bArr.length);
        builder.setPixelWidthHeightRatio(spsNalUnit.pixelWidthHeightRatio);
        builder.setHeight(spsNalUnit.height);
        builder.setWidth(spsNalUnit.width);
        String str = (String) immutableMap.get(PARAMETER_PROFILE_LEVEL_ID);
        if (str != null) {
            builder.setCodecs(H264_CODECS_PREFIX + str);
        } else {
            builder.setCodecs(CodecSpecificDataUtil.buildAvcCodecString(spsNalUnit.profileIdc, spsNalUnit.constraintsFlagsAndReservedZero2Bits, spsNalUnit.levelIdc));
        }
    }

    private static void processH265FmtpAttribute(Format.Builder builder, ImmutableMap<String, String> immutableMap) {
        if (immutableMap.containsKey(PARAMETER_H265_SPROP_MAX_DON_DIFF)) {
            int i = Integer.parseInt((String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_H265_SPROP_MAX_DON_DIFF)));
            Assertions.checkArgument(i == 0, "non-zero sprop-max-don-diff " + i + " is not supported");
        }
        Assertions.checkArgument(immutableMap.containsKey(PARAMETER_H265_SPROP_VPS));
        String str = (String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_H265_SPROP_VPS));
        Assertions.checkArgument(immutableMap.containsKey(PARAMETER_H265_SPROP_SPS));
        String str2 = (String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_H265_SPROP_SPS));
        Assertions.checkArgument(immutableMap.containsKey(PARAMETER_H265_SPROP_PPS));
        ImmutableList immutableListOf = ImmutableList.of((Object) getInitializationDataFromParameterSet(str), (Object) getInitializationDataFromParameterSet(str2), (Object) getInitializationDataFromParameterSet((String) Assertions.checkNotNull((String) immutableMap.get(PARAMETER_H265_SPROP_PPS))));
        builder.setInitializationData(immutableListOf);
        byte[] bArr = (byte[]) immutableListOf.get(1);
        NalUnitUtil.H265SpsData h265SpsNalUnit = NalUnitUtil.parseH265SpsNalUnit(bArr, NalUnitUtil.NAL_START_CODE.length, bArr.length);
        builder.setPixelWidthHeightRatio(h265SpsNalUnit.pixelWidthHeightRatio);
        builder.setHeight(h265SpsNalUnit.height).setWidth(h265SpsNalUnit.width);
        builder.setCodecs(CodecSpecificDataUtil.buildHevcCodecString(h265SpsNalUnit.generalProfileSpace, h265SpsNalUnit.generalTierFlag, h265SpsNalUnit.generalProfileIdc, h265SpsNalUnit.generalProfileCompatibilityFlags, h265SpsNalUnit.constraintBytes, h265SpsNalUnit.generalLevelIdc));
    }

    private static Uri extractTrackUri(Uri uri, String str) {
        Uri uri2 = Uri.parse(str);
        if (uri2.isAbsolute()) {
            return uri2;
        }
        return str.equals(GENERIC_CONTROL_ATTR) ? uri : uri.buildUpon().appendEncodedPath(str).build();
    }
}
