import { createRouter, createWebHistory } from "vue-router";
import ScaffoldView from "@/views/ScaffoldView.vue";

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: "/", redirect: "/tasks" },
    { path: "/login", component: ScaffoldView, props: { title: "Admin login" } },
    { path: "/tasks", component: ScaffoldView, props: { title: "Generation tasks" } },
    { path: "/inspirations", component: ScaffoldView, props: { title: "Inspirations" } },
  ],
});

export default router;
