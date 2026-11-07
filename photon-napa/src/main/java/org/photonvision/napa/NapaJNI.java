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
     * @return Detections, {@link #kDetectionStride} doubles each; null on invalid input
     */
    public static native double[] detect(
            long handle,
            long matAddr,
            int minTagPx,
            int maxHamming,
            int minContrast,
            int minBorderCells,
            int sharpeningX100);
}
