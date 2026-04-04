package com.filmbe.service;

public interface EmailService {
    void sendRegistrationVerification(String email, String verificationToken);
}
