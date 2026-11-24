package mx.sntss1puebla.credenciales;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

/** Bounded native download of the existing, retrieval-only radio capsule endpoint. */
final class RadioCapsuleDownload {
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final int SOCKET_TIMEOUT_MS = 1_500;
    private static final int WAV_RIFF = 0x52494646;
    private static final int WAV_WAVE = 0x57415645;
    private static final int WAV_FORMAT = 0x666d7420;
    private static final int WAV_DATA = 0x64617461;
    private static final int[] MP3_BITRATES_1 = {0,32,40,48,56,64,80,96,112,128,160,192,224,256,320};
    private static final int[] MP3_BITRATES_2 = {0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
    private static final int[] MP3_SAMPLE_RATES = {44100,48000,32000};

    private RadioCapsuleDownload() {}

    record Clip(File file, RadioCapsuleHistory.Entry metadata) {}

    /** The caller owns the returned file; null never leaves behind a partial file. */
    static File fetch(URL url, File directory, long deadlineNanos) {
        Clip clip = fetchClip(url, directory, deadlineNanos, null);
        return clip == null ? null : clip.file();
    }

    static Clip fetchClip(URL url, File directory, long deadlineNanos, RadioCapsuleCadence.Slot slot) {
        HttpURLConnection connection = null;
        File temporary = null;
        boolean keep = false;
        try {
            if (expired(deadlineNanos)) return null;
            if (!directory.isDirectory() && !directory.mkdirs()) return null;
            // This private directory only holds capsules; clean up after a killed process.
            File[] stale = directory.listFiles();
            if (stale != null) for (File old : stale) {
                if (old.getName().startsWith("capsule-") && System.currentTimeMillis() - old.lastModified() > 86_400_000L) old.delete();
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(SOCKET_TIMEOUT_MS);
            connection.setReadTimeout(SOCKET_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "audio/wav, audio/mpeg");
            connection.setRequestProperty("Accept-Encoding", "identity");
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK || expired(deadlineNanos)) return null;
            // Require the opt-in backend contract, not a heuristic legacy recording.
            if (slot != null && (!slot.query.equals(connection.getHeaderField("x-radio-capsule-slot")) ||
                    !slot.accepts(connection.getHeaderField("x-radio-kind")))) return null;
            RadioCapsuleHistory.Entry metadata = RadioCapsuleHistory.Entry.fromHeaders(
                connection.getHeaderField("x-radio-capsule-id"), connection.getHeaderField("x-radio-template"));
            if (slot != null && metadata == null) return null;
            String type = connection.getContentType();
            if (type == null) return null;
            String contentType = type.split(";", 2)[0].trim();
            boolean wav = "audio/wav".equalsIgnoreCase(contentType) || "audio/x-wav".equalsIgnoreCase(contentType);
            if (!wav && !"audio/mpeg".equalsIgnoreCase(contentType)) return null;
            long declared = connection.getContentLengthLong();
            if (declared == 0 || declared > MAX_BYTES) return null;
            temporary = File.createTempFile("capsule-", wav ? ".wav" : ".mp3", directory);
            int count = 0;
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16_384];
                while (!expired(deadlineNanos)) {
                    int length = input.read(buffer);
                    if (length < 0) break;
                    if (length > MAX_BYTES - count) return null;
                    output.write(buffer, 0, length);
                    count += length;
                }
            }
            if (expired(deadlineNanos) || count == 0 || (declared >= 0 && declared != count)) return null;
            try (RandomAccessFile file = new RandomAccessFile(temporary, "r")) {
                if (!(wav ? completeWav(file, count, deadlineNanos) : completeMp3(file, count, deadlineNanos))) return null;
            }
            keep = !expired(deadlineNanos);
            return keep ? new Clip(temporary, metadata) : null;
        } catch (IOException | RuntimeException skipped) {
            return null;
        } finally {
            try {
                if (connection != null) connection.disconnect();
            } finally {
                if (temporary != null && !keep) temporary.delete();
            }
        }
    }

    private static boolean expired(long deadlineNanos) {
        return Thread.currentThread().isInterrupted() || System.nanoTime() - deadlineNanos >= 0;
    }

    private static long littleUInt(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(Integer.reverseBytes(file.readInt()));
    }

    private static boolean completeWav(RandomAccessFile file, int count, long deadline) throws IOException {
        if (count < 46 || file.readInt() != WAV_RIFF) return false;
        long end = littleUInt(file) + 8;
        if (end < 44 || end > count || file.readInt() != WAV_WAVE) return false;
        int align = 0;
        long data = 0;
        for (long pointer = 12; pointer + 8 <= end && !expired(deadline);) {
            file.seek(pointer);
            int chunk = file.readInt();
            long size = littleUInt(file);
            pointer += 8;
            if (pointer + size > end) return false;
            if (chunk == WAV_FORMAT) {
                if (size < 16 || Short.toUnsignedInt(Short.reverseBytes(file.readShort())) != 1) return false;
                int channels = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                long rate = littleUInt(file), bytesPerSecond = littleUInt(file);
                align = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                int depth = Short.toUnsignedInt(Short.reverseBytes(file.readShort()));
                if (channels < 1 || channels > 2 || rate < 8000 || rate > 96000 || depth != 16 ||
                    align != channels * 2 || bytesPerSecond != rate * align) return false;
            }
            if (chunk == WAV_DATA) data = size;
            pointer += size + size % 2;
        }
        return data > 0 && align > 0 && data % align == 0;
    }

    private static boolean completeMp3(RandomAccessFile file, int count, long deadline) throws IOException {
        if (count < 8) return false;
        int offset = 0, frames = 0;
        if ((file.readInt() >>> 8) == 0x494433) { // ID3v2 may precede the frames.
            if (count < 10) return false;
            file.seek(5);
            int flags = file.readUnsignedByte(), size = 0;
            for (int i = 0; i < 4; i++) {
                int value = file.readUnsignedByte();
                if ((value & 128) != 0) return false;
                size = (size << 7) | value;
            }
            offset = 10 + size + (((flags & 16) != 0) ? 10 : 0);
        }
        while (offset + 4 <= count && !expired(deadline)) {
            file.seek(offset);
            int header = file.readInt();
            if (count - offset == 128 && (header >>> 8) == 0x544147) { offset = count; break; }
            int version = (header >>> 19) & 3, layer = (header >>> 17) & 3;
            int bitrate = (header >>> 12) & 15, rate = (header >>> 10) & 3;
            if ((header >>> 21) != 0x7ff || version == 1 || layer != 1 || bitrate == 0 || bitrate == 15 || rate == 3) return false;
            int sampleRate = MP3_SAMPLE_RATES[rate] / (version == 3 ? 1 : version == 2 ? 2 : 4);
            int bits = (version == 3 ? MP3_BITRATES_1[bitrate] : MP3_BITRATES_2[bitrate]) * 1000;
            int length = (version == 3 ? 144 : 72) * bits / sampleRate + ((header >>> 9) & 1);
            if (length > count - offset) return false;
            offset += length;
            frames++;
        }
        return frames >= 2 && offset == count;
    }
}
