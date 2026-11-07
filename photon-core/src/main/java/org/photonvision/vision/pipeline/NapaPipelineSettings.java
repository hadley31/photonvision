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

package org.photonvision.vision.pipeline;

import org.photonvision.vision.apriltag.AprilTagFamily;
import org.photonvision.vision.target.TargetModel;

/** Settings for {@link NapaPipeline}, an AprilTag pipeline using the NAPA detector. */
public class NapaPipelineSettings extends AdvancedPipelineSettings {
    public AprilTagFamily tagFamily = AprilTagFamily.kTag36h11;

    // NAPA detector parameters (see napa_params in napa_core.h). Defaults match NAPA's own.
    /** Smallest tag (black square side) NAPA is guaranteed to find, in pixels. 8-30000. */
    public int minTagPx = 32;

    /** Minimum grey levels between tag black and the white quiet zone. 1-255. */
    public int minContrast = 20;

    /** Dark border cells required, out of tag36h11's 28 (scaled for other families). 0-28. */
    public int minBorderCells = 22;

    /** Cell sharpening strength, in percent. 0-100. */
    public int sharpening = 25;

    // Filtering, same meaning (and scale) as the AprilTag pipeline's
    public int hammingDist = 0;
    public int decisionMargin = 35;

    // 3d settings
    public int numIterations = 40;
    public boolean doMultiTarget = false;
    public boolean doSingleTargetAlways = false;

    public NapaPipelineSettings() {
        super();
        pipelineType = PipelineType.Napa;
        targetModel = TargetModel.kAprilTag6p5in_36h11;
        cameraExposureRaw = 20;
        cameraAutoExposure = false;
        ledMode = false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((tagFamily == null) ? 0 : tagFamily.hashCode());
        result = prime * result + minTagPx;
        result = prime * result + minContrast;
        result = prime * result + minBorderCells;
        result = prime * result + sharpening;
        result = prime * result + hammingDist;
        result = prime * result + decisionMargin;
        result = prime * result + numIterations;
        result = prime * result + (doMultiTarget ? 1231 : 1237);
        result = prime * result + (doSingleTargetAlways ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        NapaPipelineSettings other = (NapaPipelineSettings) obj;
        return tagFamily == other.tagFamily
                && minTagPx == other.minTagPx
                && minContrast == other.minContrast
                && minBorderCells == other.minBorderCells
                && sharpening == other.sharpening
                && hammingDist == other.hammingDist
                && decisionMargin == other.decisionMargin
                && numIterations == other.numIterations
                && doMultiTarget == other.doMultiTarget
                && doSingleTargetAlways == other.doSingleTargetAlways;
    }
}
