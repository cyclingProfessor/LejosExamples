// Mediates between a sensor sample provider and the user to provide normalised values between the
// Min and Max values
// seen during a calibration phase. Only works with sample providers returning a single value.
public class MinMaxFilter extends SampleProvider {
  private SampleProvider sp;
  private float range = 1.0f;
  private float offset = 0.0f;

  MaxMinColorFIlter(SampleProvider _sp) { // The actual sensor object will be used to get un-normalised values.
    sp = _sp;
  }

  public void calibrate() {  // This function is called once and then it keeps getting values until ENTER is pressed
    float samples = new float[1];
    float highValue = 0.0f;
    float LowValue = 1.0f
    while (!Button.ENTER.isPressed()) {
      sp.fetchSample(samples, 0);
      highValue = Math.max(highValue, samples[0]);
      lowValue = Math.min(lowValue, samples[0]);
    }
    offset = low_value;
    range = highValue - lowValue; // Before returning it stores the range and offset values for future use.
  }

  public int sampleSize() {
    return 1;
  }

  public void fetchSample(float[] sample, int index) {
    sp.fetchSample(sample, index); // delegate the fetchSample call to the actual sensor
    sample[index] = (sample[index] - offset) / range; // Adjust the result and put it into the
                                                      // returned array
    return;
  }
}
