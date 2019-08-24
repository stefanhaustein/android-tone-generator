package org.kobjects.atg;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class ToneGenerator {
  public static final int SAMPLE_RATE = 44100;
  public static final int MIN_FREQUENCY = 1;
  public static final int MAX_FREQUENCY = SAMPLE_RATE / 3;

  public static final WaveFunction SAWTOOTH = f -> 2*f-1;
  public static final WaveFunction SINE = f -> (float) Math.sin(2 * Math.PI * f);

  private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder().build();

  private static final AudioFormat AUDIO_FORMAT = new AudioFormat.Builder()
      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
      .setSampleRate(SAMPLE_RATE)
      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
      .build();

  private static int ENVELOPE_STEPS = 100;

  private static long MS_TO_NS = 1000000;

  private static float clamp(float value, float min, float max) {
    return value < min ? min : value > max ? max : value;
  }

  private static float fade(float from, float to, float progress) {
    progress = clamp(progress, 0, 1);
    return from * (1 - progress) + to * progress;
  }

  private float attackTimeMs = 10;
  private float decayTimeMs = 300;
  private float releaseTimeMs = 150;

  private float sustain = 0.8f;
  private float volume = 1;

  private WaveFunction waveForm = SINE;


  public void setAttackTimeMs(float value) {
    attackTimeMs = value;
  }

  public void setDecayTimeMs(float value) {
    decayTimeMs = value;
  }

  public void setSustain(float value) {
    this.sustain = value;
  }

  public void setReleaseTimeMs(float value) {
    releaseTimeMs = value;
  }

  public void setWaveForm(WaveFunction fn) {
    this.waveForm = fn;
  }

  public synchronized Tone play(float frequency, int durationMs) {
    return new Tone(frequency).play(durationMs);
  }

  public synchronized Tone start(float frequency) {
    return new Tone(frequency).play(-1);
  }

  public class Tone {
    private final float attackTimeNs = Math.max(1, MS_TO_NS * attackTimeMs);
    private final float decayTimeNs = Math.max(1, MS_TO_NS * decayTimeMs);
    private final float sustain = ToneGenerator.this.sustain;
    private final float releaseTimeNs = Math.max(1, MS_TO_NS * releaseTimeMs);
    private final WaveFunction waveForm = ToneGenerator.this.waveForm;
    private final AudioTrack audioTrack;

    private long startTimeNs;
    private long endTimeNs;
    private boolean endRequested;

    Tone(float frequency) {
      int minBufferSize = AudioTrack.getMinBufferSize( 44100, AudioFormat.CHANNEL_CONFIGURATION_MONO, AudioFormat.ENCODING_PCM_16BIT);

      // Implicitly doubling
      int minSampleCount =  minBufferSize;

      if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
        throw new IllegalArgumentException("frequency out of range (1 ..." + MAX_FREQUENCY + ")");
      }
      int period = Math.round(SAMPLE_RATE / frequency);
      int count = (minSampleCount + period) / period;
      short[] waveBuffer = new short[period * count];

      audioTrack= new AudioTrack(AUDIO_ATTRIBUTES,
          AUDIO_FORMAT, 2 * period * count,
          AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);

      audioTrack.setVolume(0);

      for (int i = 0; i < period; i++) {
        waveBuffer[i] = (short) (Short.MAX_VALUE * Math.round(waveForm.apply(i / (period - 1f))));
      }
      for (int i = 0; i < count; i++) {
        System.arraycopy(waveBuffer, 0, waveBuffer, i * period, period);
      }
      audioTrack.write(waveBuffer, 0, count * period);
      audioTrack.setLoopPoints(0, period * count, -1);
    }

    private void waitNs(float ns) throws InterruptedException {
      long l = Math.max(1, (long) ns);
      wait(l / MS_TO_NS, (int) (l % MS_TO_NS));
    }

    synchronized Tone play(int durationMs) {
      if (startTimeNs != 0) {
        throw new IllegalStateException("Tone already playing.");
      }
      new Thread(() -> {
        synchronized (Tone.this) {
          startTimeNs = System.nanoTime();
          audioTrack.play();
          try {
            // Attack
            float currentVolume = 0;
            while (!endRequested) {
              waitNs(attackTimeNs / ENVELOPE_STEPS);
              long dt = System.nanoTime() - startTimeNs;
              currentVolume = fade(0, volume, dt / attackTimeNs);
              audioTrack.setVolume(currentVolume);
              if (dt > attackTimeNs) {
                break;
              }
            }

            // Decay
            long decayStartTimeNs = System.nanoTime();
            while (!endRequested) {
              waitNs(decayTimeNs / ENVELOPE_STEPS);
              long dt = System.nanoTime() - decayStartTimeNs;
              currentVolume = fade(volume, volume * sustain, dt / decayTimeNs);
              audioTrack.setVolume(currentVolume);
              if (dt > decayTimeNs) {
                break;
              }
            }

            if (currentVolume > 0) {
              
              // Sustain
              if (!endRequested) {
                if (durationMs == -1) {
                  Tone.this.wait();
                } else {
                  long remainingNs = durationMs * MS_TO_NS - (System.nanoTime() - startTimeNs);
                  if (remainingNs > 0) {
                    waitNs(remainingNs);
                  }
                }
              }

              // Release
              endTimeNs = System.nanoTime();
              while (true) {
                waitNs(releaseTimeNs / ENVELOPE_STEPS);
                long dt = System.nanoTime() - endTimeNs;
                audioTrack.setVolume(fade(currentVolume, 0, dt / releaseTimeNs));
                if (dt > releaseTimeNs) {
                  break;
                }
              }
            }

            Thread.sleep(500);
            audioTrack.stop();
            Thread.sleep(500);
          } catch (InterruptedException e) {
            endRequested = true;
          } finally {
            audioTrack.release();
          }
        }
      }).start();
      return this;
    }

    public synchronized void end() {
      endRequested = true;
      notify();
    }
  }

  public interface WaveFunction {
    /**
     * Input and output must be in the range from 0..1.
     */
    float apply(float in);
  }
}
