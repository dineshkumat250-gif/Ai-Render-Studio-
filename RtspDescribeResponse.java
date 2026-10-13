package com.google.android.exoplayer2.source.rtsp;

/* JADX INFO: loaded from: /data/user/0/com.apktools.app.decompile/cache/managed/decompile/jadx-temp/session-0/classes2.dex */
final class RtspDescribeResponse {
    public final SessionDescription sessionDescription;
    public final int status;

    public RtspDescribeResponse(int i, SessionDescription sessionDescription) {
        this.status = i;
        this.sessionDescription = sessionDescription;
    }
}
