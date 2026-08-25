import { createRouter, createWebHistory } from "vue-router";
import AdminShell from "@/layouts/AdminShell.vue";
import AdminInspirationsView from "@/views/AdminInspirationsView.vue";
import AdminLoginView from "@/views/AdminLoginView.vue";
import AdminTasksView from "@/views/AdminTasksView.vue";
import AdminModerationView from "@/views/AdminModerationView.vue";
import AdminUsersView from "@/views/AdminUsersView.vue";
import AdminBillingOrdersView from "@/views/AdminBillingOrdersView.vue";
import AdminProductsView from "@/views/AdminProductsView.vue";
import AdminPricingRulesView from "@/views/AdminPricingRulesView.vue";
import AdminAuditEventsView from "@/views/AdminAuditEventsView.vue";
import { useAdminAuthStore } from "@/stores/adminAuth";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/tasks" },
    { path: "/login", component: AdminLoginView, meta: { public: true } },
    {
      path: "/",
      component: AdminShell,
      children: [
        { path: "tasks", component: AdminTasksView },
        { path: "moderation", component: AdminModerationView },
        { path: "inspirations", component: AdminInspirationsView },
        { path: "users", component: AdminUsersView },
        { path: "billing/orders", component: AdminBillingOrdersView },
        { path: "billing/products", component: AdminProductsView },
        { path: "billing/rules", component: AdminPricingRulesView },
        { path: "audit-events", component: AdminAuditEventsView },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const auth = useAdminAuthStore();
  if (!auth.initialized) await auth.refresh();
  if (to.meta.public) return auth.authenticated ? "/tasks" : true;
  return auth.authenticated ? true : "/login";
});

export default router;
