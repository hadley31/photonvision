/*
 * Copyright (C) Photon Vision.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.photonvision.vision.pipe.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.photonvision.common.LoadJNI;
import org.photonvision.common.util.TestUtils;
import org.photonvision.napa.NapaJNI;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.calibration.CameraCalibrationCoefficients;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.pipe.impl.AprilTagDetectionPipe.AprilTagDetectionPipeParams;
import org.photonvision.vision.pipe.impl.NapaDetectionPipe.NapaDetectionPipeParams;
import org.photonvision.vision.pipe.impl.NapaDetectionPipe.NapaLens;
import org.wpilib.vision.apriltag.AprilTagDetection;
import org.wpilib.vision.apriltag.AprilTagDetector;

/** Checks that NAPA's detections line up with WPILib's: same IDs, corner order, homography. */
public class NapaDetectionPipeTest {
    @BeforeAll
    public static void setup() throws IOException {
        LoadJNI.loadLibraries();
        LoadJNI.forceLoad(LoadJNI.JNITypes.NAPA);
    }

    private static List<AprilTagDetection> detectWpilib(Mat gray, AprilTagFamily family) {
        var pipe = new AprilTagDetectionPipe();
        try {
            var config = new AprilTagDetector.Config();
            config.refineEdges = true;
            var quad = new AprilTagDetector.QuadThresholdParameters();
            quad.minClusterPixels = 5;
            quad.maxNumMaxima = 10;
            quad.criticalAngle = 45 * Math.PI / 180.0;
            quad.maxLineFitMSE = 10.0f;
            quad.minWhiteBlackDiff = 5;
            quad.deglitch = false;
            pipe.setParams(new AprilTagDetectionPipeParams(family, config, quad));
            return pipe.run(new CVMat(gray)).output;
        } finally {
            pipe.release();
        }
    }

    private static List<AprilTagDetection> detectNapa(Mat gray, AprilTagFamily family) {
        var pipe = new NapaDetectionPipe();
        try {
            pipe.setParams(new NapaDetectionPipeParams(family, 8, 0, 20, 22, 25));
            return pipe.run(new CVMat(gray)).output;
        } finally {
            pipe.release();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "kTag1_640_480, kTag36h11",
        "kTag_corner_1280, kTag16h5",
        "k36h11_stress_test, kTag36h11",
        // Not kTag1_16h5_1280: NAPA (as of 0.5.1) doesn't detect the tag on that backlit phone
        // screen at any setting, though WPILib does
    })
    public void matchesWpilibDetector(TestUtils.ApriltagTestImages image, AprilTagFamily family) {
        var path = TestUtils.getApriltagImagePath(image, false);
        Mat gray = Imgcodecs.imread(path.toString(), Imgcodecs.IMREAD_GRAYSCALE);
        assertFalse(gray.empty(), "couldn't load " + path);

        // Only compare well-decoded tags; 16h5 has lots of low-margin false positives in WPILib
        List<AprilTagDetection> wpilib =
                detectWpilib(gray, family).stream()
                        .filter(d -> d.getHamming() == 0 && d.getDecisionMargin() > 35)
                        .toList();
        List<AprilTagDetection> napa = detectNapa(gray, family);
        System.out.println(
                image + ": WPILib found " + wpilib.size() + " tags, NAPA found " + napa.size());
        assertFalse(wpilib.isEmpty());

        // NAPA stops at 127 detections (PhotonVision can't publish more), so it must find every tag
        // WPILib does up to that limit
        assertEquals(Math.min(wpilib.size(), 127), napa.size(), "tag count in " + image);

        // IDs can repeat (the stress test image), so pair each NAPA tag with the nearest WPILib tag
        // that has the same ID
        for (var n : napa) {
            var w =
                    wpilib.stream()
                            .filter(d -> d.getId() == n.getId())
                            .min(
                                    Comparator.comparingDouble(
                                            d ->
                                                    Math.hypot(
                                                            d.getCenterX() - n.getCenterX(), d.getCenterY() - n.getCenterY())))
                            .orElse(null);
            assertNotNull(w, "NAPA found tag " + n.getId() + " that WPILib didn't in " + image);

            for (int i = 0; i < 4; i++) {
                assertEquals(w.getCornerX(i), n.getCornerX(i), 1.5, "corner " + i + " x");
                assertEquals(w.getCornerY(i), n.getCornerY(i), 1.5, "corner " + i + " y");
            }
            assertEquals(w.getCenterX(), n.getCenterX(), 1.5);
            assertEquals(w.getCenterY(), n.getCenterY(), 1.5);

            // Homographies are only defined up to scale; compare after normalizing H[2][2] to 1 by
            // checking that they project the tag frame the same way
            var hw = w.getHomography();
            var hn = n.getHomography();
            for (double[] p : new double[][] {{0, 0}, {-1, 1}, {1, 1}, {1, -1}, {-1, -1}, {0.5, -0.25}}) {
                double[] pw = project(hw, p[0], p[1]);
                double[] pn = project(hn, p[0], p[1]);
                assertEquals(pw[0], pn[0], 1.5, "homography x at " + p[0] + "," + p[1]);
                assertEquals(pw[1], pn[1], 1.5, "homography y at " + p[0] + "," + p[1]);
            }
        }
    }

    private static List<AprilTagDetection> detectNapa(Mat gray, NapaDetectionPipeParams params) {
        var pipe = new NapaDetectionPipe();
        try {
            pipe.setParams(params);
            return pipe.run(new CVMat(gray)).output;
        } finally {
            pipe.release();
        }
    }

    /** NAPA's lens form of a test calibration, as NapaPipeline builds it. */
    private static NapaLens lensOf(CameraCalibrationCoefficients cal) {
        double[] k = cal.getIntrinsicsArr();
        return new NapaLens(
                NapaJNI.kLensBrownConrady,
                new double[] {k[0], k[4], k[2], k[5], k[1]},
                cal.getDistCoeffsArr().clone());
    }

    private static NapaDetectionPipeParams withFeatures(
            AprilTagFamily family,
            int splitTouching,
            boolean recoverShadows,
            boolean softDecode,
            boolean alignCorners,
            int threads,
            boolean regrow,
            NapaLens lens) {
        return new NapaDetectionPipeParams(
                family,
                8,
                0,
                20,
                22,
                25,
                splitTouching,
                recoverShadows,
                0,
                softDecode,
                alignCorners,
                threads,
                0,
                regrow,
                1.0,
                lens);
    }

    private static Mat load(TestUtils.ApriltagTestImages image) {
        var path = TestUtils.getApriltagImagePath(image, false);
        Mat gray = Imgcodecs.imread(path.toString(), Imgcodecs.IMREAD_GRAYSCALE);
        assertFalse(gray.empty(), "couldn't load " + path);
        return gray;
    }

    @Test
    public void threadsDontChangeResults() {
        Mat gray = load(TestUtils.ApriltagTestImages.k36h11_stress_test);
        var one =
                detectNapa(
                        gray, withFeatures(AprilTagFamily.kTag36h11, 0, false, false, false, 1, false, null));
        var four =
                detectNapa(
                        gray, withFeatures(AprilTagFamily.kTag36h11, 0, false, false, false, 4, false, null));
        assertEquals(one.size(), four.size());
        for (int i = 0; i < one.size(); i++) {
            assertEquals(one.get(i).getId(), four.get(i).getId());
            assertArrayEquals(one.get(i).getCorners(), four.get(i).getCorners());
        }
    }

    @ParameterizedTest
    @CsvSource({
        // image, family, calibration, split, shadows, soft, align, regrow, lens
        "kTag1_640_480, kTag36h11, lifecam, 0, true, false, false, false, false",
        "kTag1_640_480, kTag36h11, lifecam, 0, false, true, false, false, false",
        "kTag1_640_480, kTag36h11, lifecam, 0, false, false, true, false, false",
        "kTag1_640_480, kTag36h11, lifecam, 2, false, false, false, true, false",
        "kTag1_640_480, kTag36h11, lifecam, 1, false, false, false, false, true",
        "kTag1_640_480, kTag36h11, lifecam, 1, true, true, true, true, true",
        "kTag_corner_1280, kTag16h5, limelight, 0, false, false, false, false, true",
        "kTag_corner_1280, kTag16h5, limelight, 2, true, false, false, true, true",
        // 36h11-only options on a 16h5 detector are dropped, not an error
        "kTag_corner_1280, kTag16h5, limelight, 0, false, true, true, false, false",
    })
    public void optionalFeaturesKeepDetections(
            TestUtils.ApriltagTestImages image,
            AprilTagFamily family,
            String calibration,
            int split,
            boolean shadows,
            boolean soft,
            boolean align,
            boolean regrow,
            boolean useLens) {
        Mat gray = load(image);
        var cal =
                calibration.equals("lifecam")
                        ? TestUtils.get2020LifeCamCoeffs(false)
                        : TestUtils.getCoeffs(TestUtils.LIMELIGHT_480P_CAL_FILE, false);

        var baseline = detectNapa(gray, family);
        var withOptions =
                detectNapa(
                        gray,
                        withFeatures(
                                family, split, shadows, soft, align, 1, regrow, useLens ? lensOf(cal) : null));
        assertFalse(baseline.isEmpty());

        // Every tag plain NAPA finds is still found, in the same place; the options may add more
        for (var b : baseline) {
            var n =
                    withOptions.stream()
                            .filter(d -> d.getId() == b.getId())
                            .min(
                                    Comparator.comparingDouble(
                                            d ->
                                                    Math.hypot(
                                                            d.getCenterX() - b.getCenterX(), d.getCenterY() - b.getCenterY())))
                            .orElse(null);
            assertNotNull(n, "lost tag " + b.getId() + " with options on in " + image);
            for (int i = 0; i < 4; i++) {
                assertEquals(b.getCornerX(i), n.getCornerX(i), 1.5, "corner " + i + " x");
                assertEquals(b.getCornerY(i), n.getCornerY(i), 1.5, "corner " + i + " y");
            }
        }
    }

    private static double[] project(double[] h, double x, double y) {
        double z = h[6] * x + h[7] * y + h[8];
        return new double[] {(h[0] * x + h[1] * y + h[2]) / z, (h[3] * x + h[4] * y + h[5]) / z};
    }
}
