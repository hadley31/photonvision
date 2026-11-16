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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.photonvision.common.logging.LogGroup;
import org.photonvision.common.logging.Logger;
import org.photonvision.napa.NapaJNI;
import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.opencv.CVMat;
import org.photonvision.vision.pipe.CVPipe;
import org.wpilib.vision.apriltag.AprilTagDetection;

/**
 * Detects AprilTags with NAPA (https://github.com/zwatx/NAPA, by Zachary Wilson), producing the
 * same {@link AprilTagDetection}s as {@link AprilTagDetectionPipe} so the rest of the AprilTag
 * pipeline is unchanged. Requires {@code LoadJNI.JNITypes.NAPA} to be loaded.
 */
public class NapaDetectionPipe
        extends CVPipe<CVMat, List<AprilTagDetection>, NapaDetectionPipe.NapaDetectionPipeParams> {
    private static final Logger logger = new Logger(NapaDetectionPipe.class, LogGroup.VisionModule);

    private long m_handle = 0;
    private AprilTagFamily m_handleFamily = null;

    // Detector-wide settings last applied to m_handle; null when they need (re)applying
    private DetectorConfig m_appliedConfig = null;
    private Double m_appliedGamma = null;
    private NapaLens m_appliedLens = null;

    private boolean m_warnedTag36h11Only = false;

    @Override
    protected List<AprilTagDetection> process(CVMat in) {
        if (in.getMat().empty()) {
            return List.of();
        }
        if (m_handle == 0) {
            throw new RuntimeException("NAPA detector was released or failed to initialize!");
        }

        double[] raw =
                NapaJNI.detect(
                        m_handle,
                        in.getMat().getNativeObjAddr(),
                        params.minTagPx(),
                        params.maxHamming(),
                        params.minContrast(),
                        params.minBorderCells(),
                        params.sharpeningX100(),
                        params.splitTouching(),
                        params.recoverShadows(),
                        shadowMinTagPx(params),
                        detectOptions(params));
        if (raw == null || raw.length == 0) {
            return List.of();
        }

        String family = params.family().getNativeName();
        int n = raw.length / NapaJNI.kDetectionStride;
        var ret = new ArrayList<AprilTagDetection>(n);
        for (int i = 0; i < n; i++) {
            int base = i * NapaJNI.kDetectionStride;
            var homography = new double[9];
            System.arraycopy(raw, base + NapaJNI.kHomographyOffset, homography, 0, 9);
            var corners = new double[8];
            System.arraycopy(raw, base + NapaJNI.kCornersOffset, corners, 0, 8);
            ret.add(
                    new AprilTagDetection(
                            family,
                            (int) raw[base + NapaJNI.kIdOffset],
                            (int) raw[base + NapaJNI.kHammingOffset],
                            (float) raw[base + NapaJNI.kDecisionMarginOffset],
                            homography,
                            raw[base + NapaJNI.kCenterOffset],
                            raw[base + NapaJNI.kCenterOffset + 1],
                            corners));
        }
        return ret;
    }

    /** NAPA wants 0 (full sensitivity) or a size no smaller than the ordinary pass's minimum. */
    private static int shadowMinTagPx(NapaDetectionPipeParams p) {
        if (p.shadowMinTagPx() <= 0) return 0;
        return Math.min(Math.max(p.shadowMinTagPx(), p.minTagPx()), 30000);
    }

    /** Soft decoding and corner alignment are tag36h11-only in NAPA; drop them for other families. */
    private int detectOptions(NapaDetectionPipeParams p) {
        int options = 0;
        if (p.softDecode()) options |= NapaJNI.kOptionSoftDecode;
        if (p.alignCorners()) options |= NapaJNI.kOptionAlignCorners;
        if (options != 0 && p.family() != AprilTagFamily.kTag36h11) {
            if (!m_warnedTag36h11Only) {
                logger.warn("NAPA soft decoding and corner alignment only support tag36h11; ignoring them");
                m_warnedTag36h11Only = true;
            }
            return 0;
        }
        return options;
    }

    @Override
    public void setParams(NapaDetectionPipeParams newParams) {
        if (m_handle == 0 || m_handleFamily != newParams.family()) {
            destroyHandle();
            m_handle = NapaJNI.create(newParams.family().getNativeName());
            if (m_handle == 0) {
                throw new RuntimeException(
                        "NAPA doesn't support tag family " + newParams.family().getNativeName());
            }
            m_handleFamily = newParams.family();
            m_warnedTag36h11Only = false;
        }

        var config =
                new DetectorConfig(newParams.threads(), newParams.tagShape(), newParams.regrowSmallTags());
        if (!config.equals(m_appliedConfig)) {
            int ret =
                    NapaJNI.configure(m_handle, config.threads, config.tagShape, config.regrowSmallTags);
            if (ret != 0) {
                logger.error("NAPA rejected detector settings " + config + " (error " + ret + ")");
            }
            m_appliedConfig = config;
        }

        // Camera gamma is tag36h11-only; a fresh detector is linear, so other families need nothing
        double gamma = newParams.family() == AprilTagFamily.kTag36h11 ? newParams.cameraGamma() : 1.0;
        if (m_appliedGamma == null || m_appliedGamma != gamma) {
            if (newParams.family() == AprilTagFamily.kTag36h11) {
                int ret = NapaJNI.setCameraGamma(m_handle, gamma);
                if (ret != 0) logger.error("NAPA rejected camera gamma " + gamma);
            } else if (newParams.cameraGamma() != 1.0) {
                logger.warn("NAPA camera gamma only supports tag36h11; ignoring it");
            }
            m_appliedGamma = gamma;
        }

        var lens = newParams.lens() != null ? newParams.lens() : NapaLens.NONE;
        if (!lens.equals(m_appliedLens)) {
            int ret = NapaJNI.setLens(m_handle, lens.model(), lens.intrinsics(), lens.coefficients());
            if (ret != 0) {
                logger.error("NAPA rejected the camera's lens model; fitting in pixels instead");
                NapaJNI.setLens(m_handle, NapaJNI.kLensNone, null, null);
            }
            m_appliedLens = lens;
        }

        super.setParams(newParams);
    }

    private void destroyHandle() {
        if (m_handle != 0) {
            NapaJNI.destroy(m_handle);
            m_handle = 0;
            m_handleFamily = null;
        }
        m_appliedConfig = null;
        m_appliedGamma = null;
        m_appliedLens = null;
    }

    @Override
    public void release() {
        destroyHandle();
    }

    private static record DetectorConfig(int threads, int tagShape, boolean regrowSmallTags) {}

    /**
     * A camera lens for NAPA's lens-aware side fitting, in OpenCV's conventions.
     *
     * @param model {@link NapaJNI#kLensNone}, {@link NapaJNI#kLensBrownConrady} or {@link
     *     NapaJNI#kLensFisheye}
     * @param intrinsics fx, fy, cx, cy, skew
     * @param coefficients OpenCV distortion coefficients
     */
    public static record NapaLens(int model, double[] intrinsics, double[] coefficients) {
        public static final NapaLens NONE = new NapaLens(NapaJNI.kLensNone, null, null);

        @Override
        public boolean equals(Object o) {
            return o instanceof NapaLens other
                    && model == other.model
                    && Arrays.equals(intrinsics, other.intrinsics)
                    && Arrays.equals(coefficients, other.coefficients);
        }

        @Override
        public int hashCode() {
            return Objects.hash(model, Arrays.hashCode(intrinsics), Arrays.hashCode(coefficients));
        }

        @Override
        public String toString() {
            return "NapaLens[model="
                    + model
                    + ", intrinsics="
                    + Arrays.toString(intrinsics)
                    + ", coefficients="
                    + Arrays.toString(coefficients)
                    + "]";
        }
    }

    /**
     * NAPA detector parameters; see napa_core.h.
     *
     * @param family Tag family
     * @param minTagPx Smallest black-square side to guarantee, in pixels (8-30000)
     * @param maxHamming Payload bits to correct (0-3, capped by the family)
     * @param minContrast Grey levels between tag black and the white quiet zone (1-255)
     * @param minBorderCells Dark border cells required out of 28 (0-28)
     * @param sharpeningX100 Cell sharpening strength x100 (0-100)
     * @param splitTouching 0 off, 1 always, 2 only on frames where tags touch at their corners
     * @param recoverShadows Run NAPA's shadow recovery pass
     * @param shadowMinTagPx Shadow recovery's minimum tag size; 0 for full sensitivity
     * @param softDecode Soft-decode tiny / partly flat tags (tag36h11 only)
     * @param alignCorners Re-align blurred tags' corners after decoding (tag36h11 only)
     * @param threads Threads per detection, 1-16
     * @param tagShape 0 any, 1 tall, 2 wide
     * @param regrowSmallTags Retry small candidates at full resolution
     * @param cameraGamma Camera tone-curve gamma, 1-4 (tag36h11 only)
     * @param lens Camera lens for lens-aware fitting, or null for none
     */
    public static record NapaDetectionPipeParams(
            AprilTagFamily family,
            int minTagPx,
            int maxHamming,
            int minContrast,
            int minBorderCells,
            int sharpeningX100,
            int splitTouching,
            boolean recoverShadows,
            int shadowMinTagPx,
            boolean softDecode,
            boolean alignCorners,
            int threads,
            int tagShape,
            boolean regrowSmallTags,
            double cameraGamma,
            NapaLens lens) {
        /** Basic detection with all of NAPA's optional features off. */
        public NapaDetectionPipeParams(
                AprilTagFamily family,
                int minTagPx,
                int maxHamming,
                int minContrast,
                int minBorderCells,
                int sharpeningX100) {
            this(
                    family,
                    minTagPx,
                    maxHamming,
                    minContrast,
                    minBorderCells,
                    sharpeningX100,
                    0,
                    false,
                    0,
                    false,
                    false,
                    1,
                    0,
                    false,
                    1.0,
                    null);
        }
    }
}
