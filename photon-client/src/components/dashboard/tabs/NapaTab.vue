<script setup lang="ts">
import { PipelineType, type NapaPipelineSettings, AprilTagFamily } from "@/types/PipelineTypes";
import PvSelect from "@/components/common/pv-select.vue";
import PvSlider from "@/components/common/pv-slider.vue";
import PvSwitch from "@/components/common/pv-switch.vue";
import { computed } from "vue";
import { useStateStore } from "@/stores/StateStore";
import { useCameraSettingsStore } from "@/stores/settings/CameraSettingsStore";
import { useDisplay } from "vuetify";

// Defer reference to store access method
const currentPipelineSettings = computed<NapaPipelineSettings>(
  () => useCameraSettingsStore().currentPipelineSettings as NapaPipelineSettings
);
const { mdAndDown } = useDisplay();
const interactiveCols = computed(() =>
  mdAndDown.value && (!useStateStore().sidebarFolded || useCameraSettingsStore().isDriverMode) ? 8 : 7
);
// NAPA's soft decoding, corner alignment and camera gamma only work with tag36h11
const is36h11 = computed(() => currentPipelineSettings.value.tagFamily === AprilTagFamily.Family36h11);
const change = (setting: Partial<NapaPipelineSettings>) =>
  useCameraSettingsStore().changeCurrentPipelineSetting(setting, false);
</script>

<template>
  <div v-if="currentPipelineSettings.pipelineType === PipelineType.Napa">
    <pv-select
      v-model="currentPipelineSettings.tagFamily"
      label="Target family"
      :items="[
        { value: AprilTagFamily.Family36h11, name: 'AprilTag 36h11 (6.5in)' },
        { value: AprilTagFamily.Family16h5, name: 'AprilTag 16h5 (6in)' }
      ]"
      :select-cols="interactiveCols"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ tagFamily: value }, false)"
    />
    <pv-slider
      v-model="currentPipelineSettings.minTagPx"
      :slider-cols="interactiveCols"
      label="Min Tag Size (px)"
      tooltip="Smallest tag (side of the black square, in pixels) NAPA is guaranteed to find. Lower values increase range at some cost in speed"
      :min="8"
      :max="200"
      @update:modelValue="(value) => useCameraSettingsStore().changeCurrentPipelineSetting({ minTagPx: value }, false)"
    />
    <pv-slider
      v-model="currentPipelineSettings.minContrast"
      :slider-cols="interactiveCols"
      label="Min Contrast"
      tooltip="Minimum difference in grey levels between a tag's black border and the white around it. Lower this for dim or low-contrast scenes"
      :min="1"
      :max="255"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ minContrast: value }, false)
      "
    />
    <pv-slider
      v-model="currentPipelineSettings.minBorderCells"
      :slider-cols="interactiveCols"
      label="Min Border Cells"
      tooltip="How many of the tag's 28 black border cells must read as dark (scaled for other families). Lower this to tolerate partly covered tags, at the risk of more false positives"
      :min="0"
      :max="28"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ minBorderCells: value }, false)
      "
    />
    <pv-slider
      v-model="currentPipelineSettings.sharpening"
      :slider-cols="interactiveCols"
      label="Sharpening (%)"
      tooltip="Strength of the sharpening applied when reading tag cells. Upstream AprilTag uses 25"
      :min="0"
      :max="100"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ sharpening: value }, false)
      "
    />
    <pv-slider
      v-model="currentPipelineSettings.decisionMargin"
      :slider-cols="interactiveCols"
      label="Decision Margin Cutoff"
      tooltip="Tags with a 'margin' (decoding quality score) less than this will be rejected. Increase this to reduce the number of false positive detections"
      :min="0"
      :max="250"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ decisionMargin: value }, false)
      "
    />
    <pv-slider
      v-model="currentPipelineSettings.numIterations"
      :slider-cols="interactiveCols"
      label="Pose Estimation Iterations"
      tooltip="Number of iterations the pose estimation algorithm will run, 50-100 is a good starting point"
      :min="0"
      :max="500"
      @update:modelValue="
        (value) => useCameraSettingsStore().changeCurrentPipelineSetting({ numIterations: value }, false)
      "
    />
    <v-divider class="mt-3 mb-2" />
    <p class="text-subtitle-2 pb-1">Advanced (experimental)</p>
    <pv-slider
      v-model="currentPipelineSettings.threads"
      :slider-cols="interactiveCols"
      label="Threads"
      tooltip="Threads one detection may use. Results are identical at every count; only latency changes. Leave at 1 if several cameras already keep the CPU busy"
      :min="1"
      :max="16"
      @update:modelValue="(value) => change({ threads: value })"
    />
    <pv-switch
      v-model="currentPipelineSettings.useLensModel"
      :switch-cols="interactiveCols"
      label="Use Lens Calibration"
      tooltip="Fit tag edges as straight lines through the calibrated lens instead of in raw pixels. Helps with wide-angle lenses. Needs an OpenCV calibration at this resolution"
      @update:modelValue="(value) => change({ useLensModel: value })"
    />
    <pv-switch
      v-model="currentPipelineSettings.recoverShadows"
      :switch-cols="interactiveCols"
      label="Shadow Recovery"
      tooltip="After the normal pass, search again with a lower threshold for tags in shadow or poor light. Only exact code matches are accepted"
      @update:modelValue="(value) => change({ recoverShadows: value })"
    />
    <pv-slider
      v-if="currentPipelineSettings.recoverShadows"
      v-model="currentPipelineSettings.shadowMinTagPx"
      :slider-cols="interactiveCols"
      label="Shadow Min Tag Size (px)"
      tooltip="Smallest tag the shadow recovery pass searches for. 0 searches at full sensitivity; larger values are faster. Values below Min Tag Size are raised to it"
      :min="0"
      :max="200"
      @update:modelValue="(value) => change({ shadowMinTagPx: value })"
    />
    <pv-switch
      v-model="currentPipelineSettings.regrowSmallTags"
      :switch-cols="interactiveCols"
      label="Regrow Small Tags"
      tooltip="Retry small candidates whose thin border broke apart when sampled, at full resolution. Finds more small, distant tags for a few percent more time"
      @update:modelValue="(value) => change({ regrowSmallTags: value })"
    />
    <pv-select
      v-model="currentPipelineSettings.tagShape"
      label="Tag Shape"
      tooltip="Sampling hint. Tall: tags are seen turned about the vertical axis (narrow and tall). Wide: the camera is mounted on its side"
      :items="[
        { value: 0, name: 'Any' },
        { value: 1, name: 'Tall' },
        { value: 2, name: 'Wide' }
      ]"
      :select-cols="interactiveCols"
      @update:modelValue="(value) => change({ tagShape: value })"
    />
    <pv-select
      v-model="currentPipelineSettings.splitTouching"
      label="Touching Tags"
      tooltip="Also decode tags that touch other dark shapes at their corners, as on AprilGrid boards and checkerboards. Slower. Auto only does it on frames where touching tags are seen"
      :items="[
        { value: 0, name: 'Off' },
        { value: 2, name: 'Auto' },
        { value: 1, name: 'Always' }
      ]"
      :select-cols="interactiveCols"
      @update:modelValue="(value) => change({ splitTouching: value })"
    />
    <pv-switch
      v-model="currentPipelineSettings.softDecode"
      :switch-cols="interactiveCols"
      :disabled="!is36h11"
      label="Soft Decode"
      tooltip="Recognise tiny tags (about 8-19 px) and tags with part of the code obscured by matching the whole tag against every code. Not exact matches, so structured non-tags can pass. 36h11 only"
      @update:modelValue="(value) => change({ softDecode: value })"
    />
    <pv-switch
      v-model="currentPipelineSettings.alignCorners"
      :switch-cols="interactiveCols"
      :disabled="!is36h11"
      label="Align Blurred Corners"
      tooltip="Re-align the corners of blurred tags after decoding, at some cost per tag. 36h11 only"
      @update:modelValue="(value) => change({ alignCorners: value })"
    />
    <pv-slider
      v-model="currentPipelineSettings.cameraGamma"
      :slider-cols="interactiveCols"
      :disabled="!is36h11"
      label="Camera Gamma"
      tooltip="The camera's tone curve. 1 means linear (no change). If set, corners are refined on a linearised copy of each tag. A wrong value is worse than none. 36h11 only"
      :min="1"
      :max="4"
      :step="0.1"
      @update:modelValue="(value) => change({ cameraGamma: value })"
    />
    <p class="text-caption text-medium-emphasis pt-2">
      Detection by
      <a href="https://github.com/zwatx/NAPA" target="_blank" rel="noopener" style="color: inherit">NAPA</a>, by Zachary
      Wilson (GPL-3.0).
    </p>
  </div>
</template>
