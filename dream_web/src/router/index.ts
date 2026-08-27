import { createRouter, createWebHistory } from "vue-router";
import InspirationGalleryView from "@/features/inspiration/InspirationGalleryView.vue";
import InspirationDetailView from "@/features/inspiration/InspirationDetailView.vue";
import LoginView from "@/features/auth/LoginView.vue";
import RegisterView from "@/features/auth/RegisterView.vue";
import GenerationWorkspaceView from "@/features/generation/GenerationWorkspaceView.vue";
import AccountView from "@/views/AccountView.vue";
import { useAuthStore } from "@/stores/auth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/inspiration" },
    { path: "/inspiration", component: InspirationGalleryView },
    { path: "/inspiration/:slug", component: InspirationDetailView },
    { path: "/login", component: LoginView },
    { path: "/register", component: RegisterView },
    { path: "/generate", component: GenerationWorkspaceView, props: true, meta: { requiresAuth: true } },
    { path: "/generate/:sessionId", component: GenerationWorkspaceView, props: true, meta: { requiresAuth: true } },
    { path: "/account", component: AccountView, meta: { requiresAuth: true } },
  ],
});

router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true;
  const auth = useAuthStore();
  if (auth.loading) await auth.loadSession();
  if (auth.session?.authenticated) return true;
  return { path: "/login", query: { returnTo: to.fullPath } };
});

export default router;
