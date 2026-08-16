import { createRouter, createWebHistory } from "vue-router";
import ScaffoldView from "@/views/ScaffoldView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/inspiration" },
    { path: "/inspiration", component: ScaffoldView, props: { title: "Inspiration" } },
    {
      path: "/inspiration/:slug",
      component: ScaffoldView,
      props: { title: "Inspiration detail" },
    },
    { path: "/login", component: ScaffoldView, props: { title: "Login" } },
    { path: "/generate", component: ScaffoldView, props: { title: "Generation workspace" } },
    {
      path: "/generate/:sessionId",
      component: ScaffoldView,
      props: { title: "Generation session" },
    },
  ],
});

export default router;
