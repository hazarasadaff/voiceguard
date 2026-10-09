package com.example.voiceguard.service;

import ai.onnxruntime.*;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.sound.sampled.*;
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

    // --------------------------------------------------
    // AI model initialization
    // --------------------------------------------------

    @PostConstruct
    public void initializeModel() {

        try {

            System.out.println();
            System.out.println("======================================");
            System.out.println("Loading VoiceGuard AI Model...");
            System.out.println("======================================");

            environment =
                    OrtEnvironment.getEnvironment();

            InputStream modelStream =
                    VoiceDetectionService.class
                            .getClassLoader()
                            .getResourceAsStream(
                                    "models/best_model.onnx"
                            );

            if (modelStream == null) {
                throw new RuntimeException(
                        "best_model.onnx was not found in resources/models"
                );
            }

            temporaryModelFile =
                    Files.createTempFile(
                            "voiceguard-model-",
                            ".onnx"
                    );

            Files.copy(
                    modelStream,
                    temporaryModelFile,
                    StandardCopyOption.REPLACE_EXISTING
            );

            modelStream.close();

            OrtSession.SessionOptions options =
                    new OrtSession.SessionOptions();

            session =
                    environment.createSession(
                            temporaryModelFile.toString(),
                            options
                    );

            options.close();

            System.out.println(
                    "VoiceGuard AI model loaded successfully!"
            );

            System.out.println(
                    "Input: input"
            );

            System.out.println(
                    "Output: output"
            );

            System.out.println(
                    "======================================"
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to load VoiceGuard AI model.",
                    e
            );
        }
    }

    // --------------------------------------------------
    // Main voice analysis method
    // --------------------------------------------------

    public DetectionResult analyze(MultipartFile file) {

        Path temporaryAudioFile = null;

        try {

            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException(
                        "Audio file is empty."
                );
            }

            // --------------------------------------------------
            // 1. Save uploaded audio temporarily
            // --------------------------------------------------

            String originalName =
                    file.getOriginalFilename();

            String suffix = ".audio";

            if (originalName != null &&
                    originalName.contains(".")) {

                suffix =
                        originalName.substring(
                                originalName.lastIndexOf(".")
                        );
            }

            temporaryAudioFile =
                    Files.createTempFile(
                            "voiceguard-audio-",
                            suffix
                    );

            file.transferTo(
                    temporaryAudioFile.toFile()
            );

            // --------------------------------------------------
            // 2. Decode audio
            // --------------------------------------------------

            AudioInputStream originalStream =
                    AudioSystem.getAudioInputStream(
                            temporaryAudioFile.toFile()
                    );

            AudioFormat targetFormat =
                    new AudioFormat(
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

            // --------------------------------------------------
            // 3. Read PCM audio
            // --------------------------------------------------

            ByteArrayOutputStream audioBuffer =
                    new ByteArrayOutputStream();

            byte[] buffer =
                    new byte[4096];

            int bytesRead;

            while ((bytesRead =
                    pcmStream.read(buffer)) != -1) {

                audioBuffer.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            byte[] pcmBytes =
                    audioBuffer.toByteArray();

            originalStream.close();
            pcmStream.close();

            // --------------------------------------------------
            // 4. Convert PCM bytes to float samples
            // --------------------------------------------------

            int totalSamples =
                    pcmBytes.length / 2;

            float[] samples =
                    new float[totalSamples];

            for (int i = 0; i < totalSamples; i++) {

                int low =
                        pcmBytes[i * 2] & 0xff;

                int high =
                        pcmBytes[i * 2 + 1];

                short sample =
                        (short) ((high << 8) | low);

                samples[i] =
                        sample / 32768.0f;
            }

            // --------------------------------------------------
            // 5. Create exactly 48,000 samples
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

            // --------------------------------------------------
            // 6. Normalize audio
            // --------------------------------------------------

            double mean = 0.0;

            for (float sample : modelInput) {
                mean += sample;
            }

            mean /=
                    modelInput.length;

            double variance = 0.0;

            for (float sample : modelInput) {

                double difference =
                        sample - mean;

                variance +=
                        difference * difference;
            }

            variance /=
                    modelInput.length;

            double standardDeviation =
                    Math.sqrt(
                            variance + 1e-5
                    );

            for (int i = 0;
                 i < modelInput.length;
                 i++) {

                modelInput[i] =
                        (float)
                                ((modelInput[i] - mean)
                                        / standardDeviation);
            }

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

            OrtSession.Result result =
                    session.run(
                            Collections.singletonMap(
                                    "input",
                                    inputTensor
                            )
                    );

            // --------------------------------------------------
            // 9. Read model output
            // --------------------------------------------------

            Object outputValue =
                    result.get(0).getValue();

            if (!(outputValue instanceof float[][])) {

                inputTensor.close();
                result.close();

                throw new RuntimeException(
                        "Unexpected ONNX output format."
                );
            }

            float[][] output =
                    (float[][]) outputValue;

            float[] logits =
                    output[0];

            // --------------------------------------------------
            // 10. Softmax
            // --------------------------------------------------

            double maxLogit =
                    Math.max(
                            logits[0],
                            logits[1]
                    );

            double exp0 =
                    Math.exp(
                            logits[0] - maxLogit
                    );

            double exp1 =
                    Math.exp(
                            logits[1] - maxLogit
                    );

            double sum =
                    exp0 + exp1;

            double genuineProbability =
                    exp0 / sum;

            double syntheticProbability =
                    exp1 / sum;

            // --------------------------------------------------
            // 11. Determine result
            // --------------------------------------------------

            String analysisResult;
            double confidence;
            String riskLevel;

            if (syntheticProbability >= 0.80) {

                analysisResult =
                        "SUSPECTED_SYNTHETIC";

                confidence =
                        syntheticProbability;

                riskLevel =
                        "HIGH";

            } else if (syntheticProbability >= 0.50) {

                analysisResult =
                        "SUSPECTED_SYNTHETIC";

                confidence =
                        syntheticProbability;

                riskLevel =
                        "MEDIUM";

            } else {

                analysisResult =
                        "SUSPECTED_GENUINE";

                confidence =
                        genuineProbability;

                riskLevel =
                        "LOW";
            }

            // --------------------------------------------------
            // 12. Cleanup inference objects
            // --------------------------------------------------

            inputTensor.close();
            result.close();

            // --------------------------------------------------
            // 13. Return AI result
            // --------------------------------------------------

            return new DetectionResult(
                    analysisResult,
                    confidence,
                    riskLevel
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Voice analysis failed: "
                            + e.getMessage(),
                    e
            );

        } finally {

            if (temporaryAudioFile != null) {

                try {

                    Files.deleteIfExists(
                            temporaryAudioFile
                    );

                } catch (Exception ignored) {
                }
            }
        }
    }

    // --------------------------------------------------
    // Cleanup when Spring Boot shuts down
    // --------------------------------------------------

    @PreDestroy
    public void cleanup() {

        try {

            if (session != null) {
                session.close();
            }

        } catch (Exception ignored) {
        }

        try {

            if (temporaryModelFile != null) {
                Files.deleteIfExists(
                        temporaryModelFile
                );
            }

        } catch (Exception ignored) {
        }
    }

    // --------------------------------------------------
    // AI result object
    // --------------------------------------------------

    public record DetectionResult(
            String result,
            double confidence,
            String riskLevel
    ) {
    }
}