package com.example.voiceguard;

import ai.onnxruntime.*;

import javax.sound.sampled.*;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

public class OnnxTest {

    private static final String AUDIO_FILE =
            "C:\\Users\\HAZARASADAFF\\Downloads\\myvoicefirst.ogg";

    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_SAMPLES = 48000;

    public static void main(String[] args) {

        try {

            System.out.println("======================================");
            System.out.println("VoiceGuard AI Prediction Test");
            System.out.println("======================================");

            // --------------------------------------------------
            // 1. Load and decode audio
            // --------------------------------------------------

            System.out.println();
            System.out.println("Loading audio...");

            File audioFile = new File(AUDIO_FILE);

            if (!audioFile.exists()) {
                throw new RuntimeException(
                        "Audio file not found: " + AUDIO_FILE
                );
            }

            AudioInputStream originalStream =
                    AudioSystem.getAudioInputStream(audioFile);

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    TARGET_SAMPLE_RATE,
                    16,
                    1,
                    2,
                    TARGET_SAMPLE_RATE,
                    false
            );

            AudioInputStream pcmStream =
                    AudioSystem.getAudioInputStream(
                            targetFormat,
                            originalStream
                    );

            System.out.println("Audio decoded successfully!");
            System.out.println("Format: " + pcmStream.getFormat());

            // --------------------------------------------------
            // 2. Read PCM bytes
            // --------------------------------------------------

            ByteArrayOutputStream audioBuffer =
                    new ByteArrayOutputStream();

            byte[] buffer = new byte[4096];

            int bytesRead;

            while ((bytesRead = pcmStream.read(buffer)) != -1) {
                audioBuffer.write(buffer, 0, bytesRead);
            }

            byte[] pcmBytes = audioBuffer.toByteArray();

            originalStream.close();
            pcmStream.close();

            System.out.println(
                    "PCM bytes: " + pcmBytes.length
            );

            // --------------------------------------------------
            // 3. Convert PCM bytes to float samples
            // --------------------------------------------------

            int totalSamples = pcmBytes.length / 2;

            float[] samples = new float[totalSamples];

            for (int i = 0; i < totalSamples; i++) {

                int low = pcmBytes[i * 2] & 0xff;
                int high = pcmBytes[i * 2 + 1];

                short sample =
                        (short) ((high << 8) | low);

                samples[i] =
                        sample / 32768.0f;
            }

            System.out.println(
                    "Audio samples: " + samples.length
            );

            // --------------------------------------------------
            // 4. Create exactly 48,000 samples
            // --------------------------------------------------

            float[] modelInput =
                    new float[TARGET_SAMPLES];

            int samplesToCopy =
                    Math.min(
                            samples.length,
                            TARGET_SAMPLES
                    );

            System.arraycopy(
                    samples,
                    0,
                    modelInput,
                    0,
                    samplesToCopy
            );

            System.out.println(
                    "Model samples: " + modelInput.length
            );

            // --------------------------------------------------
            // 5. Normalize audio
            // --------------------------------------------------

            double mean = 0.0;

            for (float sample : modelInput) {
                mean += sample;
            }

            mean /= modelInput.length;

            double variance = 0.0;

            for (float sample : modelInput) {

                double difference =
                        sample - mean;

                variance +=
                        difference * difference;
            }

            variance /= modelInput.length;

            double standardDeviation =
                    Math.sqrt(variance + 1e-5);

            for (int i = 0; i < modelInput.length; i++) {

                modelInput[i] =
                        (float)
                                ((modelInput[i] - mean)
                                        / standardDeviation);
            }

            System.out.println("Audio normalization complete!");

            // --------------------------------------------------
            // 6. Load ONNX model
            // --------------------------------------------------

            System.out.println();
            System.out.println("Loading ONNX model...");

            OrtEnvironment environment =
                    OrtEnvironment.getEnvironment();

            InputStream modelStream =
                    OnnxTest.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "models/best_model.onnx"
                            );

            if (modelStream == null) {
                throw new RuntimeException(
                        "Model file not found!"
                );
            }

            Path tempModel =
                    Files.createTempFile(
                            "best_model",
                            ".onnx"
                    );

            Files.copy(
                    modelStream,
                    tempModel,
                    StandardCopyOption.REPLACE_EXISTING
            );

            modelStream.close();

            OrtSession.SessionOptions options =
                    new OrtSession.SessionOptions();

            OrtSession session =
                    environment.createSession(
                            tempModel.toString(),
                            options
                    );

            System.out.println(
                    "ONNX model loaded successfully!"
            );

            // --------------------------------------------------
            // 7. Create ONNX tensor
            // --------------------------------------------------

            float[][] inputData =
                    new float[][]{
                            modelInput
                    };

            OnnxTensor inputTensor =
                    OnnxTensor.createTensor(
                            environment,
                            inputData
                    );

            // --------------------------------------------------
            // 8. Run AI inference
            // --------------------------------------------------

            System.out.println();
            System.out.println("Running AI inference...");

            OrtSession.Result result =
                    session.run(
                            java.util.Collections.singletonMap(
                                    "input",
                                    inputTensor
                            )
                    );

            // --------------------------------------------------
            // 9. Read model output
            // --------------------------------------------------

            Object outputValue =
                    result.get(0).getValue();

            float[] logits;

            if (outputValue instanceof float[][]) {

                float[][] output =
                        (float[][]) outputValue;

                logits = output[0];

            } else {

                throw new RuntimeException(
                        "Unexpected model output type: "
                                + outputValue.getClass()
                );
            }

            System.out.println();
            System.out.println("Model logits:");
            System.out.println(
                    Arrays.toString(logits)
            );

            // --------------------------------------------------
            // 10. Softmax
            // --------------------------------------------------

            double maxLogit =
                    Math.max(logits[0], logits[1]);

            double exp0 =
                    Math.exp(logits[0] - maxLogit);

            double exp1 =
                    Math.exp(logits[1] - maxLogit);

            double sum =
                    exp0 + exp1;

            double genuineProbability =
                    exp0 / sum;

            double syntheticProbability =
                    exp1 / sum;

            // --------------------------------------------------
            // 11. Display result
            // --------------------------------------------------

            System.out.println();
            System.out.println("======================================");
            System.out.println("AI ANALYSIS RESULT");
            System.out.println("======================================");

            System.out.printf(
                    "Genuine probability: %.2f%%%n",
                    genuineProbability * 100
            );

            System.out.printf(
                    "Synthetic probability: %.2f%%%n",
                    syntheticProbability * 100
            );

            if (syntheticProbability >= 0.5) {

                System.out.println();
                System.out.println(
                        "RESULT: SUSPECTED SYNTHETIC / DEEPFAKE"
                );

            } else {

                System.out.println();
                System.out.println(
                        "RESULT: SUSPECTED GENUINE"
                );
            }

            System.out.println(
                    "======================================"
            );

            // --------------------------------------------------
            // 12. Cleanup
            // --------------------------------------------------

            inputTensor.close();
            result.close();
            session.close();
            options.close();
            environment.close();

        } catch (Exception e) {

            System.out.println();
            System.out.println("AI TEST FAILED.");
            e.printStackTrace();
        }
    }
}