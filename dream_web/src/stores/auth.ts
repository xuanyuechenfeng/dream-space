import { defineStore } from "pinia";
import { ref } from "vue";
import { api, type AuthSession, type CodeResponse, type LoginPayload, type PasswordLoginPayload, type RegisterPayload } from "@/api/client";
import { clearCachedImages } from "@/lib/imageCache";

export const useAuthStore = defineStore("auth", () => {
  const session = ref<AuthSession | null>(null);
  const loading = ref(true);
  const error = ref("");
  async function loadSession() {
    loading.value = true;
    try {
      session.value = await api.session();
      if (!session.value.authenticated) await clearCachedImages();
      error.value = "";
    }
    catch (e) {
      await clearCachedImages();
      error.value = e instanceof Error ? e.message : "Session unavailable";
      session.value = { authenticated: false };
    }
    finally { loading.value = false; }
  }
  async function sendCode(phone: string): Promise<CodeResponse> { return api.sendCode(phone); }
  async function login(payload: LoginPayload) { session.value = await api.login(payload); return session.value; }
  async function passwordLogin(payload: PasswordLoginPayload) { session.value = await api.passwordLogin(payload); return session.value; }
  async function register(payload: RegisterPayload) { session.value = await api.register(payload); return session.value; }
  async function logout() {
    try { await api.logout(); }
    finally { await clearCachedImages(); session.value = { authenticated: false }; }
  }
  return { session, loading, error, loadSession, sendCode, login, passwordLogin, register, logout };
});
