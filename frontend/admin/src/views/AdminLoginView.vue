<script setup lang="ts">
import { ShieldCheck } from "lucide-vue-next";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { adminApi } from "@/api/admin";
import { useAdminAuthStore } from "@/stores/adminAuth";

const router = useRouter();
const auth = useAdminAuthStore();
const phone = ref("");
const code = ref("");
const challengeId = ref("");
const demoCode = ref("");
const sending = ref(false);
const submitting = ref(false);
const error = ref("");

async function sendCode() {
  sending.value = true;
  error.value = "";
  try {
    const response = await adminApi.sendCode(phone.value);
    challengeId.value = response.challengeId;
    demoCode.value = response.demoCode || "";
  } catch (reason) { error.value = (reason as Error).message; }
  finally { sending.value = false; }
}

async function login() {
  submitting.value = true;
  error.value = "";
  try {
    auth.session = await adminApi.login(phone.value, challengeId.value, code.value);
    auth.initialized = true;
    await router.replace("/tasks");
  } catch (reason) { error.value = (reason as Error).message; }
  finally { submitting.value = false; }
}
</script>

<template>
  <main class="admin-login-page">
    <section class="admin-login-intro">
      <span class="admin-brand-mark" aria-hidden="true"><ShieldCheck /></span>
      <p class="admin-page-kicker">DREAM SPACE</p>
      <h1>造梦空间</h1>
      <p>管理生成任务、额度对账与灵感内容。</p>
    </section>
    <section class="admin-login-panel">
      <form class="admin-login-form" @submit.prevent="login">
        <div><p class="admin-page-kicker">OPERATIONS</p><h2>管理员登录</h2><p>使用已授权的管理员手机号登录。</p></div>
        <label><span>手机号</span><input v-model.trim="phone" type="tel" inputmode="numeric" autocomplete="tel" maxlength="11" required /></label>
        <label><span>验证码</span><div class="admin-code-row"><input v-model.trim="code" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required /><button class="admin-button secondary" type="button" :disabled="sending || phone.length !== 11" @click="sendCode">{{ sending ? '发送中' : '获取验证码' }}</button></div></label>
        <p v-if="demoCode" class="admin-demo-code">演示验证码：{{ demoCode }}</p>
        <p v-if="error" class="admin-login-error" role="alert">{{ error }}</p>
        <button class="admin-button primary admin-login-submit" type="submit" :disabled="submitting || !challengeId || code.length !== 6">{{ submitting ? '正在登录' : '登录管理端' }}</button>
      </form>
    </section>
  </main>
</template>
