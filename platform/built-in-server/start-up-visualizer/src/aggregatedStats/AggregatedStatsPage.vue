<!-- Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file. -->
<template>
  <div>
    <el-row>
      <el-col :span="12">
        <el-form :inline="true" size="small">
          <el-form-item label="Server url">
            <el-input
                placeholder="Enter the aggregated stats server URL..."
                v-model="chartSettings.serverUrl">
            </el-input>
          </el-form-item>
          <el-form-item>
            <el-button @click="loadData" :loading="isFetching">Load</el-button>
          </el-form-item>
        </el-form>
      </el-col>
      <el-col :span="12">
        <div style="float: right">
          <el-checkbox
              size="small"
              v-model="chartSettings.showScrollbarXPreview"
              @change="isShowScrollbarXPreviewChanged"
          >Show horizontal scrollbar preview</el-checkbox>
        </div>
      </el-col>
    </el-row>

    <el-form :inline="true" size="small">
      <el-form-item label="Product">
        <el-select v-model="chartSettings.selectedProduct" filterable>
          <el-option
                v-for="productId in products"
                :key="productId"
                :label="productId"
                :value="productId">
              </el-option>
        </el-select>
      </el-form-item>
      <el-form-item label="Machine">
        <el-select v-model="chartSettings.selectedMachine" filterable>
          <el-option
                v-for="machine in machines"
                :key="machine.id"
                :label="machine.name"
                :value="machine.id">
              </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <div class="aggregatedChart" ref="clusteredChartContainer"></div>

    <h3>Duration Events</h3>
    <div class="aggregatedChart" ref="durationEventChartContainer"></div>

    <h3>Instant Events</h3>
    <div class="aggregatedChart" ref="instantEventChartContainer"></div>

    <el-row>
      <el-col>
        <ul>
          <li>
            <small>Events <code>bootstrap</code> and <code>splash</code> are not available for reports <= v5 (May 2019, Idea 2019.2).</small>
          </li>
        </ul>
      </el-col>
    </el-row>
  </div>
</template>

<script lang="ts" src="./AggregatedStatsPage.ts"></script>
