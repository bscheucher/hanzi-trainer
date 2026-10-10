import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import Footer from "./Footer";

it("shows the CC-CEDICT attribution with license link", () => {
  render(<Footer />);

  expect(screen.getByRole("contentinfo")).toHaveTextContent("Dictionary data: CC-CEDICT, CC BY-SA 4.0");
  expect(screen.getByRole("link", { name: "CC BY-SA 4.0" })).toHaveAttribute(
    "href",
    "https://creativecommons.org/licenses/by-sa/4.0/",
  );
});
