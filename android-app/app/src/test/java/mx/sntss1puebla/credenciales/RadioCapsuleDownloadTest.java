package mx.sntss1puebla.credenciales;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

public class RadioCapsuleDownloadTest {
    private static byte[] wav(int seconds) {
        int samples = 8000 * seconds;
        ByteBuffer out = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46464952).putInt(out.capacity() - 8).putInt(0x45564157);
        out.putInt(0x20746d66).putInt(16).putShort((short)1).putShort((short)1);
        out.putInt(8000).putInt(16000).putShort((short)2).putShort((short)16);
        out.putInt(0x61746164).putInt(samples * 2);
        while (out.hasRemaining()) out.putShort((short)400);
        return out.array();
    }

    private static URL fixture(int status, String type, long length, InputStream stream) throws IOException {
        return new URL(null, "https://fixture.invalid/api/radio/voice", new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) {
                return new HttpURLConnection(url) {
                    @Override public int getResponseCode() { return status; }
                    @Override public String getContentType() { return type; }
                    @Override public long getContentLengthLong() { return length; }
                    @Override public InputStream getInputStream() {
                        assertFalse(getInstanceFollowRedirects());
                        assertEquals("identity", getRequestProperty("Accept-Encoding"));
                        return stream;
                    }
                    @Override public void connect() {}
                    @Override public void disconnect() {}
                    @Override public boolean usingProxy() { return false; }
                };
            }
        });
    }

    private static URL response(String type, long length, byte[] bytes) throws IOException {
        return fixture(200, type, length, new ByteArrayInputStream(bytes));
    }

    private static long deadline() { return System.nanoTime() + TimeUnit.SECONDS.toNanos(5); }

    @Test public void longerWavIsStoredIdenticallyOnlyAfterCompleteEofEvenWithoutLength() throws Exception {
        File dir = Files.createTempDirectory("capsule-test").toFile();
        byte[] original = wav(25);
        try {
            File clip = RadioCapsuleDownload.fetch(response("audio/wav", -1, original), dir, deadline());
            assertNotNull(clip);
            assertTrue(clip.getName().endsWith(".wav"));
            assertArrayEquals(original, Files.readAllBytes(clip.toPath()));
            assertTrue(clip.delete());
        } finally { dir.delete(); }
    }

    @Test public void existingRiffWithTrailingMetadataKeepsEveryByte() throws Exception {
        File dir = Files.createTempDirectory("capsule-test").toFile();
        byte[] wav = wav(2);
        byte[] original = Arrays.copyOf(wav, wav.length + 5);
        original[original.length - 1] = 1;
        try {
            File clip = RadioCapsuleDownload.fetch(response("audio/wav", original.length, original), dir, deadline());
            assertNotNull(clip);
            assertArrayEquals(original, Files.readAllBytes(clip.toPath()));
            assertTrue(clip.delete());
        } finally { dir.delete(); }
    }

    @Test public void completeMp3WorksAndPartialMpegFrameDoesNot() throws Exception {
        File dir = Files.createTempDirectory("capsule-test").toFile();
        byte[] frames = new byte[834];
        for (int i : new int[] {0, 417}) { frames[i]=(byte)0xff; frames[i+1]=(byte)0xfb; frames[i+2]=(byte)0x90; }
        try {
            File valid = RadioCapsuleDownload.fetch(response("audio/mpeg", frames.length, frames), dir, deadline());
            assertNotNull(valid);
            assertTrue(valid.getName().endsWith(".mp3"));
            assertArrayEquals(frames, Files.readAllBytes(valid.toPath()));
            assertTrue(valid.delete());
            assertNull(RadioCapsuleDownload.fetch(response("audio/mpeg", -1, Arrays.copyOf(frames, 697)), dir, deadline()));
            assertEquals(0, dir.list().length);
        } finally { dir.delete(); }
    }

    @Test public void oversizedMissingTruncatedOrNonAudioDownloadsLeaveNoFiles() throws Exception {
        File dir = Files.createTempDirectory("capsule-test").toFile();
        byte[] original = wav(1);
        try {
            assertNull(RadioCapsuleDownload.fetch(fixture(204, "audio/wav", -1, InputStream.nullInputStream()), dir, deadline()));
            assertNull(RadioCapsuleDownload.fetch(response("text/html", original.length, original), dir, deadline()));
            assertNull(RadioCapsuleDownload.fetch(response("audio/wav", original.length + 1, original), dir, deadline()));
            assertNull(RadioCapsuleDownload.fetch(response("audio/wav", -1, Arrays.copyOf(original, original.length - 1)), dir, deadline()));
            assertNull(RadioCapsuleDownload.fetch(response("audio/wav", RadioCapsuleDownload.MAX_BYTES + 1L, original), dir, deadline()));
            assertNull(RadioCapsuleDownload.fetch(response("audio/wav", -1, new byte[RadioCapsuleDownload.MAX_BYTES + 1]), dir, deadline()));
            assertEquals(0, dir.list().length);
        } finally { dir.delete(); }
    }

    @Test public void expiredOrCanceledPartialDownloadIsDeleted() throws Exception {
        File dir = Files.createTempDirectory("capsule-test").toFile();
        try {
            assertNull(RadioCapsuleDownload.fetch(response("audio/wav", -1, wav(2)), dir, System.nanoTime() - 1));
            InputStream interrupted = new InputStream() {
                boolean readOnce;
                @Override public int read() { return -1; }
                @Override public int read(byte[] bytes) {
                    if (!readOnce) { readOnce = true; Arrays.fill(bytes, (byte)1); return 50; }
                    Thread.currentThread().interrupt();
                    return 6;
                }
            };
            assertNull(RadioCapsuleDownload.fetch(fixture(200, "audio/wav", -1, interrupted), dir, deadline()));
            assertTrue(Thread.interrupted());
            assertEquals(0, dir.list().length);
        } finally { Thread.interrupted(); dir.delete(); }
    }
}
