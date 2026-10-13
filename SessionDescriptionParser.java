package com.google.android.exoplayer2.source.rtsp;

import android.net.Uri;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.database.VersionTable;
import com.google.android.exoplayer2.metadata.dvbsi.AppInfoTableDecoder;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Strings;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
final class SessionDescriptionParser {
    private static final String ATTRIBUTE_TYPE = "a";
    private static final String BANDWIDTH_TYPE = "b";
    private static final String CONNECTION_TYPE = "c";
    private static final String EMAIL_TYPE = "e";
    private static final String INFORMATION_TYPE = "i";
    private static final String KEY_TYPE = "k";
    private static final String MEDIA_TYPE = "m";
    private static final String ORIGIN_TYPE = "o";
    private static final String PHONE_NUMBER_TYPE = "p";
    private static final String REPEAT_TYPE = "r";
    private static final String SESSION_TYPE = "s";
    private static final String TIMING_TYPE = "t";
    private static final String URI_TYPE = "u";
    private static final String VERSION_TYPE = "v";
    private static final String ZONE_TYPE = "z";
    private static final Pattern SDP_LINE_PATTERN = Pattern.compile("([a-z])=\\s?(.+)");
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([\\x21\\x23-\\x27\\x2a\\x2b\\x2d\\x2e\\x30-\\x39\\x41-\\x5a\\x5e-\\x7e]+)(?::(.*))?");
    private static final Pattern MEDIA_DESCRIPTION_PATTERN = Pattern.compile("(\\S+)\\s(\\S+)\\s(\\S+)\\s(\\S+)");

    /* JADX WARN: Can't fix incorrect switch cases order, some code will duplicate */
    /* JADX WARN: Code duplicated, block: B:57:0x00e9  */
    public static SessionDescription parse(String str) throws ParserException {
        byte b;
        SessionDescription.Builder builder = new SessionDescription.Builder();
        MediaDescription.Builder mediaDescriptionLine = null;
        for (String str2 : RtspMessageUtil.splitRtspMessageBody(str)) {
            if (!"".equals(str2)) {
                Matcher matcher = SDP_LINE_PATTERN.matcher(str2);
                if (!matcher.matches()) {
                    throw ParserException.createForMalformedManifest("Malformed SDP line: " + str2, null);
                }
                String str3 = (String) Assertions.checkNotNull(matcher.group(1));
                String str4 = (String) Assertions.checkNotNull(matcher.group(2));
                switch (str3.hashCode()) {
                    case 97:
                        if (str3.equals(ATTRIBUTE_TYPE)) {
                            b = 11;
                        } else {
                            b = -1;
                        }
                        break;
                    case 98:
                        if (str3.equals(BANDWIDTH_TYPE)) {
                            b = 8;
                        } else {
                            b = -1;
                        }
                        break;
                    case 99:
                        if (str3.equals(CONNECTION_TYPE)) {
                            b = 7;
                        } else {
                            b = -1;
                        }
                        break;
                    case 100:
                    case 102:
                    case 103:
                    case 104:
                    case 106:
                    case 108:
                    case 110:
                    case 113:
                    case 119:
                    case 120:
                    case 121:
                    default:
                        b = -1;
                        break;
                    case 101:
                        if (str3.equals(EMAIL_TYPE)) {
                            b = 5;
                        } else {
                            b = -1;
                        }
                        break;
                    case 105:
                        if (str3.equals(INFORMATION_TYPE)) {
                            b = 3;
                        } else {
                            b = -1;
                        }
                        break;
                    case 107:
                        if (str3.equals(KEY_TYPE)) {
                            b = 10;
                        } else {
                            b = -1;
                        }
                        break;
                    case 109:
                        if (str3.equals(MEDIA_TYPE)) {
                            b = 12;
                        } else {
                            b = -1;
                        }
                        break;
                    case 111:
                        if (str3.equals(ORIGIN_TYPE)) {
                            b = 1;
                        } else {
                            b = -1;
                        }
                        break;
                    case 112:
                        if (str3.equals("p")) {
                            b = 6;
                        } else {
                            b = -1;
                        }
                        break;
                    case 114:
                        if (str3.equals(REPEAT_TYPE)) {
                            b = 13;
                        } else {
                            b = -1;
                        }
                        break;
                    case 115:
                        if (str3.equals(SESSION_TYPE)) {
                            b = 2;
                        } else {
                            b = -1;
                        }
                        break;
                    case AppInfoTableDecoder.APPLICATION_INFORMATION_TABLE_ID /* 116 */:
                        if (str3.equals(TIMING_TYPE)) {
                            b = 9;
                        } else {
                            b = -1;
                        }
                        break;
                    case 117:
                        if (str3.equals(URI_TYPE)) {
                            b = 4;
                        } else {
                            b = -1;
                        }
                        break;
                    case 118:
                        if (str3.equals(VERSION_TYPE)) {
                            b = 0;
                        } else {
                            b = -1;
                        }
                        break;
                    case 122:
                        if (str3.equals(ZONE_TYPE)) {
                            b = 14;
                        } else {
                            b = -1;
                        }
                        break;
                }
                switch (b) {
                    case 0:
                        if (!SessionDescription.SUPPORTED_SDP_VERSION.equals(str4)) {
                            throw ParserException.createForMalformedManifest(String.format("SDP version %s is not supported.", str4), null);
                        }
                        break;
                        break;
                    case 1:
                        builder.setOrigin(str4);
                        break;
                    case 2:
                        builder.setSessionName(str4);
                        break;
                    case 3:
                        if (mediaDescriptionLine == null) {
                            builder.setSessionInfo(str4);
                        } else {
                            mediaDescriptionLine.setMediaTitle(str4);
                        }
                        break;
                    case 4:
                        builder.setUri(Uri.parse(str4));
                        break;
                    case 5:
                        builder.setEmailAddress(str4);
                        break;
                    case 6:
                        builder.setPhoneNumber(str4);
                        break;
                    case 7:
                        if (mediaDescriptionLine == null) {
                            builder.setConnection(str4);
                        } else {
                            mediaDescriptionLine.setConnection(str4);
                        }
                        break;
                    case 8:
                        String[] strArrSplit = Util.split(str4, ":\\s?");
                        Assertions.checkArgument(strArrSplit.length == 2);
                        int i = Integer.parseInt(strArrSplit[1]);
                        if (mediaDescriptionLine == null) {
                            builder.setBitrate(i * VersionTable.FEATURE_EXTERNAL);
                        } else {
                            mediaDescriptionLine.setBitrate(i * VersionTable.FEATURE_EXTERNAL);
                        }
                        break;
                    case 9:
                        builder.setTiming(str4);
                        break;
                    case 10:
                        if (mediaDescriptionLine == null) {
                            builder.setKey(str4);
                        } else {
                            mediaDescriptionLine.setKey(str4);
                        }
                        break;
                    case 11:
                        Matcher matcher2 = ATTRIBUTE_PATTERN.matcher(str4);
                        if (!matcher2.matches()) {
                            throw ParserException.createForMalformedManifest("Malformed Attribute line: " + str2, null);
                        }
                        String str5 = (String) Assertions.checkNotNull(matcher2.group(1));
                        String strNullToEmpty = Strings.nullToEmpty(matcher2.group(2));
                        if (mediaDescriptionLine == null) {
                            builder.addAttribute(str5, strNullToEmpty);
                        } else {
                            mediaDescriptionLine.addAttribute(str5, strNullToEmpty);
                        }
                        break;
                        break;
                    case 12:
                        if (mediaDescriptionLine != null) {
                            addMediaDescriptionToSession(builder, mediaDescriptionLine);
                        }
                        mediaDescriptionLine = parseMediaDescriptionLine(str4);
                        break;
                }
            }
        }
        if (mediaDescriptionLine != null) {
            addMediaDescriptionToSession(builder, mediaDescriptionLine);
        }
        try {
            return builder.build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw ParserException.createForMalformedManifest(null, e);
        }
    }

    private static void addMediaDescriptionToSession(SessionDescription.Builder builder, MediaDescription.Builder builder2) throws ParserException {
        try {
            builder.addMediaDescription(builder2.build());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw ParserException.createForMalformedManifest(null, e);
        }
    }

    private static MediaDescription.Builder parseMediaDescriptionLine(String str) throws ParserException {
        Matcher matcher = MEDIA_DESCRIPTION_PATTERN.matcher(str);
        if (!matcher.matches()) {
            throw ParserException.createForMalformedManifest("Malformed SDP media description line: " + str, null);
        }
        try {
            return new MediaDescription.Builder((String) Assertions.checkNotNull(matcher.group(1)), Integer.parseInt((String) Assertions.checkNotNull(matcher.group(2))), (String) Assertions.checkNotNull(matcher.group(3)), Integer.parseInt((String) Assertions.checkNotNull(matcher.group(4))));
        } catch (NumberFormatException e) {
            throw ParserException.createForMalformedManifest("Malformed SDP media description line: " + str, e);
        }
    }

    private SessionDescriptionParser() {
    }
}
