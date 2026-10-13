package com.google.android.exoplayer2.source.hls.playlist;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.decoder.DecoderReuseEvaluation;
import com.google.android.exoplayer2.drm.DrmInitData;
import com.google.android.exoplayer2.extractor.mp4.PsshAtomUtil;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.icy.IcyHeaders;
import com.google.android.exoplayer2.source.hls.HlsTrackMetadataEntry;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.ParsingLoadable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.UriUtil;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.Iterables;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.EnsuresNonNullIf;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
public final class HlsPlaylistParser implements ParsingLoadable.Parser<HlsPlaylist> {
    private static final String ATTR_CLOSED_CAPTIONS_NONE = "CLOSED-CAPTIONS=NONE";
    private static final String BOOLEAN_FALSE = "NO";
    private static final String BOOLEAN_TRUE = "YES";
    private static final String KEYFORMAT_IDENTITY = "identity";
    private static final String KEYFORMAT_PLAYREADY = "com.microsoft.playready";
    private static final String KEYFORMAT_WIDEVINE_PSSH_BINARY = "urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed";
    private static final String KEYFORMAT_WIDEVINE_PSSH_JSON = "com.widevine";
    private static final String LOG_TAG = "HlsPlaylistParser";
    private static final String METHOD_AES_128 = "AES-128";
    private static final String METHOD_NONE = "NONE";
    private static final String METHOD_SAMPLE_AES = "SAMPLE-AES";
    private static final String METHOD_SAMPLE_AES_CENC = "SAMPLE-AES-CENC";
    private static final String METHOD_SAMPLE_AES_CTR = "SAMPLE-AES-CTR";
    private static final String PLAYLIST_HEADER = "#EXTM3U";
    private static final String TAG_BYTERANGE = "#EXT-X-BYTERANGE";
    private static final String TAG_DEFINE = "#EXT-X-DEFINE";
    private static final String TAG_DISCONTINUITY = "#EXT-X-DISCONTINUITY";
    private static final String TAG_DISCONTINUITY_SEQUENCE = "#EXT-X-DISCONTINUITY-SEQUENCE";
    private static final String TAG_ENDLIST = "#EXT-X-ENDLIST";
    private static final String TAG_GAP = "#EXT-X-GAP";
    private static final String TAG_IFRAME = "#EXT-X-I-FRAMES-ONLY";
    private static final String TAG_INDEPENDENT_SEGMENTS = "#EXT-X-INDEPENDENT-SEGMENTS";
    private static final String TAG_INIT_SEGMENT = "#EXT-X-MAP";
    private static final String TAG_I_FRAME_STREAM_INF = "#EXT-X-I-FRAME-STREAM-INF";
    private static final String TAG_KEY = "#EXT-X-KEY";
    private static final String TAG_MEDIA = "#EXT-X-MEDIA";
    private static final String TAG_MEDIA_DURATION = "#EXTINF";
    private static final String TAG_MEDIA_SEQUENCE = "#EXT-X-MEDIA-SEQUENCE";
    private static final String TAG_PART = "#EXT-X-PART";
    private static final String TAG_PART_INF = "#EXT-X-PART-INF";
    private static final String TAG_PLAYLIST_TYPE = "#EXT-X-PLAYLIST-TYPE";
    private static final String TAG_PREFIX = "#EXT";
    private static final String TAG_PRELOAD_HINT = "#EXT-X-PRELOAD-HINT";
    private static final String TAG_PROGRAM_DATE_TIME = "#EXT-X-PROGRAM-DATE-TIME";
    private static final String TAG_RENDITION_REPORT = "#EXT-X-RENDITION-REPORT";
    private static final String TAG_SERVER_CONTROL = "#EXT-X-SERVER-CONTROL";
    private static final String TAG_SESSION_KEY = "#EXT-X-SESSION-KEY";
    private static final String TAG_SKIP = "#EXT-X-SKIP";
    private static final String TAG_START = "#EXT-X-START";
    private static final String TAG_STREAM_INF = "#EXT-X-STREAM-INF";
    private static final String TAG_TARGET_DURATION = "#EXT-X-TARGETDURATION";
    private static final String TAG_VERSION = "#EXT-X-VERSION";
    private static final String TYPE_AUDIO = "AUDIO";
    private static final String TYPE_CLOSED_CAPTIONS = "CLOSED-CAPTIONS";
    private static final String TYPE_MAP = "MAP";
    private static final String TYPE_PART = "PART";
    private static final String TYPE_SUBTITLES = "SUBTITLES";
    private static final String TYPE_VIDEO = "VIDEO";
    private final HlsMultivariantPlaylist multivariantPlaylist;
    private final HlsMediaPlaylist previousMediaPlaylist;
    private static final Pattern REGEX_AVERAGE_BANDWIDTH = Pattern.compile("AVERAGE-BANDWIDTH=(\\d+)\\b");
    private static final Pattern REGEX_VIDEO = Pattern.compile("VIDEO=\"(.+?)\"");
    private static final Pattern REGEX_AUDIO = Pattern.compile("AUDIO=\"(.+?)\"");
    private static final Pattern REGEX_SUBTITLES = Pattern.compile("SUBTITLES=\"(.+?)\"");
    private static final Pattern REGEX_CLOSED_CAPTIONS = Pattern.compile("CLOSED-CAPTIONS=\"(.+?)\"");
    private static final Pattern REGEX_BANDWIDTH = Pattern.compile("[^-]BANDWIDTH=(\\d+)\\b");
    private static final Pattern REGEX_CHANNELS = Pattern.compile("CHANNELS=\"(.+?)\"");
    private static final Pattern REGEX_CODECS = Pattern.compile("CODECS=\"(.+?)\"");
    private static final Pattern REGEX_RESOLUTION = Pattern.compile("RESOLUTION=(\\d+x\\d+)");
    private static final Pattern REGEX_FRAME_RATE = Pattern.compile("FRAME-RATE=([\\d\\.]+)\\b");
    private static final Pattern REGEX_TARGET_DURATION = Pattern.compile("#EXT-X-TARGETDURATION:(\\d+)\\b");
    private static final Pattern REGEX_ATTR_DURATION = Pattern.compile("DURATION=([\\d\\.]+)\\b");
    private static final Pattern REGEX_PART_TARGET_DURATION = Pattern.compile("PART-TARGET=([\\d\\.]+)\\b");
    private static final Pattern REGEX_VERSION = Pattern.compile("#EXT-X-VERSION:(\\d+)\\b");
    private static final Pattern REGEX_PLAYLIST_TYPE = Pattern.compile("#EXT-X-PLAYLIST-TYPE:(.+)\\b");
    private static final Pattern REGEX_CAN_SKIP_UNTIL = Pattern.compile("CAN-SKIP-UNTIL=([\\d\\.]+)\\b");
    private static final Pattern REGEX_CAN_SKIP_DATE_RANGES = compileBooleanAttrPattern("CAN-SKIP-DATERANGES");
    private static final Pattern REGEX_SKIPPED_SEGMENTS = Pattern.compile("SKIPPED-SEGMENTS=(\\d+)\\b");
    private static final Pattern REGEX_HOLD_BACK = Pattern.compile("[:|,]HOLD-BACK=([\\d\\.]+)\\b");
    private static final Pattern REGEX_PART_HOLD_BACK = Pattern.compile("PART-HOLD-BACK=([\\d\\.]+)\\b");
    private static final Pattern REGEX_CAN_BLOCK_RELOAD = compileBooleanAttrPattern("CAN-BLOCK-RELOAD");
    private static final Pattern REGEX_MEDIA_SEQUENCE = Pattern.compile("#EXT-X-MEDIA-SEQUENCE:(\\d+)\\b");
    private static final Pattern REGEX_MEDIA_DURATION = Pattern.compile("#EXTINF:([\\d\\.]+)\\b");
    private static final Pattern REGEX_MEDIA_TITLE = Pattern.compile("#EXTINF:[\\d\\.]+\\b,(.+)");
    private static final Pattern REGEX_LAST_MSN = Pattern.compile("LAST-MSN=(\\d+)\\b");
    private static final Pattern REGEX_LAST_PART = Pattern.compile("LAST-PART=(\\d+)\\b");
    private static final Pattern REGEX_TIME_OFFSET = Pattern.compile("TIME-OFFSET=(-?[\\d\\.]+)\\b");
    private static final Pattern REGEX_BYTERANGE = Pattern.compile("#EXT-X-BYTERANGE:(\\d+(?:@\\d+)?)\\b");
    private static final Pattern REGEX_ATTR_BYTERANGE = Pattern.compile("BYTERANGE=\"(\\d+(?:@\\d+)?)\\b\"");
    private static final Pattern REGEX_BYTERANGE_START = Pattern.compile("BYTERANGE-START=(\\d+)\\b");
    private static final Pattern REGEX_BYTERANGE_LENGTH = Pattern.compile("BYTERANGE-LENGTH=(\\d+)\\b");
    private static final Pattern REGEX_METHOD = Pattern.compile("METHOD=(NONE|AES-128|SAMPLE-AES|SAMPLE-AES-CENC|SAMPLE-AES-CTR)\\s*(?:,|$)");
    private static final Pattern REGEX_KEYFORMAT = Pattern.compile("KEYFORMAT=\"(.+?)\"");
    private static final Pattern REGEX_KEYFORMATVERSIONS = Pattern.compile("KEYFORMATVERSIONS=\"(.+?)\"");
    private static final Pattern REGEX_URI = Pattern.compile("URI=\"(.+?)\"");
    private static final Pattern REGEX_IV = Pattern.compile("IV=([^,.*]+)");
    private static final Pattern REGEX_TYPE = Pattern.compile("TYPE=(AUDIO|VIDEO|SUBTITLES|CLOSED-CAPTIONS)");
    private static final Pattern REGEX_PRELOAD_HINT_TYPE = Pattern.compile("TYPE=(PART|MAP)");
    private static final Pattern REGEX_LANGUAGE = Pattern.compile("LANGUAGE=\"(.+?)\"");
    private static final Pattern REGEX_NAME = Pattern.compile("NAME=\"(.+?)\"");
    private static final Pattern REGEX_GROUP_ID = Pattern.compile("GROUP-ID=\"(.+?)\"");
    private static final Pattern REGEX_CHARACTERISTICS = Pattern.compile("CHARACTERISTICS=\"(.+?)\"");
    private static final Pattern REGEX_INSTREAM_ID = Pattern.compile("INSTREAM-ID=\"((?:CC|SERVICE)\\d+)\"");
    private static final Pattern REGEX_AUTOSELECT = compileBooleanAttrPattern("AUTOSELECT");
    private static final Pattern REGEX_DEFAULT = compileBooleanAttrPattern("DEFAULT");
    private static final Pattern REGEX_FORCED = compileBooleanAttrPattern("FORCED");
    private static final Pattern REGEX_INDEPENDENT = compileBooleanAttrPattern("INDEPENDENT");
    private static final Pattern REGEX_GAP = compileBooleanAttrPattern("GAP");
    private static final Pattern REGEX_PRECISE = compileBooleanAttrPattern("PRECISE");
    private static final Pattern REGEX_VALUE = Pattern.compile("VALUE=\"(.+?)\"");
    private static final Pattern REGEX_IMPORT = Pattern.compile("IMPORT=\"(.+?)\"");
    private static final Pattern REGEX_VARIABLE_REFERENCE = Pattern.compile("\\{\\$([a-zA-Z0-9\\-_]+)\\}");

    public static final class DeltaUpdateException extends IOException {
    }

    public HlsPlaylistParser() {
        this(HlsMultivariantPlaylist.EMPTY, null);
    }

    public HlsPlaylistParser(HlsMultivariantPlaylist hlsMultivariantPlaylist, HlsMediaPlaylist hlsMediaPlaylist) {
        this.multivariantPlaylist = hlsMultivariantPlaylist;
        this.previousMediaPlaylist = hlsMediaPlaylist;
    }

    /* JADX WARN: Can't rename method to resolve collision */
    @Override // com.google.android.exoplayer2.upstream.ParsingLoadable.Parser
    public HlsPlaylist parse(Uri uri, InputStream inputStream) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        ArrayDeque arrayDeque = new ArrayDeque();
        try {
            if (!checkPlaylistHeader(bufferedReader)) {
                throw ParserException.createForMalformedManifest("Input does not start with the #EXTM3U header.", null);
            }
            while (true) {
                String line = bufferedReader.readLine();
                if (line != null) {
                    String strTrim = line.trim();
                    if (!strTrim.isEmpty()) {
                        if (strTrim.startsWith(TAG_STREAM_INF)) {
                            arrayDeque.add(strTrim);
                            HlsMultivariantPlaylist multivariantPlaylist = parseMultivariantPlaylist(new LineIterator(arrayDeque, bufferedReader), uri.toString());
                            Util.closeQuietly(bufferedReader);
                            return multivariantPlaylist;
                        }
                        if (!strTrim.startsWith(TAG_TARGET_DURATION) && !strTrim.startsWith(TAG_MEDIA_SEQUENCE) && !strTrim.startsWith(TAG_MEDIA_DURATION) && !strTrim.startsWith(TAG_KEY) && !strTrim.startsWith(TAG_BYTERANGE) && !strTrim.equals(TAG_DISCONTINUITY) && !strTrim.equals(TAG_DISCONTINUITY_SEQUENCE) && !strTrim.equals(TAG_ENDLIST)) {
                            arrayDeque.add(strTrim);
                        }
                        arrayDeque.add(strTrim);
                        HlsMediaPlaylist mediaPlaylist = parseMediaPlaylist(this.multivariantPlaylist, this.previousMediaPlaylist, new LineIterator(arrayDeque, bufferedReader), uri.toString());
                        Util.closeQuietly(bufferedReader);
                        return mediaPlaylist;
                    }
                } else {
                    Util.closeQuietly(bufferedReader);
                    throw ParserException.createForMalformedManifest("Failed to parse the playlist, could not identify any tags.", null);
                }
            }
        } catch (Throwable th) {
            Util.closeQuietly(bufferedReader);
            throw th;
        }
    }

    private static boolean checkPlaylistHeader(BufferedReader bufferedReader) throws IOException {
        int i = bufferedReader.read();
        if (i == 239) {
            if (bufferedReader.read() != 187 || bufferedReader.read() != 191) {
                return false;
            }
            i = bufferedReader.read();
        }
        int iSkipIgnorableWhitespace = skipIgnorableWhitespace(bufferedReader, true, i);
        int length = PLAYLIST_HEADER.length();
        for (int i2 = 0; i2 < length; i2++) {
            if (iSkipIgnorableWhitespace != PLAYLIST_HEADER.charAt(i2)) {
                return false;
            }
            iSkipIgnorableWhitespace = bufferedReader.read();
        }
        return Util.isLinebreak(skipIgnorableWhitespace(bufferedReader, false, iSkipIgnorableWhitespace));
    }

    private static int skipIgnorableWhitespace(BufferedReader bufferedReader, boolean z, int i) throws IOException {
        while (i != -1 && Character.isWhitespace(i) && (z || !Util.isLinebreak(i))) {
            i = bufferedReader.read();
        }
        return i;
    }

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:79:0x02f9  */
    private static HlsMultivariantPlaylist parseMultivariantPlaylist(LineIterator lineIterator, String str) throws IOException {
        ArrayList arrayList;
        String mediaMimeType;
        int i;
        String str2;
        ArrayList arrayList2;
        String mediaMimeType2;
        int i2;
        int i3;
        Uri uriResolveToUri;
        int i4;
        String str3 = str;
        HashMap map = new HashMap();
        HashMap map2 = new HashMap();
        ArrayList arrayList3 = new ArrayList();
        ArrayList arrayList4 = new ArrayList();
        ArrayList arrayList5 = new ArrayList();
        ArrayList arrayList6 = new ArrayList();
        ArrayList arrayList7 = new ArrayList();
        ArrayList arrayList8 = new ArrayList();
        ArrayList arrayList9 = new ArrayList();
        ArrayList arrayList10 = new ArrayList();
        boolean z = false;
        boolean z2 = false;
        while (lineIterator.hasNext()) {
            String next = lineIterator.next();
            if (next.startsWith(TAG_PREFIX)) {
                arrayList10.add(next);
            }
            boolean zStartsWith = next.startsWith(TAG_I_FRAME_STREAM_INF);
            ArrayList arrayList11 = arrayList7;
            if (next.startsWith(TAG_DEFINE)) {
                map2.put(parseStringAttr(next, REGEX_NAME, map2), parseStringAttr(next, REGEX_VALUE, map2));
            } else {
                if (next.equals(TAG_INDEPENDENT_SEGMENTS)) {
                    z2 = true;
                } else if (next.startsWith(TAG_MEDIA)) {
                    arrayList8.add(next);
                } else if (next.startsWith(TAG_SESSION_KEY)) {
                    DrmInitData.SchemeData drmSchemeData = parseDrmSchemeData(next, parseOptionalStringAttr(next, REGEX_KEYFORMAT, KEYFORMAT_IDENTITY, map2), map2);
                    if (drmSchemeData != null) {
                        arrayList9.add(new DrmInitData(parseEncryptionScheme(parseStringAttr(next, REGEX_METHOD, map2)), drmSchemeData));
                    }
                } else if (next.startsWith(TAG_STREAM_INF) || zStartsWith) {
                    boolean zContains = z | next.contains(ATTR_CLOSED_CAPTIONS_NONE);
                    int i5 = zStartsWith ? DecoderReuseEvaluation.DISCARD_REASON_AUDIO_ENCODING_CHANGED : 0;
                    int intAttr = parseIntAttr(next, REGEX_BANDWIDTH);
                    int optionalIntAttr = parseOptionalIntAttr(next, REGEX_AVERAGE_BANDWIDTH, -1);
                    String optionalStringAttr = parseOptionalStringAttr(next, REGEX_CODECS, map2);
                    String optionalStringAttr2 = parseOptionalStringAttr(next, REGEX_RESOLUTION, map2);
                    if (optionalStringAttr2 != null) {
                        String[] strArrSplit = Util.split(optionalStringAttr2, "x");
                        int i6 = Integer.parseInt(strArrSplit[0]);
                        int i7 = Integer.parseInt(strArrSplit[1]);
                        if (i6 <= 0 || i7 <= 0) {
                            i7 = -1;
                            i4 = -1;
                        } else {
                            i4 = i6;
                        }
                        i3 = i7;
                        i2 = i4;
                    } else {
                        i2 = -1;
                        i3 = -1;
                    }
                    String optionalStringAttr3 = parseOptionalStringAttr(next, REGEX_FRAME_RATE, map2);
                    float f = optionalStringAttr3 != null ? Float.parseFloat(optionalStringAttr3) : -1.0f;
                    String optionalStringAttr4 = parseOptionalStringAttr(next, REGEX_VIDEO, map2);
                    String optionalStringAttr5 = parseOptionalStringAttr(next, REGEX_AUDIO, map2);
                    String optionalStringAttr6 = parseOptionalStringAttr(next, REGEX_SUBTITLES, map2);
                    String optionalStringAttr7 = parseOptionalStringAttr(next, REGEX_CLOSED_CAPTIONS, map2);
                    if (zStartsWith) {
                        uriResolveToUri = UriUtil.resolveToUri(str3, parseStringAttr(next, REGEX_URI, map2));
                    } else {
                        if (!lineIterator.hasNext()) {
                            throw ParserException.createForMalformedManifest("#EXT-X-STREAM-INF must be followed by another line", null);
                        }
                        uriResolveToUri = UriUtil.resolveToUri(str3, replaceVariableReferences(lineIterator.next(), map2));
                    }
                    Uri uri = uriResolveToUri;
                    arrayList3.add(new HlsMultivariantPlaylist.Variant(uri, new Format.Builder().setId(arrayList3.size()).setContainerMimeType(MimeTypes.APPLICATION_M3U8).setCodecs(optionalStringAttr).setAverageBitrate(optionalIntAttr).setPeakBitrate(intAttr).setWidth(i2).setHeight(i3).setFrameRate(f).setRoleFlags(i5).build(), optionalStringAttr4, optionalStringAttr5, optionalStringAttr6, optionalStringAttr7));
                    ArrayList arrayList12 = (ArrayList) map.get(uri);
                    if (arrayList12 == null) {
                        arrayList12 = new ArrayList();
                        map.put(uri, arrayList12);
                    }
                    arrayList12.add(new HlsTrackMetadataEntry.VariantInfo(optionalIntAttr, intAttr, optionalStringAttr4, optionalStringAttr5, optionalStringAttr6, optionalStringAttr7));
                    z = zContains;
                    z2 = z2;
                }
                arrayList7 = arrayList11;
                arrayList10 = arrayList10;
                arrayList9 = arrayList9;
                arrayList6 = arrayList6;
                arrayList5 = arrayList5;
            }
            arrayList7 = arrayList11;
            arrayList10 = arrayList10;
            arrayList9 = arrayList9;
            arrayList6 = arrayList6;
            arrayList5 = arrayList5;
        }
        ArrayList arrayList13 = arrayList5;
        ArrayList arrayList14 = arrayList6;
        ArrayList arrayList15 = arrayList7;
        ArrayList arrayList16 = arrayList10;
        boolean z3 = z;
        ArrayList arrayList17 = arrayList9;
        boolean z4 = z2;
        ArrayList arrayList18 = new ArrayList();
        HashSet hashSet = new HashSet();
        for (int i8 = 0; i8 < arrayList3.size(); i8++) {
            HlsMultivariantPlaylist.Variant variant = (HlsMultivariantPlaylist.Variant) arrayList3.get(i8);
            if (hashSet.add(variant.url)) {
                Assertions.checkState(variant.format.metadata == null);
                arrayList18.add(variant.copyWithFormat(variant.format.buildUpon().setMetadata(new Metadata(new HlsTrackMetadataEntry(null, null, (List) Assertions.checkNotNull((ArrayList) map.get(variant.url))))).build()));
            }
        }
        int i9 = 0;
        ArrayList arrayList19 = null;
        Format formatBuild = null;
        while (i9 < arrayList8.size()) {
            String str4 = (String) arrayList8.get(i9);
            String stringAttr = parseStringAttr(str4, REGEX_GROUP_ID, map2);
            String stringAttr2 = parseStringAttr(str4, REGEX_NAME, map2);
            ArrayList arrayList20 = arrayList19;
            Format.Builder language = new Format.Builder().setId(stringAttr + ":" + stringAttr2).setLabel(stringAttr2).setContainerMimeType(MimeTypes.APPLICATION_M3U8).setSelectionFlags(parseSelectionFlags(str4)).setRoleFlags(parseRoleFlags(str4, map2)).setLanguage(parseOptionalStringAttr(str4, REGEX_LANGUAGE, map2));
            String optionalStringAttr8 = parseOptionalStringAttr(str4, REGEX_URI, map2);
            Uri uriResolveToUri2 = optionalStringAttr8 == null ? null : UriUtil.resolveToUri(str3, optionalStringAttr8);
            ArrayList arrayList21 = arrayList8;
            ArrayList arrayList22 = arrayList18;
            Metadata metadata = new Metadata(new HlsTrackMetadataEntry(stringAttr, stringAttr2, Collections.emptyList()));
            String stringAttr3 = parseStringAttr(str4, REGEX_TYPE, map2);
            stringAttr3.hashCode();
            switch (stringAttr3) {
                case "SUBTITLES":
                    arrayList = arrayList13;
                    HlsMultivariantPlaylist.Variant variantWithSubtitleGroup = getVariantWithSubtitleGroup(arrayList3, stringAttr);
                    if (variantWithSubtitleGroup != null) {
                        String codecsOfType = Util.getCodecsOfType(variantWithSubtitleGroup.format.codecs, 3);
                        language.setCodecs(codecsOfType);
                        mediaMimeType = MimeTypes.getMediaMimeType(codecsOfType);
                    } else {
                        mediaMimeType = null;
                    }
                    if (mediaMimeType == null) {
                        mediaMimeType = MimeTypes.TEXT_VTT;
                    }
                    language.setSampleMimeType(mediaMimeType).setMetadata(metadata);
                    if (uriResolveToUri2 != null) {
                        HlsMultivariantPlaylist.Rendition rendition = new HlsMultivariantPlaylist.Rendition(uriResolveToUri2, language.build(), stringAttr, stringAttr2);
                        arrayList14 = arrayList14;
                        arrayList14.add(rendition);
                    } else {
                        arrayList14 = arrayList14;
                        Log.w(LOG_TAG, "EXT-X-MEDIA tag with missing mandatory URI attribute: skipping");
                    }
                    arrayList2 = arrayList20;
                    break;
                case "CLOSED-CAPTIONS":
                    arrayList = arrayList13;
                    String stringAttr4 = parseStringAttr(str4, REGEX_INSTREAM_ID, map2);
                    if (stringAttr4.startsWith("CC")) {
                        i = Integer.parseInt(stringAttr4.substring(2));
                        str2 = MimeTypes.APPLICATION_CEA608;
                    } else {
                        i = Integer.parseInt(stringAttr4.substring(7));
                        str2 = MimeTypes.APPLICATION_CEA708;
                    }
                    arrayList2 = arrayList20 == null ? new ArrayList() : arrayList20;
                    language.setSampleMimeType(str2).setAccessibilityChannel(i);
                    arrayList2.add(language.build());
                    arrayList14 = arrayList14;
                    break;
                case "AUDIO":
                    HlsMultivariantPlaylist.Variant variantWithAudioGroup = getVariantWithAudioGroup(arrayList3, stringAttr);
                    if (variantWithAudioGroup != null) {
                        String codecsOfType2 = Util.getCodecsOfType(variantWithAudioGroup.format.codecs, 1);
                        language.setCodecs(codecsOfType2);
                        mediaMimeType2 = MimeTypes.getMediaMimeType(codecsOfType2);
                    } else {
                        mediaMimeType2 = null;
                    }
                    String optionalStringAttr9 = parseOptionalStringAttr(str4, REGEX_CHANNELS, map2);
                    if (optionalStringAttr9 != null) {
                        language.setChannelCount(Integer.parseInt(Util.splitAtFirst(optionalStringAttr9, "/")[0]));
                        if (MimeTypes.AUDIO_E_AC3.equals(mediaMimeType2) && optionalStringAttr9.endsWith("/JOC")) {
                            language.setCodecs(MimeTypes.CODEC_E_AC3_JOC);
                            mediaMimeType2 = MimeTypes.AUDIO_E_AC3_JOC;
                        }
                    }
                    language.setSampleMimeType(mediaMimeType2);
                    if (uriResolveToUri2 == null) {
                        arrayList = arrayList13;
                        if (variantWithAudioGroup != null) {
                            arrayList2 = arrayList20;
                            formatBuild = language.build();
                            arrayList14 = arrayList14;
                        }
                        break;
                    } else {
                        language.setMetadata(metadata);
                        arrayList = arrayList13;
                        arrayList.add(new HlsMultivariantPlaylist.Rendition(uriResolveToUri2, language.build(), stringAttr, stringAttr2));
                    }
                    arrayList2 = arrayList20;
                    break;
                case "VIDEO":
                    HlsMultivariantPlaylist.Variant variantWithVideoGroup = getVariantWithVideoGroup(arrayList3, stringAttr);
                    if (variantWithVideoGroup != null) {
                        Format format = variantWithVideoGroup.format;
                        String codecsOfType3 = Util.getCodecsOfType(format.codecs, 2);
                        language.setCodecs(codecsOfType3).setSampleMimeType(MimeTypes.getMediaMimeType(codecsOfType3)).setWidth(format.width).setHeight(format.height).setFrameRate(format.frameRate);
                    }
                    if (uriResolveToUri2 != null) {
                        language.setMetadata(metadata);
                        arrayList4.add(new HlsMultivariantPlaylist.Rendition(uriResolveToUri2, language.build(), stringAttr, stringAttr2));
                        break;
                    }
                default:
                    arrayList = arrayList13;
                    arrayList2 = arrayList20;
                    break;
            }
            i9++;
            str3 = str;
            arrayList14 = arrayList14;
            arrayList13 = arrayList;
            arrayList19 = arrayList2;
            arrayList8 = arrayList21;
            arrayList18 = arrayList22;
        }
        return new HlsMultivariantPlaylist(str, arrayList16, arrayList18, arrayList4, arrayList13, arrayList14, arrayList15, formatBuild, z3 ? Collections.emptyList() : arrayList19, z4, map2, arrayList17);
    }

    private static HlsMultivariantPlaylist.Variant getVariantWithAudioGroup(ArrayList<HlsMultivariantPlaylist.Variant> arrayList, String str) {
        for (int i = 0; i < arrayList.size(); i++) {
            HlsMultivariantPlaylist.Variant variant = arrayList.get(i);
            if (str.equals(variant.audioGroupId)) {
                return variant;
            }
        }
        return null;
    }

    private static HlsMultivariantPlaylist.Variant getVariantWithVideoGroup(ArrayList<HlsMultivariantPlaylist.Variant> arrayList, String str) {
        for (int i = 0; i < arrayList.size(); i++) {
            HlsMultivariantPlaylist.Variant variant = arrayList.get(i);
            if (str.equals(variant.videoGroupId)) {
                return variant;
            }
        }
        return null;
    }

    private static HlsMultivariantPlaylist.Variant getVariantWithSubtitleGroup(ArrayList<HlsMultivariantPlaylist.Variant> arrayList, String str) {
        for (int i = 0; i < arrayList.size(); i++) {
            HlsMultivariantPlaylist.Variant variant = arrayList.get(i);
            if (str.equals(variant.subtitleGroupId)) {
                return variant;
            }
        }
        return null;
    }

    /* JADX WARN: Code duplicated, block: B:115:0x0321 A[PHI: r13
      0x0321: PHI (r13v7 java.lang.String) = (r13v5 java.lang.String), (r13v2 java.lang.String) binds: [B:119:0x0330, B:113:0x0314] A[DONT_GENERATE, DONT_INLINE]] */
    /* JADX WARN: Code duplicated, block: B:229:0x0627  */
    /* JADX WARN: Code duplicated, block: B:230:0x062a  */
    /* JADX WARN: Code duplicated, block: B:233:0x0649  */
    private static HlsMediaPlaylist parseMediaPlaylist(HlsMultivariantPlaylist hlsMultivariantPlaylist, HlsMediaPlaylist hlsMediaPlaylist, LineIterator lineIterator, String str) throws IOException {
        String str2;
        HlsMediaPlaylist.Segment segment;
        int i;
        long j;
        int i2;
        long j2;
        long j3;
        HlsMediaPlaylist.Segment segment2;
        long j4;
        boolean z;
        DrmInitData drmInitData;
        DrmInitData playlistProtectionSchemes;
        HlsMediaPlaylist.Segment segment3;
        hlsMultivariantPlaylist = hlsMultivariantPlaylist;
        HlsMediaPlaylist hlsMediaPlaylist2 = hlsMediaPlaylist;
        boolean z2 = hlsMultivariantPlaylist.hasIndependentSegments;
        HashMap map = new HashMap();
        HashMap map2 = new HashMap();
        ArrayList arrayList = new ArrayList();
        ArrayList arrayList2 = new ArrayList();
        ArrayList arrayList3 = new ArrayList();
        ArrayList arrayList4 = new ArrayList();
        HlsMediaPlaylist.ServerControl serverControl = new HlsMediaPlaylist.ServerControl(-9223372036854775807L, false, -9223372036854775807L, -9223372036854775807L, false);
        TreeMap treeMap = new TreeMap();
        boolean z3 = z2;
        long j5 = -9223372036854775807L;
        long doubleAttr = -9223372036854775807L;
        long j6 = 0;
        long j7 = 0;
        long jMsToUs = 0;
        long j8 = 0;
        long longAttr = 0;
        long timeSecondsToUs = 0;
        long j9 = 0;
        String optionalStringAttr = "";
        String str3 = optionalStringAttr;
        boolean optionalBooleanAttribute = false;
        String encryptionScheme = null;
        DrmInitData drmInitData2 = null;
        HlsMediaPlaylist.Part part = null;
        int i3 = 0;
        boolean z4 = false;
        DrmInitData playlistProtectionSchemes2 = null;
        String str4 = null;
        long j10 = -1;
        boolean z5 = false;
        boolean z6 = false;
        int i4 = 0;
        HlsMediaPlaylist.Segment segment4 = null;
        int i5 = 0;
        String stringAttr = null;
        long j11 = -1;
        boolean z7 = false;
        long intAttr = -9223372036854775807L;
        long j12 = 0;
        int intAttr2 = 1;
        while (lineIterator.hasNext()) {
            String next = lineIterator.next();
            if (next.startsWith(TAG_PREFIX)) {
                arrayList4.add(next);
            }
            if (next.startsWith(TAG_PLAYLIST_TYPE)) {
                String stringAttr2 = parseStringAttr(next, REGEX_PLAYLIST_TYPE, map);
                if ("VOD".equals(stringAttr2)) {
                    i3 = 1;
                } else if ("EVENT".equals(stringAttr2)) {
                    i3 = 2;
                }
            } else if (next.equals(TAG_IFRAME)) {
                z7 = true;
            } else {
                if (next.startsWith(TAG_START)) {
                    ArrayList arrayList5 = arrayList4;
                    serverControl = serverControl;
                    long doubleAttr2 = (long) (parseDoubleAttr(next, REGEX_TIME_OFFSET) * 1000000.0d);
                    optionalBooleanAttribute = parseOptionalBooleanAttribute(next, REGEX_PRECISE, false);
                    arrayList4 = arrayList5;
                    j5 = doubleAttr2;
                } else {
                    ArrayList arrayList6 = arrayList4;
                    serverControl = serverControl;
                    if (next.startsWith(TAG_SERVER_CONTROL)) {
                        serverControl = parseServerControl(next);
                        arrayList4 = arrayList6;
                    } else if (next.startsWith(TAG_PART_INF)) {
                        doubleAttr = (long) (parseDoubleAttr(next, REGEX_PART_TARGET_DURATION) * 1000000.0d);
                        arrayList4 = arrayList6;
                    } else if (next.startsWith(TAG_INIT_SEGMENT)) {
                        String stringAttr3 = parseStringAttr(next, REGEX_URI, map);
                        boolean z8 = optionalBooleanAttribute;
                        String optionalStringAttr2 = parseOptionalStringAttr(next, REGEX_ATTR_BYTERANGE, map);
                        if (optionalStringAttr2 != null) {
                            String[] strArrSplit = Util.split(optionalStringAttr2, "@");
                            j10 = Long.parseLong(strArrSplit[0]);
                            if (strArrSplit.length > 1) {
                                j6 = Long.parseLong(strArrSplit[1]);
                            }
                        }
                        long j13 = j10;
                        long j14 = j13 == j11 ? 0L : j6;
                        if (stringAttr != null && str4 == null) {
                            throw ParserException.createForMalformedManifest("The encryption IV attribute must be present when an initialization segment is encrypted with METHOD=AES-128.", null);
                        }
                        HlsMediaPlaylist.Segment segment5 = new HlsMediaPlaylist.Segment(stringAttr3, j14, j13, stringAttr, str4);
                        String str5 = str4;
                        if (j13 != j11) {
                            j14 += j13;
                        }
                        str4 = str5;
                        arrayList4 = arrayList6;
                        segment4 = segment5;
                        j6 = j14;
                        j10 = j11;
                        serverControl = serverControl;
                        optionalBooleanAttribute = z8;
                    } else {
                        optionalBooleanAttribute = optionalBooleanAttribute;
                        arrayList4 = arrayList6;
                        str4 = str4;
                        stringAttr = stringAttr;
                        if (next.startsWith(TAG_TARGET_DURATION)) {
                            intAttr = DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE * ((long) parseIntAttr(next, REGEX_TARGET_DURATION));
                        } else if (next.startsWith(TAG_MEDIA_SEQUENCE)) {
                            longAttr = parseLongAttr(next, REGEX_MEDIA_SEQUENCE);
                            j12 = longAttr;
                            serverControl = serverControl;
                            optionalBooleanAttribute = optionalBooleanAttribute;
                            arrayList4 = arrayList4;
                        } else if (next.startsWith(TAG_VERSION)) {
                            intAttr2 = parseIntAttr(next, REGEX_VERSION);
                        } else {
                            if (next.startsWith(TAG_DEFINE)) {
                                String optionalStringAttr3 = parseOptionalStringAttr(next, REGEX_IMPORT, map);
                                if (optionalStringAttr3 != null) {
                                    String str6 = hlsMultivariantPlaylist.variableDefinitions.get(optionalStringAttr3);
                                    if (str6 != null) {
                                        map.put(optionalStringAttr3, str6);
                                    }
                                } else {
                                    map.put(parseStringAttr(next, REGEX_NAME, map), parseStringAttr(next, REGEX_VALUE, map));
                                }
                                treeMap = treeMap;
                                str2 = str3;
                            } else if (next.startsWith(TAG_MEDIA_DURATION)) {
                                timeSecondsToUs = parseTimeSecondsToUs(next, REGEX_MEDIA_DURATION);
                                optionalStringAttr = parseOptionalStringAttr(next, REGEX_MEDIA_TITLE, str3, map);
                            } else {
                                String str7 = str3;
                                if (next.startsWith(TAG_SKIP)) {
                                    int intAttr3 = parseIntAttr(next, REGEX_SKIPPED_SEGMENTS);
                                    Assertions.checkState(hlsMediaPlaylist2 != null && arrayList.isEmpty());
                                    str2 = str7;
                                    int i6 = (int) (j12 - ((HlsMediaPlaylist) Util.castNonNull(hlsMediaPlaylist2)).mediaSequence);
                                    int i7 = intAttr3 + i6;
                                    if (i6 < 0 || i7 > hlsMediaPlaylist2.segments.size()) {
                                        throw new DeltaUpdateException();
                                    }
                                    stringAttr = stringAttr;
                                    str4 = str4;
                                    long j15 = j7;
                                    while (i6 < i7) {
                                        HlsMediaPlaylist.Segment segmentCopyWith = hlsMediaPlaylist2.segments.get(i6);
                                        int i8 = i6;
                                        if (j12 != hlsMediaPlaylist2.mediaSequence) {
                                            segmentCopyWith = segmentCopyWith.copyWith(j15, (hlsMediaPlaylist2.discontinuitySequence - i4) + segmentCopyWith.relativeDiscontinuitySequence);
                                        }
                                        arrayList.add(segmentCopyWith);
                                        j8 = j15 + segmentCopyWith.durationUs;
                                        if (segmentCopyWith.byteRangeLength != j11) {
                                            j6 = segmentCopyWith.byteRangeOffset + segmentCopyWith.byteRangeLength;
                                        }
                                        int i9 = segmentCopyWith.relativeDiscontinuitySequence;
                                        HlsMediaPlaylist.Segment segment6 = segmentCopyWith.initializationSegment;
                                        drmInitData2 = segmentCopyWith.drmInitData;
                                        String str8 = segmentCopyWith.fullSegmentEncryptionKeyUri;
                                        if (segmentCopyWith.encryptionIV != null) {
                                            i2 = i7;
                                            if (!segmentCopyWith.encryptionIV.equals(Long.toHexString(longAttr))) {
                                            }
                                            longAttr++;
                                            i5 = i9;
                                            segment4 = segment6;
                                            stringAttr = str8;
                                            j15 = j8;
                                            i6 = i8 + 1;
                                            i7 = i2;
                                        } else {
                                            i2 = i7;
                                        }
                                        str4 = segmentCopyWith.encryptionIV;
                                        longAttr++;
                                        i5 = i9;
                                        segment4 = segment6;
                                        stringAttr = str8;
                                        j15 = j8;
                                        i6 = i8 + 1;
                                        i7 = i2;
                                    }
                                    j7 = j15;
                                } else {
                                    str2 = str7;
                                    if (next.startsWith(TAG_KEY)) {
                                        String stringAttr4 = parseStringAttr(next, REGEX_METHOD, map);
                                        String optionalStringAttr4 = parseOptionalStringAttr(next, REGEX_KEYFORMAT, KEYFORMAT_IDENTITY, map);
                                        if (METHOD_NONE.equals(stringAttr4)) {
                                            treeMap.clear();
                                            drmInitData2 = null;
                                            str4 = null;
                                            stringAttr = null;
                                        } else {
                                            String optionalStringAttr5 = parseOptionalStringAttr(next, REGEX_IV, map);
                                            if (KEYFORMAT_IDENTITY.equals(optionalStringAttr4)) {
                                                if (METHOD_AES_128.equals(stringAttr4)) {
                                                    stringAttr = parseStringAttr(next, REGEX_URI, map);
                                                    str4 = optionalStringAttr5;
                                                } else {
                                                    str4 = optionalStringAttr5;
                                                    stringAttr = null;
                                                }
                                            } else {
                                                if (encryptionScheme == null) {
                                                    encryptionScheme = parseEncryptionScheme(stringAttr4);
                                                }
                                                DrmInitData.SchemeData drmSchemeData = parseDrmSchemeData(next, optionalStringAttr4, map);
                                                if (drmSchemeData != null) {
                                                    treeMap.put(optionalStringAttr4, drmSchemeData);
                                                    str4 = optionalStringAttr5;
                                                    drmInitData2 = null;
                                                    stringAttr = null;
                                                } else {
                                                    str4 = optionalStringAttr5;
                                                    stringAttr = null;
                                                }
                                            }
                                        }
                                    } else {
                                        if (next.startsWith(TAG_BYTERANGE)) {
                                            String[] strArrSplit2 = Util.split(parseStringAttr(next, REGEX_BYTERANGE, map), "@");
                                            j10 = Long.parseLong(strArrSplit2[0]);
                                            if (strArrSplit2.length > 1) {
                                                j6 = Long.parseLong(strArrSplit2[1]);
                                            }
                                        } else if (next.startsWith(TAG_DISCONTINUITY_SEQUENCE)) {
                                            i4 = Integer.parseInt(next.substring(next.indexOf(58) + 1));
                                            hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                            stringAttr = stringAttr;
                                            str4 = str4;
                                            serverControl = serverControl;
                                            optionalBooleanAttribute = optionalBooleanAttribute;
                                            arrayList4 = arrayList4;
                                            str3 = str2;
                                            z6 = true;
                                        } else if (next.equals(TAG_DISCONTINUITY)) {
                                            i5++;
                                        } else if (next.startsWith(TAG_PROGRAM_DATE_TIME)) {
                                            if (jMsToUs == 0) {
                                                jMsToUs = Util.msToUs(Util.parseXsDateTime(next.substring(next.indexOf(58) + 1))) - j7;
                                            } else {
                                                treeMap = treeMap;
                                            }
                                        } else if (next.equals(TAG_GAP)) {
                                            hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                            stringAttr = stringAttr;
                                            str4 = str4;
                                            serverControl = serverControl;
                                            optionalBooleanAttribute = optionalBooleanAttribute;
                                            arrayList4 = arrayList4;
                                            str3 = str2;
                                            z5 = true;
                                        } else if (next.equals(TAG_INDEPENDENT_SEGMENTS)) {
                                            hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                            stringAttr = stringAttr;
                                            str4 = str4;
                                            serverControl = serverControl;
                                            optionalBooleanAttribute = optionalBooleanAttribute;
                                            arrayList4 = arrayList4;
                                            str3 = str2;
                                            z3 = true;
                                        } else if (next.equals(TAG_ENDLIST)) {
                                            hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                            stringAttr = stringAttr;
                                            str4 = str4;
                                            serverControl = serverControl;
                                            optionalBooleanAttribute = optionalBooleanAttribute;
                                            arrayList4 = arrayList4;
                                            str3 = str2;
                                            z4 = true;
                                        } else if (next.startsWith(TAG_RENDITION_REPORT)) {
                                            treeMap = treeMap;
                                            arrayList3.add(new HlsMediaPlaylist.RenditionReport(Uri.parse(UriUtil.resolve(str, parseStringAttr(next, REGEX_URI, map))), parseOptionalLongAttr(next, REGEX_LAST_MSN, j11), parseOptionalIntAttr(next, REGEX_LAST_PART, -1)));
                                        } else {
                                            treeMap = treeMap;
                                            if (next.startsWith(TAG_PRELOAD_HINT)) {
                                                if (part == null && TYPE_PART.equals(parseStringAttr(next, REGEX_PRELOAD_HINT_TYPE, map))) {
                                                    String stringAttr5 = parseStringAttr(next, REGEX_URI, map);
                                                    long optionalLongAttr = parseOptionalLongAttr(next, REGEX_BYTERANGE_START, -1L);
                                                    long optionalLongAttr2 = parseOptionalLongAttr(next, REGEX_BYTERANGE_LENGTH, -1L);
                                                    long j16 = longAttr;
                                                    String segmentEncryptionIV = getSegmentEncryptionIV(j16, stringAttr, str4);
                                                    if (drmInitData2 == null && !treeMap.isEmpty()) {
                                                        DrmInitData.SchemeData[] schemeDataArr = (DrmInitData.SchemeData[]) treeMap.values().toArray(new DrmInitData.SchemeData[0]);
                                                        drmInitData2 = new DrmInitData(encryptionScheme, schemeDataArr);
                                                        if (playlistProtectionSchemes2 == null) {
                                                            playlistProtectionSchemes2 = getPlaylistProtectionSchemes(encryptionScheme, schemeDataArr);
                                                        }
                                                    }
                                                    DrmInitData drmInitData3 = drmInitData2;
                                                    if (optionalLongAttr == -1 || optionalLongAttr2 != -1) {
                                                        part = new HlsMediaPlaylist.Part(stringAttr5, segment4, 0L, i5, j8, drmInitData3, stringAttr, segmentEncryptionIV, optionalLongAttr != -1 ? optionalLongAttr : 0L, optionalLongAttr2, false, false, true);
                                                    }
                                                    hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                                    hlsMediaPlaylist2 = hlsMediaPlaylist;
                                                    stringAttr = stringAttr;
                                                    str4 = str4;
                                                    longAttr = j16;
                                                    drmInitData2 = drmInitData3;
                                                    serverControl = serverControl;
                                                    optionalBooleanAttribute = optionalBooleanAttribute;
                                                    arrayList4 = arrayList4;
                                                    str3 = str2;
                                                    treeMap = treeMap;
                                                    j11 = -1;
                                                }
                                            } else {
                                                j = longAttr;
                                                if (next.startsWith(TAG_PART)) {
                                                    String segmentEncryptionIV2 = getSegmentEncryptionIV(j, stringAttr, str4);
                                                    String stringAttr6 = parseStringAttr(next, REGEX_URI, map);
                                                    long doubleAttr3 = (long) (parseDoubleAttr(next, REGEX_ATTR_DURATION) * 1000000.0d);
                                                    boolean optionalBooleanAttribute2 = parseOptionalBooleanAttribute(next, REGEX_INDEPENDENT, false) | (z3 && arrayList2.isEmpty());
                                                    boolean optionalBooleanAttribute3 = parseOptionalBooleanAttribute(next, REGEX_GAP, false);
                                                    String optionalStringAttr6 = parseOptionalStringAttr(next, REGEX_ATTR_BYTERANGE, map);
                                                    if (optionalStringAttr6 != null) {
                                                        String[] strArrSplit3 = Util.split(optionalStringAttr6, "@");
                                                        long j17 = Long.parseLong(strArrSplit3[0]);
                                                        if (strArrSplit3.length > 1) {
                                                            j9 = Long.parseLong(strArrSplit3[1]);
                                                        }
                                                        j2 = j17;
                                                    } else {
                                                        j2 = -1;
                                                    }
                                                    long j18 = j2 == -1 ? 0L : j9;
                                                    if (drmInitData2 == null && !treeMap.isEmpty()) {
                                                        DrmInitData.SchemeData[] schemeDataArr2 = (DrmInitData.SchemeData[]) treeMap.values().toArray(new DrmInitData.SchemeData[0]);
                                                        drmInitData2 = new DrmInitData(encryptionScheme, schemeDataArr2);
                                                        if (playlistProtectionSchemes2 == null) {
                                                            playlistProtectionSchemes2 = getPlaylistProtectionSchemes(encryptionScheme, schemeDataArr2);
                                                        }
                                                    }
                                                    DrmInitData drmInitData4 = drmInitData2;
                                                    HlsMediaPlaylist.Segment segment7 = segment4;
                                                    int i10 = i5;
                                                    arrayList2.add(new HlsMediaPlaylist.Part(stringAttr6, segment4, doubleAttr3, i5, j8, drmInitData4, stringAttr, segmentEncryptionIV2, j18, j2, optionalBooleanAttribute3, optionalBooleanAttribute2, false));
                                                    j8 += doubleAttr3;
                                                    if (j2 != -1) {
                                                        j18 += j2;
                                                    }
                                                    j9 = j18;
                                                    segment4 = segment7;
                                                    stringAttr = stringAttr;
                                                    longAttr = j;
                                                    i5 = i10;
                                                    drmInitData2 = drmInitData4;
                                                    str3 = str2;
                                                } else {
                                                    segment = segment4;
                                                    i = i5;
                                                    if (next.startsWith("#")) {
                                                        j7 = j7;
                                                        hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                                                        segment4 = segment;
                                                        stringAttr = stringAttr;
                                                        str4 = str4;
                                                        longAttr = j;
                                                        optionalStringAttr = optionalStringAttr;
                                                        timeSecondsToUs = timeSecondsToUs;
                                                        j7 = j7;
                                                        j6 = j6;
                                                        serverControl = serverControl;
                                                        optionalBooleanAttribute = optionalBooleanAttribute;
                                                        arrayList4 = arrayList4;
                                                        str3 = str2;
                                                        treeMap = treeMap;
                                                        j11 = -1;
                                                        hlsMediaPlaylist2 = hlsMediaPlaylist;
                                                        i5 = i;
                                                    } else {
                                                        long j19 = j7;
                                                        String segmentEncryptionIV3 = getSegmentEncryptionIV(j, stringAttr, str4);
                                                        longAttr = j + 1;
                                                        String strReplaceVariableReferences = replaceVariableReferences(next, map);
                                                        HlsMediaPlaylist.Segment segment8 = (HlsMediaPlaylist.Segment) map2.get(strReplaceVariableReferences);
                                                        if (j10 == -1) {
                                                            segment2 = segment8;
                                                            j4 = 0;
                                                        } else {
                                                            if (z7 && segment == null && segment8 == null) {
                                                                j3 = j6;
                                                                segment8 = new HlsMediaPlaylist.Segment(strReplaceVariableReferences, 0L, j3, null, null);
                                                                map2.put(strReplaceVariableReferences, segment8);
                                                            } else {
                                                                j3 = j6;
                                                            }
                                                            segment2 = segment8;
                                                            j4 = j3;
                                                        }
                                                        if (drmInitData2 != null || treeMap.isEmpty()) {
                                                            z = false;
                                                        } else {
                                                            z = false;
                                                            DrmInitData.SchemeData[] schemeDataArr3 = (DrmInitData.SchemeData[]) treeMap.values().toArray(new DrmInitData.SchemeData[0]);
                                                            drmInitData2 = new DrmInitData(encryptionScheme, schemeDataArr3);
                                                            if (playlistProtectionSchemes2 == null) {
                                                                playlistProtectionSchemes = getPlaylistProtectionSchemes(encryptionScheme, schemeDataArr3);
                                                                drmInitData = drmInitData2;
                                                            }
                                                            if (segment != null) {
                                                                segment3 = segment;
                                                            } else {
                                                                segment3 = segment2;
                                                            }
                                                            long j20 = timeSecondsToUs;
                                                            arrayList.add(new HlsMediaPlaylist.Segment(strReplaceVariableReferences, segment3, optionalStringAttr, j20, i, j19, drmInitData, stringAttr, segmentEncryptionIV3, j4, j10, z5, arrayList2));
                                                            j8 = j19 + j20;
                                                            arrayList2 = new ArrayList();
                                                            if (j10 != -1) {
                                                                j4 += j10;
                                                            }
                                                            j6 = j4;
                                                            segment4 = segment;
                                                            stringAttr = stringAttr;
                                                            playlistProtectionSchemes2 = playlistProtectionSchemes;
                                                            z5 = z;
                                                            i5 = i;
                                                            drmInitData2 = drmInitData;
                                                            j7 = j8;
                                                            timeSecondsToUs = 0;
                                                            optionalStringAttr = str2;
                                                            str3 = optionalStringAttr;
                                                            j10 = -1;
                                                        }
                                                        drmInitData = drmInitData2;
                                                        playlistProtectionSchemes = playlistProtectionSchemes2;
                                                        if (segment != null) {
                                                            segment3 = segment;
                                                        } else {
                                                            segment3 = segment2;
                                                        }
                                                        long j21 = timeSecondsToUs;
                                                        arrayList.add(new HlsMediaPlaylist.Segment(strReplaceVariableReferences, segment3, optionalStringAttr, j21, i, j19, drmInitData, stringAttr, segmentEncryptionIV3, j4, j10, z5, arrayList2));
                                                        j8 = j19 + j21;
                                                        arrayList2 = new ArrayList();
                                                        if (j10 != -1) {
                                                            j4 += j10;
                                                        }
                                                        j6 = j4;
                                                        segment4 = segment;
                                                        stringAttr = stringAttr;
                                                        playlistProtectionSchemes2 = playlistProtectionSchemes;
                                                        z5 = z;
                                                        i5 = i;
                                                        drmInitData2 = drmInitData;
                                                        j7 = j8;
                                                        timeSecondsToUs = 0;
                                                        optionalStringAttr = str2;
                                                        str3 = optionalStringAttr;
                                                        j10 = -1;
                                                    }
                                                }
                                                j11 = -1;
                                                hlsMediaPlaylist2 = hlsMediaPlaylist;
                                            }
                                        }
                                        stringAttr = stringAttr;
                                        str4 = str4;
                                    }
                                }
                                serverControl = serverControl;
                                optionalBooleanAttribute = optionalBooleanAttribute;
                                arrayList4 = arrayList4;
                                str3 = str2;
                            }
                            segment = segment4;
                            i = i5;
                            j = longAttr;
                            hlsMultivariantPlaylist = hlsMultivariantPlaylist;
                            segment4 = segment;
                            stringAttr = stringAttr;
                            str4 = str4;
                            longAttr = j;
                            optionalStringAttr = optionalStringAttr;
                            timeSecondsToUs = timeSecondsToUs;
                            j7 = j7;
                            j6 = j6;
                            serverControl = serverControl;
                            optionalBooleanAttribute = optionalBooleanAttribute;
                            arrayList4 = arrayList4;
                            str3 = str2;
                            treeMap = treeMap;
                            j11 = -1;
                            hlsMediaPlaylist2 = hlsMediaPlaylist;
                            i5 = i;
                        }
                        serverControl = serverControl;
                        optionalBooleanAttribute = optionalBooleanAttribute;
                        arrayList4 = arrayList4;
                    }
                }
                serverControl = serverControl;
            }
        }
        boolean z9 = optionalBooleanAttribute;
        ArrayList arrayList7 = arrayList4;
        HlsMediaPlaylist.ServerControl serverControl2 = serverControl;
        HashMap map3 = new HashMap();
        for (int i11 = 0; i11 < arrayList3.size(); i11++) {
            HlsMediaPlaylist.RenditionReport renditionReport = (HlsMediaPlaylist.RenditionReport) arrayList3.get(i11);
            long size = renditionReport.lastMediaSequence;
            if (size == -1) {
                size = (j12 + ((long) arrayList.size())) - (arrayList2.isEmpty() ? 1L : 0L);
            }
            int size2 = renditionReport.lastPartIndex;
            if (size2 == -1 && doubleAttr != -9223372036854775807L) {
                size2 = (arrayList2.isEmpty() ? ((HlsMediaPlaylist.Segment) Iterables.getLast(arrayList)).parts : arrayList2).size() - 1;
            }
            map3.put(renditionReport.playlistUri, new HlsMediaPlaylist.RenditionReport(renditionReport.playlistUri, size, size2));
        }
        if (part != null) {
            arrayList2.add(part);
        }
        return new HlsMediaPlaylist(i3, str, arrayList7, j5, z9, jMsToUs, z6, i4, j12, intAttr2, intAttr, doubleAttr, z3, z4, jMsToUs != 0, playlistProtectionSchemes2, arrayList, arrayList2, serverControl2, map3);
    }

    private static DrmInitData getPlaylistProtectionSchemes(String str, DrmInitData.SchemeData[] schemeDataArr) {
        DrmInitData.SchemeData[] schemeDataArr2 = new DrmInitData.SchemeData[schemeDataArr.length];
        for (int i = 0; i < schemeDataArr.length; i++) {
            schemeDataArr2[i] = schemeDataArr[i].copyWithData(null);
        }
        return new DrmInitData(str, schemeDataArr2);
    }

    private static String getSegmentEncryptionIV(long j, String str, String str2) {
        if (str == null) {
            return null;
        }
        return str2 != null ? str2 : Long.toHexString(j);
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference failed for: r0v2, types: [int] */
    /* JADX WARN: Type inference failed for: r0v5 */
    /* JADX WARN: Type inference failed for: r0v6 */
    private static int parseSelectionFlags(String str) {
        boolean optionalBooleanAttribute = parseOptionalBooleanAttribute(str, REGEX_DEFAULT, false);
        ?? r0 = optionalBooleanAttribute;
        if (parseOptionalBooleanAttribute(str, REGEX_FORCED, false)) {
            r0 = (optionalBooleanAttribute ? 1 : 0) | 2;
        }
        return parseOptionalBooleanAttribute(str, REGEX_AUTOSELECT, false) ? r0 | 4 : r0;
    }

    private static int parseRoleFlags(String str, Map<String, String> map) {
        String optionalStringAttr = parseOptionalStringAttr(str, REGEX_CHARACTERISTICS, map);
        if (TextUtils.isEmpty(optionalStringAttr)) {
            return 0;
        }
        String[] strArrSplit = Util.split(optionalStringAttr, ",");
        int i = Util.contains(strArrSplit, "public.accessibility.describes-video") ? 512 : 0;
        if (Util.contains(strArrSplit, "public.accessibility.transcribes-spoken-dialog")) {
            i |= 4096;
        }
        if (Util.contains(strArrSplit, "public.accessibility.describes-music-and-sound")) {
            i |= DecoderReuseEvaluation.DISCARD_REASON_VIDEO_ROTATION_CHANGED;
        }
        return Util.contains(strArrSplit, "public.easy-to-read") ? i | DecoderReuseEvaluation.DISCARD_REASON_AUDIO_SAMPLE_RATE_CHANGED : i;
    }

    private static DrmInitData.SchemeData parseDrmSchemeData(String str, String str2, Map<String, String> map) throws ParserException {
        String optionalStringAttr = parseOptionalStringAttr(str, REGEX_KEYFORMATVERSIONS, IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE, map);
        if (KEYFORMAT_WIDEVINE_PSSH_BINARY.equals(str2)) {
            String stringAttr = parseStringAttr(str, REGEX_URI, map);
            return new DrmInitData.SchemeData(C.WIDEVINE_UUID, MimeTypes.VIDEO_MP4, Base64.decode(stringAttr.substring(stringAttr.indexOf(44)), 0));
        }
        if (KEYFORMAT_WIDEVINE_PSSH_JSON.equals(str2)) {
            return new DrmInitData.SchemeData(C.WIDEVINE_UUID, "hls", Util.getUtf8Bytes(str));
        }
        if (!KEYFORMAT_PLAYREADY.equals(str2) || !IcyHeaders.REQUEST_HEADER_ENABLE_METADATA_VALUE.equals(optionalStringAttr)) {
            return null;
        }
        String stringAttr2 = parseStringAttr(str, REGEX_URI, map);
        return new DrmInitData.SchemeData(C.PLAYREADY_UUID, MimeTypes.VIDEO_MP4, PsshAtomUtil.buildPsshAtom(C.PLAYREADY_UUID, Base64.decode(stringAttr2.substring(stringAttr2.indexOf(44)), 0)));
    }

    private static HlsMediaPlaylist.ServerControl parseServerControl(String str) {
        double optionalDoubleAttr = parseOptionalDoubleAttr(str, REGEX_CAN_SKIP_UNTIL, -9.223372036854776E18d);
        long j = optionalDoubleAttr == -9.223372036854776E18d ? -9223372036854775807L : (long) (optionalDoubleAttr * 1000000.0d);
        boolean optionalBooleanAttribute = parseOptionalBooleanAttribute(str, REGEX_CAN_SKIP_DATE_RANGES, false);
        double optionalDoubleAttr2 = parseOptionalDoubleAttr(str, REGEX_HOLD_BACK, -9.223372036854776E18d);
        long j2 = optionalDoubleAttr2 == -9.223372036854776E18d ? -9223372036854775807L : (long) (optionalDoubleAttr2 * 1000000.0d);
        double optionalDoubleAttr3 = parseOptionalDoubleAttr(str, REGEX_PART_HOLD_BACK, -9.223372036854776E18d);
        return new HlsMediaPlaylist.ServerControl(j, optionalBooleanAttribute, j2, optionalDoubleAttr3 != -9.223372036854776E18d ? (long) (optionalDoubleAttr3 * 1000000.0d) : -9223372036854775807L, parseOptionalBooleanAttribute(str, REGEX_CAN_BLOCK_RELOAD, false));
    }

    private static String parseEncryptionScheme(String str) {
        if (METHOD_SAMPLE_AES_CENC.equals(str) || METHOD_SAMPLE_AES_CTR.equals(str)) {
            return "cenc";
        }
        return "cbcs";
    }

    private static int parseIntAttr(String str, Pattern pattern) throws ParserException {
        return Integer.parseInt(parseStringAttr(str, pattern, Collections.emptyMap()));
    }

    private static int parseOptionalIntAttr(String str, Pattern pattern, int i) {
        Matcher matcher = pattern.matcher(str);
        return matcher.find() ? Integer.parseInt((String) Assertions.checkNotNull(matcher.group(1))) : i;
    }

    private static long parseLongAttr(String str, Pattern pattern) throws ParserException {
        return Long.parseLong(parseStringAttr(str, pattern, Collections.emptyMap()));
    }

    private static long parseOptionalLongAttr(String str, Pattern pattern, long j) {
        Matcher matcher = pattern.matcher(str);
        return matcher.find() ? Long.parseLong((String) Assertions.checkNotNull(matcher.group(1))) : j;
    }

    private static long parseTimeSecondsToUs(String str, Pattern pattern) throws ParserException {
        return new BigDecimal(parseStringAttr(str, pattern, Collections.emptyMap())).multiply(new BigDecimal(DefaultBandwidthMeter.DEFAULT_INITIAL_BITRATE_ESTIMATE)).longValue();
    }

    private static double parseDoubleAttr(String str, Pattern pattern) throws ParserException {
        return Double.parseDouble(parseStringAttr(str, pattern, Collections.emptyMap()));
    }

    private static String parseStringAttr(String str, Pattern pattern, Map<String, String> map) throws ParserException {
        String optionalStringAttr = parseOptionalStringAttr(str, pattern, map);
        if (optionalStringAttr != null) {
            return optionalStringAttr;
        }
        throw ParserException.createForMalformedManifest("Couldn't match " + pattern.pattern() + " in " + str, null);
    }

    private static String parseOptionalStringAttr(String str, Pattern pattern, Map<String, String> map) {
        return parseOptionalStringAttr(str, pattern, null, map);
    }

    private static String parseOptionalStringAttr(String str, Pattern pattern, String str2, Map<String, String> map) {
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            str2 = (String) Assertions.checkNotNull(matcher.group(1));
        }
        return (map.isEmpty() || str2 == null) ? str2 : replaceVariableReferences(str2, map);
    }

    private static double parseOptionalDoubleAttr(String str, Pattern pattern, double d) {
        Matcher matcher = pattern.matcher(str);
        return matcher.find() ? Double.parseDouble((String) Assertions.checkNotNull(matcher.group(1))) : d;
    }

    private static String replaceVariableReferences(String str, Map<String, String> map) {
        Matcher matcher = REGEX_VARIABLE_REFERENCE.matcher(str);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            String strGroup = matcher.group(1);
            if (map.containsKey(strGroup)) {
                matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement(map.get(strGroup)));
            }
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    private static boolean parseOptionalBooleanAttribute(String str, Pattern pattern, boolean z) {
        Matcher matcher = pattern.matcher(str);
        return matcher.find() ? BOOLEAN_TRUE.equals(matcher.group(1)) : z;
    }

    private static Pattern compileBooleanAttrPattern(String str) {
        return Pattern.compile(str + "=(NO|YES)");
    }

    private static class LineIterator {
        private final Queue<String> extraLines;
        private String next;
        private final BufferedReader reader;

        public LineIterator(Queue<String> queue, BufferedReader bufferedReader) {
            this.extraLines = queue;
            this.reader = bufferedReader;
        }

        @EnsuresNonNullIf(expression = {"next"}, result = true)
        public boolean hasNext() throws IOException {
            String strTrim;
            if (this.next != null) {
                return true;
            }
            if (!this.extraLines.isEmpty()) {
                this.next = (String) Assertions.checkNotNull(this.extraLines.poll());
                return true;
            }
            do {
                String line = this.reader.readLine();
                this.next = line;
                if (line == null) {
                    return false;
                }
                strTrim = line.trim();
                this.next = strTrim;
            } while (strTrim.isEmpty());
            return true;
        }

        public String next() throws IOException {
            if (hasNext()) {
                String str = this.next;
                this.next = null;
                return str;
            }
            throw new NoSuchElementException();
        }
    }
}
