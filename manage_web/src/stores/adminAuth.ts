import { defineStore } from "pinia";
import { adminApi, type AdminSession } from "@/api/admin";

export const useAdminAuthStore = defineStore("admin-auth", {
  state: () => ({ session: null as AdminSession | null, loading: false, initialized: false, error: "" }),
  getters: {
    authenticated: (state) => state.session?.authenticated === true,
    canWrite: (state) => state.session?.authenticated === true && state.session.user.permissions.includes("inspirations:write"),
  },
  actions: {
    async refresh() {
      this.loading = true;
      this.error = "";
      try { this.session = await adminApi.session(); }
      catch (error) { this.error = (error as Error).message; this.session = null; }
      finally { this.loading = false; this.initialized = true; }
    },
    async logout() {
      try { await adminApi.logout(); } finally { this.session = { authenticated: false, user: null }; }
    },
  },
});
