import type { Inspiration, InspirationPage } from "@/api/client";

const categories = ["portrait", "photography", "anime", "illustration", "design"] as const;
const files = [
  ...Array.from({ length: 12 }, (_, i) => `portrait-${String(i + 1).padStart(2, "0")}.webp`),
  ...Array.from({ length: 10 }, (_, i) => `photography-${String(i + 1).padStart(2, "0")}.webp`),
  ...Array.from({ length: 8 }, (_, i) => `anime-${String(i + 1).padStart(2, "0")}.webp`),
  ...Array.from({ length: 11 }, (_, i) => `illustration-${String(i + 1).padStart(2, "0")}.webp`),
  ...Array.from({ length: 11 }, (_, i) => `design-${String(i + 1).padStart(2, "0")}.webp`),
];
export const fixtureInspirations: Inspiration[] = files.map((file, index) => {
  const category = file.split("-")[0] as (typeof categories)[number];
  const slug = file.replace(".webp", "");
  return { id: `fixture-${index + 1}`, slug, title: `${category.charAt(0).toUpperCase()}${category.slice(1)} study ${String(index + 1).padStart(2, "0")}`, promptSummary: "Curated visual reference with soft directional light.", prompt: "A carefully composed visual study with clear subject, natural light, detailed texture and a restrained editorial palette.", category, imageUrl: `/inspiration/${file}`, thumbnailUrl: `/inspiration/${file}`, width: 1350, height: 1800, authorDisplayName: "Dream Space", likeCount: 12 + index, modelName: "Image 4.7", ratio: "3:4", resolutionLabel: "2K", isAiGenerated: true, sourceName: "Dream Space Gallery", publishedAt: "2026-07-31" };
});

export function fixturePage(category?: string, query?: string): InspirationPage {
  const normalized = (query || "").trim().toLowerCase();
  const items = fixtureInspirations.filter((item) => (category ? item.category === category : true)).filter((item) => !normalized || `${item.title} ${item.prompt}`.toLowerCase().includes(normalized));
  return { items, total: items.length, page: 1, pageSize: items.length, pageCount: items.length ? 1 : 0 };
}
