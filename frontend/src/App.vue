<template>
  <router-view />
</template>

<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
// Restore auth state from localStorage on app init
authStore.restore()
</script>
