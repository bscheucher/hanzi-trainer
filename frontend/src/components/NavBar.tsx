"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

export const NAV_ITEMS = [
  { href: "/lessons", label: "Lessons" },
  { href: "/vocabulary", label: "Vocabulary" },
  { href: "/review", label: "Review" },
] as const;

export default function NavBar() {
  const pathname = usePathname();

  return (
    <header className="border-b border-border">
      <nav className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-3">
        <Link href="/" className="mr-4 text-lg font-semibold">
          <span lang="zh-CN" className="text-accent">
            汉字
          </span>{" "}
          Hanzi Trainer
        </Link>
        {NAV_ITEMS.map(({ href, label }) => {
          const active = pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              aria-current={active ? "page" : undefined}
              className={
                active
                  ? "font-medium text-foreground underline underline-offset-8"
                  : "text-muted hover:text-foreground"
              }
            >
              {label}
            </Link>
          );
        })}
      </nav>
    </header>
  );
}
