package com.example.voiceguard.service;

import com.example.voiceguard.entity.VoiceAnalysis;
import com.example.voiceguard.repository.VoiceAnalysisRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VoiceAnalysisService {

    private final VoiceAnalysisRepository voiceAnalysisRepository;

    public VoiceAnalysisService(VoiceAnalysisRepository voiceAnalysisRepository) {
        this.voiceAnalysisRepository = voiceAnalysisRepository;
    }

    public VoiceAnalysis saveAnalysis(VoiceAnalysis voiceAnalysis) {
        return voiceAnalysisRepository.save(voiceAnalysis);
    }

    public List<VoiceAnalysis> getAllAnalyses() {
        return voiceAnalysisRepository.findAll();
    }

    public VoiceAnalysis getAnalysisById(Long id) {
        return voiceAnalysisRepository.findById(id).orElse(null);
    }
}