<script setup>
defineProps({
  size: {
    type: String,
    default: 'default',
  },
})
</script>

<template>
  <div class="project-brand" :class="`is-${size}`">
    <div class="project-brand-mark" aria-hidden="true">
      <span class="project-brand-ring outer"></span>
      <span class="project-brand-ring inner"></span>
      <span class="project-brand-core"></span>
      <span class="project-brand-dot dot-top"></span>
      <span class="project-brand-dot dot-bottom"></span>
    </div>
    <div class="project-brand-name">灵伴</div>
  </div>
</template>

<style scoped>
.project-brand {
  --brand-gap: 12px;
  --brand-mark-size: 44px;
  --brand-name-size: 18px;
  display: inline-flex;
  align-items: center;
  gap: var(--brand-gap);
  min-width: 0;
}

.project-brand.is-large {
  --brand-gap: 16px;
  --brand-mark-size: 58px;
  --brand-name-size: 32px;
}

.project-brand-mark {
  position: relative;
  width: var(--brand-mark-size);
  height: var(--brand-mark-size);
  flex: 0 0 var(--brand-mark-size);
  border-radius: calc(var(--brand-mark-size) * 0.3);
  background:
    radial-gradient(circle at 32% 28%, rgba(255, 255, 255, 0.92), rgba(255, 255, 255, 0.06) 42%),
    linear-gradient(145deg, #2459d8 0%, #22d3ee 52%, #8b5cf6 100%);
  box-shadow: 0 18px 34px rgba(36, 89, 216, 0.22);
  overflow: hidden;
}

.project-brand-mark::after {
  content: '';
  position: absolute;
  inset: 10%;
  border-radius: calc(var(--brand-mark-size) * 0.24);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0));
}

.project-brand-ring,
.project-brand-core,
.project-brand-dot {
  position: absolute;
  border-radius: 999px;
}

.project-brand-ring {
  border: 1.6px solid rgba(255, 255, 255, 0.62);
}

.project-brand-ring.outer {
  inset: 22%;
  transform: rotate(-20deg);
}

.project-brand-ring.inner {
  inset: 34%;
  border-color: rgba(255, 255, 255, 0.78);
  transform: rotate(18deg);
}

.project-brand-core {
  inset: 40%;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 0 0 6px rgba(255, 255, 255, 0.12);
}

.project-brand-dot {
  width: calc(var(--brand-mark-size) * 0.15);
  height: calc(var(--brand-mark-size) * 0.15);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.14);
}

.project-brand-dot.dot-top {
  top: 20%;
  right: 20%;
}

.project-brand-dot.dot-bottom {
  left: 18%;
  bottom: 18%;
}

.project-brand-name {
  font-size: var(--brand-name-size);
  line-height: 1;
  font-weight: 900;
  letter-spacing: 0.04em;
  color: #0f172a;
  white-space: nowrap;
}
</style>
