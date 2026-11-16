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

    // NAPA's optional features, all off by default so a new pipeline detects exactly as plain NAPA
    /** Threads one detection may use, 1-16. Results are identical at every count. */
    public int threads = 1;

    /**
     * Also decode tags touching others at a corner: 0 off, 1 always, 2 when tags are seen touching.
     */
    public int splitTouching = 0;

    /** Run NAPA's shadow recovery pass, which accepts only exact-code matches. */
    public boolean recoverShadows = false;

    /** Shadow recovery's minimum tag size in pixels; 0 for full sensitivity. */
    public int shadowMinTagPx = 0;

    /** Recognise tiny (about 8-19 px) or partly flat tags as a whole picture. tag36h11 only. */
    public boolean softDecode = false;

    /** Re-align blurred tags' corners after decoding. tag36h11 only. */
    public boolean alignCorners = false;

    /**
     * Fit tag sides through the calibrated lens instead of in pixels. Needs an OpenCV calibration.
     */
    public boolean useLensModel = false;

    /** The camera's tone curve as a gamma, 1 (linear) to 4. tag36h11 only. */
    public double cameraGamma = 1.0;

    /** Sampling hint: 0 tags look square, 1 tall (turned about vertical), 2 wide (camera on side). */
    public int tagShape = 0;

    /** Retry small candidates whose thin border came apart, at full resolution. */
    public boolean regrowSmallTags = false;

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
        result = prime * result + threads;
        result = prime * result + splitTouching;
        result = prime * result + (recoverShadows ? 1231 : 1237);
        result = prime * result + shadowMinTagPx;
        result = prime * result + (softDecode ? 1231 : 1237);
        result = prime * result + (alignCorners ? 1231 : 1237);
        result = prime * result + (useLensModel ? 1231 : 1237);
        result = prime * result + Double.hashCode(cameraGamma);
        result = prime * result + tagShape;
        result = prime * result + (regrowSmallTags ? 1231 : 1237);
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
                && threads == other.threads
                && splitTouching == other.splitTouching
                && recoverShadows == other.recoverShadows
                && shadowMinTagPx == other.shadowMinTagPx
                && softDecode == other.softDecode
                && alignCorners == other.alignCorners
                && useLensModel == other.useLensModel
                && Double.compare(cameraGamma, other.cameraGamma) == 0
                && tagShape == other.tagShape
                && regrowSmallTags == other.regrowSmallTags
                && hammingDist == other.hammingDist
                && decisionMargin == other.decisionMargin
                && numIterations == other.numIterations
                && doMultiTarget == other.doMultiTarget
                && doSingleTargetAlways == other.doSingleTargetAlways;
    }
}
