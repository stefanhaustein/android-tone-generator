package org.kobjects.atg.demo;

import org.kobjects.atg.ToneGenerator;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {

  static final String BLACK_KEYS = ".##_###.";
  static final float[] BLACK_FREQUENCIES = {277.18f, 311.13f, 369.99f, 415.3f, 466.16f};
  static final String WHITE_KEYS = "CDEFGAB";
  static final float[] WHITE_FREQUENCIES = {261.64f, 293.66f, 329.63f, 349.23f, 392, 440, 493.88f, 523.25f};

  static final ToneGenerator.WaveFunction[] WAVE_FORMS = {
      ToneGenerator.PULSE,
      ToneGenerator.SAWTOOTH,
      ToneGenerator.SINE,
      ToneGenerator.TRIANGLE,
      ToneGenerator.NOISE
  };

  static final String[] WAVE_FORM_NAMES = {"Pulse", "Sawtooth", "Sine", "Triangle", "Noise"};
  private Spinner waveFormSpinner;

  static float powerUp(int value) {
    value -= 2;
    float remainder = value % 18;
    switch (value / 18) {
      case 0:
        return 1 + remainder * 0.5f;
      case 1:
        return 10 + remainder * 5f;
      case 2:
        return 100 + remainder * 50f;
      default:
        return 1000 + remainder * 500f;
    }
  }



  ToneGenerator toneGenerator = new ToneGenerator();
  SeekBar pulseWidthSeekBar;
  SeekBar attackSeekBar;
  SeekBar decaySeekBar;
  SeekBar sustainSeekBar;
  SeekBar releaseSeekBar;


  void addKeys(LinearLayout owner, String labels, float[] frequencies, boolean paintItBlack) {
    LinearLayout keyLayout = new LinearLayout(this);
    int frequencyIndex = 0;
    for (int i = 0; i < labels.length(); i++) {
      String label = labels.substring(i, i + 1);
      TextView button;
      float weight;
      if (label.equals(".") || label.equals("_")) {
        button = new TextView(this);
        button.setText(" ");
        weight = label.equals(".") ? 0.5f : 1;
      } else {
        button = new Button(this);
        button.setText(label);
        button.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        if (paintItBlack) {
          button.getBackground().setColorFilter(0xFF444444, PorterDuff.Mode.MULTIPLY);
        }
        weight = 1;
        final float frequency = frequencies[frequencyIndex++];
        button.setOnTouchListener((view, motionEvent) -> {
          switch (motionEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
              if (button.getTag() == null) {
                ToneGenerator.WaveFunction waveForm = WAVE_FORMS[waveFormSpinner.getSelectedItemPosition()];
                if (waveForm == ToneGenerator.PULSE) {
                  final float width = pulseWidthSeekBar.getProgress() / 100f;
                  waveForm = x -> x > width ? -1 : 1;
                }
                toneGenerator.setWaveForm(waveForm);
                toneGenerator.setAttackTimeMs(powerUp(attackSeekBar.getProgress()));
                toneGenerator.setDecayTimeMs(powerUp(decaySeekBar.getProgress()));
                toneGenerator.setSustain(sustainSeekBar.getProgress() / 100f);
                toneGenerator.setReleaseTimeMs(powerUp(releaseSeekBar.getProgress()));
                button.setTag(toneGenerator.start(frequency));
              }
              return true;
            case MotionEvent.ACTION_UP:
              ToneGenerator.Tone tone = (ToneGenerator.Tone) button.getTag();
              button.setTag(null);
              if (tone != null) {
                tone.end();
              }
              return true;
          }
          return false;
        });
      }
      keyLayout.addView(button, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
      LinearLayout.LayoutParams buttonLayoutParams = (LinearLayout.LayoutParams) button.getLayoutParams();
      button.setLayoutParams(buttonLayoutParams);
    }
    owner.addView(keyLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 100, paintItBlack ? 0.5f : 1));
  }

  void addLabel(ViewGroup owner, String text) {
    TextView label = new TextView(this);
    label.setText(text);
    owner.addView(label);
  }

  SeekBar addSeekBar(ViewGroup owner, final String label, int initialValue, final String unit) {
    TextView labelView = new TextView(this);
    owner.addView(labelView);
    SeekBar seekBar = new SeekBar(this);
    seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
      @Override
      public void onProgressChanged(SeekBar seekBar, int value, boolean b) {
        double displayValue = "ms".equals(unit) ? powerUp(value) : value;
        labelView.setText(String.format("%s: %.1f%s", label, displayValue, unit));
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
    seekBar.setProgress(initialValue);
    owner.addView(seekBar);
    return seekBar;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    LinearLayout mainLayout = new LinearLayout(this);
    mainLayout.setOrientation(LinearLayout.VERTICAL);

    addLabel(mainLayout, "Waveform");
    waveFormSpinner = new Spinner(this);
    ArrayAdapter<String> waveFormAdapter  =new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, WAVE_FORM_NAMES);
    waveFormSpinner.setAdapter(waveFormAdapter);
    mainLayout.addView(waveFormSpinner);

    pulseWidthSeekBar = addSeekBar(mainLayout, "Pulse width", 50, "%");
    attackSeekBar = addSeekBar(mainLayout, "Attack", 20, "ms");
    decaySeekBar = addSeekBar(mainLayout, "Decay", 22, "ms");
    sustainSeekBar = addSeekBar(mainLayout, "Sustain", 80, "%");
    releaseSeekBar = addSeekBar(mainLayout, "Release", 28, "ms");

    addKeys(mainLayout, BLACK_KEYS, BLACK_FREQUENCIES, true);
    addKeys(mainLayout, WHITE_KEYS, WHITE_FREQUENCIES, false);

    setContentView(mainLayout);
  }

  interface IntConsumer {
    void consume(int value);
  }
}
