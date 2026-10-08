<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <header class="app-header">
    <nav class="app-nav" aria-label="主导航">
      <RouterLink class="app-brand" to="/">Interview Agent · 首页</RouterLink>
    </nav>
  </header>
  <main class="app-main">
    <RouterView />
  </main>
</template>
