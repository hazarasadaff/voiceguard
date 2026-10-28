package com.example.voiceguard.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

@Service
public class VoiceDetectionService {

    private static final int TARGET_SAMPLE_RATE = 16000;
    private static final int TARGET_SAMPLES = 48000;

    private OrtEnvironment environment;
    private OrtSession session;
    private Path temporaryModelFile;

    @PostConstruct
    public void initializeModel() {
        try {
            environment = OrtEnvironment.getEnvironment();

            InputStream modelStream = getClass()
                    .getClassLoader()
                    .getResourceAsStream("models/best_model.onnx");

            if (modelStream == null) {
                throw new IllegalStateException(
                        "best_model.onnx was not found in resources/models"
                );
            }

            try (modelStream) {
                temporaryModelFile = Files.createTempFile(
                        "voiceguard-model-", ".onnx"
                );

                Files.copy(
                        modelStream,
                        temporaryModelFile,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            OrtSession.SessionOptions options =
                    new OrtSession.SessionOptions();

            try {
                session = environment.createSession(
                        temporaryModelFile.toString(),
                        options
                );
            } finally {
                options.close();
            }

            System.out.println("VoiceGuard AI model loaded successfully.");
            System.out.println("Model input: input");
            System.out.println("Model output: output");

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load VoiceGuard AI model.", e
            );
        }
    }

    public DetectionResult analyze(MultipartFile file) {
        Path temporaryAudioFile = null;

        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Audio file is empty.");
            }

            String originalName = file.getOriginalFilename();
            String suffix = ".audio";

            if (originalName != null && originalName.contains(".")) {
                suffix = originalName.substring(
                        originalName.lastIndexOf(".")
                );

                // Avoid excessively long temporary-file suffixes.
                if (suffix.length() > 20) {
                    suffix = ".audio";
                }
            }

            temporaryAudioFile = Files.createTempFile(
                    "voiceguard-audio-", suffix
            );

            file.transferTo(temporaryAudioFile.toFile());

            float[] samples;

            try (AudioInputStream originalStream =
                         AudioSystem.getAudioInputStream(
                                 temporaryAudioFile.toFile())) {

                AudioFormat targetFormat = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        TARGET_SAMPLE_RATE,
                        16,
                        1,
                        2,
                        TARGET_SAMPLE_RATE,
                        false
                );

                try (AudioInputStream pcmStream =
                             AudioSystem.getAudioInputStream(
                                     targetFormat, originalStream)) {

                    ByteArrayOutputStream audioBuffer =
                            new ByteArrayOutputStream();

                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = pcmStream.read(buffer)) != -1) {
                        audioBuffer.write(buffer, 0, bytesRead);
                    }

                    byte[] pcmBytes = audioBuffer.toByteArray();
                    int totalSamples = pcmBytes.length / 2;

                    if (totalSamples == 0) {
                        throw new IllegalArgumentException(
                                "The uploaded audio contains no samples."
                        );
                    }

                    samples = new float[totalSamples];

                    for (int i = 0; i < totalSamples; i++) {
                        int low = pcmBytes[i * 2] & 0xff;
                        int high = pcmBytes[i * 2 + 1];

                        short sample = (short) ((high << 8) | low);
                        samples[i] = sample / 32768.0f;
                    }
                }
            }

            // Use the first 3 seconds, padding shorter clips with zeros.
            float[] modelInput = new float[TARGET_SAMPLES];

            int samplesToCopy = Math.min(
                    samples.length, TARGET_SAMPLES
            );

            System.arraycopy(
                    samples, 0, modelInput, 0, samplesToCopy
            );

            // Normalize using the same approach as the original service.
            double mean = 0.0;

            for (float sample : modelInput) {
                mean += sample;
            }

            mean /= modelInput.length;

            double variance = 0.0;

            for (float sample : modelInput) {
                double difference = sample - mean;
                variance += difference * difference;
            }

            variance /= modelInput.length;

            double standardDeviation = Math.sqrt(variance + 1e-5);

            for (int i = 0; i < modelInput.length; i++) {
                modelInput[i] = (float) (
                        (modelInput[i] - mean) / standardDeviation
                );
            }

            float[][] inputData = new float[][]{modelInput};

            try (OnnxTensor inputTensor = OnnxTensor.createTensor(
                    environment, inputData)) {

                try (OrtSession.Result result = session.run(
                        Collections.singletonMap("input", inputTensor))) {

                    Object outputValue = result.get(0).getValue();

                    if (!(outputValue instanceof float[][] output)
                            || output.length == 0
                            || output[0].length < 2) {
                        throw new IllegalStateException(
                                "Unexpected ONNX output format. "
                                        + "Expected at least two output values."
                        );
                    }

                    float[] logits = output[0];

                    // Assumes index 0 = genuine and index 1 = synthetic.
                    // Confirm this ordering against your model's training code.
                    double maxLogit = Math.max(logits[0], logits[1]);

                    double exp0 = Math.exp(logits[0] - maxLogit);
                    double exp1 = Math.exp(logits[1] - maxLogit);
                    double sum = exp0 + exp1;

                    if (!Double.isFinite(sum) || sum == 0.0) {
                        throw new IllegalStateException(
                                "Model returned invalid prediction values."
                        );
                    }

                    double genuineProbability = exp0 / sum;
                    double syntheticProbability = exp1 / sum;

                    String analysisResult;
                    double confidence;
                    String riskLevel;

                    if (syntheticProbability >= 0.80) {
                        analysisResult = "SUSPECTED_SYNTHETIC";
                        confidence = syntheticProbability;
                        riskLevel = "HIGH";
                    } else if (syntheticProbability >= 0.50) {
                        analysisResult = "SUSPECTED_SYNTHETIC";
                        confidence = syntheticProbability;
                        riskLevel = "MEDIUM";
                    } else {
                        analysisResult = "SUSPECTED_GENUINE";
                        confidence = genuineProbability;
                        riskLevel = "LOW";
                    }

                    return new DetectionResult(
                            analysisResult,
                            confidence,
                            riskLevel
                    );
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(
                    "Voice analysis failed: " + e.getMessage(), e
            );
        } finally {
            if (temporaryAudioFile != null) {
                try {
                    Files.deleteIfExists(temporaryAudioFile);
                } catch (Exception e) {
                    System.err.println(
                            "Could not delete temporary audio file: "
                                    + e.getMessage()
                    );
                }
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        if (session != null) {
            try {
                session.close();
            } catch (Exception e) {
                System.err.println(
                        "Could not close ONNX session: " + e.getMessage()
                );
            }
        }

        if (temporaryModelFile != null) {
            try {
                Files.deleteIfExists(temporaryModelFile);
            } catch (Exception e) {
                System.err.println(
                        "Could not delete temporary model file: "
                                + e.getMessage()
                );
            }
        }
    }

    public record DetectionResult(
            String result,
            double confidence,
            String riskLevel
    ) {
    }
}
