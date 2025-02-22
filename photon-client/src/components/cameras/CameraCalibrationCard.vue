<script setup lang="ts">
import { computed, ref } from "vue";
import { useCameraSettingsStore } from "@/stores/settings/CameraSettingsStore";
import { CalibrationBoardTypes, type Resolution, type VideoFormat } from "@/types/SettingTypes";
import JsPDF from "jspdf";
import { font as PromptRegular } from "@/assets/fonts/PromptRegular";
import MonoLogo from "@/assets/images/logoMono.png";
import PvSlider from "@/components/common/pv-slider.vue";
import { useStateStore } from "@/stores/StateStore";
import PvSwitch from "@/components/common/pv-switch.vue";
import PvSelect from "@/components/common/pv-select.vue";
import PvNumberInput from "@/components/common/pv-number-input.vue";
import { WebsocketPipelineType } from "@/types/WebsocketDataTypes";

const settingsValid = ref(true);

const getCalibrationCoeffs = (resolution: Resolution) => {
  return useCameraSettingsStore().currentCameraSettings.completeCalibrations.find(
    (cal) => cal.resolution.width === resolution.width && cal.resolution.height === resolution.height
  );
};
const getUniqueVideoResolutions = (): VideoFormat[] => {
  const uniqueResolutions: VideoFormat[] = [];
  useCameraSettingsStore().currentCameraSettings.validVideoFormats.forEach((format, index) => {
    if (
      !uniqueResolutions.some(
        (v) => v.resolution.width === format.resolution.width && v.resolution.height === format.resolution.height
      )
    ) {
      format.index = index;

      const calib = getCalibrationCoeffs(format.resolution);
      if (calib !== undefined) {
        format.standardDeviation = calib.standardDeviation;
        format.mean = calib.perViewErrors.reduce((a, b) => a + b) / calib.perViewErrors.length;
        format.horizontalFOV = 2 * Math.atan2(format.resolution.width / 2, calib.intrinsics[0]) * (180 / Math.PI);
        format.verticalFOV = 2 * Math.atan2(format.resolution.height / 2, calib.intrinsics[4]) * (180 / Math.PI);
        format.diagonalFOV =
          2 *
          Math.atan2(
            Math.sqrt(
              format.resolution.width ** 2 +
                (format.resolution.height / (calib.intrinsics[4] / calib.intrinsics[0])) ** 2
            ) / 2,
            calib.intrinsics[0]
          ) *
          (180 / Math.PI);
      }
      uniqueResolutions.push(format);
    }
  });
  uniqueResolutions.sort(
    (a, b) => b.resolution.width + b.resolution.height - (a.resolution.width + a.resolution.height)
  );
  return uniqueResolutions;
};
const getUniqueVideoResolutionStrings = () =>
  getUniqueVideoResolutions().map<{ name: string; value: number }>((f) => ({
    name: `${f.resolution.width} X ${f.resolution.height}`,
    // Index won't ever be undefined
    value: f.index || 0
  }));
const calibrationDivisors = computed(() =>
  [1, 2, 4].filter((v) => {
    const currentRes = useCameraSettingsStore().currentVideoFormat.resolution;
    return (currentRes.width / v >= 300 && currentRes.height / v >= 220) || v === 1;
  })
);

const squareSizeIn = ref(1);
const patternWidth = ref(8);
const patternHeight = ref(8);
const boardType = ref<CalibrationBoardTypes>(CalibrationBoardTypes.Chessboard);

const downloadCalibBoard = () => {
  const doc = new JsPDF({ unit: "in", format: "letter" });

  doc.addFileToVFS("Prompt-Regular.tff", PromptRegular);
  doc.addFont("Prompt-Regular.tff", "Prompt-Regular", "normal");
  doc.setFont("Prompt-Regular");
  doc.setFontSize(12);

  const paperWidth = 8.5;
  const paperHeight = 11.0;

  switch (boardType.value) {
    case CalibrationBoardTypes.Chessboard:
      // eslint-disable-next-line no-case-declarations
      const chessboardStartX = (paperWidth - patternWidth.value * squareSizeIn.value) / 2;
      // eslint-disable-next-line no-case-declarations
      const chessboardStartY = (paperHeight - patternWidth.value * squareSizeIn.value) / 2;

      for (let squareY = 0; squareY < patternHeight.value; squareY++) {
        for (let squareX = 0; squareX < patternWidth.value; squareX++) {
          const xPos = chessboardStartX + squareX * squareSizeIn.value;
          const yPos = chessboardStartY + squareY * squareSizeIn.value;

          // Only draw the odd squares to create the chessboard pattern
          if ((xPos + yPos + 0.25) % 2 === 0) {
            doc.rect(xPos, yPos, squareSizeIn.value, squareSizeIn.value, "F");
          }
        }
      }
      break;
    case CalibrationBoardTypes.DotBoard:
      // eslint-disable-next-line no-case-declarations
      const dotgridStartX =
        (paperWidth - (2 * (patternWidth.value - 1) + ((patternHeight.value - 1) % 2)) * squareSizeIn.value) / 2.0;
      // eslint-disable-next-line no-case-declarations
      const dotgridStartY = (paperHeight - (patternHeight.value - squareSizeIn.value)) / 2;

      for (let squareY = 0; squareY < patternHeight.value; squareY++) {
        for (let squareX = 0; squareX < patternWidth.value; squareX++) {
          const xPos = dotgridStartX + (2 * squareX + (squareY % 2)) * squareSizeIn.value;
          const yPos = dotgridStartY + squareY * squareSizeIn.value;

          doc.circle(xPos, yPos, squareSizeIn.value / 4, "F");
        }
      }
      break;
  }

  // Draw ruler pattern
  const lineStartX = 1.0;
  const lineEndX = paperWidth - lineStartX;
  const lineY = paperHeight - 1.0;

  doc.setLineWidth(0.01);
  doc.line(lineStartX, lineY, lineEndX, lineY);

  for (let tickX = lineStartX; tickX <= lineEndX; tickX++) {
    doc.line(tickX, lineY, tickX, lineY + 0.25);
    doc.text(`${tickX - 1}${tickX - 1 === 0 ? " in" : ""}`, tickX + 0.1, lineY + 0.25);
  }

  // Add branding
  const logoImage = new Image();
  logoImage.src = MonoLogo;
  doc.addImage(logoImage, "PNG", 1.0, 0.75, 1.4, 0.5);

  doc.text(`${patternWidth.value} x ${patternHeight.value} | ${squareSizeIn.value}in`, paperWidth - 1, 1.0, {
    maxWidth: (paperWidth - 2.0) / 2,
    align: "right"
  });

  doc.save(`calibrationTarget-${CalibrationBoardTypes[boardType.value]}.pdf`);
};

const importCalibrationFromCalibDB = ref();
const openCalibUploadPrompt = () => {
  importCalibrationFromCalibDB.value.click();
};
const readImportedCalibration = (payload: Event) => {
  if (payload.target == null || !payload.target?.files) return;
  const files: FileList = payload.target.files as FileList;

  files[0].text().then((text) => {
    useCameraSettingsStore()
      .importCalibDB({ payload: text, filename: files[0].name })
      .then((response) => {
        useStateStore().showSnackbarMessage({
          message: response.data.text || response.data,
          color: response.status === 200 ? "success" : "error"
        });
      })
      .catch((err) => {
        if (err.request) {
          useStateStore().showSnackbarMessage({
            message: "Error while uploading calibration file! The backend didn't respond to the upload attempt.",
            color: "error"
          });
        } else {
          useStateStore().showSnackbarMessage({
            message: "Error while uploading calibration file!",
            color: "error"
          });
        }
      });
  });
};

const isCalibrating = ref(false);
const startCalibration = () => {
  useCameraSettingsStore().startPnPCalibration({
    squareSizeIn: squareSizeIn.value,
    patternHeight: patternHeight.value,
    patternWidth: patternWidth.value,
    boardType: boardType.value
  });
  // The Start PnP method already handles updating the backend so only a store update is required
  useCameraSettingsStore().currentCameraSettings.currentPipelineIndex = WebsocketPipelineType.Calib3d;
  isCalibrating.value = true;
  calibCanceled.value = false;
};
const showCalibEndDialog = ref(false);
const calibCanceled = ref(false);
const calibSuccess = ref<boolean | undefined>(undefined);
const endCalibration = () => {
  if (!useStateStore().calibrationData.hasEnoughImages) {
    calibCanceled.value = true;
  }

  showCalibEndDialog.value = true;
  // Check if calibration finished cleanly or was canceled
  useCameraSettingsStore()
    .endPnPCalibration()
    .then(() => {
      calibSuccess.value = true;
    })
    .catch(() => {
      calibSuccess.value = false;
    })
    .finally(() => {
      isCalibrating.value = false;
    });
};
</script>

<template>
  <div>
    <v-card class="mb-3 pr-6 pb-3" color="primary" dark>
      <v-card-title>Camera Calibration</v-card-title>
      <div class="ml-5">
        <v-row>
          <v-col cols="12" md="6">
            <v-form ref="form" v-model="settingsValid">
              <pv-select
                v-model="useStateStore().calibrationData.videoFormatIndex"
                label="Resolution"
                :select-cols="7"
                :disabled="isCalibrating"
                tooltip="Resolution to calibrate at (you will have to calibrate every resolution you use 3D mode on)"
                :items="getUniqueVideoResolutionStrings()"
              />
              <pv-select
                v-show="isCalibrating"
                v-model="useCameraSettingsStore().currentPipelineSettings.streamingFrameDivisor"
                label="Decimation"
                tooltip="Resolution to which camera frames are downscaled for detection. Calibration still uses full-res"
                :items="calibrationDivisors"
                :select-cols="7"
                @input="
                  (v) => useCameraSettingsStore().changeCurrentPipelineSetting({ streamingFrameDivisor: v }, false)
                "
              />
              <pv-select
                v-model="boardType"
                label="Board Type"
                tooltip="Calibration board pattern to use"
                :select-cols="7"
                :items="['Chessboard', 'Dotboard']"
                :disabled="isCalibrating"
              />
              <pv-number-input
                v-model="squareSizeIn"
                label="Pattern Spacing (in)"
                tooltip="Spacing between pattern features in inches"
                :disabled="isCalibrating"
                :rules="[(v) => v > 0 || 'Size must be positive']"
                :label-cols="5"
              />
              <pv-number-input
                v-model="patternWidth"
                label="Board Width (in)"
                tooltip="Width of the board in dots or chessboard squares"
                :disabled="isCalibrating"
                :rules="[(v) => v >= 4 || 'Width must be at least 4']"
                :label-cols="5"
              />
              <pv-number-input
                v-model="patternHeight"
                label="Board Height (in)"
                tooltip="Height of the board in dots or chessboard squares"
                :disabled="isCalibrating"
                :rules="[(v) => v >= 4 || 'Height must be at least 4']"
                :label-cols="5"
              />
            </v-form>
          </v-col>
          <v-col cols="12" md="6">
            <v-row align="start" class="pb-4 pt-2">
              <v-simple-table fixed-header height="100%" dense>
                <thead>
                  <tr>
                    <th>Resolution</th>
                    <th>Mean Error</th>
                    <th>Standard Deviation</th>
                    <th>Horizontal FOV</th>
                    <th>Vertical FOV</th>
                    <th>Diagonal FOV</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(value, index) in getUniqueVideoResolutions()" :key="index">
                    <td>{{ value.resolution.width }} X {{ value.resolution.height }}</td>
                    <td>{{ value.mean !== undefined ? value.mean.toFixed(2) + "px" : "-" }}</td>
                    <td>
                      {{ value.standardDeviation !== undefined ? value.standardDeviation.toFixed(2) + "px" : "-" }}
                    </td>
                    <td>{{ value.horizontalFOV !== undefined ? value.horizontalFOV.toFixed(2) + "°" : "-" }}</td>
                    <td>{{ value.verticalFOV !== undefined ? value.verticalFOV.toFixed(2) + "°" : "-" }}</td>
                    <td>{{ value.diagonalFOV !== undefined ? value.diagonalFOV.toFixed(2) + "°" : "-" }}</td>
                  </tr>
                </tbody>
              </v-simple-table>
            </v-row>
            <v-row justify="center">
              <v-chip
                v-show="isCalibrating"
                label
                :color="useStateStore().calibrationData.hasEnoughImages ? 'secondary' : 'gray'"
              >
                Snapshots: {{ useStateStore().calibrationData.imageCount }} of at least
                {{ useStateStore().calibrationData.minimumImageCount }}
              </v-chip>
            </v-row>
          </v-col>
        </v-row>
        <v-row v-if="isCalibrating">
          <v-col cols="12" class="pt-0">
            <pv-slider
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraExposure"
              :disabled="useCameraSettingsStore().currentCameraSettings.pipelineSettings.cameraAutoExposure"
              label="Exposure"
              tooltip="Directly controls how much light is allowed to fall onto the sensor, which affects apparent brightness"
              :min="0"
              :max="100"
              :slider-cols="8"
              :step="0.1"
              @input="(args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraExposure: args }, false)"
            />
            <pv-slider
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraBrightness"
              label="Brightness"
              :min="0"
              :max="100"
              :slider-cols="8"
              @input="
                (args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraBrightness: args }, false)
              "
            />
            <pv-switch
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraAutoExposure"
              class="pt-2"
              label="Auto Exposure"
              :label-cols="4"
              tooltip="Enables or Disables camera automatic adjustment for current lighting conditions"
              @input="
                (args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraAutoExposure: args }, false)
              "
            />
            <pv-slider
              v-if="useCameraSettingsStore().currentPipelineSettings.cameraGain >= 0"
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraGain"
              label="Camera Gain"
              tooltip="Controls camera gain, similar to brightness"
              :min="0"
              :max="100"
              @input="(args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraGain: args }, false)"
            />
            <pv-slider
              v-if="useCameraSettingsStore().currentPipelineSettings.cameraRedGain !== -1"
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraRedGain"
              label="Red AWB Gain"
              :min="0"
              :max="100"
              tooltip="Controls red automatic white balance gain, which affects how the camera captures colors in different conditions"
              @input="(args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraRedGain: args }, false)"
            />
            <pv-slider
              v-if="useCameraSettingsStore().currentPipelineSettings.cameraBlueGain !== -1"
              v-model="useCameraSettingsStore().currentPipelineSettings.cameraBlueGain"
              label="Blue AWB Gain"
              :min="0"
              :max="100"
              tooltip="Controls blue automatic white balance gain, which affects how the camera captures colors in different conditions"
              @input="(args) => useCameraSettingsStore().changeCurrentPipelineSetting({ cameraBlueGain: args }, false)"
            />
          </v-col>
        </v-row>
        <v-row>
          <v-col :cols="6">
            <v-btn
              small
              color="secondary"
              style="width: 100%"
              :disabled="!settingsValid"
              @click="isCalibrating ? useCameraSettingsStore().takeCalibrationSnapshot() : startCalibration()"
            >
              {{ isCalibrating ? "Take Snapshot" : "Start Calibration" }}
            </v-btn>
          </v-col>
          <v-col :cols="6">
            <v-btn
              small
              :color="useStateStore().calibrationData.hasEnoughImages ? 'accent' : 'red'"
              :class="useStateStore().calibrationData.hasEnoughImages ? 'black--text' : 'white---text'"
              style="width: 100%"
              :disabled="!isCalibrating || !settingsValid"
              @click="endCalibration"
            >
              {{ useStateStore().calibrationData.hasEnoughImages ? "Finish Calibration" : "Cancel Calibration" }}
            </v-btn>
          </v-col>
        </v-row>
        <v-row>
          <v-col :cols="6">
            <v-btn
              color="accent"
              small
              outlined
              style="width: 100%"
              :disabled="!settingsValid"
              @click="downloadCalibBoard"
            >
              <v-icon left> mdi-download </v-icon>
              Generate Board
            </v-btn>
          </v-col>
          <v-col :cols="6">
            <v-btn color="secondary" :disabled="isCalibrating" small style="width: 100%" @click="openCalibUploadPrompt">
              <v-icon left> mdi-upload </v-icon>
              Import From CalibDB
            </v-btn>
            <input
              ref="importCalibrationFromCalibDB"
              type="file"
              accept=".json"
              style="display: none"
              @change="readImportedCalibration"
            />
          </v-col>
        </v-row>
      </div>
    </v-card>
    <v-dialog v-model="showCalibEndDialog" width="500px" :persistent="true">
      <v-card color="primary" dark>
        <v-card-title class="pb-8"> Camera Calibration </v-card-title>
        <div class="ml-3">
          <v-col style="text-align: center">
            <template v-if="calibCanceled">
              <v-icon color="blue" size="70"> mdi-cancel </v-icon>
              <v-card-text
                >Camera Calibration has been Canceled, the backend is attempting to cleanly cancel the calibration
                process.</v-card-text
              >
            </template>
            <template v-else-if="isCalibrating">
              <v-progress-circular indeterminate :size="70" :width="8" color="accent" />
              <v-card-text>Camera is being calibrated. This process may take several minutes...</v-card-text>
            </template>
            <template v-else-if="calibSuccess">
              <v-icon color="green" size="70"> mdi-check-bold </v-icon>
              <v-card-text>
                Camera has been successfully calibrated for
                {{
                  getUniqueVideoResolutionStrings().find(
                    (v) => v.value === useStateStore().calibrationData.videoFormatIndex
                  ).name
                }}!
              </v-card-text>
            </template>
            <template v-else>
              <v-icon color="red" size="70"> mdi-close </v-icon>
              <v-card-text
                >Camera calibration failed! Make sure that the photos are taken such that the rainbow grid circles align
                with the corners of the chessboard, and try again. More information is available in the program
                logs.</v-card-text
              >
            </template>
          </v-col>
        </div>
        <v-card-actions>
          <v-spacer />
          <v-btn v-if="!isCalibrating" color="white" text @click="showCalibEndDialog = false"> OK </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped lang="scss">
.v-data-table {
  text-align: center;

  th,
  td {
    background-color: #006492 !important;
    font-size: 1rem !important;
  }

  tbody :hover td {
    background-color: #005281 !important;
  }

  ::-webkit-scrollbar {
    width: 0;
    height: 0.55em;
    border-radius: 5px;
  }

  ::-webkit-scrollbar-track {
    -webkit-box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.3);
    border-radius: 10px;
  }

  ::-webkit-scrollbar-thumb {
    background-color: #ffd843;
    border-radius: 10px;
  }
}
</style>
