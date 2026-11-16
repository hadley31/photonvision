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

package org.photonvision.napa;

/**
 * JNI bindings for NAPA, a sparse-sampling AprilTag detector by Zachary Wilson
 * (https://github.com/zwatx/NAPA), licensed GPL-3.0-only.
 *
 * <p>The native library ("napaJNI") must be loaded before calling any of these methods.
 */
public final class NapaJNI {
    private NapaJNI() {}

    /**
     * Number of doubles per detection in the array returned by {@link #detect}: id, hamming,
     * decisionMargin, centerX, centerY, 8 corner coordinates, 9 homography entries.
     */
    public static final int kDetectionStride = 22;

    public static final int kIdOffset = 0;
    public static final int kHammingOffset = 1;
    public static final int kDecisionMarginOffset = 2;
    public static final int kCenterOffset = 3;
    public static final int kCornersOffset = 5;
    public static final int kHomographyOffset = 13;

    /** Hamming value of a tag NAPA recognised as a whole picture (soft decoding), not bit by bit. */
    public static final int kSoftDecoded = -1;

    /** {@link #detect} option: also soft-decode tiny or partly flat tags (tag36h11 only). */
    public static final int kOptionSoftDecode = 1;

    /** {@link #detect} option: re-align blurred tags' corners after decoding (tag36h11 only). */
    public static final int kOptionAlignCorners = 2;

    /** {@link #setLens} model: no lens; sides are fitted in pixels. */
    public static final int kLensNone = 0;

    /** {@link #setLens} model: OpenCV's standard (Brown-Conrady) distortion model. */
    public static final int kLensBrownConrady = 1;

    /** {@link #setLens} model: OpenCV's fisheye model. */
    public static final int kLensFisheye = 2;

    /**
     * Create a detector for one tag family.
     *
     * @param family Family name, e.g. "tag36h11" or "tag16h5"
     * @return An opaque handle, or 0 if the family is unknown or allocation failed
     */
    public static native long create(String family);

    /**
     * Destroy a detector created with {@link #create}.
     *
     * @param handle The detector handle
     */
    public static native void destroy(long handle);

    /**
     * Detect tags in an 8-bit single-channel OpenCV Mat. A handle is not thread-safe.
     *
     * <p>Corners are returned in WPILib AprilTag order (bottom-left, bottom-right, top-right,
     * top-left in the image), and each detection includes a row-major homography mapping the tag
     * frame square [-1, 1]^2 to image pixels, matching {@code AprilTagDetection}.
     *
     * @param handle The detector handle
     * @param matAddr The native address of a CV_8UC1 cv::Mat (Mat.getNativeObjAddr())
     * @param minTagPx Smallest black-square side to guarantee, in pixels (8-30000)
     * @param maxHamming Payload bits to correct (0-3, capped by the family)
     * @param minContrast Grey levels between tag black and the white quiet zone (1-255)
     * @param minBorderCells Dark border cells required out of 28 (0-28)
     * @param sharpeningX100 Cell sharpening strength x100 (0-100)
     * @param splitTouching 0 off; 1 also decode dark shapes touching others only at a corner (tag
     *     grids, checkerboards); 2 only on frames where a decoded tag touches others at its corners
     * @param recoverShadows Also run NAPA's shadow recovery pass (exact-code matches only)
     * @param shadowMinTagPx Shadow recovery's minimum tag size: 0 for full sensitivity, otherwise
     *     minTagPx through 30000
     * @param options Any of {@link #kOptionSoftDecode} and {@link #kOptionAlignCorners}; non-zero
     *     options require a tag36h11 detector
     * @return Detections, {@link #kDetectionStride} doubles each
     */
    public static native double[] detect(
            long handle,
            long matAddr,
            int minTagPx,
            int maxHamming,
            int minContrast,
            int minBorderCells,
            int sharpeningX100,
            int splitTouching,
            boolean recoverShadows,
            int shadowMinTagPx,
            int options);

    /**
     * Set detector-wide options that apply to every later {@link #detect} call.
     *
     * @param handle The detector handle
     * @param threads Threads one detection may use, 1-16. Results are identical at every count
     * @param tagShape 0 for no preference, 1 for tall tags (turned about the vertical axis), 2 for
     *     wide tags (camera on its side)
     * @param regrowSmallTags Retry small candidates whose thin border came apart at full resolution
     * @return 0 on success, -1 for an invalid value, -2 if threads couldn't be started
     */
    public static native int configure(
            long handle, int threads, int tagShape, boolean regrowSmallTags);

    /**
     * Set the camera's tone curve as a gamma, 1 (linear, no change) to 4. tag36h11 detectors only.
     *
     * @param handle The detector handle
     * @param gamma The camera gamma
     * @return 0 on success, -1 for an invalid value or a family other than tag36h11
     */
    public static native int setCameraGamma(long handle, double gamma);

    /**
     * Set the camera's lens from its calibration, in OpenCV's conventions. Sides are then fitted as
     * straight lines through the lens; corners are still returned in (distorted) pixels.
     *
     * @param handle The detector handle
     * @param model {@link #kLensNone}, {@link #kLensBrownConrady} or {@link #kLensFisheye}
     * @param intrinsics fx, fy, cx, cy, skew (ignored for kLensNone, may be null)
     * @param coefficients OpenCV distortion coefficients: 4, 5, 8, 12 or 14 for Brown-Conrady, 4 for
     *     fisheye (ignored for kLensNone, may be null)
     * @return 0 on success, -1 for invalid input
     */
    public static native int setLens(
            long handle, int model, double[] intrinsics, double[] coefficients);
}
