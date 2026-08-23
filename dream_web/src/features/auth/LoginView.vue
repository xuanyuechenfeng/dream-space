<script setup lang="ts">
import { LoaderCircle, RefreshCw, X } from "lucide-vue-next";
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { api, type CaptchaResponse } from "@/api/client";
import { useAuthStore } from "@/stores/auth";
import { usePreferencesStore } from "@/stores/preferences";

type LegalDocument = "terms" | "privacy" | "ai";
const auth = useAuthStore(); const preferences = usePreferencesStore(); const route = useRoute(); const router = useRouter();
const phone = ref(""); const password = ref(""); const captchaCode = ref(""); const captcha = ref<CaptchaResponse | null>(null);
const captchaLoading = ref(false); const submitting = ref(false); const agreed = ref(false); const error = ref(""); const legal = ref<LegalDocument | null>(null); let timer = 0;
const text = computed(() => preferences.language === "zh"
  ? { title: "登录造梦空间", subtitle: "登录后继续你的图片创作。", phone: "手机号 / 账号", password: "密码", captcha: "图形验证码", captchaPlaceholder: "输入图片中的字符", refreshCaptcha: "刷新验证码", submit: "登录并继续", close: "关闭登录", invalidPhone: "请输入正确的 11 位手机号", invalidPassword: "密码长度为 8 到 72 位", failedCaptcha: "验证码加载失败", failedLogin: "登录失败，请检查账号、密码和验证码", agree: "我已阅读并同意", terms: "用户协议", privacy: "隐私政策", and: "和", ai: "AI 功能使用协议" }
  : { title: "Sign in to Dream Space", subtitle: "Sign in to continue creating images.", phone: "Mobile number / account", password: "Password", captcha: "Image verification", captchaPlaceholder: "Enter the characters in the image", refreshCaptcha: "Refresh verification image", submit: "Sign in and continue", close: "Close sign in", invalidPhone: "Enter a valid mobile number", invalidPassword: "Password must be 8 to 72 characters", failedCaptcha: "Unable to load verification image", failedLogin: "Sign-in failed. Check your account, password, and verification code", agree: "I have read and agree to", terms: "Terms of Use", privacy: "Privacy Policy", and: "and", ai: "AI Terms" });
const legalTitle = computed(() => ({ terms: preferences.language === "zh" ? "造梦空间用户协议" : "Dream Space Terms of Use", privacy: preferences.language === "zh" ? "造梦空间隐私政策" : "Dream Space Privacy Policy", ai: preferences.language === "zh" ? "AI 功能使用协议" : "AI Terms" }));
const returnTo = computed(() => { const value = String(route.query.returnTo || "/inspiration"); return value.startsWith("/") && !value.startsWith("//") && !value.includes("\\") ? value : "/inspiration"; });
const canSubmit = computed(() => /^1[3-9]\d{9}$/.test(phone.value) && password.value.length >= 8 && password.value.length <= 72 && captcha.value !== null && captchaCode.value.length === 5 && agreed.value);
async function refreshCaptcha(clearError = true) { captchaLoading.value = true; if (clearError) error.value = ""; captchaCode.value = ""; try { captcha.value = await api.captcha(); } catch (e) { captcha.value = null; error.value = e instanceof Error ? e.message : text.value.failedCaptcha; } finally { captchaLoading.value = false; } }
async function submit() {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) { error.value = text.value.invalidPhone; return; }
  if (password.value.length < 8 || password.value.length > 72) { error.value = text.value.invalidPassword; return; }
  if (!canSubmit.value) return;
  submitting.value = true; error.value = "";
  try {
    await auth.passwordLogin({ phone: phone.value, password: password.value, captchaId: captcha.value!.captchaId, captchaCode: captchaCode.value, version: "2026-01", termsAccepted: agreed.value, privacyAccepted: agreed.value, aiTermsAccepted: agreed.value });
    const raw = sessionStorage.getItem("dream-space-pending-auth-intent"); if (raw) { sessionStorage.setItem("dream-space-restored-auth-intent", raw); sessionStorage.removeItem("dream-space-pending-auth-intent"); }
    await router.replace(returnTo.value);
  } catch (e) { const message = e instanceof Error ? e.message : text.value.failedLogin; await refreshCaptcha(false); error.value = message; } finally { submitting.value = false; }
}
function escape(event: KeyboardEvent) { if (event.key === "Escape" && legal.value) legal.value = null; }
onMounted(() => { void refreshCaptcha(); timer = window.setInterval(() => { if (captcha.value && Date.parse(captcha.value.expiresAt) <= Date.now()) captcha.value = null; }, 1000); window.addEventListener("keydown", escape); });
onUnmounted(() => { window.clearInterval(timer); window.removeEventListener("keydown", escape); });
</script>

<template>
  <main class="login-page">
    <section class="login-visual" aria-label="Dream Space brand visual"><div class="login-scene" aria-hidden="true" /><div class="login-brand"><span class="brand-mark" /><span class="login-brand-copy"><strong>造梦空间 · Dream Space</strong><small>AI IMAGE STUDIO</small></span></div><div class="login-quote"><strong>{{ preferences.language === "zh" ? "让脑海里的画面，成为看得见的作品。" : "Make the images in your mind visible." }}</strong><span>CREATE BEYOND IMAGINATION · 2026</span></div></section>
    <section class="login-form-wrap"><button class="icon-btn login-close" type="button" :aria-label="text.close" @click="router.replace(returnTo)"><X aria-hidden="true" /></button><form class="login-form" @submit.prevent="submit"><h1>{{ text.title }}</h1><p class="login-subtitle">{{ text.subtitle }}</p><label class="form-label" for="phoneInput">{{ text.phone }}</label><input id="phoneInput" v-model="phone" class="form-input" type="tel" autocomplete="username" maxlength="11" @input="phone = phone.replace(/\D/g, '').slice(0, 11)" /><label class="form-label" for="passwordInput">{{ text.password }}</label><input id="passwordInput" v-model="password" class="form-input" type="password" autocomplete="current-password" maxlength="72" /><label class="form-label" for="captchaInput">{{ text.captcha }}</label><div class="captcha-row"><button class="captcha-image-button" type="button" :aria-label="text.refreshCaptcha" :disabled="captchaLoading" @click="refreshCaptcha()"><img v-if="captcha" :src="captcha.imageData" alt="" class="captcha-image" /><LoaderCircle v-else class="spin" aria-hidden="true" /></button><input id="captchaInput" v-model="captchaCode" class="form-input captcha-input" :placeholder="text.captchaPlaceholder" autocomplete="off" maxlength="5" @input="captchaCode = captchaCode.replace(/[^a-zA-Z0-9]/g, '').slice(0, 5)" /><button class="icon-btn captcha-refresh" type="button" :aria-label="text.refreshCaptcha" :disabled="captchaLoading" @click="refreshCaptcha()"><RefreshCw aria-hidden="true" :class="{ spin: captchaLoading }" /></button></div><label class="agreement"><input v-model="agreed" type="checkbox" /><span>{{ text.agree }} <button class="agreement-link" type="button" @click="legal = 'terms'">{{ text.terms }}</button>、<button class="agreement-link" type="button" @click="legal = 'privacy'">{{ text.privacy }}</button> {{ text.and }} <button class="agreement-link" type="button" @click="legal = 'ai'">{{ text.ai }}</button>。</span></label><div class="login-error" role="alert">{{ error }}</div><button class="action-btn primary login-submit" type="submit" :disabled="submitting || !canSubmit"><LoaderCircle v-if="submitting" class="spin" aria-hidden="true" />{{ text.submit }}</button></form></section>
    <div v-if="legal" class="legal-backdrop" role="presentation" @mousedown.self="legal = null"><section class="legal-dialog" role="dialog" aria-modal="true" aria-labelledby="loginLegalTitle"><header class="legal-header"><h2 id="loginLegalTitle">{{ legalTitle[legal] }}</h2><button class="icon-btn" type="button" aria-label="关闭协议" @click="legal = null"><X aria-hidden="true" /></button></header><div class="legal-content"><h3>一、协议范围</h3><p>本协议适用于用户访问和使用造梦空间提供的 AI 图片生成、灵感浏览及相关服务。</p><h3>二、内容与隐私</h3><p>请确保上传内容合法合规。平台仅在提供服务所必要的范围内处理个人信息，并提供访问、更正和删除渠道。</p><h3>三、AI 功能说明</h3><p>AI 生成结果具有不确定性，公开发布或商业使用前应自行审核真实性、合法性和知识产权风险。</p></div></section></div>
  </main>
</template>
