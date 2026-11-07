<script setup lang="ts">
import { PipelineType, type NapaPipelineSettings, AprilTagFamily } from "@/types/PipelineTypes";
import PvSelect from "@/components/common/pv-select.vue";
import PvSlider from "@/components/common/pv-slider.vue";
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
    <p class="text-caption text-medium-emphasis pt-2">
      Detection by
      <a href="https://github.com/zwatx/NAPA" target="_blank" rel="noopener" style="color: inherit">NAPA</a>, by Zachary
      Wilson (GPL-3.0).
    </p>
  </div>
</template>
