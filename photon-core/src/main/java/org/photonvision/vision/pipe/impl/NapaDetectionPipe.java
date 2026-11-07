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
import java.util.List;
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
    private long m_handle = 0;
    private AprilTagFamily m_handleFamily = null;

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
                        params.sharpeningX100());
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
        }
        super.setParams(newParams);
    }

    private void destroyHandle() {
        if (m_handle != 0) {
            NapaJNI.destroy(m_handle);
            m_handle = 0;
            m_handleFamily = null;
        }
    }

    @Override
    public void release() {
        destroyHandle();
    }

    /**
     * NAPA detector parameters; see napa_params in napa_core.h.
     *
     * @param family Tag family
     * @param minTagPx Smallest black-square side to guarantee, in pixels (8-30000)
     * @param maxHamming Payload bits to correct (0-3, capped by the family)
     * @param minContrast Grey levels between tag black and the white quiet zone (1-255)
     * @param minBorderCells Dark border cells required out of 28 (0-28)
     * @param sharpeningX100 Cell sharpening strength x100 (0-100)
     */
    public static record NapaDetectionPipeParams(
            AprilTagFamily family,
            int minTagPx,
            int maxHamming,
            int minContrast,
            int minBorderCells,
            int sharpeningX100) {}
}
