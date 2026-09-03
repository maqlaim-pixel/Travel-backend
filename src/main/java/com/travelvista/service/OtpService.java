package com.travelvista.service;

import com.travelvista.model.OtpVerification;
import com.travelvista.repository.OtpVerificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private final OtpVerificationRepository otpRepo;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    private static final int OTP_LENGTH = 6;
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int MAX_OTPS_PER_HOUR = 10;

    public OtpService(OtpVerificationRepository otpRepo, JavaMailSender mailSender) {
        this.otpRepo = otpRepo;
        this.mailSender = mailSender;
    }

    /**
     * Generate and send an OTP for a specific purpose (edit/delete enquiry/lead).
     * Returns the OTP record or throws an exception.
     */
    public OtpVerification generateAndSendOtp(String email, String purpose, Long recordId, String recordType) {
        // Rate limiting: max 10 OTPs per hour per email
        long recentCount = otpRepo.countRecentByEmail(email, LocalDateTime.now().minusHours(1));
        if (recentCount >= MAX_OTPS_PER_HOUR) {
            throw new RuntimeException("Too many OTP requests. Please try again later.");
        }

        // Generate 6-digit OTP
        String code = generateCode();

        // Create OTP record (expires in 10 minutes)
        OtpVerification otp = new OtpVerification(
                email, code, purpose, recordId, recordType,
                LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES)
        );
        otpRepo.save(otp);

        // Send email
        sendOtpEmail(email, code, purpose);

        return otp;
    }

    /**
     * Verify an OTP code. Returns true if valid, false otherwise.
     */
    public boolean verifyOtp(String email, String purpose, Long recordId, String recordType, String code) {
        Optional<OtpVerification> optOtp = otpRepo.findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
                email, purpose, recordType, recordId);

        if (optOtp.isEmpty()) {
            return false;
        }

        OtpVerification otp = optOtp.get();

        // Check if already verified
        if (otp.getVerified()) {
            return true;
        }

        // Check max attempts
        if (otp.isMaxAttemptsExceeded()) {
            throw new RuntimeException("Too many failed attempts. Please request a new OTP.");
        }

        // Check expiry
        if (otp.isExpired()) {
            throw new RuntimeException("OTP has expired. Please request a new OTP.");
        }

        // Increment attempts
        otp.setAttempts(otp.getAttempts() + 1);

        // Check code
        if (otp.getCode().equals(code)) {
            otp.setVerified(true);
            otpRepo.save(otp);
            return true;
        }

        otpRepo.save(otp);
        return false;
    }

    /**
     * Check if a verified OTP exists for the given purpose.
     */
    public boolean hasVerifiedOtp(String email, String purpose, Long recordId, String recordType) {
        Optional<OtpVerification> optOtp = otpRepo.findTopByEmailAndPurposeAndRecordTypeAndRecordIdOrderByCreatedAtDesc(
                email, purpose, recordType, recordId);
        return optOtp.isPresent() && optOtp.get().getVerified() && !optOtp.get().isExpired();
    }

    /**
     * Invalidate all OTPs for a record after successful action.
     */
    public void invalidateOtps(String email, String purpose, Long recordId, String recordType) {
        var otps = otpRepo.findByEmailAndPurposeAndRecordTypeAndRecordIdAndVerifiedFalse(
                email, purpose, recordType, recordId);
        for (OtpVerification otp : otps) {
            otp.setVerified(true); // Mark as used
            otpRepo.save(otp);
        }
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private void sendOtpEmail(String to, String code, String purpose) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom != null && !mailFrom.isEmpty() ? mailFrom : "noreply@travelvista.com");
            message.setTo(to);
            message.setSubject("Your TravelVista Verification Code");

            String action = purpose.contains("edit") ? "edit" : "delete";
            String itemType = purpose.contains("enquiry") ? "enquiry" : "lead";

            message.setText(
                "Hello,\n\n" +
                "Your verification code for " + action + "ing your " + itemType + " is:\n\n" +
                "   " + code + "\n\n" +
                "This code will expire in 10 minutes.\n\n" +
                "If you did not request this code, please ignore this email.\n\n" +
                " Regards,\n" +
                "TravelVista Team"
            );

            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send OTP email to " + to + ": " + e.getMessage());
            // Don't throw — allow the OTP to be returned even if email fails
            // In production, you'd want to handle this better
        }
    }
}
