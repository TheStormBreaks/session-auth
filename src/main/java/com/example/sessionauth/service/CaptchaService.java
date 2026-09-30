package com.example.sessionauth.service;

import java.awt.Color;
import java.awt.Font; 
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;

import javax.imageio.ImageIO; 

import org.springframework.stereotype.Service;

@Service
public class CaptchaService {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz23456789"; 
    private final SecureRandom random = new SecureRandom();

    public String generateCaptchaText() { 
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 5; i++) { // Keep the code short enough to read.
            text.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        return text.toString(); 
    }

    public byte[] generateCaptchaImage(String text) throws IOException {
        int width = 120, height = 40; 
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Start with a plain background.
        g.setColor(new Color(240, 240, 240)); 
        g.fillRect(0, 0, width, height); 

        // A little noise makes the image less predictable.
        g.setColor(Color.LIGHT_GRAY); 
        for (int i = 0; i < 5; i++) {
            g.drawLine(random.nextInt(width), random.nextInt(height), random.nextInt(width), random.nextInt(height)); 
        }

        // Now put the code on top.
        g.setFont(new Font("Arial", Font.BOLD, 22)); 
        g.setColor(Color.DARK_GRAY); 
        for (int i = 0; i < text.length(); i++) { 
            g.drawString(String.valueOf(text.charAt(i)), 10 + i * 20, 28); 
        }

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos); 
        return baos.toByteArray();
    }
}