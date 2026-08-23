import { defineStore } from "pinia";
import { ref, watch } from "vue";

export type Language = "zh" | "en";
export type Theme = "system" | "light" | "dark";

export const usePreferencesStore = defineStore("preferences", () => {
  const language = ref<Language>((localStorage.getItem("dream-space-language") as Language) || "zh");
  const theme = ref<Theme>((localStorage.getItem("dream-space-theme") as Theme) || "system");

  function apply() {
    document.documentElement.dataset.theme = theme.value;
    document.documentElement.lang = language.value === "zh" ? "zh-CN" : "en";
  }
  function setLanguage(value: Language) { language.value = value; }
  function setTheme(value: Theme) { theme.value = value; }
  watch(language, (value) => { localStorage.setItem("dream-space-language", value); apply(); });
  watch(theme, (value) => { localStorage.setItem("dream-space-theme", value); apply(); });
  apply();
  return { language, theme, setLanguage, setTheme, apply };
});
