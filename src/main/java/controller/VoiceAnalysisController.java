package com.example.voiceguard.controller;

import com.example.voiceguard.entity.VoiceAnalysis;
import com.example.voiceguard.service.VoiceAnalysisService;
import com.example.voiceguard.service.VoiceDetectionService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/voice-analysis")
public class VoiceAnalysisController {

    private final VoiceAnalysisService voiceAnalysisService;
    private final VoiceDetectionService voiceDetectionService;

    public VoiceAnalysisController(
            VoiceAnalysisService voiceAnalysisService,
            VoiceDetectionService voiceDetectionService) {

        this.voiceAnalysisService =
                voiceAnalysisService;

        this.voiceDetectionService =
                voiceDetectionService;
    }

    // --------------------------------------------------
    // Manual JSON analysis record
    // --------------------------------------------------

    @PostMapping
    public ResponseEntity<VoiceAnalysis> createAnalysis(
            @RequestBody VoiceAnalysis voiceAnalysis) {

        VoiceAnalysis savedAnalysis =
                voiceAnalysisService.saveAnalysis(
                        voiceAnalysis
                );

        return ResponseEntity.ok(
                savedAnalysis
        );
    }

    // --------------------------------------------------
    // REAL AI VOICE ANALYSIS
    // --------------------------------------------------

    @PostMapping("/upload")
    public ResponseEntity<VoiceAnalysis> uploadVoice(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {

            return ResponseEntity.badRequest()
                    .build();
        }

        // Run the uploaded audio through
        // the real ONNX AI detector.
        VoiceDetectionService.DetectionResult detectionResult =
                voiceDetectionService.analyze(file);

        // Create database record.
        VoiceAnalysis analysis =
                new VoiceAnalysis(
                        file.getOriginalFilename(),
                        detectionResult.result(),
                        detectionResult.confidence(),
                        detectionResult.riskLevel()
                );

        // Save AI result to MySQL.
        VoiceAnalysis savedAnalysis =
                voiceAnalysisService.saveAnalysis(
                        analysis
                );

        return ResponseEntity.ok(
                savedAnalysis
        );
    }

    // --------------------------------------------------
    // Get all analyses
    // --------------------------------------------------

    @GetMapping
    public ResponseEntity<List<VoiceAnalysis>> getAllAnalyses() {

        return ResponseEntity.ok(
                voiceAnalysisService.getAllAnalyses()
        );
    }

    // --------------------------------------------------
    // Get analysis by ID
    // --------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<VoiceAnalysis> getAnalysisById(
            @PathVariable Long id) {

        VoiceAnalysis analysis =
                voiceAnalysisService.getAnalysisById(
                        id
                );

        if (analysis == null) {

            return ResponseEntity.notFound()
                    .build();
        }

        return ResponseEntity.ok(
                analysis
        );
    }
}