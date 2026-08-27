import { defineStore } from "pinia";
import { ref } from "vue";
import { api, type AuthSession, type CodeResponse, type LoginPayload, type PasswordLoginPayload, type RegisterPayload } from "@/api/client";

export const useAuthStore = defineStore("auth", () => {
  const session = ref<AuthSession | null>(null);
  const loading = ref(true);
  const error = ref("");
  async function loadSession() {
    loading.value = true;
    try { session.value = await api.session(); error.value = ""; }
    catch (e) { error.value = e instanceof Error ? e.message : "Session unavailable"; session.value = { authenticated: false }; }
    finally { loading.value = false; }
  }
  async function sendCode(phone: string): Promise<CodeResponse> { return api.sendCode(phone); }
  async function login(payload: LoginPayload) { session.value = await api.login(payload); return session.value; }
  async function passwordLogin(payload: PasswordLoginPayload) { session.value = await api.passwordLogin(payload); return session.value; }
  async function register(payload: RegisterPayload) { session.value = await api.register(payload); return session.value; }
  async function logout() { await api.logout(); session.value = { authenticated: false }; }
  return { session, loading, error, loadSession, sendCode, login, passwordLogin, register, logout };
});
