package com.example.quickbridge.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

    private final CodeGenerator generator = new CodeGenerator();

    @Test
    void generatesValidFiveCharacterCodes() {
        for (int i = 0; i < 1000; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(5);
            assertThat(CodeGenerator.isValid(code)).isTrue();
            // No ambiguous characters allowed.
            assertThat(code).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void generateUniqueSkipsTakenCodes() {
        Set<String> taken = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String code = generator.generateUnique(taken::contains);
            assertThat(taken).doesNotContain(code);
            taken.add(code);
        }
        assertThat(taken).hasSize(200);
    }

    @Test
    void isValidRejectsBadCodes() {
        assertThat(CodeGenerator.isValid(null)).isFalse();
        assertThat(CodeGenerator.isValid("")).isFalse();
        assertThat(CodeGenerator.isValid("ABCD")).isFalse();   // too short
        assertThat(CodeGenerator.isValid("ABCDEF")).isFalse(); // too long
        assertThat(CodeGenerator.isValid("ABCD0")).isFalse();  // contains 0
        assertThat(CodeGenerator.isValid("ABCDI")).isFalse();  // contains I
        assertThat(CodeGenerator.isValid("abcde")).isFalse();  // lowercase
    }

    @Test
    void isValidAcceptsGeneratedCodes() {
        assertThat(CodeGenerator.isValid("AB7KQ")).isTrue();
    }
}
