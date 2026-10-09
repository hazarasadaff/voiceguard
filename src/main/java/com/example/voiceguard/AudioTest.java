package com.example.voiceguard;

import javax.sound.sampled.*;
import java.io.File;

public class AudioTest {

    public static void main(String[] args) {

        String filePath =
                "C:\\Users\\HAZARASADAFF\\Downloads\\myvoicefirst.ogg";

        File audioFile = new File(filePath);

        try {

            System.out.println("Checking audio file...");
            System.out.println("File: " + audioFile.getAbsolutePath());
            System.out.println("Exists: " + audioFile.exists());

            if (!audioFile.exists()) {
                throw new RuntimeException("Audio file not found!");
            }

            AudioInputStream originalStream =
                    AudioSystem.getAudioInputStream(audioFile);

            AudioFormat originalFormat =
                    originalStream.getFormat();

            System.out.println();
            System.out.println("OGG audio decoded successfully!");
            System.out.println("Original format: " + originalFormat);

            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    2,
                    16000,
                    false
            );

            AudioInputStream pcmStream =
                    AudioSystem.getAudioInputStream(
                            targetFormat,
                            originalStream
                    );

            System.out.println();
            System.out.println("Converted to model format!");
            System.out.println("Target format: " + pcmStream.getFormat());

            byte[] buffer = new byte[4096];

            long totalBytes = 0;
            int bytesRead;

            while ((bytesRead = pcmStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
            }

            System.out.println();
            System.out.println("PCM bytes read: " + totalBytes);

            originalStream.close();
            pcmStream.close();

            System.out.println();
            System.out.println("AUDIO TEST SUCCESSFUL!");

        } catch (Exception e) {

            System.out.println();
            System.out.println("AUDIO TEST FAILED.");
            e.printStackTrace();
        }
    }
}