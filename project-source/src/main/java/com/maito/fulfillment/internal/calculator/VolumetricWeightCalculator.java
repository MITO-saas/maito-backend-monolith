package com.maito.fulfillment.internal.calculator;

/**
 * Packaging dimensions & billable weight calculator.
 * Standard IATA / 3PL formula: Volumetric Weight (kg) = (Length * Width * Height) / 5000 (in cm).
 */
public final class VolumetricWeightCalculator {

    public static final double VOLUMETRIC_DIVISOR = 5000.0;

    private VolumetricWeightCalculator() {}

    /**
     * Calculates volumetric weight in kg from dimensions in centimeters:
     * volumetric_weight = (length * width * height) / 5000
     */
    public static double calculateVolumetricWeightKg(double lengthCm, double widthCm, double heightCm) {
        return (lengthCm * widthCm * heightCm) / VOLUMETRIC_DIVISOR;
    }

    /**
     * Calculates volumetric weight in grams from dimensions in centimeters.
     */
    public static int calculateVolumetricWeightGrams(double lengthCm, double widthCm, double heightCm) {
        double volKg = calculateVolumetricWeightKg(lengthCm, widthCm, heightCm);
        return (int) Math.round(volKg * 1000.0);
    }

    /**
     * Calculates billable weight in grams: max(dead_weight, volumetric_weight).
     */
    public static int calculateBillableWeightGrams(int deadWeightGrams, double lengthCm, double widthCm, double heightCm) {
        int volumetricGrams = calculateVolumetricWeightGrams(lengthCm, widthCm, heightCm);
        return Math.max(deadWeightGrams, volumetricGrams);
    }
}
