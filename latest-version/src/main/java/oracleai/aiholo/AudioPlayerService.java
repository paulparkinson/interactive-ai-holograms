package oracleai.aiholo;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

@Service
public class AudioPlayerService {
    private Thread playThread;
    private final AtomicBoolean playing = new AtomicBoolean(false);
    private volatile SourceDataLine currentLine;

    public synchronized void play(String wavPath, Optional<String> mixerNameOpt) throws Exception {
        stop(); // stop any previous playback

        File wavFile = new File(wavPath);
        if (!wavFile.exists()) {
            throw new IllegalArgumentException("WAV file does not exist: " + wavPath);
        }

        playThread = new Thread(() -> {
            try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavFile)) {
                AudioFormat format = audioStream.getFormat();

                // Convert to PCM if needed (Java Sound sometimes needs this)
                AudioFormat decodedFormat = format;
                if (!isPcm(format)) {
                    decodedFormat = new AudioFormat(
                            AudioFormat.Encoding.PCM_SIGNED,
                            format.getSampleRate(),
                            16,
                            format.getChannels(),
                            format.getChannels() * 2,
                            format.getSampleRate(),
                            false
                    );
                }
                AudioInputStream playStream = isPcm(format) ? audioStream : AudioSystem.getAudioInputStream(decodedFormat, audioStream);

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);

                Mixer mixer = mixerNameOpt.map(this::findMixerByName).orElse(null);
                try {
                    currentLine = (SourceDataLine) (mixer == null ? AudioSystem.getLine(info) : mixer.getLine(info));
                    currentLine.open(decodedFormat);
                    currentLine.start();
                    playing.set(true);

                    byte[] buffer = new byte[8192];
                    int read;
                    while (playing.get() && (read = playStream.read(buffer, 0, buffer.length)) != -1) {
                        currentLine.write(buffer, 0, read);
                    }
                    currentLine.drain();
                } finally {
                    if (currentLine != null) {
                        currentLine.stop();
                        currentLine.close();
                    }
                    playing.set(false);
                }
            } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                e.printStackTrace();
            }
        }, "wav-playback-thread");

        playThread.setDaemon(true);
        playThread.start();
    }

    public synchronized void stop() {
        playing.set(false);
        if (currentLine != null) {
            try { currentLine.stop(); } catch (Exception ignored) {}
            try { currentLine.close(); } catch (Exception ignored) {}
        }
        if (playThread != null && playThread.isAlive()) {
            try { playThread.join(200); } catch (InterruptedException ignored) {}
        }
        playThread = null;
    }

    public boolean isPlaying() {
        return playing.get();
    }

    public String[] listOutputMixers() {
        return java.util.Arrays.stream(AudioSystem.getMixerInfo())
                .map(Mixer.Info::getName)
                .toArray(String[]::new);
    }

    private Mixer findMixerByName(String name) {
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().toLowerCase().contains(name.toLowerCase())) {
                return AudioSystem.getMixer(info);
            }
        }
        return null;
    }

    private boolean isPcm(AudioFormat fmt) {
        return AudioFormat.Encoding.PCM_SIGNED.equals(fmt.getEncoding())
            || AudioFormat.Encoding.PCM_UNSIGNED.equals(fmt.getEncoding());
    }
}

