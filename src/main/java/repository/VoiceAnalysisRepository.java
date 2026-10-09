package com.example.voiceguard.repository;

import com.example.voiceguard.entity.VoiceAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoiceAnalysisRepository extends JpaRepository<VoiceAnalysis, Long> {
}
