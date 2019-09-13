package org.kobjects.atg;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * The generator is a lightweight object for configuring tones, similar to a builder.
 * While it's thread safe, it probably shouldn't be shared across threads unless the tone
 * configuration is identical.
 */
public class ToneGenerator {
  public static final int SAMPLE_RATE = 44100;
  public static final int MIN_FREQUENCY = 1;
  public static final int MAX_FREQUENCY = SAMPLE_RATE / 3;

  public static final WaveFunction SAWTOOTH = x -> 2*x-1;
  public static final WaveFunction SINE = x -> (float) Math.sin(2 * Math.PI * x);
  public static final WaveFunction NOISE = null;
  public static final WaveFunction TRIANGLE = x -> x < 0.5 ? 4*x - 2 : (3 - 4*x);
  public static final WaveFunction PULSE = x -> x < 0.5 ? -1 : 1;

  private static final AudioAttributes AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_GAME).build();

  private static final AudioFormat AUDIO_FORMAT = new AudioFormat.Builder()
      .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
      .setSampleRate(SAMPLE_RATE)
      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
      .build();

  private static final int ENVELOPE_STEPS = 100;

  private static final long MS_TO_NS = 1000000;

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
  private float delayTimeMs = 0;

  private float sustain = 0.8f;
  private float volume = 1;

  private static short[] noise;

  private WaveFunction waveForm = SINE;

  /**
   * Creating the sound can take a varying small amount of time. A fixed delay can make this
   * consistent; might be useful for music note playback. Default is 0.
   */
  public void setDelayTimeMs(float value) {
    this.delayTimeMs = value;
  }

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
    Tone tone = new Tone(frequency);
    tone.play(durationMs);
    return tone;
  }

  public synchronized Tone start(float frequency) {
    Tone tone = new Tone(frequency);
    tone.play(-1);
    return tone;
  }


  public class Tone {
    private final float frequency;
    private final float attackTimeNs = Math.max(1, MS_TO_NS * attackTimeMs);
    private final float decayTimeNs = Math.max(1, MS_TO_NS * decayTimeMs);
    private final float sustain = ToneGenerator.this.sustain;
    private final float releaseTimeNs = Math.max(1, MS_TO_NS * releaseTimeMs);

    private AudioTrack audioTrack;
    private boolean endRequested;
    private boolean started;

    Tone(float frequency) {
      this.frequency = frequency;
    }

    private void waitNs(float ns) throws InterruptedException {
      long l = Math.max(MS_TO_NS / 10, (long) ns);
      wait(l / MS_TO_NS, (int) (l % MS_TO_NS));
    }

    synchronized void play(int durationMs) {
      long preparationStartTimeNs = System.nanoTime();
      if (started) {
        throw new IllegalStateException("Tone already playing.");
      }
      started = true;
      new Thread(() -> {
        synchronized (Tone.this) {

          int minBufferSize = AudioTrack.getMinBufferSize( 44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

          // Implicitly doubling
          int minSampleCount =  minBufferSize;

          if (frequency < MIN_FREQUENCY || frequency > MAX_FREQUENCY) {
            throw new IllegalArgumentException("frequency out of range (1 ..." + MAX_FREQUENCY + ")");
          }

          WaveFunction waveForm = ToneGenerator.this.waveForm;
          int period;
          int count;
          short[] waveBuffer;

          if (waveForm == null) {
            count = 1;
            period = Math.max(SAMPLE_RATE, minSampleCount);
            waveBuffer = noise;
            if (waveBuffer == null) {
              waveBuffer = new short[period];
              for (int i = 0; i < period; i++) {
                waveBuffer[i] = (short) ((Math.random() * 2 - 1) * Short.MAX_VALUE);
              }
              noise = waveBuffer;
            }
          } else {
            period = Math.round(SAMPLE_RATE / frequency);
            count = (minSampleCount + period) / period;
            waveBuffer = new short[period * count];
            for (int i = 0; i < period; i++) {
              waveBuffer[i] = (short) (clamp(Short.MAX_VALUE * Math.round(waveForm.apply(i / (period - 1f))), Short.MIN_VALUE, Short.MAX_VALUE));
            }
            for (int i = 0; i < count; i++) {
              System.arraycopy(waveBuffer, 0, waveBuffer, i * period, period);
            }
          }
          audioTrack= new AudioTrack(AUDIO_ATTRIBUTES,
              AUDIO_FORMAT, 2 * period * count,
              AudioTrack.MODE_STATIC, AudioManager.AUDIO_SESSION_ID_GENERATE);

          audioTrack.setVolume(0);
          audioTrack.write(waveBuffer, 0, count * period);
          audioTrack.setLoopPoints(0, period * count, -1);

          long remainingDelayTimeNs = ((long) (delayTimeMs * MS_TO_NS)) - (System.nanoTime() - preparationStartTimeNs);

          System.out.println("RemainingDelayTimeMS: " + (remainingDelayTimeNs / MS_TO_NS));
          try {

            if (remainingDelayTimeNs > 0) {
              waitNs(remainingDelayTimeNs);
            }

            long startTimeNs = System.nanoTime();

            audioTrack.play();
            // Attack
            float currentVolume = 0;
            long dt;
            do {
              waitNs(attackTimeNs / ENVELOPE_STEPS);
              dt = System.nanoTime() - startTimeNs;
              currentVolume = fade(0, volume, dt / attackTimeNs);
              audioTrack.setVolume(currentVolume);
            } while (dt < attackTimeNs);

            // Decay
            long decayStartTimeNs = System.nanoTime();
            do {
              waitNs(decayTimeNs / ENVELOPE_STEPS);
              dt = System.nanoTime() - decayStartTimeNs;
              currentVolume = fade(volume, volume * sustain, dt / decayTimeNs);
              audioTrack.setVolume(currentVolume);
            } while (dt < decayTimeNs);

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
              long endTimeNs = System.nanoTime();
              do {
                waitNs(releaseTimeNs / ENVELOPE_STEPS);
                dt = System.nanoTime() - endTimeNs;
                audioTrack.setVolume(fade(currentVolume, 0, dt / releaseTimeNs));
              } while (dt < releaseTimeNs);
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
    }

    public synchronized void end() {
      endRequested = true;
      notify();
    }
  }

  public interface WaveFunction {
    /**
     * Input will range from 0..1; output is expected in the range from -1 to 1.
     */
    float apply(float in);
  }
}
