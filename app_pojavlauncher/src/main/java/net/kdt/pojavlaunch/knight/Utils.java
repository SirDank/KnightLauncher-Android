package net.kdt.pojavlaunch.knight;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import java.io.FileOutputStream;

public class Utils {
    public static byte[] getFromWeb(String url, Progress pr) throws IOException {
        boolean sendProgress = true;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.connect();
        if (conn.getContentLength() != -1) {
            pr.postMaxPart(conn.getContentLength());
        } else {
            pr.setPartIndeterminate(true);
            sendProgress = false;
        }
        InputStream is = conn.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int i;
        int cur = 0;
        while ((i = is.read(buf)) != -1) {
            cur += i;
            if (sendProgress)
                pr.postStepProgress(cur);
            baos.write(buf, 0, i);
        }
        return baos.toByteArray();
    }

    public static int indexOf(byte[] haystack, byte[] needle) {
        // needle is null or empty
        if (needle == null || needle.length == 0)
            return 0;

        // haystack is null, or haystack's length is less than that of needle
        if (haystack == null || needle.length > haystack.length)
            return -1;

        // pre construct failure array for needle pattern
        int[] failure = new int[needle.length];
        int n = needle.length;
        failure[0] = -1;
        for (int j = 1; j < n; j++) {
            int i = failure[j - 1];
            while ((needle[j] != needle[i + 1]) && i >= 0)
                i = failure[i];
            if (needle[j] == needle[i + 1])
                failure[j] = i + 1;
            else
                failure[j] = -1;
        }

        // find match
        int i = 0, j = 0;
        int haystackLen = haystack.length;
        int needleLen = needle.length;
        while (i < haystackLen && j < needleLen) {
            if (haystack[i] == needle[j]) {
                i++;
                j++;
            } else if (j == 0)
                i++;
            else
                j = failure[j - 1] + 1;
        }
        return ((j == needleLen) ? (i - needleLen) : -1);
    }

    public static int indexOf(byte[] array, int off, byte sym) {
        for (int i = off; i < array.length; i++) {
            if (array[i] == sym)
                return i;
        }
        return -1;
    }

    public static void downloadFile(String url, File dest, Progress pr) throws IOException {
        boolean sendProgress = true;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.connect();
        int totalLen = conn.getContentLength();
        if (totalLen != -1) {
            // We don't want to reset max part every time if we are part of a larger
            // process,
            // but for now let's just not touch max part here or maybe we should?
            // The caller should handle max steps/parts.
            // Let's just post progress.
        } else {
            // pr.setPartIndeterminate(true);
            sendProgress = false;
        }

        dest.getParentFile().mkdirs();

        try (InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int i;
            int cur = 0;
            while ((i = is.read(buf)) != -1) {
                cur += i;
                // if (sendProgress) pr.postStepProgress(cur); // This might spam too much if we
                // don't throttle or if caller doesn't expect it
                fos.write(buf, 0, i);
            }
        }
    }

    public static int findXth(byte[] array, byte sym, int cnt) {
        int xthCount = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i] == sym) {
                xthCount++;
                if (xthCount == cnt)
                    return i;
            }
        }
        return -1;
    }
}