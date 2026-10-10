import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import NavBar from "./NavBar";

const pathname = vi.hoisted(() => ({ current: "/" }));

vi.mock("next/navigation", () => ({
  usePathname: () => pathname.current,
}));

describe("NavBar", () => {
  it("links to Lessons, Vocabulary and Review", () => {
    render(<NavBar />);

    expect(screen.getByRole("link", { name: "Lessons" })).toHaveAttribute("href", "/lessons");
    expect(screen.getByRole("link", { name: "Vocabulary" })).toHaveAttribute("href", "/vocabulary");
    expect(screen.getByRole("link", { name: "Review" })).toHaveAttribute("href", "/review");
  });

  it("marks the current section, including nested routes", () => {
    pathname.current = "/vocabulary/42";
    render(<NavBar />);

    expect(screen.getByRole("link", { name: "Vocabulary" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "Lessons" })).not.toHaveAttribute("aria-current");
  });

  it("renders the Chinese brand with lang zh-CN", () => {
    render(<NavBar />);

    expect(screen.getByText("汉字")).toHaveAttribute("lang", "zh-CN");
  });
});
