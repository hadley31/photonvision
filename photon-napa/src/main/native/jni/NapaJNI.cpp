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

#include <jni.h>

#include <array>
#include <vector>

#include <opencv2/core/mat.hpp>
#include <opencv2/imgproc.hpp>

#include "napa_core.h"

namespace {

// Must match NapaJNI.kDetectionStride and the offsets there.
constexpr int kStride = 22;
// Packet.MAX_ARRAY_LEN; PhotonVision can't publish more targets than this anyway.
constexpr int kMaxDetections = 127;

struct NapaHandle {
  napa_detector* detector;
  std::vector<napa_detection> buffer;
};

// The AprilTag tag frame square, in the order WPILib/AprilTag report detection
// corners: det->p[i] = H * kTagSquare[i]. In the image these are bottom-left,
// bottom-right, top-right, top-left.
const std::array<cv::Point2f, 4> kTagSquare{
    cv::Point2f{-1.f, 1.f}, cv::Point2f{1.f, 1.f}, cv::Point2f{1.f, -1.f},
    cv::Point2f{-1.f, -1.f}};

// NAPA reports corners in OpenCV's AprilTag order, which is upstream AprilTag's
// p[1], p[0], p[3], p[2] (see NAPA docs/detection.md). WPILib corner i is NAPA
// corner kNapaIndex[i].
constexpr std::array<int, 4> kNapaIndex{1, 0, 3, 2};

// NAPA puts (0, 0) at the centre of the top-left pixel; AprilTag (and so
// WPILib and PhotonVision's existing detector) puts it at that pixel's top-left
// corner. Shift to match so switching detectors doesn't move corners.
constexpr double kPixelOffset = 0.5;

void ThrowIllegalArgument(JNIEnv* env, const char* msg) {
  jclass cls = env->FindClass("java/lang/IllegalArgumentException");
  if (cls != nullptr) {
    env->ThrowNew(cls, msg);
  }
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_org_photonvision_napa_NapaJNI_create(
    JNIEnv* env, jclass, jstring family) {
  if (family == nullptr) {
    return 0;
  }
  const char* name = env->GetStringUTFChars(family, nullptr);
  if (name == nullptr) {
    return 0;
  }
  napa_detector* det = napa_create_family(name);
  env->ReleaseStringUTFChars(family, name);
  if (det == nullptr) {
    return 0;
  }
  auto* handle = new NapaHandle{det, std::vector<napa_detection>(kMaxDetections)};
  return reinterpret_cast<jlong>(handle);
}

JNIEXPORT void JNICALL Java_org_photonvision_napa_NapaJNI_destroy(JNIEnv*, jclass,
                                                                  jlong handlePtr) {
  auto* handle = reinterpret_cast<NapaHandle*>(handlePtr);
  if (handle == nullptr) {
    return;
  }
  napa_destroy(handle->detector);
  delete handle;
}

JNIEXPORT jdoubleArray JNICALL Java_org_photonvision_napa_NapaJNI_detect(
    JNIEnv* env, jclass, jlong handlePtr, jlong matAddr, jint minTagPx,
    jint maxHamming, jint minContrast, jint minBorderCells,
    jint sharpeningX100) {
  auto* handle = reinterpret_cast<NapaHandle*>(handlePtr);
  auto* mat = reinterpret_cast<cv::Mat*>(matAddr);
  if (handle == nullptr || mat == nullptr) {
    ThrowIllegalArgument(env, "NAPA: null detector handle or Mat");
    return nullptr;
  }
  if (mat->empty()) {
    return env->NewDoubleArray(0);
  }
  if (mat->type() != CV_8UC1) {
    ThrowIllegalArgument(env, "NAPA: image must be CV_8UC1 (greyscale)");
    return nullptr;
  }

  napa_params params{minTagPx, maxHamming, minContrast, minBorderCells,
                     sharpeningX100};

  int n = napa_detect(handle->detector, mat->data, mat->cols, mat->rows,
                      static_cast<int>(mat->step[0]), &params,
                      handle->buffer.data(),
                      static_cast<int>(handle->buffer.size()));
  if (n == -1) {
    ThrowIllegalArgument(env, "NAPA: invalid image or detector parameters");
    return nullptr;
  }
  if (n < 0) {
    jclass cls = env->FindClass("java/lang/OutOfMemoryError");
    if (cls != nullptr) {
      env->ThrowNew(cls, "NAPA: allocation failed");
    }
    return nullptr;
  }

  std::vector<double> out(static_cast<size_t>(n) * kStride);
  std::array<cv::Point2f, 4> corners;
  for (int i = 0; i < n; i++) {
    const napa_detection& d = handle->buffer[i];
    double* o = out.data() + static_cast<size_t>(i) * kStride;

    for (int c = 0; c < 4; c++) {
      int src = kNapaIndex[c];
      double x = d.corners[src * 2] + kPixelOffset;
      double y = d.corners[src * 2 + 1] + kPixelOffset;
      corners[c] = cv::Point2f{static_cast<float>(x), static_cast<float>(y)};
      o[5 + c * 2] = x;
      o[5 + c * 2 + 1] = y;
    }

    // Tag frame -> image homography, normalized so H[2][2] == 1 like AprilTag's
    cv::Mat H = cv::getPerspectiveTransform(kTagSquare.data(), corners.data());
    const double* h = H.ptr<double>();
    double scale = h[8] != 0.0 ? 1.0 / h[8] : 1.0;
    for (int k = 0; k < 9; k++) {
      o[13 + k] = h[k] * scale;
    }

    // Tag center is the projection of the tag frame origin: (H02, H12) / H22
    o[0] = d.id;
    o[1] = d.hamming;
    o[2] = d.decision_margin;
    o[3] = o[13 + 2];
    o[4] = o[13 + 5];
  }

  jdoubleArray result = env->NewDoubleArray(static_cast<jsize>(out.size()));
  if (result == nullptr) {
    return nullptr;
  }
  env->SetDoubleArrayRegion(result, 0, static_cast<jsize>(out.size()),
                            out.data());
  return result;
}

}  // extern "C"
