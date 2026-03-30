<script setup>
import GlobalToast from '@/components/GlobalToast.vue'
</script>

<template>
  <div class="app">
    <router-view />
    <GlobalToast />
  </div>
</template>

<style scoped>
.app { display: flex; width: 100%; }
</style>
